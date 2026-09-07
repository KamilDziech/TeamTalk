package com.ekotak.teamtalk.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ekotak.teamtalk.data.local.preferences.SessionPreferences
import com.ekotak.teamtalk.data.notification.NotificationHelper
import com.ekotak.teamtalk.domain.repository.LeaveRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Wysyła wnioski urlopowe i decyzje zapisane bez zasięgu (ustalenie 2026-09-06).
 *
 * Zamawia go repozytorium przy każdym zakolejkowanym zapisie, z warunkiem sieci,
 * więc nie ma tu odpytywania — system budzi robotnika sam po powrocie łączności.
 * Bez sesji nie ma dokąd wysyłać: kończymy sukcesem, żeby WorkManager nie dobijał
 * się w kółko po wylogowaniu.
 *
 * Odmowa serwera nie może przejść bez echa. Człowiek widział wniosek na ekranie
 * jako złożony, więc gdy okazuje się, że ktoś w międzyczasie zajął ten termin
 * albo zwierzchnik zdążył go rozpatrzyć, mówimy o tym wprost powiadomieniem —
 * inaczej urlop znika po cichu i dowiaduje się o tym dopiero w pracy.
 */
@HiltWorker
class LeaveSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val leave: LeaveRepository,
    private val sessionPreferences: SessionPreferences,
    private val notifications: NotificationHelper,
) : CoroutineWorker(context, params) {

    companion object {
        const val UNIQUE_NAME = "leave_sync"
    }

    override suspend fun doWork(): Result {
        sessionPreferences.token.first() ?: return Result.success()

        val result = runCatching { leave.syncPendingMutations() }.getOrNull()
            // Sieć znowu zawiodła — WorkManager ponowi z własnym odstępem,
            // a kolejka siedzi w bazie, więc nic nie ginie.
            ?: return Result.retry()

        for (rejection in result.rejected) {
            notifications.showLeaveNotification(
                title = "Urlop odrzucony przy wysyłce",
                text = listOfNotNull(rejection.label, rejection.reason).joinToString(" — "),
                notificationId = rejection.label.hashCode(),
                team = false,
            )
        }

        return if (result.incomplete) Result.retry() else Result.success()
    }
}
