package com.ekotak.teamtalk.domain.ufh

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Podział pola pod ogrzewaniem podłogowym na STREFY (pętle) — port
 * `ufh-zone-split.ts` z panelu, czysta geometria.
 *
 * Pętli nie kładzie się w długi wąski pas: rura wraca meandrem do rozdzielacza,
 * więc strefa mocno wydłużona to zła pętla. Reguła audytu: proporcja boków
 * strefy nie przekracza 1 : 2,5 ([ZONE_MAX_RATIO]). Dlatego strefy tniemy jak
 * SIATKĘ — pasy w poprzek dłuższego boku, a w pasie kolejne cięcia w drugą
 * stronę.
 *
 * Liczba stref przychodzi z wyliczenia długości rury ([UfhPipeLength]) i TU SIĘ
 * NIE ZMIENIA — proporcja dobiera wyłącznie UKŁAD cięć.
 *
 * Miarą podziału jest RURA (mb), nie pole: łatka zagęszczenia zjada więcej rury
 * na m², więc równy podział pola dałby pętle o różnej długości.
 */

/** Dopuszczalna proporcja boków strefy (dłuższy / krótszy). */
const val ZONE_MAX_RATIO = 2.5

/** Ile kroków wyszukiwania binarnego położenia cięcia. */
private const val CUT_STEPS = 20

/** Oś, po której zmienia się współrzędna cięcia („X" = linia pionowa). */
private enum class Axis { X, Y }

/** Prostokątne okno w jednostkach szerokości obrazu. */
data class ZoneBox(val x0: Double, val x1: Double, val y0: Double, val y1: Double)

/** Łatka zagęszczenia przygotowana do rachunku: obrys + waga = 1 / rozstaw. */
internal data class PatchRing(val ring: List<PlanPoint>, val weight: Double)

/** Pomieszczenie gotowe do podziału (jednostki szerokości obrazu). */
class ZoneField internal constructor(
    /** Obrys (indeks 0) + wycięcia. */
    internal val rings: List<List<PlanPoint>>,
    internal val patches: List<PatchRing>,
    /** 1 / rozstaw pola podstawowego; 0 = kategoria bez grzania. */
    internal val baseWeight: Double,
    /** Czy pole podstawowe wchodzi do metrażu OP. */
    internal val baseHeats: Boolean,
)

/** Jedna strefa = jedna pętla. */
data class ZoneGeom(
    val box: ZoneBox,
    /** Rura strefy w jednostkach ważonych (× przelicznik = mb). */
    val pipeW: Double,
    /** Pole grzewcze strefy [jednostki² szerokości obrazu]. */
    val heatArea: Double,
    /** Prostokąt opisany na rzeczywistej geometrii strefy. */
    val bbox: ZoneBox,
    /** Proporcja boków `bbox` (dłuższy / krótszy, ≥ 1). */
    val ratio: Double,
    /** Kotwica podpisu — środek najdłuższego odcinka wewnątrz strefy. */
    val at: PlanPoint,
)

data class ZoneCut(val a: PlanPoint, val b: PlanPoint)

data class ZoneSplit(
    val zones: List<ZoneGeom>,
    val cuts: List<ZoneCut>,
    val maxRatio: Double,
    /** Nie da się zejść do 1 : 2,5 przy tej liczbie pętli — kształt nie pozwala. */
    val ratioOver: Boolean,
)

private fun along(p: PlanPoint, axis: Axis): Double = if (axis == Axis.X) p.x else p.y

private fun across(p: PlanPoint, axis: Axis): Double = if (axis == Axis.X) p.y else p.x

private fun pointAt(axis: Axis, alongV: Double, acrossV: Double): PlanPoint =
    if (axis == Axis.X) PlanPoint(alongV, acrossV) else PlanPoint(acrossV, alongV)

/** Pole wieloboku (wzór Gaussa) — punkty już w jednostkach szerokości. */
private fun shoelace(ring: List<PlanPoint>): Double {
    if (ring.size < 3) return 0.0
    var sum = 0.0
    for (i in ring.indices) {
        val p = ring[i]
        val q = ring[(i + 1) % ring.size]
        sum += p.x * q.y - q.x * p.y
    }
    return abs(sum) / 2
}

/**
 * Część wieloboku po stronie `oś ≤ t` (albo `≥ t`) — Sutherland–Hodgman.
 * Sklejenia wieloboku wklęsłego leżą DOKŁADNIE na linii cięcia, więc pole
 * i prostokąt opisany wychodzą prawidłowe — a tego tu potrzebujemy.
 */
