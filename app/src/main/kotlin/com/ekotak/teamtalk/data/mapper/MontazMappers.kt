package com.ekotak.teamtalk.data.mapper

import com.ekotak.teamtalk.data.local.entity.MontazCrewEntity
import com.ekotak.teamtalk.data.local.entity.MontazEntity
import com.ekotak.teamtalk.data.local.entity.MontazMaterialEntity
import com.ekotak.teamtalk.data.local.entity.MontazPhotoEntity
import com.ekotak.teamtalk.data.remote.dto.MontazAssigneeDto
import com.ekotak.teamtalk.data.remote.dto.MontazCrewDto
import com.ekotak.teamtalk.data.remote.dto.MontazDto
import com.ekotak.teamtalk.data.remote.dto.MontazMaterialDto
import com.ekotak.teamtalk.data.remote.dto.MontazPhotoDto
import com.ekotak.teamtalk.domain.model.DealDifficulty
import com.ekotak.teamtalk.domain.model.MaterialStatus
import com.ekotak.teamtalk.domain.model.Montaz
import com.ekotak.teamtalk.domain.model.MontazAssignee
import com.ekotak.teamtalk.domain.model.MontazCrew
import com.ekotak.teamtalk.domain.model.MontazMaterial
import com.ekotak.teamtalk.domain.model.MontazPhoto
import com.ekotak.teamtalk.domain.model.MontazStatus
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Zakładka „Montaż": DTO ↔ encja cache ↔ model domenowy.
 *
 * Obsada jedzie do bazy jako surowy JSON listy `{userId, role}` — to kontrakt
 * z panelem, czytany zawsze w całości. Domyślna liczba dni (2) jest tą samą
 * wartością, którą pokazuje karta montażu w panelu, więc telefon nie wymyśla
 * innego terminu, gdy backend pola nie odda.
 */

private val assigneeListSerializer = ListSerializer(MontazAssigneeDto.serializer())

/** Obsada z rolami; starszy backend oddaje samo `assigneeIds` — bierzemy je. */
private fun MontazDto.assigneeList(): List<MontazAssigneeDto> = when {
    assignees.isNotEmpty() -> assignees
    else -> assigneeIds.map { MontazAssigneeDto(userId = it, role = null) }
}

fun MontazDto.toEntity(json: Json, now: Long): MontazEntity = MontazEntity(
    id = id,
    dealId = dealId,
    scheduledAt = scheduledAt,
    status = status,
    difficulty = difficulty,
    teamNote = teamNote,
    nodeIds = nodeIds,
    crewId = crewId,
    assigneesJson = json.encodeToString(assigneeListSerializer, assigneeList()),
    briefedAt = briefedAt,
    briefingMessageId = briefingMessageId,
    durationDays = durationDays ?: DEFAULT_MONTAZ_DAYS,
    syncedAt = now,
)

fun MontazEntity.toDomain(json: Json): Montaz = Montaz(
    id = id,
    dealId = dealId,
    scheduledAt = scheduledAt,
    status = MontazStatus.fromWire(status),
    difficulty = DealDifficulty.fromWire(difficulty),
    teamNote = teamNote,
    nodeIds = nodeIds,
    crewId = crewId,
    assignees = runCatching {
        json.decodeFromString(assigneeListSerializer, assigneesJson)
    }.getOrDefault(emptyList()).map { MontazAssignee(it.userId, it.role) },
    briefedAt = briefedAt,
    briefingMessageId = briefingMessageId,
    durationDays = durationDays,
    local = id.startsWith("local:"),
)

fun MontazCrewDto.toEntity(now: Long): MontazCrewEntity = MontazCrewEntity(
    id = id,
    name = name,
    color = color,
    leaderId = leaderId,
    memberIds = memberIds,
    syncedAt = now,
)

fun MontazCrewEntity.toDomain(): MontazCrew = MontazCrew(
    id = id,
    name = name,
    color = color,
    leaderId = leaderId,
    memberIds = memberIds,
)

fun MontazMaterialDto.toEntity(installationId: String, now: Long): MontazMaterialEntity =
    MontazMaterialEntity(
        id = id,
        installationId = installationId,
        itemName = itemName,
        itemCode = itemCode,
        quantity = quantity,
        unit = unit,
        status = status,
        covered = covered,
        missing = missing,
        issuedAt = issuedAt,
        issuedById = issuedById,
        note = note,
        syncedAt = now,
    )

fun MontazMaterialEntity.toDomain(): MontazMaterial = MontazMaterial(
    id = id,
    itemName = itemName,
    itemCode = itemCode,
    quantity = quantity,
    unit = unit,
    status = MaterialStatus.fromWire(status),
    covered = covered,
    missing = missing,
    issuedAt = issuedAt,
    issuedById = issuedById,
    note = note,
)

fun MontazPhotoDto.toEntity(now: Long): MontazPhotoEntity = MontazPhotoEntity(
    id = id,
    installationId = installationId,
    caption = caption,
    createdAt = createdAt,
    localPath = null,
    syncedAt = now,
)

fun MontazPhotoEntity.toDomain(): MontazPhoto = MontazPhoto(
    id = id,
    installationId = installationId,
    caption = caption,
    createdAt = createdAt,
    localPath = localPath,
)

/** Tyle dni roboczych zajmuje wyjazd, gdy backend nie poda swojej liczby. */
const val DEFAULT_MONTAZ_DAYS = 2
