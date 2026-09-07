package com.ekotak.teamtalk.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ekotak.teamtalk.data.local.preferences.SessionPreferences
import com.ekotak.teamtalk.domain.repository.DealRepository
import com.ekotak.teamtalk.domain.repository.DealSyncResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Wysyła zmiany karty deala zapisane bez zasięgu: zakres instalacji etapu
 * (zakł. LEAD / Remarketing) i pola karty ruszone przy kliencie — rodzaj
 * budynku, miejsce i termin spotkania, OZC.
 *
 * Zamawia go repozytorium przy każdej zakolejkowanej zmianie, z warunkiem sieci
 * — system budzi robotnika sam, gdy telefon wróci w zasięg, więc nie ma tu
 * żadnego odpytywania. Drugi raz woła go start aplikacji, na wypadek gdyby
 * proces zginął z pełną kolejką: rozmowa u klienta bez zasięgu potrafi trwać
 * godzinę, a telefon w tym czasie bywa ubijany w tle.
 *
 * Bez sesji nie ma dokąd wysyłać: kończymy sukcesem, żeby WorkManager nie
 * dobijał się w kółko po wylogowaniu. Kolejka poczeka do następnego logowania.
 */
@HiltWorker
class DealSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val deals: DealRepository,
    private val sessionPreferences: SessionPreferences,
) : CoroutineWorker(context, params) {

    companion object {
        const val UNIQUE_NAME = "deal_sync"
    }

    override suspend fun doWork(): Result {
        sessionPreferences.token.first() ?: return Result.success()

        return when (runCatching { deals.syncPendingMutations() }.getOrNull()) {
            DealSyncResult.DONE -> Result.success()
            // Sieć znowu zawiodła albo coś padło po drodze — WorkManager ponowi
            // z własnym odstępem, a kolejka jest w bazie, więc nic nie ginie.
            else -> Result.retry()
        }
    }
}
