package com.ekotak.teamtalk.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class VoiceReportResponseDto(
    val id: String,
    @SerialName("call_log_id")  val callLogId: String,
    @SerialName("audio_url")    val audioUrl: String? = null,
    val transcription: String? = null,
    @SerialName("ai_summary")   val aiSummary: String? = null,
    @SerialName("created_by")   val createdBy: String? = null,
    @SerialName("call_count")   val callCount: Int = 1,
    @SerialName("created_at")   val createdAt: String,
    @SerialName("updated_at")   val updatedAt: String,
)

@Serializable
data class CreateVoiceReportRequest(
    @SerialName("call_log_id")  val callLogId: String,
    @SerialName("audio_url")    val audioUrl: String? = null,
    val transcription: String? = null,
    @SerialName("ai_summary")   val aiSummary: String? = null,
    @SerialName("call_count")   val callCount: Int = 1,
)

/** Response from POST /api/storage/voice-reports — note the camelCase key from the server. */
@Serializable
data class StorageUploadResponseDto(
    val path: String,
    val publicUrl: String,
)

/** Response from POST /api/functions/transcribe-audio. */
@Serializable
data class TranscriptionResponseDto(
    val transcription: String? = null,
    val isEmptyOrHallucination: Boolean,
)
