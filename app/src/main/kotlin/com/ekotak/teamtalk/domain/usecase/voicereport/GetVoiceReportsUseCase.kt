package com.ekotak.teamtalk.domain.usecase.voicereport

import com.ekotak.teamtalk.domain.model.VoiceReport
import com.ekotak.teamtalk.domain.repository.VoiceReportRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetVoiceReportsUseCase @Inject constructor(
    private val voiceReportRepository: VoiceReportRepository,
) {
    operator fun invoke(
        callLogId: String? = null,
        clientId: String? = null,
    ): Flow<List<VoiceReport>> =
        voiceReportRepository.getVoiceReports(callLogId = callLogId, clientId = clientId)
}