private fun clipHalf(
    ring: List<PlanPoint>,
    axis: Axis,
    t: Double,
    keepBelow: Boolean,
): List<PlanPoint> {
    if (ring.size < 3) return emptyList()
    fun inside(p: PlanPoint) = if (keepBelow) along(p, axis) <= t else along(p, axis) >= t
    val out = ArrayList<PlanPoint>()
    for (i in ring.indices) {
        val a = ring[i]
        val b = ring[(i + 1) % ring.size]
        val ia = inside(a)
        val ib = inside(b)
        if (ia) out += a
        if (ia != ib) {
            val ca = along(a, axis)
            val cb = along(b, axis)
            val k = (t - ca) / (cb - ca)
            out += PlanPoint(a.x + (b.x - a.x) * k, a.y + (b.y - a.y) * k)
        }
    }
    return if (out.size >= 3) out else emptyList()
}

/** Wielobok przycięty prostokątnym oknem (cztery półpłaszczyzny). */
private fun clipBox(ring: List<PlanPoint>, box: ZoneBox): List<PlanPoint> {
    var r = clipHalf(ring, Axis.X, box.x0, false)
    if (r.isNotEmpty()) r = clipHalf(r, Axis.X, box.x1, true)
    if (r.isNotEmpty()) r = clipHalf(r, Axis.Y, box.y0, false)
    if (r.isNotEmpty()) r = clipHalf(r, Axis.Y, box.y1, true)
    return r
}

/** Pole pomieszczenia w oknie — obrys minus wycięcia. */
private fun areaIn(rings: List<List<PlanPoint>>, box: ZoneBox): Double {
    var sum = shoelace(clipBox(rings[0], box))
    for (i in 1 until rings.size) sum -= shoelace(clipBox(rings[i], box))
    return max(0.0, sum)
}

/** „Ważone pole" w oknie — pomnożone przez przelicznik daje wprost metry rury. */
private fun pipeIn(field: ZoneField, box: ZoneBox): Double {
    var base = areaIn(field.rings, box)
    var sum = 0.0
    for (p in field.patches) {
        val a = shoelace(clipBox(p.ring, box))
        base -= a
        sum += a * p.weight
    }
    return sum + max(0.0, base) * field.baseWeight
}

/** Pole GRZEWCZE w oknie — bez łatek i pola podstawowego, które nie grzeją. */
private fun heatAreaIn(field: ZoneField, box: ZoneBox): Double {
    var base = areaIn(field.rings, box)
    var sum = 0.0
    for (p in field.patches) {
        val a = shoelace(clipBox(p.ring, box))
        base -= a
        if (p.weight > 0) sum += a
    }
    return sum + if (field.baseHeats) max(0.0, base) else 0.0
}

/** Prostokąt opisany na rzeczywistej geometrii w oknie (pusto → samo okno). */
private fun geomBox(rings: List<List<PlanPoint>>, box: ZoneBox): ZoneBox {
    val r = clipBox(rings[0], box)
    if (r.size < 3) return box
    return ZoneBox(
        x0 = r.minOf { it.x },
        x1 = r.maxOf { it.x },
        y0 = r.minOf { it.y },
        y1 = r.maxOf { it.y },
    )
}

/** Okno przycięte do `oś ≤ t` / `oś ≥ t`. */
private fun boxSlice(box: ZoneBox, axis: Axis, t: Double, below: Boolean): ZoneBox =
    if (axis == Axis.X) {
        if (below) box.copy(x1 = t) else box.copy(x0 = t)
    } else {
        if (below) box.copy(y1 = t) else box.copy(y0 = t)
    }

/** Odcinki linii `oś = t` leżące WEWNĄTRZ pomieszczenia, przycięte do okna. */
private fun spansAt(
    rings: List<List<PlanPoint>>,
    axis: Axis,
    t: Double,
    box: ZoneBox,
): List<Pair<Double, Double>> {
    val lo = if (axis == Axis.X) box.y0 else box.x0
    val hi = if (axis == Axis.X) box.y1 else box.x1
    val hits = ArrayList<Double>()
    for (ring in rings) {
        for (i in ring.indices) {
            val a = ring[i]
            val b = ring[(i + 1) % ring.size]
            val ca = along(a, axis)
            val cb = along(b, axis)
            if ((ca > t) == (cb > t)) continue
            val k = (t - ca) / (cb - ca)
            hits += across(a, axis) + (across(b, axis) - across(a, axis)) * k
        }
    }
    hits.sort()
    val out = ArrayList<Pair<Double, Double>>()
    var i = 0
    while (i + 1 < hits.size) {
        val s0 = max(hits[i], lo)
        val s1 = min(hits[i + 1], hi)
        if (s1 - s0 > 1e-9) out += s0 to s1
        i += 2
    }
    return out
}

