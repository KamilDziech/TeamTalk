package com.ekotak.teamtalk.data.remote.dto

import kotlinx.serialization.Serializable

/** Body POST /api/tasks (board360). `dueAt` jako ISO-8601 (board360 = z.coerce.date). */
@Serializable
data class CreateTaskRequest(
    val title: String,
    val description: String? = null,
    val assigneeId: String? = null,
    val dueAt: String? = null,
    val priority: String? = null,
)

/** Odpowiedź board360 dla zadania. Większość pól opcjonalna (odporność na zmiany). */
@Serializable
data class TaskResponseDto(
    val id: String,
    val title: String,
    val description: String? = null,
    val assigneeId: String? = null,
    val assigneeEmail: String? = null,
    val dueAt: String? = null,
    val status: String? = null,
    val priority: String? = null,
    val createdAt: String? = null,
)

/** Członek zespołu z GET /api/tasks/members. */
@Serializable
data class TaskMemberDto(
    val id: String,
    val email: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val role: String? = null,
)
