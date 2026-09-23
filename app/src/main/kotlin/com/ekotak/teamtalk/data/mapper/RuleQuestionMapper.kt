package com.ekotak.teamtalk.data.mapper

import com.ekotak.teamtalk.data.remote.dto.RuleQuestionDto
import com.ekotak.teamtalk.data.remote.dto.RuleQuestionFieldDto
import com.ekotak.teamtalk.domain.model.RuleQuestion
import com.ekotak.teamtalk.domain.model.RuleQuestionField
import com.ekotak.teamtalk.domain.model.RuleQuestionOption
import kotlinx.serialization.json.jsonPrimitive

fun RuleQuestionDto.toDomain(): RuleQuestion = RuleQuestion(
    runId = runId,
    taskId = taskId,
    ruleName = ruleName,
    question = question,
    subjectType = subjectType,
    subjectId = subjectId,
    subjectLabel = subjectLabel,
    occurredAt = occurredAt,
    answerTemplate = answerTemplate,
    fields = fields.map { it.toDomain() },
    answered = status == "answered",
    answerNote = answer?.get("note")?.jsonPrimitive?.contentOrNull(),
)

private fun RuleQuestionFieldDto.toDomain(): RuleQuestionField = RuleQuestionField(
    key = key,
    label = label,
    type = type,
    options = options.orEmpty().map { RuleQuestionOption(it.value, it.label) },
    required = required,
    unit = unit,
    hint = hint,
)

/** `JsonPrimitive` bywa `JsonNull` — wtedy treści nie ma, a nie jest nią „null". */
private fun kotlinx.serialization.json.JsonPrimitive.contentOrNull(): String? =
    if (this is kotlinx.serialization.json.JsonNull) null else content
