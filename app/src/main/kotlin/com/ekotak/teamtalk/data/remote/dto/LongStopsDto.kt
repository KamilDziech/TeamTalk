package com.ekotak.teamtalk.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Długie postoje auta (`GET /api/fleet/assets/{id}/stops`) — kontrakt board360
 * (`api/src/modules/fleet/domain/long-stops.ts`). Postoje > 10 min ze zgaszonym
 * silnikiem, z adresem z geokodera i punktem z systemu w 250 m. Wszystko
 * liczy serwer; telefon tylko pokazuje.
 */
@Serializable
data class LongStopsResponseDto(
    val stops: List<LongStopDto> = emptyList(),
)

@Serializable
data class LongStopDto(
    val from: String,
    val to: String,
    val minutes: Int,
    val lat: Double,
    val lng: Double,
    val open: Boolean = false,
    val address: LongStopAddressDto? = null,
    val match: LongStopMatchDto? = null,
    val nearby: List<LongStopMatchDto> = emptyList(),
)

@Serializable
data class LongStopAddressDto(
    val city: String? = null,
    val street: String? = null,
    val houseNumber: String? = null,
    val label: String? = null,
)

@Serializable
data class LongStopMatchDto(
    /** `client` | `warranty` | `zone` | `place`. */
    val kind: String,
    val id: String,
    val label: String,
    val detail: String? = null,
    val distanceM: Int,
    val visit: LongStopVisitDto? = null,
)

@Serializable
data class LongStopVisitDto(
    /** `montaz` | `audyt` | `serwis` | `przeglad` | `przeglad_gwarancyjny`. */
    val kind: String,
    val at: String,
    val ref: String? = null,
)
