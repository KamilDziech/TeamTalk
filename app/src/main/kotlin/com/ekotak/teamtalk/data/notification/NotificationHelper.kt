package com.ekotak.teamtalk.data.notification

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ekotak.teamtalk.MainActivity
import com.ekotak.teamtalk.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NotificationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        const val CHANNEL_ID = "missed_calls"
        const val POST_CALL_CHANNEL_ID = "post_call_note"
        const val MENTIONS_CHANNEL_ID = "mentions"
        const val REMINDERS_CHANNEL_ID = "task_reminders"

        /** Alarmy okna SLA zleceń serwisowych (2 h przed i po przekroczeniu). */
        const val SLA_CHANNEL_ID = "service_sla"

        /** Przypomnienia o wydarzeniach kalendarza (30 min przed). */
        const val CALENDAR_CHANNEL_ID = "calendar_reminders"

        /** Urlopy: decyzja o moim wniosku i wniosek podwładnego do akceptacji. */
        const val LEAVE_CHANNEL_ID = "hr_leave"

        /** Poczta: wiadomość, której serwer nie przyjął przy wysyłce z kolejki. */
        const val EMAIL_CHANNEL_ID = "email_sync"

        /** Pliki deala: plik z kolejki, którego serwer nie przyjął. */
        const val DOCUMENTS_CHANNEL_ID = "documents_sync"

        /** Jedno powiadomienie na przypomnienia — kolejne podmienia poprzednie. */
        private const val REMINDER_NOTIFICATION_ID = 4200
        private val idCounter = AtomicInteger(1000)
    }

    /**
     * Wywołanie w komentarzu zadania. [title] to podpis dyskusji („Nowak · a3dc"),
     * [teaser] początek komentarza — resztę widać po wejściu w kartę zadania,
     * którą otwiera dotknięcie powiadomienia.
     *
     * Id powiadomienia liczymy z `taskId`: kolejne wiadomości w tej samej
     * dyskusji podmieniają poprzednie zamiast piętrzyć się w szufladzie.
     */
    fun showMentionNotification(title: String, teaser: String, taskId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) return

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_TASK_ID, taskId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            taskId.hashCode(),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, MENTIONS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ekotak)
            .setColor(ContextCompat.getColor(context, R.color.ekotak_green))
            .setContentTitle(title)
            .setContentText(teaser)
            .setStyle(NotificationCompat.BigTextStyle().bigText(teaser))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(taskId.hashCode(), notification)
    }

    /**
     * Przypomnienie o zadaniach na dziś i zaległych. [taskId] podane, gdy
     * zadanie jest jedno — wtedy dotknięcie prowadzi wprost w jego kartę;
     * przy kilku otwieramy listę, bo nie ma jednego oczywistego celu.
     *
     * Stałe id powiadomienia: kolejne przypomnienie ma podmienić poprzednie,
     * a nie piętrzyć się w szufladzie.
     */
    fun showTaskReminder(title: String, text: String, taskId: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) return

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (taskId != null) putExtra(MainActivity.EXTRA_TASK_ID, taskId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            REMINDER_NOTIFICATION_ID,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, REMINDERS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ekotak)
            .setColor(ContextCompat.getColor(context, R.color.ekotak_green))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(REMINDER_NOTIFICATION_ID, notification)
    }

    /**
     * Alarm okna SLA własnego zlecenia awaryjnego: dwie godziny przed końcem
     * i w chwili przekroczenia (ustalenie 2026-09-02). Id liczymy ze zlecenia,
     * więc drugi alarm podmienia pierwszy zamiast piętrzyć się w szufladzie.
     * Dotknięcie otwiera kartę zlecenia.
     */
    fun showSlaAlert(title: String, text: String, jobId: String, urgent: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) return

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_SERVICE_JOB_ID, jobId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            jobId.hashCode(),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, SLA_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ekotak)
            .setColor(ContextCompat.getColor(context, R.color.ekotak_green))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(
                if (urgent) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT,
            )
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(jobId.hashCode(), notification)
    }

    /**
     * Przypomnienie o wydarzeniu kalendarza — 30 minut przed początkiem, przy
     * całodniowych o 7:00 (ustalenie 2026-09-03). Id liczymy z wydarzenia, więc
     * kolejne przypomnienie o tym samym terminie podmienia poprzednie zamiast
     * piętrzyć się w szufladzie. Dotknięcie otwiera kalendarz na tym dniu.
     */
    fun showCalendarReminder(title: String, text: String, eventId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) return

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_CALENDAR_EVENT_ID, eventId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            eventId.hashCode(),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CALENDAR_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ekotak)
            .setColor(ContextCompat.getColor(context, R.color.ekotak_green))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(eventId.hashCode(), notification)
    }

    /**
     * Powiadomienie modułu Urlop. [team] decyduje, dokąd prowadzi dotknięcie:
     * do własnych wniosków albo do skrzynki zwierzchnika — bo to dwa różne
     * powody, dla których człowiek sięga po telefon.
     *
     * Treść niesie liczby (ile dni, ile zostało), a nie samo „sprawdź
     * aplikację": z ekranu blokady ma być widać, czy trzeba w ogóle wchodzić.
     * [notificationId] liczymy z identyfikatora wniosku, więc kolejna wiadomość
     * o tym samym urlopie podmienia poprzednią zamiast piętrzyć się w szufladzie.
     */
    fun showLeaveNotification(title: String, text: String, notificationId: Int, team: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) return

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_LEAVE_TAB, if (team) "team" else "mine")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, LEAVE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ekotak)
            .setColor(ContextCompat.getColor(context, R.color.ekotak_green))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    /**
     * Odmowa serwera przy wysyłce poczty zakolejkowanej bez zasięgu.
     *
     * To jedyne powiadomienie tego modułu i jest konieczne: człowiek widział na
     * ekranie wiadomość jako wysłaną, więc jej cichy zanik oznaczałby, że
     * czeka na odpowiedź na pismo, które nigdy nie wyszło. [notificationId]
     * liczymy z opisu wiadomości, żeby kolejna próba podmieniała poprzednią.
     */
    fun showEmailNotification(title: String, text: String, notificationId: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) return

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_EMAIL, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, EMAIL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ekotak)
            .setColor(ContextCompat.getColor(context, R.color.ekotak_green))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    /**
     * Odmowa serwera przy wysyłce pliku deala zakolejkowanego bez zasięgu.
     *
     * Konieczne z tego samego powodu co przy poczcie: zdjęcie z montażu było
     * widoczne na karcie, więc jego cichy zanik znaczyłby, że nikt go już nie
     * zrobi drugi raz — a oryginał z aparatu bywa w międzyczasie skasowany.
     * Bez wskazania karty w intencji: deal zna dopiero treść powiadomienia,
     * a wchodzi się do niego z lejka.
     */
    fun showDocumentNotification(title: String, text: String, notificationId: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) return

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, DOCUMENTS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ekotak)
            .setColor(ContextCompat.getColor(context, R.color.ekotak_green))
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    fun showMissedCallNotification(callerLabel: String, callLogId: String? = null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) return

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            if (callLogId != null) putExtra(MainActivity.EXTRA_CALL_LOG_ID, callLogId)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            idCounter.get(),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ekotak)
            .setColor(ContextCompat.getColor(context, R.color.ekotak_green))
            .setContentTitle("Nieodebrane połączenie")
            .setContentText("Od: $callerLabel")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context).notify(idCounter.getAndIncrement(), notification)
    }

    fun showPostCallNoteNotification(phone: String?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
        ) return

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_POST_CALL_PHONE, phone ?: "")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            idCounter.get(),
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val label = if (!phone.isNullOrBlank()) phone else "Nieznany numer"
        val notification = NotificationCompat.Builder(context, POST_CALL_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_ekotak)
            .setColor(ContextCompat.getColor(context, R.color.ekotak_green))
            .setContentTitle("Dodaj notatkę z rozmowy")
            .setContentText("Rozmowa z: $label")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setFullScreenIntent(pendingIntent, true)
            .build()

        NotificationManagerCompat.from(context).notify(idCounter.getAndIncrement(), notification)
    }
}
