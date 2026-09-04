package com.ekotak.teamtalk.data.repository

import com.ekotak.teamtalk.data.local.dao.ServiceDao
import com.ekotak.teamtalk.data.local.dao.ServiceMutationDao
import com.ekotak.teamtalk.data.local.entity.ServiceJobEntity
import com.ekotak.teamtalk.data.local.entity.ServiceMutationEntity
import com.ekotak.teamtalk.data.local.entity.ServiceMutationEntity.Companion.FIELD_CREATE
import com.ekotak.teamtalk.data.local.entity.ServiceMutationEntity.Companion.LOCAL_ID_PREFIX
import com.ekotak.teamtalk.data.mapper.applyPatch
import com.ekotak.teamtalk.data.mapper.toDomain
import com.ekotak.teamtalk.data.mapper.toEntity
import com.ekotak.teamtalk.data.mapper.toServiceClientEntity
import com.ekotak.teamtalk.data.mapper.toServiceTechnicianEntity
import com.ekotak.teamtalk.data.remote.api.TeamTalkApi
import com.ekotak.teamtalk.data.remote.dto.ServiceJobCreateDto
import com.ekotak.teamtalk.data.remote.dto.WarrantyCardCreateDto
import com.ekotak.teamtalk.data.remote.dto.buildServiceJobPatch
import com.ekotak.teamtalk.data.remote.dto.buildWarrantyCardPatch
import com.ekotak.teamtalk.data.remote.dto.buildWarrantyInspection
import com.ekotak.teamtalk.data.sync.ServiceSyncScheduler
import com.ekotak.teamtalk.domain.model.ServiceJob
import com.ekotak.teamtalk.domain.model.ServiceJobDraft
import com.ekotak.teamtalk.domain.model.ServiceJobPatch
import com.ekotak.teamtalk.domain.model.ServiceJobStatus
import com.ekotak.teamtalk.domain.model.WarrantyCard
import com.ekotak.teamtalk.domain.model.WarrantyCardDraft
import com.ekotak.teamtalk.domain.model.WarrantyCardPatch
import com.ekotak.teamtalk.domain.model.WarrantyInspectionUpsert
import com.ekotak.teamtalk.domain.repository.ServiceRepository
import com.ekotak.teamtalk.domain.repository.ServiceSnapshot
import com.ekotak.teamtalk.domain.repository.ServiceSyncResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Moduł Serwis — mobilny odpowiednik `web/src/app/app/service/page.tsx`.
 *
 * Źródłem prawdy dla ekranu jest Room: lista otwiera się w kotłowni bez zasięgu,
 * a sieć tylko dolewa świeże dane. Zapis idzie wprost do API, a gdy sieci nie ma
 * — do kolejki i do cache, żeby serwisant zobaczył swoją decyzję od razu.
 *
 * Rozróżnienie awarii jest tu istotne: brak łączności (`IOException`) da się
 * nadrobić później, odmowa serwera (403/404/422) nie — taką zmianę puszczamy
 * dalej jako błąd, zamiast wozić ją w kółko po kolejce.
 *
 * Karty gwarancyjne są miękkie: 403 na `/warranty-cards` (brak migracji albo
 * uprawnienia) daje `warrantyAvailable = false` i komunikat, jak w panelu.
 * Kolejka offline obejmuje na razie zlecenia — karty edytuje się w biurze,
 * a nie na dachu.
 */
