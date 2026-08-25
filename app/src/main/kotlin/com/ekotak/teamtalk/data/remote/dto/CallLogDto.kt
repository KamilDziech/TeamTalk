package com.ekotak.teamtalk.data.remote.dto

import kotlinx.serialization.Serializable

/** Rejestr połączenia — kształt board360. */
@Serializable
data class CallLogResponseDto(
    val id: String,
    val organizationId: String? = null,
    val userId: String? = null,
    val clientId: String? = null,
    val phoneNumber: String,
    val direction: String,
    val simSlot: Int? = null,
    val startedAt: String,
    val endedAt: String? = null,
    val durationSec: Int? = null,
    val createdAt: String,
)

/** Body POST /api/call-logs (pojedynczy wpis; wysyłamy jako element tablicy). */
@Serializable
data class CreateCallLogRequest(
    val clientId: String? = null,
    val phoneNumber: String,
    val direction: String,
    val simSlot: Int? = null,
    val startedAt: String,
    val endedAt: String? = null,
    val durationSec: Int? = null,
)
