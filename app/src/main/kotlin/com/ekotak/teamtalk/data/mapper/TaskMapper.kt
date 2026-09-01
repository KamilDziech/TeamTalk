package com.ekotak.teamtalk.data.mapper

import com.ekotak.teamtalk.data.remote.dto.ProjectDto
import com.ekotak.teamtalk.data.remote.dto.TaskMemberDto
import com.ekotak.teamtalk.data.remote.dto.TaskResponseDto
import com.ekotak.teamtalk.domain.model.Task
import com.ekotak.teamtalk.domain.model.TaskMember
import com.ekotak.teamtalk.domain.model.TaskPriority
import com.ekotak.teamtalk.domain.model.TaskProject

fun TaskResponseDto.toDomain(): Task = Task(
    id            = id,
    title         = title,
    description   = description,
    assigneeId    = assigneeId,
    assigneeEmail = assigneeEmail,
    dueAt         = dueAt,
    status        = status ?: "open",
    priority      = TaskPriority.fromWire(priority),
    createdAt     = createdAt ?: "",
)

fun TaskMemberDto.toDomain(): TaskMember = TaskMember(
    id        = id,
    email     = email,
    firstName = firstName,
    lastName  = lastName,
    role      = role,
    additionalRoles = additionalRoles,
    functions = functions,
)

fun ProjectDto.toDomain(): TaskProject = TaskProject(
    id        = id,
    name      = name,
    taskCount = taskCount,
)
