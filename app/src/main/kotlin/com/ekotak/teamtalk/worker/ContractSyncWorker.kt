package com.ekotak.teamtalk.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ekotak.teamtalk.data.local.preferences.SessionPreferences
import com.ekotak.teamtalk.domain.repository.ContractRepository
import com.ekotak.teamtalk.domain.repository.ContractSyncResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Wysyła zapisy zakładki „Umowa" zrobione bez zasięgu: wystawione umowy,
 * zgłoszone zmiany, decyzje zarządu, nowe linki do podpisu i unieważnienia.
 *
 * Zamawia go repozytorium przy każdym zakolejkowanym zapisie, z warunkiem sieci
 * — system budzi robotnika sam, gdy telefon wróci w zasięg. Drugi raz woła go
 * start aplikacji: wizyta u klienta bez zasięgu potrafi trwać godziny, a telefon
 * bywa w tym czasie ubijany w tle.
 *
 * Bez sesji nie ma dokąd wysyłać: kończymy sukcesem, żeby WorkManager nie
 * dobijał się w kółko po wylogowaniu. Kolejka poczeka do następnego logowania.
 */
@HiltWorker
class ContractSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val contracts: ContractRepository,
    private val sessionPreferences: SessionPreferences,
) : CoroutineWorker(context, params) {

    companion object {
        const val UNIQUE_NAME = "contract_sync"
    }

    override suspend fun doWork(): Result {
        sessionPreferences.token.first() ?: return Result.success()

        return when (runCatching { contracts.syncPendingMutations() }.getOrNull()) {
            ContractSyncResult.DONE -> Result.success()
            // Sieć znowu zawiodła albo coś padło po drodze — WorkManager ponowi
            // z własnym odstępem, a kolejka jest w bazie, więc nic nie ginie.
            else -> Result.retry()
        }
    }
}
