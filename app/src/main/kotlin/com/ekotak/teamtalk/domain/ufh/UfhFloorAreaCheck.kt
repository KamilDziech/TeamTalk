package com.ekotak.teamtalk.domain.ufh

import kotlin.math.abs

/**
 * KONTROLA METRAŻU KONDYGNACJI — port `ufh-floor-area-check.ts`.
 *
 * Ile m² kondygnacja MA MIEĆ (metraż wg projektu) kontra ile wyszło z WRYSOWANEJ
 * MAPY (suma obrysów wszystkich kategorii). Rozjazd powyżej 3% = obrys albo
 * metraż do poprawy.
 */

/** Dopuszczalny rozjazd metrażu projektowego i pomiaru z rzutu (3%). */
const val AREA_TOLERANCE = 0.03

/** Podpowiedź metrażu kondygnacji z danych budynku (pow. ogrzewana / kondygnacje). */
fun suggestFloorM2(buildingAreaM2: Double?, floorCount: Int): Double? {
    if (buildingAreaM2 == null || !buildingAreaM2.isFinite() || buildingAreaM2 <= 0) return null
    if (floorCount < 1) return null
    return jsRound(buildingAreaM2 / floorCount * 10) / 10
}

/** Metraż kondygnacji z wrysowanej mapy — suma obrysów wszystkich kategorii; `null` = nie ma czego porównać. */
fun planFloorM2(rooms: List<RoomShape>, scale: PlanScale?): Double? {
    var total: Double? = null
    for (c in AreaCat.entries) {
        val m2 = catM2(rooms, scale, c)
        if (m2 != null) total = (total ?: 0.0) + m2
    }
    return total?.let { jsRound(it * 10) / 10 }
}

enum class FloorAreaStatus {
    /** Metraż wg projektu nie podany. */
    NO_TARGET,

    /** Brak pomiaru z rzutu (bez skali albo bez obrysów). */
    NO_PLAN,

    /** Rozjazd w granicach tolerancji. */
    OK,

    /** Rozjazd powyżej tolerancji — do poprawy. */
    OFF,
}

data class FloorAreaCheck(
    val status: FloorAreaStatus,
    /** Metraż wg projektu [m²]. */
    val target: Double?,
    /** Metraż z wrysowanej mapy [m²]. */
    val measured: Double?,
    /** Różnica `measured − target` [m²]. */
    val diff: Double?,
    /** Względny rozjazd (0,05 = 5%). */
    val ratio: Double?,
)

/** Porównanie metrażu projektowego z pomiarem z rzutu dla jednej kondygnacji. */
fun floorAreaCheck(target: Double?, measured: Double?): FloorAreaCheck {
    if (target == null || !target.isFinite() || target <= 0) {
        return FloorAreaCheck(FloorAreaStatus.NO_TARGET, null, measured, null, null)
    }
    if (measured == null) return FloorAreaCheck(FloorAreaStatus.NO_PLAN, target, null, null, null)
    val diff = jsRound((measured - target) * 10) / 10
    val ratio = abs(measured - target) / target
    return FloorAreaCheck(
        status = if (ratio > AREA_TOLERANCE) FloorAreaStatus.OFF else FloorAreaStatus.OK,
        target = target,
        measured = measured,
        diff = diff,
        ratio = ratio,
    )
}

/** Rozjazd jako procent z jednym miejscem po przecinku („8.4%"). */
fun fmtRatio(ratio: Double): String = "${jsNum(jsRound(ratio * 1000) / 10)}%"

/** Komunikat do audytora; `null` = nie ma nic do napisania. */
fun floorAreaMessage(c: FloorAreaCheck): String? {
    if (c.status == FloorAreaStatus.NO_TARGET || c.target == null) return null
    val target = jsNum(c.target)
    if (c.status == FloorAreaStatus.NO_PLAN) return "Wg projektu $target m² — brak pomiaru z rzutu"
    val measured = "z rzutu ${c.measured?.let { jsNum(it) }} m²"
    if (c.status == FloorAreaStatus.OK) return "Zgadza się z rzutem ($measured, ${fmtRatio(c.ratio ?: 0.0)})"
    val dir = if ((c.diff ?: 0.0) > 0) "więcej" else "mniej"
    return "Rozjazd ${fmtRatio(c.ratio ?: 0.0)}: wg projektu $target m², $measured " +
        "(o ${jsNum(abs(c.diff ?: 0.0))} m² $dir) — popraw obrys na rzucie albo metraż wg projektu"
}

/** Numery kondygnacji (1-based) z rozjazdem ponad tolerancję. */
fun floorsOverTolerance(checks: List<FloorAreaCheck>): List<Int> =
    checks.mapIndexedNotNull { i, c -> if (c.status == FloorAreaStatus.OFF) i + 1 else null }

/** Zbiorczy komunikat pod sumą wszystkich kondygnacji (`null` = wszystko gra). */
fun floorsOverMessage(checks: List<FloorAreaCheck>): String? {
    val bad = floorsOverTolerance(checks)
    if (bad.isEmpty()) return null
    val label = if (bad.size == 1) "Kondygnacja" else "Kondygnacje"
    return "⚠ Metraż do poprawy — $label ${bad.joinToString(", ")} (rozjazd z rzutem ponad ${fmtRatio(AREA_TOLERANCE)})"
}
