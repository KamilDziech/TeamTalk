package com.ekotak.teamtalk.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "client_groups")
data class ClientGroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val isDefault: Boolean,
    val createdAt: String,
)
