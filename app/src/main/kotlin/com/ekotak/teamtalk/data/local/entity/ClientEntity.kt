package com.ekotak.teamtalk.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "clients",
    indices = [Index(value = ["phone"], unique = true)],
)
data class ClientEntity(
    @PrimaryKey val id: String,
    val phone: String,
    val name: String?,
    val address: String?,
    val notes: String?,
    val createdAt: String,
    val updatedAt: String,
)
