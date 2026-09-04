package com.ekotak.teamtalk.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ekotak.teamtalk.data.local.preferences.SessionPreferences
import com.ekotak.teamtalk.domain.repository.ServiceRepository
import com.ekotak.teamtalk.domain.repository.ServiceSyncResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Wysyła zmiany serwisowe zrobione bez zasięgu — w tym całe zgłoszenia awarii
 * spisane w kotłowni (ustalenie 2026-09-02).
 *
 * Zamawia go repozytorium przy każdej zakolejkowanej zmianie, z warunkiem sieci,
 * więc nie ma tu żadnego odpytywania: system budzi robotnika sam po powrocie
 * łączności. Bez sesji nie ma dokąd wysyłać — kończymy sukcesem, żeby
 * WorkManager nie dobijał się w kółko po wylogowaniu.
 */
@HiltWorker
class ServiceSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val service: ServiceRepository,
    private val sessionPreferences: SessionPreferences,
) : CoroutineWorker(context, params) {

    companion object {
        const val UNIQUE_NAME = "service_sync"
    }

    override suspend fun doWork(): Result {
        sessionPreferences.token.first() ?: return Result.success()

        return when (runCatching { service.syncPendingMutations() }.getOrNull()) {
            ServiceSyncResult.DONE -> Result.success()
            // Sieć znowu zawiodła — WorkManager ponowi z własnym odstępem,
            // a kolejka jest w bazie, więc nic nie ginie.
            else -> Result.retry()
        }
    }
}
