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
import com.ekotak.teamtalk.data.sync.AuditSyncScheduler
import com.ekotak.teamtalk.data.sync.CalendarSyncScheduler
import com.ekotak.teamtalk.data.sync.LeaveSyncScheduler
import com.ekotak.teamtalk.data.sync.OrderSyncScheduler
import com.ekotak.teamtalk.data.sync.ServiceSyncScheduler
import com.ekotak.teamtalk.data.sync.SettlementSyncScheduler
import com.ekotak.teamtalk.data.sync.TaskSyncScheduler
import com.ekotak.teamtalk.service.CallMonitorService
import com.ekotak.teamtalk.worker.CalendarReminderWorker
import com.ekotak.teamtalk.worker.LeaveNotifyWorker
import com.ekotak.teamtalk.worker.MentionsWorker
import com.ekotak.teamtalk.worker.ServiceSlaWorker
import com.ekotak.teamtalk.worker.TaskReminderWorker
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class TeamTalkApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var taskSyncScheduler: TaskSyncScheduler

    @Inject lateinit var serviceSyncScheduler: ServiceSyncScheduler

    @Inject lateinit var calendarSyncScheduler: CalendarSyncScheduler

    @Inject lateinit var auditSyncScheduler: AuditSyncScheduler

    @Inject lateinit var orderSyncScheduler: OrderSyncScheduler

    @Inject lateinit var leaveSyncScheduler: LeaveSyncScheduler

    @Inject lateinit var settlementSyncScheduler: SettlementSyncScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        scheduleMentionsPolling()
        scheduleTaskReminders()
        scheduleSlaAlerts()
        scheduleCalendarReminders()
        scheduleLeaveNotifications()
        // Proces mógł zginąć z pełną kolejką zmian — przy starcie prosimy
        // o jej opróżnienie. Pusta kolejka kończy robotnika od razu.
        taskSyncScheduler.scheduleSync()
        serviceSyncScheduler.scheduleSync()
        calendarSyncScheduler.scheduleSync()
        auditSyncScheduler.scheduleSync()
        orderSyncScheduler.scheduleSync()
        leaveSyncScheduler.scheduleSync()
        settlementSyncScheduler.scheduleSync()
    }

    /**
     * Alarmy okna SLA. Co 15 minut — krócej WorkManager nie pozwala, a okno
     * awarii to 24 h, więc kwadrans rozdzielczości w zupełności wystarcza.
     * Bez warunku sieci: robotnik liczy z cache, więc ostrzeże także wtedy, gdy
     * telefon siedzi w kotłowni bez zasięgu.
     */
    private fun scheduleSlaAlerts() {
        val request = PeriodicWorkRequestBuilder<ServiceSlaWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            ServiceSlaWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
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

    /**
     * Przypomnienia o zadaniach na dziś i zaległych. Co sześć godzin, nie co
     * kwadrans: terminy nie zmieniają się tak szybko, a sam robotnik i tak
     * pilnuje, żeby zatrąbić raz dziennie. Bez warunku sieci — liczy z cache,
     * więc przypomni także wtedy, gdy telefon jest poza zasięgiem.
     */
    private fun scheduleTaskReminders() {
        val request = PeriodicWorkRequestBuilder<TaskReminderWorker>(6, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            TaskReminderWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * Przypomnienia o wydarzeniach kalendarza. Co 15 minut — krócej WorkManager
     * nie pozwala, a przypominamy na 30 minut przed, więc kwadrans rozdzielczości
     * mieści się w tym oknie. Bez warunku sieci: robotnik liczy z cache, więc
     * przypomni także w aucie bez zasięgu.
     */
    private fun scheduleCalendarReminders() {
        val request = PeriodicWorkRequestBuilder<CalendarReminderWorker>(15, TimeUnit.MINUTES).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            CalendarReminderWorker.UNIQUE_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /**
     * Powiadomienia urlopowe: decyzja o moim wniosku i wniosek podwładnego do
     * akceptacji. Co pół godziny, a nie co kwadrans jak wywołania (@) — urlop
     * to nie rozmowa, pół godziny zwłoki niczego nie psuje, a bateria zostaje
     * w telefonie. Z warunkiem sieci, bo robotnik dopytuje serwer.
     */
    private fun scheduleLeaveNotifications() {
        val request = PeriodicWorkRequestBuilder<LeaveNotifyWorker>(30, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            LeaveNotifyWorker.UNIQUE_NAME,
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
                    NotificationHelper.REMINDERS_CHANNEL_ID,
                    "Przypomnienia o zadaniach",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Zadania z terminem na dziś albo po terminie"
                }
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    NotificationHelper.SLA_CHANNEL_ID,
                    "Okno SLA serwisu",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Twoje zlecenie awaryjne zbliża się do końca okna SLA"
                }
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    NotificationHelper.CALENDAR_CHANNEL_ID,
                    "Przypomnienia o wydarzeniach",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Wydarzenie z Twojego kalendarza zaczyna się za pół godziny"
                }
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    NotificationHelper.LEAVE_CHANNEL_ID,
                    "Urlopy",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Decyzja o Twoim wniosku urlopowym i wnioski podwładnych do akceptacji"
                }
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    NotificationHelper.EMAIL_CHANNEL_ID,
                    "Poczta",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Wiadomość zapisana bez zasięgu, której serwer nie przyjął"
                }
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    NotificationHelper.DOCUMENTS_CHANNEL_ID,
                    "Pliki deala",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Plik wgrany bez zasięgu, którego serwer nie przyjął"
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
