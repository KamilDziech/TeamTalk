package com.ekotak.teamtalk.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val email: String,
    val password: String,
)

@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    @SerialName("display_name") val displayName: String,
)

@Serializable
data class RefreshRequest(
    @SerialName("refresh_token") val refreshToken: String,
)

@Serializable
data class LogoutRequest(
    @SerialName("refresh_token") val refreshToken: String,
)

@Serializable
data class UserMetadataDto(
    @SerialName("display_name") val displayName: String,
)

@Serializable
data class UserResponseDto(
    val id: String,
    val email: String,
    @SerialName("user_metadata") val userMetadata: UserMetadataDto,
)

@Serializable
data class SessionResponseDto(
    @SerialName("access_token")  val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("token_type")    val tokenType: String,
    @SerialName("expires_at")    val expiresAt: Long,
    @SerialName("expires_in")    val expiresIn: Long,
    val user: UserResponseDto,
)

/** Response from POST /api/auth/login and POST /api/auth/register. */
@Serializable
data class AuthResponseDto(
    val session: SessionResponseDto,
    val user: UserResponseDto? = null,
)

/** Response from POST /api/auth/refresh. */
@Serializable
data class RefreshResponseDto(
    val session: SessionResponseDto,
)
