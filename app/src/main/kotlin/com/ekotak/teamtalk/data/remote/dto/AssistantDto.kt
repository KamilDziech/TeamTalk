package com.ekotak.teamtalk.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Kontrakt modułu „Asystent" (`POST /api/assistant/chat`, `POST /api/assistant/actions`).
 *
 * Rozmowa jest BEZSTANOWA po stronie serwera: przy każdym pytaniu wysyłamy całą
 * dotychczasową wymianę zdań, a serwer dokłada do niej wiedzę (Kontekst
 * organizacji + dane CRM czytane na żywo). Propozycje akcji NIE są wykonywane —
 * wracają do aplikacji jako `actions` i idą do wykonania osobnym żądaniem
 * dopiero po kliknięciu użytkownika.
 */
@Serializable
data class AssistantChatRequest(val messages: List<AssistantMessageDto>)

@Serializable
data class AssistantChatReplyDto(
    val text: String = "",
    val actions: List<ProposedActionDto> = emptyList(),
    /** false = brak klucza LLM po stronie serwera (odpowiedź informacyjna). */
    val configured: Boolean = false,
    /** Narzędzia odczytu użyte przy tej odpowiedzi — ślad „skąd to wiem". */
    val usedTools: List<String> = emptyList(),
)

/** Propozycja akcji do zatwierdzenia przez człowieka. */
@Serializable
data class ProposedActionDto(
    val type: String,
    val label: String,
    /** Argumenty akcji — wracają na serwer nietknięte, aplikacja ich nie interpretuje. */
    val args: JsonObject = JsonObject(emptyMap()),
)

@Serializable
data class AssistantActionRequest(val type: String, val args: JsonObject)

@Serializable
data class AssistantActionResultDto(
    val ok: Boolean = false,
    val summary: String = "",
    /** Karta założona akcją `create_contact` — do „Otwórz kartę". */
    val clientId: String? = null,
)

/** `POST /api/assistant/card-scan` — odczyt wizytówki, bez zapisu. */
@Serializable
data class CardScanResponseDto(
    /** qr | photo | qr+photo */
    val source: String = "photo",
    val card: ScannedCardDto = ScannedCardDto(),
    val duplicates: List<CardDuplicateDto> = emptyList(),
    /** Czy wołający może zapisać kontakt (`deal.manage`). */
    val canSave: Boolean = false,
)

@Serializable
data class ScannedCardDto(
    val firstName: String = "",
    val lastName: String = "",
    val companyName: String = "",
    val jobTitle: String = "",
    val nip: String = "",
    val phone: String = "",
    val phone2: String = "",
    val email: String = "",
    val email2: String = "",
    val website: String = "",
    val street: String = "",
    val postalCode: String = "",
    val city: String = "",
)

@Serializable
data class CardDuplicateDto(
    val id: String,
    val name: String = "",
    val companyName: String? = null,
    val category: String? = null,
    val phone: String? = null,
    val email: String? = null,
)
