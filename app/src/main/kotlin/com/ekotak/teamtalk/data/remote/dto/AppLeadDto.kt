package com.ekotak.teamtalk.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * `POST /api/intake/app/lead` — zgłoszenie z kreatora LEAD (board360:
 * `IntakeAppController`, `appLeadSchema`). Pola opcjonalne serwer przyjmuje jako
 * NIEOBECNE, nie `null` — dlatego do serializacji zawsze bierzemy `Json`
 * z `NetworkModule` (`explicitNulls = false`), także przy zapisie do kolejki.
 */
@Serializable
data class AppLeadRequestDto(
    /** UUID nadany w telefonie — ponowiona wysyłka nie założy drugiej karty. */
    val clientRef: String,
    /** tel | spotkanie | polecenie | targi */
    val channel: String,
    val eventId: String? = null,
    val sourceLabel: String? = null,
    val takenById: String? = null,
    val installations: List<String>,
    /** w_budowie | zamieszkaly */
    val occupancy: String,
    /** katalog | wlasny | niepamieta */
    val projectKind: String? = null,
    val projectName: String? = null,
    val building: AppLeadBuildingDto? = null,
    val floorHeating: AppLeadFloorHeatingDto? = null,
    val fullName: String,
    val phone: String? = null,
    val email: String? = null,
    val postalCode: String? = null,
    val city: String? = null,
    val leadOrigin: String? = null,
    val referralFrom: String? = null,
)

@Serializable
data class AppLeadBuildingDto(
    val shape: String? = null,
    val construction: String? = null,
    val areaM2: Int? = null,
    val heatedBasement: Boolean = false,
    val heatedGarage: Boolean = false,
)

@Serializable
data class AppLeadFloorHeatingDto(
    /** standard | suchy | opis — nowy dom. */
    val variant: String? = null,
    val note: String? = null,
    val lightSlab: Boolean = false,
    val milling: Boolean = false,
    /** skucie | suchy | frezowanie — dom zamieszkały. */
    val works: String? = null,
    val worksAreaM2: Int? = null,
    val heatSource: String? = null,
    /** jest | ma_byc */
    val heatSourceWhen: String? = null,
)

@Serializable
data class AppLeadResponseDto(
    val dealId: String,
    val duplicate: Boolean = false,
)

/** `GET /api/intake/app/events` — lista „Które targi?". */
@Serializable
data class LeadEventDto(
    val id: String,
    val name: String,
    val eventDate: String? = null,
)
