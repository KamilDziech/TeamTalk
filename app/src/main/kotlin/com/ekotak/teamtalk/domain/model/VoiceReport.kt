package com.ekotak.teamtalk.domain.model

data class VoiceReport(
    val id: String,
    val callLogId: String,
    val audioUrl: String?,
    val transcription: String?,
    val aiSummary: String?,
    val createdBy: String?,
    val callCount: Int,
    val createdAt: String,
    val updatedAt: String,
)
