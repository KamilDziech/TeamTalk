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
    /** Streszczenie napisane na serwerze z transkrypcji nagrania. */
    val summary: String? = null,
    /** Ustalenia z rozmowy — wpisane ręcznie albo wyciągnięte z nagrania. */
    val agreements: String? = null,
    val nextStep: String? = null,
    /** Kolejka transkrypcji nagrania; null — notatka bez nagrania. */
    val transcriptionStatus: TranscriptionStatus? = null,
    val transcriptionError: String? = null,
)

/** Etap transkrypcji nagrania rozmowy na serwerze (lustro enuma board360). */
enum class TranscriptionStatus(val value: String) {
    PENDING("pending"), PROCESSING("processing"), DONE("done"), FAILED("failed");

    companion object {
        fun fromValue(value: String?): TranscriptionStatus? = entries.firstOrNull { it.value == value }
    }
}
