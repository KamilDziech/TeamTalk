package com.ekotak.teamtalk.domain.ufh

import com.ekotak.teamtalk.domain.model.UfhFloor
import com.ekotak.teamtalk.domain.model.toM2
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Długość rury ogrzewania podłogowego na kondygnacji — port `ufh-pipe-length.ts`.
 *
 * Rachunek idzie po pomieszczeniach zmierzonych na rzucie:
 *
 *   rura grzewcza  = powierzchnia [m²] / rozstaw [m]   (5 / 10 / 15 / 20 cm)
 *   dobieg pętli   = odległość rozdzielacz → STREFA TEJ PĘTLI × 2 × 1,25
 *   pętla          ≤ limit średnicy (⌀16 → 100 mb, ⌀18 → 120 mb), DOBIEG
 *                    WLICZA SIĘ W TEN LIMIT
 *   razem          = rura grzewcza + dobiegi wszystkich pętli
 *
 * Bez obrysów (metraż wpisany ręcznie) liczymy to samo z pól kondygnacji, tylko
 * bez dobiegu — nie wiadomo, gdzie stoi rozdzielacz względem powierzchni.
 */

/** Dobieg liczymy w obie strony (zasilanie + powrót). */
const val LEAD_IN_TRIPS = 2

/** Zapas na trasę dobiegu (obejścia, przejścia, zejście do skrzynki). */
const val LEAD_IN_SLACK = 1.25

/** Ile razy dokładamy pętlę, gdy strefom nie starcza limitu długości. */
private const val SPLIT_TRIES = 12

/** Kawałek pola grzewczego o jednym rozstawie. */
data class PipePart(
    val cat: AreaCat,
    val m2: Double,
    val spacing: Double,
    val heating: Double,
    /** Kawałek pochodzi z łatki zagęszczenia (a nie z pola rodzica). */
    val patch: Boolean,
)

/** Jedna pętla pomieszczenia = jedna strefa na rzucie, z własnym dobiegiem. */
data class PipeLoop(
    val heating: Double,
    val lead: Double?,
    val total: Double,
    val m2: Double,
    val at: PlanPoint,
    val ratio: Double,
)

/** Podział pomieszczenia na strefy. */
data class RoomSplit(
    val loops: List<PipeLoop>,
    val cuts: List<ZoneCut>,
    val maxRatio: Double,
    val ratioOver: Boolean,
)

/** Jedna pozycja wyliczenia — pomieszczenie z rzutu albo pole metrażu. */
data class PipeItem(
    val key: String,
    val label: String,
    val cat: AreaCat,
    val m2: Double,
    val m2Total: Double,
    val spacing: Double,
    val parts: List<PipePart>,
    val heating: Double,
    /** Dobieg do samego POMIESZCZENIA [mb]; `null` = nieznany. */
    val lead: Double?,
    /** Suma dobiegów wszystkich pętli [mb]. */
    val leadTotal: Double,
    val manifoldIndex: Int?,
    val manifoldAuto: Boolean,
    val loops: Int,
    val total: Double,
    /** Dobieg ≥ limit pętli — pętli nie da się ułożyć tą rurą. */
    val impossible: Boolean,
    val split: RoomSplit?,
)

/** Skąd wzięte dane: obrysy z rzutu / pola metrażu / nie ma czego liczyć. */
enum class PipeSource { PLAN, AREAS, NONE }

data class FloorPipe(
    val source: PipeSource,
    val items: List<PipeItem> = emptyList(),
    val heating: Double = 0.0,
    val lead: Double = 0.0,
    /** Rura razem [mb] — to jest wynik dla kondygnacji. */
    val total: Double = 0.0,
    val loops: Int = 0,
    val noLead: Int = 0,
    val impossible: Int = 0,
)

data class PipeSum(val total: Double, val loops: Int, val heating: Double)

/** Dobieg jednej pętli [mb] z odległości rozdzielacz → pole pomieszczenia [m]. */
fun leadInM(distanceM: Double): Double = r1(distanceM * LEAD_IN_TRIPS * LEAD_IN_SLACK)

