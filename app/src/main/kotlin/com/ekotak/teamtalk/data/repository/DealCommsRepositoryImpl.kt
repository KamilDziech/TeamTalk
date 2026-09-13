package com.ekotak.teamtalk.data.repository

import com.ekotak.teamtalk.data.local.dao.DealCommsDao
import com.ekotak.teamtalk.data.local.entity.DealCallSummaryEntity
import com.ekotak.teamtalk.data.local.entity.DealCommMutationEntity
import com.ekotak.teamtalk.data.local.entity.DealCommMutationEntity.Companion.KIND_CALL
import com.ekotak.teamtalk.data.local.entity.DealCommMutationEntity.Companion.KIND_COMMENT
import com.ekotak.teamtalk.data.local.entity.DealCommMutationEntity.Companion.KIND_WHATSAPP
import com.ekotak.teamtalk.data.local.entity.DealCommentEntity
import com.ekotak.teamtalk.data.local.entity.DealWhatsappEntity
import com.ekotak.teamtalk.data.local.preferences.SessionPreferences
import com.ekotak.teamtalk.data.remote.api.TeamTalkApi
import com.ekotak.teamtalk.data.remote.dto.AddCommentRequest
import com.ekotak.teamtalk.data.remote.dto.CreateManualVoiceReportRequest
import com.ekotak.teamtalk.data.remote.dto.DiscussionCommentDto
import com.ekotak.teamtalk.data.remote.dto.SendWhatsappRequest
import com.ekotak.teamtalk.data.remote.dto.VoiceReportResponseDto
import com.ekotak.teamtalk.data.remote.dto.WhatsappMessageDto
import com.ekotak.teamtalk.data.sync.DealCommsSyncScheduler
import com.ekotak.teamtalk.domain.model.CommsSendResult
import com.ekotak.teamtalk.domain.model.CommsSyncResult
import com.ekotak.teamtalk.domain.model.DealCallSummary
import com.ekotak.teamtalk.domain.model.DealComment
import com.ekotak.teamtalk.domain.model.WhatsappDirection
import com.ekotak.teamtalk.domain.model.WhatsappMessage
import com.ekotak.teamtalk.domain.repository.DealCommsRepository
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Ile streszczeń rozmów ciągniemy — deal ich tyle nie ma, to zabezpieczenie. */
private const val CALL_SUMMARY_LIMIT = 200

/**
 * Zakładka „Komunikacja" — cache Room i kolejka offline dla trzech kanałów:
 * wątku wewnętrznego, WhatsAppa i streszczeń rozmów.
 *
 * Schemat jest wszędzie ten sam i celowo prosty:
 *  1. próbujemy sieci i PODMIENIAMY cache tego deala odpowiedzią serwera,
 *  2. czytamy cache,
 *  3. doklejamy to, co czeka w kolejce, jako wpisy `pending`.
 *
 * Kolejki NIE wpisujemy do cache'u jako faktu (tak samo jak w „Rozliczeniu"
 * i „Zamówieniu"): cache trzyma to, co powiedział serwer, więc jego odpowiedź
 * nigdy nie kasuje wiadomości, o której serwer jeszcze nie wie, a opróżnienie
 * kolejki nie wymaga sprzątania duplikatów.
 *
 * Odmowa serwera (403, 404, 422) to NIE brak sieci — leci dalej do ekranu.
 * Dotyczy to zwłaszcza WhatsAppa: poza oknem 24h od ostatniej wiadomości
 * klienta API odrzuca treść free-form i to jest reguła WhatsApp Business,
 * której powtarzanie w kółko z kolejki niczego by nie zmieniło.
 */
