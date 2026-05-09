package com.ekotak.teamtalk.domain.usecase.voicereport

import com.ekotak.teamtalk.domain.repository.VoiceReportRepository
import java.io.File
import javax.inject.Inject

class TranscribeAudioUseCase @Inject constructor(
    private val voiceReportRepository: VoiceReportRepository,
) {
    /** Returns the transcription text, or null when Whisper detects silence/hallucination. */
    suspend operator fun invoke(file: File): String? = voiceReportRepository.transcribeAudio(file)
}
