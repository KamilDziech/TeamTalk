package com.ekotak.teamtalk.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.ekotak.teamtalk.worker.TaskSyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Zleca opróżnienie kolejki zmian zadań. Wydzielone z repozytorium, żeby
 * warstwa danych nie znała `WorkManagera` z pierwszej ręki i dała się testować.
 *
 * Warunek sieci zdejmuje z nas odpytywanie: system sam obudzi robotnika, gdy
 * telefon wróci w zasięg. `KEEP` pilnuje, by kilka zmian pod rząd (odhaczam
 * trzy zadania w windzie) nie zamawiało trzech przebiegów — jeden i tak
 * zabierze całą kolejkę.
 */
@Singleton
class TaskSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun scheduleSync() {
        val request = OneTimeWorkRequestBuilder<TaskSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            TaskSyncWorker.UNIQUE_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}
