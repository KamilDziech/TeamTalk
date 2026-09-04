package com.ekotak.teamtalk.data.repository

import com.ekotak.teamtalk.data.local.dao.CalendarDao
import com.ekotak.teamtalk.data.local.dao.CalendarMutationDao
import com.ekotak.teamtalk.data.local.dao.MemberDao
import com.ekotak.teamtalk.data.local.entity.CalendarEntity
import com.ekotak.teamtalk.data.local.entity.CalendarEventEntity
import com.ekotak.teamtalk.data.local.entity.TeamMemberEntity
import com.ekotak.teamtalk.data.local.entity.CalendarMutationEntity
import com.ekotak.teamtalk.data.local.entity.CalendarMutationEntity.Companion.FIELD_CREATE
import com.ekotak.teamtalk.data.local.entity.CalendarMutationEntity.Companion.FIELD_DELETE
import com.ekotak.teamtalk.data.local.entity.CalendarMutationEntity.Companion.FIELD_RSVP
import com.ekotak.teamtalk.data.local.entity.CalendarMutationEntity.Companion.FIELD_SCOPE
import com.ekotak.teamtalk.data.local.entity.CalendarMutationEntity.Companion.LOCAL_ID_PREFIX
import com.ekotak.teamtalk.data.mapper.applyPatch
import com.ekotak.teamtalk.data.mapper.applyRsvp
import com.ekotak.teamtalk.data.mapper.toTeamMemberEntity
import com.ekotak.teamtalk.data.mapper.toDomain
import com.ekotak.teamtalk.data.mapper.toDto
import com.ekotak.teamtalk.data.mapper.toEntity
import com.ekotak.teamtalk.data.mapper.toLocalEntity
import com.ekotak.teamtalk.data.remote.api.TeamTalkApi
import com.ekotak.teamtalk.data.remote.dto.CalendarCreateDto
import com.ekotak.teamtalk.data.remote.dto.CalendarEventCreateDto
import com.ekotak.teamtalk.data.remote.dto.RsvpRequest
import com.ekotak.teamtalk.data.remote.dto.buildCalendarEventPatch
import com.ekotak.teamtalk.data.remote.dto.buildCalendarPatch
import com.ekotak.teamtalk.data.sync.CalendarSyncScheduler
import com.ekotak.teamtalk.domain.model.Calendar
import com.ekotak.teamtalk.domain.model.CalendarConflictException
import com.ekotak.teamtalk.domain.model.CalendarDraft
import com.ekotak.teamtalk.domain.model.CalendarEvent
import com.ekotak.teamtalk.domain.model.CalendarEventDraft
import com.ekotak.teamtalk.domain.model.CalendarEventPatch
import com.ekotak.teamtalk.domain.model.CalendarOverlay
import com.ekotak.teamtalk.domain.model.CalendarPatch
import com.ekotak.teamtalk.domain.model.FreeBusy
import com.ekotak.teamtalk.domain.model.PrivateBusyConflictException
import com.ekotak.teamtalk.domain.model.RecurrenceScope
import com.ekotak.teamtalk.domain.model.RsvpStatus
import com.ekotak.teamtalk.domain.repository.CalendarRepository
import com.ekotak.teamtalk.domain.repository.CalendarSnapshot
import com.ekotak.teamtalk.domain.repository.CalendarSyncResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Moduł Kalendarz — mobilny odpowiednik `web/src/app/app/calendar`.
 *
 * Źródłem prawdy dla ekranu jest Room: obejrzany miesiąc otwiera się w aucie
 * bez zasięgu, a sieć tylko dolewa świeże dane. Zapis idzie wprost do API,
 * a gdy sieci nie ma — do kolejki i do cache, żeby decyzja była widoczna od razu
 * (ustalenie z makiety `design/mockups/modul-kalendarz.html`).
 *
 * Rozróżnienie awarii jest tu istotne: brak łączności (`IOException`) da się
 * nadrobić później, odmowa serwera (403/404/422) nie — taką zmianę puszczamy
 * dalej jako błąd, zamiast wozić ją w kółko po kolejce. Osobno stoi `409`:
 * to nie awaria, tylko kolizja rezerwacji zasobu, na którą człowiek ma
 * odpowiedzieć „zapisz mimo to" — dlatego dostaje własny typ wyjątku.
 *
 * Serie rozwija serwer, więc kolejka offline nie zna reguł powtarzania: wysyła
 * ten sam `scope`, który wybrano przy zapisie, i nic nie liczy sama.
 */
