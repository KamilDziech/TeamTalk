package com.ekotak.teamtalk.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Zatwierdzone rozliczenie instalacji deala
 * (`GET/PUT/DELETE /api/financial-terms/settlements/:dealId[/:categoryId]`).
 *
 * `breakdown` zostaje surowym JSON-em — to migawka rozbicia z chwili
 * zatwierdzenia, którą czyta panel. Telefon jej nie rozbiera na pola: rozkładać
 * ją tylko po to, żeby złożyć z powrotem przy wysyłce, znaczyłoby gubić to,
 * czego jeszcze nie znamy.
 */
@Serializable
data class DealSettlementDto(
    val id: String = "",
    val dealId: String = "",
    val categoryId: String = "",
    val totalPoints: Double = 0.0,
    val breakdown: JsonObject? = null,
    val approvedById: String? = null,
    val approvedAt: String = "",
)
