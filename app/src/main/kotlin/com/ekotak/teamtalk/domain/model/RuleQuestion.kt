package com.ekotak.teamtalk.domain.model

/**
 * Pytanie reguły przypięte do zadania. Zadanie założone akcją „Zapytaj
 * człowieka" nie jest do odhaczenia, tylko do ODPOWIEDZENIA — karta pokazuje
 * formularz, a odpowiedź ląduje w historii obiektu, którego dotyczy
 * (docs/tasks/modul-reguly.md, decyzje D2 i D3).
 */
data class RuleQuestion(
    val runId: String,
    val taskId: String?,
    val ruleName: String,
    val question: String,
    val subjectType: String,
    val subjectId: String,
    val subjectLabel: String?,
    val occurredAt: String,
    val answerTemplate: String,
    val fields: List<RuleQuestionField>,
    val answered: Boolean,
    /** Treść odpowiedzi, gdy pytanie jest już zamknięte (do pokazania w karcie). */
    val answerNote: String?,
)

data class RuleQuestionField(
    val key: String,
    val label: String,
    val type: String,
    val options: List<RuleQuestionOption>,
    val required: Boolean,
    val unit: String?,
    val hint: String?,
)

data class RuleQuestionOption(val value: String, val label: String)

/**
 * Pytanie reguły czekające na odpowiedź — skrót pod powiadomienie w telefonie.
 * Pełny formularz karta zadania pobiera osobno.
 */
data class PendingRuleQuestion(
    val runId: String,
    val taskId: String,
    val ruleName: String,
    val question: String,
    val subjectLabel: String?,
    val occurredAt: String,
)
