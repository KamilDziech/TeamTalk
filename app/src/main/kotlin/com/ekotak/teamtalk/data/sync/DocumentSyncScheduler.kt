package com.ekotak.teamtalk.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.ekotak.teamtalk.worker.DocumentSyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Zleca wysłanie plików i zmian zapisanych bez zasięgu. Wydzielone
 * z repozytorium, żeby warstwa danych nie znała `WorkManagera` z pierwszej ręki.
 *
 * Warunek sieci zdejmuje z nas odpytywanie — system sam obudzi robotnika po
 * powrocie łączności. `KEEP` pilnuje, by seria zdjęć z montażu zrobiona jedno
 * po drugim nie zamawiała osobnego przebiegu dla każdego kadru.
 */
@Singleton
class DocumentSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun scheduleSync() {
        val request = OneTimeWorkRequestBuilder<DocumentSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            DocumentSyncWorker.UNIQUE_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}
