package com.ekotak.teamtalk.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ekotak.teamtalk.data.local.preferences.SessionPreferences
import com.ekotak.teamtalk.domain.repository.MontazRepository
import com.ekotak.teamtalk.domain.repository.MontazSyncResult
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Wysyła zmiany zakładki „Montaż" zrobione bez zasięgu: nowe etapy, obsadę
 * i zakres, wydania materiału, zdjęcia powykonawcze i odprawy.
 *
 * Zamawia go repozytorium przy każdym zakolejkowanym zapisie, z warunkiem sieci
 * — system budzi robotnika sam, gdy telefon wróci w zasięg. Drugi raz woła go
 * start aplikacji: montaż trwa cały dzień, a telefon w kieszeni bywa w tym
 * czasie ubijany w tle, więc kolejka nie może czekać na kolejne dotknięcie
 * zakładki.
 *
 * Bez sesji nie ma dokąd wysyłać: kończymy sukcesem, żeby WorkManager nie
 * dobijał się w kółko po wylogowaniu. Kolejka poczeka do następnego logowania.
 */
@HiltWorker
class MontazSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val montaze: MontazRepository,
    private val sessionPreferences: SessionPreferences,
) : CoroutineWorker(context, params) {

    companion object {
        const val UNIQUE_NAME = "montaz_sync"
    }

    override suspend fun doWork(): Result {
        sessionPreferences.token.first() ?: return Result.success()

        return when (runCatching { montaze.syncPendingMutations() }.getOrNull()) {
            MontazSyncResult.DONE -> Result.success()
            // Sieć znowu zawiodła albo coś padło po drodze — WorkManager ponowi
            // z własnym odstępem, a kolejka jest w bazie, więc nic nie ginie.
            else -> Result.retry()
        }
    }
}
