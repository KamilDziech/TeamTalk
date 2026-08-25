package com.ekotak.teamtalk.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "voice_reports",
    indices = [Index(value = ["callLogId"]), Index(value = ["clientId"])],
)
data class VoiceReportEntity(
    @PrimaryKey val id: String,
    val callLogId: String?,
    val clientId: String?,
    val text: String?,
    val transcript: String?,
    val recordingKey: String?,
    val durationSec: Int?,
    val createdAt: String,
    val updatedAt: String,
)
