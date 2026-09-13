package com.ekotak.teamtalk.service

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.ekotak.teamtalk.MainActivity
import com.ekotak.teamtalk.data.local.preferences.CallRecordingPreferences
import com.ekotak.teamtalk.data.notification.NotificationHelper
import com.ekotak.teamtalk.data.scanner.DeviceCallLogReader
import com.ekotak.teamtalk.domain.repository.CallLogRepository
import com.ekotak.teamtalk.worker.CallRecordingUploadWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
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
    @Inject lateinit var callLogRepository: CallLogRepository

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val isoFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

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
                var call = deviceCallLogReader.readMostRecentCallSince(callStartMs, phoneAccountId)
                    ?: deviceCallLogReader.readMostRecentCallSince(callStartMs, null)
                val deadline = System.currentTimeMillis() + 5_000
                while (call == null && System.currentTimeMillis() < deadline) {
                    delay(500)
                    call = deviceCallLogReader.readMostRecentCallSince(callStartMs, phoneAccountId)
                        ?: deviceCallLogReader.readMostRecentCallSince(callStartMs, null)
                }
                val phone = call?.phoneNumber
                    ?.let { deviceCallLogReader.normalizePhone(it) }
                    ?.takeIf { it.isNotBlank() }
                recordCallInHistory(call, phone)
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
            wakeScreen()
            val activityIntent = Intent(applicationContext, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
                putExtra(MainActivity.EXTRA_OPEN_POST_CALL_NOTE, true)
                putExtra(MainActivity.EXTRA_POST_CALL_PHONE, phone ?: "")
            }
            startActivity(activityIntent)
        } else {
            notificationHelper.showPostCallNoteNotification(phone)
        }
    }

    @Suppress("DEPRECATION")
    private fun wakeScreen() {
        val pm = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isInteractive) {
            val wl = pm.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or
                PowerManager.ACQUIRE_CAUSES_WAKEUP or
                PowerManager.ON_AFTER_RELEASE,
                "com.ekotak.teamtalk:post_call_wake"
            )
            wl.acquire(5_000)
            wl.release()
        }
    }

    private suspend fun recordCallInHistory(call: DeviceCallLogReader.DeviceCall?, phone: String?) {
        if (phone == null) return
        val timestampMs = call?.timestampMs ?: System.currentTimeMillis()
        val durationSec = call?.durationSec
        val startedAt = isoFmt.format(Date(timestampMs))
        val endedAt = durationSec?.let { isoFmt.format(Date(timestampMs + it * 1000L)) }
        val simSlot = call?.phoneAccountId?.toIntOrNull()
        val direction = call?.direction ?: com.ekotak.teamtalk.domain.model.CallDirection.OUTBOUND
        // Bez wpisu w rejestrze telefonu nie ma czasu ani długości rozmowy, po
        // których da się dopasować plik nagrania — wtedy nagrania nie szukamy.
        if (call != null) {
            enqueueRecordingUpload(timestampMs, durationSec, phone, startedAt, endedAt, direction, simSlot)
        }
        runCatching {
            callLogRepository.createCallLog(
                phoneNumber = phone,
                direction   = direction,
                startedAt   = startedAt,
                endedAt     = endedAt,
                durationSec = durationSec,
                simSlot     = simSlot,
            )
        }
    }

    /**
     * Nagranie rozmowy z systemowej nagrywarki idzie do transkrypcji w tle —
     * worker sam poczeka, aż plik się pojawi, i na zasięg. Nieodebrane i
     * kilkusekundowe połączenia pomijamy: nie ma w nich czego streszczać.
     */
    private fun enqueueRecordingUpload(
        timestampMs: Long,
        durationSec: Int?,
        phone: String,
        startedAt: String,
        endedAt: String?,
        direction: com.ekotak.teamtalk.domain.model.CallDirection,
        simSlot: Int?,
    ) {
        if (!CallRecordingPreferences.isEnabled(applicationContext)) return
        if (direction == com.ekotak.teamtalk.domain.model.CallDirection.MISSED) return
        if (durationSec != null && durationSec < CallRecordingUploadWorker.MIN_CALL_SEC) return
        CallRecordingUploadWorker.enqueue(
            context = applicationContext,
            callStartMs = timestampMs,
            callEndMs = timestampMs + (durationSec ?: 0) * 1000L,
            durationSec = durationSec,
            phone = phone,
            startedAt = startedAt,
            endedAt = endedAt,
            direction = direction,
            simSlot = simSlot,
        )
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
