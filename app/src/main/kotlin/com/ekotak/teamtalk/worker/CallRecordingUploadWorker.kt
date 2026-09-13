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
import com.ekotak.teamtalk.data.local.preferences.CallRecordingPreferences
import com.ekotak.teamtalk.data.local.preferences.SessionPreferences
import com.ekotak.teamtalk.data.recording.CallRecordingFinder
import com.ekotak.teamtalk.domain.model.CallDirection
import com.ekotak.teamtalk.domain.repository.CallLogRepository
import com.ekotak.teamtalk.domain.repository.VoiceReportRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import retrofit2.HttpException
import java.util.concurrent.TimeUnit

/**
 * Wysyła nagranie rozmowy (z systemowej nagrywarki Samsunga) do board360, gdzie
 * serwer robi transkrypcję i streszczenie.
 *
 * Kroki, każdy odporny na powtórkę:
 *  1. znajdź plik w MediaStore — nagrywarka dopisuje go kilka sekund po
 *     rozłączeniu, więc brak pliku to najpierw „jeszcze nie", a dopiero po kilku
 *     próbach „nie będzie" (np. nagrywanie wyłączone w telefonie);
 *  2. zapisz połączenie — `POST /call-logs` jest idempotentne po (użytkownik,
 *     numer, początek), więc to samo połączenie z [com.ekotak.teamtalk.service.CallMonitorService]
 *     nie zdubluje się, a bez zasięgu w chwili rozmowy powstaje dopiero tutaj;
 *  3. załóż notatkę — id trzymamy w [CallRecordingPreferences], żeby powtórka po
 *     zerwanym uploadzie nie zakładała kolejnej;
 *  4. wgraj plik.
 */