/** Położenie cięcia w oknie, które odcina `target` rury (wyszukiwanie binarne). */
private fun cutFor(field: ZoneField, box: ZoneBox, axis: Axis, target: Double): Double {
    var a = if (axis == Axis.X) box.x0 else box.y0
    var b = if (axis == Axis.X) box.x1 else box.y1
    repeat(CUT_STEPS) {
        val m = (a + b) / 2
        if (pipeIn(field, boxSlice(box, axis, m, true)) < target) a = m else b = m
    }
    return (a + b) / 2
}

/** Środek najdłuższego odcinka w strefie — podpis nie ląduje na wycięciu. */
private fun anchorOf(rings: List<List<PlanPoint>>, box: ZoneBox, bbox: ZoneBox): PlanPoint {
    val wide = (bbox.x1 - bbox.x0) >= (bbox.y1 - bbox.y0)
    val axis = if (wide) Axis.Y else Axis.X
    val t = if (wide) (bbox.y0 + bbox.y1) / 2 else (bbox.x0 + bbox.x1) / 2
    val mid = PlanPoint((bbox.x0 + bbox.x1) / 2, (bbox.y0 + bbox.y1) / 2)
    val spans = spansAt(rings, axis, t, box)
    if (spans.isEmpty()) return mid
    var best = spans[0]
    for (s in spans) if (s.second - s.first > best.second - best.first) best = s
    return pointAt(axis, t, (best.first + best.second) / 2)
}

private fun ratioOf(bbox: ZoneBox): Double {
    val w = bbox.x1 - bbox.x0
    val h = bbox.y1 - bbox.y0
    val lo = min(w, h)
    if (lo <= 1e-9) return Double.POSITIVE_INFINITY
    return max(w, h) / lo
}

private fun zoneOf(field: ZoneField, box: ZoneBox): ZoneGeom {
    val bbox = geomBox(field.rings, box)
    return ZoneGeom(
        box = box,
        pipeW = pipeIn(field, box),
        heatArea = heatAreaIn(field, box),
        bbox = bbox,
        ratio = ratioOf(bbox),
        at = anchorOf(field.rings, box, bbox),
    )
}

/** Rozdział `n` stref na `p` pasów — możliwie po równo (reszta do pierwszych). */
private fun distribute(n: Int, p: Int): List<Int> {
    val base = n / p
    val rest = n % p
    return List(p) { i -> base + if (i < rest) 1 else 0 }
}

private class SplitResult(
    val zones: List<ZoneGeom>,
    val cuts: List<ZoneCut>,
    val maxRatio: Double,
    /** Suma proporcji — rozstrzyga remis między układami o tej samej najgorszej. */
    val sumRatio: Double,
)

private fun resultOf(zones: List<ZoneGeom>, cuts: List<ZoneCut>): SplitResult {
    var maxRatio = 0.0
    var sumRatio = 0.0
    for (z in zones) {
        maxRatio = max(maxRatio, z.ratio)
        sumRatio += if (z.ratio.isFinite()) z.ratio else 1e6
    }
    return SplitResult(zones, cuts, maxRatio, sumRatio)
}

private fun better(a: SplitResult, b: SplitResult): Boolean {
    if (a.maxRatio < b.maxRatio - 1e-9) return true
    if (a.maxRatio > b.maxRatio + 1e-9) return false
    return a.sumRatio < b.sumRatio - 1e-9
}

/** Awaryjne cięcie „na oko" — gdy rury wyszło zero (obrys zdegenerowany). */
private fun cutAtFraction(box: ZoneBox, axis: Axis, k: Double): Double =
    if (axis == Axis.X) box.x0 + (box.x1 - box.x0) * k else box.y0 + (box.y1 - box.y0) * k

/**
 * Podział okna na `n` stref o równej rurze. Tniemy w poprzek DŁUŻSZEGO boku na
 * `p` pasów, a każdy pas dzielimy tak samo — czyli już w drugą stronę.
 */
private fun build(field: ZoneField, box: ZoneBox, n: Int): SplitResult {
    if (n <= 1) return resultOf(listOf(zoneOf(field, box)), emptyList())

    val bb = geomBox(field.rings, box)
    val w = bb.x1 - bb.x0
    val h = bb.y1 - bb.y0
    val longAxis = if (w >= h) Axis.X else Axis.Y
    val l = max(w, h)
    val s = max(1e-9, min(w, h))
    val ideal = sqrt(n * l / s)

    // p = 1 nie ma sensu (pas = całe okno), p = n to same plastry.
    val cands = LinkedHashSet<Int>()
    for (c in listOf(floor(ideal), jsRound(ideal), ceil(ideal))) {
        cands += min(n, max(2, c.toInt()))
    }

    var best: SplitResult? = null
    for (p in cands) {
        val r = buildBands(field, box, n, p, longAxis)
        if (best == null || better(r, best!!)) best = r
    }
    return best!!
}

