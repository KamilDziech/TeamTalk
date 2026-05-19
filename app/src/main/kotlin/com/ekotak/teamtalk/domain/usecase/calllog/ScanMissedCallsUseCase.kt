package com.ekotak.teamtalk.domain.usecase.calllog

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.ekotak.teamtalk.data.local.dao.CallLogDao
import com.ekotak.teamtalk.data.local.preferences.SessionPreferences
import com.ekotak.teamtalk.data.local.preferences.SimPreferences
import com.ekotak.teamtalk.data.notification.NotificationHelper
import com.ekotak.teamtalk.data.scanner.DeviceCallLogReader
import com.ekotak.teamtalk.domain.model.CallStatus
import com.ekotak.teamtalk.domain.model.CallType
import com.ekotak.teamtalk.domain.repository.CallLogRepository
import com.ekotak.teamtalk.domain.repository.ClientRepository
import com.ekotak.teamtalk.domain.usecase.auth.GetCurrentUserUseCase
import com.ekotak.teamtalk.domain.usecase.profile.GetProfilesUseCase
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

class ScanMissedCallsUseCase @Inject constructor(
    private val deviceCallLogReader: DeviceCallLogReader,
    private val clientRepository: ClientRepository,
    private val callLogRepository: CallLogRepository,
    private val callLogDao: CallLogDao,
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val getProfilesUseCase: GetProfilesUseCase,
    private val notificationHelper: NotificationHelper,
    private val dataStore: DataStore<Preferences>,
    private val simPreferences: SimPreferences,
) {
    private val isoFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    suspend operator fun invoke() {
        val user = try { getCurrentUserUseCase() } catch (_: Exception) { return }

        val profiles = getProfilesUseCase(idEq = user.id).first()
        if (profiles.firstOrNull()?.isAdmin == true) return

        val lastScanMs = dataStore.data.first()[SessionPreferences.KEY_LAST_SCAN_MS]
            ?: (System.currentTimeMillis() - 30 * 24 * 60 * 60 * 1000L)

        val missedCalls = deviceCallLogReader.readMissedCallsSince(lastScanMs, simPreferences.monitoredPhoneAccountId)

        for (call in missedCalls) {
            processCall(call, user.id)
        }

        dataStore.edit { it[SessionPreferences.KEY_LAST_SCAN_MS] = System.currentTimeMillis() }
    }

    private suspend fun processCall(call: DeviceCallLogReader.DeviceCall, userId: String) {
        val normalized = deviceCallLogReader.normalizePhone(call.phoneNumber)
        if (normalized.isBlank()) return

        val windowStart = isoFmt.format(Date(call.timestampMs - 5_000))
        val windowEnd   = isoFmt.format(Date(call.timestampMs + 5_000))

        val duplicateId = callLogDao.findDuplicateByPhone(normalized, windowStart, windowEnd)
        if (duplicateId != null) {
            try { callLogRepository.appendUniqueRecipient(duplicateId, userId) } catch (_: Exception) {}
            return
        }

        var client = clientRepository.getClientByPhone(normalized)
        if (client == null) {
            client = try {
                clientRepository.createClient(phone = normalized, name = call.cachedName, address = null, notes = null)
            } catch (_: Exception) { null }
        } else if (client.name == null && call.cachedName != null) {
            client = try {
                clientRepository.updateClient(id = client.id, name = call.cachedName)
            } catch (_: Exception) { client }
        }

        val timestamp = isoFmt.format(Date(call.timestampMs))
        val dedupKey  = "${client?.id ?: normalized}_${call.timestampMs / 5_000}"

        try {
            val callLog = callLogRepository.createCallLog(
                clientId       = client?.id,
                employeeId     = userId,
                type           = CallType.MISSED,
                status         = CallStatus.MISSED,
                timestamp      = timestamp,
                callerPhone    = normalized,
                dedupKey       = dedupKey,
                phoneAccountId = call.phoneAccountId,
            )
            notificationHelper.showMissedCallNotification(client?.name ?: normalized, callLog.id)
        } catch (_: Exception) {}
    }
}
