package com.ekotak.teamtalk.data.remote.interceptor

import com.ekotak.teamtalk.data.local.preferences.SessionPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

/**
 * Dokłada token sesji board360 do każdego żądania jako cookie `b360_session`
 * (tak odczytuje go `SessionAuthGuard` po stronie API). Jeśli backend włączy też
 * odczyt z nagłówka `Authorization: Bearer` (opcja A1b), można tu dodać Bearer —
 * nadmiarowy nagłówek jest nieszkodliwy.
 */
class AuthInterceptor @Inject constructor(
    private val sessionPreferences: SessionPreferences,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { sessionPreferences.token.first() }
        val request = if (token != null) {
            chain.request().newBuilder()
                .header("Cookie", "b360_session=$token")
                .build()
        } else {
            chain.request()
        }
        return chain.proceed(request)
    }
}
