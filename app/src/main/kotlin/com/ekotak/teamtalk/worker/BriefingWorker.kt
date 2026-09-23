package com.ekotak.teamtalk.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ekotak.teamtalk.data.local.preferences.SessionPreferences
import com.ekotak.teamtalk.data.notification.NotificationHelper
import com.ekotak.teamtalk.domain.repository.BriefingRepository
import com.ekotak.teamtalk.presentation.crm.parseIsoMillis
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlin.math.absoluteValue

/**
 * Komunikaty odprawy — w tym „Harmonogram na tydzień…" z modułu Harmonogram.
 *
 * board360 nie wysyła pusha, więc pytamy sami, co 15 minut (krócej WorkManager
 * nie pozwala) — ten sam układ co przy wywołaniach w zadaniach. Trąbimy raz na
 * komunikat: znacznik `briefingSeenAt` trzyma czas publikacji ostatniego, który
 * pokazaliśmy, więc nic nie wraca do skutku.
 *
 * Pierwsze uruchomienie po zalogowaniu nie zasypuje szuflady zaległą
 * korespondencją — zapamiętujemy stan i odzywamy się dopiero od następnego.
 * Bez sesji robotnik kończy się sukcesem, żeby WorkManager nie próbował w kółko
 * po wylogowaniu.
 */
@HiltWorker
class BriefingWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val briefing: BriefingRepository,
    private val sessionPreferences: SessionPreferences,
    private val notifications: NotificationHelper,
) : CoroutineWorker(context, params) {

    companion object {
        const val UNIQUE_NAME = "briefing_poll"
    }

    override suspend fun doWork(): Result {
        sessionPreferences.token.first() ?: return Result.success()

        val seenAt = sessionPreferences.briefingSeenAt.first()
        val items = runCatching { briefing.inbox() }.getOrElse { return Result.retry() }

        var newest = seenAt
        for (item in items) {
            val at = parseIsoMillis(item.publishedAt) ?: continue
            if (at > newest) newest = at
            if (at <= seenAt || seenAt == 0L) continue
            // Odhaczony komunikat czytał już człowiek — w panelu albo tutaj.
            if (item.ackAt != null) continue
            notifications.showBriefingNotification(
                title = item.title,
                text = item.body.lineSequence().take(4).joinToString("\n"),
                notificationId = item.id.hashCode().absoluteValue,
                urgent = item.urgent,
            )
        }

        if (newest > seenAt) sessionPreferences.saveBriefingSeenAt(newest)
        return Result.success()
    }
}
