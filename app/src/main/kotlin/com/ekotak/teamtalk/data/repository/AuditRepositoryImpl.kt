package com.ekotak.teamtalk.data.repository

import com.ekotak.teamtalk.data.local.dao.AuditDao
import com.ekotak.teamtalk.data.local.dao.MemberDao
import com.ekotak.teamtalk.data.local.entity.AuditEntity
import com.ekotak.teamtalk.data.local.entity.AuditInstallationsEntity
import com.ekotak.teamtalk.data.local.entity.AuditMutationEntity
import com.ekotak.teamtalk.data.local.entity.AuditMutationEntity.Companion.FIELD_CREATE
import com.ekotak.teamtalk.data.local.entity.AuditMutationEntity.Companion.FIELD_FORM
import com.ekotak.teamtalk.data.local.entity.AuditMutationEntity.Companion.LOCAL_ID_PREFIX
import com.ekotak.teamtalk.data.local.preferences.SessionPreferences
import com.ekotak.teamtalk.data.mapper.offerLockFrom
import com.ekotak.teamtalk.data.mapper.toCatalogEntity
import com.ekotak.teamtalk.data.mapper.toDomain
import com.ekotak.teamtalk.data.mapper.toEntity
import com.ekotak.teamtalk.data.mapper.ufhToFormData
import com.ekotak.teamtalk.data.remote.api.TeamTalkApi
import com.ekotak.teamtalk.data.remote.dto.AuditDto
import com.ekotak.teamtalk.data.sync.AuditSyncScheduler
import com.ekotak.teamtalk.domain.model.Audit
import com.ekotak.teamtalk.domain.model.AuditAuthor
import com.ekotak.teamtalk.domain.model.AuditConflict
import com.ekotak.teamtalk.domain.model.Category
import com.ekotak.teamtalk.domain.model.InstallationStage
import com.ekotak.teamtalk.domain.model.OfferLock
import com.ekotak.teamtalk.domain.model.UfhState
import com.ekotak.teamtalk.domain.repository.AuditInstallations
import com.ekotak.teamtalk.domain.repository.AuditRepository
import com.ekotak.teamtalk.domain.repository.AuditSaveResult
import com.ekotak.teamtalk.domain.repository.AuditSyncResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
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
 *
 * Wyjątkiem od „odmowa = porzuć" jest 409 `AUDIT_STALE`: audyt zmieniono
 * w panelu od wersji, na której audytor zaczął edycję. Tu nie wiadomo, kto ma
 * rację, więc zapis zostaje w kolejce w stanie konfliktu i czeka na człowieka.
 * Od tego zależy niezmiennik zakładki: zapis z terenu nie kasuje po cichu pracy
 * zrobionej w panelu (rzutu, kropek rozdzielaczy, obrysów).
 */
