package com.ekotak.teamtalk.data.local.preferences

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wysyłka nagrań rozmów do transkrypcji: przełącznik z Ustawień i postęp
 * pojedynczych wysyłek.
 *
 * SharedPreferences, nie DataStore — tak jak [SimPreferences]: serwis
 * połączeń i worker czytają to synchronicznie, bez DI w odbiorniku.
 */
@Singleton
class CallRecordingPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val PREFS_NAME = "call_recording_prefs"
        private const val KEY_ENABLED = "upload_enabled"
        private const val KEY_REPORT_PREFIX = "report_"

        /** Domyślnie włączone — o wyłączeniu decyduje człowiek w Ustawieniach. */
        fun isEnabled(context: Context): Boolean =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getBoolean(KEY_ENABLED, true)
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) { prefs.edit().putBoolean(KEY_ENABLED, value).apply() }

    /**
     * Notatka założona na serwerze dla danego połączenia, zanim poszedł plik.
     * Ponowiona próba workera wgrywa nagranie do TEJ SAMEJ notatki, zamiast
     * zakładać przy każdym zerwaniu sieci kolejną pustą.
     */
    fun reportIdFor(callKey: String): String? = prefs.getString(KEY_REPORT_PREFIX + callKey, null)

    fun saveReportId(callKey: String, reportId: String) {
        prefs.edit().putString(KEY_REPORT_PREFIX + callKey, reportId).apply()
    }

    fun clearReportId(callKey: String) {
        prefs.edit().remove(KEY_REPORT_PREFIX + callKey).apply()
    }
}