/** Jeden układ: `p` pasów w poprzek `axis`, w pasach reszta stref rekurencyjnie. */
private fun buildBands(
    field: ZoneField,
    box: ZoneBox,
    n: Int,
    p: Int,
    axis: Axis,
): SplitResult {
    val per = distribute(n, p)
    val whole = pipeIn(field, box)
    val zones = ArrayList<ZoneGeom>()
    val cuts = ArrayList<ZoneCut>()
    var rest = box
    var taken = 0
    for (i in 0 until p) {
        taken += per[i]
        var band = rest
        if (i < p - 1) {
            // Cięcie liczymy narastająco w CAŁYM oknie, więc zaokrąglenia nie
            // kumulują się z pasa na pas.
            val t = if (whole > 0) {
                cutFor(field, box, axis, whole * taken / n)
            } else {
                cutAtFraction(box, axis, taken.toDouble() / n)
            }
            band = boxSlice(rest, axis, t, true)
            rest = boxSlice(rest, axis, t, false)
            for ((s0, s1) in spansAt(field.rings, axis, t, band)) {
                cuts += ZoneCut(pointAt(axis, t, s0), pointAt(axis, t, s1))
            }
        }
        val inner = build(field, band, per[i])
        zones += inner.zones
        cuts += inner.cuts
    }
    return resultOf(zones, cuts)
}

/** Pomieszczenie z rzutu → pole gotowe do podziału (jednostki szerokości obrazu). */
fun zoneField(room: RoomShape, aspect: Double): ZoneField {
    fun toUnit(ring: List<PlanPoint>): List<PlanPoint> =
        ring.map { PlanPoint(it.x, if (aspect > 0) it.y / aspect else it.y) }
    val baseSpacing = room.cat.spacingM
    return ZoneField(
        rings = listOf(toUnit(room.outline)) + room.holes.filter { it.size >= 3 }.map { toUnit(it) },
        patches = room.patches.filter { it.outline.size >= 3 }.map { p ->
            val spacing = p.cat.spacingM
            PatchRing(toUnit(p.outline), if (spacing != null && spacing > 0) 1 / spacing else 0.0)
        },
        baseWeight = if (baseSpacing != null && baseSpacing > 0) 1 / baseSpacing else 0.0,
        baseHeats = baseSpacing != null,
    )
}

/** Podział pomieszczenia na `count` stref o równej długości rury. */
fun splitZones(field: ZoneField, count: Int): ZoneSplit {
    val n = max(1, floor(count.toDouble()).toInt())
    val outline = field.rings.firstOrNull().orEmpty()
    if (outline.size < 3) return ZoneSplit(emptyList(), emptyList(), 0.0, false)
    val box = ZoneBox(
        x0 = outline.minOf { it.x },
        x1 = outline.maxOf { it.x },
        y0 = outline.minOf { it.y },
        y1 = outline.maxOf { it.y },
    )
    val r = build(field, box, n)
    return ZoneSplit(
        zones = r.zones,
        cuts = r.cuts,
        maxRatio = r.maxRatio,
        ratioOver = r.maxRatio > ZONE_MAX_RATIO + 1e-9,
    )
}

/** Odległość punktu od odcinka — oba w jednostkach szerokości obrazu. */
internal fun distToSeg(p: PlanPoint, a: PlanPoint, b: PlanPoint): Double {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val len2 = dx * dx + dy * dy
    if (len2 <= 0) return hypot(p.x - a.x, p.y - a.y)
    val t = max(0.0, min(1.0, ((p.x - a.x) * dx + (p.y - a.y) * dy) / len2))
    return hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy))
}

private fun distToRing(p: PlanPoint, ring: List<PlanPoint>): Double {
    var best = Double.POSITIVE_INFINITY
    for (i in ring.indices) {
        val d = distToSeg(p, ring[i], ring[(i + 1) % ring.size])
        if (d < best) best = d
    }
    return best
}

/**
 * Najkrótsza odległość rozdzielacza od STREFY [jednostki szerokości] — czyli od
 * miejsca, w którym ta pętla wychodzi. Rozdzielacz stojący w strefie → 0.
 */
fun distToZone(field: ZoneField, zone: ZoneGeom, mark: PlanPoint): Double {
    val ring = clipBox(field.rings[0], zone.box)
    if (ring.size < 3) return Double.POSITIVE_INFINITY
    val holes = field.rings.drop(1).map { clipBox(it, zone.box) }.filter { it.size >= 3 }
    val inHole = holes.any { pointInPoly(mark, it) }
    if (!inHole && pointInPoly(mark, ring)) return 0.0
    var best = distToRing(mark, ring)
    for (h in holes) best = min(best, distToRing(mark, h))
    return best
}
