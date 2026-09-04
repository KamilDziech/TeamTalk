package com.ekotak.teamtalk.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cache offline modułu Kalendarz. Ekran czyta wyłącznie stąd — sieć tylko
 * dolewa świeże dane — więc kalendarz otwiera się w aucie bez zasięgu tak samo
 * jak w biurze.
 */
@Entity(tableName = "calendars")
data class CalendarEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val color: String,
    val description: String?,
    val ownerId: String,
    val ownerEmail: String?,
    val isArchived: Boolean,
    val effectiveLevel: String,
    val syncedAt: Long,
)

/**
 * Wystąpienie wydarzenia w cache. Uczestnicy leżą w jednej kolumnie jako JSON:
 * zawsze czytamy ich w komplecie (arkusz pokazuje awatary z odpowiedziami),
 * a osobna tabela dokładałaby złączenie bez zysku.
 *
 * [localOnly] oznacza wydarzenie zapisane bez zasięgu: ma identyfikator
 * `local:<uuid>` i czeka w kolejce na wysłanie, po której dostaje id z serwera.
 */
@Entity(tableName = "calendar_events")
data class CalendarEventEntity(
    @PrimaryKey val id: String,
    val calendarId: String,
    val calendarColor: String?,
    val title: String,
    val description: String?,
    val location: String?,
    val color: String?,
    val startAt: String,
    val endAt: String?,
    val allDay: Boolean,
    val assigneeId: String?,
    val assigneeEmail: String?,
    /** `List<CalendarAttendeeDto>` zserializowane wspólnym `Json` aplikacji. */
    val attendeesJson: String,
    val recurrenceGroupId: String?,
    val recurrenceRule: String?,
    val localOnly: Boolean,
    val syncedAt: Long,
)

// Osoby zespołu mieszkają w `TeamMemberEntity` (tabela `team_members`) — cache
// dzielony z Mapą, patrz migracja 10 → 11.

/**
 * Kolejka zmian zapisanych bez zasięgu — jedyna kopia decyzji podjętej w aucie,
 * więc tabela przeżywa podniesienie wersji bazy (migracja, nie kasowanie).
 *
 * Klucz to para (wydarzenie, pole): kolejne dotknięcie tego samego pola nadpisuje
 * poprzednie, a różne pola scalają się w jedno ciało `PATCH`. Pola specjalne
 * ([FIELD_CREATE], [FIELD_DELETE], [FIELD_RSVP], [FIELD_SCOPE]) nie są polami
 * wydarzenia — nazwy zaczynają się od `__`, więc nie zderzą się z niczym z API.
 */
@Entity(tableName = "calendar_mutations", primaryKeys = ["eventId", "field"])
data class CalendarMutationEntity(
    val eventId: String,
    val field: String,
    val payload: String,
    val createdAt: Long,
) {
    companion object {
        const val FIELD_CREATE = "__create"
        const val FIELD_DELETE = "__delete"
        const val FIELD_RSVP = "__rsvp"

        /** Zakres serii dla zakolejkowanej zmiany (`this` / `following` / `all`). */
        const val FIELD_SCOPE = "__scope"

        const val LOCAL_ID_PREFIX = "local:"
    }
}

/**
 * Zajętość z PRYWATNEGO kalendarza kolegi z zespołu (podpiętego w panelu
 * sekretnym adresem iCal). Cache jest po to, żeby monter w aucie bez zasięgu
 * widział, że koordynator ma zajęte popołudnie — tak samo jak widzi wydarzenia.
 *
 * Kolumn na treść tu NIE MA i nie będzie: board360 z prywatnego kalendarza
 * czyta wyłącznie godziny, więc telefon nie ma jak wyświetlić czegoś więcej
 * niż napis „Zajęte".
 */
@Entity(tableName = "calendar_private_busy")
data class CalendarBusyEntity(
    /** `userId|startAt` — serwer nie nadaje tym blokom własnych identyfikatorów. */
    @PrimaryKey val id: String,
    val userId: String,
    val startAt: String,
    val endAt: String,
    val syncedAt: Long,
)
