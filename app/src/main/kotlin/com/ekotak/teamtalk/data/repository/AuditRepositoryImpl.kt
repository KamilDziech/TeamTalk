package com.ekotak.teamtalk.data.repository

import com.ekotak.teamtalk.data.local.dao.AuditDao
import com.ekotak.teamtalk.data.local.entity.AuditEntity
import com.ekotak.teamtalk.data.local.entity.AuditInstallationsEntity
import com.ekotak.teamtalk.data.local.entity.AuditMutationEntity
import com.ekotak.teamtalk.data.local.entity.AuditMutationEntity.Companion.FIELD_CREATE
import com.ekotak.teamtalk.data.local.entity.AuditMutationEntity.Companion.FIELD_FORM
import com.ekotak.teamtalk.data.local.entity.AuditMutationEntity.Companion.LOCAL_ID_PREFIX
import com.ekotak.teamtalk.data.local.preferences.SessionPreferences
import com.ekotak.teamtalk.data.mapper.buildHeatloadBody
import com.ekotak.teamtalk.data.mapper.offerLockFrom
import com.ekotak.teamtalk.data.mapper.toCatalogEntity
import com.ekotak.teamtalk.data.mapper.toDomain
import com.ekotak.teamtalk.data.mapper.toEntity
import com.ekotak.teamtalk.data.mapper.ufhToFormData
import com.ekotak.teamtalk.data.remote.api.TeamTalkApi
import com.ekotak.teamtalk.data.sync.AuditSyncScheduler
import com.ekotak.teamtalk.domain.model.Audit
import com.ekotak.teamtalk.domain.model.BuildingStandard
import com.ekotak.teamtalk.domain.model.Category
import com.ekotak.teamtalk.domain.model.HeatloadMode
import com.ekotak.teamtalk.domain.model.InstallationStage
import com.ekotak.teamtalk.domain.model.OfferLock
import com.ekotak.teamtalk.domain.model.UfhState
import com.ekotak.teamtalk.domain.repository.AuditInstallations
import com.ekotak.teamtalk.domain.repository.AuditRepository
import com.ekotak.teamtalk.domain.repository.AuditSaveResult
import com.ekotak.teamtalk.domain.repository.AuditSyncResult
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import retrofit2.HttpException
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import javax.inject.Inject

/**
 * Odczyt „najpierw sieć, przy jej braku cache", zapis „najpierw sieć, przy jej
 * braku kolejka". Rozróżnienie brak-sieci (`IOException`) od odmowy serwera
 * (`HttpException`) jest tu istotne: pierwsze da się nadrobić później, drugiego
 * nie — 409 przy podpisanej umowie nie stanie się prawdziwe przez ponowienie.
 */
