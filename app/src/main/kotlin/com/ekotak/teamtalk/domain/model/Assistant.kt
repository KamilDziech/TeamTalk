package com.ekotak.teamtalk.domain.model

import kotlinx.serialization.json.JsonObject

/**
 * Model rozmowy z asystentem firmowym (kafelek „Asystent" pulpitu).
 *
 * Wiadomości reużywają [AssistantMessage] z kartoteki — to ta sama para
 * rola + treść. Różnica względem asystenta karty klienta jest w zasięgu:
 * ten asystent czyta Kontekst organizacji ORAZ szuka po kartotece i kartach
 * deali, a do tego może zaproponować akcję zapisu.
 */

/** Rodzaje akcji, jakie asystent może zaproponować (lustro kontraktu API). */
object AssistantActionType {
    const val CREATE_TASK = "create_task"
    const val CREATE_CALENDAR_EVENT = "create_calendar_event"
    const val CREATE_DEAL_NOTE = "create_deal_note"
    const val CREATE_SERVICE_JOB = "create_service_job"
    const val CREATE_LEAVE_REQUEST = "create_leave_request"
}

/**
 * Propozycja akcji czekająca na zatwierdzenie. `args` przechodzą przez telefon
 * nietknięte — walidację i wykonanie robi serwer, a RBAC sprawdza dwa razy
 * (przy proponowaniu i przy wykonaniu).
 */
data class AssistantAction(
    val type: String,
    val label: String,
    val args: JsonObject,
    val status: Status = Status.IDLE,
    /** Podsumowanie wykonania albo treść błędu — pod przyciskiem. */
    val result: String? = null,
) {
    enum class Status { IDLE, RUNNING, DONE, ERROR }
}

/** Odpowiedź asystenta: tekst + ewentualne propozycje akcji. */
data class AssistantAnswer(
    val text: String,
    val actions: List<AssistantAction>,
    /** false = brak klucza LLM po stronie serwera (tryb informacyjny). */
    val configured: Boolean,
    /** Nazwy narzędzi odczytu użytych przy tej odpowiedzi. */
    val usedTools: List<String>,
)
