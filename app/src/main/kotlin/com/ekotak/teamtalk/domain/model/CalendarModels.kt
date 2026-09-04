package com.ekotak.teamtalk.domain.model

/**
 * Model modułu Kalendarz — lustro `web/src/app/app/calendar/actions.ts`.
 *
 * Nazewnictwo i wartości „na drucie" (`wire`) muszą zgadzać się co do znaku
 * z API board360: to te same łańcuchy trafiają do kolejki offline, więc
 * przemianowanie ich tutaj wysłałoby po odzyskaniu zasięgu śmieci.
 */

enum class CalendarType(val wire: String, val label: String) {
    PERSONAL("personal", "osobisty"),
    TEAM("team", "zespołowy"),
    RESOURCE("resource", "zasób"),
    ;

    companion object {
        fun fromWire(value: String?): CalendarType =
            entries.firstOrNull { it.wire == value } ?: TEAM
    }
}

/** Poziom dostępu do kalendarza; [rank] pozwala je porównywać jak w panelu. */
enum class ShareLevel(val wire: String, val rank: Int, val label: String) {
    FREEBUSY("freebusy", 1, "tylko zajętość"),
    READER("reader", 2, "tylko odczyt"),
    WRITER("writer", 3, "zapis"),
    OWNER("owner", 4, "właściciel"),
    ;

    companion object {
        fun fromWire(value: String?): ShareLevel =
            entries.firstOrNull { it.wire == value } ?: READER
    }
}

data class Calendar(
    val id: String,
    val name: String,
    val type: CalendarType,
    /** Kolor w zapisie panelu (`#rrggbb`) — wydarzenie bez własnego dziedziczy go. */
    val color: String,
    val description: String?,
    val ownerId: String,
    val ownerEmail: String?,
    val isArchived: Boolean,
    val effectiveLevel: ShareLevel,
) {
    /** Czy w tym kalendarzu wolno dodawać i zmieniać wydarzenia. */
    val canWrite: Boolean get() = effectiveLevel.rank >= ShareLevel.WRITER.rank
    val isOwner: Boolean get() = effectiveLevel == ShareLevel.OWNER
}

enum class RsvpStatus(val wire: String, val label: String) {
    NEEDS_ACTION("needs_action", "Bez odpowiedzi"),
    ACCEPTED("accepted", "Idę"),
    DECLINED("declined", "Nie idę"),
    TENTATIVE("tentative", "Może"),
    ;

    companion object {
        fun fromWire(value: String?): RsvpStatus =
            entries.firstOrNull { it.wire == value } ?: NEEDS_ACTION
    }
}

data class EventAttendee(
    val id: String,
    val email: String?,
    val response: RsvpStatus,
)

/**
 * Wystąpienie wydarzenia. Serie rozwija SERWER — telefon dostaje gotowe
 * wystąpienia z [recurrenceGroupId], więc nie liczy reguł powtarzania ani
 * w widoku, ani w kolejce offline.
 *
 * [pendingSync] = zmiana czeka w kolejce; [localOnly] = serwer o tym wydarzeniu
 * jeszcze nie wie (identyfikator `local:<uuid>`).
 */
data class CalendarEvent(
    val id: String,
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
    val attendees: List<EventAttendee>,
    val recurrenceGroupId: String?,
    val recurrenceRule: String?,
    val pendingSync: Boolean = false,
    val localOnly: Boolean = false,
) {
    val isRecurring: Boolean get() = recurrenceGroupId != null

    /** Odpowiedź wskazanej osoby, jeśli jest uczestnikiem. */
    fun responseOf(userId: String?): RsvpStatus? =
        userId?.let { id -> attendees.firstOrNull { it.id == id }?.response }
}

/** Źródła nakładek operacyjnych — `OVERLAY_META` z panelu, w tej samej kolejności. */
enum class OverlaySource(val wire: String, val label: String, val color: String) {
    INSTALLATION("installation", "Montaże", "#44d62c"),
    SERVICE("service", "Serwis", "#e0a500"),
    FLEET("fleet", "Zasoby", "#d55181"),
    RESERVATION("reservation", "Rezerwacje aut", "#14b8a6"),
    DEAL("deal", "Kontakt z dealami", "#59c1c9"),
    PROJECT("project", "Zadania z projektów", "#8a2cd6"),
    LEAVE("leave", "Urlopy", "#79c0ff"),
    ;

