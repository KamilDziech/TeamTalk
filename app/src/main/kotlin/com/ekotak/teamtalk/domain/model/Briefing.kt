package com.ekotak.teamtalk.domain.model

/**
 * Komunikat odprawy w skrzynce montera.
 *
 * `system` = komunikat wystawiony przez moduł (dziś: „Opublikuj tydzień"
 * w Harmonogramie), `message` = pismo od człowieka. Rozróżnienie idzie na
 * ekran, bo inaczej czyta się „Harmonogram na tydzień…" od kierownika, a
 * inaczej od automatu.
 */
data class BriefingItem(
    val id: String,
    val title: String,
    val body: String,
    val priority: String,
    val kind: String,
    val link: String?,
    val requiresAck: Boolean,
    val authorName: String,
    val publishedAt: String,
    val expiresAt: String?,
    val ackAt: String?,
) {
    val urgent: Boolean get() = priority == "high"
    val system: Boolean get() = kind == "system"
    /** Czeka na moje odhaczenie. */
    val pending: Boolean get() = requiresAck && ackAt == null
}
