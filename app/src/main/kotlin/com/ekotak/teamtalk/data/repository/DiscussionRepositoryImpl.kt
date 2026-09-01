package com.ekotak.teamtalk.data.repository

import com.ekotak.teamtalk.data.mapper.toDomain
import com.ekotak.teamtalk.data.remote.api.TeamTalkApi
import com.ekotak.teamtalk.data.remote.dto.AddCommentRequest
import com.ekotak.teamtalk.domain.model.Discussion
import com.ekotak.teamtalk.domain.model.DiscussionThread
import com.ekotak.teamtalk.domain.repository.DiscussionRepository
import javax.inject.Inject

class DiscussionRepositoryImpl @Inject constructor(
    private val api: TeamTalkApi,
) : DiscussionRepository {

    override suspend fun listDiscussions(): List<Discussion> =
        api.getDiscussions().map { it.toDomain() }

    override suspend fun unreadCount(): Int = api.getDiscussionsUnreadCount().count

    override suspend fun getThread(taskId: String): DiscussionThread =
        api.getDiscussionThread(taskId).toDomain()

    override suspend fun markRead(taskId: String) = api.markDiscussionRead(taskId)

    override suspend fun reply(taskId: String, body: String, mentions: List<String>) {
        api.addDiscussionComment(taskId, AddCommentRequest(body = body, mentions = mentions))
    }
}
