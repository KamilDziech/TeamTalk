package com.ekotak.teamtalk.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.ekotak.teamtalk.MainActivity
import com.ekotak.teamtalk.R
import com.ekotak.teamtalk.data.meeting.MeetingRecorder
import com.ekotak.teamtalk.data.notification.NotificationHelper
import com.ekotak.teamtalk.domain.repository.MeetingRepository
import com.ekotak.teamtalk.worker.MeetingRecordingUploadWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Nagrywanie spotkania w tle (moduł Spotkanie, D1). Usługa pierwszoplanowa typu
 * `microphone`: dopóki trwa, system nie odbierze mikrofonu także przy
 * zablokowanym ekranie, a w pasku wisi powiadomienie z Pauzą i Zakończ.
 *
 * Źródłem prawdy o stanie spotkania jest serwer. Pauza/Wznów/Zakończ idą
 * najpierw do API; bez zasięgu działają lokalnie od razu, a polecenie czeka
 * w [pending] i leci przy następnym obrocie pętli. Co [POLL_MS] usługa pyta
 * o stan — pauza albo koniec kliknięte w panelu zatrzymują nagranie tutaj.
 * Po zakończeniu plik przejmuje [MeetingRecordingUploadWorker].
 */
@AndroidEntryPoint
class MeetingRecordingService : Service() {

    @Inject lateinit var recorder: MeetingRecorder
    @Inject lateinit var repository: MeetingRepository

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pollJob: Job? = null
    @Volatile private var pending: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val id = intent.getStringExtra(EXTRA_MEETING_ID) ?: return stopIfIdle()
                val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
                // startForeground przed mikrofonem — Android 14 wymaga typu `microphone`.
                goForeground(title)
                if (!recorder.isActive) {
                    try {
                        recorder.start(id, title)
                    } catch (e: Exception) {
                        Log.e(TAG, "Nie udało się włączyć mikrofonu: ${e.message}")
                        return stopIfIdle()
                    }
                }
                updateNotification()
                startPolling()
            }
            ACTION_PAUSE -> command("pause")
            ACTION_RESUME -> command("resume")
            ACTION_FINISH -> finish(sendFinish = true)
            else -> return stopIfIdle()
        }
        return START_NOT_STICKY
    }

    private fun stopIfIdle(): Int {
        if (!recorder.isActive) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun command(cmd: String) {
        val id = recorder.state.value.meetingId ?: return
        if (cmd == "pause") recorder.pause() else recorder.resume()
        updateNotification()
        pending = cmd
        scope.launch { flushPending(id) }
    }

    private suspend fun flushPending(id: String) {
        val cmd = pending ?: return
        try {
            repository.command(id, cmd)
            if (pending == cmd) pending = null
        } catch (e: Exception) {
            // Bez zasięgu — ponowi pętla.
            Log.i(TAG, "Polecenie $cmd czeka na sieć: ${e.message}")
        }
    }

    private fun finish(sendFinish: Boolean) {
        val state = recorder.state.value
        val id = state.meetingId
        val done = recorder.stop()
        pollJob?.cancel()
        pending = null
        if (done != null) {
            MeetingRecordingUploadWorker.enqueue(this, done.meetingId, done.file, done.durationSec, state.title)
        }
        if (sendFinish && id != null) {
            // Najlepiej jak się da — upload i tak zamyka spotkanie po stronie serwera.
            scope.launch { runCatching { repository.command(id, "finish") } }
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = scope.launch {
            while (isActive && recorder.isActive) {
                delay(POLL_MS)
                val id = recorder.state.value.meetingId ?: break
                flushPending(id)
                val m = try {
                    repository.get(id)
                } catch (_: Exception) {
                    continue
                }
                if (pending != null) continue
                // Nagrywarkę dotykamy tylko z wątku głównego (tam też chodzą akcje z powiadomienia).
                val ended = withContext(Dispatchers.Main) {
                    val phase = recorder.state.value.phase
                    when {
                        m.status == "paused" && phase == MeetingRecorder.Phase.RECORDING -> {
                            recorder.pause(); updateNotification(); false
                        }
                        m.status == "live" && phase == MeetingRecorder.Phase.PAUSED -> {
                            recorder.resume(); updateNotification(); false
                        }
                        m.status != "live" && m.status != "paused" -> true
                        else -> false
                    }
                }
                if (ended) {
                    scope.launch(Dispatchers.Main) { finish(sendFinish = false) }
                    break
                }
            }
        }
    }

    private fun goForeground(title: String) {
        val n = buildNotification(title)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, n, type)
    }

    private fun updateNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return
        NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, buildNotification(recorder.state.value.title))
    }

    private fun buildNotification(title: String): Notification {
        val state = recorder.state.value
        val paused = state.phase == MeetingRecorder.Phase.PAUSED
        val open = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                state.meetingId?.let { putExtra(MainActivity.EXTRA_MEETING_ID, it) }
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(this, NotificationHelper.MEETING_RECORDING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ekotak)
            .setColor(ContextCompat.getColor(this, R.color.ekotak_green))
            .setContentTitle(if (paused) "Spotkanie — pauza" else "Nagrywam spotkanie")
            .setContentText(title.ifBlank { "Spotkanie" })
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(open)
            .addAction(
                0,
                if (paused) "Wznów" else "Pauza",
                servicePending(if (paused) ACTION_RESUME else ACTION_PAUSE, 2),
            )
            .addAction(0, "Zakończ", servicePending(ACTION_FINISH, 3))
        if (!paused && state.phase == MeetingRecorder.Phase.RECORDING) {
            builder.setUsesChronometer(true).setWhen(System.currentTimeMillis() - state.recordedNowMs())
        }
        return builder.build()
    }

    private fun servicePending(action: String, code: Int): PendingIntent = PendingIntent.getService(
        this,
        code,
        Intent(this, MeetingRecordingService::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "MeetingRecording"
        private const val NOTIFICATION_ID = 7299
        private const val POLL_MS = 5_000L
        const val ACTION_START = "com.ekotak.teamtalk.meeting.START"
        const val ACTION_PAUSE = "com.ekotak.teamtalk.meeting.PAUSE"
        const val ACTION_RESUME = "com.ekotak.teamtalk.meeting.RESUME"
        const val ACTION_FINISH = "com.ekotak.teamtalk.meeting.FINISH"
        const val EXTRA_MEETING_ID = "meeting_id"
        const val EXTRA_TITLE = "title"

        /** Musi być wołane z aplikacji na pierwszym planie, z przyznanym RECORD_AUDIO. */
        fun start(context: Context, meetingId: String, title: String) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, MeetingRecordingService::class.java)
                    .setAction(ACTION_START)
                    .putExtra(EXTRA_MEETING_ID, meetingId)
                    .putExtra(EXTRA_TITLE, title),
            )
        }

        fun send(context: Context, action: String) {
            context.startService(Intent(context, MeetingRecordingService::class.java).setAction(action))
        }
    }
}
