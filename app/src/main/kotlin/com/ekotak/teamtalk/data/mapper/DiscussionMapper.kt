package com.ekotak.teamtalk.data.mapper

import com.ekotak.teamtalk.data.remote.dto.DiscussionCommentDto
import com.ekotak.teamtalk.data.remote.dto.DiscussionSummaryDto
import com.ekotak.teamtalk.data.remote.dto.DiscussionThreadDto
import com.ekotak.teamtalk.data.remote.dto.TaskCommentDto
import com.ekotak.teamtalk.domain.model.Discussion
import com.ekotak.teamtalk.domain.model.DiscussionThread
import com.ekotak.teamtalk.domain.model.TaskComment

/**
 * Komentarz z karty zadania. Backend nie mówi tu, czy komentarz jest nasz —
 * porównujemy autora z id zalogowanego (w Komunikatorze robi to serwer).
 */
fun TaskCommentDto.toDomain(currentUserId: String?): TaskComment = TaskComment(
    id         = id,
    authorId   = authorId,
    authorName = listOfNotNull(authorFirstName, authorLastName)
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .ifBlank { authorEmail ?: "—" },
    body       = body,
    createdAt  = createdAt,
    mine       = currentUserId != null && authorId == currentUserId,
)

fun DiscussionCommentDto.toDomain(): TaskComment = TaskComment(
    id         = id,
    authorId   = authorId,
    authorName = authorName,
    body       = body,
    createdAt  = createdAt,
    mine       = mine,
)

fun DiscussionSummaryDto.toDomain(): Discussion = Discussion(
    taskId       = taskId,
    taskTitle    = taskTitle,
    title        = title,
    clientName   = clientName,
    dealId       = dealId,
    dealCode     = dealCode,
    projectName  = projectName,
    lastComment  = lastComment?.toDomain(),
    commentCount = commentCount,
    unreadCount  = unreadCount,
    mentionedMe  = mentionedMe,
)

fun DiscussionThreadDto.toDomain(): DiscussionThread = DiscussionThread(
    taskId      = taskId,
    taskTitle   = taskTitle,
    title       = title,
    clientName  = clientName,
    dealId      = dealId,
    projectName = projectName,
    comments    = comments.map { it.toDomain() },
)
