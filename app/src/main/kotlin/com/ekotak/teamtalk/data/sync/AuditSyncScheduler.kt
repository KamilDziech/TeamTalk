package com.ekotak.teamtalk.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.ekotak.teamtalk.worker.AuditSyncWorker
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Zleca opróżnienie kolejki audytów. Wydzielone z repozytorium, żeby warstwa
 * danych nie znała `WorkManagera` z pierwszej ręki.
 *
 * Warunek sieci zdejmuje z nas odpytywanie: system sam obudzi robotnika, gdy
 * telefon wróci w zasięg — a audytor w domu w budowie bywa bez zasięgu przez
 * całą wizytę. `KEEP` pilnuje, by kilka zapisów pod rząd (audytor poprawia
 * metraż pokój po pokoju) nie zamawiało kilkunastu przebiegów.
 */
@Singleton
class AuditSyncScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun scheduleSync() {
        val request = OneTimeWorkRequestBuilder<AuditSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            AuditSyncWorker.UNIQUE_NAME,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }
}
