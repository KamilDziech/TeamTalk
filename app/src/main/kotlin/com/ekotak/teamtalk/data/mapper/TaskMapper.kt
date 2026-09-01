package com.ekotak.teamtalk.data.mapper

import com.ekotak.teamtalk.data.local.entity.TaskEntity
import com.ekotak.teamtalk.data.remote.dto.ProjectDto
import com.ekotak.teamtalk.data.remote.dto.TaskMemberDto
import com.ekotak.teamtalk.data.remote.dto.TaskResponseDto
import com.ekotak.teamtalk.domain.model.Task
import com.ekotak.teamtalk.domain.model.TaskMember
import com.ekotak.teamtalk.domain.model.TaskPriority
import com.ekotak.teamtalk.domain.model.TaskProject
import com.ekotak.teamtalk.domain.model.TaskSection
import com.ekotak.teamtalk.domain.model.TaskSource
import com.ekotak.teamtalk.domain.model.TaskStatus

/**
 * Źródło zadania: deal ma pierwszeństwo przed projektem, bo zadanie spod karty
 * klienta niesie oba pola (projekt jest wtedy tylko nazwą projektu deala), a na
 * wierszu listy chcemy widzieć klienta.
 */
private fun sourceOf(
    dealId: String?,
    dealName: String?,
    projectId: String?,
    projectName: String?,
): TaskSource? = when {
    dealId != null -> TaskSource.Deal(dealId, dealName)
    projectId != null -> TaskSource.Project(projectId, projectName)
    else -> null
}

fun TaskResponseDto.toDomain(): Task = Task(
    id               = id,
    title            = title,
    description      = description,
    assigneeId       = assigneeId,
    assigneeEmail    = assigneeEmail,
    dueAt            = dueAt,
    status           = TaskStatus.fromWire(status),
    priority         = TaskPriority.fromWire(priority),
    section          = TaskSection.fromWire(section),
    estimatedMinutes = estimatedMinutes,
    slaHours         = slaHours,
    commentCount     = commentCount,
    createdBy        = createdBy,
    createdAt        = createdAt ?: "",
    updatedAt        = updatedAt,
    source           = sourceOf(dealId, dealName, projectId, projectName),
)

fun TaskResponseDto.toEntity(): TaskEntity = TaskEntity(
    id               = id,
    title            = title,
    description      = description,
    assigneeId       = assigneeId,
    assigneeEmail    = assigneeEmail,
    dueAt            = dueAt,
    status           = status ?: TaskStatus.OPEN.wire,
    priority         = priority ?: TaskPriority.NORMAL.wire,
    section          = section,
    estimatedMinutes = estimatedMinutes,
    slaHours         = slaHours,
    commentCount     = commentCount,
    createdBy        = createdBy,
    createdAt        = createdAt ?: "",
    updatedAt        = updatedAt,
    dealId           = dealId,
    dealName         = dealName,
    projectId        = projectId,
    projectName      = projectName,
)

fun TaskEntity.toDomain(): Task = Task(
    id               = id,
    title            = title,
    description      = description,
    assigneeId       = assigneeId,
    assigneeEmail    = assigneeEmail,
    dueAt            = dueAt,
    status           = TaskStatus.fromWire(status),
    priority         = TaskPriority.fromWire(priority),
    section          = TaskSection.fromWire(section),
    estimatedMinutes = estimatedMinutes,
    slaHours         = slaHours,
    commentCount     = commentCount,
    createdBy        = createdBy,
    createdAt        = createdAt,
    updatedAt        = updatedAt,
    source           = sourceOf(dealId, dealName, projectId, projectName),
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
