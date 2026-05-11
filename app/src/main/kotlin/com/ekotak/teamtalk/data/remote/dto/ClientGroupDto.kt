package com.ekotak.teamtalk.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ClientGroupResponseDto(
    val id: String,
    val name: String,
    @SerialName("is_default") val isDefault: Boolean = false,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class CreateClientGroupRequest(
    val name: String,
    @SerialName("is_default") val isDefault: Boolean = false,
)
