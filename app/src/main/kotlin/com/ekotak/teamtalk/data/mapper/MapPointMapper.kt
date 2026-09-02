package com.ekotak.teamtalk.data.mapper

import com.ekotak.teamtalk.data.local.entity.MapPointEntity
import com.ekotak.teamtalk.domain.model.MapBadge
import com.ekotak.teamtalk.domain.model.MapKind
import com.ekotak.teamtalk.domain.model.MapPoint

/** Punkt mapy z cache Room na model domenowy. */
fun MapPointEntity.toDomain(): MapPoint = MapPoint(
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
    badge = MapBadge(
        key = badgeKey,
        label = badgeLabel,
        colorArgb = badgeColor,
        order = badgeOrder,
        letter = badgeLetter,
    ),
    dealId = dealId,
    clientId = clientId,
)

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
    syncedAt = syncedAt,
)