@HiltWorker
class CallRecordingUploadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val finder: CallRecordingFinder,
    private val callLogRepository: CallLogRepository,
    private val voiceReportRepository: VoiceReportRepository,
    private val recordingPreferences: CallRecordingPreferences,
    private val sessionPreferences: SessionPreferences,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!recordingPreferences.enabled) return Result.success()
        sessionPreferences.token.first() ?: return Result.success()
        if (!finder.hasPermission()) return Result.success()

        val callStartMs = inputData.getLong(KEY_CALL_START_MS, 0L)
        val callEndMs = inputData.getLong(KEY_CALL_END_MS, 0L)
        val durationSec = inputData.getInt(KEY_DURATION_SEC, -1).takeIf { it >= 0 }
        val phone = inputData.getString(KEY_PHONE) ?: return Result.success()
        val startedAt = inputData.getString(KEY_STARTED_AT) ?: return Result.success()
        val endedAt = inputData.getString(KEY_ENDED_AT)
        val direction = CallDirection.fromValue(inputData.getString(KEY_DIRECTION))
        val simSlot = inputData.getInt(KEY_SIM_SLOT, -1).takeIf { it >= 0 }
        val callKey = callStartMs.toString()

        val recording = finder.find(callStartMs, callEndMs, durationSec)
        if (recording == null) {
            return if (runAttemptCount < FIND_ATTEMPTS) {
                Result.retry()
            } else {
                Log.i(TAG, "Brak nagrania dla połączenia $callKey — nagrywanie w telefonie wyłączone?")
                Result.success()
            }
        }
        if (recording.sizeBytes > MAX_UPLOAD_BYTES) {
            Log.w(TAG, "Nagranie ${recording.displayName} ma ${recording.sizeBytes} B — powyżej limitu serwera")
            return Result.success()
        }

        val file = finder.copyToCache(recording) ?: return retryOrGiveUp()
        return try {
            val reportId = recordingPreferences.reportIdFor(callKey) ?: run {
                val callLog = callLogRepository.createCallLog(
                    phoneNumber = phone,
                    direction = direction,
                    startedAt = startedAt,
                    endedAt = endedAt,
                    durationSec = durationSec,
                    simSlot = simSlot,
                )
                voiceReportRepository.createVoiceReport(
                    callLogId = callLog.id,
                    clientId = callLog.clientId,
                    durationSec = durationSec,
                ).id.also { recordingPreferences.saveReportId(callKey, it) }
            }
            voiceReportRepository.uploadRecording(reportId, file, uploadMimeType(recording.mimeType))
            recordingPreferences.clearReportId(callKey)
            Result.success()
        } catch (e: HttpException) {
            when (e.code()) {
                // Notatkę ktoś skasował między próbami — założymy nową.
                404 -> { recordingPreferences.clearReportId(callKey); retryOrGiveUp() }
                // Serwer tego pliku nie przyjmie (rozmiar, typ) — powtórka nic nie zmieni.
                413, 415 -> { recordingPreferences.clearReportId(callKey); Result.success() }
                401, 403 -> Result.success()
                else -> retryOrGiveUp()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Wysyłka nagrania nieudana: ${e.message}")
            retryOrGiveUp()
        } finally {
            file.delete()
        }
    }

    private fun retryOrGiveUp(): Result =
        if (runAttemptCount < UPLOAD_ATTEMPTS) Result.retry() else Result.failure()

    companion object {
        private const val TAG = "CallRecordingUpload"
        const val KEY_CALL_START_MS = "call_start_ms"
        const val KEY_CALL_END_MS = "call_end_ms"
        const val KEY_DURATION_SEC = "duration_sec"
        const val KEY_PHONE = "phone"
        const val KEY_STARTED_AT = "started_at"
        const val KEY_ENDED_AT = "ended_at"
        const val KEY_DIRECTION = "direction"
        const val KEY_SIM_SLOT = "sim_slot"

        /** Próby szukania pliku: 20 s + backoff 30 s → ~8 min okna na zapis nagrania. */
        private const val FIND_ATTEMPTS = 5
        /** Próby wysyłki bez zasięgu — WorkManager i tak czeka na sieć między nimi. */
        private const val UPLOAD_ATTEMPTS = 12
        /** Limit serwera to 100 MB; margines na narzut multipartu. */
        private const val MAX_UPLOAD_BYTES = 95L * 1024 * 1024

        /** Rozmowy krótsze niż to nie mają czego streszczać (pomyłka, „oddzwonię"). */
        const val MIN_CALL_SEC = 5

        /** MediaStore podaje m4a różnie; serwer przyjmuje konkretne typy audio. */
        private fun uploadMimeType(mime: String): String = when (mime.lowercase()) {
            "audio/mp4", "audio/x-m4a", "audio/m4a", "audio/aac", "audio/ogg", "audio/mpeg",
            "audio/amr", "audio/3gpp", "audio/wav", "audio/x-wav", "audio/webm" -> mime.lowercase()
            else -> "audio/mp4"
        }

        fun enqueue(
            context: Context,
            callStartMs: Long,
            callEndMs: Long,
            durationSec: Int?,
            phone: String,
            startedAt: String,
            endedAt: String?,
            direction: CallDirection,
            simSlot: Int?,
        ) {
            val request = OneTimeWorkRequestBuilder<CallRecordingUploadWorker>()
                .setInputData(
                    workDataOf(
                        KEY_CALL_START_MS to callStartMs,
                        KEY_CALL_END_MS to callEndMs,
                        KEY_DURATION_SEC to (durationSec ?: -1),
                        KEY_PHONE to phone,
                        KEY_STARTED_AT to startedAt,
                        KEY_ENDED_AT to endedAt,
                        KEY_DIRECTION to direction.value,
                        KEY_SIM_SLOT to (simSlot ?: -1),
                    )
                )
                // Nagrywarka zamyka plik chwilę po rozłączeniu — pierwsza próba od razu
                // prawie zawsze trafiałaby w pustkę.
                .setInitialDelay(20, TimeUnit.SECONDS)
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("call_recording_$callStartMs", ExistingWorkPolicy.KEEP, request)
        }
    }
}
