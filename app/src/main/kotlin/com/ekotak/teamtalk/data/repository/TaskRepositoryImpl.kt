package com.ekotak.teamtalk.data.repository

import com.ekotak.teamtalk.data.local.dao.TaskDao
import com.ekotak.teamtalk.data.local.dao.TaskMutationDao
import com.ekotak.teamtalk.data.local.entity.TaskMutationEntity
import com.ekotak.teamtalk.data.local.preferences.SessionPreferences
import com.ekotak.teamtalk.data.mapper.applyPatch
import com.ekotak.teamtalk.data.mapper.toDomain
import com.ekotak.teamtalk.data.mapper.toEntity
import com.ekotak.teamtalk.data.remote.api.TeamTalkApi
import com.ekotak.teamtalk.data.remote.dto.AddCommentRequest
import com.ekotak.teamtalk.data.remote.dto.CreateTaskRequest
import com.ekotak.teamtalk.data.remote.dto.buildTaskPatch
import com.ekotak.teamtalk.data.sync.TaskSyncScheduler
import com.ekotak.teamtalk.domain.model.Task
import com.ekotak.teamtalk.domain.model.TaskComment
import com.ekotak.teamtalk.domain.model.TaskLink
import com.ekotak.teamtalk.domain.model.TaskMember
import com.ekotak.teamtalk.domain.model.TaskPatch
import com.ekotak.teamtalk.domain.model.TaskPriority
import com.ekotak.teamtalk.domain.model.TaskProject
import com.ekotak.teamtalk.domain.repository.TaskRepository
import com.ekotak.teamtalk.domain.repository.TaskSyncResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