/**
 * Pętle i długość razem dla jednego pola grzewczego. Dobieg mieści się w limicie
 * pętli, więc na grzanie zostaje `limit − dobieg` mb.
 */
private fun loopsFor(heating: Double, lead: Double?, loopMaxM: Double): Pair<Int, Double> {
    if (heating <= 0) return 0 to 0.0
    val usable = loopMaxM - (lead ?: 0.0)
    if (usable <= 0) return 0 to r1(heating)
    val loops = max(1.0, ceil(heating / usable)).toInt()
    return loops to r1(heating + loops * (lead ?: 0.0))
}

/** Najkrótsza odległość rozdzielacza od pola pomieszczenia [jednostki szerokości]. */
fun distToRoom(mark: PlanPoint, room: RoomShape, aspect: Double): Double {
    if (room.outline.size < 3) return Double.POSITIVE_INFINITY
    if (pointInPoly(mark, room.outline)) return 0.0
    val p = unitPoint(mark, aspect)
    val poly = room.outline.map { unitPoint(it, aspect) }
    var best = Double.POSITIVE_INFINITY
    for (i in poly.indices) {
        val d = distToSeg(p, poly[i], poly[(i + 1) % poly.size])
        if (d < best) best = d
    }
    return best
}

/** Rozdzielacz zasilający pomieszczenie: przypisany albo najbliższa kropka. */
private data class ManifoldFor(val index: Int?, val auto: Boolean, val dist: Double)

private fun manifoldFor(
    room: RoomShape,
    marks: List<ManifoldMark>,
    aspect: Double,
): ManifoldFor {
    if (marks.isEmpty()) return ManifoldFor(null, false, Double.POSITIVE_INFINITY)
    val assigned = markIndexById(marks, room.manifoldId)
    if (assigned >= 0) {
        return ManifoldFor(assigned, false, distToRoom(marks[assigned].point, room, aspect))
    }
    var best = 0
    var bestD = Double.POSITIVE_INFINITY
    marks.forEachIndexed { i, m ->
        val d = distToRoom(m.point, room, aspect)
        if (d < bestD) {
            bestD = d
            best = i
        }
    }
    return ManifoldFor(best, true, bestD)
}

private fun totals(source: PipeSource, items: List<PipeItem>): FloorPipe {
    var heating = 0.0
    var lead = 0.0
    var total = 0.0
    var loops = 0
    var noLead = 0
    var impossible = 0
    for (it in items) {
        heating += it.heating
        lead += it.leadTotal
        total += it.total
        loops += it.loops
        if (it.lead == null) noLead += 1
        if (it.impossible) impossible += 1
    }
    return FloorPipe(
        source = source,
        items = items,
        heating = r1(heating),
        lead = r1(lead),
        total = r1(total),
        loops = loops,
        noLead = noLead,
        impossible = impossible,
    )
}

/**
 * Pola grzewcze pomieszczenia: pole podstawowe (obrys minus wycięcia minus
 * łatki) w rozstawie pomieszczenia + każda łatka zagęszczenia w swoim.
 */
fun roomParts(room: RoomShape, scale: PlanScale): List<PipePart> {
    val parts = ArrayList<PipePart>()
    val base = roomBaseM2(room, scale)
    val baseSpacing = room.cat.spacingM
    if (baseSpacing != null && base != null && base > 0) {
        parts += PipePart(room.cat, base, baseSpacing, r1(base / baseSpacing), false)
    }
    for (p in room.patches) {
        val spacing = p.cat.spacingM ?: continue
        val m2 = patchM2(p, scale) ?: continue
        if (m2 <= 0) continue
        parts += PipePart(p.cat, m2, spacing, r1(m2 / spacing), true)
    }
    return parts
}

private class SplitCtx(
    val m2: Double,
    val heating: Double,
    val lead: Double?,
    val mark: PlanPoint?,
    val mPerUnit: Double,
    val loopMaxM: Double,
)

