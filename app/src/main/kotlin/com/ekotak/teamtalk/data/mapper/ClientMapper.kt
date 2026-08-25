package com.ekotak.teamtalk.data.mapper

import com.ekotak.teamtalk.data.local.entity.ClientEntity
import com.ekotak.teamtalk.data.remote.dto.ClientResponseDto
import com.ekotak.teamtalk.domain.model.Client

fun ClientResponseDto.toEntity(): ClientEntity = ClientEntity(
    id         = id,
    firstName  = firstName,
    lastName   = lastName,
    email      = email,
    email2     = email2,
    phone      = phone,
    phone2     = phone2,
    address    = address,
    postalCode = postalCode,
    city       = city,
    street     = street,
    geoLat     = geoLat,
    geoLng     = geoLng,
    type       = type,
    category   = category,
    createdAt  = createdAt,
    updatedAt  = updatedAt,
)

fun ClientEntity.toDomain(): Client = Client(
    id         = id,
    firstName  = firstName,
    lastName   = lastName,
    email      = email,
    email2     = email2,
    phone      = phone,
    phone2     = phone2,
    address    = address,
    postalCode = postalCode,
    city       = city,
    street     = street,
    geoLat     = geoLat,
    geoLng     = geoLng,
    type       = type,
    category   = category,
    createdAt  = createdAt,
    updatedAt  = updatedAt,
)

fun ClientResponseDto.toDomain(): Client = Client(
    id         = id,
    firstName  = firstName,
    lastName   = lastName,
    email      = email,
    email2     = email2,
    phone      = phone,
    phone2     = phone2,
    address    = address,
    postalCode = postalCode,
    city       = city,
    street     = street,
    geoLat     = geoLat,
    geoLng     = geoLng,
    type       = type,
    category   = category,
    createdAt  = createdAt,
    updatedAt  = updatedAt,
)
