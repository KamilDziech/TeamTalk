package com.ekotak.teamtalk.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Podpowiedź miejscowości z `GET /api/geo/suggest?q=` (proxy Nominatim po
 * stronie board360). Telefon nie pyta geokodera wprost — publiczna instancja ma
 * limit ~1 zapytania na sekundę na cały ruch, więc buforuje go serwer.
 */
@Serializable
data class PlaceSuggestionDto(
    val label: String,
    val lat: Double,
    val lng: Double,
)
