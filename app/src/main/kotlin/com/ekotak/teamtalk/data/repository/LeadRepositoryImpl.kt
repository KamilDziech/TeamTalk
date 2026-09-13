package com.ekotak.teamtalk.data.repository

import com.ekotak.teamtalk.data.local.dao.LeadOutboxDao
import com.ekotak.teamtalk.data.local.entity.LeadOutboxEntity
import com.ekotak.teamtalk.data.remote.api.TeamTalkApi
import com.ekotak.teamtalk.data.remote.dto.AppLeadBuildingDto
import com.ekotak.teamtalk.data.remote.dto.AppLeadFloorHeatingDto
import com.ekotak.teamtalk.data.remote.dto.AppLeadRequestDto
import com.ekotak.teamtalk.data.sync.LeadSyncScheduler
import com.ekotak.teamtalk.domain.model.FloorHeatingVariant
import com.ekotak.teamtalk.domain.model.LeadChannel
import com.ekotak.teamtalk.domain.model.LeadDraft
import com.ekotak.teamtalk.domain.model.LeadEvent
import com.ekotak.teamtalk.domain.model.LeadOccupancy
import com.ekotak.teamtalk.domain.model.LeadOrigin
import com.ekotak.teamtalk.domain.model.LeadSubmitResult
import com.ekotak.teamtalk.domain.repository.LeadRepository
import com.ekotak.teamtalk.domain.repository.LeadSyncResult
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import retrofit2.HttpException
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Odmowa serwera z jego komunikatem — kreator pokazuje ją nad przyciskiem zapisu. */
class LeadRejectedException(message: String) : Exception(message)

/**
 * Zapis leada z kreatora. Bez zasięgu lead ląduje w `lead_outbox` i wysyła go
 * [com.ekotak.teamtalk.worker.LeadSyncWorker]. `clientRef` nadajemy RAZ, przy
 * pierwszej próbie, więc ponowienie po zerwanym połączeniu (serwer zapisał,
 * odpowiedź nie doszła) nie zakłada drugiej karty — board360 zwraca istniejący deal.
 */
@Singleton
class LeadRepositoryImpl @Inject constructor(
    private val api: TeamTalkApi,
    private val dao: LeadOutboxDao,
    /** `Json` z `NetworkModule` — `explicitNulls = false`, jak w Retroficie. */
    private val json: Json,
    private val syncScheduler: LeadSyncScheduler,
) : LeadRepository {

    override suspend fun submit(draft: LeadDraft): LeadSubmitResult {
        val body = draft.toRequest(clientRef = UUID.randomUUID().toString())
        return try {
            LeadSubmitResult.Created(api.submitAppLead(body).dealId)
        } catch (e: HttpException) {
            // 5xx to zwykle kilkusekundowe okno restartu przy deployu — rozmowy
            // z klientem nie powtórzymy, więc lead czeka, zamiast przepadać.
            if (e.code() >= 500) enqueue(body) else throw LeadRejectedException(e.readableMessage())
        } catch (_: IOException) {
            enqueue(body)
        }
    }

    private suspend fun enqueue(body: AppLeadRequestDto): LeadSubmitResult {
        dao.upsert(
            LeadOutboxEntity(
                clientRef = body.clientRef,
                payload = json.encodeToString(AppLeadRequestDto.serializer(), body),
                fullName = body.fullName,
                createdAt = System.currentTimeMillis(),
            ),
        )
        syncScheduler.scheduleSync()
        return LeadSubmitResult.Queued
    }

    override fun observePendingCount(): Flow<Int> = dao.observeCount()

    override suspend fun getEvents(): List<LeadEvent> =
        runCatching { api.getLeadEvents() }
            .getOrDefault(emptyList())
            .map { LeadEvent(id = it.id, name = it.name, eventDate = it.eventDate) }

    override suspend fun syncPending(): LeadSyncResult {
        var sent = 0
        val rejected = mutableListOf<Pair<String, String>>()
        for (row in dao.getAll()) {
            try {
                val body = json.decodeFromString(AppLeadRequestDto.serializer(), row.payload)
                api.submitAppLead(body)
                dao.delete(row.clientRef)
                sent++
            } catch (_: IOException) {
                return LeadSyncResult(sent = sent, rejected = rejected, incomplete = true)
            } catch (e: HttpException) {
                // 5xx to zwykle okno restartu przy deployu (502 z Caddy) — ponowimy.
                if (e.code() >= 500) {
                    return LeadSyncResult(sent = sent, rejected = rejected, incomplete = true)
                }
                // Odmowa merytoryczna nie minie od ponawiania; lead znika z kolejki,
                // a człowiek dostaje powiadomienie z nazwiskiem, żeby wpisał go ponownie.
                rejected += row.fullName to e.readableMessage()
                dao.delete(row.clientRef)
            }
        }
        return LeadSyncResult(sent = sent, rejected = rejected)
    }

    private fun HttpException.readableMessage(): String {
        val raw = runCatching { response()?.errorBody()?.string() }.getOrNull()
        val message = raw?.let {
            runCatching {
                // NestJS oddaje `message` jako tekst albo listę błędów walidacji.
                val field = json.parseToJsonElement(it).jsonObject["message"]
                field?.jsonPrimitive?.contentOrNull
                    ?: field?.jsonArray?.joinToString("; ") { el -> el.jsonPrimitive.content }
            }.getOrNull()
        }
        return when (code()) {
            401 -> "Sesja wygasła — zaloguj się ponownie."
            403 -> "Brak uprawnienia do dodawania leadów."
            else -> message ?: "Serwer nie przyjął leada (kod ${code()})."
        }
    }
}

