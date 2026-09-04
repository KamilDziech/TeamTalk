package com.ekotak.teamtalk.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ekotak.teamtalk.data.local.preferences.SessionPreferences
import com.ekotak.teamtalk.data.notification.NotificationHelper
import com.ekotak.teamtalk.domain.model.CalendarEvent
import com.ekotak.teamtalk.domain.model.RsvpStatus
import com.ekotak.teamtalk.domain.repository.CalendarRepository
import com.ekotak.teamtalk.presentation.calendar.formatHour
import com.ekotak.teamtalk.presentation.calendar.isoUtc
import com.ekotak.teamtalk.presentation.calendar.millisToDate
import com.ekotak.teamtalk.presentation.calendar.startMillis
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Przypomnienie o wydarzeniu: 30 minut przed początkiem, a przy całodniowych
 * o 7:00 tego dnia (ustalenie 2026-09-03, `design/mockups/modul-kalendarz.html`).
 *
 * Liczy z cache Room, więc przypomni także bez zasięgu — odświeżenie z serwera
 * jest próbą, nie warunkiem. Trąbimy tylko o SWOICH terminach: własnych albo
 * takich, w których jesteśmy uczestnikiem i nie odmówiliśmy. Każdy alarm
 * brzmi raz — znacznik `eventId:startAt` siedzi w preferencjach, więc robotnik
 * chodzący co kwadrans nie powtórzy go cztery razy na godzinę, a przesunięty
 * termin przypomni o sobie na nowo (bo zmienia się druga część znacznika).
 */
@HiltWorker
class CalendarReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val calendar: CalendarRepository,
    private val sessionPreferences: SessionPreferences,
    private val notifications: NotificationHelper,
) : CoroutineWorker(context, params) {

    companion object {
        const val UNIQUE_NAME = "calendar_reminders"

        /** Ile przed początkiem przypominamy o wydarzeniu z godziną. */
        private const val LEAD_MS = 30 * 60 * 1000L

        /** Godzina przypomnienia o wydarzeniu całodniowym. */
        private const val ALL_DAY_HOUR = 7

        /** Ile po terminie alarm jeszcze ma sens — potem tylko zaśmieca szufladę. */
        private const val STALE_MS = 2 * 60 * 60 * 1000L
    }

    override suspend fun doWork(): Result {
        val session = sessionPreferences.session.first() ?: return Result.success()
        val zone = ZoneId.systemDefault()
        val now = System.currentTimeMillis()

        // Świeże dane, jeśli są: dziś i dwa dni do przodu wystarczą alarmowi.
        val today = LocalDate.now(zone)
        runCatching {
            calendar.refresh(
                fromIso = isoUtc(today.atStartOfDay(zone).toInstant().toEpochMilli()),
                toIso = isoUtc(today.plusDays(3).atStartOfDay(zone).toInstant().toEpochMilli()),
            )
        }

        val mine = calendar.observe().first().events.filter { it.concernsMe(session.userId) }

        // Znaczniki wydarzeń, które już się odbyły, nie mają czego pilnować —
        // niech nie rosną w nieskończoność w preferencjach.
        sessionPreferences.retainCalendarAlerts(mine.map { it.marker() }.toSet())
        val sent = sessionPreferences.calendarAlertsSent.first()

        for (event in mine) {
            val start = event.startMillis() ?: continue
            val alertAt = if (event.allDay) {
                millisToDate(start).atTime(LocalTime.of(ALL_DAY_HOUR, 0))
                    .atZone(zone).toInstant().toEpochMilli()
            } else {
                start - LEAD_MS
            }
            if (now < alertAt) continue
            // Terminy sprzed kilku godzin przypominać nie ma po co: telefon mógł
            // leżeć wyłączony, a powiadomienie „za 30 minut" o czymś, co było
            // rano, jest gorsze niż jego brak.
            if (now > alertAt + STALE_MS) continue

            val marker = event.marker()
            if (marker in sent) continue

            notifications.showCalendarReminder(
                title = if (event.allDay) "Dziś: ${event.title}" else event.title,
                text = reminderText(event, start),
                eventId = event.id,
            )
            sessionPreferences.markCalendarAlertSent(marker)
        }
        return Result.success()
    }

    /** Czy wydarzenie dotyczy zalogowanego: jest jego albo bierze w nim udział. */
    private fun CalendarEvent.concernsMe(userId: String): Boolean = when {
        assigneeId == userId -> true
        else -> attendees.any { it.id == userId && it.response != RsvpStatus.DECLINED }
    }

    private fun CalendarEvent.marker(): String = "$id:$startAt"

    private fun reminderText(event: CalendarEvent, start: Long): String {
        val time = if (event.allDay) "cały dzień" else "o ${formatHour(start)}"
        val place = event.location?.takeIf { it.isNotBlank() }
        return listOfNotNull(time, place).joinToString(" · ")
    }
}
