package com.ekotak.teamtalk.data.repository

import com.ekotak.teamtalk.data.local.preferences.SessionPreferences
import com.ekotak.teamtalk.data.mapper.toDomain
import com.ekotak.teamtalk.data.remote.api.TeamTalkApi
import com.ekotak.teamtalk.data.remote.dto.LoginRequest
import com.ekotak.teamtalk.data.remote.dto.LogoutRequest
import com.ekotak.teamtalk.data.remote.dto.RefreshRequest
import com.ekotak.teamtalk.data.remote.dto.RegisterRequest
import com.ekotak.teamtalk.domain.model.Session
import com.ekotak.teamtalk.domain.model.User
import com.ekotak.teamtalk.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class AuthRepositoryImpl @Inject constructor(
    private val api: TeamTalkApi,
    private val sessionPreferences: SessionPreferences,
) : AuthRepository {

    override suspend fun login(email: String, password: String): Session {
        val response = api.login(LoginRequest(email.trim().lowercase(), password))
        return response.session.toDomain().also { persistSession(it) }
    }

    override suspend fun register(email: String, password: String, displayName: String): Session {
        val response = api.register(RegisterRequest(email.trim().lowercase(), password, displayName))
        return response.session.toDomain().also { persistSession(it) }
    }

    override suspend fun logout() {
        val refreshToken = sessionPreferences.session.first()?.refreshToken
        try {
            if (refreshToken != null) api.logout(LogoutRequest(refreshToken))
        } finally {
            sessionPreferences.clear()
        }
    }

    override suspend fun refreshSession(): Session {
        val stored = sessionPreferences.session.first()
            ?: error("No active session to refresh")
        val response = api.refreshToken(RefreshRequest(stored.refreshToken))
        return response.session.toDomain().also { persistSession(it) }
    }

    override suspend fun getCurrentUser(): User = api.getMe().toDomain()

    override fun observeSession(): Flow<Session?> =
        sessionPreferences.session.map { it?.toDomain() }

    private suspend fun persistSession(session: Session) {
        sessionPreferences.save(
            accessToken  = session.accessToken,
            refreshToken = session.refreshToken,
            expiresAt    = session.expiresAt,
            userId       = session.user.id,
            email        = session.user.email,
            displayName  = session.user.displayName,
        )
    }
}