/** Odpowiedzi kreatora → ciało `POST /api/intake/app/lead`. Pola spoza gałęzi zostają puste. */
internal fun LeadDraft.toRequest(clientRef: String): AppLeadRequestDto {
    val newHouse = occupancy == LeadOccupancy.W_BUDOWIE
    val originWire = when {
        // „Polecenie" w kroku 1 samo zaznacza rekomendację (ustalenie 2026-09-13).
        origin == null && channel == LeadChannel.POLECENIE -> LeadOrigin.REKOMENDACJA.wire
        else -> origin?.wire
    }
    return AppLeadRequestDto(
        clientRef = clientRef,
        channel = channel.wire,
        eventId = eventId.takeIf { channel == LeadChannel.TARGI },
        takenById = takenById,
        installations = interests.map { it.label },
        occupancy = occupancy.wire,
        projectKind = projectKind?.wire.takeIf { newHouse },
        projectName = projectName?.trim()?.ifBlank { null }.takeIf { newHouse },
        building = if (newHouse) {
            AppLeadBuildingDto(
                shape = shape?.wire,
                construction = construction?.wire,
                areaM2 = areaM2,
                heatedBasement = basement,
                heatedGarage = garage,
            )
        } else {
            null
        },
        floorHeating = if (newHouse) {
            AppLeadFloorHeatingDto(
                variant = floorHeatingVariant?.wire,
                note = floorHeatingNote?.trim()?.ifBlank { null }
                    .takeIf { floorHeatingVariant == FloorHeatingVariant.OPIS },
                lightSlab = lightSlab,
                milling = milling,
            )
        } else {
            AppLeadFloorHeatingDto(
                works = works?.wire,
                worksAreaM2 = worksAreaM2,
                heatSource = heatSource,
                heatSourceWhen = if (heatSourcePlanned) "ma_byc" else "jest",
            )
        },
        fullName = fullName.trim(),
        phone = phone?.trim()?.ifBlank { null },
        email = email?.trim()?.ifBlank { null },
        postalCode = postalCode?.trim()?.ifBlank { null },
        city = city?.trim()?.ifBlank { null },
        leadOrigin = originWire,
        referralFrom = referralFrom?.trim()?.ifBlank { null }
            .takeIf { originWire == LeadOrigin.REKOMENDACJA.wire },
    )
}
