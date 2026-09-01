package com.ekotak.teamtalk

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.ekotak.teamtalk.data.notification.NotificationHelper
import com.ekotak.teamtalk.data.sync.TaskSyncScheduler
import com.ekotak.teamtalk.service.CallMonitorService
import com.ekotak.teamtalk.worker.MentionsWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class TeamTalkApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var taskSyncScheduler: TaskSyncScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        scheduleMentionsPolling()
        // Proces mógł zginąć z pełną kolejką zmian — przy starcie prosimy
        // o jej opróżnienie. Pusta kolejka kończy robotnika od razu.
        taskSyncScheduler.scheduleSync()
    }

    /**
     * Odpytywanie o wywołania (@) co 15 minut — board360 nie ma pusha, a
     * WorkManager i tak nie pozwala częściej. `KEEP` zostawia już zaplanowaną
     * pracę: przeładowanie procesu nie ma resetować odliczania. Bez sesji
     * robotnik kończy się od razu, więc planujemy raz, przy starcie.
     */
    private fun scheduleMentionsPolling() {
        val request = PeriodicWorkRequestBuilder<MentionsWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            MentionsWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(
                    NotificationHelper.CHANNEL_ID,
                    "Nieodebrane połączenia",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Powiadomienia o nowych nieodebranych połączeniach od klientów"
                }
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    NotificationHelper.POST_CALL_CHANNEL_ID,
                    "Notatka po rozmowie",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Prośba o dodanie notatki po zakończonej rozmowie"
                }
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    NotificationHelper.MENTIONS_CHANNEL_ID,
                    "Wywołania w zadaniach",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Ktoś wywołał Cię przez @ w komentarzu zadania"
                }
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    CallMonitorService.CHANNEL_ID,
                    "Monitorowanie połączenia",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Aktywne podczas trwania rozmowy"
                }
            )
        }
    }
}
