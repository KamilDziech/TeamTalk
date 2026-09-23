package com.ekotak.teamtalk.data.repository

import com.ekotak.teamtalk.data.local.dao.MontazDao
import com.ekotak.teamtalk.data.local.entity.MontazMutationEntity
import com.ekotak.teamtalk.data.local.entity.MontazMutationEntity.Companion.KIND_PROTOCOL
import com.ekotak.teamtalk.data.local.entity.MontazMutationEntity.Companion.KIND_STATUS
import com.ekotak.teamtalk.data.mapper.isoNow
import com.ekotak.teamtalk.data.mapper.protocolFromForm
import com.ekotak.teamtalk.data.mapper.toDomain
import com.ekotak.teamtalk.data.mapper.toEntity
import com.ekotak.teamtalk.data.mapper.toForm
import com.ekotak.teamtalk.data.mapper.toJob
import com.ekotak.teamtalk.data.mapper.toRow
import com.ekotak.teamtalk.data.remote.api.TeamTalkApi
import com.ekotak.teamtalk.data.remote.dto.JobStatusRequest
import com.ekotak.teamtalk.data.remote.dto.ProtocolSaveRequest
import com.ekotak.teamtalk.data.sync.MontazSyncScheduler
import com.ekotak.teamtalk.domain.model.MontazJob
import com.ekotak.teamtalk.domain.model.MontazProtocol
import com.ekotak.teamtalk.domain.model.MontazStatus
import com.ekotak.teamtalk.domain.repository.MontazJobRepository
import com.ekotak.teamtalk.domain.repository.MontazJobsSnapshot
import com.ekotak.teamtalk.domain.repository.MontazSaveResult
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

/**
 * MODUŁ MONTAŻ — dwa odczyty (lista, teczka) i dwa zapisy (stan roboty,
 * protokół), wszystkie z cache'em i wspólną kolejką montaży.
 *
 * Podział ról jest ten sam, co w karcie deala: cache trzyma to, co powiedział
 * serwer, a niewysłane decyzje nakładamy przy odczycie. Wspólna kolejka
 * (`montaz_mutations`) nie jest tu oszczędnością tabel, tylko porządkiem
 * wysyłki: start roboty musi pójść PRZED protokołem, bo serwer przepuszcza
 * „gotowy" dopiero z „w toku". Opróżnia ją `MontazRepositoryImpl` — jeden
 * procesor na jedną kolejkę.
 */
