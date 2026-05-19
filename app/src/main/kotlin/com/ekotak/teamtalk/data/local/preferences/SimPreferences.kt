package com.ekotak.teamtalk.data.local.preferences

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SimPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val ALL_SIMS = -1
        private const val PREFS_NAME = "sim_prefs"
        private const val KEY_MONITORED_SUB_ID = "monitored_sub_id"

        /** Static accessor for use in BroadcastReceiver (no DI available). */
        fun readMonitoredSubId(context: Context): Int =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_MONITORED_SUB_ID, ALL_SIMS)
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var monitoredSubId: Int
        get() = prefs.getInt(KEY_MONITORED_SUB_ID, ALL_SIMS)
        set(value) { prefs.edit().putInt(KEY_MONITORED_SUB_ID, value).apply() }

    /** Returns phone account ID string to pass to call log queries, or null if all SIMs. */
    val monitoredPhoneAccountId: String?
        get() = monitoredSubId.takeIf { it != ALL_SIMS }?.toString()
}
