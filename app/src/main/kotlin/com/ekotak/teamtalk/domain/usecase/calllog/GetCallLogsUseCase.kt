package com.ekotak.teamtalk.domain.usecase.calllog

import com.ekotak.teamtalk.domain.model.CallLog
import com.ekotak.teamtalk.domain.model.CallLogFilter
import com.ekotak.teamtalk.domain.repository.CallLogRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetCallLogsUseCase @Inject constructor(
    private val callLogRepository: CallLogRepository,
) {
    operator fun invoke(filter: CallLogFilter = CallLogFilter()): Flow<List<CallLog>> =
        callLogRepository.getCallLogs(filter)
}
