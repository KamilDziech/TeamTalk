package com.ekotak.teamtalk.data.repository

import com.ekotak.teamtalk.data.mapper.toDomain
import com.ekotak.teamtalk.data.remote.api.TeamTalkApi
import com.ekotak.teamtalk.data.remote.dto.CreateTaskRequest
import com.ekotak.teamtalk.domain.model.Task
import com.ekotak.teamtalk.domain.model.TaskMember
import com.ekotak.teamtalk.domain.model.TaskPriority
import com.ekotak.teamtalk.domain.repository.TaskRepository
import javax.inject.Inject

class TaskRepositoryImpl @Inject constructor(
    private val api: TeamTalkApi,
) : TaskRepository {

    override suspend fun getMembers(): List<TaskMember> =
        api.getTaskMembers().map { it.toDomain() }

    override suspend fun createTask(
        title: String,
        description: String?,
        assigneeId: String?,
        dueAt: String?,
        priority: TaskPriority,
    ): Task {
        val dto = api.createTask(
            CreateTaskRequest(
                title = title,
                description = description,
                assigneeId = assigneeId,
                dueAt = dueAt,
                priority = priority.wire,
            )
        )
        return dto.toDomain()
    }
}
