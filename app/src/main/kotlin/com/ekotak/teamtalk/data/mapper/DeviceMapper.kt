package com.ekotak.teamtalk.data.mapper

import com.ekotak.teamtalk.data.local.entity.DeviceEntity
import com.ekotak.teamtalk.data.remote.dto.DeviceResponseDto
import com.ekotak.teamtalk.domain.model.Device

fun DeviceResponseDto.toEntity(): DeviceEntity = DeviceEntity(
    id           = id,
    userName     = userName,
    pushToken    = pushToken,
    deviceInfo   = deviceInfo,
    lastActiveAt = lastActiveAt,
    createdAt    = createdAt,
    updatedAt    = updatedAt,
)

fun DeviceEntity.toDomain(): Device = Device(
    id           = id,
    userName     = userName,
    pushToken    = pushToken,
    deviceInfo   = deviceInfo,
    lastActiveAt = lastActiveAt,
    createdAt    = createdAt,
    updatedAt    = updatedAt,
)

fun DeviceResponseDto.toDomain(): Device = Device(
    id           = id,
    userName     = userName,
    pushToken    = pushToken,
    deviceInfo   = deviceInfo,
    lastActiveAt = lastActiveAt,
    createdAt    = createdAt,
    updatedAt    = updatedAt,
)
