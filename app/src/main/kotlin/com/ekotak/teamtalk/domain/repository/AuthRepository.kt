package com.ekotak.teamtalk.domain.repository

import com.ekotak.teamtalk.domain.model.Session
import com.ekotak.teamtalk.domain.model.User
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    /** Authenticates with the server and persists the session locally. */
    suspend fun login(email: String, password: String): Session

    /** Registers a new account and persists the session locally. */
    suspend fun register(email: String, password: String, displayName: String): Session

    /** Invalidates the refresh token on the server and clears local session. */
    suspend fun logout()

    /** Exchanges the stored refresh token for a new session pair. */
    suspend fun refreshSession(): Session

    /** Returns the currently authenticated user from the server. */
    suspend fun getCurrentUser(): User

    /** Emits the locally stored session; null when not authenticated. */
    fun observeSession(): Flow<Session?>
}
