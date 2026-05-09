package com.ekotak.teamtalk.data.repository

import com.ekotak.teamtalk.data.local.dao.VoiceReportDao
import com.ekotak.teamtalk.data.mapper.toDomain
import com.ekotak.teamtalk.data.mapper.toEntity
import com.ekotak.teamtalk.data.remote.api.TeamTalkApi
import com.ekotak.teamtalk.data.remote.dto.CreateVoiceReportRequest
import com.ekotak.teamtalk.domain.model.VoiceReport
import com.ekotak.teamtalk.domain.repository.VoiceReportRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import javax.inject.Inject

class VoiceReportRepositoryImpl @Inject constructor(
    private val api: TeamTalkApi,
    private val voiceReportDao: VoiceReportDao,
) : VoiceReportRepository {

    override fun getVoiceReports(
        callLogIdIn: List<String>?,
        callLogIdEq: String?,
    ): Flow<List<VoiceReport>> = channelFlow {
        val localFlow = when {
            callLogIdEq != null -> voiceReportDao.observeByCallLogId(callLogIdEq)
            callLogIdIn != null -> voiceReportDao.observeByCallLogIds(callLogIdIn)
            else                -> voiceReportDao.observeByCallLogIds(emptyList())
        }

        launch { localFlow.map { it.map { e -> e.toDomain() } }.collect(::send) }

        try {
            val fresh = api.getVoiceReports(
                callLogIdIn = callLogIdIn?.joinToString(","),
                callLogIdEq = callLogIdEq,
            )
            voiceReportDao.upsertAll(fresh.map { it.toEntity() })
        } catch (_: Exception) {}
    }

    override suspend fun createVoiceReport(
        callLogId: String,
        audioUrl: String?,
        transcription: String?,
        aiSummary: String?,
        callCount: Int,
    ): VoiceReport {
        val dto = api.createVoiceReport(
            CreateVoiceReportRequest(callLogId, audioUrl, transcription, aiSummary, callCount)
        )
        voiceReportDao.upsert(dto.toEntity())
        return dto.toDomain()
    }

    override suspend fun uploadAudio(file: File): String = withContext(Dispatchers.IO) {
        val part = file.toMultipartPart()
        api.uploadAudio(part).publicUrl
    }

    override suspend fun transcribeAudio(file: File): String? = withContext(Dispatchers.IO) {
        val part = file.toMultipartPart()
        api.transcribeAudio(part).transcription
    }

    private fun File.toMultipartPart(): MultipartBody.Part {
        val requestBody = asRequestBody("audio/m4a".toMediaType())
        return MultipartBody.Part.createFormData("file", name, requestBody)
    }
}
