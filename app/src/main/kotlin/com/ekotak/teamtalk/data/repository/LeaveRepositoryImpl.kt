package com.ekotak.teamtalk.data.repository

import com.ekotak.teamtalk.data.local.dao.LeaveDao
import com.ekotak.teamtalk.data.local.entity.LeaveMutationEntity
import com.ekotak.teamtalk.data.local.entity.LeaveMutationEntity.Companion.KIND_CANCEL
import com.ekotak.teamtalk.data.local.entity.LeaveMutationEntity.Companion.KIND_CREATE
import com.ekotak.teamtalk.data.local.entity.LeaveMutationEntity.Companion.KIND_DECISION
import com.ekotak.teamtalk.data.local.entity.LeaveMutationEntity.Companion.KIND_UPDATE
import com.ekotak.teamtalk.data.local.entity.LeaveMutationEntity.Companion.LOCAL_ID_PREFIX
import com.ekotak.teamtalk.data.local.entity.LeaveRequestEntity
import com.ekotak.teamtalk.data.local.preferences.SessionPreferences
import com.ekotak.teamtalk.data.mapper.toBalanceEntity
import com.ekotak.teamtalk.data.mapper.toDomain
import com.ekotak.teamtalk.data.mapper.toDto
import com.ekotak.teamtalk.data.mapper.toEntity
import com.ekotak.teamtalk.data.mapper.toLocalEntity
import com.ekotak.teamtalk.data.mapper.withDraft
import com.ekotak.teamtalk.data.remote.api.TeamTalkApi
import com.ekotak.teamtalk.data.remote.dto.LeaveCreateDto
import com.ekotak.teamtalk.data.remote.dto.LeaveDecisionDto
import com.ekotak.teamtalk.data.sync.LeaveSyncScheduler
import com.ekotak.teamtalk.domain.model.LeaveDraft
import com.ekotak.teamtalk.domain.model.LeaveOverlapException
import com.ekotak.teamtalk.domain.model.LeaveRequest
import com.ekotak.teamtalk.domain.model.LeaveStatus
import com.ekotak.teamtalk.domain.model.LeaveTypeNotAllowedException
import com.ekotak.teamtalk.domain.repository.LeaveRepository
import com.ekotak.teamtalk.domain.repository.LeaveSnapshot
import com.ekotak.teamtalk.domain.repository.LeaveSyncRejection
import com.ekotak.teamtalk.domain.repository.LeaveSyncResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import java.io.IOException
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Moduł Urlop — mobilny odpowiednik zakładki „Urlop" z `web/src/app/app/hr`.
 *
 * Źródłem prawdy dla ekranu jest Room: liczniki i kalendarz otwierają się
 * w busie bez zasięgu, a sieć tylko dolewa świeże dane. Zapis idzie wprost do
 * API, a gdy sieci nie ma — do kolejki i do cache, żeby wniosek był widoczny od
 * razu (ustalenie 2026-09-06, makieta `design/mockups/modul-urlop.html`).
 *
 * Trzy odpowiedzi serwera rozróżniamy, bo ekran reaguje na nie inaczej:
 *  • `409` z `conflictIds` — okres nachodzi na inny wniosek; człowiek ma zmienić
 *    istniejący zamiast zakładać drugi, więc dostaje własny typ wyjątku,
 *  • `422` — rodzaj urlopu niedostępny przy tej umowie (poza umową o pracę
 *    zostaje sam bezpłatny),
 *  • `IOException` — brak zasięgu, czyli jedyny przypadek, który kolejkujemy.
 *
 * Wniosek złożony bez zasięgu żyje pod lokalnym identyfikatorem (`local:…`)
 * i ma w kolejce WYŁĄCZNIE wpis [KIND_CREATE]: jego edycja nadpisuje to, co ma
 * polecieć, a anulowanie po prostu kasuje wiersz razem z kolejką. Dzięki temu
 * wysyłka nie musi układać kolejności zdarzeń dla czegoś, o czym serwer
 * jeszcze nie wie.
 */
