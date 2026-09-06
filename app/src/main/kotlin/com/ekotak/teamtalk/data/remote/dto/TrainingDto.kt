package com.ekotak.teamtalk.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Moduł SZKOLENIA (trasy pod `/api/training`) — część dla pracownika. Wszystkie
 * trasy stoją za `training.view`, które ma każda rola, więc token mobilny wystarcza
 * i po stronie board360 nic nie trzeba dopisywać.
 */

/** Pozycja listy „Moje szkolenia" (`GET /api/training/my`). */
@Serializable
data class MyAssignmentDto(
    val id: String = "",
    val lessonId: String = "",
    val title: String = "",
    val description: String? = null,
    val status: String = "assigned",
    val score: Int? = null,
    val passThreshold: Int = 80,
    val questionCount: Int = 0,
    val dueDate: String? = null,
    val completedAt: String? = null,
    val expiresAt: String? = null,
    /** Imię i nazwisko przypisującego — API oddaje gotowy napis albo `null`. */
    val assignedBy: String? = null,
)

@Serializable
data class TrainingLessonDto(
    val id: String = "",
    val knowledgeNodeId: String? = null,
    val title: String = "",
    val description: String? = null,
    val videoUrl: String = "",
    val videoProvider: String = "",
    val passThreshold: Int = 80,
    val validityMonths: Int? = null,
    val position: Int = 0,
)

@Serializable
data class PlayableQuestionDto(
    val id: String = "",
    val prompt: String = "",
    val kind: String = "single",
    val options: List<String> = emptyList(),
)

/** `GET /api/training/lessons/:id/play` — 403, gdy lekcja nie jest przypisana. */
@Serializable
data class PlayableLessonDto(
    val assignmentId: String = "",
    val lesson: TrainingLessonDto = TrainingLessonDto(),
    val questions: List<PlayableQuestionDto> = emptyList(),
    val status: String = "assigned",
    val lastScore: Int? = null,
)

@Serializable
data class TrainingAnswerDto(val questionId: String, val selected: List<Int>)

@Serializable
data class SubmitTrainingRequest(val answers: List<TrainingAnswerDto>)

@Serializable
data class GradeResultDto(
    val score: Int = 0,
    val passed: Boolean = false,
    val passThreshold: Int = 0,
    val correctCount: Int = 0,
    val total: Int = 0,
)

// ── Moje poziomy (`GET /api/domain-skills/me`) ───────────────────────────────
// Ta trasa, w odróżnieniu od modułu szkoleń, pakuje odpowiedź w `{data:...}`.

@Serializable
data class MySkillsEnvelopeDto(val data: MySkillsDto = MySkillsDto())

@Serializable
data class MySkillsDto(
    val rows: List<DomainSkillRowDto> = emptyList(),
    val domains: List<MyDomainInfoDto> = emptyList(),
)

/**
 * Wiersz matrycy poziomów. `levels` bywa gołym szczeblem (`sredni`) albo
 * szczeblem z częścią (`sredni:teoria`) — normalizuje to `expandLevels`.
 * Pól edycyjnych panelu (`marks`, `assessedById`, `requiredSet`) nie czytamy:
 * widok pracownika ich nie pokazuje.
 */
@Serializable
data class DomainSkillRowDto(
    val domainId: String = "",
    val levels: List<String> = emptyList(),
    val assessed: Boolean = false,
    val requiredLevel: String? = null,
    val requiredPart: String? = null,
    val requiredUntil: String? = null,
    val note: String? = null,
)

@Serializable
data class MyDomainInfoDto(
    val id: String = "",
    val name: String = "",
    val kind: String = "",
    val desc: String = "",
    val goals: Map<String, String> = emptyMap(),
)
