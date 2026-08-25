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
        callLogId: String?,
        clientId: String?,
    ): Flow<List<VoiceReport>> = channelFlow {
        val localFlow = when {
            callLogId != null -> voiceReportDao.observeByCallLogId(callLogId)
            clientId != null  -> voiceReportDao.observeByClientId(clientId)
            else              -> voiceReportDao.observeAll()
        }

        launch { localFlow.map { it.map { e -> e.toDomain() } }.collect(::send) }

        try {
            val fresh = api.getVoiceReports()
            voiceReportDao.upsertAll(fresh.map { it.toEntity() })
        } catch (_: Exception) {}
    }

    override suspend fun createVoiceReport(
        callLogId: String?,
        clientId: String?,
        text: String?,
        durationSec: Int?,
    ): VoiceReport {
        val dto = api.createVoiceReport(
            CreateVoiceReportRequest(callLogId, clientId, text, durationSec)
        )
        voiceReportDao.upsert(dto.toEntity())
        return dto.toDomain()
    }

    override suspend fun uploadRecording(reportId: String, file: File): VoiceReport =
        withContext(Dispatchers.IO) {
            val part = file.toMultipartPart()
            val dto = api.uploadRecording(reportId, part)
            voiceReportDao.upsert(dto.toEntity())
            dto.toDomain()
        }

    private fun File.toMultipartPart(): MultipartBody.Part {
        val requestBody = asRequestBody("audio/mp4".toMediaType())
        return MultipartBody.Part.createFormData("file", name, requestBody)
    }
}
