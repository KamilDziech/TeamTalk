package com.ekotak.teamtalk.domain.model

/**
 * Notatka po połączeniu (kontrakt board360). Tekst i/lub nagranie (recordingKey
 * po wgraniu pliku), opcjonalnie powiązane z połączeniem i klientem.
 */
data class VoiceReport(
    val id: String,
    val callLogId: String?,
    val clientId: String?,
    val text: String?,
    val transcript: String?,
    val recordingKey: String?,
    val durationSec: Int?,
    val createdAt: String,
    val updatedAt: String,
)
