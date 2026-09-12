package com.ekotak.teamtalk.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Ostatnia znana pozycja pojazdu (`GET /api/fleet/positions`) — kontrakt
 * board360 (`api/src/modules/fleet/domain/telemetry.ts`, `AssetLastPosition`).
 *
 * Pozycję zapisuje bramka `tracker/`, która rozbiera Codec 8 z lokalizatorów
 * Teltonika. Wiersz istnieje wyłącznie dla aut z prawdziwym fixem: odczyt bez
 * zasięgu satelitów (0/0) API odrzuca, więc `lat`/`lng` są tu zawsze realne.
 *
 * `ageMinutes` liczy serwer wobec SWOJEGO „teraz" i my go nie używamy — wiek
 * pozycji telefon liczy sam z [occurredAt], bo migawkę z cache ogląda się także
 * godzinę później, bez zasięgu.
 */
@Serializable
data class FleetPositionDto(
    val assetId: String,
    val assetName: String,
    val registration: String? = null,
    /** Czas URZĄDZENIA w ISO 8601 (UTC), nie czas zapisu w bazie. */
    val occurredAt: String,
    val lat: Double,
    val lng: Double,
    /** km/h. */
    val speed: Double? = null,
    /** Kurs w stopniach; dziś nieużywany, zostawiony dla strzałki kierunku. */
    val angle: Double? = null,
    /** `null` = w aucie nie podpięto DIN1, więc o zapłonie nic nie wiadomo. */
    val ignition: Boolean? = null,
    val ageMinutes: Int = 0,
)

/**
 * Pojazd z modułu Zasoby (`GET /api/assets?type=vehicle`). Bierzemy tylko to,
 * czym żyje mapa — reszta karty auta (przeglądy, ubezpieczenia, przypisania)
 * zostaje w panelu; `ignoreUnknownKeys` przepuszcza ją bez błędu.
 *
 * Auta bez pozycji są tu po to, by dało się odróżnić „tracker milczy" od „nie
 * ma trackera": pierwsze załatwia się sprawdzeniem bezpiecznika, drugie
 * wpisaniem IMEI w karcie.
 */
@Serializable
data class FleetVehicleDto(
    val id: String,
    val name: String,
    val registration: String? = null,
    val gpsImei: String? = null,
    /** `active` | `in_service` | `reserved` | `retired`. */
    val status: String = "active",
)

/**
 * Historia trasy jednego auta (`GET /api/fleet/assets/{id}/route`) — kontrakt
 * board360 (`api/src/modules/fleet/domain/route-history.ts`).
 *
 * Reguły („co jest postojem", „gdzie zaczyna się kurs", „jak grupują się
 * przekroczenia") liczy WYŁĄCZNIE serwer i telefon ich nie powtarza. Gdyby
 * liczył je sam, ten sam dzień miałby inną liczbę postojów w panelu i w
 * aplikacji — a to jest dokument, na który ktoś się powołuje w rozmowie z
 * kierowcą.
 */
@Serializable
data class RouteHistoryDto(
    val from: String,
    val to: String,
    val speedLimitKmh: Int,
    val points: List<RoutePointDto> = emptyList(),
    val trips: List<RouteTripDto> = emptyList(),
    val stops: List<RouteStopDto> = emptyList(),
    val speeding: List<SpeedingRunDto> = emptyList(),
    val harsh: List<HarshEventDto> = emptyList(),
    val summary: RouteSummaryDto,
)

@Serializable
data class RoutePointDto(
    val occurredAt: String,
    val lat: Double,
    val lng: Double,
    val speed: Double? = null,
    val ignition: Boolean? = null,
    /** Odcinek DO tego punktu to luka w sygnale — rysowany przerywaną linią. */
    val gap: Boolean = false,
)

@Serializable
data class RouteStopDto(
    val from: String,
    val to: String,
    val minutes: Int,
    val lat: Double,
    val lng: Double,
    /** Silnik pracował przez większość postoju (stanie na jałowym). */
    val idling: Boolean = false,
    val open: Boolean = false,
)

@Serializable
data class RouteTripDto(
    val index: Int,
    val departedAt: String,
    val arrivedAt: String,
    val minutes: Int,
    val distanceKm: Double,
    val fromLat: Double,
    val fromLng: Double,
    val toLat: Double,
    val toLng: Double,
    val maxSpeed: Double? = null,
    val avgSpeed: Double? = null,
    val speedingCount: Int = 0,
    val open: Boolean = false,
    val driverLabel: String? = null,
    val startOdometerKm: Double? = null,
    val endOdometerKm: Double? = null,
)

@Serializable
data class SpeedingRunDto(
    val from: String,
    val to: String,
    val seconds: Int,
    val maxSpeed: Double,
    val limit: Double,
    val lat: Double,
    val lng: Double,
    val distanceKm: Double = 0.0,
    val tripIndex: Int? = null,
)

@Serializable
data class HarshEventDto(
    val at: String,
    val lat: Double,
    val lng: Double,
    /** `accel` | `brake` | `unknown`. */
    val type: String = "unknown",
    /** `device` = zgłosił lokalizator, `computed` = szacunek z odczytów. */
    val source: String = "computed",
    val fromSpeed: Double? = null,
    val toSpeed: Double? = null,
    val accelMs2: Double? = null,
)

@Serializable
data class DrivingStyleDto(
    val score: Int = 100,
    /** `dobry` | `poprawny` | `do rozmowy`. */
    val grade: String = "dobry",
    val speedingPenalty: Int = 0,
    val harshPenalty: Int = 0,
    val idlePenalty: Int = 0,
    val harshEvents: Int = 0,
    val harshFromDevice: Int = 0,
)

@Serializable
data class RouteSummaryDto(
    val distanceKm: Double = 0.0,
    val drivingMinutes: Int = 0,
    val stopMinutes: Int = 0,
    val idleMinutes: Int = 0,
    val maxSpeed: Double? = null,
    val firstDepartureAt: String? = null,
    val lastArrivalAt: String? = null,
    val trips: Int = 0,
    val stops: Int = 0,
    val speedingRuns: Int = 0,
    val speedingMinutes: Int = 0,
    val longestStopMinutes: Int = 0,
    val odometerKm: Double? = null,
    val idleCostPln: Double = 0.0,
    val style: DrivingStyleDto = DrivingStyleDto(),
)

/**
 * Kondycja lokalizatora (`GET /api/fleet/health`) — „czy temu, co widzę na
 * mapie, wolno ufać". Opisy problemów układa serwer, bo mają być identyczne w
 * panelu i w telefonie.
 */
@Serializable
data class TrackerHealthDto(
    val assetId: String,
    val assetName: String,
    val registration: String? = null,
    val hasTracker: Boolean = true,
    /** `ok` | `cisza` | `zasilanie` | `usterka` | `bez_lokalizatora`. */
    val status: String = "ok",
    val lastContactAt: String? = null,
    val silentMinutes: Int? = null,
    val readings: Int = 0,
    val coveragePercent: Int = 0,
    val longestGapMinutes: Int = 0,
    val voltageV: Double? = null,
    val minVoltageV: Double? = null,
    val gsmSignal: Double? = null,
    val satellites: Double? = null,
    val faultCodes: String? = null,
    val issues: List<String> = emptyList(),
)
