package com.ekotak.teamtalk.domain.repository

import com.ekotak.teamtalk.domain.model.CallLog
import com.ekotak.teamtalk.domain.model.CallLogFilter
import com.ekotak.teamtalk.domain.model.CallStatus
import com.ekotak.teamtalk.domain.model.CallType
import kotlinx.coroutines.flow.Flow

interface CallLogRepository {
    /** Stream of call logs matching the given filter, backed by the local cache. */
    fun getCallLogs(filter: CallLogFilter = CallLogFilter()): Flow<List<CallLog>>

    suspend fun createCallLog(
        clientId: String? = null,
        employeeId: String? = null,
        type: CallType,
        status: CallStatus,
        timestamp: String? = null,
        callerPhone: String? = null,
        dedupKey: String? = null,
        phoneAccountId: String? = null,
    ): CallLog

    suspend fun updateCallLog(
        id: String,
        status: CallStatus? = null,
        type: CallType? = null,
        reservationBy: String? = null,
        reservationAt: String? = null,
        mergedIntoId: String? = null,
    ): CallLog

    /** Atomically adds recipientId to call_log.recipients if not already present. */
    suspend fun appendUniqueRecipient(callLogId: String, recipientId: String)
}
