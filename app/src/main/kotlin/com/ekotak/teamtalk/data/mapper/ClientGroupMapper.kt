package com.ekotak.teamtalk.data.mapper

import com.ekotak.teamtalk.data.local.entity.ClientGroupEntity
import com.ekotak.teamtalk.data.remote.dto.ClientGroupResponseDto
import com.ekotak.teamtalk.domain.model.ClientGroup

fun ClientGroupResponseDto.toEntity(): ClientGroupEntity = ClientGroupEntity(
    id        = id,
    name      = name,
    isDefault = isDefault,
    createdAt = createdAt,
)

fun ClientGroupEntity.toDomain(): ClientGroup = ClientGroup(
    id        = id,
    name      = name,
    isDefault = isDefault,
    createdAt = createdAt,
)

fun ClientGroupResponseDto.toDomain(): ClientGroup = ClientGroup(
    id        = id,
    name      = name,
    isDefault = isDefault,
    createdAt = createdAt,
)
