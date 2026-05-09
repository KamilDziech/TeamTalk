package com.ekotak.teamtalk.data.mapper

import com.ekotak.teamtalk.data.local.entity.CallLogEntity
import com.ekotak.teamtalk.data.remote.dto.CallLogResponseDto
import com.ekotak.teamtalk.domain.model.CallLog
import com.ekotak.teamtalk.domain.model.CallStatus
import com.ekotak.teamtalk.domain.model.CallType

fun CallLogResponseDto.toEntity(): CallLogEntity = CallLogEntity(
    id             = id,
    clientId       = clientId,
    employeeId     = employeeId,
    type           = type,
    status         = status,
    timestamp      = timestamp,
    reservationBy  = reservationBy,
    reservationAt  = reservationAt,
    recipients     = recipients,
    callerPhone    = callerPhone,
    dedupKey       = dedupKey,
    mergedIntoId   = mergedIntoId,
    phoneAccountId = phoneAccountId,
    createdAt      = createdAt,
    updatedAt      = updatedAt,
)

fun CallLogEntity.toDomain(): CallLog = CallLog(
    id             = id,
    clientId       = clientId,
    employeeId     = employeeId,
    type           = CallType.fromValue(type),
    status         = CallStatus.fromValue(status),
    timestamp      = timestamp,
    reservationBy  = reservationBy,
    reservationAt  = reservationAt,
    recipients     = recipients,
    callerPhone    = callerPhone,
    dedupKey       = dedupKey,
    mergedIntoId   = mergedIntoId,
    phoneAccountId = phoneAccountId,
    createdAt      = createdAt,
    updatedAt      = updatedAt,
)

fun CallLogResponseDto.toDomain(): CallLog = CallLog(
    id             = id,
    clientId       = clientId,
    employeeId     = employeeId,
    type           = CallType.fromValue(type),
    status         = CallStatus.fromValue(status),
    timestamp      = timestamp,
    reservationBy  = reservationBy,
    reservationAt  = reservationAt,
    recipients     = recipients,
    callerPhone    = callerPhone,
    dedupKey       = dedupKey,
    mergedIntoId   = mergedIntoId,
    phoneAccountId = phoneAccountId,
    createdAt      = createdAt,
    updatedAt      = updatedAt,
    client         = clients?.toDomain(),
)