@Singleton
class ServiceRepositoryImpl @Inject constructor(
    private val api: TeamTalkApi,
    private val dao: ServiceDao,
    private val mutationDao: ServiceMutationDao,
    private val syncScheduler: ServiceSyncScheduler,
) : ServiceRepository {

    private val json = Json

    /** Czy ostatnie pobranie kart się udało — poza Room, bo to stan sesji. */
    private val warrantyAvailable = MutableStateFlow(true)
    private val syncedAt = MutableStateFlow<Long?>(null)

    override fun observe(): Flow<ServiceSnapshot> = combine(
        dao.observeJobs(),
        dao.observeCards(),
        dao.observeClients(),
        dao.observeTechnicians(),
        mutationDao.observePendingJobIds(),
    ) { jobs, cards, clients, technicians, pendingIds ->
        val pending = pendingIds.toSet()
        ServiceSnapshot(
            jobs = jobs.map { it.toDomain(pendingSync = it.id in pending) },
            cards = cards.map { it.toDomain() },
            clients = clients.associate { it.id to it.toDomain() },
            technicians = technicians.map { it.toDomain() }
                .sortedBy { it.displayName.lowercase() },
            warrantyAvailable = warrantyAvailable.value,
            syncedAt = syncedAt.value ?: jobs.maxOfOrNull { it.syncedAt },
        )
    }

    override suspend fun refresh() = coroutineScope {
        val jobsAsync = async { api.getServiceJobs() }
        val clientsAsync = softAsync { api.getClients() }
        val techniciansAsync = softAsync { api.getTechnicians() }
        // Osobno, bo brak odpowiedzi NIE jest tu równoznaczny z pustą listą:
        // panel odróżnia „zero kart" od „modułu kart jeszcze nie ma".
        val cardsAsync = async { runCatching { api.getWarrantyCards() } }

        val now = System.currentTimeMillis()
        val jobs = jobsAsync.await()
        val cards = cardsAsync.await()

        // Znaczniki ustawiamy PRZED zapisem do Room: każdy zapis budzi strumień
        // `observe()`, a ten czyta je bezpośrednio — ustawione po zapisie
        // trafiłyby dopiero do następnej emisji.
        warrantyAvailable.value = cards.isSuccess
        syncedAt.value = now

        dao.replaceJobs(jobs.map { it.toEntity(now) })
        if (cards.isSuccess) dao.replaceCards(cards.getOrThrow().map { it.toEntity(now) })
        dao.replaceDirectory(
            clients = clientsAsync.await().map { it.toServiceClientEntity() },
            technicians = techniciansAsync.await().map { it.toServiceTechnicianEntity() },
        )
    }

    // ── Zlecenia ─────────────────────────────────────────────────────────────

    override suspend fun createJob(draft: ServiceJobDraft): ServiceJob {
        val body = draft.toDto()
        val now = System.currentTimeMillis()
        return try {
            val entity = api.createServiceJob(body).toEntity(now)
            dao.upsertJob(entity)
            entity.toDomain(pendingSync = false)
        } catch (e: IOException) {
            // Awaria spisana bez zasięgu to główny scenariusz terenowy — wiersz
            // dostaje lokalne id i czeka w kolejce (ustalenie 2026-09-02).
            val localId = LOCAL_ID_PREFIX + UUID.randomUUID()
            val entity = draft.toLocalEntity(localId, now)
            dao.upsertJob(entity)
            mutationDao.upsertAll(
                listOf(
                    ServiceMutationEntity(
                        jobId = localId,
                        field = FIELD_CREATE,
                        payload = json.encodeToString(ServiceJobCreateDto.serializer(), body),
                        createdAt = now,
                    ),
                ),
            )
            syncScheduler.scheduleSync()
            entity.toDomain(pendingSync = true)
        }
    }

    override suspend fun updateJob(id: String, patch: ServiceJobPatch): ServiceJob {
        val body = buildServiceJobPatch(patch)
        // Zlecenie, którego serwer jeszcze nie zna, można zmieniać tylko lokalnie.
        if (id.startsWith(LOCAL_ID_PREFIX)) {
            return enqueue(id, body, patch) ?: error("Brak zlecenia $id w cache.")
        }
        return try {
            val entity = api.updateServiceJob(id, body).toEntity(System.currentTimeMillis())
            // Pola, które właśnie poszły, nie mają po co czekać w kolejce.
            mutationDao.delete(id, body.keys.toList())
            dao.upsertJob(entity)
            entity.toDomain(pendingSync = mutationDao.getForJob(id).isNotEmpty())
        } catch (e: IOException) {
            enqueue(id, body, patch) ?: throw e
        }
    }

    /**
     * Kolejkuje zmianę i nakłada ją na cache. Zwraca `null`, gdy zlecenia nie ma
     * lokalnie — wtedy nie ma czego pokazać, więc niech zawoła pierwotny błąd sieci.
     */
    private suspend fun enqueue(
        id: String,
        body: JsonObject,
        patch: ServiceJobPatch,
    ): ServiceJob? {
        val cached = dao.getJob(id) ?: return null
        val now = System.currentTimeMillis()
        mutationDao.upsertAll(
            body.map { (field, value) ->
                ServiceMutationEntity(
                    jobId = id,
                    field = field,
                    payload = JsonObject(mapOf(field to value)).toString(),
                    createdAt = now,
                )
            },
        )
        val local = cached.applyPatch(patch)
        dao.upsertJob(local)
        syncScheduler.scheduleSync()
        return local.toDomain(pendingSync = true)
    }

    override suspend fun completeJob(id: String, from: ServiceJobStatus): ServiceJob {
        // Maszyna statusów board360: new → in_progress → done. Zamiast łamać
        // regułę po stronie API robimy dwa legalne kroki, tak jak panel.
        if (from == ServiceJobStatus.NEW) {
            updateJob(id, ServiceJobPatch(status = edit(ServiceJobStatus.IN_PROGRESS)))
        }
        return updateJob(id, ServiceJobPatch(status = edit(ServiceJobStatus.DONE)))
    }

    // ── Karty gwarancyjne ────────────────────────────────────────────────────

    override suspend fun createCard(draft: WarrantyCardDraft): WarrantyCard {
        val entity = api.createWarrantyCard(
            WarrantyCardCreateDto(
                name = draft.name,
                brand = draft.brand,
                location = draft.location,
                commissionedAt = draft.commissionedAt,
                status = draft.status.wire,
                outdoorModel = draft.outdoorModel,
                outdoorSerial = draft.outdoorSerial,
                indoorModel = draft.indoorModel,
                indoorSerial = draft.indoorSerial,
                note = draft.note,
            ),
        ).toEntity(System.currentTimeMillis())
        dao.upsertCard(entity)
        return entity.toDomain()
    }

    override suspend fun updateCard(id: String, patch: WarrantyCardPatch): WarrantyCard {
        val entity = api.updateWarrantyCard(id, buildWarrantyCardPatch(patch))
            .toEntity(System.currentTimeMillis())
        dao.upsertCard(entity)
        return entity.toDomain()
    }

    override suspend fun upsertInspection(
        cardId: String,
        input: WarrantyInspectionUpsert,
    ): WarrantyCard {
        val entity = api.upsertWarrantyInspection(cardId, buildWarrantyInspection(input))
            .toEntity(System.currentTimeMillis())
        dao.upsertCard(entity)
        return entity.toDomain()
    }

    // ── Kolejka ──────────────────────────────────────────────────────────────

    override suspend fun syncPendingMutations(): ServiceSyncResult {
        val queue = mutationDao.getAll()
        if (queue.isEmpty()) return ServiceSyncResult.DONE

        // Grupujemy po zleceniu: tworzenie musi pójść przed zmianami pól, a po
        // nim reszta kolejki dostaje identyfikator nadany przez serwer.
        for ((jobId, entries) in queue.groupBy { it.jobId }) {
            val create = entries.firstOrNull { it.field == FIELD_CREATE }
            var targetId = jobId
            if (create != null) {
                val body = runCatching {
                    json.decodeFromString(ServiceJobCreateDto.serializer(), create.payload)
                }.getOrNull()
                if (body == null) {
                    // Nieczytelny wpis nigdy się nie wyśle — kasujemy, żeby nie
                    // blokował reszty kolejki.
                    mutationDao.delete(jobId, listOf(FIELD_CREATE))
                    continue
                }
                val created = try {
                    api.createServiceJob(body)
                } catch (e: IOException) {
                    return ServiceSyncResult.RETRY
                } catch (e: Exception) {
                    // Serwer odmówił — zgłoszenia nie da się wysłać nigdy.
                    // Zostawiamy wiersz w cache i zdejmujemy je z kolejki.
                    mutationDao.deleteForJob(jobId)
                    continue
                }
                targetId = created.id
                dao.deleteJob(jobId)
                dao.upsertJob(created.toEntity(System.currentTimeMillis()))
                mutationDao.delete(jobId, listOf(FIELD_CREATE))
                mutationDao.rekeyJob(jobId, targetId)
            }

            val patches = entries.filter { it.field != FIELD_CREATE }
            if (patches.isEmpty()) continue
            val merged = buildMergedPatch(patches)
            try {
                val updated = api.updateServiceJob(targetId, merged)
                dao.upsertJob(updated.toEntity(System.currentTimeMillis()))
                mutationDao.delete(targetId, merged.keys.toList())
            } catch (e: IOException) {
                return ServiceSyncResult.RETRY
            } catch (e: Exception) {
                // Odmowa serwera: zmiany nie da się wysłać nigdy. Zdejmujemy ją
                // z kolejki — inaczej blokowałaby wszystko, co za nią stoi.
                mutationDao.delete(targetId, merged.keys.toList())
            }
        }
        return ServiceSyncResult.DONE
    }

    /** Scala zakolejkowane pola jednego zlecenia w jedno ciało `PATCH`. */
    private fun buildMergedPatch(entries: List<ServiceMutationEntity>): JsonObject {
        val fields = LinkedHashMap<String, kotlinx.serialization.json.JsonElement>()
        for (entry in entries.sortedBy { it.createdAt }) {
            val parsed = runCatching { json.parseToJsonElement(entry.payload) as? JsonObject }
                .getOrNull() ?: continue
            fields.putAll(parsed)
        }
        return JsonObject(fields)
    }

    /** Źródło miękkie: brak dostępu albo awaria → pusta lista, nie błąd modułu. */
    private fun <T> CoroutineScope.softAsync(
        block: suspend () -> List<T>,
    ): Deferred<List<T>> = async { runCatching { block() }.getOrDefault(emptyList()) }
}

