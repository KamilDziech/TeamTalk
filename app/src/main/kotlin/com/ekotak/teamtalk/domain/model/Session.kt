package com.ekotak.teamtalk.domain.model

data class Session(
    /** Podpisany token sesji board360 (wysyłany jako cookie `b360_session`). */
    val token: String,
    /** Unix timestamp (sekundy) wygaśnięcia tokenu. */
    val expiresAt: Long,
    val user: User,
)
