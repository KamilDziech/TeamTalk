package com.ekotak.teamtalk.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Komentarz karty zadania (`GET /api/tasks/{id}/comments`). Dane autora
 * dołącza backend przy odczycie — na telefonie nie ma listy userów pod ręką.
 */
@Serializable
data class TaskCommentDto(
    val id: String,
    val taskId: String? = null,
    val authorId: String,
    val authorEmail: String? = null,
    val authorFirstName: String? = null,
    val authorLastName: String? = null,
    val body: String,
    val createdAt: String,
)

/**
 * Nowy komentarz. `mentions` to TOKENY wywołań, nie nazwiska: „user:<id>",
 * „role:<rola>", „watchers", „all" — backend rozwija je do konkretnych osób.
 * Sam tekst komentarza zostaje z „@Imię Nazwisko" (tak samo robi panel).
 */
@Serializable
data class AddCommentRequest(
    val body: String,
    val mentions: List<String> = emptyList(),
)

/** Komentarz w Komunikatorze — backend podpisuje autora i mówi, czy to nasz. */
@Serializable
data class DiscussionCommentDto(
    val id: String,
    val body: String,
    val authorId: String,
    val authorName: String,
    val createdAt: String,
    val mine: Boolean = false,
)

/**
 * Pozycja skrzynki dyskusji. `title` liczy backend wg ustaleń z 2026-09-01:
 * „Nazwisko · kod deala" dla zadań pod dealem, nazwa projektu dla projektowych,
 * tytuł zadania dla luźnych — telefon go NIE składa sam.
 */
@Serializable
data class DiscussionSummaryDto(
    val taskId: String,
    val taskTitle: String,
    val title: String,
    val clientId: String? = null,
    val clientName: String? = null,
    val dealId: String? = null,
    val dealCode: String? = null,
    val projectId: String? = null,
    val projectName: String? = null,
    val lastComment: DiscussionCommentDto? = null,
    val commentCount: Int = 0,
    val unreadCount: Int = 0,
    val mentionedMe: Boolean = false,
)

/** Pełny wątek jednej dyskusji (`GET /api/discussions/{taskId}`). */
@Serializable
data class DiscussionThreadDto(
    val taskId: String,
    val taskTitle: String,
    val title: String,
    val clientId: String? = null,
    val clientName: String? = null,
    val dealId: String? = null,
    val dealCode: String? = null,
    val projectId: String? = null,
    val projectName: String? = null,
    val comments: List<DiscussionCommentDto> = emptyList(),
)

/** Licznik nieprzeczytanych do plakietki i do powiadomień. */
@Serializable
data class UnreadCountDto(val count: Int = 0)
