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
import com.ekotak.teamtalk.domain.repository.KIND_MEETING
import com.ekotak.teamtalk.domain.repository.KIND_SUMMARY
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
 *
 * Tą samą drogą idzie podsumowanie nagrane głosem (D9, [KIND_SUMMARY]) — gdy
 * przy wysyłce z okna nie było sieci.
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
        val kind = inputData.getString(KEY_KIND) ?: KIND_MEETING
        val what = if (kind == KIND_SUMMARY) "podsumowania" else "nagrania"
        val file = File(path)
        if (!file.exists()) return Result.success()
        // Bez sesji nie wyślemy — czekamy na zalogowanie zamiast kasować nagranie.
        sessionPreferences.token.first() ?: return Result.retry()

        return try {
            repository.uploadRecording(meetingId, file, durationSec, kind)
            file.delete()
            notificationHelper.showMeetingNotification(
                meetingId,
                if (kind == KIND_SUMMARY) "Podsumowanie wysłane" else "Nagranie wysłane",
                "„$title” — podsumowanie pojawi się po transkrypcji.",
            )
            Result.success()
        } catch (e: HttpException) {
            when (e.code()) {
                // Spotkanie usunięte, brak dostępu albo już nie czeka na podsumowanie — nie ma dokąd iść.
                404, 403, 409 -> { file.delete(); Result.success() }
                400, 413, 415 -> {
                    Log.w(TAG, "Serwer odrzucił $what spotkania $meetingId: ${e.code()}")
                    notificationHelper.showMeetingNotification(
                        meetingId,
                        "Nie udało się wysłać $what",
                        "Serwer odrzucił plik (${e.code()}). Nagranie zostaje w telefonie.",
                    )
                    Result.failure()
                }
                else -> Result.retry()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Wysyłka $what spotkania nieudana: ${e.message}")
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "MeetingUpload"
        const val KEY_MEETING_ID = "meeting_id"
        const val KEY_PATH = "path"
        const val KEY_DURATION_SEC = "duration_sec"
        const val KEY_TITLE = "title"
        const val KEY_KIND = "kind"

        /** Nazwa kolejki podsumowania głosowego — okno sprawdza po niej, czy wysyłka już czeka. */
        fun summaryWorkName(meetingId: String) = "meeting_summary_$meetingId"

        fun enqueue(
            context: Context,
            meetingId: String,
            file: File,
            durationSec: Int?,
            title: String,
            kind: String = KIND_MEETING,
        ) {
            val request = OneTimeWorkRequestBuilder<MeetingRecordingUploadWorker>()
                .setInputData(
                    workDataOf(
                        KEY_MEETING_ID to meetingId,
                        KEY_PATH to file.absolutePath,
                        KEY_DURATION_SEC to (durationSec ?: -1),
                        KEY_TITLE to title,
                        KEY_KIND to kind,
                    ),
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            // Podsumowanie nagrane od nowa zastępuje poprzednie, które jeszcze nie wyszło.
            if (kind == KIND_SUMMARY) {
                WorkManager.getInstance(context)
                    .enqueueUniqueWork(summaryWorkName(meetingId), ExistingWorkPolicy.REPLACE, request)
            } else {
                // Klucz = plik, nie samo spotkanie: wielodniowe (D14) ma jedno nagranie na dzień,
                // a nazwa pliku niesie id + dzień + chwilę startu. KEEP dalej chroni przed
                // podwójną wysyłką TEGO SAMEGO pliku (np. odzysk sieroty przy starcie aplikacji).
                WorkManager.getInstance(context)
                    .enqueueUniqueWork("meeting_recording_${file.nameWithoutExtension}", ExistingWorkPolicy.KEEP, request)
            }
        }
    }
}
