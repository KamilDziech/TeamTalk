package com.ekotak.teamtalk.data.mapper

import com.ekotak.teamtalk.data.local.entity.DeviceEntity
import com.ekotak.teamtalk.data.remote.dto.DeviceResponseDto
import com.ekotak.teamtalk.domain.model.Device

fun DeviceResponseDto.toEntity(): DeviceEntity = DeviceEntity(
    id         = id,
    deviceId   = deviceId,
    model      = model,
    osVersion  = osVersion,
    sim1Label  = sim1Label,
    sim2Label  = sim2Label,
    pushToken  = pushToken,
    lastSeenAt = lastSeenAt,
    createdAt  = createdAt,
)

fun DeviceEntity.toDomain(): Device = Device(
    id         = id,
    deviceId   = deviceId,
    model      = model,
    osVersion  = osVersion,
    sim1Label  = sim1Label,
    sim2Label  = sim2Label,
    pushToken  = pushToken,
    lastSeenAt = lastSeenAt,
    createdAt  = createdAt,
)

fun DeviceResponseDto.toDomain(): Device = Device(
    id         = id,
    deviceId   = deviceId,
    model      = model,
    osVersion  = osVersion,
    sim1Label  = sim1Label,
    sim2Label  = sim2Label,
    pushToken  = pushToken,
    lastSeenAt = lastSeenAt,
    createdAt  = createdAt,
)