@Singleton
class CalendarRepositoryImpl @Inject constructor(
    private val api: TeamTalkApi,
    private val dao: CalendarDao,
    private val mutationDao: CalendarMutationDao,
    private val memberDao: MemberDao,
    private val syncScheduler: CalendarSyncScheduler,
) : CalendarRepository {

    private val json = Json

    private val syncedAt = MutableStateFlow<Long?>(null)

    /** Czy wolno mi przebić cudzą blokadę zajętości — z `GET /calendar/private-link`. */
    private val canOverrideBusy = MutableStateFlow(false)

    override fun observe(): Flow<CalendarSnapshot> = combine(
        combine(
            dao.observeCalendars(),
            dao.observeEvents(),
            memberDao.observeMembers(),
            mutationDao.observePendingEventIds(),
            mutationDao.observeDeletedEventIds(),
            ::snapshotOf,
        ),
        dao.observeBusy(),
        canOverrideBusy,
    ) { snapshot, busy, canOverride ->
        snapshot.copy(busy = busy.map { it.toDomain() }, canOverrideBusy = canOverride)
    }

    private fun snapshotOf(
        calendars: List<CalendarEntity>,
        events: List<CalendarEventEntity>,
        members: List<TeamMemberEntity>,
        pendingIds: List<String>,
        deletedIds: List<String>,
    ): CalendarSnapshot = run {
        val pending = pendingIds.toSet()
        val deleted = deletedIds.toSet()
        CalendarSnapshot(
            calendars = calendars.map { it.toDomain() },
            // Wydarzenie skasowane bez zasięgu znika z ekranu od razu, choć
            // serwer wciąż je zna — wiersz zostaje w cache do czasu wysyłki.
            events = events.filter { it.id !in deleted }.map { it.toDomain(pendingSync = it.id in pending) },
            members = members.map { it.toDomain() },
            syncedAt = syncedAt.value ?: events.maxOfOrNull { it.syncedAt },
        )
    }

    override suspend fun refresh(fromIso: String, toIso: String) = coroutineScope {
        val calendarsAsync = async { api.getCalendars() }
        val membersAsync = softAsync { api.getTaskMembers() }
        val eventsAsync = async { api.getCalendarEvents(from = fromIso, to = toIso) }
        // Prywatna zajętość i prawo do jej przebicia — źródła MIĘKKIE: starszy
        // backend tych tras nie zna, a moduł ma się wtedy otworzyć normalnie,
        // po prostu bez szarych pól.
        val busyAsync = softAsync { api.getPrivateBusy(from = fromIso, to = toIso) }
        val overrideAsync = async {
            runCatching { api.getPrivateLinkState().canOverrideBusy }.getOrDefault(false)
        }

        val now = System.currentTimeMillis()
        val calendars = calendarsAsync.await()
        val events = eventsAsync.await()
        syncedAt.value = now

        dao.replaceCalendars(calendars.map { it.toEntity(now) })
        dao.replaceRange(fromIso, toIso, events.map { it.toEntity(now) })
        memberDao.replaceMembers(membersAsync.await().map { it.toTeamMemberEntity() })
        dao.replaceBusyRange(fromIso, toIso, busyAsync.await().map { it.toEntity(now) })
        canOverrideBusy.value = overrideAsync.await()
    }

    override suspend fun overlays(fromIso: String, toIso: String): List<CalendarOverlay> =
        api.getCalendarOverlays(from = fromIso, to = toIso).mapNotNull { it.toDomain() }

    override suspend fun freeBusy(
        userIds: List<String>,
        fromIso: String,
        toIso: String,
    ): List<FreeBusy> {
        if (userIds.isEmpty()) return emptyList()
        return api.getFreeBusy(userIds = userIds.joinToString(","), from = fromIso, to = toIso)
            .map { it.toDomain() }
    }

    // ── Wydarzenia ───────────────────────────────────────────────────────────

    override suspend fun createEvent(
        draft: CalendarEventDraft,
        allowConflict: Boolean,
    ): CalendarEvent {
        val body = draft.toDto()
        val now = System.currentTimeMillis()
        return try {
            val entity = api.createCalendarEvent(body, allowConflict.takeIf { it }?.let { "true" })
                .toEntity(now)
            dao.upsertEvent(entity)
            entity.toDomain()
        } catch (e: HttpException) {
            throw e.asConflictOrItself()
        } catch (e: IOException) {
            // Termin ustalony z klientem przy furtce, bez zasięgu — wiersz dostaje
            // lokalne id i czeka w kolejce (ustalenie 2026-09-03).
            val localId = LOCAL_ID_PREFIX + UUID.randomUUID()
            val color = dao.getCalendar(draft.calendarId)?.color
            val entity = draft.toLocalEntity(localId, color, now)
            dao.upsertEvent(entity)
            mutationDao.upsertAll(
                listOf(
                    CalendarMutationEntity(
                        eventId = localId,
                        field = FIELD_CREATE,
                        payload = json.encodeToString(CalendarEventCreateDto.serializer(), body),
                        createdAt = now,
                    ),
                ),
            )
            syncScheduler.scheduleSync()
            entity.toDomain(pendingSync = true)
        }
    }

    override suspend fun updateEvent(
        id: String,
        patch: CalendarEventPatch,
        scope: RecurrenceScope,
        allowConflict: Boolean,
    ): CalendarEvent {
        val body = buildCalendarEventPatch(patch)
        // Wydarzenia, którego serwer jeszcze nie zna, nie ma po co wysyłać —
        // zmieniamy je w kolejce, a poleci już poprawione.
        if (id.startsWith(LOCAL_ID_PREFIX)) {
            return patchLocalDraft(id, patch) ?: error("Brak wydarzenia $id w cache.")
        }
        return try {
            val entity = api.updateCalendarEvent(
                id = id,
                patch = body,
                scope = scope.wire,
                allowConflict = allowConflict.takeIf { it }?.let { "true" },
            ).toEntity(System.currentTimeMillis())
            mutationDao.delete(id, body.keys.toList())
            dao.upsertEvent(entity)
            entity.toDomain(pendingSync = mutationDao.getForEvent(id).isNotEmpty())
        } catch (e: HttpException) {
            throw e.asConflictOrItself()
        } catch (e: IOException) {
            enqueuePatch(id, body, patch, scope) ?: throw e
        }
    }

    /**
     * Kolejkuje zmianę i nakłada ją na cache. Zwraca `null`, gdy wydarzenia nie
     * ma lokalnie — wtedy nie ma czego pokazać, więc niech zawoła pierwotny błąd.
     */
    private suspend fun enqueuePatch(
        id: String,
        body: JsonObject,
        patch: CalendarEventPatch,
        scope: RecurrenceScope,
    ): CalendarEvent? {
        val cached = dao.getEvent(id) ?: return null
        val now = System.currentTimeMillis()
        mutationDao.upsertAll(
            body.map { (field, value) ->
                CalendarMutationEntity(
                    eventId = id,
                    field = field,
                    payload = JsonObject(mapOf(field to value)).toString(),
                    createdAt = now,
                )
            } + CalendarMutationEntity(
                eventId = id,
                field = FIELD_SCOPE,
                payload = JsonObject(mapOf("scope" to JsonPrimitive(scope.wire))).toString(),
                createdAt = now,
            ),
        )
        val local = cached.applyPatch(patch)
        dao.upsertEvent(local)
        syncScheduler.scheduleSync()
        return local.toDomain(pendingSync = true)
    }

    /**
     * Zmiana wydarzenia, które samo czeka jeszcze w kolejce: poprawiamy ciało
     * `__create`, a nie dokładamy `PATCH` na nieistniejące id.
     */
    private suspend fun patchLocalDraft(id: String, patch: CalendarEventPatch): CalendarEvent? {
        val cached = dao.getEvent(id) ?: return null
        val local = cached.applyPatch(patch)
        dao.upsertEvent(local)

        val create = mutationDao.getForEvent(id).firstOrNull { it.field == FIELD_CREATE }
        if (create != null) {
            val body = runCatching {
                json.decodeFromString(CalendarEventCreateDto.serializer(), create.payload)
            }.getOrNull()
            if (body != null) {
                val updated = body.copy(
                    title = patch.title?.value ?: body.title,
                    description = patch.description?.value ?: body.description,
                    location = patch.location?.value ?: body.location,
                    color = patch.color?.value ?: body.color,
                    startAt = patch.startAt?.value ?: body.startAt,
                    endAt = if (patch.endAt != null) patch.endAt.value else body.endAt,
                    allDay = patch.allDay?.value ?: body.allDay,
                    assigneeId = if (patch.assigneeId != null) patch.assigneeId.value else body.assigneeId,
                    attendeeIds = patch.attendeeIds?.value ?: body.attendeeIds,
                )
                mutationDao.upsertAll(
                    listOf(
                        create.copy(
                            payload = json.encodeToString(CalendarEventCreateDto.serializer(), updated),
                        ),
                    ),
                )
            }
        }
        syncScheduler.scheduleSync()
        return local.toDomain(pendingSync = true)
    }

    override suspend fun deleteEvent(id: String, scope: RecurrenceScope) {
        // Wydarzenie zapisane bez zasięgu i od razu skasowane nigdy nie poleci —
        // znika razem z całą swoją kolejką.
        if (id.startsWith(LOCAL_ID_PREFIX)) {
            mutationDao.deleteForEvent(id)
            dao.deleteEvent(id)
            return
        }
        try {
            api.deleteCalendarEvent(id, scope.wire)
            mutationDao.deleteForEvent(id)
            dao.deleteEvent(id)
        } catch (e: IOException) {
            mutationDao.upsertAll(
                listOf(
                    CalendarMutationEntity(
                        eventId = id,
                        field = FIELD_DELETE,
                        payload = JsonObject(mapOf("scope" to JsonPrimitive(scope.wire))).toString(),
                        createdAt = System.currentTimeMillis(),
                    ),
                ),
            )
            syncScheduler.scheduleSync()
        }
    }

    override suspend fun setRsvp(
        eventId: String,
        userId: String,
        response: RsvpStatus,
    ): CalendarEvent {
        val cached = dao.getEvent(eventId)
        return try {
            // Trasa oddaje 204, więc stan wydarzenia składamy w cache sami —
            // odpowiedź dotyczy zawsze zalogowanego użytkownika.
            api.setCalendarRsvp(eventId, RsvpRequest(response.wire))
            val entity = (cached ?: error("Brak wydarzenia $eventId w cache."))
                .applyRsvp(userId, response)
            dao.upsertEvent(entity)
            mutationDao.delete(eventId, listOf(FIELD_RSVP))
            entity.toDomain(pendingSync = mutationDao.getForEvent(eventId).isNotEmpty())
        } catch (e: IOException) {
            val local = (cached ?: throw e).applyRsvp(userId, response)
            dao.upsertEvent(local)
            mutationDao.upsertAll(
                listOf(
                    CalendarMutationEntity(
                        eventId = eventId,
                        field = FIELD_RSVP,
                        // Kto odpowiedział — kolejka opróżnia się w robotniku,
                        // który o zalogowanym użytkowniku nic nie wie.
                        payload = JsonObject(
                            mapOf(
                                "response" to JsonPrimitive(response.wire),
                                "userId" to JsonPrimitive(userId),
                            ),
                        ).toString(),
                        createdAt = System.currentTimeMillis(),
                    ),
                ),
            )
            syncScheduler.scheduleSync()
            local.toDomain(pendingSync = true)
        }
    }

    // ── Kalendarze (warstwy) ─────────────────────────────────────────────────

    override suspend fun createCalendar(draft: CalendarDraft): Calendar {
        val entity = api.createCalendar(
            CalendarCreateDto(
                name = draft.name,
                type = draft.type.wire,
                color = draft.color,
                description = draft.description,
            ),
        ).toEntity(System.currentTimeMillis())
        dao.upsertCalendar(entity)
        return entity.toDomain()
    }

    override suspend fun updateCalendar(id: String, patch: CalendarPatch): Calendar {
        val entity = api.updateCalendar(id, buildCalendarPatch(patch))
            .toEntity(System.currentTimeMillis())
        dao.upsertCalendar(entity)
        return entity.toDomain()
    }

    override suspend fun setCalendarArchived(id: String, archived: Boolean) {
        // Obie trasy oddają 204, więc znacznik przestawiamy w cache sami.
        if (archived) api.archiveCalendar(id) else api.restoreCalendar(id)
        dao.getCalendar(id)?.let { dao.upsertCalendar(it.copy(isArchived = archived)) }
    }

    // ── Kolejka ──────────────────────────────────────────────────────────────

    override suspend fun syncPendingMutations(): CalendarSyncResult {
        val queue = mutationDao.getAll()
        if (queue.isEmpty()) return CalendarSyncResult.DONE

        for ((eventId, entries) in queue.groupBy { it.eventId }) {
            val create = entries.firstOrNull { it.field == FIELD_CREATE }
            val delete = entries.firstOrNull { it.field == FIELD_DELETE }

            // Utworzone i skasowane bez zasięgu — serwer nie musi o tym wiedzieć.
            if (create != null && delete != null) {
                mutationDao.deleteForEvent(eventId)
                dao.deleteEvent(eventId)
                continue
            }

            var targetId = eventId
            if (create != null) {
                val body = runCatching {
                    json.decodeFromString(CalendarEventCreateDto.serializer(), create.payload)
                }.getOrNull()
                if (body == null) {
                    // Nieczytelny wpis nigdy się nie wyśle — kasujemy, żeby nie
                    // blokował reszty kolejki.
                    mutationDao.delete(eventId, listOf(FIELD_CREATE))
                    continue
                }
                val created = try {
                    // Kolizję zasobu wymuszamy: człowiek zapisał termin świadomie,
                    // a pytać go o to trzy godziny później nie ma jak.
                    api.createCalendarEvent(body, "true")
                } catch (e: IOException) {
                    return CalendarSyncResult.RETRY
                } catch (e: Exception) {
                    // Serwer odmówił — tego wydarzenia nie da się wysłać nigdy.
                    mutationDao.deleteForEvent(eventId)
                    continue
                }
                targetId = created.id
                dao.deleteEvent(eventId)
                dao.upsertEvent(created.toEntity(System.currentTimeMillis()))
                mutationDao.delete(eventId, listOf(FIELD_CREATE))
                mutationDao.rekeyEvent(eventId, targetId)
            }

            if (delete != null) {
                val scope = delete.payload.readString("scope") ?: RecurrenceScope.THIS.wire
                try {
                    api.deleteCalendarEvent(targetId, scope)
                } catch (e: IOException) {
                    return CalendarSyncResult.RETRY
                } catch (e: Exception) {
                    // Odmowa albo wydarzenie już nie istnieje — kolejka nie ma
                    // czego pilnować.
                }
                mutationDao.deleteForEvent(targetId)
                dao.deleteEvent(targetId)
                continue
            }

            val rsvp = entries.firstOrNull { it.field == FIELD_RSVP }
            if (rsvp != null) {
                val response = rsvp.payload.readString("response")
                val responder = rsvp.payload.readString("userId")
                if (response != null) {
                    try {
                        api.setCalendarRsvp(targetId, RsvpRequest(response))
                        if (responder != null) {
                            dao.getEvent(targetId)
                                ?.applyRsvp(responder, RsvpStatus.fromWire(response))
                                ?.let { dao.upsertEvent(it) }
                        }
                    } catch (e: IOException) {
                        return CalendarSyncResult.RETRY
                    } catch (e: Exception) {
                        // Odmowa serwera — odpowiedzi nie da się wysłać nigdy.
                    }
                }
                mutationDao.delete(targetId, listOf(FIELD_RSVP))
            }

            val patches = entries.filter { it.field !in SPECIAL_FIELDS }
            if (patches.isEmpty()) {
                mutationDao.delete(targetId, listOf(FIELD_SCOPE))
                continue
            }
            val scope = entries.firstOrNull { it.field == FIELD_SCOPE }
                ?.payload?.readString("scope")
                ?: RecurrenceScope.THIS.wire
            val merged = buildMergedPatch(patches)
            try {
                val updated = api.updateCalendarEvent(targetId, merged, scope, "true")
                dao.upsertEvent(updated.toEntity(System.currentTimeMillis()))
            } catch (e: IOException) {
                return CalendarSyncResult.RETRY
            } catch (e: Exception) {
                // Odmowa serwera: zmiany nie da się wysłać nigdy. Zdejmujemy ją
                // z kolejki — inaczej blokowałaby wszystko, co za nią stoi.
            }
            mutationDao.delete(targetId, merged.keys.toList() + FIELD_SCOPE)
        }
        return CalendarSyncResult.DONE
    }

    /** Scala zakolejkowane pola jednego wydarzenia w jedno ciało `PATCH`. */
    private fun buildMergedPatch(entries: List<CalendarMutationEntity>): JsonObject {
        val fields = LinkedHashMap<String, JsonElement>()
        for (entry in entries.sortedBy { it.createdAt }) {
            val parsed = runCatching { json.parseToJsonElement(entry.payload) as? JsonObject }
                .getOrNull() ?: continue
            fields.putAll(parsed)
        }
        return JsonObject(fields)
    }

    private fun String.readString(key: String): String? = runCatching {
        (json.parseToJsonElement(this) as? JsonObject)?.get(key)?.jsonPrimitive?.contentOrNull
    }.getOrNull()

    /** `409` to nie awaria, tylko pytanie do człowieka — patrz [CalendarConflictException]. */
    /**
     * 409 przychodzi dziś w dwóch odmianach i człowiek reaguje na nie inaczej:
     *   • kolizja ZASOBU (bus, sala) — wymusza każdy, kto może pisać w kalendarzu;
     *   • prywatna zajętość WYKONAWCY (`code: private_busy`) — twarda blokada,
     *     którą przebija wyłącznie planista z `calendar.override_busy`.
     * Komunikat bierzemy z serwera: zna godziny kolizji, a przy serii także to,
     * ile wystąpień odpadło. Treści prywatnego wpisu nie ma w nim nigdy.
     */
    private fun HttpException.asConflictOrItself(): Throwable {
        if (code() != 409) return this
        val body = runCatching { response()?.errorBody()?.string() }.getOrNull()
        val error = body?.let {
            runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull()
        }
        val code = error?.get("code")?.jsonPrimitive?.contentOrNull
        val message = error?.get("message")?.jsonPrimitive?.contentOrNull
        return if (code == "private_busy") {
            PrivateBusyConflictException(
                message ?: "Wykonawca ma w tym czasie prywatną zajętość.",
                canOverride = canOverrideBusy.value,
            )
        } else {
            CalendarConflictException(
                message ?: "Zasób jest już zajęty w tym czasie. Zapisać mimo kolizji?",
            )
        }
    }

    /** Źródło miękkie: brak dostępu albo awaria → pusta lista, nie błąd modułu. */
    private fun <T> CoroutineScope.softAsync(
        block: suspend () -> List<T>,
    ): Deferred<List<T>> = async { runCatching { block() }.getOrDefault(emptyList()) }

    private companion object {
        val SPECIAL_FIELDS = setOf(FIELD_CREATE, FIELD_DELETE, FIELD_RSVP, FIELD_SCOPE)
    }
}
