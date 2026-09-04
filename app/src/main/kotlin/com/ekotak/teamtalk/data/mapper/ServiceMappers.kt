package com.ekotak.teamtalk.data.mapper

import com.ekotak.teamtalk.data.local.entity.ServiceClientEntity
import com.ekotak.teamtalk.data.local.entity.ServiceJobEntity
import com.ekotak.teamtalk.data.local.entity.ServiceTechnicianEntity
import com.ekotak.teamtalk.data.local.entity.WarrantyCardEntity
import com.ekotak.teamtalk.data.remote.dto.ClientResponseDto
import com.ekotak.teamtalk.data.remote.dto.ServiceJobResponseDto
import com.ekotak.teamtalk.data.remote.dto.TechnicianDto
import com.ekotak.teamtalk.data.remote.dto.WarrantyCardDto
import com.ekotak.teamtalk.data.remote.dto.WarrantyInspectionDto
import com.ekotak.teamtalk.domain.model.Edit
import com.ekotak.teamtalk.domain.model.ServiceClient
import com.ekotak.teamtalk.domain.model.ServiceJob
import com.ekotak.teamtalk.domain.model.ServiceJobPatch
import com.ekotak.teamtalk.domain.model.ServiceJobPriority
import com.ekotak.teamtalk.domain.model.ServiceJobStatus
import com.ekotak.teamtalk.domain.model.ServiceJobType
import com.ekotak.teamtalk.domain.model.Technician
import com.ekotak.teamtalk.domain.model.WarrantyCard
import com.ekotak.teamtalk.domain.model.WarrantyCardStatus
import com.ekotak.teamtalk.domain.model.WarrantyInspection
import com.ekotak.teamtalk.domain.model.WarrantyInspectionStatus
import kotlinx.serialization.json.Json

/**
 * Mapowanie modułu Serwis: DTO ↔ encja cache ↔ model domenowy.
 *
 * Harmonogram przeglądów jedzie do bazy jako JSON listy DTO — zapisujemy tam
 * dokładnie to, co przyszło z API, żeby cache nie „poprawiał” danych po drodze
 * (`computedStatus` liczy serwer i tylko on wie, co jest po terminie).
 */

private val serviceJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

// ── Zlecenia ─────────────────────────────────────────────────────────────────

fun ServiceJobResponseDto.toEntity(now: Long): ServiceJobEntity = ServiceJobEntity(
    id = id,
    clientId = clientId,
    dealId = dealId,
    type = type,
    status = status,
    priority = priority,
    technicianId = technicianId,
    scheduledAt = scheduledAt,
    note = note,
    slaHours = slaHours,
    slaDueAt = slaDueAt,
    slaBreached = slaBreached,
    localOnly = false,
    syncedAt = now,
)

fun ServiceJobEntity.toDomain(pendingSync: Boolean): ServiceJob = ServiceJob(
    id = id,
    clientId = clientId,
    dealId = dealId,
    type = ServiceJobType.fromWire(type),
    status = ServiceJobStatus.fromWire(status),
    priority = ServiceJobPriority.fromWire(priority),
    technicianId = technicianId,
    scheduledAt = scheduledAt,
    note = note,
    slaHours = slaHours,
    slaDueAt = slaDueAt,
    slaBreached = slaBreached,
    pendingSync = pendingSync,
    localOnly = localOnly,
)

/**
 * Nakłada zakolejkowaną zmianę na wiersz cache, żeby lista pokazała decyzję
 * człowieka od razu — zanim serwer się o niej dowie. Pola liczone przez API
 * (`slaDueAt`, `slaBreached`) zostają bez zmian: telefon ich nie policzy tak
 * samo, a zgadywanie byłoby gorsze niż lekkie opóźnienie.
 */
