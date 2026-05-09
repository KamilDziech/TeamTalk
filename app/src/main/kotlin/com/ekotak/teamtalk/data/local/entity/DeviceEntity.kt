package com.ekotak.teamtalk.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "devices",
    indices = [Index(value = ["pushToken"], unique = true)],
)
data class DeviceEntity(
    @PrimaryKey val id: String,
    val userName: String,
    val pushToken: String,
    val deviceInfo: String?,
    val lastActiveAt: String,
    val createdAt: String,
    val updatedAt: String,
)
