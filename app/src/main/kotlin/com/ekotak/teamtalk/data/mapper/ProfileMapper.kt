package com.ekotak.teamtalk.data.mapper

import com.ekotak.teamtalk.data.local.entity.ProfileEntity
import com.ekotak.teamtalk.data.remote.dto.ProfileResponseDto
import com.ekotak.teamtalk.domain.model.Profile

fun ProfileResponseDto.toEntity(): ProfileEntity = ProfileEntity(
    id          = id,
    displayName = displayName,
    isAdmin     = isAdmin,
    createdAt   = createdAt,
    updatedAt   = updatedAt,
)

fun ProfileEntity.toDomain(): Profile = Profile(
    id          = id,
    displayName = displayName,
    isAdmin     = isAdmin,
    createdAt   = createdAt,
    updatedAt   = updatedAt,
)

fun ProfileResponseDto.toDomain(): Profile = Profile(
    id          = id,
    displayName = displayName,
    isAdmin     = isAdmin,
    createdAt   = createdAt,
    updatedAt   = updatedAt,
)
