package com.ekotak.teamtalk.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Kontrakt `GET /api/schedule?from&to` — harmonogram ekip z panelu
 * (board360 `installations/application/schedule.usecases.ts`, typy panelu
 * w `web/src/app/app/schedule/types.ts`).
 *
 * Daty to gołe dni `YYYY-MM-DD`: oś liczy dni, nie godziny. Ostrzeżenia
 * (urlop, podwójna osoba, pojemność…) liczy SERWER — telefon ich nie odgaduje,
 * tylko pokazuje to, co przyszło.
 */
@Serializable
data class ScheduleDto(
    val from: String,
    val to: String,
    val settings: ScheduleSettingsDto = ScheduleSettingsDto(),
    val crews: List<ScheduleCrewDto> = emptyList(),
    val people: List<SchedulePersonDto> = emptyList(),
    val leaves: List<ScheduleLeaveDto> = emptyList(),
    val days: List<ScheduleDayDto> = emptyList(),
    val stages: List<ScheduleStageDto> = emptyList(),
    val backlog: List<ScheduleBacklogDto> = emptyList(),
    val unplanned: List<ScheduleUnplannedDto> = emptyList(),
)

@Serializable
data class ScheduleSettingsDto(
    val defaultGapDays: Int = 0,
    val publishEnabled: Boolean = false,
)

@Serializable
data class ScheduleCrewDto(
    val id: String,
    val name: String,
    val color: String? = null,
    val external: Boolean = false,
    val leaderId: String? = null,
    val memberIds: List<String> = emptyList(),
)

@Serializable
data class SchedulePersonDto(
    val id: String,
    val name: String,
    val skills: List<String> = emptyList(),
    val crewIds: List<String> = emptyList(),
    val montage: Boolean = false,
)

@Serializable
data class ScheduleLeaveDto(
    val userId: String,
    val start: String,
    val end: String,
    val type: String,
)

@Serializable
data class ScheduleDayDto(
    val date: String,
    val workday: Boolean = true,
    val load: Int = 0,
    val limit: Int = 0,
)

@Serializable
data class ScheduleAssigneeDto(
    val userId: String,
    val role: String? = null,
)

@Serializable
data class ScheduleWarningDto(
    val code: String,
    val message: String,
    val userId: String? = null,
    val otherInstallationId: String? = null,
    val day: String? = null,
)

@Serializable
data class SchedulePublishedDto(
    val scheduledAt: String,
    val endDate: String,
    val crewId: String? = null,
    val assigneeIds: List<String> = emptyList(),
)

@Serializable
data class ScheduleStageDto(
    val id: String,
    val dealId: String,
    val clientName: String,
    val city: String? = null,
    val title: String,
    val status: String,
    val scheduledAt: String,
    val endDate: String,
    val durationDays: Int = 1,
    val crewId: String? = null,
    val assignees: List<ScheduleAssigneeDto> = emptyList(),
    val nodeIds: List<String> = emptyList(),
    val requiredRoles: List<String> = emptyList(),
    val minGapDays: Int? = null,
    val gapLabel: String? = null,
    val locked: Boolean = false,
    val stageNo: Int = 1,
    val stageCount: Int = 1,
    val draft: Boolean = false,
    val published: SchedulePublishedDto? = null,
    val warnings: List<ScheduleWarningDto> = emptyList(),
)

@Serializable
data class ScheduleBacklogDto(
    val id: String,
    val dealId: String,
    val clientName: String,
    val city: String? = null,
    val title: String,
    val status: String,
    val scheduledAt: String,
    val datePrecision: String? = null,
    val windowKey: String? = null,
    val durationDays: Int = 1,
    val reservationConfirmed: Boolean = false,
    val reservationNote: String? = null,
)

@Serializable
data class ScheduleUnplannedDto(
    val dealId: String,
    val clientName: String,
    val city: String? = null,
    val installations: List<ScheduleUnplannedInstallationDto> = emptyList(),
    val since: String,
)

@Serializable
data class ScheduleUnplannedInstallationDto(
    val id: String,
    val name: String,
)

/** `POST /api/schedule/publish` — poniedziałek publikowanego tygodnia. */
@Serializable
data class SchedulePublishRequest(val weekStart: String)

@Serializable
data class SchedulePublishResponse(
    val weekStart: String? = null,
    val published: Int = 0,
    val notified: Int = 0,
)

/** `PUT /api/schedule/settings` — firmowy przełącznik publikacji tygodni. */
@Serializable
data class ScheduleSettingsRequest(val publishEnabled: Boolean)

/** `PUT /api/schedule/crew-order` — ekipy od góry do dołu osi (wspólne dla firmy). */
@Serializable
data class ScheduleCrewOrderRequest(val crewIds: List<String>)

/** `POST /api/schedule/deal/{dealId}/plan` — ile etapów założył „Zaplanuj". */
@Serializable
data class SchedulePlanDealResponse(val created: Int = 0)
