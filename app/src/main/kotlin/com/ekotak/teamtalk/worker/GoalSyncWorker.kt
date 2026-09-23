package com.ekotak.teamtalk.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ekotak.teamtalk.data.local.preferences.SessionPreferences
import com.ekotak.teamtalk.data.notification.NotificationHelper
import com.ekotak.teamtalk.domain.repository.GoalRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Wysyła cele, wpisy ręczne i zamknięcia okresów zapisane bez zasięgu
 * (decyzja 2026-09-23 — pełny offline modułu Cele).
 *
 * Zamawia go repozytorium przy każdym zakolejkowanym zapisie, z warunkiem
 * sieci, więc nie ma tu odpytywania — system budzi robotnika sam po powrocie
 * łączności. Bez sesji nie ma dokąd wysyłać: kończymy sukcesem, żeby
 * WorkManager nie dobijał się w kółko po wylogowaniu.
 *
 * Odmowa serwera nie może przejść bez echa: człowiek widział cel na ekranie
 * jako zapisany, więc gdy okazuje się, że okres zdążył się zamknąć albo cel
 * zniknął, mówimy o tym wprost powiadomieniem.
 */
@HiltWorker
class GoalSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val goals: GoalRepository,
    private val sessionPreferences: SessionPreferences,
    private val notifications: NotificationHelper,
) : CoroutineWorker(context, params) {

    companion object {
        const val UNIQUE_NAME = "goal_sync"
    }

    override suspend fun doWork(): Result {
        sessionPreferences.token.first() ?: return Result.success()

        val result = runCatching { goals.syncPendingMutations() }.getOrNull()
            // Sieć znowu zawiodła — WorkManager ponowi z własnym odstępem,
            // a kolejka siedzi w bazie, więc nic nie ginie.
            ?: return Result.retry()

        for (rejection in result.rejected) {
            notifications.showGoalNotification(
                title = "Cel nie zapisał się na serwerze",
                text = listOfNotNull(rejection.label, rejection.reason).joinToString(" — "),
                notificationId = rejection.label.hashCode(),
            )
        }

        return if (result.incomplete) Result.retry() else Result.success()
    }
}