/** Skrót na opakowanie wartości w [com.ekotak.teamtalk.domain.model.Edit]. */
private fun <T> edit(value: T) = com.ekotak.teamtalk.domain.model.Edit(value)

private fun ServiceJobDraft.toDto(): ServiceJobCreateDto = ServiceJobCreateDto(
    type = type.wire,
    clientId = clientId,
    technicianId = technicianId,
    scheduledAt = scheduledAt,
    note = note,
    priority = priority?.wire,
    slaHours = slaHours,
)

/**
 * Wiersz cache dla zgłoszenia zapisanego bez zasięgu. SLA zostaje puste — okno
 * liczy serwer od momentu przyjęcia zgłoszenia, więc dopisywanie go tutaj
 * pokazywałoby serwisantowi termin, którego jeszcze nie ma.
 */
private fun ServiceJobDraft.toLocalEntity(localId: String, now: Long): ServiceJobEntity =
    ServiceJobEntity(
        id = localId,
        clientId = clientId,
        dealId = null,
        type = type.wire,
        status = ServiceJobStatus.NEW.wire,
        priority = (priority ?: com.ekotak.teamtalk.domain.model.ServiceJobPriority.NORMAL).wire,
        technicianId = technicianId,
        scheduledAt = scheduledAt,
        note = note,
        slaHours = slaHours,
        slaDueAt = null,
        slaBreached = false,
        localOnly = true,
        syncedAt = now,
    )
