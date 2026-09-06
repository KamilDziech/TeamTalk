package com.ekotak.teamtalk.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ekotak.teamtalk.data.local.preferences.SessionPreferences
import com.ekotak.teamtalk.domain.repository.AuditRepository
import com.ekotak.teamtalk.domain.repository.AuditSyncResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Wysyła audyty zapisane bez zasięgu.
 *
 * Zamawia go repozytorium przy każdym zakolejkowanym zapisie, z warunkiem sieci
 * — system budzi robotnika sam, gdy telefon wróci w zasięg, więc nie ma tu
 * żadnego odpytywania. Drugi raz woła go start aplikacji, na wypadek gdyby
 * proces zginął z pełną kolejką: audyt bywa wypełniany godzinę, a telefon
 * potrafi w tym czasie ubić aplikację w tle.
 *
 * Bez sesji nie ma dokąd wysyłać: kończymy sukcesem, żeby WorkManager nie
 * dobijał się w kółko po wylogowaniu. Kolejka poczeka do następnego logowania.
 */
@HiltWorker
class AuditSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val audits: AuditRepository,
    private val sessionPreferences: SessionPreferences,
) : CoroutineWorker(context, params) {

    companion object {
        const val UNIQUE_NAME = "audit_sync"
    }

    override suspend fun doWork(): Result {
        sessionPreferences.token.first() ?: return Result.success()

        return when (runCatching { audits.syncPendingMutations() }.getOrNull()) {
            AuditSyncResult.DONE -> Result.success()
            // Sieć znowu zawiodła albo coś padło po drodze — WorkManager ponowi
            // z własnym odstępem, a kolejka jest w bazie, więc nic nie ginie.
            else -> Result.retry()
        }
    }
}
