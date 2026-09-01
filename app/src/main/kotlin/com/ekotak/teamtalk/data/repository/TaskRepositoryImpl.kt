package com.ekotak.teamtalk.data.repository

import com.ekotak.teamtalk.data.local.dao.TaskDao
import com.ekotak.teamtalk.data.local.preferences.SessionPreferences
import com.ekotak.teamtalk.data.mapper.toDomain
import com.ekotak.teamtalk.data.mapper.toEntity
import com.ekotak.teamtalk.data.remote.api.TeamTalkApi
import com.ekotak.teamtalk.data.remote.dto.AddCommentRequest
import com.ekotak.teamtalk.data.remote.dto.CreateTaskRequest
import com.ekotak.teamtalk.data.remote.dto.buildTaskPatch
import com.ekotak.teamtalk.domain.model.Task
import com.ekotak.teamtalk.domain.model.TaskComment
import com.ekotak.teamtalk.domain.model.TaskLink
import com.ekotak.teamtalk.domain.model.TaskMember
import com.ekotak.teamtalk.domain.model.TaskPatch
import com.ekotak.teamtalk.domain.model.TaskPriority
import com.ekotak.teamtalk.domain.model.TaskProject
import com.ekotak.teamtalk.domain.repository.TaskRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class TaskRepositoryImpl @Inject constructor(
    private val api: TeamTalkApi,
    private val taskDao: TaskDao,
    private val sessionPreferences: SessionPreferences,
) : TaskRepository {

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

    override suspend fun updateTask(id: String, patch: TaskPatch): Task {
        val dto = api.updateTask(id, buildTaskPatch(patch))
        // Zapis do cache dopiero po odpowiedzi serwera: dopóki nie ma kolejki
        // offline (E3), lokalna zmiana bez potwierdzenia byłaby kłamstwem.
        taskDao.upsert(dto.toEntity())
        return dto.toDomain()
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
