package com.ekotak.teamtalk.data.repository

import com.ekotak.teamtalk.data.local.dao.SettlementDao
import com.ekotak.teamtalk.data.local.entity.DealSettlementEntity
import com.ekotak.teamtalk.data.local.entity.SettlementMutationEntity
import com.ekotak.teamtalk.data.local.entity.SettlementMutationEntity.Companion.KIND_APPROVE
import com.ekotak.teamtalk.data.local.entity.SettlementMutationEntity.Companion.KIND_REVOKE
import com.ekotak.teamtalk.data.local.preferences.SessionPreferences
import com.ekotak.teamtalk.data.remote.api.TeamTalkApi
import com.ekotak.teamtalk.data.remote.dto.DealSettlementDto
import com.ekotak.teamtalk.data.sync.SettlementSyncScheduler
import com.ekotak.teamtalk.domain.model.DealSettlement
import com.ekotak.teamtalk.domain.repository.SettlementRepository
import com.ekotak.teamtalk.domain.repository.SettlementSaveResult
import com.ekotak.teamtalk.domain.repository.SettlementSyncResult
import com.ekotak.teamtalk.domain.ufh.PointScheme
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Zakładka „Rozliczenie" — migawki zatwierdzeń z cache Room i kolejką offline.
 *
 * Zmian z kolejki NIE wpisujemy w cache jako fakt (tak samo jak w „Zamówieniu"):
 * cache trzyma to, co powiedział serwer, a niewysłane decyzje nakładamy na niego
 * przy odczycie ([overlay]). Dzięki temu odpowiedź serwera nigdy nie kasuje
 * decyzji, o której serwer jeszcze nie wie, a wysłanie kolejki nie wymaga
 * sprzątania podwójnych wierszy.
 *
 * Odmowa serwera (403 bez `financial.terms.manage`, 404) to nie brak sieci —
 * taki błąd leci dalej do ekranu, zamiast wozić decyzję w kółko po kolejce.
 */
@Singleton
class SettlementRepositoryImpl @Inject constructor(
    private val api: TeamTalkApi,
    private val dao: SettlementDao,
    private val schemesStore: FinancialSchemesStore,
    private val syncScheduler: SettlementSyncScheduler,
    private val sessionPreferences: SessionPreferences,
    private val json: Json,
) : SettlementRepository {

    override suspend fun getSettlements(dealId: String): List<DealSettlement> {
        try {
            val fresh = api.getDealSettlements(dealId)
            dao.replaceForDeal(dealId, fresh.map { it.toEntity(dealId) })
        } catch (_: IOException) {
            // Bez zasięgu zostaje ostatnie pobranie — rozliczenie ogląda się na
            // budowie, więc pusta lista byłaby tu kłamstwem.
        } catch (_: Exception) {
            // 403 = brak `financial.terms.view`. Zatwierdzeń nie zobaczymy,
            // rachunek na żywo zakładka pokaże i tak.
        }
        return overlay(
            cached = dao.getForDeal(dealId).map { it.toDomain() },
            queue = dao.getMutationsForDeal(dealId),
        )
    }

    override suspend fun getSchemes(): List<PointScheme> = schemesStore.schemes()

    override suspend fun approve(
        dealId: String,
        categoryId: String,
        totalPoints: Double,
        breakdown: JsonObject,
    ): SettlementSaveResult {
        val body = approveBody(totalPoints, breakdown)
        return try {
            val saved = api.approveDealSettlement(dealId, categoryId, body)
            dao.upsert(saved.toEntity(dealId))
            dao.deleteMutation(dealId, categoryId)
            SettlementSaveResult.SENT
        } catch (_: IOException) {
            enqueue(dealId, categoryId, KIND_APPROVE, body.toString())
            SettlementSaveResult.QUEUED
        }
    }

    override suspend fun revoke(dealId: String, categoryId: String): SettlementSaveResult = try {
        api.revokeDealSettlement(dealId, categoryId)
        dao.delete(dealId, categoryId)
        dao.deleteMutation(dealId, categoryId)
        SettlementSaveResult.SENT
    } catch (_: IOException) {
        enqueue(dealId, categoryId, KIND_REVOKE, "")
        SettlementSaveResult.QUEUED
    }

    private suspend fun enqueue(dealId: String, categoryId: String, kind: String, payload: String) {
        dao.upsertMutation(
            SettlementMutationEntity(
                dealId = dealId,
                categoryId = categoryId,
                kind = kind,
                payload = payload,
                createdAt = System.currentTimeMillis(),
            ),
        )
        // Warunek sieci zdejmuje z nas odpytywanie — system sam obudzi
        // robotnika, gdy telefon wróci w zasięg.
        syncScheduler.scheduleSync()
    }

    // ── Kolejka ───────────────────────────────────────────────────────────────

    /**
     * Opróżnianie kolejki, od najstarszej decyzji. Odmowa serwera kończy wpis
     * (ponowienie nic nie zmieni), ale mówimy o niej człowiekowi: to jego
     * decyzja o pieniądzach ekipy przepadła.
     */
    override suspend fun syncPendingMutations(): SettlementSyncResult {
        val queue = dao.getMutations()
        if (queue.isEmpty()) return SettlementSyncResult.DONE

        for (row in queue) {
            try {
                when (row.kind) {
                    KIND_APPROVE -> {
                        val body = runCatching {
                            json.parseToJsonElement(row.payload) as? JsonObject
                        }.getOrNull() ?: throw IllegalStateException("payload")
                        val saved = api.approveDealSettlement(row.dealId, row.categoryId, body)
                        dao.upsert(saved.toEntity(row.dealId))
                    }

                    KIND_REVOKE -> {
                        api.revokeDealSettlement(row.dealId, row.categoryId)
                        dao.delete(row.dealId, row.categoryId)
                    }

                    else -> Unit
                }
                dao.deleteMutation(row.dealId, row.categoryId)
            } catch (_: IOException) {
                // Sieć znowu padła — reszta kolejki poczeka na następny przebieg.
                return SettlementSyncResult.RETRY
            } catch (e: Exception) {
                dao.deleteMutation(row.dealId, row.categoryId)
                sessionPreferences.saveSyncProblem(discardMessage(row.kind, e))
            }
        }
        return SettlementSyncResult.DONE
    }

    /**
     * Migawki widziane przez zakładkę: cache serwera z nałożoną kolejką.
     * Zatwierdzenie z kolejki dokłada wiersz (z sumą punktów odczytaną z ciała
     * żądania), cofnięcie zabiera — bez tego przycisk zmieniałby stan dopiero
     * po powrocie zasięgu i wyglądałby na nieklikalny.
     */
    private fun overlay(
        cached: List<DealSettlement>,
        queue: List<SettlementMutationEntity>,
    ): List<DealSettlement> {
        if (queue.isEmpty()) return cached
        val byCategory = cached.associateByTo(LinkedHashMap()) { it.categoryId }
        for (row in queue) {
            when (row.kind) {
                KIND_REVOKE -> byCategory.remove(row.categoryId)
                KIND_APPROVE -> {
                    val body = runCatching {
                        json.parseToJsonElement(row.payload) as? JsonObject
                    }.getOrNull()
                    val points = body?.get("totalPoints")?.jsonPrimitive?.doubleOrNull ?: 0.0
                    val breakdown = body?.get("breakdown")?.toString().orEmpty()
                    byCategory[row.categoryId] = DealSettlement(
                        categoryId = row.categoryId,
                        totalPoints = points,
                        breakdownJson = breakdown,
                        // Autora podpisuje serwer przy wysyłce — zgadywanie go
                        // w telefonie pokazałoby w migawce nazwisko, którego
                        // w bazie nigdy nie będzie.
                        approvedById = null,
                        approvedAt = isoAt(row.createdAt),
                        pending = true,
                    )
                }
            }
        }
        return byCategory.values.toList()
    }

    private fun approveBody(totalPoints: Double, breakdown: JsonObject): JsonObject =
        buildJsonObject {
            put("totalPoints", JsonPrimitive(totalPoints))
            put("breakdown", breakdown)
        }

    private fun discardMessage(kind: String, e: Exception): String {
        val what = if (kind == KIND_REVOKE) {
            "Cofnięcie rozliczenia przepadło"
        } else {
            "Zatwierdzenie rozliczenia przepadło"
        }
        return when (val code = (e as? HttpException)?.code()) {
            403 -> "$what — rozliczenia zatwierdza zarząd (uprawnienie do Warunków finansowych)."
            404 -> "$what — deal albo instalacja zniknęły z panelu."
            409, 422 -> "$what — serwer odrzucił zmianę. Sprawdź kartę w panelu."
            null -> "$what — nie udało się wysłać."
            else -> "$what — serwer go odrzucił (kod $code)."
        }
    }

    private fun DealSettlementDto.toEntity(dealId: String) = DealSettlementEntity(
        dealId = this.dealId.ifBlank { dealId },
        categoryId = categoryId,
        totalPoints = totalPoints,
        breakdownJson = breakdown?.toString().orEmpty(),
        approvedById = approvedById,
        approvedAt = approvedAt,
        syncedAt = System.currentTimeMillis(),
    )

    private fun DealSettlementEntity.toDomain() = DealSettlement(
        categoryId = categoryId,
        totalPoints = totalPoints,
        breakdownJson = breakdownJson,
        approvedById = approvedById,
        approvedAt = approvedAt,
    )

    /** Czas decyzji w kształcie, w jakim API oddaje `approvedAt` (UTC, ISO-8601). */
    private fun isoAt(millis: Long): String = Instant.ofEpochMilli(millis).toString()
}
