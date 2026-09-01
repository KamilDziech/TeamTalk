package com.ekotak.teamtalk.domain.usecase.task

import com.ekotak.teamtalk.domain.model.Edit
import com.ekotak.teamtalk.domain.model.Task
import com.ekotak.teamtalk.domain.model.TaskPatch
import com.ekotak.teamtalk.domain.model.TaskPriority
import com.ekotak.teamtalk.domain.model.TaskStatus
import com.ekotak.teamtalk.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Strumień zadań zespołu (cache Room + odświeżenie z board360). */
class ObserveTasksUseCase @Inject constructor(
    private val taskRepository: TaskRepository,
) {
    operator fun invoke(): Flow<List<Task>> = taskRepository.observeTasks()
}

/** Zadania z niewysłaną zmianą — znacznik „czeka na wysyłkę" na wierszu. */
class ObservePendingTaskIdsUseCase @Inject constructor(
    private val taskRepository: TaskRepository,
) {
    operator fun invoke(): Flow<Set<String>> = taskRepository.observePendingTaskIds()
}

/** Zespół do filtra osoby i do podpisów „kto wykonuje" na wierszach listy. */
class GetTaskMembersUseCase @Inject constructor(
    private val taskRepository: TaskRepository,
) {
    suspend operator fun invoke(): List<com.ekotak.teamtalk.domain.model.TaskMember> =
        taskRepository.getMembers()
}

/** Ponowne pobranie listy — gest „pociągnij, by odświeżyć". */
class RefreshTasksUseCase @Inject constructor(
    private val taskRepository: TaskRepository,
) {
    suspend operator fun invoke() = taskRepository.refreshTasks()
}

/** Zmiana dowolnych pól zadania (`PATCH /api/tasks/:id`). */
class UpdateTaskUseCase @Inject constructor(
    private val taskRepository: TaskRepository,
) {
    suspend operator fun invoke(id: String, patch: TaskPatch): Task =
        taskRepository.updateTask(id, patch)
}

/**
 * Odhaczenie i cofnięcie odhaczenia — najczęstsza akcja na liście, więc ma
 * własną nazwę zamiast gołego patcha ze statusem w ekranie.
 */
class SetTaskDoneUseCase @Inject constructor(
    private val taskRepository: TaskRepository,
) {
    suspend operator fun invoke(id: String, done: Boolean): Task =
        taskRepository.updateTask(
            id,
            TaskPatch(status = Edit(if (done) TaskStatus.DONE else TaskStatus.OPEN)),
        )
}

/** Przełączenie wysokiego priorytetu (gwiazdka na wierszu, jak w panelu). */
class ToggleTaskPriorityUseCase @Inject constructor(
    private val taskRepository: TaskRepository,
) {
    suspend operator fun invoke(id: String, high: Boolean): Task =
        taskRepository.updateTask(
            id,
            TaskPatch(priority = Edit(if (high) TaskPriority.HIGH else TaskPriority.NORMAL)),
        )
}
