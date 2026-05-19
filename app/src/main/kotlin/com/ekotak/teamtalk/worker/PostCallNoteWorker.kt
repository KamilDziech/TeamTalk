package com.ekotak.teamtalk.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ekotak.teamtalk.data.local.dao.CallLogDao
import com.ekotak.teamtalk.data.notification.NotificationHelper
import com.ekotak.teamtalk.data.scanner.DeviceCallLogReader
import com.ekotak.teamtalk.domain.model.CallLogFilter
import com.ekotak.teamtalk.domain.model.CallStatus
import com.ekotak.teamtalk.domain.model.CallType
import com.ekotak.teamtalk.domain.repository.CallLogRepository
import com.ekotak.teamtalk.domain.repository.ClientRepository
import com.ekotak.teamtalk.domain.usecase.auth.GetCurrentUserUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@HiltWorker
class PostCallNoteWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val deviceCallLogReader: DeviceCallLogReader,
    private val clientRepository: ClientRepository,
    private val callLogRepository: CallLogRepository,
    private val callLogDao: CallLogDao,
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val notificationHelper: NotificationHelper,
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

        recordCallInHistory(call, phone)
        notificationHelper.showPostCallNoteNotification(phone)
        return Result.success()
    }

    private suspend fun recordCallInHistory(call: DeviceCallLogReader.DeviceCall?, phone: String?) {
        if (phone == null) return
        runCatching {
            val userId = getCurrentUserUseCase().id
            val timestampMs = call?.timestampMs ?: System.currentTimeMillis()
            val windowStart = isoFmt.format(Date(timestampMs - 5_000))
            val windowEnd   = isoFmt.format(Date(timestampMs + 5_000))

            if (callLogDao.findDuplicateByPhone(phone, windowStart, windowEnd) != null) return@runCatching

            val callName = call?.cachedName
            var client = clientRepository.getClientByPhone(phone)
            when {
                client == null -> {
                    client = runCatching {
                        clientRepository.createClient(phone = phone, name = callName, address = null, notes = null)
                    }.getOrNull()
                }
                client.name == null && callName != null -> {
                    val clientId = client.id
                    client = runCatching {
                        clientRepository.updateClient(id = clientId, name = callName)
                    }.getOrElse { client }
                }
            }

            callLogRepository.createCallLog(
                clientId       = client?.id,
                employeeId     = userId,
                type           = CallType.COMPLETED,
                status         = CallStatus.COMPLETED,
                timestamp      = isoFmt.format(Date(timestampMs)),
                callerPhone    = phone,
                dedupKey       = "${client?.id ?: phone}_${timestampMs / 5_000}",
                phoneAccountId = call?.phoneAccountId,
            )
            resolveMissedCalls(phone)
        }
    }

    private suspend fun resolveMissedCalls(phone: String) {
        runCatching {
            val missed = callLogRepository.getCallLogs(
                CallLogFilter(callerPhoneEq = phone, statusEq = "missed")
            ).first()
            for (entry in missed) {
                runCatching { callLogRepository.updateCallLog(id = entry.id, status = CallStatus.COMPLETED) }
            }
        }
    }
}
