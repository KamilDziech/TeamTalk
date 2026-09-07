package com.ekotak.teamtalk.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Plik deala (`GET /api/deals/:id/documents`). Metadane — treść leży w MinIO
 * i schodzi osobnym żądaniem.
 *
 * `planData` zostaje surowym JSON-em z tego samego powodu co `formData` audytu:
 * mieszka w nim przygotowanie rzutu zapisane przez panel, a telefon ma oddać
 * nietknięte te pola, których nie edytuje (podpisy autorów, historia).
 */
@Serializable
data class DealDocumentDto(
    val id: String,
    val name: String = "",
    val size: Long = 0,
    val contentType: String = "",
    val category: String = "inne",
    val planData: JsonElement? = null,
    val createdAt: String = "",
)

/** Metadane podglądu (`GET /api/documents/:id/preview`). */
@Serializable
data class DocumentPreviewDto(
    val pdf: Boolean = false,
    val pages: Int = 0,
)
