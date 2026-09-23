package com.ekotak.teamtalk.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Komunikat odprawy widziany OD STRONY ODBIORCY — `GET /api/briefing/inbox`.
 *
 * Do tej pory telefon znał odprawę tylko od strony nadawcy (koordynator
 * wysyłał ją z karty montażu), więc komunikat do ekipy trafiał wyłącznie do
 * panelu. Od modułu Harmonogram tą samą rurą idą powiadomienia o opublikowanym
 * tygodniu (`kind = "system"`), a te muszą dojść do człowieka w terenie.
 *
 * `ackAt` niesie odhaczenie TEGO użytkownika (null = jeszcze nie potwierdził),
 * a `requiresAck` mówi, czy nadawca w ogóle czeka na potwierdzenie.
 */
@Serializable
data class BriefingInboxDto(
    val id: String = "",
    val title: String = "",
    val body: String = "",
    val priority: String = "normal",
    val kind: String = "message",
    val link: String? = null,
    val requiresAck: Boolean = false,
    val authorName: String = "",
    val publishedAt: String = "",
    val expiresAt: String? = null,
    val ackAt: String? = null,
)

/** Odpowiedź `GET /api/briefing/unread-count` — liczba czekających na odhaczenie. */
@Serializable
data class BriefingUnreadDto(val count: Int = 0)
