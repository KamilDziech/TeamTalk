package com.ekotak.teamtalk.data.repository

import com.ekotak.teamtalk.data.remote.api.TeamTalkApi
import com.ekotak.teamtalk.data.remote.dto.AssistantActionRequest
import com.ekotak.teamtalk.data.remote.dto.AssistantChatRequest
import com.ekotak.teamtalk.data.remote.dto.AssistantMessageDto
import com.ekotak.teamtalk.domain.model.AssistantAction
import com.ekotak.teamtalk.domain.model.AssistantAnswer
import com.ekotak.teamtalk.domain.model.AssistantMessage
import com.ekotak.teamtalk.domain.repository.AssistantRepository
import javax.inject.Inject
import javax.inject.Singleton

/** Warstwa danych asystenta — cienki most na trasy `POST /api/assistant/…`. */
@Singleton
class AssistantRepositoryImpl @Inject constructor(
    private val api: TeamTalkApi,
) : AssistantRepository {

    override suspend fun ask(messages: List<AssistantMessage>): AssistantAnswer {
        val reply = api.askAssistant(
            AssistantChatRequest(
                messages = messages.map { AssistantMessageDto(role = it.role, content = it.content) },
            ),
        )
        return AssistantAnswer(
            text = reply.text,
            actions = reply.actions.map {
                AssistantAction(type = it.type, label = it.label, args = it.args)
            },
            configured = reply.configured,
            usedTools = reply.usedTools,
        )
    }

    override suspend fun runAction(action: AssistantAction): String {
        val result = api.runAssistantAction(
            AssistantActionRequest(type = action.type, args = action.args),
        )
        return result.summary
    }
}
