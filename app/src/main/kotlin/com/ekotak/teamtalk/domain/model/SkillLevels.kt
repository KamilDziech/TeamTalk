package com.ekotak.teamtalk.domain.model

/**
 * „Moje poziomy" — port `web/src/lib/skill-domain-people.ts` (część dla jednej
 * osoby). Tabelę prowadzi zarząd w module Zespół; pracownik ma ją wyłącznie do
 * wglądu, bo to jest odpowiedź na pytanie „po co mi to szkolenie".
 *
 * ⚠️ Szczebel dzieli się na TEORIĘ i PRAKTYKĘ i to jest realna różnica („umie
 * zamontować, ale nie wie, gdzie szukać w katalogu"). Praktyka zakłada teorię —
 * wymóg praktyczny wystawia lukę także na części teoretycznej; odwrotnie nie.
 */

/** Trzy szczeble skali, w kolejności rosnącej (`SKILL_LEVELS` w panelu). */
enum class SkillLevel(val wire: String, val label: String) {
    PODSTAWOWY("podstawowy", "podstawowy"),
    SREDNI("sredni", "średnio zaawansowany"),
    ZAAWANSOWANY("zaawansowany", "zaawansowany"),
    ;

    companion object {
        fun from(wire: String?): SkillLevel? = entries.firstOrNull { it.wire == wire }
    }
}

enum class SkillPart(val wire: String, val label: String) {
    TEORIA("teoria", "teoria"),
    PRAKTYKA("praktyka", "praktyka"),
    ;

    companion object {
        fun from(wire: String?): SkillPart? = entries.firstOrNull { it.wire == wire }
    }
}

/** Część szczebla — tak trzyma to kolumna `levels` (`sredni:teoria`). */
data class LevelToken(val level: SkillLevel, val part: SkillPart) {
    val label: String get() = "${level.label} (${part.label})"
}

/** Opis domeny w widoku pracownika — wycinek drzewa, które jest admin-only. */
data class SkillDomainInfo(
    val id: String,
    val name: String,
    /** `rdzen` / `ogolna` / `biurowa` / `instalacja` — steruje kolejnością. */
    val kind: String,
    val desc: String,
    /** Założenie poziomu: co człowiek na tym szczeblu potrafi. */
    val goals: Map<String, String>,
)

/** Wiersz matrycy dla zalogowanego: co ma i czego się od niego wymaga. */
data class DomainSkillCell(
    val domainId: String,
    val levels: List<LevelToken>,
    /** Czy ktokolwiek oceniał — odróżnia „nie wiemy" od „sprawdzone: nic". */
    val assessed: Boolean,
    val requiredLevel: SkillLevel?,
    val requiredPart: SkillPart,
    /** Termin nadgonienia braków, `YYYY-MM-DD`. */
    val requiredUntil: String?,
    val note: String?,
)

/** Domena + stan + luki, gotowe do wyświetlenia. */
data class MySkillEntry(
    val domain: SkillDomainInfo,
    val cell: DomainSkillCell,
    val missing: List<LevelToken>,
)

/** Odpowiedź `GET /api/domain-skills/me` po zmapowaniu. */
data class MySkills(
    val cells: List<DomainSkillCell>,
    val domains: List<SkillDomainInfo>,
) {
    companion object {
        val EMPTY = MySkills(emptyList(), emptyList())
    }
}

private val KIND_ORDER = listOf("rdzen", "ogolna", "biurowa", "instalacja")

/**
 * Surowe `levels` → tokeny z częścią. Goły szczebel (`sredni`) to zapis sprzed
 * podziału na teorię i praktykę i znaczy OBIE części — panel czyta to tak samo,
 * więc wiersze zapisane przed 2026-08-21 wyglądają identycznie po obu stronach.
 */
fun expandLevels(raw: List<String>?): List<LevelToken> {
    val out = mutableListOf<LevelToken>()
    for (entry in raw.orEmpty()) {
        val parts = entry.split(":")
        val level = SkillLevel.from(parts.getOrNull(0)) ?: continue
        val declared = SkillPart.from(parts.getOrNull(1))
        for (part in declared?.let { listOf(it) } ?: SkillPart.entries) {
            val token = LevelToken(level, part)
            if (token !in out) out += token
        }
    }
    return out.sortedWith(compareBy({ it.level.ordinal }, { it.part.ordinal }))
}

/**
 * Czego żąda wymóg: wszystkie szczeble DO `required` włącznie, na każdym
 * teoria — i praktyka, gdy wymóg sięga praktyki.
 */
fun requiredTokens(required: SkillLevel?, part: SkillPart): List<LevelToken> {
    if (required == null) return emptyList()
    val parts = if (part == SkillPart.TEORIA) listOf(SkillPart.TEORIA) else SkillPart.entries.toList()
    return SkillLevel.entries
        .filter { it.ordinal <= required.ordinal }
        .flatMap { level -> parts.map { LevelToken(level, it) } }
}

/** Części szczebli wymagane, których człowiek nie ma — to jest „do nadgonienia". */
fun missingLevels(cell: DomainSkillCell): List<LevelToken> =
    requiredTokens(cell.requiredLevel, cell.requiredPart).filter { it !in cell.levels }

/**
 * Wiersze jednej osoby bez komórek, które jej nie dotyczą. Braki idą na górę —
 * po to pracownik tu zagląda; dalej kolejność gałęzi drzewa i nazwa.
 */
fun mineEntries(skills: MySkills): List<MySkillEntry> {
    val byId = skills.domains.associateBy { it.id }
    return skills.cells
        .mapNotNull { cell ->
            val domain = byId[cell.domainId] ?: return@mapNotNull null
            if (cell.requiredLevel == null && cell.levels.isEmpty() && !cell.assessed) return@mapNotNull null
            MySkillEntry(domain = domain, cell = cell, missing = missingLevels(cell))
        }
        .sortedWith(
            compareByDescending<MySkillEntry> { it.missing.isNotEmpty() }
                .thenBy { KIND_ORDER.indexOf(it.domain.kind).takeIf { i -> i >= 0 } ?: KIND_ORDER.size }
                .thenBy { it.domain.name },
        )
}

/** Opis wymogu: „średnio zaawansowany (teoria + praktyka)" albo „nie dotyczy". */
fun requirementLabel(required: SkillLevel?, part: SkillPart): String = when {
    required == null -> "nie dotyczy"
    part == SkillPart.TEORIA -> "${required.label} (sama teoria)"
    else -> "${required.label} (teoria + praktyka)"
}
