package com.ekotak.teamtalk.data.repository

import com.ekotak.teamtalk.data.files.MontazPhotoStore
import com.ekotak.teamtalk.data.local.dao.MontazDao
import com.ekotak.teamtalk.data.local.entity.MontazEntity
import com.ekotak.teamtalk.data.local.entity.MontazMutationEntity
import com.ekotak.teamtalk.data.local.entity.MontazMutationEntity.Companion.KIND_BRIEFING
import com.ekotak.teamtalk.data.local.entity.MontazMutationEntity.Companion.KIND_CREATE
import com.ekotak.teamtalk.data.local.entity.MontazMutationEntity.Companion.KIND_ISSUE
import com.ekotak.teamtalk.data.local.entity.MontazMutationEntity.Companion.KIND_PATCH
import com.ekotak.teamtalk.data.local.entity.MontazMutationEntity.Companion.KIND_PHOTO
import com.ekotak.teamtalk.data.local.entity.MontazMutationEntity.Companion.KIND_PROTOCOL
import com.ekotak.teamtalk.data.local.entity.MontazMutationEntity.Companion.KIND_STATUS
import com.ekotak.teamtalk.data.local.entity.MontazMutationEntity.Companion.LOCAL_ID_PREFIX
import com.ekotak.teamtalk.data.local.entity.MontazPackEntity
import com.ekotak.teamtalk.data.local.entity.MontazPhotoEntity
import com.ekotak.teamtalk.data.local.preferences.SessionPreferences
import com.ekotak.teamtalk.data.mapper.DEFAULT_MONTAZ_DAYS
import com.ekotak.teamtalk.data.mapper.isoNow
import com.ekotak.teamtalk.data.mapper.toDomain
import com.ekotak.teamtalk.data.mapper.toEntity
import com.ekotak.teamtalk.data.remote.api.TeamTalkApi
import com.ekotak.teamtalk.data.remote.dto.BriefingCreateRequest
import com.ekotak.teamtalk.data.remote.dto.JobStatusRequest
import com.ekotak.teamtalk.data.remote.dto.MontazCreateRequest
import com.ekotak.teamtalk.data.remote.dto.MontazIssueRequest
import com.ekotak.teamtalk.data.remote.dto.ProtocolSaveRequest
import com.ekotak.teamtalk.data.sync.MontazSyncScheduler
import com.ekotak.teamtalk.domain.model.BriefingAck
import com.ekotak.teamtalk.domain.model.MaterialStatus
import com.ekotak.teamtalk.domain.model.Montaz
import com.ekotak.teamtalk.domain.model.MontazAssignee
import com.ekotak.teamtalk.domain.model.MontazMaterial
import com.ekotak.teamtalk.domain.model.MontazPhoto
import com.ekotak.teamtalk.domain.model.MontazSnapshot
import com.ekotak.teamtalk.domain.model.MontazStatus
import com.ekotak.teamtalk.domain.repository.MontazPatch
import com.ekotak.teamtalk.domain.repository.MontazPhotoSaved
import com.ekotak.teamtalk.domain.repository.MontazRepository
import com.ekotak.teamtalk.domain.repository.MontazSaveResult
import com.ekotak.teamtalk.domain.repository.MontazSyncResult
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject

/**
 * Zakładka „Montaż": cztery odczyty (montaże, ekipy, materiał, zdjęcia)
 * i jedna wspólna kolejka pięciu rodzajów zapisu.
 *
 * Podział ról jest ten sam, co w zakładkach „Zamówienie" i „Pliki": cache trzyma
 * to, co powiedział serwer, a niewysłane decyzje nakładamy na niego przy
 * odczycie ([overlay]). Wyjątki są dwa i oba z tego samego powodu — serwer
 * o nich jeszcze nie wie, więc nakładka nie miałaby czego nakładać:
 *  • ETAP założony bez zasięgu jest wierszem cache'u (`local:…`),
 *  • ZDJĘCIE zrobione bez zasięgu też (`local:…` + kopia w `outbox`).
 */