/**
 * Podział pomieszczenia na pętle. Każda pętla dostaje WŁASNY dobieg: do miejsca,
 * w którym wychodzi ze swojej strefy. Gdy któraś pętla przez to nie mieści się
 * w limicie, dokładamy strefę i liczymy jeszcze raz.
 */
private fun splitRoom(room: RoomShape, scale: PlanScale, ctx: SplitCtx): RoomSplit? {
    if (room.outline.size < 3) return null
    val field = zoneField(room, scale.aspect)
    fun fromUnit(p: PlanPoint) = PlanPoint(p.x, p.y * scale.aspect)
    var count = max(1, loopsFor(ctx.heating, ctx.lead, ctx.loopMaxM).first)
    var out: RoomSplit? = null
    var guard = 0
    while (guard < SPLIT_TRIES) {
        val s = splitZones(field, count)
        if (s.zones.isEmpty()) return null
        // Sumy stref mają się zgadzać z pozycją CO DO ZAOKRĄGLEŃ, więc skalujemy
        // je do znanych `m2` i `heating`.
        val areaAll = s.zones.sumOf { it.heatArea }
        val pipeAll = s.zones.sumOf { it.pipeW }
        val m2K = if (areaAll > 0) ctx.m2 / areaAll else 0.0
        val mbK = if (pipeAll > 0) ctx.heating / pipeAll else 0.0
        val loops = s.zones.map { z ->
            val zoneHeating = r1(z.pipeW * mbK)
            val zoneLead = ctx.mark?.let { leadInM(distToZone(field, z, it) * ctx.mPerUnit) }
            PipeLoop(
                heating = zoneHeating,
                lead = zoneLead,
                total = r1(zoneHeating + (zoneLead ?: 0.0)),
                m2 = r2(z.heatArea * m2K),
                at = fromUnit(z.at),
                ratio = z.ratio,
            )
        }
        out = RoomSplit(
            loops = loops,
            cuts = s.cuts.map { ZoneCut(fromUnit(it.a), fromUnit(it.b)) },
            maxRatio = s.maxRatio,
            ratioOver = s.ratioOver,
        )
        val overLimit = loops.any { it.total > ctx.loopMaxM }
        val hopeless = loops.any { (it.lead ?: 0.0) >= ctx.loopMaxM }
        if (!overLimit || hopeless) break
        count += 1
        guard += 1
    }
    return out
}

/** Wyliczenie z obrysów na rzucie (z dobiegiem od rozdzielacza). */
private fun fromRooms(
    rooms: List<RoomShape>,
    marks: List<ManifoldMark>,
    scale: PlanScale,
    loopMaxM: Double,
): FloorPipe {
    val mPerUnit = cmPerUnit(scale) / 100
    val items = ArrayList<PipeItem>()
    rooms.forEachIndexed { i, room ->
        val parts = roomParts(room, scale)
        if (parts.isEmpty()) return@forEachIndexed
        val m2 = r2(parts.sumOf { it.m2 })
        val heating = r1(parts.sumOf { it.heating })
        if (m2 <= 0 || heating <= 0) return@forEachIndexed
        val mf = manifoldFor(room, marks, scale.aspect)
        val lead = if (mf.index == null || !mf.dist.isFinite()) {
            null
        } else {
            leadInM(mf.dist * mPerUnit)
        }
        val mark = mf.index?.let { unitPoint(marks[it].point, scale.aspect) }
        val split = splitRoom(
            room,
            scale,
            SplitCtx(m2, heating, lead, mark, mPerUnit, loopMaxM),
        )
        val loops = split?.loops?.size ?: loopsFor(heating, lead, loopMaxM).first
        val leadTotal = if (split != null) {
            r1(split.loops.sumOf { it.lead ?: 0.0 })
        } else {
            r1(loops * (lead ?: 0.0))
        }
        items += PipeItem(
            key = room.id,
            label = roomLabel(room, i),
            cat = room.cat,
            m2 = m2,
            m2Total = roomM2(room, scale) ?: m2,
            spacing = room.cat.spacingM ?: parts[0].spacing,
            parts = parts,
            heating = heating,
            lead = lead,
            leadTotal = leadTotal,
            manifoldIndex = mf.index,
            manifoldAuto = mf.auto,
            loops = loops,
            // Suma DOKŁADNIE tych pętli, które widać na rzucie.
            total = if (split != null) r1(split.loops.sumOf { it.total }) else r1(heating + leadTotal),
            impossible = if (split != null) {
                split.loops.any { (it.lead ?: 0.0) >= loopMaxM }
            } else {
                lead != null && lead >= loopMaxM
            },
            split = split,
        )
    }
    return totals(PipeSource.PLAN, items)
}

