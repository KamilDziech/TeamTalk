package com.ekotak.teamtalk.data.mapper

import com.ekotak.teamtalk.data.local.entity.CallLogEntity
import com.ekotak.teamtalk.data.remote.dto.CallLogResponseDto
import com.ekotak.teamtalk.domain.model.CallDirection
import com.ekotak.teamtalk.domain.model.CallLog

fun CallLogResponseDto.toEntity(): CallLogEntity = CallLogEntity(
    id          = id,
    clientId    = clientId,
    userId      = userId,
    phoneNumber = phoneNumber,
    direction   = direction,
    simSlot     = simSlot,
    startedAt   = startedAt,
    endedAt     = endedAt,
    durationSec = durationSec,
    createdAt   = createdAt,
)

fun CallLogEntity.toDomain(): CallLog = CallLog(
    id          = id,
    clientId    = clientId,
    userId      = userId,
    phoneNumber = phoneNumber,
    direction   = CallDirection.fromValue(direction),
    simSlot     = simSlot,
    startedAt   = startedAt,
    endedAt     = endedAt,
    durationSec = durationSec,
    createdAt   = createdAt,
)

fun CallLogResponseDto.toDomain(): CallLog = CallLog(
    id          = id,
    clientId    = clientId,
    userId      = userId,
    phoneNumber = phoneNumber,
    direction   = CallDirection.fromValue(direction),
    simSlot     = simSlot,
    startedAt   = startedAt,
    endedAt     = endedAt,
    durationSec = durationSec,
    createdAt   = createdAt,
)