@Singleton
class DealCommsRepositoryImpl @Inject constructor(
    private val api: TeamTalkApi,
    private val dao: DealCommsDao,
    private val syncScheduler: DealCommsSyncScheduler,
    private val sessionPreferences: SessionPreferences,
    private val json: Json,
) : DealCommsRepository {

    // ── Komunikator wewnętrzny ────────────────────────────────────────────────

    override suspend fun getComments(dealId: String): List<DealComment> {
        try {
            val fresh = api.getDealDiscussion(dealId).comments
            dao.replaceComments(dealId, fresh.map { it.toEntity(dealId) })
        } catch (_: IOException) {
            // Bez zasięgu zostaje ostatni odczyt — pusty wątek byłby kłamstwem.
        } catch (_: Exception) {
            // 403/404 — karta i tak pokaże, co zdążyliśmy pobrać wcześniej.
        }

        val cached = dao.getComments(dealId).map { it.toDomain() }
        val queued = dao.getMutationsForDeal(dealId, KIND_COMMENT).map { row ->
            DealComment(
                id = row.localId,
                authorId = "",
                // Podpis wstawia serwer przy zapisie. Zgadywanie go tutaj
                // wpisałoby w dymek nazwisko, którego w bazie nigdy nie będzie.
                authorName = "Ja",
                body = row.payload.field("body"),
                createdAt = isoAt(row.createdAt),
                mine = true,
                pending = true,
            )
        }
        return cached + queued
    }

    override suspend fun addComment(
        dealId: String,
        body: String,
        mentions: List<String>,
    ): CommsSendResult = try {
        val saved = api.addDealDiscussionComment(
            dealId,
            AddCommentRequest(body = body, mentions = mentions),
        )
        dao.upsertComments(listOf(saved.toEntity(dealId)))
        CommsSendResult.SENT
    } catch (_: IOException) {
        enqueue(dealId, KIND_COMMENT, commentBody(body, mentions))
        CommsSendResult.QUEUED
    }

    override suspend fun markRead(dealId: String) {
        // Znacznik przeczytania to informacja dla licznika w skrzynce, a nie
        // decyzja handlowca — bez zasięgu przepada bez śladu i nic się nie dzieje.
        runCatching { api.markDealDiscussionRead(dealId) }
    }

    // ── WhatsApp ──────────────────────────────────────────────────────────────

    override suspend fun getWhatsapp(dealId: String): List<WhatsappMessage> {
        try {
            val fresh = api.getDealWhatsapp(dealId)
            dao.replaceWhatsapp(dealId, fresh.map { it.toEntity(dealId) })
        } catch (_: IOException) {
        } catch (_: Exception) {
        }

        val cached = dao.getWhatsapp(dealId).map { it.toDomain() }
        val queued = dao.getMutationsForDeal(dealId, KIND_WHATSAPP).map { row ->
            WhatsappMessage(
                id = row.localId,
                direction = WhatsappDirection.OUTBOUND,
                body = row.payload.field("body"),
                template = null,
                status = WhatsappMessage.STATUS_QUEUED_LOCAL,
                createdAt = isoAt(row.createdAt),
                pending = true,
            )
        }
        return cached + queued
    }

    override suspend fun sendWhatsapp(dealId: String, body: String): CommsSendResult = try {
        api.sendDealWhatsapp(dealId, SendWhatsappRequest(body)).close()
        // Odpowiedź zamykamy bez czytania: wątek i tak dociągamy w całości,
        // żeby zobaczyć status nadany przez serwer (bywa `pending_config`).
        runCatching { dao.replaceWhatsapp(dealId, api.getDealWhatsapp(dealId).map { it.toEntity(dealId) }) }
        CommsSendResult.SENT
    } catch (_: IOException) {
        enqueue(dealId, KIND_WHATSAPP, whatsappBody(body))
        CommsSendResult.QUEUED
    }

    // ── Telefon ───────────────────────────────────────────────────────────────

    override suspend fun getCallSummaries(dealId: String): List<DealCallSummary> {
        try {
            val fresh = api.getDealVoiceReports(dealId, CALL_SUMMARY_LIMIT)
            dao.replaceCallSummaries(dealId, fresh.map { it.toEntity(dealId) })
        } catch (_: IOException) {
        } catch (_: Exception) {
        }

        val cached = dao.getCallSummaries(dealId).map { it.toDomain() }
        val queued = dao.getMutationsForDeal(dealId, KIND_CALL).map { row ->
            DealCallSummary(
                id = row.localId,
                body = row.payload.field("text"),
                agreements = row.payload.field("agreements").ifBlank { null },
                nextStep = row.payload.field("nextStep").ifBlank { null },
                phoneNumber = row.payload.field("phoneNumber").ifBlank { null },
                direction = null,
                occurredAt = isoAt(row.createdAt),
                durationSec = null,
                hasRecording = false,
                manual = true,
                pending = true,
            )
        }
        // Najnowsze na górze — razem z kolejką, która jest zawsze najświeższa.
        return (queued + cached).sortedByDescending { it.occurredAt }
    }

    override suspend fun addCallSummary(
        dealId: String,
        clientId: String?,
        phoneNumber: String?,
        text: String,
        agreements: String?,
        nextStep: String?,
    ): CommsSendResult {
        val request = CreateManualVoiceReportRequest(
            clientId = clientId,
            dealId = dealId,
            phoneNumber = phoneNumber,
            text = text,
            agreements = agreements,
            nextStep = nextStep,
        )
        return try {
            val saved = api.createManualVoiceReport(request)
            dao.upsertCallSummaries(listOf(saved.toEntity(dealId)))
            CommsSendResult.SENT
        } catch (_: IOException) {
            enqueue(dealId, KIND_CALL, json.encodeToString(CreateManualVoiceReportRequest.serializer(), request))
            CommsSendResult.QUEUED
        }
    }

    // ── Kolejka ───────────────────────────────────────────────────────────────

    private suspend fun enqueue(dealId: String, kind: String, payload: String) {
        dao.upsertMutation(
            DealCommMutationEntity(
                localId = DealCommMutationEntity.newLocalId(),
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

    /**
     * Opróżnianie kolejki, od najstarszego wpisu. Kolejność ma tu znaczenie:
     * trzy wiadomości napisane pod rząd mają dojść w kolejności pisania, więc
     * pierwszy brak sieci przerywa przebieg zamiast przeskakiwać dalej.
     *
     * Odmowa serwera kończy wpis (ponowienie nic nie zmieni), ale mówimy o niej
     * człowiekowi: widział swoją wiadomość na ekranie, więc jej cichy zanik
     * byłby najgorszym możliwym zachowaniem.
     */
    override suspend fun syncPendingMutations(): CommsSyncResult {
        val queue = dao.getMutations()
        if (queue.isEmpty()) return CommsSyncResult.DONE

        for (row in queue) {
            try {
                when (row.kind) {
                    KIND_COMMENT -> {
                        val saved = api.addDealDiscussionComment(
                            row.dealId,
                            AddCommentRequest(
                                body = row.payload.field("body"),
                                mentions = row.payload.mentions(),
                            ),
                        )
                        dao.upsertComments(listOf(saved.toEntity(row.dealId)))
                    }

                    KIND_WHATSAPP -> {
                        api.sendDealWhatsapp(
                            row.dealId,
                            SendWhatsappRequest(row.payload.field("body")),
                        ).close()
                    }

                    KIND_CALL -> {
                        val request = json.decodeFromString(CreateManualVoiceReportRequest.serializer(), row.payload)
                        val saved = api.createManualVoiceReport(request)
                        dao.upsertCallSummaries(listOf(saved.toEntity(row.dealId)))
                    }

                    else -> Unit
                }
                dao.deleteMutation(row.localId)
            } catch (_: IOException) {
                // Sieć znowu padła — reszta kolejki poczeka na kolejne obudzenie.
                return CommsSyncResult.RETRY
            } catch (e: Exception) {
                dao.deleteMutation(row.localId)
                sessionPreferences.saveSyncProblem(discardMessage(row.kind, e))
            }
        }
        return CommsSyncResult.DONE
    }

    // ── Ciała żądań i odczyt pól z kolejki ────────────────────────────────────

    private fun commentBody(body: String, mentions: List<String>): String =
        json.encodeToString(AddCommentRequest.serializer(), AddCommentRequest(body = body, mentions = mentions))

    private fun whatsappBody(body: String): String = json.encodeToString(SendWhatsappRequest.serializer(), SendWhatsappRequest(body))

    /**
     * Pole tekstowe z zakolejkowanego ciała żądania. Czytamy je z powrotem po
     * to, żeby narysować wiadomość w wątku — bez tego wpis z kolejki byłby
     * pustym dymkiem „w kolejce", a człowiek nie wiedziałby, co napisał.
     */
    private fun String.field(name: String): String = runCatching {
        (json.parseToJsonElement(this) as? JsonObject)
            ?.get(name)
            ?.jsonPrimitive
            ?.contentOrNull
            .orEmpty()
    }.getOrDefault("")

    private fun String.mentions(): List<String> = runCatching {
        json.decodeFromString(AddCommentRequest.serializer(), this).mentions
    }.getOrDefault(emptyList())

    private fun discardMessage(kind: String, e: Exception): String {
        val what = when (kind) {
            KIND_WHATSAPP -> "Wiadomość WhatsApp przepadła"
            KIND_CALL -> "Streszczenie rozmowy przepadło"
            else -> "Wiadomość do zespołu przepadła"
        }
        return when (val code = (e as? HttpException)?.code()) {
            403 -> "$what — brak uprawnień do tego kanału."
            404 -> "$what — deal zniknął z panelu."
            409, 422 -> if (kind == KIND_WHATSAPP) {
                "$what — minęło okno 24h od ostatniej wiadomości klienta. " +
                    "Poza nim WhatsApp puszcza wyłącznie zatwierdzony szablon."
            } else {
                "$what — serwer odrzucił zapis. Sprawdź kartę w panelu."
            }
            null -> "$what — nie udało się wysłać."
            else -> "$what — serwer go odrzucił (kod $code)."
        }
    }

    // ── Mapowanie ─────────────────────────────────────────────────────────────

    private fun DiscussionCommentDto.toEntity(dealId: String) = DealCommentEntity(
        id = id,
        dealId = dealId,
        authorId = authorId,
        authorName = authorName,
        body = body,
        createdAt = createdAt,
        mine = mine,
        syncedAt = System.currentTimeMillis(),
    )

    private fun DealCommentEntity.toDomain() = DealComment(
        id = id,
        authorId = authorId,
        authorName = authorName,
        body = body,
        createdAt = createdAt,
        mine = mine,
    )

    private fun WhatsappMessageDto.toEntity(dealId: String) = DealWhatsappEntity(
        id = id,
        dealId = this.dealId ?: dealId,
        direction = direction,
        body = body,
        template = template,
        status = status,
        createdAt = createdAt,
        syncedAt = System.currentTimeMillis(),
    )

    private fun DealWhatsappEntity.toDomain() = WhatsappMessage(
        id = id,
        direction = WhatsappDirection.fromWire(direction),
        body = body,
        template = template,
        status = status,
        createdAt = createdAt,
    )

    /**
     * Streszczenie z odpowiedzi API. Przebieg rozmowy bierzemy z `text`, a gdy
     * go nie ma — z transkrypcji nagrania: notatka dyktowana w TeamTalku ma
     * treść dopiero po przepisaniu jej przez serwer.
     */
    private fun VoiceReportResponseDto.toEntity(dealId: String) = DealCallSummaryEntity(
        id = id,
        dealId = this.dealId ?: dealId,
        body = (text ?: transcript).orEmpty().trim(),
        agreements = agreements?.trim()?.ifBlank { null },
        nextStep = nextStep?.trim()?.ifBlank { null },
        phoneNumber = phoneNumber,
        direction = direction,
        occurredAt = occurredAt ?: createdAt,
        durationSec = durationSec,
        hasRecording = recordingKey != null,
        manual = source == SOURCE_MANUAL,
        syncedAt = System.currentTimeMillis(),
    )

    private fun DealCallSummaryEntity.toDomain() = DealCallSummary(
        id = id,
        body = body,
        agreements = agreements,
        nextStep = nextStep,
        phoneNumber = phoneNumber,
        direction = direction,
        occurredAt = occurredAt,
        durationSec = durationSec,
        hasRecording = hasRecording,
        manual = manual,
    )

    /** Czas w kształcie, w jakim API oddaje daty (UTC, ISO-8601). */
    private fun isoAt(millis: Long): String = Instant.ofEpochMilli(millis).toString()

    private companion object {
        /** `source` streszczenia dopisanego z ręki, nie z nagrania. */
        const val SOURCE_MANUAL = "manual"
    }
}
