package com.ekotak.teamtalk.domain.repository

import com.ekotak.teamtalk.domain.model.Task
import com.ekotak.teamtalk.domain.model.TaskComment
import com.ekotak.teamtalk.domain.model.TaskLink
import com.ekotak.teamtalk.domain.model.TaskMember
import com.ekotak.teamtalk.domain.model.TaskPatch
import com.ekotak.teamtalk.domain.model.TaskPriority
import com.ekotak.teamtalk.domain.model.TaskProject
import kotlinx.coroutines.flow.Flow

interface TaskRepository {
    /**
     * Zadania zespołu z cache Room — strumień rusza od danych lokalnych, więc
     * lista pokazuje się bez zasięgu, a odświeżenie z sieci dochodzi po chwili.
     */
    fun observeTasks(): Flow<List<Task>>

    /** Pobranie z board360 i podmiana cache. Błąd leci dalej (pull-to-refresh). */
    suspend fun refreshTasks()

    /**
     * Jedno zadanie (`GET /api/tasks/:id`) z odświeżeniem cache. Karta otwiera
     * się też z powiadomienia i z dyskusji, więc nie zawsze jest w cache.
     */
    suspend fun getTask(id: String): Task

    /** Komentarze karty zadania — to zarazem wątek dyskusji w Komunikatorze. */
    suspend fun getComments(taskId: String): List<TaskComment>

    /**
     * Nowy komentarz. [mentions] to tokeny wywołań („user:<id>", „role:<rola>",
     * „watchers", „all") — backend rozwija je do osób i wciąga zadanie do ich
     * skrzynek w Komunikatorze.
     */
    suspend fun addComment(taskId: String, body: String, mentions: List<String>): TaskComment

    /**
     * Zmiana pól zadania (`PATCH /api/tasks/:id`). Wymaga sieci — kolejka zmian
     * zrobionych offline wchodzi dopiero w E3.
     */
    suspend fun updateTask(id: String, patch: TaskPatch): Task

    /** Lista członków zespołu (do wyboru osoby przypisanej). */
    suspend fun getMembers(): List<TaskMember>

    /** Aktywne projekty — krok „kogo dotyczy" w kreatorze zadania. */
    suspend fun getProjects(): List<TaskProject>

    /**
     * Tworzy zadanie zespołu (board360). [link] decyduje o endpoincie: bez
     * powiązania, pod dealem klienta albo w projekcie.
     */
    suspend fun createTask(
        title: String,
        description: String? = null,
        assigneeId: String? = null,
        dueAt: String? = null,
        priority: TaskPriority = TaskPriority.NORMAL,
        link: TaskLink = TaskLink.None,
    ): Task
}