@Singleton
class LeaveRepositoryImpl @Inject constructor(
    private val api: TeamTalkApi,
    private val dao: LeaveDao,
    private val sessionPreferences: SessionPreferences,
    private val syncScheduler: LeaveSyncScheduler,
) : LeaveRepository {

    private val json = Json
    private val syncedAt = MutableStateFlow<Long?>(null)

    override fun observe(): Flow<LeaveSnapshot> = combine(
        combine(
            dao.observeBalance(LocalDate.now().year),
            dao.observeMyRequests(),
            dao.observeInboxRequests(),
            dao.observePendingIds(),
            ::snapshotOf,
        ),
        dao.observeAbsences(),
        syncedAt,
    ) { snapshot, absences, at ->
        snapshot.copy(absences = absences.map { it.toDomain() }, syncedAt = at)
    }

    private fun snapshotOf(
        balance: com.ekotak.teamtalk.data.local.entity.LeaveBalanceEntity?,
        mine: List<com.ekotak.teamtalk.data.local.entity.LeaveRequestEntity>,
        inbox: List<com.ekotak.teamtalk.data.local.entity.LeaveRequestEntity>,
        pendingIds: List<String>,
    ): LeaveSnapshot {
        val pending = pendingIds.toSet()
        return LeaveSnapshot(
            balance = balance?.toDomain(),
            myRequests = mine.map { it.toDomain(pendingSync = it.id in pending) },
            inbox = inbox.map { it.toDomain(pendingSync = it.id in pending) },
        )
    }

    override suspend fun refresh() {
        // Najpierw kolejka: gdyby odpowiedź serwera trafiła do cache przed
        // wysłaniem tego, co czeka, wniosek złożony w busie zniknąłby z ekranu.
        runCatching { syncPendingMutations() }

        val now = System.currentTimeMillis()
        val dashboard = api.getHrDashboard()
        dao.upsertBalance(dashboard.toBalanceEntity(now))
        dao.replaceRequests(mine = true, requests = dashboard.requests.map { it.toEntity(mine = true, syncedAt = now) })

        // Skrzynka i nieobecności są MIĘKKIE: obie trasy dopisujemy do board360
        // dopiero teraz, a aplikacja chodzi też przeciwko starszemu API. Brak
        // trasy (404) albo brak uprawnienia nie może wywalić własnego urlopu —
        // po prostu zakładka „Zespół" zostaje pusta.
        runCatching { api.getLeaveInbox() }
            .onSuccess { inbox ->
                dao.replaceRequests(mine = false, requests = inbox.requests.map { it.toEntity(now) })
            }
        runCatching { api.getLeaveAbsences() }
            .onSuccess { list -> dao.replaceAbsences(list.map { it.toEntity(now) }) }

        syncedAt.value = now
    }

    override suspend fun submit(draft: LeaveDraft): LeaveRequest {
        val body = draft.toDto()
        val now = System.currentTimeMillis()
        return try {
            val entity = api.createLeaveRequest(body).toEntity(mine = true, syncedAt = now)
            dao.upsertRequest(entity)
            entity.toDomain()
        } catch (e: HttpException) {
            throw e.asLeaveFailure()
        } catch (e: IOException) {
            // Urlop planuje się wieczorem w domu albo w drodze na budowę —
            // brak zasięgu nie może kasować decyzji (ustalenie 2026-09-06).
            val localId = LOCAL_ID_PREFIX + UUID.randomUUID()
            val userId = sessionPreferences.session.first()?.userId.orEmpty()
            val entity = draft.toLocalEntity(localId, userId, now)
            dao.upsertRequest(entity)
            enqueue(localId, KIND_CREATE, body, now)
            entity.toDomain(pendingSync = true)
        }
    }

    override suspend fun update(id: String, draft: LeaveDraft): LeaveRequest {
        val body = draft.toDto()
        val now = System.currentTimeMillis()

        // Wniosku, którego serwer jeszcze nie zna, nie ma po co wysyłać dwa
        // razy — podmieniamy to, co czeka w kolejce, i poleci już poprawiony.
        if (id.startsWith(LOCAL_ID_PREFIX)) {
            val cached = dao.getRequest(id) ?: error("Brak wniosku $id w cache.")
            val updated = cached.withDraft(draft, now)
            dao.upsertRequest(updated)
            enqueue(id, KIND_CREATE, body, now)
            return updated.toDomain(pendingSync = true)
        }

        return try {
            val entity = api.updateLeaveRequest(id, body).toEntity(mine = true, syncedAt = now)
            dao.upsertRequest(entity)
            dao.deleteMutation(id, KIND_UPDATE)
            entity.toDomain()
        } catch (e: HttpException) {
            throw e.asLeaveFailure()
        } catch (e: IOException) {
            val cached = dao.getRequest(id) ?: throw e
            val updated = cached.withDraft(draft, now)
            dao.upsertRequest(updated)
            enqueue(id, KIND_UPDATE, body, now)
            updated.toDomain(pendingSync = true)
        }
    }

    override suspend fun cancel(id: String): LeaveRequest {
        val now = System.currentTimeMillis()

        // Anulowanie wniosku, który jeszcze nie poleciał, to po prostu jego
        // wycofanie z kolejki — serwer nigdy się o nim nie dowie.
        if (id.startsWith(LOCAL_ID_PREFIX)) {
            val cached = dao.getRequest(id) ?: error("Brak wniosku $id w cache.")
            dao.deleteMutationsFor(id)
            dao.deleteRequest(id)
            return cached.toDomain().copy(status = LeaveStatus.ANULOWANY)
        }

        return try {
            val entity = api.cancelLeaveRequest(id).toEntity(mine = true, syncedAt = now)
            dao.upsertRequest(entity)
            dao.deleteMutation(id, KIND_CANCEL)
            entity.toDomain()
        } catch (e: HttpException) {
            throw e.asLeaveFailure()
        } catch (e: IOException) {
            val cached = dao.getRequest(id) ?: throw e
            val cancelled = cached.copy(status = LeaveStatus.ANULOWANY.wire, syncedAt = now)
            dao.upsertRequest(cancelled)
            enqueue(id, KIND_CANCEL, body = null, now = now)
            cancelled.toDomain(pendingSync = true)
        }
    }

    override suspend fun decide(id: String, approve: Boolean, note: String?): LeaveRequest {
        val now = System.currentTimeMillis()
        val body = LeaveDecisionDto(
            status = if (approve) LeaveStatus.ZATWIERDZONY.wire else LeaveStatus.ODRZUCONY.wire,
            decisionNote = note?.takeIf { it.isNotBlank() },
        )
        return try {
            val entity = api.decideLeaveRequest(id, body).toEntity(mine = false, syncedAt = now)
            // Odpowiedź serwera nie niesie pól skrzynki (rola, decyzyjność) —
            // przepisujemy je z cache, żeby wiersz nie zgubił swojego kontekstu.
            val merged = dao.getRequest(id)?.let { cached ->
                entity.copy(
                    employeeRole = cached.employeeRole,
                    canDecide = false,
                    awaitingName = cached.awaitingName,
                    awaitingIsBackup = cached.awaitingIsBackup,
                )
            } ?: entity
            dao.upsertRequest(merged)
            dao.deleteMutation(id, KIND_DECISION)
            merged.toDomain()
        } catch (e: HttpException) {
            throw e.asLeaveFailure()
        } catch (e: IOException) {
            val cached = dao.getRequest(id) ?: throw e
            val decided = cached.copy(
                status = if (approve) LeaveStatus.ZATWIERDZONY.wire else LeaveStatus.ODRZUCONY.wire,
                decisionNote = note?.takeIf { it.isNotBlank() },
                canDecide = false,
                syncedAt = now,
            )
            dao.upsertRequest(decided)
            dao.upsertMutation(
                LeaveMutationEntity(
                    targetId = id,
                    kind = KIND_DECISION,
                    payload = json.encodeToString(LeaveDecisionDto.serializer(), body),
                    createdAt = now,
                ),
            )
            syncScheduler.scheduleSync()
            decided.toDomain(pendingSync = true)
        }
    }

    /**
     * Opróżnia kolejkę w kolejności zapisu.
     *
     * Brak zasięgu przerywa przebieg — reszta poczeka na kolejne obudzenie.
     * Odmowa serwera (4xx) zdejmuje wpis z kolejki, bo wożenie go w kółko
     * niczego nie naprawi; wraca za to w [LeaveSyncResult.rejected], żeby
     * ktoś powiedział o tym człowiekowi. Cichy zanik wniosku byłby najgorszym
     * możliwym zachowaniem: ludzie planują wtedy urlop, którego nie mają.
     */
    override suspend fun syncPendingMutations(): LeaveSyncResult {
        var sent = 0
        val rejected = mutableListOf<LeaveSyncRejection>()
        for (mutation in dao.getMutations()) {
            try {
                when (mutation.kind) {
                    KIND_CREATE -> {
                        val body = json.decodeFromString(LeaveCreateDto.serializer(), mutation.payload)
                        val created = api.createLeaveRequest(body)
                        dao.deleteMutationsFor(mutation.targetId)
                        dao.deleteRequest(mutation.targetId)
                        dao.upsertRequest(created.toEntity(mine = true, syncedAt = System.currentTimeMillis()))
                    }
                    KIND_UPDATE -> {
                        val body = json.decodeFromString(LeaveCreateDto.serializer(), mutation.payload)
                        val updated = api.updateLeaveRequest(mutation.targetId, body)
                        dao.deleteMutation(mutation.targetId, KIND_UPDATE)
                        dao.upsertRequest(updated.toEntity(mine = true, syncedAt = System.currentTimeMillis()))
                    }
                    KIND_CANCEL -> {
                        val cancelled = api.cancelLeaveRequest(mutation.targetId)
                        dao.deleteMutation(mutation.targetId, KIND_CANCEL)
                        dao.upsertRequest(cancelled.toEntity(mine = true, syncedAt = System.currentTimeMillis()))
                    }
                    KIND_DECISION -> {
                        val body = json.decodeFromString(LeaveDecisionDto.serializer(), mutation.payload)
                        val decided = api.decideLeaveRequest(mutation.targetId, body)
                        dao.deleteMutation(mutation.targetId, KIND_DECISION)
                        // Decyzja dotyczy CUDZEGO wniosku — zostaje w skrzynce.
                        val cached = dao.getRequest(mutation.targetId)
                        dao.upsertRequest(
                            decided.toEntity(mine = false, syncedAt = System.currentTimeMillis()).copy(
                                employeeRole = cached?.employeeRole,
                                canDecide = false,
                                awaitingName = cached?.awaitingName,
                                awaitingIsBackup = cached?.awaitingIsBackup ?: false,
                            ),
                        )
                    }
                    else -> dao.deleteMutation(mutation.targetId, mutation.kind)
                }
                sent++
            } catch (_: IOException) {
                // Nadal bez zasięgu — reszta kolejki poczeka na następny raz.
                return LeaveSyncResult(sent = sent, rejected = rejected, incomplete = true)
            } catch (e: HttpException) {
                val cached = dao.getRequest(mutation.targetId)
                rejected += LeaveSyncRejection(
                    label = cached?.let { rejectionLabel(it, mutation.kind) } ?: "Zmiana wniosku",
                    reason = e.serverMessage(),
                )
                if (mutation.kind == KIND_CREATE) {
                    // Wniosek odrzucony przy wysyłce: kasujemy lokalny wiersz,
                    // żeby ekran nie pokazywał urlopu, którego serwer nie przyjął.
                    dao.deleteMutationsFor(mutation.targetId)
                    dao.deleteRequest(mutation.targetId)
                } else {
                    // Zmiana, decyzja i anulowanie dotyczą wniosku, który na
                    // serwerze istnieje — najbliższe odświeżenie przywróci jego
                    // prawdziwy stan, więc kasujemy sam wpis kolejki.
                    dao.deleteMutation(mutation.targetId, mutation.kind)
                }
            }
        }
        return LeaveSyncResult(sent = sent, rejected = rejected)
    }

    /** Opis odrzuconego zapisu — tyle, żeby człowiek poznał, o który urlop chodzi. */
    private fun rejectionLabel(entity: LeaveRequestEntity, kind: String): String {
        val what = when (kind) {
            KIND_CREATE -> "Wniosek"
            KIND_UPDATE -> "Zmiana wniosku"
            KIND_CANCEL -> "Anulowanie wniosku"
            KIND_DECISION -> "Decyzja o wniosku ${entity.employeeName.orEmpty()}".trim()
            else -> "Zmiana"
        }
        return "$what ${entity.startDate} – ${entity.endDate}"
    }

    /** Komunikat serwera z ciała błędu; bez niego zostaje sam kod odpowiedzi. */
    private fun HttpException.serverMessage(): String? {
        val raw = runCatching { response()?.errorBody()?.string() }.getOrNull() ?: return null
        return runCatching {
            json.parseToJsonElement(raw).jsonObject["message"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()
    }

    /**
     * Dopisuje zapis do kolejki i zamawia jego wysyłkę. Robotnik ma warunek
     * sieci, więc nie odpytuje — system obudzi go sam, gdy wróci zasięg.
     */
    private suspend fun enqueue(targetId: String, kind: String, body: LeaveCreateDto?, now: Long) {
        dao.upsertMutation(
            LeaveMutationEntity(
                targetId = targetId,
                kind = kind,
                payload = body?.let { json.encodeToString(LeaveCreateDto.serializer(), it) }.orEmpty(),
                createdAt = now,
            ),
        )
        syncScheduler.scheduleSync()
    }

    /**
     * Odpowiedź serwera → wyjątek, na który ekran umie odpowiedzieć.
     * `409` niesie `conflictIds` — bez nich nie dałoby się otworzyć edycji
     * wniosku, z którym okres się nakłada.
     */
    private fun HttpException.asLeaveFailure(): Throwable {
        val raw = runCatching { response()?.errorBody()?.string() }.getOrNull()
        val body = raw?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }
        val message = body?.get("message")?.jsonPrimitive?.contentOrNull
        return when (code()) {
            409 -> {
                val ids = body?.get("conflictIds")
                    ?.let { runCatching { it.jsonArray.mapNotNull { id -> id.jsonPrimitive.contentOrNull } }.getOrNull() }
                    .orEmpty()
                LeaveOverlapException(message ?: "Wniosek nakłada się na istniejący urlop.", ids)
            }
            422 -> LeaveTypeNotAllowedException(
                message ?: "Przy tym rodzaju umowy dostępny jest wyłącznie urlop bezpłatny.",
            )
            else -> this
        }
    }
}

/** Wiersz cache, którego szuka ekran przy kolizji — pomocnik dla ViewModelu. */
fun List<LeaveRequest>.firstMatching(ids: List<String>): LeaveRequest? =
    firstOrNull { it.id in ids }

/** Czy wiersz pochodzi z kolejki (nie ma go jeszcze na serwerze). */
val LeaveRequestEntity.isLocal: Boolean get() = id.startsWith(LOCAL_ID_PREFIX)
