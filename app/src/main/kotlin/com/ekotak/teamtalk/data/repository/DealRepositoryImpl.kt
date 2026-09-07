package com.ekotak.teamtalk.data.repository

import com.ekotak.teamtalk.data.local.dao.DealDao
import com.ekotak.teamtalk.data.local.entity.DealInstallationsEntity
import com.ekotak.teamtalk.data.local.entity.DealMutationEntity
import com.ekotak.teamtalk.data.local.entity.DealMutationEntity.Companion.KIND_PATCH
import com.ekotak.teamtalk.data.local.preferences.SessionPreferences
import com.ekotak.teamtalk.data.mapper.toDetail
import com.ekotak.teamtalk.data.mapper.toDomain
import com.ekotak.teamtalk.data.remote.api.TeamTalkApi
import com.ekotak.teamtalk.data.remote.dto.AssistantMessageDto
import com.ekotak.teamtalk.data.remote.dto.ChangeStageRequest
import com.ekotak.teamtalk.data.remote.dto.ClientAssistantRequest
import com.ekotak.teamtalk.data.remote.dto.DealContactRequest
import com.ekotak.teamtalk.data.remote.dto.DealInstallationsDto
import com.ekotak.teamtalk.data.remote.dto.SetInstallationsRequest
import com.ekotak.teamtalk.data.remote.dto.buildDealPatch
import com.ekotak.teamtalk.data.sync.DealSyncScheduler
import com.ekotak.teamtalk.domain.model.AssistantMessage
import com.ekotak.teamtalk.domain.model.AssistantReply
import com.ekotak.teamtalk.domain.model.Client
import com.ekotak.teamtalk.domain.model.Deal
import com.ekotak.teamtalk.domain.model.DealDetail
import com.ekotak.teamtalk.domain.model.DealDraft
import com.ekotak.teamtalk.domain.model.DealInstallations
import com.ekotak.teamtalk.domain.model.DealStage
import com.ekotak.teamtalk.domain.model.InstallationStage
import com.ekotak.teamtalk.domain.model.StageInstallations
import com.ekotak.teamtalk.domain.model.applyDraft
import com.ekotak.teamtalk.domain.repository.DealRepository
import com.ekotak.teamtalk.domain.repository.DealSyncResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import java.io.IOException
import javax.inject.Inject

/**
 * Lista i karta deala idą prosto z sieci (patrz `DealRepository`), ale zmiany
 * robione przy kliencie mają cache i kolejkę: rozróżnienie brak-sieci
 * (`IOException`) od odmowy serwera (`HttpException`) jest tu istotne —
 * pierwsze da się nadrobić później, drugiego nie: 403 przy braku `deal.manage`
 * nie stanie się prawdziwe przez ponowienie.
 */
