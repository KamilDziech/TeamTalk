package com.ekotak.teamtalk.domain.model

/**
 * Sekcja zadania — rozwijalna grupa na liście, ta sama co w module „Zadania"
 * board360. Wartości to etapy lejka plus „dotacja", która etapem nie jest.
 *
 * Lustro słownika `web/src/app/app/tasks/task-sections.ts` i enumu `TaskSection`
 * w schemacie API. Kolejność = kolejność nagłówków od góry. Zmiana słownika po
 * stronie board360 wymaga ręcznej aktualizacji tego pliku — tak samo jak przy
 * [TaskTeam], bo kod się nie współdzieli.
 */
enum class TaskSection(val wire: String, val label: String) {
    AUDYT("audyt", "Audyt"),
    OFERTA("oferta", "Oferta"),
    WSTRZYMANE("wstrzymane", "Wstrzymane"),
    SPRZEDANE("sprzedane", "Sprzedane"),
    PRZED_MONTAZEM("przed_montazem", "Przed montażem"),
    OCZEKIWANIE("oczekiwanie", "Oczekiwanie"),
    MONTAZ("montaz", "Montaż"),
    PO_MONTAZU("po_montazu", "Po montażu"),
    DOTACJA("dotacja", "Dotacja");

    companion object {
        /** Nieznana albo pusta sekcja → `null`, czyli kubełek „Bez sekcji". */
        fun fromWire(value: String?): TaskSection? =
            entries.firstOrNull { it.wire == value }
    }
}

/** Nagłówek kubełka dla zadań bez sekcji — zawsze na dole listy. */
const val NO_SECTION_LABEL = "Bez sekcji"

/**
 * SLA zadania — czas na realizację liczony od utworzenia karty (nie mylić
 * z „Terminem", który ustawia się ręcznie). W board360 trzymane w godzinach,
 * dozwolone są tylko te trzy wartości — inne API odrzuca kodem 422.
 */
enum class SlaOption(val hours: Int, val label: String) {
    H24(24, "24 h"),
    D7(168, "7 dni"),
    D30(720, "30 dni");

    companion object {
        fun fromHours(hours: Int?): SlaOption? = entries.firstOrNull { it.hours == hours }
    }
}

/** Etykieta SLA do znacznika; brak SLA opisujemy wprost. */
fun slaLabel(hours: Int?): String = SlaOption.fromHours(hours)?.label ?: "Bez SLA"
