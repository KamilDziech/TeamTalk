package com.ekotak.teamtalk.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Migawki instalacji karty deala (`GET /api/deals/:id/installations`).
 * `current` to etap, na którym deal stoi na osi instalacyjnej; każdy wpis
 * `stages` niesie już wybór EFEKTYWNY (z dziedziczeniem z wcześniejszych
 * etapów), więc mobilna karta nie musi liczyć carry-forward u siebie.
 */
@Serializable
data class DealInstallationsDto(
    val current: String? = null,
    val stages: List<DealInstallationStageDto> = emptyList(),
)

@Serializable
data class DealInstallationStageDto(
    val stage: String = "",
    /** Id węzłów katalogu — dowolna głębokość, nie tylko kategorie główne. */
    val categories: List<String> = emptyList(),
    val editable: Boolean = false,
    val state: String = "past",
)