class DealRepositoryImpl @Inject constructor(
    private val api: TeamTalkApi,
    private val dao: DealDao,
    private val json: Json,
    private val syncScheduler: DealSyncScheduler,
    private val sessionPreferences: SessionPreferences,
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
        return try {
            api.updateDeal(original.id, patch).toDomain()
        } catch (_: IOException) {
            // Bez zasięgu: żądanie do kolejki, a ekranowi oddajemy deala tak,
            // jak będzie wyglądał po wysłaniu. Scalamy z tym, co już czeka —
            // handlowiec u klienta rusza kilka pól pod rząd, a każde z nich ma
            // dojechać, nie tylko ostatnie.
            enqueue(original.id, KIND_PATCH, merged(original.id, patch).toString())
            original.applyDraft(draft)
        }
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

    // ── Migawki instalacji ────────────────────────────────────────────────────

    /**
     * Odczyt „najpierw sieć, przy jej braku cache". Bez cache i bez sieci
     * puszczamy `IOException` dalej: pusty zakres wyglądałby jak wybór klienta
     * („nic nie chce"), a to inna informacja niż „nie wiem, co wybrał".
     */
    override suspend fun getInstallations(dealId: String): DealInstallations {
        val fresh = try {
            api.getDealInstallations(dealId).also { cache(dealId, it) }
        } catch (e: IOException) {
            cached(dealId) ?: throw e
        }
        return overlay(fresh.toDomain(), dao.getMutationsForDeal(dealId))
    }

    override suspend fun setInstallations(
        dealId: String,
        stage: InstallationStage,
        categoryIds: List<String>,
    ): DealInstallations {
        val kind = DealMutationEntity.installationKind(stage.wire)
        return try {
            val saved = api.setDealInstallations(
                id = dealId,
                stage = stage.wire,
                request = SetInstallationsRequest(categoryIds),
            )
            cache(dealId, saved)
            dao.deleteMutation(dealId, kind)
            overlay(saved.toDomain(), dao.getMutationsForDeal(dealId))
        } catch (_: IOException) {
            val body = json.encodeToString(
                SetInstallationsRequest.serializer(),
                SetInstallationsRequest(categoryIds),
            )
            enqueue(dealId, kind, body)
            // Zakładka dostaje wynik od razu — inaczej zaznaczenie wracałoby do
            // poprzedniego stanu i wyglądało jak zignorowany klik.
            val base = cached(dealId)?.toDomain() ?: DealInstallations()
            overlay(base, dao.getMutationsForDeal(dealId))
        }
    }

    // ── Kolejka ───────────────────────────────────────────────────────────────

    /**
     * Opróżnianie kolejki, od najstarszej zmiany. Odmowa serwera kończy wpis
     * (ponowienie nic nie zmieni), ale mówimy o niej człowiekowi: to jego
     * ustalenie z klientem przepadło.
     */
    override suspend fun syncPendingMutations(): DealSyncResult {
        val queue = dao.getMutations()
        if (queue.isEmpty()) return DealSyncResult.DONE

        for (row in queue) {
            try {
                val stage = DealMutationEntity.stageOf(row.kind)
                if (stage != null) {
                    val ids = decodeIds(row.payload)
                    val saved = api.setDealInstallations(
                        id = row.dealId,
                        stage = stage,
                        request = SetInstallationsRequest(ids),
                    )
                    cache(row.dealId, saved)
                } else {
                    val body = runCatching { json.parseToJsonElement(row.payload) as? JsonObject }
                        .getOrNull() ?: throw IllegalStateException("payload")
                    api.updateDeal(row.dealId, body)
                }
                dao.deleteMutation(row.dealId, row.kind)
            } catch (_: IOException) {
                // Sieć znowu padła — reszta kolejki poczeka na następny przebieg.
                return DealSyncResult.RETRY
            } catch (e: Exception) {
                dao.deleteMutation(row.dealId, row.kind)
                sessionPreferences.saveSyncProblem(discardMessage(row.kind, e))
            }
        }
        return DealSyncResult.DONE
    }

    private suspend fun enqueue(dealId: String, kind: String, payload: String) {
        dao.upsertMutation(
            DealMutationEntity(
                dealId = dealId,
                kind = kind,
                payload = payload,
                createdAt = System.currentTimeMillis(),
            ),
        )
        // Warunek sieci zdejmuje z nas odpytywanie — system sam obudzi
        // robotnika, gdy telefon wróci w zasięg.
        syncScheduler.scheduleSync()
    }

    /** Nowy `PATCH` doklejony do czekającego: pola ruszone teraz wygrywają. */
    private suspend fun merged(dealId: String, patch: JsonObject): JsonObject {
        val waiting = dao.getMutation(dealId, KIND_PATCH)?.payload
            ?.let { runCatching { json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
            ?: return patch
        return buildJsonObject {
            waiting.forEach { (key, value) -> put(key, value) }
            patch.forEach { (key, value) -> put(key, value) }
        }
    }

    private suspend fun cache(dealId: String, dto: DealInstallationsDto) {
        dao.upsertInstallations(
            DealInstallationsEntity(
                dealId = dealId,
                payload = json.encodeToString(DealInstallationsDto.serializer(), dto),
                syncedAt = System.currentTimeMillis(),
            ),
        )
    }

    /** Uszkodzony wpis w cache nie może wywrócić zakładki — wtedy po prostu brak. */
    private suspend fun cached(dealId: String): DealInstallationsDto? =
        dao.getInstallations(dealId)?.payload
            ?.let { runCatching { json.decodeFromString(DealInstallationsDto.serializer(), it) }.getOrNull() }

    private fun decodeIds(payload: String): List<String> =
        runCatching { json.decodeFromString(SetInstallationsRequest.serializer(), payload).categoryIds }
            .getOrDefault(emptyList())

    /**
     * Migawki widziane przez zakładkę: odpowiedź serwera z nałożoną kolejką.
     *
     * Dziedziczenie (carry-forward) liczy API, więc bez zasięgu robimy to samo
     * u siebie przybliżeniem: etapy PÓŹNIEJSZE, które miały dokładnie ten sam
     * wybór co edytowany (czyli go dziedziczyły), dostają nowy. Etap z własną,
     * różną migawką zostaje nietknięty — tak samo zachowa się serwer.
     */
    private fun overlay(
        base: DealInstallations,
        queue: List<DealMutationEntity>,
    ): DealInstallations {
        if (queue.isEmpty()) return base
        var out = base
        for (row in queue) {
            val stage = DealMutationEntity.stageOf(row.kind)
                ?.let { InstallationStage.fromWire(it) } ?: continue
            out = out.withPendingStage(stage, decodeIds(row.payload))
        }
        return out
    }

    private fun DealInstallations.withPendingStage(
        stage: InstallationStage,
        categoryIds: List<String>,
    ): DealInstallations {
        val inheritedBefore = forStage(stage)?.categoryIds
        val patched = stages.map { snapshot ->
            when {
                snapshot.stage == stage ->
                    snapshot.copy(categoryIds = categoryIds, pending = true)

                snapshot.stage.ordinal > stage.ordinal &&
                    snapshot.categoryIds == inheritedBefore ->
                    snapshot.copy(categoryIds = categoryIds)

                else -> snapshot
            }
        }
        // Migawki tego etapu mogło nie być w cache (deal wczytany po raz
        // pierwszy bez zasięgu) — wtedy dokładamy ją, żeby wybór nie zniknął.
        return copy(
            stages = if (patched.any { it.stage == stage }) {
                patched
            } else {
                patched + StageInstallations(
                    stage = stage,
                    categoryIds = categoryIds,
                    editable = true,
                    pending = true,
                )
            },
        )
    }

    /** Co powiedzieć, gdy serwer odmówił i zmiana przepadła bezpowrotnie. */
    private fun discardMessage(kind: String, error: Exception): String {
        val what = if (DealMutationEntity.stageOf(kind) != null) {
            "zakresu instalacji"
        } else {
            "zmian karty deala"
        }
        return "Nie udało się wysłać $what zapisanych bez zasięgu: ${error.message ?: "odmowa serwera"}."
    }
}
