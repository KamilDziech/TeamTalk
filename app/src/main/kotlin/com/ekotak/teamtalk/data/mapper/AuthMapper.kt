package com.ekotak.teamtalk.data.mapper

import com.ekotak.teamtalk.data.local.preferences.SessionPreferences
import com.ekotak.teamtalk.data.remote.dto.MobileLoginResponseDto
import com.ekotak.teamtalk.data.remote.dto.MobileUserDto
import com.ekotak.teamtalk.domain.model.Session
import com.ekotak.teamtalk.domain.model.User

/** Role board360 traktowane jak administrator aplikacji. */
private val ADMIN_ROLES = setOf("admin", "zarzad")

private fun displayNameFrom(email: String): String =
    email.substringBefore('@').ifBlank { email }

fun MobileUserDto.toDomain(): User = User(
    id             = userId,
    email          = email,
    displayName    = displayNameFrom(email),
    organizationId = organizationId,
    role           = role,
    permissions    = permissions,
    isAdmin        = role in ADMIN_ROLES,
)

fun MobileLoginResponseDto.toDomain(): Session = Session(
    token     = token,
    expiresAt = expiresAt,
    user      = user.toDomain(),
)

fun SessionPreferences.StoredSession.toDomain(): Session = Session(
    token     = token,
    expiresAt = expiresAt,
    user      = User(
        id             = userId,
        email          = email,
        displayName    = displayName,
        organizationId = organizationId,
        role           = role,
        isAdmin        = role in ADMIN_ROLES,
    ),
)
