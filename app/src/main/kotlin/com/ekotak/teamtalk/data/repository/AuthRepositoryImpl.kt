package com.ekotak.teamtalk.data.repository

import com.ekotak.teamtalk.data.local.preferences.SessionPreferences
import com.ekotak.teamtalk.data.mapper.toDomain
import com.ekotak.teamtalk.data.remote.api.TeamTalkApi
import com.ekotak.teamtalk.data.remote.dto.MobileLoginRequest
import com.ekotak.teamtalk.domain.model.Session
import com.ekotak.teamtalk.domain.model.User
import com.ekotak.teamtalk.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val api: TeamTalkApi,
    private val sessionPreferences: SessionPreferences,
) : AuthRepository {

    override suspend fun login(email: String, password: String): Session {
        val response = api.mobileLogin(MobileLoginRequest(email.trim().lowercase(), password))
        return response.toDomain().also { persistSession(it) }
    }

    // board360 nie wspiera samodzielnej rejestracji — konta zakłada panel web.
    override suspend fun register(email: String, password: String, displayName: String): Session =
        throw UnsupportedOperationException(
            "Rejestracja jest niedostępna — konto zakłada administrator w panelu board360."
        )

    // Sesja board360 jest bezstanowa (brak endpointu wylogowania) — czyścimy lokalnie.
    override suspend fun logout() {
        sessionPreferences.clear()
    }

    // board360 nie używa refresh tokenów — po wygaśnięciu wymagane ponowne logowanie.
    override suspend fun refreshSession(): Session =
        throw UnsupportedOperationException("board360 nie używa refresh tokenów.")

    override suspend fun getCurrentUser(): User = api.getMe().toDomain()

    override fun observeSession(): Flow<Session?> =
        sessionPreferences.session.map { it?.toDomain() }

    private suspend fun persistSession(session: Session) {
        sessionPreferences.save(
            token          = session.token,
            expiresAt      = session.expiresAt,
            userId         = session.user.id,
            organizationId = session.user.organizationId,
            email          = session.user.email,
            role           = session.user.role,
            displayName    = session.user.displayName,
        )
    }
}