class MontazRepositoryImpl @Inject constructor(
    private val api: TeamTalkApi,
    private val dao: MontazDao,
    private val photos: MontazPhotoStore,
    private val json: Json,
    private val syncScheduler: MontazSyncScheduler,
    private val sessionPreferences: SessionPreferences,
) : MontazRepository {

    // ── Odczyty ───────────────────────────────────────────────────────────────

    override suspend fun getMontaze(dealId: String): MontazSnapshot {
        val now = System.currentTimeMillis()
        var offline = false
        var error: String? = null

        coroutineScope {
            val montazeCall = async { runCatching { api.getMontaze(dealId) } }
            val crewsCall = async { runCatching { api.getMontazCrews() } }

            montazeCall.await()
                .onSuccess { rows ->
                    dao.replaceMontaze(
                        dealId = dealId,
                        // Rezerwacje terminu odsiewa KLIENT, tak samo jak
                        // `listDealInstallations` w panelu: to jeszcze nie
                        // montaż, tylko zaklepane okno z zakładki „Oferta".
                        rows = rows
                            .filter { it.status != MontazStatus.RESERVED.wire }
                            .map { it.toEntity(json, now) },
                    )
                }
                .onFailure { e -> if (e is IOException) offline = true else error = readError(e) }

            crewsCall.await()
                .onSuccess { rows -> dao.replaceCrews(rows.map { it.toEntity(now) }) }
                // Brak ekip nie psuje karty — obsadę da się złożyć osobami.
                .onFailure { e -> if (e is IOException) offline = true }
        }

        val queue = dao.getMutationsForDeal(dealId)
        return MontazSnapshot(
            montaze = dao.getMontaze(dealId)
                .map { it.toDomain(json) }
                .map { overlay(it, queue) },
            crews = dao.getCrews().map { it.toDomain() },
            fromCache = offline,
            error = error,
        )
    }

    /**
     * Zmiany czekające w kolejce nałożone na montaż z cache'u. Bez tego osoba
     * dopisana do obsady w piwnicy zniknęłaby przy pierwszym odświeżeniu karty,
     * a lider dopisałby ją drugi raz.
     */
    private fun overlay(montaz: Montaz, queue: List<MontazMutationEntity>): Montaz {
        val mine = queue.filter { it.installationId == montaz.id }
        if (mine.isEmpty()) return montaz

        val patch = mine.firstOrNull { it.kind == KIND_PATCH }?.let { parse(it.payload) }
        return montaz.copy(
            nodeIds = patch?.strings("nodeIds") ?: montaz.nodeIds,
            assignees = patch?.assignees() ?: montaz.assignees,
            crewId = if (patch != null && patch.containsKey("crewId")) {
                patch.text("crewId")
            } else {
                montaz.crewId
            },
            teamNote = if (patch != null && patch.containsKey("teamNote")) {
                patch.text("teamNote")
            } else {
                montaz.teamNote
            },
            // Odprawa czekająca w kolejce JESZCZE nie poszła — daty jej wysłania
            // nie udajemy, ale mówimy wprost, że montaż ma niewysłany zapis.
            pendingSince = mine.minOf { it.createdAt },
        )
    }

    override suspend fun getMaterials(installationId: String): List<MontazMaterial> {
        // Etap założony offline nie istnieje jeszcze na serwerze — nie ma kogo
        // pytać o jego listę wyjazdową.
        if (!installationId.startsWith(LOCAL_ID_PREFIX)) {
            runCatching { api.getMontazMaterials(installationId) }
                .onSuccess { rows ->
                    val now = System.currentTimeMillis()
                    dao.replaceMaterials(installationId, rows.map { it.toEntity(installationId, now) })
                }
        }

        val queued = queuedIssueIds(installationId)
        return dao.getMaterials(installationId).map { entity ->
            val row = entity.toDomain()
            if (row.id in queued && row.status == MaterialStatus.ACTIVE) {
                // Dla ekipy materiał JEST zabrany; magazyn dowie się w zasięgu.
                row.copy(status = MaterialStatus.DONE, pending = true)
            } else {
                row
            }
        }
    }

    override suspend fun getPhotos(installationId: String): List<MontazPhoto> {
        if (!installationId.startsWith(LOCAL_ID_PREFIX)) {
            runCatching { api.getMontazPhotos(installationId) }
                .onSuccess { rows ->
                    val now = System.currentTimeMillis()
                    dao.replacePhotos(installationId, rows.map { it.toEntity(now) })
                }
        }
        return dao.getPhotos(installationId).map { it.toDomain() }
    }

    // ── Zapisy ────────────────────────────────────────────────────────────────

    override suspend fun createMontaz(dealId: String, scheduledAt: String): MontazSaveResult = try {
        val created = api.createMontaz(MontazCreateRequest(dealId = dealId, scheduledAt = scheduledAt))
        dao.upsertMontaz(created.toEntity(json, System.currentTimeMillis()))
        MontazSaveResult.SENT
    } catch (_: IOException) {
        val localId = LOCAL_ID_PREFIX + UUID.randomUUID()
        val now = System.currentTimeMillis()
        // Etap od razu w cache — bez tego pasek montaży wyglądałby, jakby
        // dodanie nie zadziałało, i koordynator kliknąłby drugi raz.
        dao.upsertMontaz(
            MontazEntity(
                id = localId,
                dealId = dealId,
                scheduledAt = scheduledAt,
                status = MontazStatus.PLANNED.wire,
                difficulty = null,
                teamNote = null,
                nodeIds = emptyList(),
                crewId = null,
                assigneesJson = "[]",
                briefedAt = null,
                briefingMessageId = null,
                durationDays = DEFAULT_MONTAZ_DAYS,
                syncedAt = now,
            ),
        )
        enqueue(
            targetId = localId,
            kind = KIND_CREATE,
            installationId = localId,
            dealId = dealId,
            payload = buildJsonObject { put("scheduledAt", JsonPrimitive(scheduledAt)) },
            now = now,
        )
        MontazSaveResult.QUEUED
    }

    override suspend fun patchMontaz(
        dealId: String,
        id: String,
        patch: MontazPatch,
    ): MontazSaveResult {
        val body = patchBody(patch)
        if (body.isEmpty()) return MontazSaveResult.SENT

        // Etap żyjący jeszcze tylko w telefonie nie ma czego łatać na serwerze —
        // zmiany dopisujemy do kolejki i pójdą razem z jego założeniem.
        if (id.startsWith(LOCAL_ID_PREFIX)) {
            queuePatch(dealId, id, body)
            return MontazSaveResult.QUEUED
        }

        return try {
            val updated = api.updateMontaz(id, body)
            dao.upsertMontaz(updated.toEntity(json, System.currentTimeMillis()))
            dao.deleteMutation(id, KIND_PATCH)
            MontazSaveResult.SENT
        } catch (_: IOException) {
            queuePatch(dealId, id, body)
            MontazSaveResult.QUEUED
        }
    }

    /**
     * Kolejna zmiana tego samego montażu DOPISUJE się do czekającej, zamiast ją
     * zastąpić: zakres, obsada i uwaga to niezależne pola, a nadpisanie ciała
     * zgubiłoby osobę dopisaną minutę wcześniej.
     */
    private suspend fun queuePatch(dealId: String, id: String, body: JsonObject) {
        val waiting = dao.getMutationPayload(id, KIND_PATCH)
        val merged = if (waiting == null) {
            body
        } else {
            buildJsonObject {
                parse(waiting).forEach { (key, value) -> put(key, value) }
                body.forEach { (key, value) -> put(key, value) }
            }
        }
        enqueue(
            targetId = id,
            kind = KIND_PATCH,
            installationId = id,
            dealId = dealId,
            payload = merged,
            now = System.currentTimeMillis(),
        )
    }

    override suspend fun issueMaterials(
        dealId: String,
        installationId: String,
        reservationIds: List<String>,
    ): MontazSaveResult {
        if (reservationIds.isEmpty()) return MontazSaveResult.SENT

        if (!installationId.startsWith(LOCAL_ID_PREFIX)) {
            try {
                api.issueMontazMaterials(installationId, MontazIssueRequest(reservationIds))
                // Świeży stan magazynu zamiast zgadywania: wydanie mogło objąć
                // mniej linii, niż prosiliśmy (ktoś wydał je równolegle w hali).
                runCatching { api.getMontazMaterials(installationId) }.onSuccess { rows ->
                    val now = System.currentTimeMillis()
                    dao.replaceMaterials(installationId, rows.map { it.toEntity(installationId, now) })
                }
                dao.deleteMutation(installationId, KIND_ISSUE)
                return MontazSaveResult.SENT
            } catch (e: Exception) {
                // Odmowa (nie jesteś w obsadzie tego montażu) to nie brak sieci —
                // taki błąd leci dalej, zamiast wozić wydanie w kółko po kolejce.
                if (e !is IOException) throw e
            }
        }

        // Suma, nie podmiana: ekipa wydaje materiał partiami, a każda partia
        // musi dojechać do magazynu.
        val queued = queuedIssueIds(installationId) + reservationIds
        enqueue(
            targetId = installationId,
            kind = KIND_ISSUE,
            installationId = installationId,
            dealId = dealId,
            payload = buildJsonObject {
                put("reservationIds", buildJsonArray { queued.forEach { add(JsonPrimitive(it)) } })
            },
            now = System.currentTimeMillis(),
        )
        return MontazSaveResult.QUEUED
    }

    override suspend fun addPhoto(
        dealId: String,
        installationId: String,
        bytes: ByteArray,
        fileName: String,
    ): MontazPhotoSaved {
        if (!installationId.startsWith(LOCAL_ID_PREFIX)) {
            val sent = runCatching {
                api.uploadMontazPhoto(installationId, part(bytes, fileName))
            }.getOrNull()
            if (sent != null) {
                dao.upsertPhoto(sent.toEntity(System.currentTimeMillis()))
                return MontazPhotoSaved(sent.id, MontazSaveResult.SENT)
            }
        }

        val staged = photos.stage(bytes, fileName)
            ?: return MontazPhotoSaved("", MontazSaveResult.QUEUED)
        val localId = LOCAL_ID_PREFIX + UUID.randomUUID()
        val now = System.currentTimeMillis()
        dao.upsertPhoto(
            MontazPhotoEntity(
                id = localId,
                installationId = installationId,
                caption = null,
                createdAt = isoNow(now),
                localPath = staged.absolutePath,
                syncedAt = now,
            ),
        )
        enqueue(
            targetId = localId,
            kind = KIND_PHOTO,
            installationId = installationId,
            dealId = dealId,
            payload = buildJsonObject {
                put("path", JsonPrimitive(staged.absolutePath))
                put("name", JsonPrimitive(fileName))
            },
            now = now,
        )
        return MontazPhotoSaved(localId, MontazSaveResult.QUEUED)
    }

    override suspend fun sendBriefing(
        dealId: String,
        installationId: String,
        title: String,
        body: String,
        userIds: List<String>,
    ): MontazSaveResult {
        if (!installationId.startsWith(LOCAL_ID_PREFIX)) {
            try {
                val published = api.publishBriefing(
                    BriefingCreateRequest(
                        title = title,
                        body = body,
                        audienceUserIds = userIds,
                    ),
                )
                stampBriefing(installationId, published.id)
                dao.deleteMutation(installationId, KIND_BRIEFING)
                return MontazSaveResult.SENT
            } catch (e: Exception) {
                // 403 (bez `briefing.publish`) ma dojść do człowieka od razu —
                // kolejka niczego tu nie naprawi.
                if (e !is IOException) throw e
            }
        }

        enqueue(
            targetId = installationId,
            kind = KIND_BRIEFING,
            installationId = installationId,
            dealId = dealId,
            payload = buildJsonObject {
                put("title", JsonPrimitive(title))
                put("body", JsonPrimitive(body))
                put("userIds", buildJsonArray { userIds.forEach { add(JsonPrimitive(it)) } })
            },
            now = System.currentTimeMillis(),
        )
        return MontazSaveResult.QUEUED
    }

    /**
     * Ślad odprawy przy montażu. Gdyby ten zapis padł (np. brak
     * `installation.assign`), odprawa i tak POSZŁA — dlatego porażkę tu
     * połykamy, zamiast udawać, że nic nie wysłano.
     */
    private suspend fun stampBriefing(installationId: String, messageId: String) {
        runCatching {
            val updated = api.updateMontaz(
                installationId,
                buildJsonObject {
                    put("briefedAt", JsonPrimitive(isoNow(System.currentTimeMillis())))
                    put("briefingMessageId", JsonPrimitive(messageId))
                },
            )
            dao.upsertMontaz(updated.toEntity(json, System.currentTimeMillis()))
        }
    }

    override suspend fun briefingAck(messageId: String): BriefingAck? {
        val rows = runCatching { api.getBriefingReceipts(messageId) }.getOrNull() ?: return null
        return BriefingAck(acked = rows.count { it.ackAt != null }, total = rows.size)
    }

    // ── Lista pakowania (stan lokalny) ────────────────────────────────────────

    override suspend fun packedItems(installationId: String): Set<String> =
        dao.getPacked(installationId).map { it.itemKey }.toSet()

    override suspend fun setPacked(installationId: String, itemKey: String, checked: Boolean) {
        dao.upsertPacked(
            MontazPackEntity(
                installationId = installationId,
                itemKey = itemKey,
                checked = checked,
                changedAt = System.currentTimeMillis(),
            ),
        )
    }

    // ── Kolejka ───────────────────────────────────────────────────────────────

    private suspend fun enqueue(
        targetId: String,
        kind: String,
        installationId: String,
        dealId: String,
        payload: JsonObject,
        now: Long,
    ) {
        dao.upsertMutation(
            MontazMutationEntity(
                targetId = targetId,
                kind = kind,
                payload = payload.toString(),
                dealId = dealId,
                installationId = installationId,
                createdAt = now,
            ),
        )
        // Warunek sieci zdejmuje z nas odpytywanie — system sam obudzi
        // robotnika, gdy telefon wróci w zasięg.
        syncScheduler.scheduleSync()
    }

    /**
     * Opróżnianie kolejki, od najstarszego zapisu — a to ważne właśnie tutaj:
     * etap założony bez zasięgu musi pójść PRZED obsadą i odprawą, które przy
     * nim powstały. Odmowa serwera kończy wpis (ponowienie nic nie zmieni), ale
     * mówimy o niej człowiekowi: to jego praca na budowie przepadła.
     */
    override suspend fun syncPendingMutations(): MontazSyncResult {
        val queue = dao.getMutations()
        if (queue.isEmpty()) return MontazSyncResult.DONE

        for (row in queue) {
            val body = runCatching { json.parseToJsonElement(row.payload) as? JsonObject }.getOrNull()
            if (body == null) {
                // Nieczytelne ciało — ponowienie nic nie da, a wpis blokowałby
                // kolejkę w nieskończoność.
                dao.deleteMutation(row.targetId, row.kind)
                continue
            }

            try {
                when (row.kind) {
                    KIND_CREATE -> {
                        val created = api.createMontaz(
                            MontazCreateRequest(
                                dealId = row.dealId,
                                scheduledAt = body.text("scheduledAt")
                                    ?: throw IllegalStateException("scheduledAt"),
                            ),
                        )
                        dao.upsertMontaz(created.toEntity(json, System.currentTimeMillis()))
                        // Etap dostał id od serwera — atrapa znika, a wszystko,
                        // co pod nią czekało (obsada, materiał, zdjęcia), dostaje
                        // nowy cel. Inaczej te zapisy poszłyby w próżnię.
                        retarget(localId = row.targetId, serverId = created.id)
                        dao.deleteMontaz(row.targetId)
                    }

                    KIND_PATCH -> {
                        val updated = api.updateMontaz(row.targetId, body)
                        dao.upsertMontaz(updated.toEntity(json, System.currentTimeMillis()))
                    }

                    KIND_ISSUE -> {
                        api.issueMontazMaterials(
                            row.targetId,
                            MontazIssueRequest(body.strings("reservationIds")),
                        )
                        runCatching { api.getMontazMaterials(row.targetId) }.onSuccess { rows ->
                            val now = System.currentTimeMillis()
                            dao.replaceMaterials(
                                row.targetId,
                                rows.map { it.toEntity(row.targetId, now) },
                            )
                        }
                    }

                    KIND_PHOTO -> {
                        val path = body.text("path") ?: throw IllegalStateException("path")
                        val file = File(path)
                        if (!file.isFile) throw IllegalStateException("brak kopii zdjęcia")
                        val sent = api.uploadMontazPhoto(
                            row.installationId,
                            part(file.readBytes(), body.text("name") ?: file.name),
                        )
                        dao.upsertPhoto(sent.toEntity(System.currentTimeMillis()))
                        // Kadr dostał id od serwera — a mógł być już przypięty
                        // do pytania protokołu. Przepisujemy go wszędzie, gdzie
                        // go użyto, bo inaczej protokół wskazywałby na zdjęcie,
                        // którego już nie ma.
                        dao.retargetProtocolPhoto(row.installationId, row.targetId, sent.id)
                        dao.retargetProtocolPayload(row.installationId, row.targetId, sent.id)
                        // Atrapa i kopia z `outbox` znikają dopiero teraz —
                        // do tej chwili były jedynym śladem tego kadru.
                        dao.deletePhoto(row.targetId)
                        photos.dropStaged(path)
                    }

                    // ── moduł Montaż (kafelek „Montaże") ──
                    // Obie te zmiany powstają na budowie, w tej samej kolejce
                    // co reszta: start roboty przed protokołem, bo serwer
                    // przepuszcza `done` dopiero z `in_progress`.

                    KIND_STATUS -> {
                        val status = body.text("status") ?: throw IllegalStateException("status")
                        api.setJobStatus(row.targetId, JobStatusRequest(status))
                    }

                    KIND_PROTOCOL -> {
                        val form = body["formData"] as? JsonObject
                            ?: throw IllegalStateException("formData")
                        api.saveProtocol(
                            row.targetId,
                            ProtocolSaveRequest(
                                formData = form,
                                signature = body.text("signature"),
                            ),
                        )
                    }

                    KIND_BRIEFING -> {
                        val published = api.publishBriefing(
                            BriefingCreateRequest(
                                title = body.text("title").orEmpty(),
                                body = body.text("body").orEmpty(),
                                audienceUserIds = body.strings("userIds"),
                            ),
                        )
                        stampBriefing(row.installationId, published.id)
                    }

                    else -> Unit
                }
                dao.deleteMutation(row.targetId, row.kind)
            } catch (_: IOException) {
                // Sieć znowu padła — reszta kolejki poczeka na następny przebieg.
                return MontazSyncResult.RETRY
            } catch (e: Exception) {
                dao.deleteMutation(row.targetId, row.kind)
                if (row.kind == KIND_CREATE) dao.deleteMontaz(row.targetId)
                if (row.kind == KIND_PHOTO) {
                    dao.deletePhoto(row.targetId)
                    photos.dropStaged(body.text("path"))
                }
                sessionPreferences.saveSyncProblem(discardMessage(row.kind, e))
            }
        }
        return MontazSyncResult.DONE
    }

    /** Przepisanie kolejki i cache'u z lokalnego id etapu na id z serwera. */
    private suspend fun retarget(localId: String, serverId: String) {
        dao.retargetTarget(localId, serverId)
        dao.retargetInstallation(localId, serverId)
        dao.getPhotos(localId).forEach { photo ->
            dao.upsertPhoto(photo.copy(installationId = serverId))
        }
    }

    private fun discardMessage(kind: String, e: Exception): String {
        val what = when (kind) {
            KIND_CREATE -> "Nowy etap montażu nie powstał"
            KIND_PATCH -> "Zmiana montażu przepadła"
            KIND_ISSUE -> "Wydanie materiału na budowę przepadło"
            KIND_PHOTO -> "Zdjęcie z montażu nie zostało wysłane"
            KIND_STATUS -> "Zmiana stanu montażu nie dotarła do biura"
            KIND_PROTOCOL -> "Protokół odbioru nie został wysłany"
            else -> "Odprawa nie poszła do ekipy"
        }
        val code = (e as? HttpException)?.code()
        return when (code) {
            403 -> "$what — brak uprawnień."
            404 -> "$what — montaż zniknął z panelu."
            409, 422 -> "$what — serwer odrzucił zmianę. Sprawdź kartę w panelu."
            null -> "$what — nie udało się wysłać."
            else -> "$what — serwer go odrzucił (kod $code)."
        }
    }

    // ── Drobiazgi ─────────────────────────────────────────────────────────────

    /**
     * Ciało `PATCH`-a. `null` w [MontazPatch] znaczy „nie ruszaj", a jawne
     * wyczyszczenie ma własny znacznik — inaczej odpięcie ekipy wypadłoby
     * z ciała (wspólny `Json` ma `explicitNulls = false`) i nic by się nie stało.
     */
    private fun patchBody(patch: MontazPatch): JsonObject = buildJsonObject {
        patch.nodeIds?.let { ids ->
            put("nodeIds", buildJsonArray { ids.forEach { add(JsonPrimitive(it)) } })
        }
        patch.assignees?.let { list ->
            put(
                "assignees",
                buildJsonArray {
                    list.forEach { a ->
                        add(
                            buildJsonObject {
                                put("userId", JsonPrimitive(a.userId))
                                put("role", a.role?.let { JsonPrimitive(it) } ?: JsonNull)
                            },
                        )
                    }
                },
            )
        }
        when {
            patch.clearCrew -> put("crewId", JsonNull)
            patch.crewId != null -> put("crewId", JsonPrimitive(patch.crewId))
        }
        when {
            patch.clearNote -> put("teamNote", JsonNull)
            patch.teamNote != null -> put("teamNote", JsonPrimitive(patch.teamNote))
        }
        patch.difficulty?.let { put("difficulty", JsonPrimitive(it.wire)) }
    }

    private suspend fun queuedIssueIds(installationId: String): Set<String> =
        dao.getMutationPayload(installationId, KIND_ISSUE)
            ?.let { parse(it).strings("reservationIds").toSet() }
            .orEmpty()

    private fun part(bytes: ByteArray, fileName: String): MultipartBody.Part {
        // Kopia w cache, bo `RequestBody` czyta plik dopiero przy wysyłce.
        val tmp = File.createTempFile("montaz-", ".jpg")
        tmp.writeBytes(bytes)
        tmp.deleteOnExit()
        val media = if (fileName.endsWith(".png", true)) "image/png" else "image/jpeg"
        return MultipartBody.Part.createFormData(
            "file",
            fileName,
            tmp.asRequestBody(media.toMediaTypeOrNull()),
        )
    }

    private fun readError(e: Throwable): String = when ((e as? HttpException)?.code()) {
        403 -> "Nie masz dostępu do montaży (`installation.view`)."
        404 -> "Montaże tego deala zniknęły z panelu."
        else -> "Nie udało się wczytać montaży."
    }

    private fun parse(raw: String): JsonObject =
        runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
            ?: JsonObject(emptyMap())

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.strings(key: String): List<String> =
        (this[key] as? JsonArray)
            ?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }
            .orEmpty()

    private fun JsonObject.assignees(): List<MontazAssignee>? {
        val arr = this["assignees"] as? JsonArray ?: return null
        return arr.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val userId = o.text("userId") ?: return@mapNotNull null
            MontazAssignee(userId = userId, role = o.text("role"))
        }
    }
}
