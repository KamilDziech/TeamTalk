package com.ekotak.teamtalk.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ekotak.teamtalk.data.scanner.DeviceCallLogReader
import com.ekotak.teamtalk.domain.model.CallDirection
import com.ekotak.teamtalk.domain.repository.CallLogRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Zapasowy zapis połączenia w historii board360, gdy ekran notatki nie zdążył
 * się otworzyć. Odczytuje ostatnie połączenie z telefonu i wysyła je do backendu.
 */
@HiltWorker
class PostCallNoteWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val deviceCallLogReader: DeviceCallLogReader,
    private val callLogRepository: CallLogRepository,
) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_CALL_START_MS = "call_start_ms"
        const val KEY_PHONE_ACCOUNT_ID = "phone_account_id"
    }

    private val isoFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override suspend fun doWork(): Result {
        val callStartMs = inputData.getLong(KEY_CALL_START_MS, 0L)
        val phoneAccountId = inputData.getString(KEY_PHONE_ACCOUNT_ID)
        val call = deviceCallLogReader.readMostRecentCallSince(callStartMs, phoneAccountId)
        val phone = call?.phoneNumber
            ?.let { deviceCallLogReader.normalizePhone(it) }
            ?.takeIf { it.isNotBlank() }
            ?: return Result.success()

        runCatching {
            val timestampMs = call?.timestampMs ?: System.currentTimeMillis()
            val durationSec = call?.durationSec
            callLogRepository.createCallLog(
                phoneNumber = phone,
                direction   = call?.direction ?: CallDirection.OUTBOUND,
                startedAt   = isoFmt.format(Date(timestampMs)),
                endedAt     = durationSec?.let { isoFmt.format(Date(timestampMs + it * 1000L)) },
                durationSec = durationSec,
                simSlot     = call?.phoneAccountId?.toIntOrNull(),
            )
        }
        return Result.success()
    }
}
