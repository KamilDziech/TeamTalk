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
        private val KEY_TOKEN          = stringPreferencesKey("session_token")
        val KEY_LAST_SCAN_MS           = longPreferencesKey("last_scan_ms")
        private val KEY_EXPIRES_AT     = longPreferencesKey("expires_at")
        private val KEY_USER_ID        = stringPreferencesKey("user_id")
        private val KEY_ORG_ID         = stringPreferencesKey("organization_id")
        private val KEY_USER_EMAIL     = stringPreferencesKey("user_email")
        private val KEY_ROLE           = stringPreferencesKey("user_role")
        private val KEY_DISPLAY_NAME   = stringPreferencesKey("display_name")
        private val KEY_THEME          = stringPreferencesKey("theme_mode")
        private val KEY_MENTIONS_SEEN_AT = longPreferencesKey("mentions_seen_at")
        private val KEY_SYNC_PROBLEM   = stringPreferencesKey("task_sync_problem")
        private val KEY_REMINDERS_DAY  = longPreferencesKey("task_reminders_day")
    }

    /** Token sesji board360 (wysyłany jako cookie `b360_session`). */
    val token: Flow<String?> = dataStore.data.map { it[KEY_TOKEN] }

    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        ThemeMode.entries.firstOrNull { it.name == prefs[KEY_THEME] } ?: ThemeMode.SYSTEM
    }

    suspend fun saveThemeMode(mode: ThemeMode) {
        dataStore.edit { it[KEY_THEME] = mode.name }
    }

    val session: Flow<StoredSession?> = dataStore.data.map { prefs ->
        val token        = prefs[KEY_TOKEN]        ?: return@map null
        val expiresAt    = prefs[KEY_EXPIRES_AT]   ?: return@map null
        val userId       = prefs[KEY_USER_ID]      ?: return@map null
        val organizationId = prefs[KEY_ORG_ID]     ?: ""
        val email        = prefs[KEY_USER_EMAIL]   ?: return@map null
        val role         = prefs[KEY_ROLE]         ?: ""
        val displayName  = prefs[KEY_DISPLAY_NAME] ?: email
        StoredSession(token, expiresAt, userId, organizationId, email, role, displayName)
    }

    suspend fun save(
        token: String,
        expiresAt: Long,
        userId: String,
        organizationId: String,
        email: String,
        role: String,
        displayName: String,
    ) {
        dataStore.edit { prefs ->
            prefs[KEY_TOKEN]        = token
            prefs[KEY_EXPIRES_AT]   = expiresAt
            prefs[KEY_USER_ID]      = userId
            prefs[KEY_ORG_ID]       = organizationId
            prefs[KEY_USER_EMAIL]   = email
            prefs[KEY_ROLE]         = role
            prefs[KEY_DISPLAY_NAME] = displayName
        }
    }

    /**
     * Do kiedy pokazaliśmy już powiadomienia o wywołaniach (@) — robotnik
     * odpytuje skrzynkę co 15 min i bez tego znacznika trąbiłby o tym samym
     * komentarzu do skutku. 0 = jeszcze nic nie pokazywaliśmy.
     */
    val mentionsSeenAt: Flow<Long> = dataStore.data.map { it[KEY_MENTIONS_SEEN_AT] ?: 0L }

    suspend fun saveMentionsSeenAt(millis: Long) {
        dataStore.edit { it[KEY_MENTIONS_SEEN_AT] = millis }
    }

    /**
     * Zmiana z kolejki offline, którą serwer odrzucił — czeka tu na moment,
     * gdy będzie komu ją pokazać. Kolejkę opróżnia robotnik w tle, często przy
     * zamkniętej aplikacji, więc bez tej skrzynki decyzja człowieka przepadałaby
     * bez słowa. Lista zadań pokazuje komunikat i od razu go kasuje.
     */
    val syncProblem: Flow<String?> = dataStore.data.map { it[KEY_SYNC_PROBLEM] }

    suspend fun saveSyncProblem(message: String) {
        dataStore.edit { it[KEY_SYNC_PROBLEM] = message }
    }

    suspend fun clearSyncProblem() {
        dataStore.edit { it.remove(KEY_SYNC_PROBLEM) }
    }

    /**
     * Dzień (epoch day), w którym pokazaliśmy już przypomnienie o zadaniach na
     * dziś i zaległych. Robotnik chodzi co kilka godzin, ale trąbi raz dziennie
     * — powiadomienie wracające co sześć godzin uczy ludzi je zamiatać.
     */
    val remindersShownOn: Flow<Long> = dataStore.data.map { it[KEY_REMINDERS_DAY] ?: -1L }

    suspend fun saveRemindersShownOn(epochDay: Long) {
        dataStore.edit { it[KEY_REMINDERS_DAY] = epochDay }
    }

    suspend fun clear() {
        dataStore.edit { it.clear() }
    }

    data class StoredSession(
        val token: String,
        val expiresAt: Long,
        val userId: String,
        val organizationId: String,
        val email: String,
        val role: String,
        val displayName: String,
    )
}