fun ServiceJobEntity.applyPatch(patch: ServiceJobPatch): ServiceJobEntity = copy(
    clientId = patch.clientId?.value ?: clientId,
    status = patch.status?.value?.wire ?: status,
    priority = patch.priority?.value?.wire ?: priority,
    note = patch.note.orKeep(note),
    technicianId = patch.technicianId.orKeep(technicianId),
    scheduledAt = patch.scheduledAt.orKeep(scheduledAt),
    slaHours = patch.slaHours?.value ?: slaHours,
)

private fun <T> Edit<T?>?.orKeep(current: T?): T? = if (this == null) current else value

// ── Karty gwarancyjne ────────────────────────────────────────────────────────

fun WarrantyCardDto.toEntity(now: Long): WarrantyCardEntity = WarrantyCardEntity(
    id = id,
    brand = brand,
    name = name,
    location = location,
    commissionedAt = commissionedAt,
    status = status,
    outdoorModel = outdoorModel,
    outdoorSerial = outdoorSerial,
    indoorModel = indoorModel,
    indoorSerial = indoorSerial,
    note = note,
    inspectionsJson = serviceJson.encodeToString(
        kotlinx.serialization.builtins.ListSerializer(WarrantyInspectionDto.serializer()),
        inspections,
    ),
    doneCount = doneCount,
    overdueCount = overdueCount,
    suspectCount = suspectCount,
    nextPlannedAt = nextPlannedAt,
    syncedAt = now,
)

fun WarrantyCardEntity.toDomain(): WarrantyCard {
    val inspections = runCatching {
        serviceJson.decodeFromString(
            kotlinx.serialization.builtins.ListSerializer(WarrantyInspectionDto.serializer()),
            inspectionsJson,
        )
    }.getOrDefault(emptyList())
    return WarrantyCard(
        id = id,
        brand = brand,
        name = name,
        location = location,
        commissionedAt = commissionedAt,
        status = WarrantyCardStatus.fromWire(status),
        outdoorModel = outdoorModel,
        outdoorSerial = outdoorSerial,
        indoorModel = indoorModel,
        indoorSerial = indoorSerial,
        note = note,
        inspections = inspections
            .map { it.toDomainInspection(id) }
            .sortedBy { it.ordinal },
        doneCount = doneCount,
        overdueCount = overdueCount,
        suspectCount = suspectCount,
        nextPlannedAt = nextPlannedAt,
    )
}

private fun WarrantyInspectionDto.toDomainInspection(fallbackCardId: String): WarrantyInspection =
    WarrantyInspection(
        id = id,
        cardId = cardId.ifBlank { fallbackCardId },
        ordinal = ordinal,
        plannedAt = plannedAt,
        doneAt = doneAt,
        price = price,
        technicianId = technicianId,
        note = note,
        computedStatus = WarrantyInspectionStatus.fromWire(computedStatus),
        suspect = suspect,
    )

// ── Klienci i serwisanci ─────────────────────────────────────────────────────

/**
 * Klient w kształcie modułu Serwis. Nazwa jak w panelu (`fullName`), a gdy jej
 * brak — telefon, żeby wiersz listy nie został bez etykiety.
 */
fun ClientResponseDto.toServiceClientEntity(): ServiceClientEntity = ServiceClientEntity(
    id = id,
    label = listOf(firstName, lastName)
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .trim()
        .ifBlank { phone ?: "Klient bez nazwy" },
    city = city ?: postalCode,
    phone = phone ?: phone2,
    address = address?.takeIf { it.isNotBlank() }
        ?: listOfNotNull(postalCode, city).filter { it.isNotBlank() }
            .joinToString(" ")
            .takeIf { it.isNotBlank() },
)

fun ServiceClientEntity.toDomain(): ServiceClient = ServiceClient(
    id = id,
    label = label,
    city = city,
    phone = phone,
    address = address,
)

fun TechnicianDto.toServiceTechnicianEntity(): ServiceTechnicianEntity = ServiceTechnicianEntity(
    id = id,
    email = email,
    firstName = firstName,
    lastName = lastName,
)

fun ServiceTechnicianEntity.toDomain(): Technician = Technician(
    id = id,
    email = email,
    firstName = firstName,
    lastName = lastName,
)
