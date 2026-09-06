package com.ekotak.teamtalk.domain.ufh

import com.ekotak.teamtalk.domain.model.UFH_SUBFLOOR_JOINTS
import kotlin.math.PI
import kotlin.math.max

/**
 * Parametry instalacji, które wpływają na ILOŚĆ materiału — port tej części
 * `ufh-install-params.ts` i `ufh-water-volume.ts`, której potrzebuje oferta.
 * Same listy odpowiedzi mieszkają w `UfhAudit.kt` (wypełnia je formularz audytu).
 */

/**
 * Ile procent odpadu rury dokładamy przy danej odpowiedzi o łączenie rur pod
 * posadzką. Zakaz łączenia = całe pętle z jednego kawałka, więc końcówek zwojów
 * nie da się wykorzystać (10 %); dopuszczone łączenie schodzi do resztek (3 %).
 */
val WASTE_PCT_BY_JOINTS: Map<String, Int> = mapOf(
    UFH_SUBFLOOR_JOINTS[0] to 3,
    UFH_SUBFLOOR_JOINTS[1] to 10,
)

/** Brak odpowiedzi w audycie liczymy ostrożnie — jak przy zakazie łączenia. */
val DEFAULT_WASTE_PCT: Int = WASTE_PCT_BY_JOINTS.getValue(UFH_SUBFLOOR_JOINTS[1])

/** Ile procent odpadu przy tej odpowiedzi (do podpisu wiersza formuły). */
fun wastePct(subfloorJoints: String): Int =
    WASTE_PCT_BY_JOINTS[subfloorJoints] ?: DEFAULT_WASTE_PCT

/** Mnożnik odpadu dla pola `subfloorJoints` (1,03 albo 1,10). */
fun wasteFactor(subfloorJoints: String): Double = 1 + wastePct(subfloorJoints) / 100.0

// ── Pojemność wodna instalacji ──────────────────────────────────────────────

/** Grubość ścianki rury podejściowej [mm] wg średnicy zewnętrznej. */
private val SUPPLY_WALL_MM_BY_MM = mapOf(25.0 to 2.5, 32.0 to 3.0, 40.0 to 3.5)

private fun r3(v: Double): Double = jsRound(v * 1000) / 1000

/** Średnica wewnętrzna [mm] ze średnicy zewnętrznej i grubości ścianki. */
fun innerMm(outerMm: Double, wallMm: Double): Double = r1(outerMm - 2 * wallMm)

/** Litry na metr bieżący rury o danej średnicy wewnętrznej [mm]. */
fun litersPerM(innerMm: Double): Double {
    if (innerMm <= 0) return 0.0
    val rM = innerMm / 2 / 1000
    return r3(PI * rM * rM * 1000)
}

/** Pojemność rury [l] z jej średnicy wewnętrznej [mm] i długości [mb]. */
fun pipeLiters(innerMm: Double, meters: Double): Double = r1(litersPerM(innerMm) * max(0.0, meters))

/**
 * Pojemność wodna instalacji OP [l] — rura podłogowa (pętle z dobiegami) plus
 * rura podejściowa źródło → rozdzielacze. Rozdzielacze, źródło ciepła i sprzęgło
 * NIE są liczone: to pojemność samych RUR, tak samo jak w panelu.
 */
fun waterVolumeTotal(system: String?, loopM: Double, supplyMmRaw: String, supplyM: Double): Double {
    var total = 0.0
    if (loopM > 0) {
        total += pipeLiters(innerMm(ufhPipeMm(system).toDouble(), ufhPipeWallMm(system)), loopM)
    }
    val supplyMm = supplyMmRaw.trim().replace(',', '.').toDoubleOrNull()
    val wall = if (supplyMm != null && supplyMm > 0) SUPPLY_WALL_MM_BY_MM[supplyMm] else null
    if (supplyM > 0 && supplyMm != null && wall != null) {
        total += pipeLiters(innerMm(supplyMm, wall), supplyM)
    }
    return r1(total)
}
