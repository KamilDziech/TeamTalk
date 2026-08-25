package com.ekotak.teamtalk.data.remote.dto

import kotlinx.serialization.Serializable

/** Urządzenie — kształt board360. */
@Serializable
data class DeviceResponseDto(
    val id: String,
    val organizationId: String? = null,
    val userId: String? = null,
    val deviceId: String,
    val model: String? = null,
    val osVersion: String? = null,
    val sim1Label: String? = null,
    val sim2Label: String? = null,
    val pushToken: String? = null,
    val lastSeenAt: String? = null,
    val createdAt: String? = null,
)

/** Body POST /api/devices (upsert). */
@Serializable
data class UpsertDeviceRequest(
    val deviceId: String,
    val model: String? = null,
    val osVersion: String? = null,
    val sim1Label: String? = null,
    val sim2Label: String? = null,
    val pushToken: String? = null,
)
