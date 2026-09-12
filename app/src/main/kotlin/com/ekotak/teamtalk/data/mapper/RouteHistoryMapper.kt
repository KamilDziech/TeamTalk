package com.ekotak.teamtalk.data.mapper

import com.ekotak.teamtalk.data.remote.dto.DrivingStyleDto
import com.ekotak.teamtalk.data.remote.dto.HarshEventDto
import com.ekotak.teamtalk.data.remote.dto.RouteHistoryDto
import com.ekotak.teamtalk.data.remote.dto.RoutePointDto
import com.ekotak.teamtalk.data.remote.dto.RouteStopDto
import com.ekotak.teamtalk.data.remote.dto.RouteSummaryDto
import com.ekotak.teamtalk.data.remote.dto.RouteTripDto
import com.ekotak.teamtalk.data.remote.dto.SpeedingRunDto
import com.ekotak.teamtalk.data.remote.dto.TrackerHealthDto
import com.ekotak.teamtalk.domain.model.DrivingStyle
import com.ekotak.teamtalk.domain.model.HarshEvent
import com.ekotak.teamtalk.domain.model.HarshType
import com.ekotak.teamtalk.domain.model.RouteHistory
import com.ekotak.teamtalk.domain.model.RoutePoint
import com.ekotak.teamtalk.domain.model.RouteStop
import com.ekotak.teamtalk.domain.model.RouteSummary
import com.ekotak.teamtalk.domain.model.RouteTrip
import com.ekotak.teamtalk.domain.model.SpeedingRun
import com.ekotak.teamtalk.domain.model.TrackerHealth
import com.ekotak.teamtalk.domain.model.TrackerStatus
import java.time.Instant

/**
 * Historia trasy z API na model domenowy.
 *
 * Jedyna praca tego mapera to zamiana ISO na milisekundy — reszta przechodzi
 * jeden do jednego, bo wszystkie reguły policzył serwer. Punkt z niepoprawnym
 * znacznikiem czasu WYPADA ze śladu zamiast wywracać cały ekran: jeden zepsuty
 * rekord nie ma prawa zabrać kierowcy całego dnia.
 */
fun RouteHistoryDto.toDomain(): RouteHistory = RouteHistory(
    fromMillis = millis(from) ?: 0L,
    toMillis = millis(to) ?: System.currentTimeMillis(),
    speedLimitKmh = speedLimitKmh,
    points = points.mapNotNull { it.toDomainOrNull() },
    trips = trips.mapNotNull { it.toDomainOrNull() },
    stops = stops.mapNotNull { it.toDomainOrNull() },
    speeding = speeding.mapNotNull { it.toDomainOrNull() },
    harsh = harsh.mapNotNull { it.toDomainOrNull() },
    summary = summary.toDomain(),
)

private fun RoutePointDto.toDomainOrNull(): RoutePoint? {
    val at = millis(occurredAt) ?: return null
    return RoutePoint(
        atMillis = at,
        lat = lat,
        lng = lng,
        speedKmh = speed,
        ignition = ignition,
        gap = gap,
    )
}

private fun RouteStopDto.toDomainOrNull(): RouteStop? {
    val start = millis(from) ?: return null
    val end = millis(to) ?: return null
    return RouteStop(
        fromMillis = start,
        toMillis = end,
        minutes = minutes,
        lat = lat,
        lng = lng,
        idling = idling,
        open = open,
    )
}

private fun RouteTripDto.toDomainOrNull(): RouteTrip? {
    val dep = millis(departedAt) ?: return null
    val arr = millis(arrivedAt) ?: return null
    return RouteTrip(
        index = index,
        departedMillis = dep,
        arrivedMillis = arr,
        minutes = minutes,
        distanceKm = distanceKm,
        fromLat = fromLat,
        fromLng = fromLng,
        toLat = toLat,
        toLng = toLng,
        maxSpeed = maxSpeed,
        avgSpeed = avgSpeed,
        speedingCount = speedingCount,
        open = open,
        driverLabel = driverLabel?.takeIf { it.isNotBlank() },
        startOdometerKm = startOdometerKm,
        endOdometerKm = endOdometerKm,
    )
}

private fun SpeedingRunDto.toDomainOrNull(): SpeedingRun? {
    val start = millis(from) ?: return null
    val end = millis(to) ?: return null
    return SpeedingRun(
        fromMillis = start,
        toMillis = end,
        seconds = seconds,
        maxSpeed = maxSpeed,
        limit = limit,
        lat = lat,
        lng = lng,
        distanceKm = distanceKm,
        tripIndex = tripIndex,
    )
}

private fun HarshEventDto.toDomainOrNull(): HarshEvent? {
    val when_ = millis(at) ?: return null
    return HarshEvent(
        atMillis = when_,
        lat = lat,
        lng = lng,
        type = HarshType.fromWire(type),
        fromDevice = source == "device",
        fromSpeed = fromSpeed,
        toSpeed = toSpeed,
        accelMs2 = accelMs2,
    )
}

private fun RouteSummaryDto.toDomain(): RouteSummary = RouteSummary(
    distanceKm = distanceKm,
    drivingMinutes = drivingMinutes,
    stopMinutes = stopMinutes,
    idleMinutes = idleMinutes,
    maxSpeed = maxSpeed,
    firstDepartureMillis = millis(firstDepartureAt),
    lastArrivalMillis = millis(lastArrivalAt),
    trips = trips,
    stops = stops,
    speedingRuns = speedingRuns,
    speedingMinutes = speedingMinutes,
    longestStopMinutes = longestStopMinutes,
    odometerKm = odometerKm,
    idleCostPln = idleCostPln,
    style = style.toDomain(),
)

private fun DrivingStyleDto.toDomain(): DrivingStyle = DrivingStyle(
    score = score,
    grade = grade,
    speedingPenalty = speedingPenalty,
    harshPenalty = harshPenalty,
    idlePenalty = idlePenalty,
    harshEvents = harshEvents,
    harshFromDevice = harshFromDevice,
)

fun TrackerHealthDto.toDomain(): TrackerHealth = TrackerHealth(
    assetId = assetId,
    assetName = assetName,
    registration = registration?.takeIf { it.isNotBlank() },
    hasTracker = hasTracker,
    status = TrackerStatus.fromWire(status),
    lastContactMillis = millis(lastContactAt),
    silentMinutes = silentMinutes,
    readings = readings,
    coveragePercent = coveragePercent,
    longestGapMinutes = longestGapMinutes,
    voltageV = voltageV,
    minVoltageV = minVoltageV,
    gsmSignal = gsmSignal,
    satellites = satellites,
    faultCodes = faultCodes?.takeIf { it.isNotBlank() },
    issues = issues,
)

private fun millis(iso: String?): Long? =
    iso?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
