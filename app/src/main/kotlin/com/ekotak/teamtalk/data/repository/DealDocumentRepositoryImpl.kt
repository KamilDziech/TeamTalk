package com.ekotak.teamtalk.data.repository

import com.ekotak.teamtalk.data.files.DocumentFileStore
import com.ekotak.teamtalk.data.local.dao.DocumentDao
import com.ekotak.teamtalk.data.local.entity.DealDocumentEntity
import com.ekotak.teamtalk.data.local.entity.DocumentMutationEntity
import com.ekotak.teamtalk.data.local.entity.DocumentMutationEntity.Companion.KIND_CATEGORY
import com.ekotak.teamtalk.data.local.entity.DocumentMutationEntity.Companion.KIND_DELETE
import com.ekotak.teamtalk.data.local.entity.DocumentMutationEntity.Companion.KIND_PLAN
import com.ekotak.teamtalk.data.local.entity.DocumentMutationEntity.Companion.KIND_UPLOAD
import com.ekotak.teamtalk.data.mapper.isoNow
import com.ekotak.teamtalk.data.mapper.toDomain
import com.ekotak.teamtalk.data.mapper.toEntity
import com.ekotak.teamtalk.data.remote.api.TeamTalkApi
import com.ekotak.teamtalk.data.sync.DocumentSyncScheduler
import com.ekotak.teamtalk.domain.model.DealDocument
import com.ekotak.teamtalk.domain.model.DocumentCategory
import com.ekotak.teamtalk.domain.model.LOCAL_DOCUMENT_PREFIX
import com.ekotak.teamtalk.domain.repository.DealDocumentRepository
import com.ekotak.teamtalk.domain.repository.DocumentSyncRejection
import com.ekotak.teamtalk.domain.repository.DocumentSyncResult
import com.ekotak.teamtalk.domain.repository.DocumentsSnapshot
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject

/**
 * Zakładka „Pliki": cache metadanych, kolejka niewysłanych decyzji i kopie
 * treści czekających na wysyłkę.
 *
 * Podział ról jest ten sam co w zakładce „Zamówienie": cache trzyma to, co
 * powiedział serwer, a niewysłane decyzje nakładamy na niego przy odczycie
 * ([overlay]). Wyjątkiem są PLIKI WGRANE OFFLINE — te są wierszami cache'u
 * (`local-…`, `pending`), bo inaczej zdjęcie zrobione w kotłowni nie miałoby
 * gdzie zaistnieć: serwer o nim nie wie, a nakładka nie ma czego nakładać.
 */
