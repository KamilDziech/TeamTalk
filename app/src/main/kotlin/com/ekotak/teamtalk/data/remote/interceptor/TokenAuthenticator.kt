package com.ekotak.teamtalk.data.remote.interceptor

import com.ekotak.teamtalk.data.local.preferences.SessionPreferences
import com.ekotak.teamtalk.data.remote.api.TeamTalkApi
import com.ekotak.teamtalk.data.remote.dto.RefreshRequest
import dagger.Lazy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject

class TokenAuthenticator @Inject constructor(
    private val sessionPreferences: SessionPreferences,
    // Lazy<> breaks the circular dependency: OkHttpClient → Authenticator → TeamTalkApi → OkHttpClient
    private val apiLazy: Lazy<TeamTalkApi>,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // Stop if this is already a refresh call to avoid an infinite loop
        if (response.request.url.encodedPath.contains("auth/refresh")) return null

        val refreshToken = runBlocking { sessionPreferences.refreshToken.first() } ?: return null

        return runBlocking {
            try {
                val result = apiLazy.get().refreshToken(RefreshRequest(refreshToken))
                val session = result.session

                sessionPreferences.save(
                    accessToken  = session.accessToken,
                    refreshToken = session.refreshToken,
                    expiresAt    = session.expiresAt,
                    userId       = session.user.id,
                    email        = session.user.email,
                    displayName  = session.user.userMetadata.displayName,
                )

                response.request.newBuilder()
                    .header("Authorization", "Bearer ${session.accessToken}")
                    .build()
            } catch (e: Exception) {
                // Refresh failed — clear session so the app navigates to Login
                sessionPreferences.clear()
                null
            }
        }
    }
}
