package com.ekotak.teamtalk.data.remote.dto

import kotlinx.serialization.Serializable

// Moduł Spotkanie (2026-09-28) — kontrakt 1:1 z `api/src/modules/meetings`.
// Moduł jest online, poza nagraniem: to leży w pliku i idzie workerem, gdy wróci sieć.

@Serializable
data class MeetingPersonDto(
    val id: String,
    val name: String = "",
)

@Serializable
data class MeetingMetaPersonDto(
    val id: String,
    val name: String = "",
    val role: String = "",
    val isBoard: Boolean = false,
)

@Serializable
data class MeetingTypeDto(
    val key: String,
    val label: String = "",
    val confidential: Boolean = false,
    val boardOnly: Boolean = false,
    /** Rodzaj `contractor` — wymaga kontrahenta z kartoteki (Kontrahenci / Inne). */
    val needsContractor: Boolean = false,
    val canCreate: Boolean = true,
    val agendaTemplate: List<String> = emptyList(),
)

@Serializable
data class MeetingMeDto(val id: String, val isBoard: Boolean = false)

@Serializable
data class MeetingMetaDto(
    val me: MeetingMeDto,
    val types: List<MeetingTypeDto> = emptyList(),
    val departments: List<String> = emptyList(),
    val people: List<MeetingMetaPersonDto> = emptyList(),
)

@Serializable
data class MeetingListItemDto(
    val id: String,
    val type: String,
    val typeLabel: String = "",
    val title: String = "",
    val status: String = "scheduled",
    val startAt: String = "",
    val durationMin: Int = 60,
    val host: MeetingPersonDto = MeetingPersonDto(""),
    val client: MeetingPersonDto? = null,
    val participantCount: Int = 0,
    val agendaCount: Int = 0,
    val agendaDone: Int = 0,
    val isHost: Boolean = false,
    val myRsvp: String? = null,
    /** D9: prowadzący musi nagrać podsumowanie głosem (spotkanie bez nagrania). */
    val needsVoiceSummary: Boolean = false,
)

/**
 * D9: spotkanie bez nagrania → okno „Nagraj podsumowanie". Tylko dla prowadzącego.
 * [reason]: not_started | no_recording | failed; [required] = okno wyskakuje samo.
 */
@Serializable
data class MeetingVoiceSummaryDto(
    val reason: String = "no_recording",
    val required: Boolean = false,
    val canRetry: Boolean = false,
)

@Serializable
data class MeetingParticipantDto(
    val id: String,
    val name: String = "",
    val role: String = "participant",
    val rsvp: String? = null,
)

@Serializable
data class MeetingAgendaItemDto(
    val id: String,
    val text: String = "",
    val done: Boolean = false,
    val aiDiscussed: Boolean? = null,
)

@Serializable
data class MeetingProposalDto(
    val id: String,
    val title: String = "",
    val description: String? = null,
    val area: String? = null,
    val assigneeId: String? = null,
    val assigneeName: String? = null,
    val durationDays: Int? = null,
    val status: String = "proposed",
    val source: String = "ai",
    val taskId: String? = null,
)

@Serializable
data class MeetingDto(
    val id: String,
    val type: String,
    val typeLabel: String = "",
    val confidential: Boolean = false,
    val title: String = "",
    val status: String = "scheduled",
    val startAt: String = "",
    val durationMin: Int = 60,
    val location: String? = null,
    val host: MeetingPersonDto = MeetingPersonDto(""),
    val client: MeetingContractorDto? = null,
    val participants: List<MeetingParticipantDto> = emptyList(),
    val agenda: List<MeetingAgendaItemDto> = emptyList(),
    val recordingDevice: String? = null,
    val startedAt: String? = null,
    val pausedAt: String? = null,
    val endedAt: String? = null,
    val elapsedSec: Long = 0,
    val hasRecording: Boolean = false,
    val recordingSec: Int? = null,
    /** meeting = nagranie przebiegu, summary = podsumowanie nagrane głosem (D9). */
    val recordingKind: String? = null,
    val voiceSummary: MeetingVoiceSummaryDto? = null,
    val transcriptionStatus: String? = null,
    val transcriptionError: String? = null,
    val transcript: String? = null,
    val summary: String? = null,
    val decisions: List<String> = emptyList(),
    val proposals: List<MeetingProposalDto> = emptyList(),
    val approvedAt: String? = null,
    val approvedByName: String? = null,
    val isHost: Boolean = false,
    val canEdit: Boolean = false,
    val canControl: Boolean = false,
    val canApprove: Boolean = false,
    val myRsvp: String? = null,
    val serverTime: String? = null,
)

@Serializable
data class MeetingConflictDto(
    val userId: String,
    val name: String = "",
    val startAt: String = "",
    val endAt: String = "",
)

@Serializable
data class MeetingAgendaInput(val id: String? = null, val text: String)

@Serializable
data class MeetingUpsertRequest(
    val type: String,
    val title: String,
    val hostId: String,
    val participantIds: List<String>,
    val startAt: String,
    val durationMin: Int,
    val location: String? = null,
    val clientId: String? = null,
    val agenda: List<MeetingAgendaInput>,
)

/** Kontrahent z kartoteki (grupa Kontrahenci albo Inne) — spotkanie z kontrahentem. */
@Serializable
data class MeetingContractorDto(
    val id: String,
    val name: String = "",
    /** Osoba kontaktowa, gdy `name` to nazwa firmy. */
    val person: String? = null,
    val category: String = "inne",
    val businessRole: String? = null,
    val phone: String? = null,
    val email: String? = null,
)

/** Szybkie dodanie kontrahenta z kreatora — nowy wpis kartoteki w grupie „Inne". */
@Serializable
data class MeetingContractorCreateRequest(
    val companyName: String? = null,
    val personName: String? = null,
    val businessRole: String? = null,
    val phone: String? = null,
    val email: String? = null,
)

@Serializable
data class MeetingStartRequest(val device: String = "phone")

@Serializable
data class MeetingAgendaToggleRequest(val done: Boolean)

@Serializable
data class MeetingRsvpRequest(val response: String)

@Serializable
data class MeetingApproveAgenda(val id: String, val done: Boolean)

@Serializable
data class MeetingApproveProposal(
    val id: String? = null,
    val status: String,
    val title: String,
    val description: String? = null,
    val area: String? = null,
    val assigneeId: String? = null,
    val durationDays: Int? = null,
)

@Serializable
data class MeetingApproveRequest(
    val summary: String,
    val decisions: List<String>,
    val agenda: List<MeetingApproveAgenda>,
    val proposals: List<MeetingApproveProposal>,
)
