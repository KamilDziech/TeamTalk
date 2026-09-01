package com.ekotak.teamtalk.data.mapper

import com.ekotak.teamtalk.data.local.entity.ClientEntity
import com.ekotak.teamtalk.data.remote.dto.ClientResponseDto
import com.ekotak.teamtalk.domain.model.Client
import com.ekotak.teamtalk.domain.model.ClientCategory
import com.ekotak.teamtalk.domain.model.ClientTravel
import com.ekotak.teamtalk.domain.model.ClientType
import com.ekotak.teamtalk.domain.model.TravelLeg

fun ClientResponseDto.toEntity(): ClientEntity = ClientEntity(
    id                  = id,
    firstName           = firstName,
    lastName            = lastName,
    email               = email,
    email2              = email2,
    phone               = phone,
    phone2              = phone2,
    address             = address,
    postalCode          = postalCode,
    city                = city,
    street              = street,
    geoLat              = geo?.lat,
    geoLng              = geo?.lng,
    geoCity             = geoCity,
    geoMunicipality     = geoMunicipality,
    travelKobierniceKm  = travel?.kobiernice?.km,
    travelKobierniceMin = travel?.kobiernice?.min,
    travelGliwiceKm     = travel?.gliwice?.km,
    travelGliwiceMin    = travel?.gliwice?.min,
    type                = type,
    category            = category,
    createdAt           = createdAt,
    updatedAt           = updatedAt,
)

fun ClientEntity.toDomain(): Client = Client(
    id              = id,
    firstName       = firstName,
    lastName        = lastName,
    email           = email,
    email2          = email2,
    phone           = phone,
    phone2          = phone2,
    address         = address,
    postalCode      = postalCode,
    city            = city,
    street          = street,
    geoLat          = geoLat,
    geoLng          = geoLng,
    geoCity         = geoCity,
    geoMunicipality = geoMunicipality,
    travel          = travelOf(
        kobierniceKm = travelKobierniceKm,
        kobierniceMin = travelKobierniceMin,
        gliwiceKm = travelGliwiceKm,
        gliwiceMin = travelGliwiceMin,
    ),
    type            = ClientType.fromWire(type),
    category        = ClientCategory.fromWire(category),
    createdAt       = createdAt,
    updatedAt       = updatedAt,
)

fun ClientResponseDto.toDomain(): Client = Client(
    id              = id,
    firstName       = firstName,
    lastName        = lastName,
    email           = email,
    email2          = email2,
    phone           = phone,
    phone2          = phone2,
    address         = address,
    postalCode      = postalCode,
    city            = city,
    street          = street,
    geoLat          = geo?.lat,
    geoLng          = geo?.lng,
    geoCity         = geoCity,
    geoMunicipality = geoMunicipality,
    travel          = travel?.let {
        ClientTravel(
            kobiernice = it.kobiernice?.let { leg -> TravelLeg(leg.km, leg.min) },
            gliwice = it.gliwice?.let { leg -> TravelLeg(leg.km, leg.min) },
        )
    },
    type            = ClientType.fromWire(type),
    category        = ClientCategory.fromWire(category),
    createdAt       = createdAt,
    updatedAt       = updatedAt,
)

/** Brak obu tras = dojazd niepoliczony (`null`), nie „trasa nieznaleziona". */
private fun travelOf(
    kobierniceKm: Double?,
    kobierniceMin: Double?,
    gliwiceKm: Double?,
    gliwiceMin: Double?,
): ClientTravel? {
    val kobiernice = if (kobierniceKm != null && kobierniceMin != null) {
        TravelLeg(kobierniceKm, kobierniceMin)
    } else null
    val gliwice = if (gliwiceKm != null && gliwiceMin != null) {
        TravelLeg(gliwiceKm, gliwiceMin)
    } else null
    return if (kobiernice == null && gliwice == null) null else ClientTravel(kobiernice, gliwice)
}
