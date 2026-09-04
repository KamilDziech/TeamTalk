package com.ekotak.teamtalk.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Kształt odpowiedzi modułu Kalendarz board360 (`api/src/modules/calendar`).
 * Pola opcjonalne mają wartości domyślne — starszy backend nie zna wszystkich,
 * a moduł ma się otworzyć także wtedy.
 */

@Serializable
data class CalendarDto(
    val id: String,
    val name: String,
    val type: String,
    val color: String,
    val description: String? = null,
    val ownerId: String = "",
    val ownerEmail: String? = null,
    val isArchived: Boolean = false,
    val effectiveLevel: String = "reader",
)

@Serializable
data class CalendarAttendeeDto(
    val id: String,
    val email: String? = null,
    val response: String = "needs_action",
)

@Serializable
data class CalendarEventDto(
    val id: String,
    val calendarId: String,
    val calendarColor: String? = null,
    val title: String,
    val description: String? = null,
    val location: String? = null,
    val color: String? = null,
    val startAt: String,
    val endAt: String? = null,
    val allDay: Boolean = false,
    val assigneeId: String? = null,
    val assigneeEmail: String? = null,
    val attendees: List<CalendarAttendeeDto> = emptyList(),
    val recurrenceGroupId: String? = null,
    val recurrenceRule: String? = null,
)

@Serializable
data class CalendarOverlayDto(
    val source: String,
    val id: String,
    val title: String,
    val startAt: String,
    val allDay: Boolean = false,
    val link: String = "",
    val color: String = "#5f5e5a",
)

@Serializable
data class FreeBusySlotDto(val startAt: String, val endAt: String)

@Serializable
data class FreeBusyUserDto(
    val userId: String,
    val busy: List<FreeBusySlotDto> = emptyList(),
)

/** Ciało `POST /calendar/events`. Serializowane też do kolejki offline. */
@Serializable
data class CalendarEventCreateDto(
    val calendarId: String,
    val title: String,
    val description: String? = null,
    val location: String? = null,
    val color: String? = null,
    val startAt: String,
    val endAt: String? = null,
    val allDay: Boolean = false,
    val assigneeId: String? = null,
    val attendeeIds: List<String> = emptyList(),
    val recurrence: RecurrenceDto? = null,
)

@Serializable
data class RecurrenceDto(
    val freq: String,
    val interval: Int = 1,
    val until: String? = null,
    val count: Int? = null,
)

@Serializable
data class RsvpRequest(val response: String)

@Serializable
data class CalendarCreateDto(
    val name: String,
    val type: String,
    val color: String,
    val description: String? = null,
)

/**
 * Blok zajętości z prywatnego kalendarza (`GET /calendar/events/private-busy`).
 * CELOWO nie ma tu tytułu ani opisu — board360 z feedu iCal bierze wyłącznie
 * godziny, więc nie ma czego przesyłać ani czego wyświetlić.
 */
@Serializable
data class PrivateBusyDto(
    val userId: String,
    val startAt: String,
    val endAt: String,
)

/**
 * `GET /calendar/private-link` — telefonowi potrzebne jest z tego jedno pole:
 * czy wolno mu przebić cudzą blokadę zajętości („Zaplanuj mimo to").
 * Samo podpinanie kalendarza zostaje w panelu: wklejanie 200-znakowego
 * sekretnego adresu na telefonie to droga przez mękę.
 */
@Serializable
data class PrivateLinkStateDto(
    val canOverrideBusy: Boolean = false,
)
