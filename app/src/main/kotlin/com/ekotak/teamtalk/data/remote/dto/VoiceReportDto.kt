package com.ekotak.teamtalk.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Notatka po połączeniu — kształt board360.
 *
 * Pola od `dealId` w dół niesie WYŁĄCZNIE streszczenie dopisane ręcznie
 * (`POST /api/voice-reports/manual`, zakładka „Komunikacja" karty deala
 * i kanał Telefon w panelu): notatka z nagrania ma połączenie w `callLogId`,
 * więc numeru i czasu rozmowy nie musi wozić ze sobą. Wszystkie z domyślnymi
 * wartościami — starsze wpisy ich po prostu nie mają.
 */
@Serializable
data class VoiceReportResponseDto(
    val id: String,
    val organizationId: String? = null,
    val userId: String? = null,
    val callLogId: String? = null,
    val clientId: String? = null,
    val dealId: String? = null,
    val text: String? = null,
    /** Ustalenia z rozmowy — osobne pole formularza, nie część przebiegu. */
    val agreements: String? = null,
    /** Następny krok po rozmowie; z niego zakłada się zadanie. */
    val nextStep: String? = null,
    val transcript: String? = null,
    /** Streszczenie z transkrypcji nagrania (model na serwerze). */
    val summary: String? = null,
    /** `pending` / `processing` / `done` / `failed`; null — bez nagrania. */
    val transcriptionStatus: String? = null,
    val transcriptionError: String? = null,
    val recordingKey: String? = null,
    val durationSec: Int? = null,
    val phoneNumber: String? = null,
    /** `inbound` / `outbound` / `missed`; formularz ręczny o kierunek nie pyta. */
    val direction: String? = null,
    /** Kiedy rozmowa się odbyła; `null` = liczy się `createdAt`. */
    val occurredAt: String? = null,
    /** `teamtalk` (z nagrania) albo `manual` (dopisane z ręki). */
    val source: String? = null,
    val createdAt: String,
    val updatedAt: String,
)

/** Body POST /api/voice-reports. */
@Serializable
data class CreateVoiceReportRequest(
    val callLogId: String? = null,
    val clientId: String? = null,
    val text: String? = null,
    val durationSec: Int? = null,
)
