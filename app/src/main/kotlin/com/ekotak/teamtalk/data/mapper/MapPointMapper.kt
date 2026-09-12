package com.ekotak.teamtalk.data.mapper

import com.ekotak.teamtalk.data.local.entity.MapPointEntity
import com.ekotak.teamtalk.domain.model.FleetInfo
import com.ekotak.teamtalk.domain.model.MapBadge
import com.ekotak.teamtalk.domain.model.MapKind
import com.ekotak.teamtalk.domain.model.MapPoint
import com.ekotak.teamtalk.domain.model.fleetBadge

/**
 * Punkt mapy z cache Room na model domenowy.
 *
 * Pojazd dostaje badge PRZELICZONY na moment odczytu, a nie ten zapisany przy
 * pobraniu: mapa otwierana bez zasięgu pokazuje migawkę sprzed godzin i „W
 * ruchu" byłoby wtedy nieprawdą. Reszta punktów ma badge stały (etap deala nie
 * zmienia się od patrzenia), więc idzie z bazy.
 */
fun MapPointEntity.toDomain(now: Long = System.currentTimeMillis()): MapPoint = MapPoint(
    id = id,
    kind = MapKind.fromWire(kind),
    lat = lat,
    lng = lng,
    name = name,
    city = city,
    address = address,
    phone = phone,
    installs = installs,
    ownerId = ownerId,
    ownerLabel = ownerLabel,
    stageOwnerId = stageOwnerId,
    stageOwnerLabel = stageOwnerLabel,
    technicianId = technicianId,
    technicianLabel = technicianLabel,
    badge = fleetInfo()?.let { fleetBadge(it, now) } ?: MapBadge(
        key = badgeKey,
        label = badgeLabel,
        colorArgb = badgeColor,
        order = badgeOrder,
        letter = badgeLetter,
    ),
    dealId = dealId,
    clientId = clientId,
    fleet = fleetInfo(),
)

/** Dane lokalizatora z wiersza; null = punkt nie jest pojazdem. */
private fun MapPointEntity.fleetInfo(): FleetInfo? {
    val hasTracker = fleetHasTracker ?: return null
    return FleetInfo(
        occurredAt = fleetOccurredAt,
        speedKmh = fleetSpeed,
        ignition = fleetIgnition,
        hasTracker = hasTracker,
    )
}

/** Punkt do zapisu w cache. `syncedAt` stempluje repozytorium przy zapisie. */
fun MapPoint.toEntity(syncedAt: Long): MapPointEntity = MapPointEntity(
    id = id,
    kind = kind.wire,
    lat = lat,
    lng = lng,
    name = name,
    city = city,
    address = address,
    phone = phone,
    installs = installs,
    ownerId = ownerId,
    ownerLabel = ownerLabel,
    stageOwnerId = stageOwnerId,
    stageOwnerLabel = stageOwnerLabel,
    technicianId = technicianId,
    technicianLabel = technicianLabel,
    badgeKey = badge.key,
    badgeLabel = badge.label,
    badgeColor = badge.colorArgb,
    badgeOrder = badge.order,
    badgeLetter = badge.letter,
    dealId = dealId,
    clientId = clientId,
    fleetOccurredAt = fleet?.occurredAt,
    fleetSpeed = fleet?.speedKmh,
    fleetIgnition = fleet?.ignition,
    fleetHasTracker = fleet?.hasTracker,
    syncedAt = syncedAt,
)
