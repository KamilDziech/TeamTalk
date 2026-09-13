package com.ekotak.teamtalk.domain.ufh

import kotlin.math.ceil

/**
 * POJEMNOŚĆ WODNA instalacji OP — blok audytu z rozbiciem na rury. Port
 * `ufh-water-volume.ts` (+ dawka inhibitora z `ufh-water-chemistry.ts`).
 *
 * Sama liczba (bez rozbicia) jest już w `UfhInstallParams.kt` dla oferty; tu
 * dochodzi to, co pokazuje audyt: pozycje z wymiarem rury, litry na metr
 * i komunikaty, czego brakuje do kompletu.
 *
 *   ⌀ wewnętrzna = ⌀ zewnętrzna − 2 × ścianka
 *   litry na metr = π/4 × (⌀ wewn. [m])² × 1000
 *   pojemność     = litry na metr × długość rury [mb]
 */

/** Grubość ścianki rury podejściowej [mm] wg średnicy zewnętrznej (25×2,5 · 32×3,0 · 40×3,5). */
private val SUPPLY_WALL_MM_BY_MM = mapOf(25.0 to 2.5, 32.0 to 3.0, 40.0 to 3.5)

enum class WaterLineKey { LOOP, SUPPLY }

/** Jedna rura w rachunku pojemności. */
data class WaterLine(
    val key: WaterLineKey,
    val label: String,
    /** Średnica zewnętrzna [mm]. */
    val outerMm: Double,
    val wallMm: Double,
    /** Średnica wewnętrzna [mm] = zewnętrzna − 2 × ścianka. */
    val innerMm: Double,
    /** Litry na metr bieżący tej rury. */
    val lPerM: Double,
    /** Długość rury [mb]. */
    val m: Double,
    /** Pojemność tej rury [l]. */
    val liters: Double,
)

data class WaterVolume(
    val lines: List<WaterLine>,
    /** Pojemność wodna razem [l] — suma pozycji, które dało się policzyć. */
    val total: Double,
    /** Czego brakuje do kompletu (długość albo średnica). */
    val problems: List<String>,
)

/** Grubość ścianki rury podejściowej [mm]; `null` = średnica spoza katalogu. */
fun supplyWallMm(outerMm: Double): Double? = SUPPLY_WALL_MM_BY_MM[outerMm]

private fun line(key: WaterLineKey, label: String, outerMm: Double, wallMm: Double, m: Double): WaterLine {
    val dn = innerMm(outerMm, wallMm)
    return WaterLine(
        key = key,
        label = label,
        outerMm = outerMm,
        wallMm = wallMm,
        innerMm = dn,
        lPerM = litersPerM(dn),
        m = r1(m),
        liters = pipeLiters(dn, m),
    )
}

/**
 * Pojemność wodna całej instalacji OP.
 *
 * @param system kod systemu rur (daje ⌀ i ściankę pętli)
 * @param loopM rura podłogowa całego budynku [mb] (`sumPipe(...).total`)
 * @param supplyMmRaw średnica rury podejściowej z „Danych instalacji"
 * @param supplyM rura podejściowa [mb] (`supplyPlan(...).total`)
 */
fun waterVolume(system: String?, loopM: Double, supplyMmRaw: String, supplyM: Double): WaterVolume {
    val lines = ArrayList<WaterLine>()
    val problems = ArrayList<String>()

    if (loopM > 0) {
        lines += line(
            WaterLineKey.LOOP,
            "Rura podłogowa (pętle z dobiegami)",
            ufhPipeMm(system).toDouble(),
            ufhPipeWallMm(system),
            loopM,
        )
    } else {
        problems += "Brak długości rury podłogowej — zmierz pomieszczenia na rzucie albo wpisz " +
            "powierzchnie wg rozstawu rur."
    }

    // `Number(...)` z panelu: przecinek to NIE liczba, pusty tekst to zero.
    val raw = supplyMmRaw.trim()
    val supplyMm = if (raw.isEmpty()) 0.0 else raw.toDoubleOrNull()
    val wall = if (supplyMm != null && supplyMm.isFinite() && supplyMm > 0) supplyWallMm(supplyMm) else null
    if (!(supplyM > 0)) {
        problems += "Podejścia do rozdzielaczy nie są policzone — bez nich pojemność jest niepełna " +
            "(zaznacz źródło ciepła i rozdzielacze na rzucie)."
    } else if (wall == null) {
        problems += "Nie znamy średnicy rury podejściowej — uzupełnij „Rura dobiegowa do rozdzielacza” " +
            "w sekcji „Dane instalacji”, inaczej podejścia nie wchodzą do pojemności."
    } else {
        lines += line(WaterLineKey.SUPPLY, "Rura podejściowa (źródło → rozdzielacze)", supplyMm!!, wall, supplyM)
    }

    var sum = 0.0
    for (l in lines) sum += l.liters
    return WaterVolume(lines = lines, total = r1(sum), problems = problems)
}

/** Litry po polsku („128,4"). */
fun fmtL(n: Double): String = jsFixed(n, 1).replace('.', ',')

/** Litry na metr bieżący („0,113"). */
fun fmtLpm(n: Double): String = jsFixed(n, 3).replace('.', ',')

/** Wymiar rury do podpisu pozycji („⌀16 × 2,0 mm → wewn. 12 mm"). */
fun pipeSizeLabel(l: WaterLine): String {
    val wall = jsFixed(l.wallMm, 1).replace('.', ',')
    val dn = jsNum(l.innerMm).replace('.', ',')
    return "⌀${jsNum(l.outerMm)} × $wall mm → wewn. $dn mm"
}

const val WATER_VOLUME_FORMULA =
    "pojemność = π/4 × (⌀ wewnętrzna)² × długość rury · ⌀ wewn. = ⌀ zewn. − 2 × ścianka"

// ── Inhibitor biobójczy ─────────────────────────────────────────────────────

/** Wartość pola „Inhibitor biobójczy" (`install.biocide`) znacząca „tak". */
const val BIOCIDE_YES = "tak"

data class ChemDose(
    /** Pojemność układu [l], z której liczona jest dawka. */
    val liters: Double,
    /** Ile butelek kupujemy — pełne, zawsze w górę. */
    val packs: Int,
    /** Ile litrów wody pokrywa tyle butelek. */
    val coversL: Int,
)

/** Dawka inhibitora ADEY MC10+ (125 l na butelkę); `null` = pojemność nie policzona. */
fun biocideDose(liters: Double): ChemDose? {
    if (!(liters > 0)) return null
    val packs = ceil(liters / BiocideAdeyMc10.LITERS_PER_PACK).toInt()
    return ChemDose(liters, packs, packs * BiocideAdeyMc10.LITERS_PER_PACK)
}
