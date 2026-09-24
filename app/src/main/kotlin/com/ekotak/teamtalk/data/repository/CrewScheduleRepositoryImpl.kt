package com.ekotak.teamtalk.data.repository

import com.ekotak.teamtalk.data.files.CrewScheduleCacheStore
import com.ekotak.teamtalk.data.local.dao.MontazDao
import com.ekotak.teamtalk.data.local.entity.MontazMutationEntity
import com.ekotak.teamtalk.data.local.entity.MontazMutationEntity.Companion.KIND_PATCH
import com.ekotak.teamtalk.data.local.entity.MontazMutationEntity.Companion.KIND_SCHEDULE_MOVE
import com.ekotak.teamtalk.data.mapper.toDomain
import com.ekotak.teamtalk.data.mapper.toEntity
import com.ekotak.teamtalk.data.mapper.toJson
import com.ekotak.teamtalk.data.mapper.toRequest
import com.ekotak.teamtalk.data.mapper.withPending
import com.ekotak.teamtalk.data.mapper.withPendingMoves
import com.ekotak.teamtalk.data.remote.api.TeamTalkApi
import com.ekotak.teamtalk.data.remote.dto.MyDaysDto
import com.ekotak.teamtalk.data.remote.dto.ScheduleBlockRequest
import com.ekotak.teamtalk.data.remote.dto.ScheduleCrewOrderRequest
import com.ekotak.teamtalk.data.remote.dto.ScheduleWorkdayRequest
import com.ekotak.teamtalk.domain.model.BlockScope
import com.ekotak.teamtalk.domain.model.MyDayBlock
import com.ekotak.teamtalk.domain.model.MyDays
import com.ekotak.teamtalk.domain.model.MyWorkday
import com.ekotak.teamtalk.domain.model.ScheduleBlockInput
import com.ekotak.teamtalk.data.remote.dto.ScheduleDto
import com.ekotak.teamtalk.data.remote.dto.ScheduleMoveRequest
import com.ekotak.teamtalk.data.remote.dto.SchedulePublishRequest
import com.ekotak.teamtalk.data.remote.dto.ScheduleSettingsRequest
import com.ekotak.teamtalk.data.sync.MontazSyncScheduler
import com.ekotak.teamtalk.domain.model.PersonMove
import com.ekotak.teamtalk.domain.model.StagePatch
import com.ekotak.teamtalk.domain.model.orderCrews
import com.ekotak.teamtalk.domain.repository.CrewScheduleRepository
import com.ekotak.teamtalk.domain.repository.CrewScheduleSnapshot
import com.ekotak.teamtalk.domain.repository.MontazRepository
import com.ekotak.teamtalk.domain.repository.PublishOutcome
import com.ekotak.teamtalk.domain.repository.ScheduleCallResult
import com.ekotak.teamtalk.domain.repository.ScheduleSaveResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import java.io.IOException
import java.time.LocalDate
import javax.inject.Inject

/**
 * HARMONOGRAM EKIP — oś z serwera, kopia w pliku i zmiany przez kolejkę montaży.
 *
 * Zmiana etapu to ten sam `PATCH /installations/{id}`, który wysyła zakładka
 * Montaż karty deala, więc bez zasięgu ląduje w TEJ SAMEJ kolejce
 * (`montaz_mutations`, rodzaj `montaz_patch`) i opróżnia ją ten sam procesor
 * (`MontazRepositoryImpl`). Dzięki temu zmiana z osi i zmiana z karty deala
 * tego samego montażu scalają się w jeden wpis, a nie ścigają.
 */
