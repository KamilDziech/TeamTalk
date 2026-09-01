package com.ekotak.teamtalk.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ekotak.teamtalk.data.local.preferences.SessionPreferences
import com.ekotak.teamtalk.data.notification.NotificationHelper
import com.ekotak.teamtalk.domain.model.TaskStatus
import com.ekotak.teamtalk.domain.repository.TaskRepository
import com.ekotak.teamtalk.presentation.task.isDueToday
import com.ekotak.teamtalk.presentation.task.isOverdue
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.time.LocalDate

/**
 * Przypomnienie o zadaniach na dziś i zaległych (E4).
 *
 * Liczy z cache Room, więc działa też bez zasięgu — odświeżenie z serwera jest
 * próbą, nie warunkiem. Terminy nie zmieniają się co kwadrans, więc robotnik
 * chodzi co sześć godzin, a nie co piętnaście minut jak licznik wywołań.
 *
 * Trąbimy raz dziennie: powiadomienie o tym samym zadaniu wracające co sześć
 * godzin nauczyłoby ludzi je odruchowo zamiatać. Znacznik dnia leży w tych
 * samych preferencjach co znacznik wywołań.
 */
@HiltWorker
class TaskReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val tasks: TaskRepository,
    private val sessionPreferences: SessionPreferences,
    private val notifications: NotificationHelper,
) : CoroutineWorker(context, params) {

    companion object {
        const val UNIQUE_NAME = "task_reminders"
    }

    override suspend fun doWork(): Result {
        val session = sessionPreferences.session.first() ?: return Result.success()

        // Świeże dane, jeśli są; bez sieci lecimy na tym, co w cache.
        runCatching { tasks.refreshTasks() }

        val today = LocalDate.now().toEpochDay()
        if (sessionPreferences.remindersShownOn.first() == today) return Result.success()

        val mine = tasks.observeTasks().first().filter {
            it.assigneeId == session.userId && it.status != TaskStatus.DONE
        }
        val overdue = mine.filter { isOverdue(it.dueAt) }
        val dueToday = mine.filter { isDueToday(it.dueAt) && !isOverdue(it.dueAt) }
        if (overdue.isEmpty() && dueToday.isEmpty()) return Result.success()

        notifications.showTaskReminder(
            title = reminderTitle(overdue.size, dueToday.size),
            text = reminderText(overdue, dueToday),
            // Jedno zadanie → prosto w jego kartę; kilka → w listę zadań.
            taskId = (overdue + dueToday).singleOrNull()?.id,
        )
        sessionPreferences.saveRemindersShownOn(today)
        return Result.success()
    }
}

/** „2 zadania po terminie" / „Masz 3 zadania na dziś" / obie liczby naraz. */
private fun reminderTitle(overdue: Int, today: Int): String = when {
    overdue > 0 && today > 0 -> "$overdue po terminie, $today na dziś"
    overdue > 0 -> "${plural(overdue, "zadanie", "zadania", "zadań")} po terminie"
    else -> "Masz ${plural(today, "zadanie", "zadania", "zadań")} na dziś"
}

/**
 * Treść: tytuły dwóch najpilniejszych zadań, reszta jako „i jeszcze N".
 * Najpierw zaległe — one już kogoś blokują.
 */
private fun reminderText(
    overdue: List<com.ekotak.teamtalk.domain.model.Task>,
    dueToday: List<com.ekotak.teamtalk.domain.model.Task>,
): String {
    val ordered = (overdue.sortedBy { it.dueAt } + dueToday.sortedBy { it.dueAt })
    val shown = ordered.take(2).joinToString(" · ") { it.title }
    val rest = ordered.size - 2
    return if (rest > 0) "$shown i jeszcze $rest" else shown
}

/** Polska odmiana liczebnika: 1 zadanie, 2–4 zadania, 5+ zadań. */
private fun plural(n: Int, one: String, few: String, many: String): String {
    val mod10 = n % 10
    val mod100 = n % 100
    val form = when {
        n == 1 -> one
        mod10 in 2..4 && mod100 !in 12..14 -> few
        else -> many
    }
    return "$n $form"
}
