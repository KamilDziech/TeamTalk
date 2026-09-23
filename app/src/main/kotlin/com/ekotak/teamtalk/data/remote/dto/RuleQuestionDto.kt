package com.ekotak.teamtalk.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Pytanie reguły doczepione do zadania (`GET /api/rules/questions/by-task/:id`).
 * Pola formularza przychodzą z serwera, bo to reguła decyduje, o co pyta —
 * telefon ich nie zna i nie ma po co znać.
 */
@Serializable
data class RuleQuestionDto(
    val runId: String,
    val taskId: String? = null,
    val ruleName: String,
    val question: String,
    val subjectType: String,
    val subjectId: String,
    val subjectLabel: String? = null,
    val occurredAt: String,
    val answerTemplate: String,
    val fields: List<RuleQuestionFieldDto> = emptyList(),
    val status: String,
    val answer: JsonObject? = null,
)

/** Jedno pole formularza odpowiedzi (lustro `FieldDef` z katalogu reguł). */
@Serializable
data class RuleQuestionFieldDto(
    val key: String,
    val label: String,
    val type: String,
    val options: List<RuleOptionDto>? = null,
    val required: Boolean = false,
    val unit: String? = null,
    val hint: String? = null,
)

@Serializable
data class RuleOptionDto(val value: String, val label: String)

/** Body `POST /api/rules/questions/:runId/answer`. */
@Serializable
data class RuleAnswerRequest(val answer: JsonObject)

/** Odpowiedź serwera: gdzie wylądował wpis (zdarzenie albo naprawa). */
@Serializable
data class RuleAnswerResultDto(val status: String, val resultRef: String? = null)

/**
 * Odpowiedź zapisana bez zasięgu — całe żądanie odłożone do kolejki zadań pod
 * pseudopolem `__rule_answer`. Trzymamy `runId` obok ciała, bo adres wysyłki
 * bierze się z niego, a nie z identyfikatora zadania.
 */
@Serializable
data class QueuedRuleAnswer(val runId: String, val answer: JsonObject)

/**
 * Pytanie reguły czekające na zalogowanego (`GET /api/rules/questions/pending`).
 * Odpytuje je robotnik w tle, żeby pokazać powiadomienie — pusha przez FCM
 * w tym projekcie nie ma, więc telefon pyta sam.
 */
@Serializable
data class PendingRuleQuestionDto(
    val runId: String,
    val taskId: String,
    val ruleName: String,
    val question: String,
    val subjectLabel: String? = null,
    val occurredAt: String,
)
