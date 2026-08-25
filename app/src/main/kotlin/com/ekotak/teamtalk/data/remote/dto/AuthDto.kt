package com.ekotak.teamtalk.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Kontrakt logowania board360 (VPS). Sesja to bezstanowy, podpisany token HMAC
 * (cookie `b360_session`). Endpoint `POST /api/auth/mobile-login` (dobudowany po
 * stronie board360 — patrz prompt A1) weryfikuje hasło i zwraca gotowy token.
 * Brak refresh tokenów: token ma długi TTL (ustawiany serwerowo, ~30 dni),
 * a po jego wygaśnięciu użytkownik loguje się ponownie.
 */
@Serializable
data class MobileLoginRequest(
    val email: String,
    val password: String,
)

/**
 * Tożsamość użytkownika zwracana przez `mobile-login` oraz `GET /api/me`
 * (board360 `AuthContext`). Uwaga: board360 nie zwraca tu imienia/nazwiska —
 * `displayName` po stronie aplikacji wywodzimy z e-maila (TODO: wzbogacić o
 * imię/nazwisko z `/api/users`, gdy będzie potrzebne).
 */
@Serializable
data class MobileUserDto(
    val userId: String,
    val organizationId: String,
    val email: String,
    val role: String,
    val permissions: List<String> = emptyList(),
    val clientVisibility: String? = null,
)

/** Odpowiedź `POST /api/auth/mobile-login`. */
@Serializable
data class MobileLoginResponseDto(
    val token: String,
    /** Unix timestamp (sekundy) wygaśnięcia tokenu sesji. */
    val expiresAt: Long,
    val user: MobileUserDto,
)
