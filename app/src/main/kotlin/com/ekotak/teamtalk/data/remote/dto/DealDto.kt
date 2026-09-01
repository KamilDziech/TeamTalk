package com.ekotak.teamtalk.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Deal lejka sprzedaży — kształt board360 (`GET /api/deals`, `GET /api/deals/:id`).
 * Wszystkie pola opcjonalne poza `id`: `ignoreUnknownKeys` przepuszcza części
 * karty nieużywane na mobile (oferta, rozliczenie, split/merge, kwalifikacja),
 * a `client`/`activities` przychodzą wyłącznie ze szczegółów deala.
 */
@Serializable
data class DealResponseDto(
    val id: String,
    val clientId: String = "",
    val ownerId: String = "",
    val stageOwnerId: String? = null,
    val stage: String = "lead",
    val stageEnteredAt: String? = null,
    val source: String? = null,
    val nextContactAt: String? = null,
    val segment: String? = null,
    val buildingKind: String? = null,
    val difficulty: String? = null,
    val buyerPersona: String? = null,
    val projectName: String? = null,
    val buildingData: DealBuildingDataDto? = null,
    val ozcData: DealOzcDataDto? = null,
    val description: String? = null,
    val discountCode: String? = null,
    val driveFolder: String? = null,
    val rodoConsent: Boolean = false,
    val rodoConsentAt: String? = null,
    val elderlyContactException: Boolean = false,
    val meetingKind: String? = null,
    val meetingAt: String? = null,
    val meetingOwnerId: String? = null,
    val meetingDurationMin: Int? = null,
    val meetingUrl: String? = null,
    val auditAddressKind: String? = null,
    val auditAddress: String? = null,
    val auditMeetingAt: String? = null,
    val auditOwnerId: String? = null,
    val billingSameAsInstall: Boolean = true,
    val billingName: String? = null,
    val billingCompany: String? = null,
    val billingNip: String? = null,
    val billingAddress: String? = null,
    /** Auto-kwalifikacja leada: `true` = czeka na decyzję człowieka. */
    val qualReview: Boolean = false,
    val qualReviewAt: String? = null,
    val qualReviewReason: String? = null,
    val lostReason: String? = null,
    val lostReasonCategory: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    /** Tylko w `GET /api/deals/:id` — lista dealów klienta nie zawiera. */
    val client: ClientResponseDto? = null,
    /** Tylko w `GET /api/deals/:id` — historia zmian, malejąco po dacie. */
    val activities: List<DealActivityDto> = emptyList(),
)

@Serializable
data class DealBuildingDataDto(
    val people: Int? = null,
    val areaM2: Double? = null,
    val floors: Int? = null,
    val shape: String? = null,
    val construction: String? = null,
    val stage: String? = null,
    val windows: String? = null,
    val heatedBasement: Boolean? = null,
    val heatedGarage: Boolean? = null,
)

@Serializable
data class DealOzcDataDto(
    val buildingKw: Double? = null,
    val dhwKw: Double? = null,
    val sourceUrl: String? = null,
    val confirmed: Boolean = false,
)

/**
 * Wpis `ActivityLog`. `diff` to dowolny JSON zależny od akcji — czytamy z niego
 * tylko `from`/`to` dla `stage_change`, resztę pomijamy.
 */
@Serializable
data class DealActivityDto(
    val id: String,
    val action: String = "",
    val userId: String = "",
    val createdAt: String = "",
    val diff: JsonElement? = null,
)

/**
 * `POST /api/deals/:id/stage`. Przy `stage = "lost"` API wymaga powodu —
 * bez `lostReasonCategory` odpowiada 422.
 */
@Serializable
data class ChangeStageRequest(
    val stage: String,
    val lostReason: String? = null,
    val lostReasonCategory: String? = null,
    val note: String? = null,
)
