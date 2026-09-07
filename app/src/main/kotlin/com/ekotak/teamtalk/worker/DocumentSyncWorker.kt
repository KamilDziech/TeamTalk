package com.ekotak.teamtalk.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ekotak.teamtalk.data.local.preferences.SessionPreferences
import com.ekotak.teamtalk.data.notification.NotificationHelper
import com.ekotak.teamtalk.domain.repository.DealDocumentRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Wysyła pliki deala i zmiany zapisane bez zasięgu.
 *
 * Zamawia go repozytorium przy każdym zakolejkowanym zapisie, z warunkiem sieci,
 * więc nie ma tu odpytywania — system budzi robotnika sam po powrocie łączności.
 * Bez sesji nie ma dokąd wysyłać: kończymy sukcesem, żeby WorkManager nie
 * dobijał się w kółko po wylogowaniu.
 *
 * Odmowa serwera nie może przejść bez echa. Człowiek widział zdjęcie na karcie
 * deala, więc gdy okazuje się, że plik jest za duży albo konto straciło
 * `deal.manage`, mówimy o tym wprost powiadomieniem — inaczej dokumentacja
 * montażu wygląda na kompletną, a w panelu jej nie ma.
 */
@HiltWorker
class DocumentSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val documents: DealDocumentRepository,
    private val sessionPreferences: SessionPreferences,
    private val notifications: NotificationHelper,
) : CoroutineWorker(context, params) {

    companion object {
        const val UNIQUE_NAME = "document_sync"
    }

    override suspend fun doWork(): Result {
        sessionPreferences.token.first() ?: return Result.success()

        val result = runCatching { documents.syncPendingMutations() }.getOrNull()
            // Sieć znowu zawiodła — WorkManager ponowi z własnym odstępem,
            // a kolejka siedzi w bazie, więc nic nie ginie.
            ?: return Result.retry()

        for (rejection in result.rejected) {
            notifications.showDocumentNotification(
                title = rejection.label,
                text = rejection.reason ?: "Serwer nie przyjął tego pliku.",
                notificationId = rejection.label.hashCode(),
            )
        }

        return if (result.incomplete) Result.retry() else Result.success()
    }
}
