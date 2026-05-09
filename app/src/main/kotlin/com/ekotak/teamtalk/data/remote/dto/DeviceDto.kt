package com.ekotak.teamtalk.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DeviceResponseDto(
    val id: String,
    @SerialName("user_name")      val userName: String,
    @SerialName("push_token")     val pushToken: String,
    @SerialName("device_info")    val deviceInfo: String? = null,
    @SerialName("last_active_at") val lastActiveAt: String,
    @SerialName("created_at")     val createdAt: String,
    @SerialName("updated_at")     val updatedAt: String,
)

@Serializable
data class UpsertDeviceRequest(
    @SerialName("push_token")  val pushToken: String,
    @SerialName("user_name")   val userName: String,
    @SerialName("device_info") val deviceInfo: String? = null,
)

@Serializable
data class UpdateLastActiveRequest(
    @SerialName("push_token") val pushToken: String,
)
