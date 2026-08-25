package com.ekotak.teamtalk.domain.model

/** Kierunek połączenia (zgodny z board360). */
enum class CallDirection(val value: String) {
    INBOUND("inbound"),
    OUTBOUND("outbound"),
    MISSED("missed");

    companion object {
        fun fromValue(value: String?): CallDirection =
            entries.firstOrNull { it.value == value } ?: OUTBOUND
    }
}

/**
 * Rejestr połączenia serwisanta (kontrakt board360). Aplikacja monitoruje
 * połączenia na własnym telefonie i zapisuje je tutaj; dopasowanie klienta po
 * numerze robi backend.
 */
data class CallLog(
    val id: String,
    val clientId: String?,
    val userId: String?,
    val phoneNumber: String,
    val direction: CallDirection,
    val simSlot: Int?,
    /** ISO-8601 UTC. */
    val startedAt: String,
    val endedAt: String?,
    val durationSec: Int?,
    val createdAt: String,
    /** Wypełniane lokalnie, gdy dane klienta są dostępne z cache. */
    val client: Client? = null,
)

/** Filtr listy połączeń (proste, po stronie backendu/cache). */
data class CallLogFilter(
    val since: String? = null,
    val limit: Int? = null,
    val clientId: String? = null,
    val direction: String? = null,
)
