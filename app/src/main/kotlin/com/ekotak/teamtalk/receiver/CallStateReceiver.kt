package com.ekotak.teamtalk.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat
import com.ekotak.teamtalk.data.local.preferences.SimPreferences
import com.ekotak.teamtalk.service.CallMonitorService
import com.ekotak.teamtalk.worker.CallEndedWorker

class CallStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val prefs = context.getSharedPreferences("call_state_prefs", Context.MODE_PRIVATE)

        when (state) {
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                val incomingSubId = intent.getIntExtra("subscription", -1)
                val monitoredSubId = SimPreferences.readMonitoredSubId(context)
                if (monitoredSubId != SimPreferences.ALL_SIMS && incomingSubId != -1 && incomingSubId != monitoredSubId) return

                prefs.edit()
                    .putBoolean("was_in_call", true)
                    .putLong("call_start_ms", System.currentTimeMillis())
                    .putInt("call_sub_id", incomingSubId)
                    .apply()
                // Usługi tu NIE startujemy: w trakcie rozmowy nic nie robiła poza
                // powiadomieniem, a start z tła wywalał aplikację na Androidzie 14.
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                val wasInCall = prefs.getBoolean("was_in_call", false)
                if (wasInCall) {
                    val callStartMs = prefs.getLong("call_start_ms", System.currentTimeMillis())
                    val callSubId = prefs.getInt("call_sub_id", -1)
                    prefs.edit()
                        .putBoolean("was_in_call", false)
                        .remove("call_start_ms")
                        .remove("call_sub_id")
                        .apply()

                    val phoneAccountId = if (callSubId != -1) callSubId.toString() else null

                    try {
                        ContextCompat.startForegroundService(
                            context,
                            Intent(context, CallMonitorService::class.java).apply {
                                action = CallMonitorService.ACTION_CALL_ENDED
                                putExtra(CallMonitorService.EXTRA_CALL_START_MS, callStartMs)
                                if (phoneAccountId != null) putExtra(CallMonitorService.EXTRA_PHONE_ACCOUNT_ID, phoneAccountId)
                            },
                        )
                    } catch (e: IllegalStateException) {
                        // Android 12+: ForegroundServiceStartNotAllowedException (podklasa
                        // IllegalStateException) — system nie daje startu z tła. Ta sama
                        // robota idzie przyspieszonym zleceniem WorkManagera.
                        CallEndedWorker.enqueue(context, callStartMs, phoneAccountId)
                    }
                }
            }
        }
    }
}
