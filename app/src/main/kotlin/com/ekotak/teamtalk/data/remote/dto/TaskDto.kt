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

/**
 * Odpowiedź board360 dla zadania — pełny kształt z `domain/task.ts`. Większość
 * pól opcjonalna (odporność na starszy backend): `section`, `slaHours` czy
 * `commentCount` dołożono do panelu później niż samo zadanie.
 */
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
    /** Sekcja zadania (etap lejka + „dotacja"); null = „Bez sekcji". */
    val section: String? = null,
    val estimatedMinutes: Int? = null,
    /** SLA w godzinach: 24 / 168 / 720. */
    val slaHours: Int? = null,
    val commentCount: Int = 0,
    /** Id zlecającego albo „system" dla zadań od automatu. */
    val createdBy: String? = null,
    /** Powiązanie z klientem idzie przez deal — `Task` nie ma `clientId`. */
    val dealId: String? = null,
    /** Nazwa klienta z deala; board360 dokleja ją przy odczycie. */
    val dealName: String? = null,
    val projectId: String? = null,
    val projectName: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

/**
 * Projekt z GET /api/projects — w aplikacji mobilnej używany wyłącznie jako
 * pozycja listy w kroku „kogo dotyczy" kreatora zadania, stąd okrojony kształt.
 */
@Serializable
data class ProjectDto(
    val id: String,
    val name: String,
    val status: String? = null,
    val color: String? = null,
    val taskCount: Int? = null,
)

/** Członek zespołu z GET /api/tasks/members. */
@Serializable
data class TaskMemberDto(
    val id: String,
    val email: String,
    val firstName: String? = null,
    val lastName: String? = null,
    val role: String? = null,
    /** Role dodatkowe — liczą się na równi z główną (np. ktoś z Biura jeżdżący na montaże). */
    val additionalRoles: List<String> = emptyList(),
    /** Funkcje pełnione w firmie (board360 ADR-0013) — po nich filtrujemy osoby
     *  w kreatorze zadania. Domyślnie pusta lista: starszy backend pola nie zwraca. */
    val functions: List<String> = emptyList(),
)
