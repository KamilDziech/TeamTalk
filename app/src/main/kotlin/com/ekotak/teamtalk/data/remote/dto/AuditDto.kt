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

