package com.ekotak.teamtalk.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Zlecenie serwisowe (`GET /api/service-jobs`). Komplet pól kontraktu board360 —
 * moduł Mapa czyta z tego typ, status i SLA, moduł Serwis potrzebuje reszty
 * (opis usterki, priorytet, okno SLA) do listy i karty zlecenia.
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

/** Ciało `POST /api/service-jobs`. Klient opcjonalny — zapis „na szybko”. */
@Serializable
data class ServiceJobCreateDto(
    val type: String,
    val clientId: String? = null,
    val technicianId: String? = null,
    val scheduledAt: String? = null,
    val note: String? = null,
    val priority: String? = null,
    val slaHours: Int? = null,
)

/** Serwisant (`GET /api/technicians`) — przypisanie i filtr osoby. */
@Serializable
data class TechnicianDto(
    val id: String,
    val email: String = "",
    val firstName: String? = null,
    val lastName: String? = null,
    val region: String? = null,
)

/** Przegląd w karcie gwarancyjnej — jedna pozycja harmonogramu (rok 1..5). */
@Serializable
data class WarrantyInspectionDto(
    val id: String,
    val cardId: String = "",
    val ordinal: Int = 0,
    val plannedAt: String? = null,
    val doneAt: String? = null,
    val price: Int? = null,
    val technicianId: String? = null,
    val note: String? = null,
    /** `done` / `overdue` / `planned` / `unscheduled` — liczone przez API. */
    val computedStatus: String = "unscheduled",
    /** Data planowana przed uruchomieniem instalacji — do korekty. */
    val suspect: Boolean = false,
)

/** Karta gwarancyjna Panasonic (`GET /api/warranty-cards`). */
@Serializable
data class WarrantyCardDto(
    val id: String,
    val brand: String = "",
    val name: String = "",
    /** Adres jako wolny tekst; współrzędne idą osobno ze snapshotu geo. */
    val location: String? = null,
    val commissionedAt: String? = null,
    val status: String = "inne",
    val outdoorModel: String? = null,
    val outdoorSerial: String? = null,
    val indoorModel: String? = null,
    val indoorSerial: String? = null,
    val note: String? = null,
    val inspections: List<WarrantyInspectionDto> = emptyList(),
    val doneCount: Int = 0,
    val overdueCount: Int = 0,
    val suspectCount: Int = 0,
    val nextPlannedAt: String? = null,
)

/** Ciało `POST /api/warranty-cards`. */
@Serializable
data class WarrantyCardCreateDto(
    val name: String,
    val brand: String? = null,
    val location: String? = null,
    val commissionedAt: String? = null,
    val status: String? = null,
    val outdoorModel: String? = null,
    val outdoorSerial: String? = null,
    val indoorModel: String? = null,
    val indoorSerial: String? = null,
    val note: String? = null,
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
