package com.ekotak.teamtalk.data.mapper

import com.ekotak.teamtalk.data.local.preferences.SessionPreferences
import com.ekotak.teamtalk.data.remote.dto.SessionResponseDto
import com.ekotak.teamtalk.data.remote.dto.UserResponseDto
import com.ekotak.teamtalk.domain.model.Session
import com.ekotak.teamtalk.domain.model.User

fun UserResponseDto.toDomain(): User = User(
    id          = id,
    email       = email,
    displayName = userMetadata.displayName,
)

fun SessionResponseDto.toDomain(): Session = Session(
    accessToken  = accessToken,
    refreshToken = refreshToken,
    expiresAt    = expiresAt,
    user         = user.toDomain(),
)

fun SessionPreferences.StoredSession.toDomain(): Session = Session(
    accessToken  = accessToken,
    refreshToken = refreshToken,
    expiresAt    = expiresAt,
    user         = User(id = userId, email = email, displayName = displayName),
)