class CrewScheduleRepositoryImpl @Inject constructor(
    private val api: TeamTalkApi,
    private val dao: MontazDao,
    private val cache: CrewScheduleCacheStore,
    private val json: Json,
    private val syncScheduler: MontazSyncScheduler,
    private val montaz: MontazRepository,
) : CrewScheduleRepository {

    override suspend fun load(from: LocalDate, to: LocalDate): CrewScheduleSnapshot {
        var dto: ScheduleDto? = null
        var fromCache = false
        var error: String? = null

        // Kolejność ekip ułożona bez zasięgu idzie PRZED odczytem — wtedy
        // serwer odda już oś w nowym układzie.
        val waitingOrder = cache.readPendingCrewOrder()
        if (waitingOrder != null && sendCrewOrder(waitingOrder) != ScheduleSaveResult.Queued) {
            cache.clearPendingCrewOrder()
        }

        try {
            dto = api.getCrewSchedule(from.toString(), to.toString())
            cache.write(from, to, json.encodeToString(ScheduleDto.serializer(), dto))
        } catch (e: IOException) {
            dto = cached(from, to)
            fromCache = true
        } catch (e: HttpException) {
            if (e.code() == 403) {
                return CrewScheduleSnapshot(null, false, forbidden = true, error = null, pendingCount = 0)
            }
            error = e.serverMessage() ?: "Nie udało się wczytać harmonogramu (kod ${e.code()})."
            dto = cached(from, to)
            fromCache = dto != null
        } catch (e: Exception) {
            error = "Nie udało się odczytać harmonogramu."
            dto = cached(from, to)
            fromCache = dto != null
        }

        val pending = pendingPatches()
        val moves = pendingMoves()
        val order = cache.readPendingCrewOrder()
        return CrewScheduleSnapshot(
            schedule = dto?.toDomain()?.withPending(pending)?.withPendingMoves(moves)?.let { sch ->
                if (order == null) sch else sch.copy(crews = orderCrews(sch.crews, order))
            },
            fromCache = fromCache,
            forbidden = false,
            error = error,
            pendingCount = pending.size + moves.size,
        )
    }

    private suspend fun cached(from: LocalDate, to: LocalDate): ScheduleDto? =
        cache.read(from, to)?.let { raw ->
            runCatching { json.decodeFromString(ScheduleDto.serializer(), raw) }.getOrNull()
        }

    /** Zmiany montaży czekające w kolejce — po id montażu. */
    private suspend fun pendingPatches(): Map<String, JsonObject> =
        dao.getMutations()
            .filter { it.kind == KIND_PATCH }
            .mapNotNull { row -> parse(row.payload)?.let { row.targetId to it } }
            .toMap()

    /** Przeniesienia osób czekające w kolejce — w kolejności, w jakiej je zrobiono. */
    private suspend fun pendingMoves(): List<PersonMove> =
        dao.getMutations()
            .filter { it.kind == KIND_SCHEDULE_MOVE }
            .mapNotNull { row ->
                runCatching { json.decodeFromString(ScheduleMoveRequest.serializer(), row.payload).toDomain() }.getOrNull()
            }

    override suspend fun movePerson(dealId: String, move: PersonMove): ScheduleSaveResult {
        val body = move.toRequest()
        return try {
            api.moveSchedulePerson(body)
            ScheduleSaveResult.Sent
        } catch (_: IOException) {
            // Ta sama kolejka co zmiany etapów — opróżnia ją `MontazRepositoryImpl`
            // po kolei, więc przeniesienie nie wyprzedzi wcześniejszej zmiany składu.
            dao.upsertMutation(
                MontazMutationEntity(
                    targetId = "${move.toInstallationId}|${move.userId}",
                    kind = KIND_SCHEDULE_MOVE,
                    payload = json.encodeToString(ScheduleMoveRequest.serializer(), body),
                    dealId = dealId,
                    installationId = move.toInstallationId,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            syncScheduler.scheduleSync()
            ScheduleSaveResult.Queued
        } catch (e: HttpException) {
            ScheduleSaveResult.Failed(e.serverMessage() ?: saveError(e.code()))
        }
    }

    override suspend fun patchStage(dealId: String, id: String, patch: StagePatch): ScheduleSaveResult {
        val body = patch.toJson()
        if (body.isEmpty()) return ScheduleSaveResult.Sent

        // Zmiana czekająca w kolejce idzie RAZEM z nową: wysłanie samej nowej
        // i starej po niej cofnęłoby termin, który koordynator właśnie ustawił.
        val waiting = dao.getMutationPayload(id, KIND_PATCH)?.let(::parse)
        val merged = if (waiting == null) body else buildJsonObject {
            waiting.forEach { (k, v) -> put(k, v) }
            body.forEach { (k, v) -> put(k, v) }
        }

        return try {
            val updated = api.updateMontaz(id, merged)
            dao.deleteMutation(id, KIND_PATCH)
            // Zakładka Montaż karty deala czyta ten sam montaż ze swojego cache'u.
            dao.upsertMontaz(updated.toEntity(json, System.currentTimeMillis()))
            ScheduleSaveResult.Sent
        } catch (_: IOException) {
            dao.upsertMutation(
                MontazMutationEntity(
                    targetId = id,
                    kind = KIND_PATCH,
                    payload = merged.toString(),
                    dealId = dealId,
                    installationId = id,
                    createdAt = System.currentTimeMillis(),
                ),
            )
            syncScheduler.scheduleSync()
            ScheduleSaveResult.Queued
        } catch (e: HttpException) {
            ScheduleSaveResult.Failed(e.serverMessage() ?: saveError(e.code()))
        }
    }

    override suspend fun saveCrewOrder(crewIds: List<String>): ScheduleSaveResult {
        val res = sendCrewOrder(crewIds)
        if (res == ScheduleSaveResult.Queued) {
            cache.writePendingCrewOrder(crewIds)
        } else {
            // Nowszy układ zapisany (albo odrzucony) — starszy z kolejki już nie obowiązuje.
            cache.clearPendingCrewOrder()
        }
        return res
    }

    private suspend fun sendCrewOrder(crewIds: List<String>): ScheduleSaveResult = try {
        api.saveScheduleCrewOrder(ScheduleCrewOrderRequest(crewIds))
        ScheduleSaveResult.Sent
    } catch (_: IOException) {
        ScheduleSaveResult.Queued
    } catch (e: HttpException) {
        ScheduleSaveResult.Failed(e.serverMessage() ?: saveError(e.code()))
    }

    override suspend fun publishWeek(weekStart: LocalDate): ScheduleCallResult<PublishOutcome> {
        // Publikujemy to, co widzi koordynator — więc najpierw dosyłamy zmiany
        // z kolejki; inaczej ekipy dostałyby tydzień sprzed przesunięć z telefonu.
        runCatching { montaz.syncPendingMutations() }
        if (dao.getMutations().any { it.kind == KIND_PATCH || it.kind == KIND_SCHEDULE_MOVE }) {
            return ScheduleCallResult.Failed(
                "Najpierw muszą dojść zmiany z telefonu — spróbuj za chwilę, w zasięgu.",
            )
        }
        return call {
            val res = api.publishScheduleWeek(SchedulePublishRequest(weekStart.toString()))
            PublishOutcome(res.published, res.notified, res.calendar)
        }
    }

    override suspend fun setPublishEnabled(enabled: Boolean): ScheduleCallResult<Unit> = call {
        api.setScheduleSettings(ScheduleSettingsRequest(enabled))
        Unit
    }

    override suspend fun planDeal(dealId: String): ScheduleCallResult<Int> = call {
        api.planScheduleDeal(dealId, JsonObject(emptyMap())).created
    }

    override suspend fun setCrewWorkday(
        crewId: String,
        day: LocalDate,
        working: Boolean,
    ): ScheduleCallResult<Unit> = call {
        api.setCrewWorkday(crewId, day.toString(), ScheduleWorkdayRequest(working))
        Unit
    }

    override suspend fun saveBlock(id: String?, input: ScheduleBlockInput): ScheduleCallResult<Unit> = call {
        val body = ScheduleBlockRequest(
            scope = input.scope.wire,
            crewId = input.crewId.takeIf { input.scope == BlockScope.CREW },
            userId = input.userId.takeIf { input.scope == BlockScope.USER },
            start = input.start.toString(),
            end = input.end.toString(),
            reason = input.reason.wire,
            note = input.note?.trim()?.takeIf { it.isNotEmpty() },
        )
        if (id == null) api.createScheduleBlock(body) else api.updateScheduleBlock(id, body)
        Unit
    }

    override suspend fun deleteBlock(id: String): ScheduleCallResult<Unit> = call {
        api.deleteScheduleBlock(id)
        Unit
    }

    override suspend fun restoreBlock(id: String): ScheduleCallResult<Unit> = call {
        api.restoreScheduleBlock(id, JsonObject(emptyMap()))
        Unit
    }

    override suspend fun myDays(from: LocalDate, to: LocalDate): MyDays? {
        val dto = try {
            api.getMyDays(from.toString(), to.toString()).also {
                cache.writeMyDays(json.encodeToString(MyDaysDto.serializer(), it))
            }
        } catch (_: Exception) {
            cache.readMyDays()?.let { raw ->
                runCatching { json.decodeFromString(MyDaysDto.serializer(), raw) }.getOrNull()
            }
        } ?: return null
        return MyDays(
            blocks = dto.blocks.map {
                MyDayBlock(
                    scope = BlockScope.fromWire(it.scope),
                    start = LocalDate.parse(it.start.take(10)),
                    end = LocalDate.parse(it.end.take(10)),
                    label = it.label,
                    crewName = it.crewName,
                )
            },
            workdays = dto.workdays.map { MyWorkday(LocalDate.parse(it.date.take(10)), it.crewName) },
        )
    }

    private inline fun <T> call(block: () -> T): ScheduleCallResult<T> = try {
        ScheduleCallResult.Ok(block())
    } catch (_: IOException) {
        ScheduleCallResult.Offline
    } catch (e: HttpException) {
        ScheduleCallResult.Failed(e.serverMessage() ?: saveError(e.code()))
    }

    private fun parse(raw: String): JsonObject? =
        runCatching { json.parseToJsonElement(raw).jsonObject }.getOrNull()

    /** Komunikat serwera z ciała błędu — panel pokazuje dokładnie ten sam. */
    private fun HttpException.serverMessage(): String? {
        val raw = runCatching { response()?.errorBody()?.string() }.getOrNull() ?: return null
        return runCatching {
            json.parseToJsonElement(raw).jsonObject["message"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()
    }

    private fun saveError(code: Int): String = when (code) {
        403 -> "Harmonogram ekip jest dla koordynatora, zarządu i admina."
        404 -> "Montaż zniknął z panelu — odśwież oś."
        409, 422 -> "Serwer odrzucił zmianę."
        else -> "Nie udało się zapisać (kod $code)."
    }
}
