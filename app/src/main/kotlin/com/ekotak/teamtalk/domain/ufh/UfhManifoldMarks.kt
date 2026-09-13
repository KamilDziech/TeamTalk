package com.ekotak.teamtalk.domain.ufh

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.math.abs

/**
 * KROPKI ROZDZIELACZY — reszta `ufh-manifold-marks.ts` i `ufh-box-colors.ts`
 * potrzebna edytorowi rzutu (typ i odczyt są w `UfhPlan.kt`).
 */

private var markSeq = 0

/** Id nowej kropki — unikalne wobec id nadawanych starym zapisom (`m1`, `m2`…). */
fun newMarkId(): String {
    markSeq += 1
    return "mk${System.currentTimeMillis().toString(36)}${markSeq.toString(36)}"
}

/** Podpis rozdzielacza w listach („Rozdzielacz 2"). */
fun manifoldLabel(index: Int): String = "Rozdzielacz ${index + 1}"

/** Skrót rozdzielacza na rzucie i przy pomieszczeniu („R2"). */
fun manifoldShort(index: Int): String = "R${index + 1}"

/**
 * Rodzaj skrzynki, który legenda ma podświetlić bez zaznaczonej kropki: wspólny
 * typ wszystkich rozdzielaczy kondygnacji; różne typy → `""` (nic nie świeci).
 */
fun commonBoxType(marks: List<ManifoldMark>, floorBoxType: String): String {
    if (marks.isEmpty()) return floorBoxType
    val uniq = LinkedHashSet(marks.map { markBoxType(it, floorBoxType) })
    return if (uniq.size == 1) uniq.first() else ""
}

/** Równe położenia, id I typy skrzynek — decyduje, czy jest co zapisywać. */
fun sameMarks(a: List<ManifoldMark>, b: List<ManifoldMark>): Boolean {
    if (a.size != b.size) return false
    return a.indices.all { i ->
        abs(a[i].x - b[i].x) < 1e-6 &&
            abs(a[i].y - b[i].y) < 1e-6 &&
            a[i].id == b[i].id &&
            a[i].boxType == b[i].boxType
    }
}

/** Kropka do zapisu — 4 miejsca po przecinku, puste `id`/`boxType` pomijane (`roundMark`). */
fun markToJson(m: ManifoldMark): JsonObject = buildJsonObject {
    put("x", jsonNum(jsRound(m.x * 10000) / 10000))
    put("y", jsonNum(jsRound(m.y * 10000) / 10000))
    if (m.id.isNotEmpty()) put("id", JsonPrimitive(m.id))
    if (m.boxType.isNotEmpty()) put("boxType", JsonPrimitive(m.boxType))
}

// ── Kolory rodzajów skrzynki ────────────────────────────────────────────────

/** Kolor kropki rozdzielacza wg rodzaju skrzynki (ARGB, bez Compose). */
data class BoxColorDef(val type: String, val color: Long, val label: String)

/** Wartości `type` muszą się zgadzać z `UFH_BOX_TYPES`. */
val UFH_BOX_COLORS: List<BoxColorDef> = listOf(
    BoxColorDef("natynkowa", 0xFF2F80ED, "niebieski — skrzynka natynkowa"),
    BoxColorDef(
        "podtynkowa w ścianie nośnej",
        0xFF1B2A6B,
        "granatowy — skrzynka podtynkowa w ścianie nośnej",
    ),
    BoxColorDef(
        "podtynkowa w ścianie działowej",
        0xFF8E44AD,
        "fioletowy — skrzynka podtynkowa w ścianie działowej",
    ),
    BoxColorDef("brak", 0xFFE5342A, "czerwony — brak skrzynki"),
)

/** Kolor dla nieznanej/starej wartości `boxType` — szary, żeby nie kłamać legendą. */
const val UFH_BOX_COLOR_FALLBACK = 0xFF8A8A8A

fun boxColor(boxType: String): Long =
    UFH_BOX_COLORS.firstOrNull { it.type == boxType }?.color ?: UFH_BOX_COLOR_FALLBACK

/** Nazwa rodzaju skrzynki do podpowiedzi kropki. */
fun boxColorLabel(boxType: String): String =
    UFH_BOX_COLORS.firstOrNull { it.type == boxType }?.label ?: "skrzynka: ${boxType.ifEmpty { "—" }}"

private const val TEXT_LIGHT = 0xFFFFFFFF
private const val TEXT_DARK = 0xFF080808

/** Relatywna luminancja WCAG koloru ARGB. */
private fun luminance(argb: Long): Double {
    fun lin(shift: Int): Double {
        val v = ((argb shr shift) and 0xFF).toDouble() / 255
        return if (v <= 0.04045) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
    }
    return 0.2126 * lin(16) + 0.7152 * lin(8) + 0.0722 * lin(0)
}

/**
 * Kolor numeru na kropce — jasny na ciemnym tle, ciemny na jasnym. Przy różnicy
 * kontrastów poniżej 10% wygrywa jasny (cyfra mała i pogrubiona).
 */
fun boxTextColor(color: Long): Long {
    val l = luminance(color)
    val onLight = (l + 0.05) / (luminance(TEXT_DARK) + 0.05)
    val onDark = 1.05 / (l + 0.05)
    return if (onDark * 1.1 >= onLight) TEXT_LIGHT else TEXT_DARK
}
