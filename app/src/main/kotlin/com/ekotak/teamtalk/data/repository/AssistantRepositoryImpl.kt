package com.ekotak.teamtalk.data.repository

import com.ekotak.teamtalk.data.remote.api.TeamTalkApi
import com.ekotak.teamtalk.data.remote.dto.AssistantActionRequest
import com.ekotak.teamtalk.data.remote.dto.AssistantChatRequest
import com.ekotak.teamtalk.data.remote.dto.AssistantMessageDto
import com.ekotak.teamtalk.domain.model.AssistantAction
import com.ekotak.teamtalk.domain.model.AssistantActionOutcome
import com.ekotak.teamtalk.domain.model.AssistantAnswer
import com.ekotak.teamtalk.domain.model.CardDuplicate
import com.ekotak.teamtalk.domain.model.CardScanResult
import com.ekotak.teamtalk.domain.model.ClientCategory
import com.ekotak.teamtalk.domain.model.ScannedCard
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
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

    override suspend fun runAction(action: AssistantAction): AssistantActionOutcome {
        val result = api.runAssistantAction(
            AssistantActionRequest(type = action.type, args = action.args),
        )
        return AssistantActionOutcome(summary = result.summary, clientId = result.clientId)
    }

    override suspend fun scanCard(jpeg: ByteArray): CardScanResult {
        val part = MultipartBody.Part.createFormData(
            "file",
            "wizytowka.jpg",
            jpeg.toRequestBody("image/jpeg".toMediaType()),
        )
        val dto = api.scanBusinessCard(part)
        val c = dto.card
        return CardScanResult(
            source = dto.source,
            card = ScannedCard(
                firstName = c.firstName,
                lastName = c.lastName,
                companyName = c.companyName,
                jobTitle = c.jobTitle,
                nip = c.nip,
                phone = c.phone,
                phone2 = c.phone2,
                email = c.email,
                email2 = c.email2,
                website = c.website,
                street = c.street,
                postalCode = c.postalCode,
                city = c.city,
            ),
            duplicates = dto.duplicates.map {
                CardDuplicate(
                    id = it.id,
                    name = it.name,
                    companyName = it.companyName,
                    category = ClientCategory.fromWire(it.category),
                    phone = it.phone,
                    email = it.email,
                )
            },
            canSave = dto.canSave,
        )
    }
}
