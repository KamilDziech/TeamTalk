package com.ekotak.teamtalk.data.remote.interceptor

import com.ekotak.teamtalk.data.local.preferences.SessionPreferences
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject

/**
 * board360 nie używa refresh tokenów — sesja to jeden podpisany token o długim
 * TTL. Odpowiedź 401 oznacza token nieważny/wygasły, więc czyścimy sesję (co
 * przekieruje aplikację na ekran logowania) i nie ponawiamy żądania.
 */
class TokenAuthenticator @Inject constructor(
    private val sessionPreferences: SessionPreferences,
) : Authenticator {

    override fun authenticate(route: Route?, response: Response): Request? {
        // Nie reaguj na 401 z samego logowania (błędne dane) — nie ma czego czyścić.
        if (response.request.url.encodedPath.contains("auth/mobile-login")) return null

        runBlocking { sessionPreferences.clear() }
        return null
    }
}
