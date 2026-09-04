package com.ekotak.teamtalk.data.mapper

import com.ekotak.teamtalk.data.local.entity.CalendarBusyEntity
import com.ekotak.teamtalk.data.local.entity.CalendarEntity
import com.ekotak.teamtalk.data.local.entity.CalendarEventEntity
import com.ekotak.teamtalk.data.local.entity.CalendarMemberEntity
import com.ekotak.teamtalk.data.remote.dto.CalendarAttendeeDto
import com.ekotak.teamtalk.data.remote.dto.CalendarDto
import com.ekotak.teamtalk.data.remote.dto.CalendarEventCreateDto
import com.ekotak.teamtalk.data.remote.dto.CalendarEventDto
import com.ekotak.teamtalk.data.remote.dto.CalendarOverlayDto
import com.ekotak.teamtalk.data.remote.dto.FreeBusyUserDto
import com.ekotak.teamtalk.data.remote.dto.PrivateBusyDto
import com.ekotak.teamtalk.data.remote.dto.RecurrenceDto
import com.ekotak.teamtalk.data.remote.dto.TaskMemberDto
import com.ekotak.teamtalk.domain.model.BusySlot
import com.ekotak.teamtalk.domain.model.Calendar
import com.ekotak.teamtalk.domain.model.CalendarEvent
import com.ekotak.teamtalk.domain.model.CalendarEventDraft
import com.ekotak.teamtalk.domain.model.CalendarEventPatch
import com.ekotak.teamtalk.domain.model.CalendarOverlay
import com.ekotak.teamtalk.domain.model.CalendarType
import com.ekotak.teamtalk.domain.model.EventAttendee
import com.ekotak.teamtalk.domain.model.FreeBusy
import com.ekotak.teamtalk.domain.model.OverlaySource
import com.ekotak.teamtalk.domain.model.PrivateBusy
import com.ekotak.teamtalk.domain.model.Recurrence
import com.ekotak.teamtalk.domain.model.RsvpStatus
import com.ekotak.teamtalk.domain.model.ShareLevel
import com.ekotak.teamtalk.domain.model.TaskMember
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Mapowanie modułu Kalendarz: DTO ↔ encja cache ↔ model domenowy.
 *
 * Uczestnicy jadą do bazy jako JSON listy DTO — dokładnie to, co przyszło
 * z API, żeby cache nie „poprawiał” danych po drodze.
 */

private val calendarJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

private val attendeesSerializer = ListSerializer(CalendarAttendeeDto.serializer())

// ── Kalendarze (warstwy) ─────────────────────────────────────────────────────

fun CalendarDto.toEntity(now: Long): CalendarEntity = CalendarEntity(
    id = id,
    name = name,
    type = type,
    color = color,
    description = description,
    ownerId = ownerId,
    ownerEmail = ownerEmail,
    isArchived = isArchived,
    effectiveLevel = effectiveLevel,
    syncedAt = now,
)

fun CalendarEntity.toDomain(): Calendar = Calendar(
    id = id,
    name = name,
    type = CalendarType.fromWire(type),
    color = color,
    description = description,
    ownerId = ownerId,
    ownerEmail = ownerEmail,
    isArchived = isArchived,
    effectiveLevel = ShareLevel.fromWire(effectiveLevel),
)

// ── Wydarzenia ───────────────────────────────────────────────────────────────

fun CalendarEventDto.toEntity(now: Long): CalendarEventEntity = CalendarEventEntity(
    id = id,
    calendarId = calendarId,
    calendarColor = calendarColor,
    title = title,
    description = description,
    location = location,
    color = color,
    startAt = startAt,
    endAt = endAt,
    allDay = allDay,
    assigneeId = assigneeId,
    assigneeEmail = assigneeEmail,
    attendeesJson = calendarJson.encodeToString(attendeesSerializer, attendees),
    recurrenceGroupId = recurrenceGroupId,
    recurrenceRule = recurrenceRule,
    localOnly = false,
    syncedAt = now,
)

fun CalendarEventEntity.toDomain(pendingSync: Boolean = false): CalendarEvent = CalendarEvent(
    id = id,
    calendarId = calendarId,
    calendarColor = calendarColor,
    title = title,
    description = description,
    location = location,
    color = color,
    startAt = startAt,
    endAt = endAt,
    allDay = allDay,
    assigneeId = assigneeId,
    assigneeEmail = assigneeEmail,
    attendees = decodeAttendees(attendeesJson),
    recurrenceGroupId = recurrenceGroupId,
    recurrenceRule = recurrenceRule,
    pendingSync = pendingSync,
    localOnly = localOnly,
)

private fun decodeAttendees(json: String): List<EventAttendee> =
    runCatching { calendarJson.decodeFromString(attendeesSerializer, json) }
        .getOrDefault(emptyList())
        .map { EventAttendee(id = it.id, email = it.email, response = RsvpStatus.fromWire(it.response)) }

