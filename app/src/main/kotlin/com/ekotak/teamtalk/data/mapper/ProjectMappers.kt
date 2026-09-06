package com.ekotak.teamtalk.data.mapper

import com.ekotak.teamtalk.data.local.entity.ProjectEntity
import com.ekotak.teamtalk.data.local.entity.ProjectMilestoneEntity
import com.ekotak.teamtalk.data.local.entity.ProjectTaskEntity
import com.ekotak.teamtalk.data.remote.dto.ProjectDetailDto
import com.ekotak.teamtalk.data.remote.dto.ProjectDto
import com.ekotak.teamtalk.data.remote.dto.ProjectMemberDto
import com.ekotak.teamtalk.data.remote.dto.ProjectMilestoneDto
import com.ekotak.teamtalk.data.remote.dto.ProjectTaskDto
import com.ekotak.teamtalk.domain.model.Project
import com.ekotak.teamtalk.domain.model.ProjectMember
import com.ekotak.teamtalk.domain.model.ProjectMilestone
import com.ekotak.teamtalk.domain.model.ProjectStage
import com.ekotak.teamtalk.domain.model.ProjectTask

/** DTO → encja cache. `localOnly` zostaje przy false: to przyszło z serwera. */
fun ProjectDto.toEntity(cachedAt: Long): ProjectEntity = ProjectEntity(
    id = id,
    name = name,
    description = description,
    color = color,
    stage = stage ?: "active",
    department = department,
    managerEmail = managerEmail,
    sponsorEmail = sponsorEmail,
    memberCount = memberCount ?: 0,
    taskCount = taskCount ?: 0,
    doneCount = doneCount ?: 0,
    dueAt = dueAt ?: metricDueAt,
    problemStatement = problemStatement,
    metricName = metricName,
    metricBaseline = metricBaseline,
    metricTarget = metricTarget,
    membersJson = null,
    localOnly = false,
    cachedAt = cachedAt,
)

/**
 * Karta projektu → encja. Liczniki zadań bierzemy z listy zadań w karcie, bo
 * szczegół ich nie niesie osobno, a pasek postępu ma pokazywać to samo co kafel.
 */
fun ProjectDetailDto.toEntity(cachedAt: Long): ProjectEntity = ProjectEntity(
    id = id,
    name = name,
    description = description,
    color = color,
    stage = stage ?: "active",
    department = department,
    managerEmail = managerEmail,
    sponsorEmail = sponsorEmail,
    memberCount = members.size,
    taskCount = tasks.size,
    doneCount = tasks.count { it.status == "done" },
    dueAt = dueAt,
    problemStatement = problemStatement,
    metricName = metricName,
    metricBaseline = metricBaseline,
    metricTarget = metricTarget,
    membersJson = null,
    localOnly = false,
    cachedAt = cachedAt,
)

fun ProjectMilestoneDto.toEntity(): ProjectMilestoneEntity = ProjectMilestoneEntity(
    id = id,
    projectId = projectId,
    name = name,
    dueAt = dueAt,
    acceptanceCriteria = acceptanceCriteria,
    ownerEmail = ownerEmail,
    doneAt = doneAt,
    position = position,
    taskCount = taskCount ?: 0,
    doneCount = doneCount ?: 0,
)

fun ProjectTaskDto.toEntity(fallbackProjectId: String): ProjectTaskEntity = ProjectTaskEntity(
    id = id,
    projectId = projectId ?: fallbackProjectId,
    title = title,
    assigneeId = assigneeId,
    assigneeEmail = assigneeEmail,
    startAt = startAt,
    dueAt = dueAt,
    status = status ?: "open",
    lifecycle = lifecycle ?: "active",
    milestoneId = milestoneId,
    estimatedMinutes = estimatedMinutes,
    actualMinutes = actualMinutes,
)

fun ProjectEntity.toDomain(): Project = Project(
    id = id,
    name = name,
    description = description,
    color = color,
    stage = ProjectStage.from(stage),
    department = department,
    managerEmail = managerEmail,
    sponsorEmail = sponsorEmail,
    memberCount = memberCount,
    taskCount = taskCount,
    doneCount = doneCount,
    dueAt = dueAt,
    problemStatement = problemStatement,
    metricName = metricName,
    metricBaseline = metricBaseline,
    metricTarget = metricTarget,
    localOnly = localOnly,
)

fun ProjectMilestoneEntity.toDomain(): ProjectMilestone = ProjectMilestone(
    id = id,
    projectId = projectId,
    name = name,
    dueAt = dueAt,
    acceptanceCriteria = acceptanceCriteria,
    ownerEmail = ownerEmail,
    doneAt = doneAt,
    position = position,
    taskCount = taskCount,
    doneCount = doneCount,
)

fun ProjectTaskEntity.toDomain(): ProjectTask = ProjectTask(
    id = id,
    projectId = projectId,
    title = title,
    assigneeId = assigneeId,
    assigneeEmail = assigneeEmail,
    startAt = startAt,
    dueAt = dueAt,
    status = status,
    lifecycle = lifecycle,
    milestoneId = milestoneId,
    estimatedMinutes = estimatedMinutes,
    actualMinutes = actualMinutes,
)

fun ProjectMemberDto.toDomain(): ProjectMember = ProjectMember(
    userId = userId,
    email = email,
    firstName = firstName,
    lastName = lastName,
    role = role,
)
