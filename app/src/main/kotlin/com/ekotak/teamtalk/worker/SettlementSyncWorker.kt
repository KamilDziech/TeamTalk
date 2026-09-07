package com.ekotak.teamtalk.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ekotak.teamtalk.data.local.preferences.SessionPreferences
import com.ekotak.teamtalk.domain.repository.SettlementRepository
import com.ekotak.teamtalk.domain.repository.SettlementSyncResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Wysyła decyzje zakładki „Rozliczenie" podjęte bez zasięgu: zatwierdzenia
 * i cofnięcia rozliczeń instalacji.
 *
 * Zamawia go repozytorium przy każdej zakolejkowanej decyzji, z warunkiem sieci
 * — system budzi robotnika sam, gdy telefon wróci w zasięg, więc nie ma tu
 * żadnego odpytywania. Drugi raz woła go start aplikacji, na wypadek gdyby
 * proces zginął z pełną kolejką: odbiór na budowie potrafi trwać godziny,
 * a telefon w tym czasie bywa ubijany w tle.
 *
 * Bez sesji nie ma dokąd wysyłać: kończymy sukcesem, żeby WorkManager nie
 * dobijał się w kółko po wylogowaniu. Kolejka poczeka do następnego logowania.
 */
@HiltWorker
class SettlementSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val settlements: SettlementRepository,
    private val sessionPreferences: SessionPreferences,
) : CoroutineWorker(context, params) {

    companion object {
        const val UNIQUE_NAME = "settlement_sync"
    }

    override suspend fun doWork(): Result {
        sessionPreferences.token.first() ?: return Result.success()

        return when (runCatching { settlements.syncPendingMutations() }.getOrNull()) {
            SettlementSyncResult.DONE -> Result.success()
            // Sieć znowu zawiodła albo coś padło po drodze — WorkManager ponowi
            // z własnym odstępem, a kolejka jest w bazie, więc nic nie ginie.
            else -> Result.retry()
        }
    }
}
