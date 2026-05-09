package com.ekotak.teamtalk.domain.usecase.calllog

import com.ekotak.teamtalk.domain.model.CallLog
import com.ekotak.teamtalk.domain.model.CallStatus
import com.ekotak.teamtalk.domain.model.CallType
import com.ekotak.teamtalk.domain.repository.CallLogRepository
import javax.inject.Inject

class CreateCallLogUseCase @Inject constructor(
    private val callLogRepository: CallLogRepository,
) {
    suspend operator fun invoke(
        type: CallType,
        status: CallStatus,
        clientId: String? = null,
        employeeId: String? = null,
        timestamp: String? = null,
        callerPhone: String? = null,
        dedupKey: String? = null,
        phoneAccountId: String? = null,
    ): CallLog = callLogRepository.createCallLog(
        clientId = clientId,
        employeeId = employeeId,
        type = type,
        status = status,
        timestamp = timestamp,
        callerPhone = callerPhone,
        dedupKey = dedupKey,
        phoneAccountId = phoneAccountId,
    )
}
