package com.ekotak.teamtalk.domain.repository

import com.ekotak.teamtalk.domain.model.Task
import com.ekotak.teamtalk.domain.model.TaskMember
import com.ekotak.teamtalk.domain.model.TaskPriority

interface TaskRepository {
    /** Lista członków zespołu (do wyboru osoby przypisanej). */
    suspend fun getMembers(): List<TaskMember>

    /** Tworzy zadanie zespołu (board360). */
    suspend fun createTask(
        title: String,
        description: String? = null,
        assigneeId: String? = null,
        dueAt: String? = null,
        priority: TaskPriority = TaskPriority.NORMAL,
    ): Task
}
