package com.ekotak.teamtalk.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ekotak.teamtalk.data.local.preferences.SessionPreferences
import com.ekotak.teamtalk.data.notification.NotificationHelper
import com.ekotak.teamtalk.domain.repository.LeadRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Wysyła leady zapisane w kreatorze bez zasięgu (ustalenie 2026-09-13).
 *
 * Handlowiec widział „zapisano, wyśle się samo", więc odmowa serwera nie może
 * przejść bez echa — lead z kolejki znika i bez powiadomienia nikt by nie
 * wiedział, że klient nie trafił do lejka.
 */
@HiltWorker
class LeadSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val leads: LeadRepository,
    private val sessionPreferences: SessionPreferences,
    private val notifications: NotificationHelper,
) : CoroutineWorker(context, params) {

    companion object {
        const val UNIQUE_NAME = "lead_sync"
    }

    override suspend fun doWork(): Result {
        sessionPreferences.token.first() ?: return Result.success()

        val result = runCatching { leads.syncPending() }.getOrNull() ?: return Result.retry()

        for ((client, reason) in result.rejected) {
            notifications.showLeadNotification(
                title = "Lead nie trafił do lejka",
                text = "$client — $reason. Wpisz go ponownie.",
                notificationId = client.hashCode(),
            )
        }

        return if (result.incomplete) Result.retry() else Result.success()
    }
}