class AuditRepositoryImpl @Inject constructor(
    private val api: TeamTalkApi,
    private val dao: AuditDao,
    private val json: Json,
    private val syncScheduler: AuditSyncScheduler,
    private val sessionPreferences: SessionPreferences,
) : AuditRepository {

    override suspend fun getAudits(dealId: String): List<Audit> = try {
        val fresh = api.getDealAudits(dealId).map { it.toEntity(dealId) }
        dao.replaceForDeal(dealId, fresh)
        // Czytamy z cache, a nie z odpowiedzi: w bazie leżą też rekordy
        // czekające w kolejce, o których serwer jeszcze nie wie.
        dao.getForDeal(dealId).map { it.toDomain() }
    } catch (_: IOException) {
        dao.getForDeal(dealId).map { it.toDomain() }
    }

    override suspend fun getCategories(): List<Category> = try {
        val fresh = api.getCategories().map { it.toCatalogEntity() }
        dao.replaceCategories(fresh)
        fresh.map { it.toDomain() }
    } catch (_: IOException) {
        dao.getCategories().map { it.toDomain() }
    }

    override suspend fun getAuditInstallations(dealId: String): AuditInstallations = try {
        val stages = api.getDealInstallations(dealId).stages
        val result = AuditInstallations(
            auditStage = stages
                .firstOrNull { it.stage == InstallationStage.AUDIT.wire }
                ?.categories
                .orEmpty(),
            allStages = stages.flatMap { it.categories }.distinct(),
            soldStage = stages
                .firstOrNull { it.stage == InstallationStage.SOLD.wire }
                ?.categories
                .orEmpty(),
            byStage = stages.associate { it.stage to it.categories },
        )
        dao.upsertInstallations(
            AuditInstallationsEntity(
                dealId = dealId,
                categoryIds = result.auditStage,
                allStageCategoryIds = result.allStages,
                soldStageCategoryIds = result.soldStage,
                stagesJson = encodeStages(result.byStage),
                syncedAt = System.currentTimeMillis(),
            ),
        )
        result
    } catch (_: IOException) {
        dao.getInstallations(dealId)
            ?.let {
                AuditInstallations(
                    auditStage = it.categoryIds,
                    allStages = it.allStageCategoryIds,
                    soldStage = it.soldStageCategoryIds,
                    byStage = decodeStages(it.stagesJson),
                )
            }
            ?: AuditInstallations()
    }

    /** Migawki etapów do jednej kolumny — mapa `etap → id węzłów`. */
    private fun encodeStages(byStage: Map<String, List<String>>): String =
        runCatching { json.encodeToString(STAGES_SERIALIZER, byStage) }.getOrDefault("{}")

    /** Uszkodzony wpis nie może wywrócić zakładki — wtedy po prostu brak kaskady. */
    private fun decodeStages(raw: String): Map<String, List<String>> =
        runCatching { json.decodeFromString(STAGES_SERIALIZER, raw) }.getOrDefault(emptyMap())

    override suspend fun createHeatload(
        dealId: String,
        mode: HeatloadMode?,
        areaM2: Double?,
        standard: BuildingStandard?,
        heightM: Double?,
        kw: Double?,
        note: String?,
    ): AuditSaveResult {
        val body = buildHeatloadBody(mode, areaM2, standard, heightM, kw, note)
        return try {
            val created = api.createDealAudit(dealId, body)
            dao.upsert(created.toEntity(dealId))
            AuditSaveResult.SENT
        } catch (_: IOException) {
            // Wpis dostaje lokalne id i od razu ląduje w cache — bez tego
            // audytor po zapisie zobaczyłby pustą listę i wpisał go drugi raz.
            val localId = LOCAL_ID_PREFIX + UUID.randomUUID()
            val now = System.currentTimeMillis()
            dao.upsert(
                AuditEntity(
                    id = localId,
                    dealId = dealId,
                    heatloadMode = mode?.wire,
                    // kW szybkiego szacunku liczy serwer — do czasu wysyłki
                    // pokazujemy wpis bez wyniku, zamiast zgadywać za niego.
                    heatloadKw = if (mode == HeatloadMode.DIN) kw else null,
                    formData = note?.trim()?.takeIf { it.isNotEmpty() }
                        ?.let { buildJsonObject { put("note", JsonPrimitive(it)) }.toString() },
                    createdAt = nowIso(),
                    pendingSince = now,
                ),
            )
            enqueue(localId, FIELD_CREATE, body, dealId, now)
            AuditSaveResult.QUEUED
        }
    }

    override suspend fun saveInstallationAudit(
        dealId: String,
        auditId: String?,
        categoryId: String,
        state: UfhState,
        includeCooling: Boolean,
    ): AuditSaveResult {
        val formData = ufhToFormData(state, includeCooling, categoryId)
        val body = buildJsonObject { put("formData", formData) }
        val target = resolveTargetId(dealId, auditId, categoryId)

        // Rekord istnieje tylko lokalnie (poprzedni zapis czeka w kolejce) —
        // nie ma czego PATCHować, poprawiamy ciało czekającego `POST`-a.
        if (target?.startsWith(LOCAL_ID_PREFIX) == true) {
            enqueueForm(dealId, target, formData, body, create = true)
            return AuditSaveResult.QUEUED
        }

        return try {
            val saved = if (target != null) {
                api.updateAudit(target, body)
            } else {
                api.createDealAudit(dealId, body)
            }
            dao.upsert(saved.toEntity(dealId))
            dao.deleteMutations(saved.id)
            AuditSaveResult.SENT
        } catch (_: IOException) {
            enqueueForm(dealId, target, formData, body, create = target == null)
            AuditSaveResult.QUEUED
        }
    }

    /**
     * Do którego rekordu ma trafić zapis.
     *
     * Ekran trzyma id z ostatniego odczytu, a to bywa NIEAKTUALNE: kolejka mogła
     * w międzyczasie wysłać rekord zapisany offline, więc jego lokalne id już nie
     * istnieje — serwer nadał własne. Zapis pod martwym lokalnym id poszedłby
     * jako kolejny `POST` i deal miałby DWA formularze tego samego węzła.
     * Dlatego przy lokalnym id, którego nie ma już w cache, szukamy rekordu tego
     * samego węzła katalogu — dokładnie tak, jak dobiera go zakładka.
     */
    private suspend fun resolveTargetId(
        dealId: String,
        auditId: String?,
        categoryId: String,
    ): String? {
        if (auditId == null) return null
        if (!auditId.startsWith(LOCAL_ID_PREFIX)) return auditId
        if (dao.getById(auditId) != null) return auditId

        return dao.getForDeal(dealId)
            .firstOrNull { !it.id.startsWith(LOCAL_ID_PREFIX) && it.formCategoryId() == categoryId }
            ?.id
    }

    /** `formData.categoryId` rekordu z cache; `null` = wpis Heizlast albo śmieć. */
    private fun AuditEntity.formCategoryId(): String? = formData
        ?.let { runCatching { json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
        ?.get("categoryId")
        ?.let { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }

    /**
     * Formularz do kolejki i do cache. Rekordu bez serwerowego id nie da się
     * PATCHować, więc czeka jako `POST` (`FIELD_CREATE`) — po wysłaniu dostanie
     * id od serwera i kolejne zapisy pójdą już zwykłą drogą.
     */
    private suspend fun enqueueForm(
        dealId: String,
        auditId: String?,
        formData: JsonObject,
        body: JsonObject,
        create: Boolean,
    ) {
        val id = auditId ?: (LOCAL_ID_PREFIX + UUID.randomUUID())
        val now = System.currentTimeMillis()
        val cached = dao.getById(id)
        dao.upsert(
            AuditEntity(
                id = id,
                dealId = dealId,
                heatloadMode = cached?.heatloadMode,
                heatloadKw = cached?.heatloadKw,
                formData = formData.toString(),
                createdAt = cached?.createdAt ?: nowIso(),
                pendingSince = now,
            ),
        )
        enqueue(id, if (create) FIELD_CREATE else FIELD_FORM, body, dealId, now)
    }

    private suspend fun enqueue(
        auditId: String,
        field: String,
        body: JsonObject,
        dealId: String,
        now: Long,
    ) {
        dao.upsertMutation(
            AuditMutationEntity(
                auditId = auditId,
                field = field,
                payload = body.toString(),
                dealId = dealId,
                createdAt = now,
            ),
        )
        // Warunek sieci zdejmuje z nas odpytywanie — system sam obudzi
        // robotnika, gdy telefon wróci w zasięg.
        syncScheduler.scheduleSync()
    }

    override suspend fun getOfferLock(dealId: String): OfferLock? = try {
        offerLockFrom(api.getDealContracts(dealId))
    } catch (_: HttpException) {
        // Cisza celowa, tak jak w panelu: gdy odczyt umów padnie (brak
        // uprawnienia do modułu, starsze API), formularz zostaje otwarty,
        // a niedozwolony zapis i tak zatrzyma API.
        null
    } catch (_: IOException) {
        // Bez zasięgu nie wiemy, czy oferta jest zamknięta. Zostawiamy
        // formularz otwarty: audytor ma zapisać to, co zmierzył, a rozjazd
        // z umową i tak wychwyci serwer przy wysyłce (409) i powie o tym.
        null
    }

    /**
     * Opróżnianie kolejki, od najstarszego zapisu. Rekord bez serwerowego id
     * idzie jako `POST`, reszta jako `PATCH`. Po udanym `POST` przepisujemy
     * cache i kolejkę pod id nadane przez serwer — bez tego drugi zapis
     * założyłby DRUGI formularz tego samego węzła.
     */
    override suspend fun syncPendingMutations(): AuditSyncResult {
        val queue = dao.getMutations()
        if (queue.isEmpty()) return AuditSyncResult.DONE

        for (row in queue) {
            val body = runCatching { json.parseToJsonElement(row.payload) as? JsonObject }
                .getOrNull()
            if (body == null) {
                // Nieczytelne ciało — ponowienie nic nie da, a wpis blokowałby
                // kolejkę w nieskończoność.
                dao.deleteMutation(row.auditId, row.field)
                continue
            }

            try {
                val saved = if (row.field == FIELD_CREATE) {
                    api.createDealAudit(row.dealId, body)
                } else {
                    api.updateAudit(row.auditId, body)
                }
                // Kasujemy dokładnie ten wiersz, nie całą kolejkę audytu:
                // w trakcie wysyłki audytor mógł zapisać formularz jeszcze raz.
                dao.deleteMutation(row.auditId, row.field)
                if (row.auditId != saved.id) {
                    // Rekord dostał id od serwera. Zapis zrobiony w międzyczasie
                    // przechodzi na nie i staje się `PATCH`-em — jako `POST`
                    // założyłby drugi formularz tego samego węzła.
                    dao.rekeyMutations(row.auditId, saved.id, FIELD_FORM)
                    dao.deleteById(row.auditId)
                }
                dao.upsert(saved.toEntity(row.dealId))
            } catch (_: IOException) {
                // Sieć znowu padła — reszta kolejki poczeka na następny przebieg.
                return AuditSyncResult.RETRY
            } catch (e: HttpException) {
                // Serwer odrzucił zapis. Ponowienie nic nie zmieni, więc
                // porzucamy wpis — ale mówimy o tym człowiekowi, bo to jego
                // praca u klienta przepadła.
                dao.deleteMutation(row.auditId, row.field)
                dao.getById(row.auditId)?.let { dao.upsert(it.copy(pendingSince = null)) }
                sessionPreferences.saveSyncProblem(discardMessage(e.code()))
            }
        }
        return AuditSyncResult.DONE
    }

    /**
     * Znacznik czasu w formacie, w jakim daty przychodzą z API — wpis
     * zakolejkowany offline ma stanąć na liście w tym samym porządku, w jakim
     * stanie po wysłaniu.
     */
    private fun nowIso(): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(Date())

    private fun discardMessage(code: Int): String = when (code) {
        409 -> "Zapis audytu przepadł — klient podpisał już umowę na tę ofertę. " +
            "Zmianę zgłoś w panelu."
        404 -> "Zapis audytu przepadł — deal zniknął z panelu."
        403 -> "Zapis audytu przepadł — brak uprawnień."
        else -> "Zapis audytu przepadł — serwer go odrzucił (kod $code)."
    }

    private companion object {
        val STAGES_SERIALIZER = MapSerializer(String.serializer(), ListSerializer(String.serializer()))
    }
}
