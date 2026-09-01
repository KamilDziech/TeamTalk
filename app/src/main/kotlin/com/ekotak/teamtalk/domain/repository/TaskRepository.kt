package com.ekotak.teamtalk.domain.repository

import com.ekotak.teamtalk.domain.model.Task
import com.ekotak.teamtalk.domain.model.TaskComment
import com.ekotak.teamtalk.domain.model.TaskLink
import com.ekotak.teamtalk.domain.model.TaskMember
import com.ekotak.teamtalk.domain.model.TaskPatch
import com.ekotak.teamtalk.domain.model.TaskPriority
import com.ekotak.teamtalk.domain.model.TaskProject
import kotlinx.coroutines.flow.Flow

/** Czy kolejka zmian opróżniła się do końca, czy trzeba wrócić po sieci. */
enum class TaskSyncResult { DONE, RETRY }

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
     * Zmiana pól zadania (`PATCH /api/tasks/:id`). Bez zasięgu zmiana ląduje
     * w kolejce i w cache, więc zwrócone zadanie jest wersją lokalną —
     * serwer zobaczy ją, gdy telefon wróci w zasięg. Odmowa serwera (4xx)
     * leci dalej jako wyjątek: kolejkowanie jej nie naprawi.
     */
    suspend fun updateTask(id: String, patch: TaskPatch): Task

    /**
     * Usunięcie zadania (menu karty). Wymaga sieci — kolejka offline wozi
     * zmiany pól, a nie usunięcia: cofnąć się z niego nie da, więc lepiej
     * powiedzieć wprost „bez zasięgu nie usuniemy" niż obiecywać na później.
     */
    suspend fun deleteTask(id: String)

    /** Zadania z niewysłaną zmianą — znacznik „czeka na wysyłkę" na liście. */
    fun observePendingTaskIds(): Flow<Set<String>>

    /** Wysyła kolejkę zmian; woła ją robotnik synchronizacji po powrocie sieci. */
    suspend fun syncPendingMutations(): TaskSyncResult

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
