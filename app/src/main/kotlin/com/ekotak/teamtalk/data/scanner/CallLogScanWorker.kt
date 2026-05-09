package com.ekotak.teamtalk.data.scanner

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ekotak.teamtalk.domain.usecase.calllog.ScanMissedCallsUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.util.concurrent.TimeUnit

@HiltWorker
class CallLogScanWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted workerParams: WorkerParameters,
    private val scanMissedCallsUseCase: ScanMissedCallsUseCase,
) : CoroutineWorker(context, workerParams) {

    override suspend fun doWork(): Result = try {
        scanMissedCallsUseCase()
        Result.success()
    } catch (_: Exception) {
        Result.retry()
    }

    companion object {
        private const val WORK_NAME = "call_log_scan"

        fun schedule(workManager: WorkManager) {
            val request = PeriodicWorkRequestBuilder<CallLogScanWorker>(15, TimeUnit.MINUTES)
                .build()
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
        }

        fun cancel(workManager: WorkManager) {
            workManager.cancelUniqueWork(WORK_NAME)
        }
    }
}
