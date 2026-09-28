package com.ekotak.teamtalk.domain.repository

import com.ekotak.teamtalk.data.remote.dto.MeetingApproveRequest
import com.ekotak.teamtalk.data.remote.dto.MeetingConflictDto
import com.ekotak.teamtalk.data.remote.dto.MeetingDto
import com.ekotak.teamtalk.data.remote.dto.MeetingListItemDto
import com.ekotak.teamtalk.data.remote.dto.MeetingMetaDto
import com.ekotak.teamtalk.data.remote.dto.MeetingUpsertRequest
import java.io.File

/**
 * Moduł Spotkanie. WYŁĄCZNIE ONLINE — poza nagraniem: to leży w pliku i idzie
 * workerem, gdy wróci sieć (spotkanie w piwnicy bez zasięgu też się nagra).
 * Planowanie i akceptacja wymagają serwera, bo dotykają kalendarzy i zadań
 * innych osób — kolejka offline dałaby tu tylko konflikty.
 */
interface MeetingRepository {
    suspend fun meta(): MeetingMetaDto
    suspend fun list(): List<MeetingListItemDto>
    suspend fun get(id: String): MeetingDto
    suspend fun conflicts(userIds: List<String>, startAt: String, durationMin: Int, excludeId: String?): List<MeetingConflictDto>
    suspend fun create(request: MeetingUpsertRequest): MeetingDto
    suspend fun update(id: String, request: MeetingUpsertRequest): MeetingDto
    suspend fun delete(id: String)
    suspend fun rsvp(id: String, response: String): MeetingDto
    suspend fun start(id: String): MeetingDto
    /** pause | resume | finish | skip-recording | retry */
    suspend fun command(id: String, command: String): MeetingDto
    suspend fun toggleAgenda(id: String, itemId: String, done: Boolean): MeetingDto
    suspend fun uploadRecording(id: String, file: File, durationSec: Int?): MeetingDto
    suspend fun approve(id: String, request: MeetingApproveRequest): MeetingDto
}
