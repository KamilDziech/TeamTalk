package com.ekotak.teamtalk.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ekotak.teamtalk.data.files.DocumentFileStore
import com.ekotak.teamtalk.data.local.preferences.SessionPreferences
import com.ekotak.teamtalk.domain.repository.DealDocumentRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Pobiera rzuty kondygnacji deala (sloty „Projekt domu") do
 * `filesDir/deal-docs/plans` — tak, żeby edytor rzutu w audycie OP miał obraz
 * także wtedy, gdy audytor nigdy go wcześniej nie otworzył.
 *
 * Lista plików idzie przez [DealDocumentRepository.getDocuments], więc przy
 * okazji odświeża cache `deal_documents`, z którego zakładka czyta bez zasięgu.
 * Kopie rzutów, które zniknęły z deala, sprzątamy wyłącznie na liście prosto
 * z serwera.
 */
@HiltWorker
class FloorPlanPrefetchWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val documents: DealDocumentRepository,
    private val files: DocumentFileStore,
    private val sessionPreferences: SessionPreferences,
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_DEAL_ID = "dealId"
        const val UNIQUE_PREFIX = "floor_plan_prefetch:"

        /** Po tylu próbach dajemy spokój — rzut i tak pobierze się przy obejrzeniu. */
        private const val MAX_ATTEMPTS = 5
    }

    override suspend fun doWork(): Result {
        sessionPreferences.token.first() ?: return Result.success()
        val dealId = inputData.getString(KEY_DEAL_ID)?.takeIf { it.isNotBlank() }
            ?: return Result.success()

        val snapshot = runCatching { documents.getDocuments(dealId) }.getOrNull()
            ?: return retryOrGiveUp()
        // Odmowa serwera (brak uprawnienia) nie minie po ponowieniu.
        if (snapshot.error != null) return Result.success()

        val pinned = runCatching {
            files.pinPlans(dealId, snapshot.documents, prune = !snapshot.offline)
        }.getOrNull() ?: return retryOrGiveUp()

        return if (snapshot.offline || pinned.failed > 0) retryOrGiveUp() else Result.success()
    }

    private fun retryOrGiveUp(): Result =
        if (runAttemptCount + 1 >= MAX_ATTEMPTS) Result.success() else Result.retry()
}
