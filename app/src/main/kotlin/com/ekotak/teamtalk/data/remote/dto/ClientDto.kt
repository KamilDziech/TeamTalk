package com.ekotak.teamtalk.data.remote.dto

import kotlinx.serialization.Serializable

/** Klient — kształt board360 (read-only). */
@Serializable
data class ClientResponseDto(
    val id: String,
    val organizationId: String? = null,
    val firstName: String = "",
    val lastName: String = "",
    val email: String? = null,
    val email2: String? = null,
    val phone: String? = null,
    val phone2: String? = null,
    val address: String? = null,
    val postalCode: String? = null,
    val city: String? = null,
    val street: String? = null,
    val geoLat: Double? = null,
    val geoLng: Double? = null,
    val type: String? = null,
    val category: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)
