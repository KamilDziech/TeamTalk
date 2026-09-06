package com.ekotak.teamtalk.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ekotak.teamtalk.data.local.preferences.SessionPreferences
import com.ekotak.teamtalk.data.notification.NotificationHelper
import com.ekotak.teamtalk.domain.repository.EmailRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Wysyła pocztę i zmiany wątków zapisane bez zasięgu (ustalenie 2026-09-06).
 *
 * Zamawia go repozytorium przy każdym zakolejkowanym zapisie, z warunkiem sieci,
 * więc nie ma tu odpytywania — system budzi robotnika sam po powrocie łączności.
 * Bez sesji nie ma dokąd wysyłać: kończymy sukcesem, żeby WorkManager nie
 * dobijał się w kółko po wylogowaniu.
 *
 * Odmowa serwera nie może przejść bez echa. Człowiek widział wiadomość na
 * ekranie jako wysłaną, więc gdy okazuje się, że adresat jest nie do przyjęcia
 * albo skrzynka zniknęła, mówimy o tym wprost powiadomieniem — inaczej poczta
 * wygląda na dostarczoną, a druga strona czeka na odpowiedź, której nie ma.
 */
@HiltWorker
class EmailSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val email: EmailRepository,
    private val sessionPreferences: SessionPreferences,
    private val notifications: NotificationHelper,
) : CoroutineWorker(context, params) {

    companion object {
        const val UNIQUE_NAME = "email_sync"
    }

    override suspend fun doWork(): Result {
        sessionPreferences.token.first() ?: return Result.success()

        val result = runCatching { email.syncPendingMutations() }.getOrNull()
            // Sieć znowu zawiodła — WorkManager ponowi z własnym odstępem,
            // a kolejka siedzi w bazie, więc nic nie ginie.
            ?: return Result.retry()

        for (rejection in result.rejected) {
            notifications.showEmailNotification(
                title = rejection.label,
                text = rejection.reason ?: "Serwer nie przyjął tej wiadomości.",
                notificationId = rejection.label.hashCode(),
            )
        }

        return if (result.incomplete) Result.retry() else Result.success()
    }
}
