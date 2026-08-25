package com.ekotak.teamtalk.domain.usecase.voicereport

import com.ekotak.teamtalk.domain.model.VoiceReport
import com.ekotak.teamtalk.domain.repository.VoiceReportRepository
import javax.inject.Inject

class CreateVoiceReportUseCase @Inject constructor(
    private val voiceReportRepository: VoiceReportRepository,
) {
    suspend operator fun invoke(
        callLogId: String? = null,
        clientId: String? = null,
        text: String? = null,
        durationSec: Int? = null,
    ): VoiceReport = voiceReportRepository.createVoiceReport(
        callLogId = callLogId,
        clientId = clientId,
        text = text,
        durationSec = durationSec,
    )
}