    companion object {
        fun fromWire(value: String?): OverlaySource? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * Pozycja z innego modułu pokazywana w kalendarzu tylko do podglądu.
 * [link] to ścieżka panelu (np. `/app/service?job=…`) — na telefonie służy
 * wyłącznie do rozpoznania, dokąd prowadzić w aplikacji.
 */
data class CalendarOverlay(
    val source: OverlaySource,
    val id: String,
    val title: String,
    val startAt: String,
    val allDay: Boolean,
    val link: String,
    val color: String,
)

enum class RecurFreq(val wire: String, val label: String) {
    DAILY("daily", "Codziennie"),
    WEEKLY("weekly", "Co tydzień"),
    MONTHLY("monthly", "Co miesiąc"),
}

data class Recurrence(
    val freq: RecurFreq,
    val interval: Int = 1,
    /** ISO 8601 albo `null`; wzajemnie wykluczające się z [count] — jak w panelu. */
    val until: String? = null,
    val count: Int? = null,
)

/** Zakres, na jaki działa zmiana wystąpienia serii. */
enum class RecurrenceScope(val wire: String, val label: String) {
    THIS("this", "Tego wystąpienia"),
    FOLLOWING("following", "Tego i dalszych"),
    ALL("all", "Całej serii"),
}

data class CalendarEventDraft(
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
    val recurrence: Recurrence? = null,
)

/**
 * Zmiana wydarzenia. Każde pole opakowane w [Edit], bo API rozróżnia „pola nie
 * ma = bez zmian" od „`null` = wyczyść" — ta sama zasada co przy zadaniach
 * i kartach klienta.
 */
data class CalendarEventPatch(
    val title: Edit<String>? = null,
    val description: Edit<String?>? = null,
    val location: Edit<String?>? = null,
    val color: Edit<String?>? = null,
    val startAt: Edit<String>? = null,
    val endAt: Edit<String?>? = null,
    val allDay: Edit<Boolean>? = null,
    val assigneeId: Edit<String?>? = null,
    val attendeeIds: Edit<List<String>>? = null,
)

data class CalendarDraft(
    val name: String,
    val type: CalendarType,
    val color: String,
    val description: String? = null,
)

data class CalendarPatch(
    val name: Edit<String>? = null,
    val color: Edit<String>? = null,
    val description: Edit<String?>? = null,
)

/** Zajętość jednej osoby — `GET /calendar/events/freebusy`, bez treści wydarzeń. */
data class FreeBusy(
    val userId: String,
    val busy: List<BusySlot>,
)

data class BusySlot(val startAt: String, val endAt: String)

/**
 * Odmowa zapisu z powodu podwójnej rezerwacji zasobu (HTTP 409). Panel pyta
 * wtedy „zapisać mimo kolizji?" i powtarza żądanie z `allowConflict=true`;
 * telefon robi to samo, więc błąd musi być rozpoznawalny po typie.
 */
class CalendarConflictException(message: String) : Exception(message)

/**
 * Blok „Zajęte" z prywatnego kalendarza kolegi. Nie ma tytułu, bo board360 nie
 * zapisuje treści prywatnych wpisów — z feedu iCal bierze wyłącznie godziny.
 */
data class PrivateBusy(
    val userId: String,
    val startAt: String,
    val endAt: String,
)

/**
 * Odmowa zapisu z powodu PRYWATNEJ zajętości wykonawcy (409 z `code:
 * private_busy`). Osobny typ od [CalendarConflictException], bo reakcja jest
 * inna: kolizję zasobu wymusza każdy, a prywatną blokadę tylko osoba
 * z `calendar.override_busy` — reszcie pokazujemy sam komunikat.
 */
class PrivateBusyConflictException(
    message: String,
    val canOverride: Boolean,
) : Exception(message)
