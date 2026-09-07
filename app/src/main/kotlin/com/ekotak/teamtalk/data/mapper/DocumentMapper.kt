package com.ekotak.teamtalk.data.mapper

import com.ekotak.teamtalk.data.local.entity.DealDocumentEntity
import com.ekotak.teamtalk.data.remote.dto.DealDocumentDto
import com.ekotak.teamtalk.domain.model.DealDocument
import com.ekotak.teamtalk.domain.model.DocumentCategory
import kotlinx.serialization.json.Json

/**
 * Pliki deala: DTO → cache → model. `planData` przechodzi jako TEKST, nie jako
 * rozpakowany obiekt — telefon edytuje z niego tylko obrysy i skalę, a resztę
 * (podpisy autorów, historia panelu) ma oddać nietkniętą.
 */

fun DealDocumentDto.toEntity(dealId: String, syncedAt: Long): DealDocumentEntity =
    DealDocumentEntity(
        id = id,
        dealId = dealId,
        name = name,
        size = size,
        contentType = contentType,
        category = category,
        planDataJson = planData?.toString(),
        createdAt = createdAt,
        pending = false,
        localPath = null,
        syncedAt = syncedAt,
    )

fun DealDocumentEntity.toDomain(json: Json): DealDocument = DealDocument(
    id = id,
    dealId = dealId,
    name = name,
    size = size,
    contentType = contentType,
    category = DocumentCategory.fromWire(category),
    // Uszkodzony zapis nie może wywrócić całej zakładki: plik bez przygotowania
    // rzutu jest w pełni użytecznym plikiem.
    planData = planDataJson?.let { runCatching { json.parseToJsonElement(it) }.getOrNull() },
    createdAt = createdAt,
    pending = pending,
    localPath = localPath,
)