class DealDocumentRepositoryImpl @Inject constructor(
    private val api: TeamTalkApi,
    private val dao: DocumentDao,
    private val files: DocumentFileStore,
    private val json: Json,
    private val syncScheduler: DocumentSyncScheduler,
) : DealDocumentRepository {

    override suspend fun getDocuments(dealId: String): DocumentsSnapshot {
        var offline = false
        var error: String? = null

        runCatching { api.getDealDocuments(dealId) }
            .onSuccess { rows ->
                val now = System.currentTimeMillis()
                dao.replaceDocuments(dealId, rows.map { it.toEntity(dealId, now) })
            }
            .onFailure { e ->
                if (e is IOException) offline = true else error = readError(e)
            }

        val queue = dao.getMutationsForDeal(dealId)
        val documents = dao.getDocuments(dealId)
            .map { it.toDomain(json) }
            .mapNotNull { overlay(it, queue) }
        return DocumentsSnapshot(documents = documents, offline = offline, error = error)
    }

    /**
     * Nakłada kolejkę na wiersz z cache'u. `null` = plik usunięty bez zasięgu —
     * znika z karty od razu, bo tego właśnie chciał człowiek, a serwer dowie się
     * o tym przy najbliższej łączności.
     */
    private fun overlay(
        document: DealDocument,
        queue: List<DocumentMutationEntity>,
    ): DealDocument? {
        val mine = queue.filter { it.targetId == document.id }
        if (mine.any { it.kind == KIND_DELETE }) return null

        var result = document
        mine.firstOrNull { it.kind == KIND_CATEGORY }?.let { mutation ->
            val wire = payload(mutation)["category"]?.jsonPrimitive?.contentOrNull
            result = result.copy(category = DocumentCategory.fromWire(wire), pending = true)
        }
        if (mine.any { it.kind == KIND_PLAN || it.kind == KIND_UPLOAD }) {
            result = result.copy(pending = true)
        }
        return result
    }

    override suspend fun upload(
        dealId: String,
        name: String,
        contentType: String,
        category: DocumentCategory?,
        bytes: ByteArray,
    ): DealDocument? {
        val staged = files.stage(bytes, name) ?: return null
        val id = "$LOCAL_DOCUMENT_PREFIX${UUID.randomUUID()}"
        val entity = DealDocumentEntity(
            id = id,
            dealId = dealId,
            name = name,
            size = bytes.size.toLong(),
            contentType = contentType,
            // „Wykryj sekcję" rozstrzyga serwer — do czasu wysyłki plik leży
            // w „Pozostałe" i po wysłaniu sam przeskakuje do właściwej sekcji.
            category = (category ?: DocumentCategory.INNE).wire,
            planDataJson = null,
            createdAt = isoNow(),
            pending = true,
            localPath = staged.absolutePath,
            syncedAt = System.currentTimeMillis(),
        )
        dao.upsert(entity)
        dao.upsertMutation(
            DocumentMutationEntity(
                targetId = id,
                kind = KIND_UPLOAD,
                dealId = dealId,
                payload = buildJsonObject {
                    put("auto", JsonPrimitive(category == null))
                }.toString(),
                createdAt = System.currentTimeMillis(),
            ),
        )
        syncScheduler.scheduleSync()
        return entity.toDomain(json)
    }

    override suspend fun setCategory(document: DealDocument, category: DocumentCategory) {
        if (document.category == category) return
        // Plik jeszcze niewysłany: sekcję zna wyłącznie wiersz kolejki, więc
        // poprawiamy jego, zamiast dokładać zmianę dla id, którego nie ma.
        if (document.id.startsWith(LOCAL_DOCUMENT_PREFIX)) {
            val entity = dao.getDocument(document.id) ?: return
            dao.upsert(entity.copy(category = category.wire))
            // Sekcja wybrana ręcznie wygrywa z „wykryj automatycznie".
            dao.upsertMutation(
                DocumentMutationEntity(
                    targetId = document.id,
                    kind = KIND_UPLOAD,
                    dealId = document.dealId,
                    payload = buildJsonObject { put("auto", JsonNull) }.toString(),
                    createdAt = System.currentTimeMillis(),
                ),
            )
            return
        }
        dao.upsertMutation(
            DocumentMutationEntity(
                targetId = document.id,
                kind = KIND_CATEGORY,
                dealId = document.dealId,
                payload = buildJsonObject {
                    put("category", JsonPrimitive(category.wire))
                }.toString(),
                createdAt = System.currentTimeMillis(),
            ),
        )
        syncScheduler.scheduleSync()
    }

    override suspend fun setPlanData(document: DealDocument, planData: JsonObject?) {
        val entity = dao.getDocument(document.id) ?: return
        // Przygotowanie widać na karcie od razu — także wtedy, gdy rysowano je
        // bez zasięgu; wysyłka i tak idzie z tego samego zapisu.
        dao.upsert(entity.copy(planDataJson = planData?.toString()))
        if (document.id.startsWith(LOCAL_DOCUMENT_PREFIX)) {
            // Plik nie ma jeszcze id na serwerze — przygotowanie poleci zaraz
            // po jego wgraniu, prosto z wiersza cache'u.
            syncScheduler.scheduleSync()
            return
        }
        dao.upsertMutation(
            DocumentMutationEntity(
                targetId = document.id,
                kind = KIND_PLAN,
                dealId = document.dealId,
                payload = buildJsonObject {
                    put("planData", planData ?: JsonNull)
                }.toString(),
                createdAt = System.currentTimeMillis(),
            ),
        )
        syncScheduler.scheduleSync()
    }

    override suspend fun delete(document: DealDocument) {
        if (document.id.startsWith(LOCAL_DOCUMENT_PREFIX)) {
            // Plik nigdy nie wyszedł z telefonu — kasujemy go w całości,
            // razem z kopią treści, zamiast prosić serwer o nieistniejące id.
            files.dropStaged(document.localPath)
            dao.deleteMutationsFor(document.id)
            dao.delete(document.id)
            return
        }
        dao.upsertMutation(
            DocumentMutationEntity(
                targetId = document.id,
                kind = KIND_DELETE,
                dealId = document.dealId,
                payload = "{}",
                createdAt = System.currentTimeMillis(),
            ),
        )
        syncScheduler.scheduleSync()
    }

    /**
     * Opróżnia kolejkę, od najstarszej decyzji.
     *
     * Brak zasięgu przerywa przebieg — reszta poczeka na kolejne obudzenie.
     * Odmowa serwera (4xx) zdejmuje wpis, bo wożenie go w kółko niczego nie
     * naprawi, ale wraca w [DocumentSyncResult.rejected]: człowiek widział plik
     * na karcie i musi się dowiedzieć, że jednak go tam nie ma.
     */
    override suspend fun syncPendingMutations(): DocumentSyncResult {
        var sent = 0
        val rejected = mutableListOf<DocumentSyncRejection>()
        for (mutation in dao.getMutations()) {
            try {
                when (mutation.kind) {
                    KIND_UPLOAD -> flushUpload(mutation)
                    KIND_CATEGORY -> {
                        val wire = payload(mutation)["category"]?.jsonPrimitive?.contentOrNull
                        if (wire != null) {
                            val row = api.setDocumentCategory(
                                id = mutation.targetId,
                                body = buildJsonObject {
                                    put("category", JsonPrimitive(wire))
                                },
                            )
                            dao.upsert(
                                row.toEntity(mutation.dealId, System.currentTimeMillis()),
                            )
                        }
                        dao.deleteMutation(mutation.targetId, KIND_CATEGORY)
                    }
                    KIND_PLAN -> {
                        val row = api.setDocumentPlanData(
                            id = mutation.targetId,
                            body = buildJsonObject {
                                put("planData", payload(mutation)["planData"] ?: JsonNull)
                            },
                        )
                        dao.upsert(row.toEntity(mutation.dealId, System.currentTimeMillis()))
                        dao.deleteMutation(mutation.targetId, KIND_PLAN)
                    }
                    KIND_DELETE -> {
                        api.deleteDocument(mutation.targetId)
                        finishDelete(mutation.targetId)
                    }
                    else -> dao.deleteMutation(mutation.targetId, mutation.kind)
                }
                sent++
            } catch (_: IOException) {
                // Nadal bez zasięgu — reszta kolejki poczeka na następny raz.
                return DocumentSyncResult(sent = sent, rejected = rejected, incomplete = true)
            } catch (e: HttpException) {
                // Plik skasowany w panelu w międzyczasie: nasze „usuń" właśnie
                // się spełniło, więc to nie jest odmowa, o której trzeba mówić.
                if (mutation.kind == KIND_DELETE && e.code() == 404) {
                    finishDelete(mutation.targetId)
                    sent++
                    continue
                }
                rejected += DocumentSyncRejection(
                    label = rejectionLabel(mutation),
                    reason = e.serverMessage(),
                )
                if (mutation.kind == KIND_UPLOAD) {
                    // Serwer odmówił przyjęcia pliku (za duży, zły typ, brak
                    // uprawnienia) — wiersz musi zniknąć z karty, bo inaczej
                    // wyglądałby na wgrany i nikt by go nie wgrał drugi raz.
                    val entity = dao.getDocument(mutation.targetId)
                    files.dropStaged(entity?.localPath)
                    dao.delete(mutation.targetId)
                }
                dao.deleteMutation(mutation.targetId, mutation.kind)
            }
        }
        return DocumentSyncResult(sent = sent, rejected = rejected)
    }

    /**
     * Wgrywa plik z kolejki i podmienia jego lokalny wiersz na ten z serwera.
     * Nazwa i sekcja idą z WIERSZA, nie z wpisu kolejki: przełożenie
     * zakolejkowanego zdjęcia do innej sekcji zmienia właśnie wiersz.
     */
    private suspend fun flushUpload(mutation: DocumentMutationEntity) {
        val entity = dao.getDocument(mutation.targetId)
        if (entity?.localPath == null) {
            // Wiersz zniknął albo nigdy nie miał kopii treści — nie ma czego wysłać.
            dao.deleteMutation(mutation.targetId, KIND_UPLOAD)
            return
        }
        val file = File(entity.localPath)
        if (!file.isFile) {
            dao.deleteMutation(mutation.targetId, KIND_UPLOAD)
            dao.delete(entity.id)
            return
        }

        val media = entity.contentType.toMediaTypeOrNull()
        val part = MultipartBody.Part.createFormData(
            "file",
            entity.name,
            file.asRequestBody(media),
        )
        val auto = payload(mutation)["auto"]?.jsonPrimitive?.booleanOrNull == true
        val uploaded = api.uploadDealDocument(
            dealId = entity.dealId,
            file = part,
            category = if (auto) null else entity.category.toRequestBody(TEXT_PLAIN),
        )

        // Przygotowanie rzutu zrobione, zanim plik w ogóle wyszedł z telefonu,
        // wysyłamy zaraz po nim — dopiero teraz jest id, pod które ma trafić.
        val withPrep = entity.planDataJson
            ?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }
            ?.let { prep ->
                runCatching {
                    api.setDocumentPlanData(
                        id = uploaded.id,
                        body = buildJsonObject { put("planData", prep) },
                    )
                }.getOrNull()
            }

        dao.deleteMutation(mutation.targetId, KIND_UPLOAD)
        dao.delete(entity.id)
        files.dropStaged(entity.localPath)
        dao.upsert(
            (withPrep ?: uploaded).toEntity(entity.dealId, System.currentTimeMillis()),
        )
    }

    private suspend fun finishDelete(documentId: String) {
        dao.deleteMutation(documentId, KIND_DELETE)
        dao.delete(documentId)
        files.forget(documentId)
    }

    private fun payload(mutation: DocumentMutationEntity): JsonObject =
        runCatching { json.parseToJsonElement(mutation.payload).jsonObject }
            .getOrElse { JsonObject(emptyMap()) }

    private fun rejectionLabel(mutation: DocumentMutationEntity): String = when (mutation.kind) {
        KIND_UPLOAD -> "Nie udało się wgrać pliku"
        KIND_DELETE -> "Nie udało się usunąć pliku"
        KIND_PLAN -> "Nie udało się zapisać przygotowania rzutu"
        else -> "Nie udało się przenieść pliku"
    }

    /** Komunikat serwera z ciała błędu; bez niego zostaje sam kod odpowiedzi. */
    private fun HttpException.serverMessage(): String? {
        val raw = runCatching { response()?.errorBody()?.string() }.getOrNull() ?: return null
        return runCatching {
            json.parseToJsonElement(raw).jsonObject["message"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()
    }

    private fun readError(e: Throwable): String = when {
        e is HttpException && e.code() == 403 -> "Brak uprawnienia do plików tego deala."
        e is HttpException -> e.serverMessage() ?: "Nie udało się wczytać plików (${e.code()})."
        else -> "Nie udało się wczytać plików."
    }

    private companion object {
        val TEXT_PLAIN = "text/plain".toMediaTypeOrNull()
    }
}
