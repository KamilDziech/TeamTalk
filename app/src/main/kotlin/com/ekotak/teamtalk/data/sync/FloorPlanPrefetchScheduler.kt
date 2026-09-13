package com.ekotak.teamtalk.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.ekotak.teamtalk.worker.FloorPlanPrefetchWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Zamawia pobranie rzutów kondygnacji deala do trwałego katalogu telefonu.
 *
 * Woła to karta deala przy otwarciu. Audytor zwykle otwiera kartę w biurze
 * albo w aucie, a rysuje w domu w budowie bez zasięgu — dlatego pobranie idzie
 * przez WorkManagera, a nie przez `viewModelScope`: wyjście z karty chwilę po
 * wejściu nie może go przerwać.
 *
 * Jedno zlecenie na deal (`KEEP`) — kilka wejść w tę samą kartę pod rząd nie
 * zamawia kilku pobrań. Warunek sieci: otwarcie karty z cache bez zasięgu
 * poczeka na zasięg, zamiast skończyć się porażką.
 */
@Singleton
class FloorPlanPrefetchScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun schedule(dealId: String) {
        if (dealId.isBlank()) return
        val request = OneTimeWorkRequestBuilder<FloorPlanPrefetchWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setInputData(workDataOf(FloorPlanPrefetchWorker.KEY_DEAL_ID to dealId))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "${FloorPlanPrefetchWorker.UNIQUE_PREFIX}$dealId",
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}
