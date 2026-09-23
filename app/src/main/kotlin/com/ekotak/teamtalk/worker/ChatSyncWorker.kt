package com.ekotak.teamtalk.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ekotak.teamtalk.data.local.preferences.SessionPreferences
import com.ekotak.teamtalk.domain.model.CommsSyncResult
import com.ekotak.teamtalk.domain.repository.ChatRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Wysyła to, co napisano w Komunikatorze bez zasięgu.
 *
 * Zamawia go repozytorium przy każdej zakolejkowanej wiadomości, z warunkiem
 * sieci — system budzi robotnika sam, gdy telefon wróci w zasięg, więc nie ma
 * tu żadnego odpytywania. Drugi raz woła go start aplikacji, na wypadek gdyby
 * proces zginął z pełną kolejką: montaż w piwnicy trwa godzinami, a telefon
 * w kieszeni bywa w tym czasie ubijany w tle.
 *
 * Bez sesji nie ma dokąd wysyłać: kończymy sukcesem, żeby WorkManager nie
 * dobijał się w kółko po wylogowaniu. Kolejka poczeka do następnego logowania.
 */
@HiltWorker
class ChatSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val chat: ChatRepository,
    private val sessionPreferences: SessionPreferences,
) : CoroutineWorker(context, params) {

    companion object {
        const val UNIQUE_NAME = "chat_sync"
    }

    override suspend fun doWork(): Result {
        sessionPreferences.token.first() ?: return Result.success()

        return when (runCatching { chat.syncPendingMutations() }.getOrNull()) {
            CommsSyncResult.DONE -> Result.success()
            // Sieć znowu zawiodła — WorkManager ponowi z własnym odstępem,
            // a kolejka siedzi w bazie, więc nic nie ginie.
            else -> Result.retry()
        }
    }
}
