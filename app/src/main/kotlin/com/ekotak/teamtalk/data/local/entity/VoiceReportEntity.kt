package com.ekotak.teamtalk.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "voice_reports",
    indices = [Index(value = ["callLogId"])],
)
data class VoiceReportEntity(
    @PrimaryKey val id: String,
    val callLogId: String,
    val audioUrl: String?,
    val transcription: String?,
    val aiSummary: String?,
    val createdBy: String?,
    val callCount: Int,
    val createdAt: String,
    val updatedAt: String,
)
