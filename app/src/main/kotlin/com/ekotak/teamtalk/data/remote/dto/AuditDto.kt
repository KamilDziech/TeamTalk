package com.ekotak.teamtalk.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Audyt deala (`GET /api/deals/:id/audits`). `formData` zostaje surowym
 * JSON-em: mieszkają w nim dwie różne struktury (notatka Heizlast albo pełny
 * formularz instalacji), a telefon musi umieć oddać nietknięte te pola, których
 * nie edytuje — patrz `UfhFloor.planJson`.
 */
@Serializable
data class AuditDto(
    val id: String,
    val dealId: String = "",
    val heatloadMode: String? = null,
    val heatloadKw: Double? = null,
    val formData: JsonObject? = null,
    val createdAt: String = "",
)

/**
 * Umowa deala (`GET /api/deals/:id/contracts`) — bierzemy z niej wyłącznie to,
 * co rozstrzyga, czy oferta jest już zamknięta podpisem. Reszta pól (linki do
 * podpisu, parafy, wysyłki mailowe) zostaje w panelu: telefon umowami nie
 * zarządza.
 */
@Serializable
data class ContractSummaryDto(
    val id: String,
    val numer: String = "",
    val status: String = "",
    /** `umowa` | `aneks` — przy zmianie oferty rozstrzyga o rodzaju dokumentu. */
    val rodzaj: String = "",
    val podpisana: String? = null,
    /** Numer dokumentu, który ta wersja zastępuje; ustawione tylko na zmianach. */
    val zastepuje: String? = null,
    /** Numer wersji, która zastąpiła ten dokument; `null` = nadal aktualny. */
    val zastapionaPrzez: String? = null,
)
