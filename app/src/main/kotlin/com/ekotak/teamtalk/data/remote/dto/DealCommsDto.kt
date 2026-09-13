package com.ekotak.teamtalk.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Zakładka „Komunikacja" karty deala (board360: `modules/discussions`,
 * `modules/whatsapp`, `modules/telephony`). Kształt 1:1 z odpowiedziami
 * kontrolerów.
 */

/**
 * Wątek wewnętrzny deala — `GET /api/discussions/deal/{dealId}`. Oddaje SAME
 * komentarze (bez nagłówka z tytułem, który ma wątek zadania): karta deala i tak
 * wie, o którym dealu mowa, a wątek deala celowo nie wchodzi do skrzynki
 * Komunikatora.
 */
@Serializable
data class DealDiscussionDto(
    val comments: List<DiscussionCommentDto> = emptyList(),
)

/** Wiadomość ze skrzynki WhatsApp deala — `GET /api/deals/{id}/whatsapp`. */
@Serializable
data class WhatsappMessageDto(
    val id: String,
    val dealId: String? = null,
    /** `inbound` albo `outbound`. */
    val direction: String = "outbound",
    val body: String? = null,
    val template: String? = null,
    /** `received` / `queued` / `sent` / `delivered` / `failed` / `pending_config`. */
    val status: String = "queued",
    val createdAt: String,
)

/**
 * Ręczne streszczenie rozmowy — `POST /api/voice-reports/manual`.
 *
 * Osobna trasa od `POST /api/voice-reports`: tamta zapisuje notatkę po
 * połączeniu, które telefon zarejestrował, więc niesie `callLogId`. Tu połączenia
 * nie ma — numer, kierunek i czas rozmowy wpis niesie sam, a wymagana jest
 * wyłącznie treść.
 */
@Serializable
data class CreateManualVoiceReportRequest(
    val clientId: String? = null,
    val dealId: String? = null,
    val phoneNumber: String? = null,
    val direction: String? = null,
    val text: String,
    val agreements: String? = null,
    val nextStep: String? = null,
    /** ISO-8601 z offsetem; `null` = serwer wstawia czas zapisu. */
    val occurredAt: String? = null,
)
