package com.ekotak.teamtalk.domain.usecase.calllog

import com.ekotak.teamtalk.domain.repository.CallLogRepository
import javax.inject.Inject

class AppendRecipientUseCase @Inject constructor(
    private val callLogRepository: CallLogRepository,
) {
    suspend operator fun invoke(callLogId: String, recipientId: String) =
        callLogRepository.appendUniqueRecipient(callLogId, recipientId)
}
