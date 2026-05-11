package com.ekotak.teamtalk.data.mapper

import com.ekotak.teamtalk.data.local.entity.ClientEntity
import com.ekotak.teamtalk.data.remote.dto.ClientResponseDto
import com.ekotak.teamtalk.domain.model.Client

fun ClientResponseDto.toEntity(): ClientEntity = ClientEntity(
    id        = id,
    phone     = phone,
    name      = name,
    address   = address,
    notes     = notes,
    groupId   = groupId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun ClientEntity.toDomain(): Client = Client(
    id        = id,
    phone     = phone,
    name      = name,
    address   = address,
    notes     = notes,
    groupId   = groupId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun ClientResponseDto.toDomain(): Client = Client(
    id        = id,
    phone     = phone,
    name      = name,
    address   = address,
    notes     = notes,
    groupId   = groupId,
    createdAt = createdAt,
    updatedAt = updatedAt,
)
