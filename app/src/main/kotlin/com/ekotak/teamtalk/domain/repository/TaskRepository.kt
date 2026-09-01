package com.ekotak.teamtalk.domain.repository

import com.ekotak.teamtalk.domain.model.Task
import com.ekotak.teamtalk.domain.model.TaskLink
import com.ekotak.teamtalk.domain.model.TaskMember
import com.ekotak.teamtalk.domain.model.TaskPriority
import com.ekotak.teamtalk.domain.model.TaskProject

interface TaskRepository {
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
