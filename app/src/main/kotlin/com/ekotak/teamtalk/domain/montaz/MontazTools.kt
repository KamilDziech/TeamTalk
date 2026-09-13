package com.ekotak.teamtalk.domain.montaz

import com.ekotak.teamtalk.domain.model.Category

/**
 * LISTA SPRZĘTU NA MONTAŻ — most katalog → karta montażu. Port
 * `web/src/app/app/crm/montaz-tools.ts` razem z modelem zakładki „🧰 Narzędzia"
 * (`catalog/tools/model.ts`).
 *
 * TRZY ZASADY (te same, co w panelu):
 *  1. Lista DZIEDZICZY SIĘ W DÓŁ, jak zakres montażowy: węzeł producenta zwykle
 *     nie ma własnego sprzętu, więc bierze go od technologii-rodzica.
 *  2. Montaż obejmujący kilka technologii SUMUJE listy, a powtórki scala po
 *     nazwie — tacker jest jeden, choćby wymagały go dwa węzły. Wygrywa większa
 *     ilość i mocniejszy wymóg: brak sprzętu na budowie kosztuje więcej niż
 *     jeden zabrany nadmiarowo.
 *  3. Kolejność sekcji jest kolejnością PAKOWANIA AUTA ([TOOL_GROUPS]), a nie
 *     alfabetem — lista czyta się przy klapie bagażnika.
 */

/** Pozycja z zakładki „🧰 Narzędzia" węzła katalogu. */
data class ToolItem(
    val id: String,
    val name: String,
    val group: String,
    /** Ile sztuk na ekipę; `null` = nieustalone. */
    val qty: Double?,
    val unit: String,
    /** Obowiązkowe (bez tego nie wyjeżdżamy) vs. przydatne. */
    val required: Boolean,
    /** Sprzęt oznakowany beaconem — do sprawdzenia na liście. */
    val beacon: Boolean,
    /** Skąd bierzemy: Ekipa / Magazyn / Wynajem / Podwykonawca. */
    val owner: String,
    val note: String,
)

/** Zawartość `Category.tools` po rozłożeniu na pola. */
data class ToolsScheme(val note: String = "", val items: List<ToolItem> = emptyList())

/** Sekcje listy — kolejność jak przy pakowaniu auta na montaż. */
val TOOL_GROUPS = listOf(
    "Narzędzia osobiste",
    "Elektronarzędzia",
    "Narzędzia ręczne",
    "Sprzęt specjalistyczny",
    "Pomiar i diagnostyka",
    "Materiały pomocnicze",
    "BHP",
    "Zaplecze i transport",
    "Próba szczelności powietrzem",
)

/** Pozycja listy wyjazdowej — pozycja katalogu plus ślad, skąd przyszła. */
data class MontazTool(
    val name: String,
    val group: String,
    val qty: Double?,
    val unit: String,
    val required: Boolean,
    val beacon: Boolean,
    val owner: String,
    val note: String,
    /** Nazwy węzłów, które tego sprzętu wymagają — „po co to wieziemy". */
    val fromNodes: List<String>,
) {
    /** Klucz do odhaczania na liście pakowania — stały między odczytami. */
    val key: String get() = name.trim().lowercase()
}

/** Sekcja listy (kolejność pakowania auta). */
data class MontazToolGroup(val group: String, val items: List<MontazTool>)

/**
 * Sprzęt dla zakresu montażu, pogrupowany i posortowany jak przy pakowaniu.
 * [onlyRequired] = tylko to, bez czego nie wyjeżdżamy (domyślnie cała lista).
 */
fun montazTools(
    nodeIds: List<String>,
    byId: Map<String, Category>,
    onlyRequired: Boolean = false,
): List<MontazToolGroup> {
    val merged = LinkedHashMap<String, MontazTool>()

    for (id in nodeIds) {
        val owner = toolOwner(id, byId) ?: continue
        val label = byId[id]?.name ?: owner.first.name
        for (item in owner.second.items) {
            if (item.name.isBlank()) continue
            val key = item.name.trim().lowercase()
            val prev = merged[key]
            if (prev == null) {
                merged[key] = MontazTool(
                    name = item.name,
                    group = item.group,
                    qty = item.qty,
                    unit = item.unit,
                    required = item.required,
                    beacon = item.beacon,
                    owner = item.owner,
                    note = item.note,
                    fromNodes = listOf(label),
                )
                continue
            }
            merged[key] = prev.copy(
                qty = maxQty(prev.qty, item.qty),
                required = prev.required || item.required,
                beacon = prev.beacon || item.beacon,
                fromNodes = if (label in prev.fromNodes) prev.fromNodes else prev.fromNodes + label,
            )
        }
    }

    val wanted = merged.values.filter { !onlyRequired || it.required }
    return groupByPackingOrder(wanted)
}

/** Uwagi ogólne do wyposażenia z węzłów zakresu (co sprawdzamy przed wyjazdem). */
fun montazToolNotes(nodeIds: List<String>, byId: Map<String, Category>): List<String> {
    val out = mutableListOf<String>()
    for (id in nodeIds) {
        val note = toolOwner(id, byId)?.second?.note?.trim()
        if (!note.isNullOrBlank() && note !in out) out += note
    }
    return out
}

/** Najbliższy przodek (albo sam węzeł) z niepustą listą sprzętu. */
private fun toolOwner(
    nodeId: String,
    byId: Map<String, Category>,
): Pair<Category, ToolsScheme>? {
    var cur = byId[nodeId]
    var guard = 0
    while (cur != null && guard++ < 20) {
        val scheme = cur.tools
        if (scheme != null && scheme.items.isNotEmpty()) return cur to scheme
        cur = cur.parentId?.let { byId[it] }
    }
    return null
}

private fun maxQty(a: Double?, b: Double?): Double? = when {
    a == null -> b
    b == null -> a
    else -> maxOf(a, b)
}

private fun groupByPackingOrder(items: List<MontazTool>): List<MontazToolGroup> {
    // Sekcje znane katalogowi idą w kolejności pakowania; własne (dopisane przez
    // firmę w karcie węzła) lądują na końcu, w kolejności pojawienia się.
    val buckets = LinkedHashMap<String, MutableList<MontazTool>>()
    for (g in TOOL_GROUPS) buckets[g] = mutableListOf()
    for (t in items) {
        val group = t.group.trim().ifBlank { "Pozostałe" }
        buckets.getOrPut(group) { mutableListOf() } += t
    }
    return buckets
        .filterValues { it.isNotEmpty() }
        .map { (group, list) ->
            MontazToolGroup(
                group = group,
                // Obowiązkowe na górze sekcji, reszta alfabetem — tak pakuje się auto.
                items = list.sortedWith(
                    compareByDescending<MontazTool> { it.required }
                        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
                ),
            )
        }
}
