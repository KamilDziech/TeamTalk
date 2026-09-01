package com.ekotak.teamtalk.data.mapper

import com.ekotak.teamtalk.data.remote.dto.CategoryDto
import com.ekotak.teamtalk.data.remote.dto.DealInstallationStageDto
import com.ekotak.teamtalk.data.remote.dto.DealInstallationsDto
import com.ekotak.teamtalk.data.remote.dto.LeadBuildingDto
import com.ekotak.teamtalk.data.remote.dto.LeadIntakeResponseDto
import com.ekotak.teamtalk.domain.model.Category
import com.ekotak.teamtalk.domain.model.DealInstallations
import com.ekotak.teamtalk.domain.model.InstallationStage
import com.ekotak.teamtalk.domain.model.InstallationStageState
import com.ekotak.teamtalk.domain.model.LeadBuilding
import com.ekotak.teamtalk.domain.model.LeadChannel
import com.ekotak.teamtalk.domain.model.LeadIntake
import com.ekotak.teamtalk.domain.model.StageInstallations

/**
 * Zgłoszenie z leadowni i migawki instalacji na modele domenowe. Nieznany
 * kanał zostawiamy jako `null` — karta pokaże wtedy surowe `source` zamiast
 * chować całe zgłoszenie przez jedno nierozpoznane pole.
 */
fun LeadIntakeResponseDto.toDomain(): LeadIntake = LeadIntake(
    channel = LeadChannel.fromWire(channel),
    source = source,
    sourceLabel = sourceLabel,
    fullName = fullName,
    phone = phone,
    email = email,
    city = city,
    interest = interest,
    budget = budget,
    note = note?.takeIf { it.isNotBlank() },
    consent = consent,
    submittedBy = submittedBy,
    createdAt = createdAt,
    building = building?.toDomain()?.takeIf { !it.isEmpty },
)

fun LeadBuildingDto.toDomain(): LeadBuilding = LeadBuilding(
    shape = shape,
    construction = construction,
    area = area,
    people = people,
    floors = floors,
    stage = stage,
    windows = windows,
    heatedBasement = heatedBasement,
    heatedGarage = heatedGarage,
)

/**
 * Migawki instalacji. Etap spoza znanej listy pomijamy — board360 mógłby
 * dołożyć nowy po wydaniu tej wersji aplikacji, a jedna nieznana pozycja nie
 * może przewrócić całej zakładki.
 */
fun DealInstallationsDto.toDomain(): DealInstallations = DealInstallations(
    current = InstallationStage.fromWire(current),
    stages = stages.mapNotNull { it.toDomainOrNull() },
)

private fun DealInstallationStageDto.toDomainOrNull(): StageInstallations? {
    val known = InstallationStage.fromWire(stage) ?: return null
    return StageInstallations(
        stage = known,
        categoryIds = categories,
        editable = editable,
        state = InstallationStageState.fromWire(state),
    )
}

fun CategoryDto.toDomain(): Category = Category(
    id = id,
    parentId = parentId,
    name = name,
    position = position,
)
