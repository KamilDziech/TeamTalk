package com.ekotak.teamtalk.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ekotak.teamtalk.data.local.preferences.SessionPreferences
import com.ekotak.teamtalk.domain.repository.TaskRepository
import com.ekotak.teamtalk.domain.repository.TaskSyncResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Wysyła zmiany zadań zrobione bez zasięgu (kolejka offline, E3).
 *
 * Zamawia go repozytorium przy każdej zakolejkowanej zmianie, z warunkiem sieci
 * — system budzi robotnika sam, gdy telefon wróci w zasięg, więc nie ma tu
 * żadnego odpytywania. Drugi raz woła go start aplikacji, na wypadek gdyby
 * proces zginął z pełną kolejką.
 *
 * Bez sesji nie ma dokąd wysyłać: kończymy sukcesem, żeby WorkManager nie
 * dobijał się w kółko po wylogowaniu. Kolejka poczeka do następnego logowania.
 */
@HiltWorker
class TaskSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val tasks: TaskRepository,
    private val sessionPreferences: SessionPreferences,
) : CoroutineWorker(context, params) {

    companion object {
        const val UNIQUE_NAME = "task_sync"
    }

    override suspend fun doWork(): Result {
        sessionPreferences.token.first() ?: return Result.success()

        return when (runCatching { tasks.syncPendingMutations() }.getOrNull()) {
            TaskSyncResult.DONE -> Result.success()
            // Sieć znowu zawiodła albo coś padło po drodze — WorkManager ponowi
            // z własnym odstępem, a kolejka jest w bazie, więc nic nie ginie.
            else -> Result.retry()
        }
    }
}
