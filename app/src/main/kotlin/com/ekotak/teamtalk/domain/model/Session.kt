package com.ekotak.teamtalk.domain.model

data class Session(
    val accessToken: String,
    val refreshToken: String,
    /** Unix timestamp (seconds) when the access token expires. */
    val expiresAt: Long,
    val user: User,
)
