package com.ekotak.teamtalk.domain.usecase.calllog

import com.ekotak.teamtalk.domain.model.CallLog
import com.ekotak.teamtalk.domain.model.CallStatus
import com.ekotak.teamtalk.domain.model.CallType
import com.ekotak.teamtalk.domain.repository.CallLogRepository
import javax.inject.Inject

class UpdateCallLogUseCase @Inject constructor(
    private val callLogRepository: CallLogRepository,
) {
    suspend operator fun invoke(
        id: String,
        status: CallStatus? = null,
        type: CallType? = null,
        reservationBy: String? = null,
        reservationAt: String? = null,
        mergedIntoId: String? = null,
    ): CallLog = callLogRepository.updateCallLog(
        id = id,
        status = status,
        type = type,
        reservationBy = reservationBy,
        reservationAt = reservationAt,
        mergedIntoId = mergedIntoId,
    )
}
