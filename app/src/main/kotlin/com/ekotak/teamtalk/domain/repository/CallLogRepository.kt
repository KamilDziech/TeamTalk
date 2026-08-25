package com.ekotak.teamtalk.domain.repository

import com.ekotak.teamtalk.domain.model.CallDirection
import com.ekotak.teamtalk.domain.model.CallLog
import com.ekotak.teamtalk.domain.model.CallLogFilter
import kotlinx.coroutines.flow.Flow

interface CallLogRepository {
    /** Strumień połączeń z lokalnego cache, odświeżany z sieci. */
    fun getCallLogs(filter: CallLogFilter = CallLogFilter()): Flow<List<CallLog>>

    /** Zapisuje jedno połączenie w board360 (dopasowanie klienta po numerze po stronie backendu). */
    suspend fun createCallLog(
        phoneNumber: String,
        direction: CallDirection,
        startedAt: String,
        endedAt: String? = null,
        durationSec: Int? = null,
        simSlot: Int? = null,
        clientId: String? = null,
    ): CallLog
}
