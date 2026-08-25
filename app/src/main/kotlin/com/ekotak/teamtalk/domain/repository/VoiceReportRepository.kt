package com.ekotak.teamtalk.domain.repository

import com.ekotak.teamtalk.domain.model.VoiceReport
import kotlinx.coroutines.flow.Flow
import java.io.File

interface VoiceReportRepository {
    /** Strumień notatek z cache; opcjonalnie filtr po połączeniu lub kliencie. */
    fun getVoiceReports(
        callLogId: String? = null,
        clientId: String? = null,
    ): Flow<List<VoiceReport>>

    /** Tworzy notatkę po połączeniu (board360). */
    suspend fun createVoiceReport(
        callLogId: String? = null,
        clientId: String? = null,
        text: String? = null,
        durationSec: Int? = null,
    ): VoiceReport

    /** Wgrywa nagranie audio do istniejącej notatki (ustawia recordingKey). */
    suspend fun uploadRecording(reportId: String, file: File): VoiceReport
}
