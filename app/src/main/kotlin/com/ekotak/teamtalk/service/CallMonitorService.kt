package com.ekotak.teamtalk.service

import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.ekotak.teamtalk.MainActivity
import com.ekotak.teamtalk.data.notification.NotificationHelper
import com.ekotak.teamtalk.data.scanner.DeviceCallLogReader
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class CallMonitorService : Service() {

    companion object {
        const val ACTION_CALL_ENDED = "com.ekotak.teamtalk.CALL_ENDED"
        const val EXTRA_CALL_START_MS = "call_start_ms"
        const val EXTRA_PHONE_ACCOUNT_ID = "phone_account_id"
        const val CHANNEL_ID = "call_monitor"
        private const val NOTIF_ID = 101
    }

    @Inject lateinit var deviceCallLogReader: DeviceCallLogReader
    @Inject lateinit var notificationHelper: NotificationHelper

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        val notification = buildForegroundNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SHORT_SERVICE)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CALL_ENDED) {
            val callStartMs = intent.getLongExtra(EXTRA_CALL_START_MS, System.currentTimeMillis())
            val phoneAccountId = intent.getStringExtra(EXTRA_PHONE_ACCOUNT_ID)
            scope.launch {
                delay(2_000)
                val call = deviceCallLogReader.readMostRecentCallSince(callStartMs, phoneAccountId)
                val phone = call?.phoneNumber
                    ?.let { deviceCallLogReader.normalizePhone(it) }
                    ?.takeIf { it.isNotBlank() }
                openNoteScreen(phone)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun openNoteScreen(phone: String?) {
        val canOverlay = Build.VERSION.SDK_INT < Build.VERSION_CODES.M ||
            Settings.canDrawOverlays(applicationContext)
        if (canOverlay) {
            val activityIntent = Intent(applicationContext, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
                putExtra(MainActivity.EXTRA_POST_CALL_PHONE, phone ?: "")
            }
            startActivity(activityIntent)
        } else {
            notificationHelper.showPostCallNoteNotification(phone)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildForegroundNotification(): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setContentTitle("Połączenie w toku...")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
}
