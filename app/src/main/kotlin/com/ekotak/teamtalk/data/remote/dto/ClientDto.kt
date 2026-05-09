package com.ekotak.teamtalk.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ClientResponseDto(
    val id: String,
    val phone: String,
    val name: String? = null,
    val address: String? = null,
    val notes: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
data class CreateClientRequest(
    val phone: String,
    val name: String? = null,
    val address: String? = null,
    val notes: String? = null,
)

@Serializable
data class UpdateClientRequest(
    val phone: String? = null,
    val name: String? = null,
    val address: String? = null,
    val notes: String? = null,
)
