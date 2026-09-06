package com.ekotak.teamtalk.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Moduł Projekty (board360 `api/src/modules/projects`). Sam `ProjectDto` mieszka
 * w `TaskDto.kt` — powstał wcześniej dla kreatora zadania i rozszerzyliśmy go
 * zamiast zakładać drugi kształt na ten sam endpoint.
 *
 * Wszystkie pola poza `id` i `name` są nullable: moduł rósł etapami (E1–E4)
 * i starszy backend nie musi znać najnowszych.
 */
@Serializable
data class ProjectMilestoneDto(
    val id: String,
    val projectId: String,
    val name: String,
    val dueAt: String? = null,
    val acceptanceCriteria: String? = null,
    val ownerEmail: String? = null,
    val doneAt: String? = null,
    val position: Int = 0,
    val taskCount: Int? = null,
    val doneCount: Int? = null,
)

@Serializable
data class ProjectTaskDto(
    val id: String,
    val projectId: String? = null,
    val title: String,
    val assigneeId: String? = null,
    val assigneeEmail: String? = null,
    val startAt: String? = null,
    val dueAt: String? = null,
    val status: String? = null,
    val lifecycle: String? = null,
    val milestoneId: String? = null,
    val estimatedMinutes: Int? = null,
    val actualMinutes: Int? = null,
)

@Serializable
data class ProjectMemberDto(
    val userId: String,
    val email: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    /** sponsor | manager | worker | observer (albo starsze owner/member). */
    val role: String? = null,
)

/** `GET /api/projects/{id}` — jeden strzał na całą kartę projektu. */
@Serializable
data class ProjectDetailDto(
    val id: String,
    val name: String,
    val description: String? = null,
    val color: String? = null,
    val status: String? = null,
    val stage: String? = null,
    val department: String? = null,
    val managerEmail: String? = null,
    val sponsorEmail: String? = null,
    val problemStatement: String? = null,
    val metricName: String? = null,
    val metricBaseline: String? = null,
    val metricTarget: String? = null,
    val dueAt: String? = null,
    val milestones: List<ProjectMilestoneDto> = emptyList(),
    val tasks: List<ProjectTaskDto> = emptyList(),
    val members: List<ProjectMemberDto> = emptyList(),
)

/** Body `POST /api/projects/tasks/{id}/close`. `actualMinutes = null` znaczy
 * „nie podano" — rozliczenie policzy zadanie po estymacie. */
@Serializable
data class TaskCloseDto(
    val actualMinutes: Int? = null,
)

/** Body `POST /api/projects` dla pomysłu z Poczekalni (status `idea`). */
@Serializable
data class IdeaCreateDto(
    val name: String,
    val description: String? = null,
    val department: String? = null,
    val status: String = "idea",
)
