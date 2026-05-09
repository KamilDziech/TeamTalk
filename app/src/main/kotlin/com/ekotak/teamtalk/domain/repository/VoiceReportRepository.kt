package com.ekotak.teamtalk.domain.repository

import com.ekotak.teamtalk.domain.model.VoiceReport
import kotlinx.coroutines.flow.Flow
import java.io.File

interface VoiceReportRepository {
    /** Stream of voice reports for the given call log IDs, backed by local cache. */
    fun getVoiceReports(
        callLogIdIn: List<String>? = null,
        callLogIdEq: String? = null,
    ): Flow<List<VoiceReport>>

    suspend fun createVoiceReport(
        callLogId: String,
        audioUrl: String? = null,
        transcription: String? = null,
        aiSummary: String? = null,
        callCount: Int = 1,
    ): VoiceReport

    /** Uploads an audio file and returns the public URL assigned by the server. */
    suspend fun uploadAudio(file: File): String

    /**
     * Sends audio to OpenAI Whisper via the server.
     * Returns the transcription text, or null when Whisper detects silence/hallucination.
     */
    suspend fun transcribeAudio(file: File): String?
}