class TaskRepositoryImpl @Inject constructor(
    private val api: TeamTalkApi,
    private val taskDao: TaskDao,
    private val mutationDao: TaskMutationDao,
    private val sessionPreferences: SessionPreferences,
    private val syncScheduler: TaskSyncScheduler,
) : TaskRepository {

    /** Do odczytu zakolejkowanych fragmentów ciała żądania. */
    private val json = Json

    /**
     * Czysty strumień z cache — bez dociągania w tle. Pobranie z sieci zleca
     * ekran przez [refreshTasks], dzięki czemu wie, kiedy pokazać kręciołek
     * i kiedy powiedzieć o awarii, zamiast zgadywać po pustej liście.
     */
    override fun observeTasks(): Flow<List<Task>> =
        taskDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    /** Błąd świadomie leci dalej — pull-to-refresh musi umieć pokazać awarię. */
    override suspend fun refreshTasks() {
        taskDao.replaceAll(api.getTasks().map { it.toEntity() })
    }

    /** Świeże zadanie z serwera; cache aktualizujemy przy okazji. */
    override suspend fun getTask(id: String): Task {
        val dto = api.getTask(id)
        taskDao.upsert(dto.toEntity())
        return dto.toDomain()
    }

    override suspend fun getComments(taskId: String): List<TaskComment> {
        val me = sessionPreferences.session.first()?.userId
        return api.getTaskComments(taskId).map { it.toDomain(me) }
    }

    override suspend fun addComment(
        taskId: String,
        body: String,
        mentions: List<String>,
    ): TaskComment {
        val me = sessionPreferences.session.first()?.userId
        return api.addTaskComment(taskId, AddCommentRequest(body = body, mentions = mentions))
            .toDomain(me)
    }

    /**
     * Zmiana idzie wprost na serwer, a gdy sieci nie ma — do kolejki i do
     * cache, żeby człowiek zobaczył swoją decyzję od razu. Rozróżnienie jest
     * tu istotne: brak łączności (`IOException`) da się nadrobić później,
     * odmowa serwera (`HttpException`: 403, 404, 422) nie — taką zmianę
     * puszczamy dalej jako błąd, zamiast wozić ją w kółko po kolejce.
     */
    override suspend fun updateTask(id: String, patch: TaskPatch): Task {
        val body = buildTaskPatch(patch)
        return try {
            val dto = api.updateTask(id, body)
            // Pola, które właśnie poszły, nie mają po co czekać w kolejce.
            mutationDao.delete(id, body.keys.toList())
            taskDao.upsert(dto.toEntity())
            dto.toDomain()
        } catch (e: IOException) {
            enqueue(id, body, patch) ?: throw e
        }
    }

    /**
     * Kolejkuje zmianę i nakłada ją na cache. Zwraca `null`, gdy zadania nie ma
     * lokalnie — wtedy nie ma czego pokazać ani do czego wrócić, więc niech
     * zawoła o tym pierwotny błąd sieci.
     */
    private suspend fun enqueue(id: String, body: JsonObject, patch: TaskPatch): Task? {
        val cached = taskDao.getById(id) ?: return null
        val now = System.currentTimeMillis()
        mutationDao.upsertAll(
            body.map { (field, value) ->
                TaskMutationEntity(
                    taskId = id,
                    field = field,
                    payload = JsonObject(mapOf(field to value)).toString(),
                    createdAt = now,
                )
            },
        )
        val local = cached.applyPatch(patch)
        taskDao.upsert(local)
        syncScheduler.scheduleSync()
        return local.toDomain()
    }

    override fun observePendingTaskIds(): Flow<Set<String>> =
        mutationDao.observePendingTaskIds().map { it.toSet() }

    /**
     * Opróżnianie kolejki. Zmiany jednego zadania scalamy w jedno żądanie —
     * pola i tak są rozłączne, a serwer nie musi oglądać trzech okrążeń.
     * Kolejność ma znaczenie tylko między zadaniami, więc idziemy od najstarszej.
     */
    override suspend fun syncPendingMutations(): TaskSyncResult {
        val pending = mutationDao.getAll()
        if (pending.isEmpty()) return TaskSyncResult.DONE

        var networkFailed = false
        for ((taskId, rows) in pending.groupBy { it.taskId }) {
            val body = buildJsonObject {
                rows.forEach { row ->
                    (json.parseToJsonElement(row.payload) as? JsonObject)
                        ?.forEach { (key, value) -> put(key, value) }
                }
            }
            try {
                val dto = api.updateTask(taskId, body)
                mutationDao.delete(taskId, rows.map { it.field })
                taskDao.upsert(dto.toEntity())
            } catch (_: IOException) {
                // Sieć znowu padła — reszta kolejki poczeka na następny przebieg.
                networkFailed = true
            } catch (e: HttpException) {
                // Serwer odrzucił zmianę: 404 = zadania już nie ma, 403/422 =
                // zmiana nie do przyjęcia. Ponowienie nic nie zmieni, więc
                // porzucamy wpis i zostawiamy prawdę serwera — ale mówimy o tym
                // człowiekowi, bo to jego decyzja przepadła.
                mutationDao.delete(taskId, rows.map { it.field })
                val title = taskDao.getById(taskId)?.title
                if (e.code() == 404) taskDao.deleteById(taskId)
                sessionPreferences.saveSyncProblem(discardMessage(title, e.code()))
            }
        }
        return if (networkFailed) TaskSyncResult.RETRY else TaskSyncResult.DONE
    }

    private fun discardMessage(taskTitle: String?, code: Int): String {
        val what = if (taskTitle != null) "zadania „$taskTitle”" else "zadania"
        return when (code) {
            404 -> "Zmiana $what przepadła — zadanie zniknęło z panelu."
            403 -> "Zmiana $what przepadła — brak uprawnień."
            else -> "Zmiana $what przepadła — serwer ją odrzucił (kod $code)."
        }
    }

    override suspend fun getMembers(): List<TaskMember> =
        api.getTaskMembers().map { it.toDomain() }

    override suspend fun getProjects(): List<TaskProject> =
        api.getProjects().map { it.toDomain() }

    override suspend fun createTask(
        title: String,
        description: String?,
        assigneeId: String?,
        dueAt: String?,
        priority: TaskPriority,
        link: TaskLink,
    ): Task {
        val request = CreateTaskRequest(
            title = title,
            description = description,
            assigneeId = assigneeId,
            dueAt = dueAt,
            priority = priority.wire,
        )
        // Ciało jest identyczne dla wszystkich trzech ścieżek — różni je adres.
        val dto = when (link) {
            is TaskLink.None -> api.createTask(request)
            is TaskLink.Deal -> api.createDealTask(link.dealId, request)
            is TaskLink.Project -> api.createProjectTask(link.projectId, request)
        }
        // Nowe zadanie ląduje w cache od razu — lista pokaże je bez odświeżania.
        taskDao.upsert(dto.toEntity())
        return dto.toDomain()
    }
}
