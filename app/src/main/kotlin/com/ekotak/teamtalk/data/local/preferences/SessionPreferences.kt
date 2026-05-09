package com.ekotak.teamtalk.data.local.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ekotak.teamtalk.presentation.settings.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionPreferences @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    companion object {
        private val KEY_ACCESS_TOKEN   = stringPreferencesKey("access_token")
        val KEY_LAST_SCAN_MS           = longPreferencesKey("last_scan_ms")
        private val KEY_REFRESH_TOKEN  = stringPreferencesKey("refresh_token")
        private val KEY_EXPIRES_AT     = longPreferencesKey("expires_at")
        private val KEY_USER_ID        = stringPreferencesKey("user_id")
        private val KEY_USER_EMAIL     = stringPreferencesKey("user_email")
        private val KEY_DISPLAY_NAME   = stringPreferencesKey("display_name")
        private val KEY_THEME          = stringPreferencesKey("theme_mode")
    }

    val accessToken: Flow<String?>  = dataStore.data.map { it[KEY_ACCESS_TOKEN] }
    val refreshToken: Flow<String?> = dataStore.data.map { it[KEY_REFRESH_TOKEN] }

    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        ThemeMode.entries.firstOrNull { it.name == prefs[KEY_THEME] } ?: ThemeMode.SYSTEM
    }

    suspend fun saveThemeMode(mode: ThemeMode) {
        dataStore.edit { it[KEY_THEME] = mode.name }
    }

    val session: Flow<StoredSession?> = dataStore.data.map { prefs ->
        val accessToken  = prefs[KEY_ACCESS_TOKEN]  ?: return@map null
        val refreshToken = prefs[KEY_REFRESH_TOKEN] ?: return@map null
        val expiresAt    = prefs[KEY_EXPIRES_AT]    ?: return@map null
        val userId       = prefs[KEY_USER_ID]       ?: return@map null
        val email        = prefs[KEY_USER_EMAIL]    ?: return@map null
        val displayName  = prefs[KEY_DISPLAY_NAME]  ?: return@map null
        StoredSession(accessToken, refreshToken, expiresAt, userId, email, displayName)
    }

    suspend fun save(
        accessToken: String,
        refreshToken: String,
        expiresAt: Long,
        userId: String,
        email: String,
        displayName: String,
    ) {
        dataStore.edit { prefs ->
            prefs[KEY_ACCESS_TOKEN]  = accessToken
            prefs[KEY_REFRESH_TOKEN] = refreshToken
            prefs[KEY_EXPIRES_AT]    = expiresAt
            prefs[KEY_USER_ID]       = userId
            prefs[KEY_USER_EMAIL]    = email
            prefs[KEY_DISPLAY_NAME]  = displayName
        }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    data class StoredSession(
        val accessToken: String,
        val refreshToken: String,
        val expiresAt: Long,
        val userId: String,
        val email: String,
        val displayName: String,
    )
}
