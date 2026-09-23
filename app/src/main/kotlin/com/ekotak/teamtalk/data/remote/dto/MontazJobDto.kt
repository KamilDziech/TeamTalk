package com.ekotak.teamtalk.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * MODUŁ MONTAŻ — kontrakt teczki wyjazdu (`/api/installations/my`,
 * `/api/installations/{id}/job`, `/api/installations/{id}/status`).
 *
 * Teczkę składa SERWER, nie telefon, i to nie jest wygoda, tylko konieczność:
 * rola `montaz` ma `installation.view` i nic z okolic sprzedaży — bez
 * `crm.view` nie odczyta kartoteki ani umowy, bez `catalog.view` nie zobaczy
 * drzewa, z którego biorą się lista sprzętu i pytania protokołu. Panel składa
 * to sobie z pięciu żądań, bo koordynator ma prawa do wszystkiego; montażysta
 * dostaje jedną paczkę — i tę samą paczkę telefon trzyma w pamięci na czas
 * roboty w kotłowni bez zasięgu.
 */

/** Klient i miejsce roboty — tyle, ile trzeba, żeby dojechać i zadzwonić. */
@Serializable
data class JobClientDto(
    val name: String = "",
    val address: String? = null,
    val city: String? = null,
    val phone: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
)

/** Wiersz listy „moje montaże" (`GET /api/installations/my`). */
@Serializable
data class JobRowDto(
    val id: String = "",
    val dealId: String = "",
    val dealCode: String? = null,
    val scheduledAt: String? = null,
    val status: String = "planned",
    val difficulty: String? = null,
    val durationDays: Int? = null,
    val nodeIds: List<String> = emptyList(),
    val client: JobClientDto = JobClientDto(),
)

@Serializable
data class JobAssigneeDto(
    val userId: String = "",
    val name: String = "",
    val role: String? = null,
)

@Serializable
data class JobScopeItemDto(
    val lp: Int = 0,
    val opis: String = "",
    val ilosc: Double = 0.0,
    val jm: String = "",
    val etap: Int = 1,
)

@Serializable
data class JobContractDto(
    val numer: String = "",
    val podpisana: String? = null,
    val pozycje: List<JobScopeItemDto> = emptyList(),
    val wylaczony: List<String> = emptyList(),
)

/** Pozycja listy wyjazdowej sprzętu — scalona po stronie serwera. */
@Serializable
data class JobToolDto(
    val name: String = "",
    val group: String = "",
    val qty: Double? = null,
    val unit: String = "szt.",
    val required: Boolean = false,
    val beacon: Boolean = false,
    val owner: String = "",
    val note: String = "",
    val fromNodes: List<String> = emptyList(),
)

/** Pytanie protokołu po scaleniu zakresu (zakładka „📋 Protokół" katalogu). */
@Serializable
data class JobProtocolItemDto(
    val id: String = "",
    val label: String = "",
    val group: String = "Wykonanie",
    val kind: String = "check",
    val unit: String = "",
    val options: List<String> = emptyList(),
    val required: Boolean = false,
    val photo: String = "none",
    val photoMin: Int? = null,
    val hint: String = "",
    val fromNodes: List<String> = emptyList(),
)

/** Cała teczka wyjazdu (`GET /api/installations/{id}/job`). */
@Serializable
data class JobPacketDto(
    val installationId: String = "",
    val dealId: String = "",
    val dealCode: String? = null,
    val scheduledAt: String? = null,
    val status: String = "planned",
    val durationDays: Int? = null,
    val difficulty: String? = null,
    val teamNote: String? = null,
    val nodeIds: List<String> = emptyList(),
    val scopeNames: List<String> = emptyList(),
    val client: JobClientDto = JobClientDto(),
    val contract: JobContractDto? = null,
    val assignees: List<JobAssigneeDto> = emptyList(),
    val tools: List<JobToolDto> = emptyList(),
    val protocol: List<JobProtocolItemDto> = emptyList(),
    val toolNotes: List<String> = emptyList(),
)

/**
 * Ciało `POST /api/installations/{id}/status` — start i koniec roboty.
 * Osobna trasa od `PATCH /installations/{id}`, bo tamta wymaga
 * `installation.assign` (zmienia też termin i obsadę); tutaj wolno JEDNO
 * i tylko komuś z obsady tego montażu.
 */
@Serializable
data class JobStatusRequest(val status: String)

/** Protokół odbioru montażu (`GET|PUT /api/installations/{id}/protocol`). */
@Serializable
data class ProtocolDto(
    val id: String = "",
    val installationId: String = "",
    /** Wolny JSON — kształt opisuje `PROTOCOL_SCHEMA_VERSION`. */
    val formData: JsonObject? = null,
    /** Podpis klienta jako data URL (`data:image/png;base64,…`). */
    val signature: String? = null,
)

/** Ciało `PUT /api/installations/{id}/protocol` (upsert 1:1 z montażem). */
@Serializable
data class ProtocolSaveRequest(
    val formData: JsonObject,
    val signature: String? = null,
)
