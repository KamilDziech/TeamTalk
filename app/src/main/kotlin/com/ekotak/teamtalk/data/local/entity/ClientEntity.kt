package com.ekotak.teamtalk.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "clients")
data class ClientEntity(
    @PrimaryKey val id: String,
    val firstName: String,
    val lastName: String,
    val email: String?,
    val email2: String?,
    val phone: String?,
    val phone2: String?,
    val address: String?,
    val postalCode: String?,
    val city: String?,
    val street: String?,
    val geoLat: Double?,
    val geoLng: Double?,
    val type: String?,
    val category: String?,
    val createdAt: String?,
    val updatedAt: String?,
)
