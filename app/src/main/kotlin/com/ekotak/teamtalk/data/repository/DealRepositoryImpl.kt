package com.ekotak.teamtalk.data.repository

import com.ekotak.teamtalk.data.mapper.toDetail
import com.ekotak.teamtalk.data.mapper.toDomain
import com.ekotak.teamtalk.data.remote.api.TeamTalkApi
import com.ekotak.teamtalk.data.remote.dto.AssistantMessageDto
import com.ekotak.teamtalk.data.remote.dto.ChangeStageRequest
import com.ekotak.teamtalk.data.remote.dto.ClientAssistantRequest
import com.ekotak.teamtalk.data.remote.dto.DealContactRequest
import com.ekotak.teamtalk.data.remote.dto.SetInstallationsRequest
import com.ekotak.teamtalk.data.remote.dto.buildDealPatch
import com.ekotak.teamtalk.domain.model.AssistantMessage
import com.ekotak.teamtalk.domain.model.AssistantReply
import com.ekotak.teamtalk.domain.model.Client
import com.ekotak.teamtalk.domain.model.Deal
import com.ekotak.teamtalk.domain.model.DealDetail
import com.ekotak.teamtalk.domain.model.DealDraft
import com.ekotak.teamtalk.domain.model.DealInstallations
import com.ekotak.teamtalk.domain.model.DealStage
import com.ekotak.teamtalk.domain.model.InstallationStage
import com.ekotak.teamtalk.domain.repository.DealRepository
import javax.inject.Inject

class DealRepositoryImpl @Inject constructor(
    private val api: TeamTalkApi,
) : DealRepository {

    override suspend fun getDeals(stage: DealStage?, overdue: Boolean): List<Deal> =
        api.getDeals(
            stage = stage?.wire,
            overdue = if (overdue) "true" else null,
        ).map { it.toDomain() }

    override suspend fun getDealDetail(id: String): DealDetail =
        api.getDealById(id).toDetail()

    override suspend fun changeStage(
        id: String,
        stage: DealStage,
        lostReasonCategory: String?,
        lostReason: String?,
        note: String?,
    ): Deal = api.changeDealStage(
        id = id,
        request = ChangeStageRequest(
            stage = stage.wire,
            lostReason = lostReason,
            lostReasonCategory = lostReasonCategory,
            note = note,
        ),
    ).toDomain()

    override suspend fun updateDeal(original: Deal, draft: DealDraft): Deal {
        val patch = buildDealPatch(original, draft)
        // Pusty patch API odrzuciłoby („Brak pól do aktualizacji." → 422),
        // a i tak nie ma czego zapisywać.
        if (patch.isEmpty()) return original
        return api.updateDeal(original.id, patch).toDomain()
    }

    override suspend fun getCompanions(dealId: String): List<Client> =
        api.getDealCompanions(dealId).map { it.toDomain() }

    override suspend fun addCompanion(dealId: String, clientId: String): List<Client> =
        api.addDealCompanion(dealId, DealContactRequest(clientId)).map { it.toDomain() }

    // 204 bez ciała — listę po zmianie dociągamy osobno, żeby wywołujący
    // dostał ten sam kontrakt co przy dopięciu kontaktu.
    override suspend fun removeCompanion(dealId: String, clientId: String): List<Client> {
        api.removeDealCompanion(dealId, clientId)
        return getCompanions(dealId)
    }

    override suspend fun setPrimaryContact(dealId: String, clientId: String) {
        api.setPrimaryDealContact(dealId, DealContactRequest(clientId))
    }

    override suspend fun askAssistant(
        dealId: String,
        messages: List<AssistantMessage>,
    ): AssistantReply {
        val reply = api.askDealAssistant(
            id = dealId,
            request = ClientAssistantRequest(
                messages = messages.map { AssistantMessageDto(role = it.role, content = it.content) },
            ),
        )
        return AssistantReply(
            text = reply.text,
            configured = reply.configured,
            commsCount = reply.commsCount,
            dealCount = reply.dealCount,
        )
    }

    override suspend fun getInstallations(dealId: String): DealInstallations =
        api.getDealInstallations(dealId).toDomain()

    override suspend fun setInstallations(
        dealId: String,
        stage: InstallationStage,
        categoryIds: List<String>,
    ): DealInstallations = api.setDealInstallations(
        id = dealId,
        stage = stage.wire,
        request = SetInstallationsRequest(categoryIds),
    ).toDomain()
}