class AuditRepositoryImpl @Inject constructor(
    private val api: TeamTalkApi,
    private val dao: AuditDao,
    private val memberDao: MemberDao,
    private val json: Json,
    private val syncScheduler: AuditSyncScheduler,
    private val sessionPreferences: SessionPreferences,
) : AuditRepository {

    /**
     * Jedna wysyłka audytu naraz — z ekranu, z workera albo z rozstrzygnięcia.
     * Bez tego worker wysyłający starszy zapis i zapis z ekranu idący w tej
     * samej chwili ścigałyby się o `updatedAt`, a przegrany dostałby 409
     * `AUDIT_STALE` na WŁASNYM zapisie — fałszywy konflikt. Blokada trzyma
     * się pojedynczego żądania, więc ekran czeka najwyżej jedną wysyłkę.
     * Repozytorium jest `@Singleton`, więc jedna blokada na proces.
     */
    private val queueLock = Mutex()

    override suspend fun getAudits(dealId: String): List<Audit> = try {
        val fresh = api.getDealAudits(dealId).map { it.toCachedEntity(dealId) }
        dao.replaceForDeal(dealId, fresh)
        // Czytamy z cache, a nie z odpowiedzi: w bazie leżą też rekordy
        // czekające w kolejce, o których serwer jeszcze nie wie.
        dao.getForDeal(dealId).map { it.toVersionedDomain() }
    } catch (_: IOException) {
        dao.getForDeal(dealId).map { it.toVersionedDomain() }
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

    override suspend fun saveInstallationAudit(
        dealId: String,
        auditId: String?,
        categoryId: String,
        state: UfhState,
        includeCooling: Boolean,
        baseUpdatedAt: String?,
    ): AuditSaveResult = queueLock.withLock {
        val formData = ufhToFormData(state, includeCooling, categoryId)
        val body = buildJsonObject { put("formData", formData) }
        val target = resolveTargetId(dealId, auditId, categoryId)

        // Rekord istnieje tylko lokalnie (poprzedni zapis czeka w kolejce) —
        // nie ma czego PATCHować, poprawiamy ciało czekającego `POST`-a.
        // Konflikt tu nie grozi: serwer o tym rekordzie jeszcze nie wie.
        if (target?.startsWith(LOCAL_ID_PREFIX) == true) {
            enqueueForm(dealId, target, formData, body, create = true, base = null)
            return@withLock AuditSaveResult.QUEUED
        }

        if (target == null) {
            return@withLock try {
                val saved = api.createDealAudit(dealId, body)
                dao.upsert(saved.toCachedEntity(dealId))
                dao.deleteMutations(saved.id)
                AuditSaveResult.SENT
            } catch (_: IOException) {
                enqueueForm(dealId, null, formData, body, create = true, base = null)
                AuditSaveResult.QUEUED
            }
        }

        val waiting = dao.getMutationsFor(target).firstOrNull { it.field == FIELD_FORM }
        if (waiting?.conflictJson != null) {
            // Konflikt czeka na decyzję człowieka. Nowy zapis podmienia tylko
            // moją wersję w kolejce — wysłanie jej teraz na tej samej bazie
            // i tak skończyłoby się tym samym 409.
            enqueueForm(dealId, target, formData, body, create = false, base = waiting.baseUpdatedAt)
            return@withLock AuditSaveResult.CONFLICT
        }

        // Edycja zaczęła się na wersji z czekającego wiersza, jeśli taki jest —
        // kolejne zapisy przed wysyłką bazy nie przesuwają.
        val base = if (waiting != null) {
            waiting.baseUpdatedAt
        } else {
            baseUpdatedAt ?: dao.getById(target)?.updatedAt
        }

        try {
            val saved = api.updateAudit(target, withExpected(body, base))
            dao.upsert(saved.toCachedEntity(dealId))
            dao.deleteMutations(saved.id)
            AuditSaveResult.SENT
        } catch (_: IOException) {
            enqueueForm(dealId, target, formData, body, create = false, base = base)
            AuditSaveResult.QUEUED
        } catch (e: HttpException) {
            val stale = e.readStale()
            val current = stale.current ?: throw stale.rethrowable
            enqueueForm(dealId, target, formData, body, create = false, base = base)
            dao.markConflict(target, FIELD_FORM, current.toString(), System.currentTimeMillis())
            AuditSaveResult.CONFLICT
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

    /**
     * `formData.categoryId` rekordu z cache; `null` = wpis spoza formularza
     * (np. Heizlast założony w panelu) albo śmieć.
     */
    private fun AuditEntity.formCategoryId(): String? = formData
        ?.let { runCatching { json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
        ?.get("categoryId")
        ?.let { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }

    /**
     * Formularz do kolejki i do cache. Rekordu bez serwerowego id nie da się
     * PATCHować, więc czeka jako `POST` (`FIELD_CREATE`) — po wysłaniu dostanie
     * id od serwera i kolejne zapisy pójdą już zwykłą drogą.
     *
     * Wiersz już czekający w kolejce zachowuje BAZĘ i ewentualny KONFLIKT —
     * nowy zapis zmienia wyłącznie treść. [base] liczy się tylko dla nowego
     * wiersza.
     */
    private suspend fun enqueueForm(
        dealId: String,
        auditId: String?,
        formData: JsonObject,
        body: JsonObject,
        create: Boolean,
        base: String?,
    ) {
        val id = auditId ?: (LOCAL_ID_PREFIX + UUID.randomUUID())
        val field = if (create) FIELD_CREATE else FIELD_FORM
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
                // Wersja serwera zostaje ta sama — moja zmiana jeszcze na nim
                // nie leży, więc to nadal wersja, na której zaczęła się edycja.
                updatedAt = cached?.updatedAt,
            ),
        )
        val existing = dao.getMutationsFor(id).firstOrNull { it.field == field }
        dao.upsertMutation(
            AuditMutationEntity(
                auditId = id,
                field = field,
                payload = body.toString(),
                dealId = dealId,
                createdAt = now,
                baseUpdatedAt = if (existing != null) existing.baseUpdatedAt else base,
                conflictJson = existing?.conflictJson,
                conflictAt = existing?.conflictAt,
            ),
        )
        // Warunek sieci zdejmuje z nas odpytywanie — system sam obudzi
        // robotnika, gdy telefon wróci w zasięg. Wiersz w konflikcie robotnik
        // pominie, więc zamówienie przebiegu niczego tu nie psuje.
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
     * idzie jako `POST`, reszta jako `PATCH` z `expectedUpdatedAt`.
     *
     * Kolejkę czytamy po każdym wierszu od nowa: wysłanie `POST`-a potrafi
     * przepisać zapis zrobiony w międzyczasie pod id z serwera, a taki wiersz
     * ma polecieć w tym samym przebiegu, a nie dopiero przy następnym starcie
     * aplikacji. [attempted] pilnuje, żeby żadnej wersji nie wysłać dwa razy.
     * Wiersze w konflikcie przeskakujemy — czekają na człowieka, nie na sieć.
     */
    override suspend fun syncPendingMutations(): AuditSyncResult {
        val attempted = HashSet<Triple<String, String, Long>>()
        while (true) {
            val outcome = queueLock.withLock {
                val row = dao.getMutations().firstOrNull {
                    it.conflictJson == null &&
                        Triple(it.auditId, it.field, it.createdAt) !in attempted
                } ?: return AuditSyncResult.DONE
                attempted += Triple(row.auditId, row.field, row.createdAt)
                sendRow(row, interactive = false)
            }
            // Sieć znowu padła — reszta kolejki poczeka na następny przebieg.
            if (outcome is SendOutcome.Network) return AuditSyncResult.RETRY
        }
    }

    override fun observeConflicts(dealId: String): Flow<List<AuditConflict>> =
        dao.observeConflicts(dealId).map { rows -> rows.mapNotNull { it.toConflict() } }

    override suspend fun resolveOverwrite(auditId: String): AuditSaveResult = queueLock.withLock {
        val rows = dao.getMutationsFor(auditId)
        val row = rows.firstOrNull { it.field == FIELD_FORM && it.conflictJson != null }
            // Nie ma już czego rozstrzygać (np. porzucone na drugim ekranie).
            ?: return@withLock if (rows.isEmpty()) AuditSaveResult.SENT else AuditSaveResult.QUEUED

        val current = row.currentDto()
        // Świadome „nadpisz": bazą staje się wersja, którą człowiek właśnie
        // widział. Jeśli panel zmieni audyt JESZCZE RAZ, dostaniemy nowy konflikt.
        dao.rebaseMutation(auditId, FIELD_FORM, current?.updatedAt)
        val fresh = dao.getMutationsFor(auditId).firstOrNull { it.field == FIELD_FORM }
            ?: return@withLock AuditSaveResult.SENT

        when (val outcome = sendRow(fresh, interactive = true)) {
            SendOutcome.Sent -> AuditSaveResult.SENT
            SendOutcome.Conflict -> AuditSaveResult.CONFLICT
            SendOutcome.Network -> {
                syncScheduler.scheduleSync()
                AuditSaveResult.QUEUED
            }
            is SendOutcome.Rejected -> {
                // Moja wersja przepadła (np. oferta zamknięta umową) — cache nie
                // może udawać, że leży na serwerze, więc bierze wersję z panelu.
                current?.let { dao.upsert(it.toCachedEntity(row.dealId)) }
                throw outcome.error ?: IllegalStateException("Zapis audytu odrzucony")
            }
        }
    }

    override suspend fun resolveDiscard(auditId: String) {
        queueLock.withLock {
            val row = dao.getMutationsFor(auditId).firstOrNull { it.conflictJson != null }
                ?: return
            val current = row.currentDto()
            dao.deleteMutations(auditId)
            if (current != null) {
                dao.upsert(current.toCachedEntity(row.dealId))
            } else {
                // Nieczytelny `current` — zdejmujemy tylko znacznik „czeka",
                // a świeżą wersję dociągnie najbliższy odczyt z zasięgiem.
                dao.getById(auditId)?.let { dao.upsert(it.copy(pendingSince = null)) }
            }
        }
    }

    override fun observeAuthor(): Flow<AuditAuthor?> =
        combine(sessionPreferences.session, memberDao.observeMembers()) { session, members ->
            if (session == null) return@combine null
            val me = members.firstOrNull { it.id == session.userId }
            // Ta sama reguła co `currentAuthor()` w panelu (`audit-actions.ts`):
            // „Imię Nazwisko", a gdy ich brak — e-mail konta.
            val fullName = listOfNotNull(me?.firstName, me?.lastName)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .trim()
            AuditAuthor(
                userId = session.userId,
                name = fullName.ifBlank { me?.email?.takeIf { it.isNotBlank() } ?: session.email },
            )
        }.distinctUntilChanged()

    // ── Wysyłka jednego wiersza ──────────────────────────────────────────────

    private sealed interface SendOutcome {
        data object Sent : SendOutcome
        data object Network : SendOutcome
        data object Conflict : SendOutcome
        data class Rejected(val error: Throwable?) : SendOutcome
    }

    /**
     * Wysyła wiersz kolejki. Wołać WYŁĄCZNIE pod [queueLock].
     *
     * @param interactive rozstrzygnięcie z ekranu — odmowę pokazuje wtedy sam
     *   ekran, więc nie odkładamy jej do skrzynki problemów synchronizacji.
     */
    private suspend fun sendRow(row: AuditMutationEntity, interactive: Boolean): SendOutcome {
        val body = runCatching { json.parseToJsonElement(row.payload) as? JsonObject }
            .getOrNull()
        if (body == null) {
            // Nieczytelne ciało — ponowienie nic nie da, a wpis blokowałby
            // kolejkę w nieskończoność.
            dao.deleteMutation(row.auditId, row.field)
            return SendOutcome.Rejected(null)
        }

        return try {
            val saved = if (row.field == FIELD_CREATE) {
                api.createDealAudit(row.dealId, body)
            } else {
                api.updateAudit(row.auditId, withExpected(body, row.baseUpdatedAt))
            }
            afterSent(row, saved)
            SendOutcome.Sent
        } catch (_: IOException) {
            SendOutcome.Network
        } catch (e: HttpException) {
            // `POST` nie ma bazy, więc nieaktualny być nie może.
            val stale = if (row.field == FIELD_FORM) e.readStale() else StaleRead(null, e)
            val current = stale.current
            if (current != null) {
                dao.markConflict(row.auditId, row.field, current.toString(), System.currentTimeMillis())
                if (!interactive) {
                    sessionPreferences.saveSyncProblem(
                        "Audyt zmieniono w panelu, zanim wysłaliśmy zapis z telefonu. " +
                            "Otwórz kartę deala i wybierz: nadpisz albo porzuć swoją wersję.",
                    )
                }
                return SendOutcome.Conflict
            }
            // Serwer odrzucił zapis z innego powodu. Ponowienie nic nie zmieni,
            // więc porzucamy wpis — ale mówimy o tym człowiekowi, bo to jego
            // praca u klienta przepadła.
            dao.deleteMutation(row.auditId, row.field)
            dao.getById(row.auditId)?.let { dao.upsert(it.copy(pendingSince = null)) }
            if (!interactive) sessionPreferences.saveSyncProblem(discardMessage(e.code()))
            SendOutcome.Rejected(stale.rethrowable)
        }
    }

    /**
     * Sprzątanie po przyjętym zapisie. Cache dostaje `updatedAt` z odpowiedzi —
     * to od tej wersji liczy się następna edycja.
     *
     * Wiersz kasujemy tylko wtedy, gdy nadal jest tym, który poszedł. Jeśli
     * w trakcie wysyłki pojawił się nowszy zapis (ten sam klucz, inny
     * `createdAt`), zostaje w kolejce z bazą przesuniętą na NASZ przyjęty zapis
     * — inaczej przy swojej wysyłce dostałby 409 na własnej zmianie.
     */
    private suspend fun afterSent(row: AuditMutationEntity, saved: AuditDto) {
        val savedEntity = saved.toCachedEntity(row.dealId)
        val newer = dao.deleteMutationIfUnchanged(row.auditId, row.field, row.createdAt) == 0
        val local = if (newer) dao.getById(row.auditId) else null

        if (row.auditId != saved.id) {
            // Rekord dostał id od serwera. Zapis zrobiony w międzyczasie
            // przechodzi na nie i staje się `PATCH`-em — jako `POST` założyłby
            // drugi formularz tego samego węzła.
            dao.rekeyMutations(row.auditId, saved.id, FIELD_FORM)
            dao.deleteById(row.auditId)
        }

        if (newer) {
            dao.rebaseMutation(saved.id, FIELD_FORM, saved.updatedAt)
            dao.upsert(
                savedEntity.copy(
                    formData = local?.formData ?: savedEntity.formData,
                    pendingSince = local?.pendingSince ?: System.currentTimeMillis(),
                ),
            )
        } else {
            dao.upsert(savedEntity)
        }
    }

    // ── 409 AUDIT_STALE ──────────────────────────────────────────────────────

    /**
     * Wynik odczytu ciała błędu. Ciało OkHttp da się przeczytać raz, więc gdy
     * to nie jest `AUDIT_STALE`, oddajemy [rethrowable] — kopię wyjątku z tym
     * samym ciałem, żeby `crmErrorMessage` nadal pokazał komunikat serwera
     * (np. o umowie zamykającej ofertę).
     */
    private class StaleRead(val current: JsonObject?, val rethrowable: HttpException)

    private fun HttpException.readStale(): StaleRead {
        if (code() != 409) return StaleRead(null, this)
        val errorBody = runCatching { response()?.errorBody() }.getOrNull()
        val contentType = errorBody?.contentType()
        val raw = runCatching { errorBody?.string() }.getOrNull()
            ?: return StaleRead(null, this)
        val parsed = runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
        val errorCode = (parsed?.get("code") as? JsonPrimitive)?.takeIf { it.isString }?.content
        val current = parsed?.get("current") as? JsonObject
        if (errorCode == STALE_CODE && current != null) return StaleRead(current, this)

        val copy = runCatching {
            HttpException(
                Response.error<Any>(
                    code(),
                    raw.toResponseBody(contentType ?: "application/json".toMediaTypeOrNull()),
                ),
            )
        }.getOrDefault(this)
        return StaleRead(null, copy)
    }

    /** Ciało `PATCH` z warunkiem wersji; bez znanej bazy — zapis bezwarunkowy jak dotąd. */
    private fun withExpected(body: JsonObject, base: String?): JsonObject {
        if (base.isNullOrBlank()) return body
        return buildJsonObject {
            body.forEach { (key, value) -> put(key, value) }
            put("expectedUpdatedAt", JsonPrimitive(base))
        }
    }

    private fun AuditMutationEntity.currentDto(): AuditDto? = conflictJson
        ?.let { raw ->
            runCatching {
                json.decodeFromJsonElement(AuditDto.serializer(), json.parseToJsonElement(raw))
            }.getOrNull()
        }

    private suspend fun AuditMutationEntity.toConflict(): AuditConflict? {
        val server = currentDto() ?: return null
        val cached = dao.getById(auditId)
        val mineForm = runCatching {
            (json.parseToJsonElement(payload) as? JsonObject)?.get("formData") as? JsonObject
        }.getOrNull()
        val mine = AuditEntity(
            id = auditId,
            dealId = dealId,
            heatloadMode = cached?.heatloadMode,
            heatloadKw = cached?.heatloadKw,
            formData = mineForm?.toString(),
            createdAt = cached?.createdAt ?: server.createdAt,
            pendingSince = createdAt,
            updatedAt = baseUpdatedAt,
        )
        return AuditConflict(
            auditId = auditId,
            dealId = dealId,
            mine = mine.toVersionedDomain(),
            mineSavedAt = createdAt,
            baseUpdatedAt = baseUpdatedAt,
            server = server.toCachedEntity(dealId).toVersionedDomain(),
            serverUpdatedAt = server.updatedAt,
            detectedAt = conflictAt ?: createdAt,
        )
    }

    // ── Wersja rekordu ───────────────────────────────────────────────────────
    // `updatedAt` przepisujemy tutaj, a nie w `AuditMapper`, bo mapper jest
    // w tej chwili w rękach portu geometrii. Gdy mapper zacznie przenosić pole
    // sam, te dwie funkcje staną się no-opem — można je wtedy usunąć.

    private fun AuditDto.toCachedEntity(dealId: String): AuditEntity =
        toEntity(dealId).copy(updatedAt = updatedAt)

    private fun AuditEntity.toVersionedDomain(): Audit = toDomain().copy(updatedAt = updatedAt)

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

        /** Kod 409 „audyt zmieniono od `expectedUpdatedAt`" — patrz `inspections-error.ts`. */
        const val STALE_CODE = "AUDIT_STALE"
    }
}
