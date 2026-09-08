package com.ekotak.teamtalk.domain.repository

import com.ekotak.teamtalk.domain.model.Task
import com.ekotak.teamtalk.domain.model.TaskAttachment
import com.ekotak.teamtalk.domain.model.TaskComment
import com.ekotak.teamtalk.domain.model.TaskLink
import com.ekotak.teamtalk.domain.model.TaskMember
import com.ekotak.teamtalk.domain.model.TaskPatch
import com.ekotak.teamtalk.domain.model.TaskPriority
import com.ekotak.teamtalk.domain.model.TaskProject
import com.ekotak.teamtalk.domain.model.TaskSection
import kotlinx.coroutines.flow.Flow
import java.io.File

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
     * Zadania jednego deala z cache — zakładka „Zadania" karty klienta. Wycinek
     * czytamy po `dealId`, więc pokazuje też zadania innych osób i te założone
     * w terenie, których serwer jeszcze nie widział.
     */
    fun observeDealTasks(dealId: String): Flow<List<Task>>

    /**
     * Odświeżenie wycinka jednego deala (`GET /api/deals/:id/tasks`). Zwraca
     * `false`, gdy serwer był nieosiągalny — zakładka pisze wtedy wprost, że
     * pokazuje ostatnią kopię, zamiast udawać świeże dane. Odmowa serwera leci
     * dalej wyjątkiem.
     */
    suspend fun refreshDealTasks(dealId: String): Boolean

    /**
     * Ręczna kolejność zadań z preferencji `tasks.order` — ta sama, którą
     * układa się myszą w panelu. Brak zapisanej kolejności albo brak zasięgu
     * daje pustą listę: wtedy zostaje kolejność domyślna (najnowsze u góry).
     */
    suspend fun getTasksOrder(): List<String>

    /** Zapis ręcznej kolejności. Wymaga sieci — bez niej kolejność zostaje lokalna. */
    suspend fun saveTasksOrder(ids: List<String>)

    /**
     * Jedno zadanie (`GET /api/tasks/:id`) z odświeżeniem cache. Karta otwiera
     * się też z powiadomienia i z dyskusji, więc nie zawsze jest w cache.
     */
    suspend fun getTask(id: String): Task

    /** Komentarze karty zadania — to zarazem wątek dyskusji w Komunikatorze. */
    suspend fun getComments(taskId: String): List<TaskComment>

    /** Załączniki karty zadania (metadane; treść pobiera się osobno). */
    suspend fun getAttachments(taskId: String): List<TaskAttachment>

    /**
     * Wgranie pliku. Bajty czyta warstwa prezentacji (to ona ma `ContentResolver`
     * i wie, co użytkownik wybrał) — repozytorium dostaje gotową zawartość.
     */
    suspend fun uploadAttachment(
        taskId: String,
        name: String,
        contentType: String,
        bytes: ByteArray,
    ): TaskAttachment

    /** Pobranie treści do wskazanego pliku — strumieniem, bez trzymania w RAM. */
    suspend fun downloadAttachmentTo(id: String, target: File)

    suspend fun deleteAttachment(id: String)

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
     * powiązania, pod dealem klienta albo w projekcie. [section] wysyła tylko
     * karta deala — tam sekcja wyprowadza się z etapu lejka.
     *
     * Bez zasięgu zadanie dostaje identyfikator lokalny, ląduje w cache i czeka
     * w kolejce (`__create`); wynik ma wtedy `id` z prefiksem `local:`.
     */
    suspend fun createTask(
        title: String,
        description: String? = null,
        assigneeId: String? = null,
        dueAt: String? = null,
        priority: TaskPriority = TaskPriority.NORMAL,
        link: TaskLink = TaskLink.None,
        section: TaskSection? = null,
    ): Task
}
