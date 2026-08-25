package com.ekotak.teamtalk.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "devices",
    indices = [Index(value = ["deviceId"], unique = true)],
)
data class DeviceEntity(
    @PrimaryKey val id: String,
    val deviceId: String,
    val model: String?,
    val osVersion: String?,
    val sim1Label: String?,
    val sim2Label: String?,
    val pushToken: String?,
    val lastSeenAt: String?,
    val createdAt: String?,
)
