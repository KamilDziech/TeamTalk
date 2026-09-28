package com.ekotak.teamtalk.data.repository

import com.ekotak.teamtalk.data.remote.api.TeamTalkApi
import com.ekotak.teamtalk.data.remote.dto.MeetingAgendaToggleRequest
import com.ekotak.teamtalk.data.remote.dto.MeetingApproveRequest
import com.ekotak.teamtalk.data.remote.dto.MeetingConflictDto
import com.ekotak.teamtalk.data.remote.dto.MeetingDto
import com.ekotak.teamtalk.data.remote.dto.MeetingListItemDto
import com.ekotak.teamtalk.data.remote.dto.MeetingMetaDto
import com.ekotak.teamtalk.data.remote.dto.MeetingRsvpRequest
import com.ekotak.teamtalk.data.remote.dto.MeetingStartRequest
import com.ekotak.teamtalk.data.remote.dto.MeetingUpsertRequest
import com.ekotak.teamtalk.domain.repository.MeetingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** Adapter modułu Spotkanie — cienki most do API, bez pamięci podręcznej. */
@Singleton
class MeetingRepositoryImpl @Inject constructor(
    private val api: TeamTalkApi,
) : MeetingRepository {

    override suspend fun meta(): MeetingMetaDto = api.getMeetingMeta()

    override suspend fun list(): List<MeetingListItemDto> = api.getMeetings()

    override suspend fun get(id: String): MeetingDto = api.getMeeting(id)

    override suspend fun conflicts(
        userIds: List<String>,
        startAt: String,
        durationMin: Int,
        excludeId: String?,
    ): List<MeetingConflictDto> = api.getMeetingConflicts(userIds.joinToString(","), startAt, durationMin, excludeId)

    override suspend fun create(request: MeetingUpsertRequest): MeetingDto = api.createMeeting(request)

    override suspend fun update(id: String, request: MeetingUpsertRequest): MeetingDto =
        api.updateMeeting(id, request)

    override suspend fun delete(id: String) {
        val res = api.deleteMeeting(id)
        if (!res.isSuccessful) throw HttpException(res)
    }

    override suspend fun rsvp(id: String, response: String): MeetingDto =
        api.setMeetingRsvp(id, MeetingRsvpRequest(response))

    override suspend fun start(id: String): MeetingDto = api.startMeeting(id, MeetingStartRequest("phone"))

    override suspend fun command(id: String, command: String): MeetingDto = api.meetingCommand(id, command)

    override suspend fun toggleAgenda(id: String, itemId: String, done: Boolean): MeetingDto =
        api.toggleMeetingAgenda(id, itemId, MeetingAgendaToggleRequest(done))

    override suspend fun uploadRecording(id: String, file: File, durationSec: Int?): MeetingDto =
        withContext(Dispatchers.IO) {
            val part = MultipartBody.Part.createFormData(
                "file",
                file.name,
                file.asRequestBody("audio/aac".toMediaType()),
            )
            val duration = durationSec?.toString()?.toRequestBody("text/plain".toMediaType())
            api.uploadMeetingRecording(id, part, duration)
        }

    override suspend fun approve(id: String, request: MeetingApproveRequest): MeetingDto =
        api.approveMeeting(id, request)
}
