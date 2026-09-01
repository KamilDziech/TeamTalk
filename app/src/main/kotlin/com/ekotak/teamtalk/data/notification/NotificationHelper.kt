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
