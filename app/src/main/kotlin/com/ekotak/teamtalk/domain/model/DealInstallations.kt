package com.ekotak.teamtalk.domain.model

/**
 * Etapy „instalacyjne" karty deala — `INSTALLATION_STAGES` z board360
 * (api/src/modules/crm/domain/installation-stages.ts). Każdy trzyma własną
 * migawkę wyboru instalacji klienta; API zwraca już wybór efektywny, czyli
 * z dziedziczeniem z wcześniejszych etapów.
 */
enum class InstallationStage(val wire: String, val label: String) {
    LEAD("lead", "LEAD"),
    EDUKACJA("edukacja", "Remarketing"),
    AUDIT("audit", "Audyt"),
    ANGEBOT("angebot", "Oferta"),
    SOLD("sold", "Zamówienie"),
    MONTAZ("montaz", "Montaż"),
    FERTIG("fertig", "Po montażu");

    companion object {
        fun fromWire(value: String?): InstallationStage? = entries.firstOrNull { it.wire == value }
    }
}

/** Stan migawki względem pozycji deala na osi (`past` / `current` / `future`). */
enum class InstallationStageState(val wire: String) {
    PAST("past"),
    CURRENT("current"),
    FUTURE("future");

    companion object {
        fun fromWire(value: String?): InstallationStageState =
            entries.firstOrNull { it.wire == value } ?: PAST
    }
}

/** Migawka jednego etapu: wybrane węzły katalogu + czy wolno ją zmieniać. */
data class StageInstallations(
    val stage: InstallationStage,
    val categoryIds: List<String> = emptyList(),
    val editable: Boolean = false,
    val state: InstallationStageState = InstallationStageState.PAST,
)

/**
 * Komplet migawek karty deala (`GET /api/deals/:id/installations`).
 * `current` to etap, na którym deal stoi; `null` dla deala w Archiwum.
 */
data class DealInstallations(
    val current: InstallationStage? = null,
    val stages: List<StageInstallations> = emptyList(),
) {
    fun forStage(stage: InstallationStage): StageInstallations? =
        stages.firstOrNull { it.stage == stage }
}
