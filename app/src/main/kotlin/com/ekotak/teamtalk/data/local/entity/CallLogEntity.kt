package com.ekotak.teamtalk.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "call_logs",
    indices = [
        Index(value = ["clientId"]),
        Index(value = ["status"]),
        Index(value = ["callerPhone"]),
        Index(value = ["dedupKey"], unique = true),
        Index(value = ["status", "timestamp"]),
    ],
)
data class CallLogEntity(
    @PrimaryKey val id: String,
    val clientId: String?,
    val employeeId: String?,
    val type: String,
    val status: String,
    val timestamp: String,
    val reservationBy: String?,
    val reservationAt: String?,
    /** Stored as comma-separated UUIDs — see Converters. */
    val recipients: List<String>,
    val callerPhone: String?,
    val dedupKey: String?,
    val mergedIntoId: String?,
    val phoneAccountId: String?,
    val createdAt: String,
    val updatedAt: String,
)