class MontazJobRepositoryImpl @Inject constructor(
    private val api: TeamTalkApi,
    private val dao: MontazDao,
    private val json: Json,
    private val syncScheduler: MontazSyncScheduler,
) : MontazJobRepository {

    // ── Odczyty ───────────────────────────────────────────────────────────────

    override suspend fun getJobs(crew: Boolean): MontazJobsSnapshot {
        val now = System.currentTimeMillis()
        var offline = false
        var error: String? = null

        runCatching { api.getMyJobs(scope = if (crew) "crew" else null) }
            .onSuccess { rows ->
                // Teczki pobrane wcześniej ZOSTAJĄ przy swoich wierszach:
                // lista ich nie niesie, a ekipa bez zasięgu ma otworzyć wyjazd
                // z pamięci telefonu, nie zobaczyć pustą kartę.
                val cached = dao.getJobs().associateBy { it.id }
                dao.replaceJobs(
                    rows.map { dto ->
                        val fresh = dto.toEntity(now)
                        fresh.copy(
                            packetJson = cached[fresh.id]?.packetJson,
                            scopeNames = cached[fresh.id]?.scopeNames ?: fresh.scopeNames,
                        )
                    },
                )
            }
            .onFailure { e -> if (e is IOException) offline = true else error = readError(e) }

        val queue = dao.getMutations().filter { it.kind == KIND_STATUS || it.kind == KIND_PROTOCOL }
        val pending = queue.map { it.targetId }.toSet()
        return MontazJobsSnapshot(
            jobs = dao.getJobs().map { it.toRow(pending = it.id in pending) },
            fromCache = offline,
            error = error,
        )
    }

    override suspend fun getJob(installationId: String): MontazJob? {
        val now = System.currentTimeMillis()
        var offline = false

        runCatching { api.getJob(installationId) }
            .onSuccess { packet ->
                dao.upsertJob(packet.toEntity(json, now))
            }
            .onFailure { e -> if (e is IOException) offline = true }

        val pending = dao.getMutationPayload(installationId, KIND_STATUS) != null ||
            dao.getMutationPayload(installationId, KIND_PROTOCOL) != null
        val cached = dao.getJob(installationId) ?: return null
        val job = cached.toJob(json, fromCache = offline, pending = pending) ?: return null
        return job.copy(row = job.row.copy(status = statusWithQueue(installationId, job)))
    }

    /**
     * Stan roboty z uwzględnieniem kolejki. Bez tego montaż, który ekipa
     * zaczęła bez zasięgu, wracałby przy każdym odświeżeniu do „zaplanowany" —
     * i pasek kroków cofałby się ludziom na oczach.
     */
    private suspend fun statusWithQueue(installationId: String, job: MontazJob): MontazStatus {
        val raw = dao.getMutationPayload(installationId, KIND_STATUS) ?: return job.row.status
        val queued = runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()
            ?.get("status")?.let { el -> (el as? kotlinx.serialization.json.JsonPrimitive)?.content }
        return queued?.let { MontazStatus.fromWire(it) } ?: job.row.status
    }

    // ── Stan roboty ───────────────────────────────────────────────────────────

    override suspend fun setStatus(
        installationId: String,
        status: MontazStatus,
    ): MontazSaveResult {
        val body = buildJsonObject { put("status", status.wire) }
        return try {
            api.setJobStatus(installationId, JobStatusRequest(status.wire))
            dao.deleteMutation(installationId, KIND_STATUS)
            refreshJob(installationId)
            MontazSaveResult.SENT
        } catch (_: IOException) {
            enqueue(installationId, KIND_STATUS, body)
            MontazSaveResult.QUEUED
        }
    }

    // ── Protokół ──────────────────────────────────────────────────────────────

    override suspend fun getProtocol(job: MontazJob): MontazProtocol {
        val id = job.row.id

        // Serwer jest źródłem prawdy tylko wtedy, gdy telefon nie ma zapisu
        // czekającego w kolejce: protokół wypełniony w kotłowni bez zasięgu
        // przegrałby z pustym dokumentem z panelu.
        val queued = dao.getMutationPayload(id, KIND_PROTOCOL) != null
        if (!queued) {
            runCatching { api.getProtocol(id) }
                .onSuccess { response ->
                    val dto = response.body()
                    if (response.isSuccessful && dto != null) {
                        val form = dto.formData
                        val remote = protocolFromForm(id, form).copy(signature = dto.signature)
                        dao.upsertProtocol(
                            remote.toEntity(json, pendingSince = null, now = System.currentTimeMillis()),
                        )
                    }
                }
        }

        val stored = dao.getProtocol(id)?.toDomain(json) ?: MontazProtocol(installationId = id)
        return withScheme(stored, job)
    }

    /**
     * Nałożenie ŚWIEŻYCH pytań z teczki na zapisany protokół: pytania dopisane
     * w katalogu dochodzą do wersji roboczej, a odpowiedzi już udzielone
     * zostają. Protokołu ZAMKNIĘTEGO nie ruszamy — jego migawka jest dokumentem
     * i ma się wydrukować tak, jak go wypełniono.
     */
    private fun withScheme(stored: MontazProtocol, job: MontazJob): MontazProtocol {
        val base = stored.copy(
            scope = job.row.scopeNames.ifEmpty { stored.scope },
            client = stored.client.ifBlank { job.row.clientName },
            address = stored.address.ifBlank { job.row.address.orEmpty() },
            technicians = stored.technicians.ifBlank {
                job.assignees.joinToString(", ") { it.name }
            },
            clientName = stored.clientName.ifBlank { job.row.clientName },
        )
        if (base.closed) return base

        val fresh = job.protocol
        val extra = base.items.filter { saved -> fresh.none { it.id == saved.id } }
        // Pytanie skasowane w katalogu, na które ktoś już odpowiedział, zostaje:
        // inaczej odpowiedź zniknęłaby z dokumentu bez śladu.
        return base.copy(items = fresh + extra)
    }

    override suspend fun saveProtocol(
        protocol: MontazProtocol,
        close: Boolean,
    ): MontazSaveResult {
        val now = System.currentTimeMillis()
        val ready = if (close && protocol.closedAt == null) {
            protocol.copy(closedAt = isoNow())
        } else {
            protocol
        }
        val form = ready.toForm()

        return try {
            api.saveProtocol(
                ready.installationId,
                ProtocolSaveRequest(formData = form, signature = ready.signature),
            )
            dao.deleteMutation(ready.installationId, KIND_PROTOCOL)
            dao.upsertProtocol(ready.toEntity(json, pendingSince = null, now = now))
            if (close) closeMontaz(ready.installationId)
            MontazSaveResult.SENT
        } catch (_: IOException) {
            dao.upsertProtocol(ready.toEntity(json, pendingSince = now, now = now))
            enqueue(
                targetId = ready.installationId,
                kind = KIND_PROTOCOL,
                payload = buildJsonObject {
                    put("formData", form)
                    put("signature", ready.signature)
                },
            )
            if (close) closeMontaz(ready.installationId)
            MontazSaveResult.QUEUED
        } catch (e: HttpException) {
            // Odmowa serwera nie może skasować pracy z budowy: zostaje wersja
            // robocza w telefonie, a ekran pokazuje, co powiedział serwer.
            dao.upsertProtocol(ready.toEntity(json, pendingSince = now, now = now))
            throw e
        }
    }

    /**
     * Domknięcie montażu po zamknięciu protokołu.
     *
     * Serwer przepuszcza „gotowy" dopiero z „w toku", a ekipa nie zawsze klika
     * „Wyjeżdżamy" — bywa, że telefon wyjmuje się dopiero przy podpisie. Wtedy
     * przechodzimy przez oba stopnie. Odmowa serwera NIE może wywrócić zapisu
     * protokołu: podpis klienta jest już zebrany i to on jest tu dokumentem,
     * a status poprawi koordynator jednym kliknięciem w panelu.
     */
    private suspend fun closeMontaz(installationId: String) {
        runCatching { setStatus(installationId, MontazStatus.DONE) }
            .onFailure {
                runCatching {
                    setStatus(installationId, MontazStatus.IN_PROGRESS)
                    setStatus(installationId, MontazStatus.DONE)
                }
            }
    }

    // ── Drobiazgi ─────────────────────────────────────────────────────────────

    private suspend fun enqueue(targetId: String, kind: String, payload: JsonObject) {
        dao.upsertMutation(
            MontazMutationEntity(
                targetId = targetId,
                kind = kind,
                payload = payload.toString(),
                dealId = dao.getJob(targetId)?.dealId.orEmpty(),
                installationId = targetId,
                createdAt = System.currentTimeMillis(),
            ),
        )
        // Warunek sieci zdejmuje z nas odpytywanie — system sam obudzi
        // robotnika, gdy telefon wróci w zasięg.
        syncScheduler.scheduleSync()
    }

    /** Odświeżenie teczki po udanym zapisie — żeby karta pokazała nowy stan. */
    private suspend fun refreshJob(installationId: String) {
        runCatching { api.getJob(installationId) }
            .onSuccess { dao.upsertJob(it.toEntity(json, System.currentTimeMillis())) }
    }

    private fun readError(e: Throwable): String = when ((e as? HttpException)?.code()) {
        403 -> "Nie masz dostępu do montaży (`installation.view`)."
        else -> "Nie udało się wczytać montaży."
    }
}
