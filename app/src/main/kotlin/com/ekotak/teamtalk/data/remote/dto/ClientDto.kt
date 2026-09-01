package com.ekotak.teamtalk.data.remote.dto

import kotlinx.serialization.Serializable

/** Współrzędne zwalidowanego adresu (board360 zwraca je jako obiekt `geo`). */
@Serializable
data class ClientGeoDto(val lat: Double, val lng: Double)

/** Odległość po drodze [km] i czas dojazdu [min] z jednej bazy ekotak. */
@Serializable
data class TravelLegDto(val km: Double, val min: Double)

@Serializable
data class ClientTravelDto(
    val kobiernice: TravelLegDto? = null,
    val gliwice: TravelLegDto? = null,
)

/** Klient — kształt board360 (`GET /api/clients`). */
@Serializable
data class ClientResponseDto(
    val id: String,
    val organizationId: String? = null,
    val firstName: String = "",
    val lastName: String = "",
    val email: String? = null,
    val email2: String? = null,
    val phone: String? = null,
    val phone2: String? = null,
    val address: String? = null,
    val postalCode: String? = null,
    val city: String? = null,
    val street: String? = null,
    val geo: ClientGeoDto? = null,
    val geoCity: String? = null,
    val geoMunicipality: String? = null,
    val travel: ClientTravelDto? = null,
    val type: String? = null,
    val category: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

/** Ciało `POST /api/clients`. Minimum wymagane przez API: imię i nazwisko. */
@Serializable
data class CreateClientRequest(
    val firstName: String,
    val lastName: String,
    val email: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val type: String? = null,
    val category: String? = null,
)

/** Ciało `POST /api/clients/:id/merge` — rekordy scalane w klienta `:id`. */
@Serializable
data class MergeClientsRequest(val sourceIds: List<String>)

@Serializable
data class AssistantMessageDto(val role: String, val content: String)

/** Ciało `POST /api/clients/:id/assistant` — krótka historia rozmowy. */
@Serializable
data class ClientAssistantRequest(val messages: List<AssistantMessageDto>)

@Serializable
data class ClientAssistantReplyDto(
    val text: String = "",
    /** false = brak klucza LLM po stronie serwera (odpowiedź informacyjna). */
    val configured: Boolean = false,
    val commsCount: Int = 0,
    val dealCount: Int = 0,
)
