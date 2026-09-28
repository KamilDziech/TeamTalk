package com.ekotak.teamtalk.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import com.ekotak.teamtalk.MainActivity
import com.ekotak.teamtalk.data.local.preferences.CallRecordingPreferences
import com.ekotak.teamtalk.data.notification.NotificationHelper
import com.ekotak.teamtalk.data.scanner.DeviceCallLogReader
import com.ekotak.teamtalk.domain.model.CallDirection
import com.ekotak.teamtalk.domain.repository.CallLogRepository
import com.ekotak.teamtalk.worker.CallRecordingUploadWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Obsługa zakończonej rozmowy: wpis w historii, kolejka nagrania, ekran notatki.
 * Wydzielone z [CallMonitorService], bo Android 14 potrafi odmówić startu usługi
 * pierwszoplanowej z tła (ForegroundServiceStartNotAllowedException) — wtedy tę
 * samą robotę wykonuje [com.ekotak.teamtalk.worker.CallEndedWorker].
 */
@Singleton
class PostCallHandler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val deviceCallLogReader: DeviceCallLogReader,
    private val notificationHelper: NotificationHelper,
    private val callLogRepository: CallLogRepository,
) {
    private val isoFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    suspend fun handle(callStartMs: Long, phoneAccountId: String?) {
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
    }

    private fun openNoteScreen(phone: String?) {
        val canOverlay = Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)
        if (canOverlay) {
            wakeScreen()
            val activityIntent = Intent(context, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP,
                )
                putExtra(MainActivity.EXTRA_OPEN_POST_CALL_NOTE, true)
                putExtra(MainActivity.EXTRA_POST_CALL_PHONE, phone ?: "")
            }
            // Start aktywności z tła bywa zablokowany mimo nakładki — wtedy powiadomienie.
            runCatching { context.startActivity(activityIntent) }
                .onFailure { notificationHelper.showPostCallNoteNotification(phone) }
        } else {
            notificationHelper.showPostCallNoteNotification(phone)
        }
    }

    @Suppress("DEPRECATION")
    private fun wakeScreen() {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isInteractive) {
            val wl = pm.newWakeLock(
                PowerManager.FULL_WAKE_LOCK or
                    PowerManager.ACQUIRE_CAUSES_WAKEUP or
                    PowerManager.ON_AFTER_RELEASE,
                "com.ekotak.teamtalk:post_call_wake",
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
        val direction = call?.direction ?: CallDirection.OUTBOUND
        // Bez wpisu w rejestrze telefonu nie ma czasu ani długości rozmowy, po
        // których da się dopasować plik nagrania — wtedy nagrania nie szukamy.
        if (call != null) {
            enqueueRecordingUpload(timestampMs, durationSec, phone, startedAt, endedAt, direction, simSlot)
        }
        runCatching {
            callLogRepository.createCallLog(
                phoneNumber = phone,
                direction = direction,
                startedAt = startedAt,
                endedAt = endedAt,
                durationSec = durationSec,
                simSlot = simSlot,
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
        direction: CallDirection,
        simSlot: Int?,
    ) {
        if (!CallRecordingPreferences.isEnabled(context)) return
        if (direction == CallDirection.MISSED) return
        if (durationSec != null && durationSec < CallRecordingUploadWorker.MIN_CALL_SEC) return
        CallRecordingUploadWorker.enqueue(
            context = context,
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
}
