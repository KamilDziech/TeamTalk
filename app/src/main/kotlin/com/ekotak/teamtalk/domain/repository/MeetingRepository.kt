package com.ekotak.teamtalk.domain.repository

import com.ekotak.teamtalk.data.remote.dto.MeetingAgendaProposalItemDto
import com.ekotak.teamtalk.data.remote.dto.MeetingAgendaProposalRequest
import com.ekotak.teamtalk.data.remote.dto.MeetingApproveRequest
import com.ekotak.teamtalk.data.remote.dto.MeetingConflictDto
import com.ekotak.teamtalk.data.remote.dto.MeetingContractorCreateRequest
import com.ekotak.teamtalk.data.remote.dto.MeetingContractorDto
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
    /** Kartoteka: grupy Kontrahenci + Inne. */
    suspend fun searchContractors(q: String): List<MeetingContractorDto>
    /** Nowy wpis kartoteki w grupie „Inne". */
    suspend fun createContractor(request: MeetingContractorCreateRequest): MeetingContractorDto
    suspend fun get(id: String): MeetingDto
    suspend fun conflicts(
        userIds: List<String>,
        startAt: String,
        durationMin: Int,
        excludeId: String?,
        dayCount: Int = 1,
    ): List<MeetingConflictDto>
    suspend fun create(request: MeetingUpsertRequest): MeetingDto
    suspend fun update(id: String, request: MeetingUpsertRequest): MeetingDto
    suspend fun delete(id: String)
    suspend fun rsvp(id: String, response: String): MeetingDto
    suspend fun start(id: String): MeetingDto
    /** pause | resume | finish | retry */
    suspend fun command(id: String, command: String): MeetingDto
    suspend fun toggleAgenda(id: String, itemId: String, done: Boolean): MeetingDto
    suspend fun uploadRecording(id: String, file: File, durationSec: Int?, kind: String = KIND_MEETING): MeetingDto
    suspend fun approve(id: String, request: MeetingApproveRequest): MeetingDto
    /** v2 (D15): dyktowanie „Co chcesz omówić?" → tekst. */
    suspend fun dictation(file: File): String
    /** v2 (D15): propozycja agendy z czasami (suma == totalMin). */
    suspend fun proposeAgenda(request: MeetingAgendaProposalRequest): List<MeetingAgendaProposalItemDto>
}

/** Rodzaj nagrania wysyłanego na serwer: przebieg spotkania albo podsumowanie głosowe (D9). */
const val KIND_MEETING = "meeting"
const val KIND_SUMMARY = "summary"
