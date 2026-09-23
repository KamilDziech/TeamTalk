package com.ekotak.teamtalk.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.ekotak.teamtalk.worker.ChatSyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Zleca opróżnienie kolejki Komunikatora. Wydzielone z repozytorium, żeby
 * warstwa danych nie znała `WorkManagera` z pierwszej ręki.
 *
 * Warunek sieci zdejmuje z nas odpytywanie: system sam obudzi robotnika, gdy
 * telefon wróci w zasięg. `KEEP` pilnuje, żeby kilka zdań napisanych pod rząd
 * w piwnicy nie zamawiało kilku przebiegów — jeden i tak opróżni całą kolejkę,
 * w kolejności pisania.
 */
@Singleton
class ChatSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun scheduleSync() {
        val request = OneTimeWorkRequestBuilder<ChatSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            ChatSyncWorker.UNIQUE_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}
