package com.ekotak.teamtalk.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ekotak.teamtalk.data.local.preferences.SessionPreferences
import com.ekotak.teamtalk.data.notification.NotificationHelper
import com.ekotak.teamtalk.domain.repository.TaskRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * Powiadomienia o pytaniach reguł czekających na odpowiedź (moduł „Reguły").
 *
 * board360 nie wysyła pusha, więc telefon pyta sam — co 15 minut, wzorem
 * robotnika od wywołań w komentarzach. Trąbimy TYLKO o pytaniach, których
 * jeszcze nie pokazaliśmy: pytanie wisi w stanie „czeka" aż do odpowiedzi, więc
 * bez listy widzianych wracałoby w szufladzie co kwadrans.
 *
 * Bez sesji nie ma czego pytać — kończymy sukcesem, żeby WorkManager nie
 * próbował w kółko po wylogowaniu.
 */
@HiltWorker
class RuleQuestionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val tasks: TaskRepository,
    private val sessionPreferences: SessionPreferences,
    private val notifications: NotificationHelper,
) : CoroutineWorker(context, params) {

    companion object {
        const val UNIQUE_NAME = "rule_questions_poll"
    }

    override suspend fun doWork(): Result {
        sessionPreferences.token.first() ?: return Result.success()

        val pending = runCatching { tasks.getPendingRuleQuestions() }
            .getOrElse { return Result.retry() }
        val seen = sessionPreferences.ruleQuestionsSeen.first()

        for (question in pending) {
            if (question.runId in seen) continue
            notifications.showRuleQuestionNotification(
                title = question.ruleName.ifBlank { "Pytanie reguły" },
                text = listOfNotNull(question.subjectLabel, question.question)
                    .filter { it.isNotBlank() }
                    .joinToString(" — "),
                taskId = question.taskId,
                runId = question.runId,
            )
        }

        // Zapisujemy stan Z TEGO przebiegu, a nie sumę z poprzednimi: pytanie,
        // na które ktoś odpowiedział, wypada z listy oczekujących i nie ma po co
        // zajmować miejsca w preferencjach. Gdyby wróciło (bo zadanie znów
        // otwarto), powiadomienie pojawi się ponownie — i słusznie.
        sessionPreferences.saveRuleQuestionsSeen(pending.map { it.runId }.toSet())
        return Result.success()
    }
}
