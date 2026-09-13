package com.ekotak.teamtalk.domain.montaz

import com.ekotak.teamtalk.domain.model.Category
import com.ekotak.teamtalk.domain.model.MontazAssignee

/**
 * POKRYCIE ZAKRESU MONTAŻOWEGO — most katalog → obsada montażu. Port
 * `web/src/app/app/crm/montaz-roles.ts`, znak w znak.
 *
 * Rachunek jest CZYSTY (zero Androida, zero zapisu): na wejściu zakres montażu
 * (węzły drzewa instalacji), katalog i ludzie, na wyjściu odpowiedź na jedno
 * pytanie — czy ta obsada w ogóle może wykonać tę robotę, a jeśli nie, to kogo
 * brakuje.
 *
 * DWIE ZASADY (te same, co w panelu):
 *  1. Zakres montażowy DZIEDZICZY SIĘ W DÓŁ. Węzeł producenta („Gree Versati IV")
 *     zwykle nie ma własnych `montageRoles` — bierze je od technologii-rodzica.
 *  2. Rolę domyka PRZYPISANIE, nie umiejętność. Że ktoś UMIE zrobić próbę
 *     szczelności, to jeszcze nie znaczy, że jedzie ją zrobić — stąd stan
 *     pośredni [RoleState.SKILLED], a nie zielone światło.
 */

/** Minimum z członka zespołu — umiejętności to nazwy ról montażowych. */
data class RolePerson(val id: String, val skills: List<String>)

enum class RoleState { ASSIGNED, SKILLED, MISSING }

data class RoleCoverage(
    val role: String,
    val state: RoleState,
    /** Osoby z obsady, które tę rolę mają wskazaną. */
    val assignedIds: List<String>,
    /** Osoby z obsady umiejące tę rolę — podstawa stanu [RoleState.SKILLED]. */
    val skilledIds: List<String>,
    /** Spoza obsady: kto ma tę umiejętność i można go dopisać. */
    val candidateIds: List<String>,
)

/**
 * Role wymagane przez zakres — suma zakresów montażowych węzłów objętych
 * montażem, z dziedziczeniem po rodzicu. Kolejność jak w katalogu (Hydraulik
 * przed Pomocnikiem), bez duplikatów.
 */
fun requiredRoles(nodeIds: List<String>, byId: Map<String, Category>): List<String> {
    val out = mutableListOf<String>()
    for (id in nodeIds) {
        for (role in inheritedRoles(id, byId)) if (role !in out) out += role
    }
    return out
}

/** Zakres montażowy węzła — własny albo najbliższego przodka, który go ma. */
private fun inheritedRoles(nodeId: String, byId: Map<String, Category>): List<String> {
    var cur = byId[nodeId]
    var guard = 0
    // Limit kroków jak w panelu: katalog z zapętlonym rodzicem (ręczna zmiana
    // w bazie) ma dać pustą listę, a nie zawiesić ekran na budowie.
    while (cur != null && guard++ < 20) {
        if (cur.montageRoles.isNotEmpty()) return cur.montageRoles
        cur = cur.parentId?.let { byId[it] }
    }
    return emptyList()
}

/** Stan każdej wymaganej roli wobec konkretnej obsady. */
fun roleCoverage(
    nodeIds: List<String>,
    byId: Map<String, Category>,
    assignees: List<MontazAssignee>,
    people: List<RolePerson>,
): List<RoleCoverage> {
    val skillsOf = people.associate { it.id to it.skills }
    val crewIds = assignees.map { it.userId }.toSet()

    return requiredRoles(nodeIds, byId).map { role ->
        val assignedIds = assignees.filter { it.role == role }.map { it.userId }
        val skilledIds = assignees
            .filter { role in skillsOf[it.userId].orEmpty() }
            .map { it.userId }
        val candidateIds = people
            .filter { it.id !in crewIds && role in it.skills }
            .map { it.id }
        RoleCoverage(
            role = role,
            state = when {
                assignedIds.isNotEmpty() -> RoleState.ASSIGNED
                skilledIds.isNotEmpty() -> RoleState.SKILLED
                else -> RoleState.MISSING
            },
            assignedIds = assignedIds,
            skilledIds = skilledIds,
            candidateIds = candidateIds,
        )
    }
}

/** Rodzaj podsumowania obsady — decyduje o kolorze plakietki i o komunikacie. */
enum class CoverageKind { NO_SCOPE, NO_ROLES, GAP, PARTIAL, FULL }

data class CoverageSummary(val kind: CoverageKind, val text: String)

/**
 * Jednozdaniowe podsumowanie do plakietki nagłówka. Rozróżnia „brak zakresu"
 * (montaż bez przypisanych węzłów) od „zakres bez ról" (katalog nie ma
 * zdefiniowanego zakresu montażowego) — to dwa różne braki i dwie różne akcje.
 */
fun coverageSummary(nodeIds: List<String>, coverage: List<RoleCoverage>): CoverageSummary {
    if (nodeIds.isEmpty()) return CoverageSummary(CoverageKind.NO_SCOPE, "brak zakresu")
    if (coverage.isEmpty()) {
        return CoverageSummary(CoverageKind.NO_ROLES, "katalog bez zakresu montażowego")
    }
    val missing = coverage.count { it.state == RoleState.MISSING }
    val skilled = coverage.count { it.state == RoleState.SKILLED }
    return when {
        missing > 0 -> CoverageSummary(CoverageKind.GAP, "brakuje ról: $missing")
        skilled > 0 -> CoverageSummary(CoverageKind.PARTIAL, "role bez wskazania: $skilled")
        else -> CoverageSummary(CoverageKind.FULL, "obsada pełna")
    }
}

/**
 * Rola proponowana przy dopisaniu osoby: pierwsza WYMAGANA rola, którą ta osoba
 * umie i której nikt jeszcze nie obsadził. Bez tego dopisanie całej ekipy
 * zostawiałoby same puste role do wyklikania palcem na budowie.
 *
 * [taken] = role zajęte w TEJ operacji (dopisywanie kilku osób naraz); przy
 * pojedynczym dopisaniu wystarcza stan z [coverage].
 */
fun suggestRole(
    userId: String,
    coverage: List<RoleCoverage>,
    skills: List<String>,
    taken: Set<String> = emptySet(),
): String? = coverage
    .firstOrNull { it.state != RoleState.ASSIGNED && it.role !in taken && it.role in skills }
    ?.role
