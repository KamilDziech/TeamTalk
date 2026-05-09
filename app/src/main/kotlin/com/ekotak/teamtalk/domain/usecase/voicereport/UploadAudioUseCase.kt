package com.ekotak.teamtalk.domain.usecase.voicereport

import com.ekotak.teamtalk.domain.repository.VoiceReportRepository
import java.io.File
import javax.inject.Inject

class UploadAudioUseCase @Inject constructor(
    private val voiceReportRepository: VoiceReportRepository,
) {
    /** Returns the public URL assigned by the server. */
    suspend operator fun invoke(file: File): String = voiceReportRepository.uploadAudio(file)
}