/** Wyliczenie z pól metrażu kondygnacji — bez dobiegu (nie wiadomo skąd). */
private fun fromAreas(areas: Map<AreaCat, Double>, loopMaxM: Double): FloorPipe {
    val items = ArrayList<PipeItem>()
    for (c in AreaCat.entries) {
        val spacing = c.spacingM
        // Kategoria bez rozstawu albo bez własnego pola metrażu („do ustalenia").
        if (spacing == null || c.field == null) continue
        val m2 = areas[c] ?: 0.0
        if (m2 <= 0) continue
        val heating = r1(m2 / spacing)
        val (loops, total) = loopsFor(heating, null, loopMaxM)
        items += PipeItem(
            key = c.key,
            label = "Powierzchnia ${c.short}",
            cat = c,
            m2 = m2,
            m2Total = m2,
            spacing = spacing,
            parts = listOf(PipePart(c, m2, spacing, heating, false)),
            heating = heating,
            lead = null,
            leadTotal = 0.0,
            manifoldIndex = null,
            manifoldAuto = false,
            loops = loops,
            total = total,
            impossible = false,
            split = null,
        )
    }
    return totals(PipeSource.AREAS, items)
}

/** Pola metrażu kondygnacji jako liczby — wejście dla rachunku z pól. */
private fun UfhFloor.areaNumbers(): Map<AreaCat, Double> =
    AreaCat.entries
        .filter { it.field != null }
        .associateWith { c -> area(c.field!!).toM2() ?: 0.0 }

/**
 * Długość rury OP dla jednej kondygnacji. Obrysy z rzutu mają pierwszeństwo
 * (dają dobieg od rozdzielacza); gdy ich nie ma, liczymy z pól metrażu.
 */
fun floorPipe(floor: UfhFloor, loopMaxM: Double): FloorPipe {
    val plan = floor.plan()
    if (scaleReady(plan.scale)) {
        val measured = fromRooms(plan.rooms, plan.marks, plan.scale!!, loopMaxM)
        if (measured.items.isNotEmpty()) return measured
    }
    val manual = fromAreas(floor.areaNumbers(), loopMaxM)
    return if (manual.items.isNotEmpty()) manual else FloorPipe(PipeSource.NONE)
}

/** Suma długości rury ze wszystkich kondygnacji. */
fun sumPipe(list: List<FloorPipe>): PipeSum = list.fold(PipeSum(0.0, 0, 0.0)) { acc, f ->
    PipeSum(
        total = r1(acc.total + f.total),
        loops = acc.loops + f.loops,
        heating = r1(acc.heating + f.heating),
    )
}

/** „1 pętla / 2 pętle / 5 pętli" — odmiana rzeczownika po liczbie. */
fun loopsLabel(n: Int): String {
    if (n == 1) return "1 pętla"
    val last = n % 10
    val teens = n % 100
    val few = last in 2..4 && teens !in 12..14
    return "$n ${if (few) "pętle" else "pętli"}"
}

/** Metry bieżące po polsku („182,4"). */
fun fmtMb(n: Double): String = String.format(java.util.Locale.US, "%.1f", n).replace('.', ',')
