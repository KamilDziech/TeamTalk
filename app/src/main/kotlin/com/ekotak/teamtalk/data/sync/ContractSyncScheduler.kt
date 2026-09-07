package com.ekotak.teamtalk.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.ekotak.teamtalk.worker.ContractSyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Zleca opróżnienie kolejki zakładki „Umowa". Wydzielone z repozytorium, żeby
 * warstwa danych nie znała `WorkManagera` z pierwszej ręki.
 *
 * Warunek sieci zdejmuje z nas odpytywanie: system sam obudzi robotnika, gdy
 * telefon wróci w zasięg. `KEEP` pilnuje, by wystawienie umowy i zaraz po nim
 * poprawka nie zamawiały dwóch przebiegów pod rząd.
 */
@Singleton
class ContractSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun scheduleSync() {
        val request = OneTimeWorkRequestBuilder<ContractSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ContractSyncWorker.UNIQUE_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}
