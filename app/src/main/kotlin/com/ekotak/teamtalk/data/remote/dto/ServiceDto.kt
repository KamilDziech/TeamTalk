package com.ekotak.teamtalk.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Zlecenie serwisowe (`GET /api/service-jobs`). Na mapie interesuje nas typ
 * (awaria → widok „Serwisy", przegląd/konserwacja → „Przeglądy"), status,
 * serwisant i flaga przekroczonego SLA — resztę pól zostawiamy na moduł Serwis.
 */
@Serializable
data class ServiceJobResponseDto(
    val id: String,
    /** `null` = zlecenie zapisane bez klienta — na mapie nie ma czego pokazać. */
    val clientId: String? = null,
    val dealId: String? = null,
    val type: String = "awaria",
    val status: String = "new",
    val priority: String = "normal",
    val technicianId: String? = null,
    val scheduledAt: String? = null,
    val note: String? = null,
    val slaHours: Int? = null,
    val slaDueAt: String? = null,
    val slaBreached: Boolean = false,
)

/** Serwisant (`GET /api/technicians`) — do filtra osoby w widokach serwisowych. */
@Serializable
data class TechnicianDto(
    val id: String,
    val email: String = "",
    val firstName: String? = null,
    val lastName: String? = null,
    val region: String? = null,
)

/** Przegląd w karcie gwarancyjnej — potrzebny tylko po to, by wskazać serwisanta. */
@Serializable
data class WarrantyInspectionDto(
    val id: String,
    val ordinal: Int = 0,
    val plannedAt: String? = null,
    val doneAt: String? = null,
    val technicianId: String? = null,
    /** `done` / `overdue` / `planned` / `unscheduled` — liczone przez API. */
    val computedStatus: String = "unscheduled",
)

/** Karta gwarancyjna Panasonic (`GET /api/warranty-cards`) — widok „Przeglądy". */
@Serializable
data class WarrantyCardDto(
    val id: String,
    val brand: String = "",
    val name: String = "",
    /** Adres jako wolny tekst; współrzędne idą osobno ze snapshotu geo. */
    val location: String? = null,
    val status: String = "inne",
    val nextPlannedAt: String? = null,
    val inspections: List<WarrantyInspectionDto> = emptyList(),
)

/**
 * Współrzędne karty ze snapshotu (`GET /api/warranty-cards/geo`). Karta bez
 * wpisu = adres, którego geokoder nie rozpoznał → lista „bez lokalizacji".
 */
@Serializable
data class WarrantyGeoDto(
    val id: String,
    val lat: Double,
    val lng: Double,
    val city: String = "",
)
