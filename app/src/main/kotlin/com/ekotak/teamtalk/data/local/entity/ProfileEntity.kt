package com.ekotak.teamtalk.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "profiles")
data class ProfileEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val isAdmin: Boolean,
    val createdAt: String,
    val updatedAt: String,
)
