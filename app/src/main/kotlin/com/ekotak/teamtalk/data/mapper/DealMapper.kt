package com.ekotak.teamtalk.data.mapper

import com.ekotak.teamtalk.data.remote.dto.DealActivityDto
import com.ekotak.teamtalk.data.remote.dto.DealBuildingDataDto
import com.ekotak.teamtalk.data.remote.dto.DealOzcDataDto
import com.ekotak.teamtalk.data.remote.dto.DealResponseDto
import com.ekotak.teamtalk.domain.model.AuditAddressKind
import com.ekotak.teamtalk.domain.model.Deal
import com.ekotak.teamtalk.domain.model.DealActivity
import com.ekotak.teamtalk.domain.model.DealBuildingData
import com.ekotak.teamtalk.domain.model.DealBuildingKind
import com.ekotak.teamtalk.domain.model.DealBuyerPersona
import com.ekotak.teamtalk.domain.model.DealDetail
import com.ekotak.teamtalk.domain.model.DealDifficulty
import com.ekotak.teamtalk.domain.model.DealOzcData
import com.ekotak.teamtalk.domain.model.DealSegment
import com.ekotak.teamtalk.domain.model.DealStage
import com.ekotak.teamtalk.domain.model.MeetingKind
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Deal z API na model domenowy. Nieznany etap (np. dołożony w board360 po
 * wydaniu tej wersji aplikacji) mapujemy na `LEAD` — karta zostaje widoczna
 * zamiast wywalić parsowanie całej listy.
 */
fun DealResponseDto.toDomain(): Deal = Deal(
    id = id,
    clientId = clientId,
    ownerId = ownerId,
    stageOwnerId = stageOwnerId,
    stage = DealStage.fromWire(stage) ?: DealStage.LEAD,
    stageEnteredAt = stageEnteredAt,
    source = source,
    nextContactAt = nextContactAt,
    segment = DealSegment.fromWire(segment),
    buildingKind = DealBuildingKind.fromWire(buildingKind),
    difficulty = DealDifficulty.fromWire(difficulty),
    buyerPersona = DealBuyerPersona.fromWire(buyerPersona),
    projectName = projectName,
    buildingData = buildingData?.toDomain(),
    ozcData = ozcData?.toDomain(),
    description = description,
    discountCode = discountCode,
    driveFolder = driveFolder,
    rodoConsent = rodoConsent,
    rodoConsentAt = rodoConsentAt,
    elderlyContactException = elderlyContactException,
    meetingKind = MeetingKind.fromWire(meetingKind),
    meetingAt = meetingAt,
    meetingOwnerId = meetingOwnerId,
    meetingDurationMin = meetingDurationMin,
    meetingUrl = meetingUrl,
    auditAddressKind = AuditAddressKind.fromWire(auditAddressKind),
    auditAddress = auditAddress,
    auditMeetingAt = auditMeetingAt,
    auditOwnerId = auditOwnerId,
    billingSameAsInstall = billingSameAsInstall,
    billingName = billingName,
    billingCompany = billingCompany,
    billingNip = billingNip,
    billingAddress = billingAddress,
    qualReview = qualReview,
    qualReviewAt = qualReviewAt,
    qualReviewReason = qualReviewReason,
    lostReason = lostReason,
    lostReasonCategory = lostReasonCategory,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

fun DealResponseDto.toDetail(): DealDetail = DealDetail(
    deal = toDomain(),
    client = client?.toDomain(),
    activities = activities.map { it.toDomain() },
)

fun DealBuildingDataDto.toDomain(): DealBuildingData = DealBuildingData(
    people = people,
    areaM2 = areaM2,
    floors = floors,
    shape = shape,
    construction = construction,
    stage = stage,
    windows = windows,
    heatedBasement = heatedBasement,
    heatedGarage = heatedGarage,
)

fun DealOzcDataDto.toDomain(): DealOzcData = DealOzcData(
    buildingKw = buildingKw,
    dhwKw = dhwKw,
    sourceUrl = sourceUrl,
    confirmed = confirmed,
)

/**
 * Wpis historii. Dla `stage_change` board360 zapisuje w `diff` obiekt
 * `{from, to, lostReason?, note?}` — wyciągamy go, żeby historia na telefonie
 * czytała się jak w panelu („Etap: Lead → Kwalifikacja"). Dla pozostałych akcji
 * `diff` ma inny kształt i jest ignorowany.
 */
fun DealActivityDto.toDomain(): DealActivity {
    val fields = (diff as? JsonObject).takeIf { action == "stage_change" }
    fun text(key: String): String? =
        (fields?.get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
    return DealActivity(
        id = id,
        action = action,
        userId = userId,
        createdAt = createdAt,
        fromStage = DealStage.fromWire(text("from")),
        toStage = DealStage.fromWire(text("to")),
        lostReason = text("lostReason"),
        note = text("note"),
    )
}