/** Nakłada zakolejkowaną zmianę na wiersz cache — żeby decyzja była widoczna od razu. */
fun CalendarEventEntity.applyPatch(patch: CalendarEventPatch): CalendarEventEntity = copy(
    title = patch.title?.value ?: title,
    description = patch.description?.value ?: description,
    location = patch.location?.value ?: location,
    color = patch.color?.value ?: color,
    startAt = patch.startAt?.value ?: startAt,
    endAt = if (patch.endAt != null) patch.endAt.value else endAt,
    allDay = patch.allDay?.value ?: allDay,
    assigneeId = if (patch.assigneeId != null) patch.assigneeId.value else assigneeId,
    attendeesJson = patch.attendeeIds?.let { edit ->
        // Kolejka nie zna e-maili dopisanych osób — zostawiamy same identyfikatory,
        // a właściwe dane przyjdą z serwerem po synchronizacji.
        val known = decodeAttendees(attendeesJson).associateBy { it.id }
        calendarJson.encodeToString(
            attendeesSerializer,
            edit.value.map { id ->
                CalendarAttendeeDto(
                    id = id,
                    email = known[id]?.email,
                    response = (known[id]?.response ?: RsvpStatus.NEEDS_ACTION).wire,
                )
            },
        )
    } ?: attendeesJson,
)

/** Nakłada odpowiedź RSVP zapisaną bez zasięgu. */
fun CalendarEventEntity.applyRsvp(userId: String, response: RsvpStatus): CalendarEventEntity {
    val current = decodeAttendees(attendeesJson)
    if (current.none { it.id == userId }) return this
    return copy(
        attendeesJson = calendarJson.encodeToString(
            attendeesSerializer,
            current.map {
                CalendarAttendeeDto(
                    id = it.id,
                    email = it.email,
                    response = if (it.id == userId) response.wire else it.response.wire,
                )
            },
        ),
    )
}

/** Wiersz cache dla wydarzenia zapisanego bez zasięgu. */
fun CalendarEventDraft.toLocalEntity(
    localId: String,
    calendarColor: String?,
    now: Long,
): CalendarEventEntity = CalendarEventEntity(
    id = localId,
    calendarId = calendarId,
    calendarColor = calendarColor,
    title = title,
    description = description,
    location = location,
    color = color,
    startAt = startAt,
    endAt = endAt,
    allDay = allDay,
    assigneeId = assigneeId,
    assigneeEmail = null,
    attendeesJson = calendarJson.encodeToString(
        attendeesSerializer,
        attendeeIds.map { CalendarAttendeeDto(id = it, email = null, response = RsvpStatus.NEEDS_ACTION.wire) },
    ),
    recurrenceGroupId = null,
    recurrenceRule = null,
    localOnly = true,
    syncedAt = now,
)

fun CalendarEventDraft.toDto(): CalendarEventCreateDto = CalendarEventCreateDto(
    calendarId = calendarId,
    title = title,
    description = description,
    location = location,
    color = color,
    startAt = startAt,
    endAt = endAt,
    allDay = allDay,
    assigneeId = assigneeId,
    attendeeIds = attendeeIds,
    recurrence = recurrence?.toDto(),
)

fun Recurrence.toDto(): RecurrenceDto = RecurrenceDto(
    freq = freq.wire,
    interval = interval,
    until = until,
    count = count,
)

// ── Nakładki, zajętość, osoby ────────────────────────────────────────────────

/** Nakładka nieznanego źródła jest pomijana — panel też jej nie narysuje. */
fun CalendarOverlayDto.toDomain(): CalendarOverlay? {
    val src = OverlaySource.fromWire(source) ?: return null
    return CalendarOverlay(
        source = src,
        id = id,
        title = title,
        startAt = startAt,
        allDay = allDay,
        link = link,
        color = color.ifBlank { src.color },
    )
}

fun FreeBusyUserDto.toDomain(): FreeBusy = FreeBusy(
    userId = userId,
    busy = busy.map { BusySlot(startAt = it.startAt, endAt = it.endAt) },
)

fun TaskMemberDto.toCalendarMemberEntity(): CalendarMemberEntity = CalendarMemberEntity(
    id = id,
    email = email,
    firstName = firstName,
    lastName = lastName,
)

fun CalendarMemberEntity.toDomain(): TaskMember = TaskMember(
    id = id,
    email = email,
    firstName = firstName,
    lastName = lastName,
    role = null,
)

// ── Prywatna zajętość ────────────────────────────────────────────────────────

/** Klucz wiersza: serwer nie nadaje blokom identyfikatorów, więc składamy własny. */
fun PrivateBusyDto.toEntity(now: Long): CalendarBusyEntity = CalendarBusyEntity(
    id = "$userId|$startAt",
    userId = userId,
    startAt = startAt,
    endAt = endAt,
    syncedAt = now,
)

fun CalendarBusyEntity.toDomain(): PrivateBusy = PrivateBusy(
    userId = userId,
    startAt = startAt,
    endAt = endAt,
)
