package com.ekotak.teamtalk.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ekotak.teamtalk.data.local.preferences.SessionPreferences
import com.ekotak.teamtalk.data.notification.NotificationHelper
import com.ekotak.teamtalk.domain.repository.MeetingRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import retrofit2.HttpException
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Wysyła nagranie spotkania, gdy jest sieć. Plik w `filesDir` to JEDYNA kopia,
 * więc worker nie odpuszcza szybko: kolejne próby co najmniej co 30 s z rosnącym
 * odstępem, plik kasujemy dopiero po przyjęciu przez serwer (albo gdy spotkania
 * już nie ma). Serwer przyjęcie nagrania traktuje jak „Zakończ", więc spotkanie
 * zamyka się samo, nawet gdy „Zakończ" z telefonu bez zasięgu nie doszło.
 */
@HiltWorker
class MeetingRecordingUploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: MeetingRepository,
    private val sessionPreferences: SessionPreferences,
    private val notificationHelper: NotificationHelper,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val meetingId = inputData.getString(KEY_MEETING_ID) ?: return Result.success()
        val path = inputData.getString(KEY_PATH) ?: return Result.success()
        val durationSec = inputData.getInt(KEY_DURATION_SEC, -1).takeIf { it >= 0 }
        val title = inputData.getString(KEY_TITLE).orEmpty()
        val file = File(path)
        if (!file.exists()) return Result.success()
        // Bez sesji nie wyślemy — czekamy na zalogowanie zamiast kasować nagranie.
        sessionPreferences.token.first() ?: return Result.retry()

        return try {
            repository.uploadRecording(meetingId, file, durationSec)
            file.delete()
            notificationHelper.showMeetingNotification(
                meetingId,
                "Nagranie wysłane",
                "„$title” — podsumowanie pojawi się po transkrypcji.",
            )
            Result.success()
        } catch (e: HttpException) {
            when (e.code()) {
                // Spotkanie usunięte albo brak dostępu — nagranie nie ma dokąd iść.
                404, 403 -> { file.delete(); Result.success() }
                400, 413, 415 -> {
                    Log.w(TAG, "Serwer odrzucił nagranie spotkania $meetingId: ${e.code()}")
                    notificationHelper.showMeetingNotification(
                        meetingId,
                        "Nie udało się wysłać nagrania",
                        "Serwer odrzucił plik (${e.code()}). Nagranie zostaje w telefonie.",
                    )
                    Result.failure()
                }
                else -> Result.retry()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Wysyłka nagrania spotkania nieudana: ${e.message}")
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "MeetingUpload"
        const val KEY_MEETING_ID = "meeting_id"
        const val KEY_PATH = "path"
        const val KEY_DURATION_SEC = "duration_sec"
        const val KEY_TITLE = "title"

        fun enqueue(context: Context, meetingId: String, file: File, durationSec: Int?, title: String) {
            val request = OneTimeWorkRequestBuilder<MeetingRecordingUploadWorker>()
                .setInputData(
                    workDataOf(
                        KEY_MEETING_ID to meetingId,
                        KEY_PATH to file.absolutePath,
                        KEY_DURATION_SEC to (durationSec ?: -1),
                        KEY_TITLE to title,
                    ),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("meeting_recording_$meetingId", ExistingWorkPolicy.KEEP, request)
        }
    }
}
