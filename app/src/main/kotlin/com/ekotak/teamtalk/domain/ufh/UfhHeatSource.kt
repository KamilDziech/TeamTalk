package com.ekotak.teamtalk.domain.ufh

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.math.abs

/**
 * ŹRÓDŁO CIEPŁA na rzucie kondygnacji — port `ufh-heat-source.ts`.
 *
 * Źródło jest JEDNO na cały budynek, ale zapisane przy kondygnacji
 * (`floors[i].heatSource`), bo ma sens tylko na konkretnym rzucie. Jedyności
 * pilnuje [patchFloorPlan]: postawienie źródła zabiera je z innych kondygnacji.
 */

/** Rodzaj źródła — do podpisu kropki (na wyliczenie nie wpływa). */
val UFH_HEAT_SOURCE_KINDS = listOf(
    "pompa ciepła",
    "kocioł",
    "bufor / sprzęgło hydrauliczne",
    "inne",
)

val UFH_HEAT_SOURCE_DEFAULT_KIND = UFH_HEAT_SOURCE_KINDS[0]

/** Zieleń marki ekotak (ARGB) — poza paletą rodzajów skrzynek; kształt: romb. */
const val HEAT_SOURCE_COLOR = 0xFF44D62C

/** Położenie źródła ciepła na rzucie (0–1). `kind` puste = nieokreślone. */
data class HeatSourceMark(val x: Double, val y: Double, val kind: String = "") {
    val point: PlanPoint get() = PlanPoint(x, y)
}

/** Kropka do zapisu — 4 miejsca po przecinku, jak przy rozdzielaczach (`roundSource`). */
fun heatSourceToJson(m: HeatSourceMark): JsonObject = buildJsonObject {
    put("x", jsonNum(jsRound(m.x * 10000) / 10000))
    put("y", jsonNum(jsRound(m.y * 10000) / 10000))
    if (m.kind.isNotEmpty()) put("kind", JsonPrimitive(m.kind))
}

/** Odczyt z zapisanego JSON-a (śmieci i brak → `null`). */
fun heatSourceFromJson(v: JsonElement?): HeatSourceMark? {
    val o = v as? JsonObject ?: return null
    val x = o.numOrNull("x") ?: return null
    val y = o.numOrNull("y") ?: return null
    return HeatSourceMark(clamp01(x), clamp01(y), o.strOrEmpty("kind").trim())
}

fun sameSource(a: HeatSourceMark?, b: HeatSourceMark?): Boolean {
    if (a == null || b == null) return a == null && b == null
    return abs(a.x - b.x) < 1e-6 && abs(a.y - b.y) < 1e-6 && a.kind == b.kind
}

/** Podpis kropki („Źródło ciepła — pompa ciepła"). */
fun heatSourceLabel(m: HeatSourceMark?): String {
    if (m == null) return "Źródło ciepła — nie zaznaczone"
    return "Źródło ciepła${if (m.kind.isNotEmpty()) " — ${m.kind}" else ""}"
}

/** Na której kondygnacji stoi źródło (−1 = na żadnej; przy kilku liczy się pierwsza). */
fun sourceFloorIndex(sources: List<HeatSourceMark?>): Int = sources.indexOfFirst { it != null }
