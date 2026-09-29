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
    /** Rekrutacja — wymaga kandydata z kartoteki (grupa Kandydaci). */
    val needsCandidate: Boolean = false,
    /** Rekrutacja — start nagrywania wymaga potwierdzenia zgody kandydata. */
    val needsConsent: Boolean = false,
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
    // v2: wielodniowe + ocena AI (stare API ich nie zna — stąd domyślne).
    val dayCount: Int = 1,
    val score: Int? = null,
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
    /** v2: planowany czas punktu w minutach (opcjonalny). */
    val durationMin: Int? = null,
)

/** v2: termin jednego dnia spotkania wielodniowego. */
@Serializable
data class MeetingDayDto(
    val day: Int,
    val startAt: String = "",
    val endAt: String = "",
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
    /** Rekrutacja: start pyta o zgodę kandydata, dopóki nikt jej nie potwierdził. */
    val needsConsent: Boolean = false,
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
    // v2 (D14): `durationMin` to długość JEDNEGO dnia, `dayCount` kolejnych dni.
    val dayCount: Int = 1,
    /** Dzień, który trwa albo jest następny do włączenia (1..dayCount). */
    val currentDay: Int = 1,
    val days: List<MeetingDayDto> = emptyList(),
    /** dayCount × durationMin. */
    val plannedMin: Int = 0,
    // v2 (D16): ocena AI 0–100 — zostaje po akceptacji, razem z dygresjami.
    val score: Int? = null,
    val scoreReason: String? = null,
    val digressions: List<String> = emptyList(),
)

@Serializable
data class MeetingConflictDto(
    val userId: String,
    val name: String = "",
    val startAt: String = "",
    val endAt: String = "",
)

@Serializable
data class MeetingAgendaInput(val id: String? = null, val text: String, val durationMin: Int? = null)

@Serializable
data class MeetingUpsertRequest(
    val type: String,
    val title: String,
    val hostId: String,
    val participantIds: List<String>,
    val startAt: String,
    /** Minuty JEDNEGO dnia (5..1440). */
    val durationMin: Int,
    /** 1..14 — bez wartości domyślnej, bo nasz Json nie koduje domyślnych. */
    val dayCount: Int,
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

/**
 * Szybkie dodanie z kreatora — nowy wpis kartoteki w grupie „Inne"
 * (`kind = "contractor"`) albo „Kandydaci" (`kind = "candidate"`, rekrutacja).
 */
@Serializable
data class MeetingContractorCreateRequest(
    val kind: String = "contractor",
    val companyName: String? = null,
    val personName: String? = null,
    val businessRole: String? = null,
    val phone: String? = null,
    val email: String? = null,
)

/** Bez wartości domyślnej: nasz Json jej nie koduje, poszłoby `{}` i API odda 400. */
@Serializable
data class MeetingStartRequest(
    val device: String,
    /** Rekrutacja: zgoda kandydata na nagranie. `false` nie idzie w JSON — API czyta brak jak brak zgody. */
    val consent: Boolean = false,
)

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

// ── v2: dyktowanie i propozycja agendy (D15) ─────────────────────────────────

@Serializable
data class MeetingDictationDto(val text: String = "")

@Serializable
data class MeetingAgendaProposalRequest(
    val type: String,
    val title: String? = null,
    val text: String,
    /** dayCount × durationMin — suma minut punktów w odpowiedzi. */
    val totalMin: Int,
    val dayCount: Int? = null,
)

@Serializable
data class MeetingAgendaProposalItemDto(
    val text: String = "",
    val durationMin: Int = 0,
)

@Serializable
data class MeetingAgendaProposalDto(
    val items: List<MeetingAgendaProposalItemDto> = emptyList(),
)
