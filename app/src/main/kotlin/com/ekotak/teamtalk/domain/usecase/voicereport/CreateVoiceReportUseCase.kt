package com.ekotak.teamtalk.domain.usecase.voicereport

import com.ekotak.teamtalk.domain.model.VoiceReport
import com.ekotak.teamtalk.domain.repository.VoiceReportRepository
import javax.inject.Inject

class CreateVoiceReportUseCase @Inject constructor(
    private val voiceReportRepository: VoiceReportRepository,
) {
    suspend operator fun invoke(
        callLogId: String,
        audioUrl: String? = null,
        transcription: String? = null,
        aiSummary: String? = null,
        callCount: Int = 1,
    ): VoiceReport = voiceReportRepository.createVoiceReport(
        callLogId = callLogId,
        audioUrl = audioUrl,
        transcription = transcription,
        aiSummary = aiSummary,
        callCount = callCount,
    )
}
