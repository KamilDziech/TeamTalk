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
    val geoCity: String?,
    val geoMunicipality: String?,
    val travelKobierniceKm: Double?,
    val travelKobierniceMin: Double?,
    val travelGliwiceKm: Double?,
    val travelGliwiceMin: Double?,
    val type: String?,
    val category: String?,
    val createdAt: String?,
    val updatedAt: String?,
    // Dane firmowe z wizytówki (Room 32). Domyślne null — starsze wiersze ich nie mają.
    val companyName: String? = null,
    val nip: String? = null,
    val jobTitle: String? = null,
    val website: String? = null,
    val businessRole: String? = null,
)
