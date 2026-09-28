package com.ekotak.teamtalk.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ekotak.teamtalk.service.PostCallHandler
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Zapasowa ścieżka po rozłączeniu, gdy system nie pozwolił wystartować
 * [com.ekotak.teamtalk.service.CallMonitorService] z tła (Android 14:
 * ForegroundServiceStartNotAllowedException wywalało całą aplikację).
 * Robota ta sama — [PostCallHandler]; zlecenie przyspieszone, więc rusza od razu.
 */
@HiltWorker
class CallEndedWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val handler: PostCallHandler,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val callStartMs = inputData.getLong(KEY_CALL_START_MS, System.currentTimeMillis())
        handler.handle(callStartMs, inputData.getString(KEY_PHONE_ACCOUNT_ID))
        return Result.success()
    }

    companion object {
        private const val KEY_CALL_START_MS = "call_start_ms"
        private const val KEY_PHONE_ACCOUNT_ID = "phone_account_id"

        fun enqueue(context: Context, callStartMs: Long, phoneAccountId: String?) {
            val request = OneTimeWorkRequestBuilder<CallEndedWorker>()
                .setInputData(workDataOf(KEY_CALL_START_MS to callStartMs, KEY_PHONE_ACCOUNT_ID to phoneAccountId))
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }
    }
}
