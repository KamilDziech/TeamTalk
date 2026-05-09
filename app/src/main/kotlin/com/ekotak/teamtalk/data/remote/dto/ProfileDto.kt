package com.ekotak.teamtalk.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProfileResponseDto(
    val id: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("is_admin")     val isAdmin: Boolean,
    @SerialName("created_at")   val createdAt: String,
    @SerialName("updated_at")   val updatedAt: String,
)

@Serializable
data class UpdateProfileRequest(
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("is_admin")     val isAdmin: Boolean? = null,
)

@Serializable
data class UpsertProfileRequest(
    val id: String? = null,
    @SerialName("display_name") val displayName: String? = null,
)
