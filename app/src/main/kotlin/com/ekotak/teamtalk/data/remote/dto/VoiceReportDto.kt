package com.ekotak.teamtalk.data.remote.dto

import kotlinx.serialization.Serializable

/** Notatka po połączeniu — kształt board360. */
@Serializable
data class VoiceReportResponseDto(
    val id: String,
    val organizationId: String? = null,
    val userId: String? = null,
    val callLogId: String? = null,
    val clientId: String? = null,
    val text: String? = null,
    val transcript: String? = null,
    val recordingKey: String? = null,
    val durationSec: Int? = null,
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
