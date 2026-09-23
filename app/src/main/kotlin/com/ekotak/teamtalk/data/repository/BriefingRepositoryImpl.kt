package com.ekotak.teamtalk.data.repository

import com.ekotak.teamtalk.data.remote.api.TeamTalkApi
import com.ekotak.teamtalk.data.remote.dto.BriefingInboxDto
import com.ekotak.teamtalk.domain.model.BriefingItem
import com.ekotak.teamtalk.domain.repository.BriefingRepository
import javax.inject.Inject
import javax.inject.Singleton

/** Adapter skrzynki odprawy — jedno pobranie na wejście, bez pamięci podręcznej. */
@Singleton
class BriefingRepositoryImpl @Inject constructor(
    private val api: TeamTalkApi,
) : BriefingRepository {

    override suspend fun inbox(): List<BriefingItem> = api.getBriefingInbox().map { it.toDomain() }

    override suspend fun unreadCount(): Int = api.getBriefingUnreadCount().count

    override suspend fun ack(id: String) {
        api.ackBriefing(id)
    }
}

private fun BriefingInboxDto.toDomain() = BriefingItem(
    id = id,
    title = title,
    body = body,
    priority = priority,
    kind = kind,
    link = link,
    requiresAck = requiresAck,
    authorName = authorName,
    publishedAt = publishedAt,
    expiresAt = expiresAt,
    ackAt = ackAt,
)
