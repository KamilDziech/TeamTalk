package com.ekotak.teamtalk.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "call_logs",
    indices = [
        Index(value = ["clientId"]),
        Index(value = ["phoneNumber"]),
        Index(value = ["startedAt"]),
    ],
)
data class CallLogEntity(
    @PrimaryKey val id: String,
    val clientId: String?,
    val userId: String?,
    val phoneNumber: String,
    val direction: String,
    val simSlot: Int?,
    val startedAt: String,
    val endedAt: String?,
    val durationSec: Int?,
    val createdAt: String,
)
