package com.ekotak.teamtalk.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.ekotak.teamtalk.worker.LeaveSyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Zleca wysłanie wniosków i decyzji zapisanych bez zasięgu. Wydzielone
 * z repozytorium, żeby warstwa danych nie znała `WorkManagera` z pierwszej ręki.
 *
 * Warunek sieci zdejmuje z nas odpytywanie — system sam obudzi robotnika po
 * powrocie łączności. `KEEP` pilnuje, by trzy wnioski złożone pod rząd
 * w kotłowni nie zamawiały trzech przebiegów.
 */
@Singleton
class LeaveSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun scheduleSync() {
        val request = OneTimeWorkRequestBuilder<LeaveSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            LeaveSyncWorker.UNIQUE_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}
