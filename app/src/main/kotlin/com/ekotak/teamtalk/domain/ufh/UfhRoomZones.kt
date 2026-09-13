package com.ekotak.teamtalk.domain.ufh

import kotlin.math.abs

/**
 * STREFY (PĘTLE) POMIESZCZEŃ na rzucie — port `ufh-room-zones.ts`.
 *
 * Geometrię tnie `UfhZoneSplit.kt`, długości i dobiegi liczy `UfhPipeLength.kt`;
 * tutaj zostaje przepakowanie jednego wyniku na to, co rysuje rzut i pokazują
 * listy — dzięki temu rzut, legenda i „Długość rury OP" nie pokażą innych liczb.
 */

/** Jedna strefa = jedna pętla w pomieszczeniu. */
data class RoomZone(
    /** Numer strefy w pomieszczeniu (1-based) — podpis „S1", „S2". */
    val n: Int,
    val m2: Double,
    /** Rura tej pętli [mb] = grzewcza strefy + jej dobieg. */
    val pipe: Double,
    /** Dobieg TEJ pętli [mb]; `null` = brak rozdzielacza na rzucie. */
    val lead: Double?,
    /** Kotwica podpisu na rzucie (współrzędne względne 0–1). */
    val at: PlanPoint,
    val ratio: Double,
)

/** Podział jednego pomieszczenia na strefy. */
data class RoomZones(
    val label: String,
    /** Ile pętli (= `PipeItem.loops`). */
    val count: Int,
    val m2: Double,
    /** Średnia powierzchnia strefy [m²]. */
    val perZoneM2: Double,
    /** Strefy różnią się polem, bo w pomieszczeniu są łatki zagęszczenia. */
    val mixed: Boolean,
    /** Rozstaw pola podstawowego [cm] — do podpisu. */
    val spacingCm: Int,
    /** Rura razem [mb] (grzewcza + dobiegi pętli). */
    val total: Double,
    val zones: List<RoomZone>,
    /** Linie podziału przycięte do obrysu (współrzędne względne). */
    val cuts: List<ZoneCut>,
    val maxRatio: Double,
    val ratioOver: Boolean,
)

/** „1 strefa / 2 strefy / 5 stref". */
fun zonesLabel(n: Int): String = plForm(n, "strefa", "strefy", "stref")

/** Proporcja po polsku — „1 : 2,5". */
fun ratioLabel(ratio: Double): String {
    if (!ratio.isFinite()) return "—"
    return "1 : ${jsFixed(ratio, 1).replace('.', ',')}"
}

/** Podział jednego pomieszczenia — przepakowanie pozycji z wyliczenia rury. */
fun roomZones(room: RoomShape, item: PipeItem?, scale: PlanScale?): RoomZones? {
    if (item == null || item.loops < 1 || item.split == null || !scaleReady(scale)) return null
    if (room.outline.size < 3) return null
    val split = item.split
    return RoomZones(
        label = item.label,
        count = item.loops,
        m2 = item.m2,
        perZoneM2 = r2(item.m2 / item.loops),
        mixed = room.patches.isNotEmpty(),
        spacingCm = jsRound(item.spacing * 100).toInt(),
        total = item.total,
        zones = split.loops.mapIndexed { i, l ->
            RoomZone(n = i + 1, m2 = l.m2, pipe = l.total, lead = l.lead, at = l.at, ratio = l.ratio)
        },
        cuts = split.cuts,
        maxRatio = split.maxRatio,
        ratioOver = split.ratioOver,
    )
}

/**
 * Podział wszystkich pomieszczeń kondygnacji (klucz = `RoomShape.id`).
 * Pomieszczenia mają przyjść PO automacie przydziału (`AutoManifolds.rooms`).
 */
fun planZones(
    rooms: List<RoomShape>,
    marks: List<ManifoldMark>,
    scale: PlanScale?,
    loopMaxM: Double = PIPE_LOOP_MAX_M,
): Map<String, RoomZones> {
    val pipes = roomPipeMap(rooms, marks, scale, loopMaxM)
    val out = LinkedHashMap<String, RoomZones>()
    if (pipes.isEmpty()) return out
    for (room in rooms) {
        val z = roomZones(room, pipes[room.id], scale)
        if (z != null) out[room.id] = z
    }
    return out
}

/** Suma stref (pętli) z podanych pomieszczeń — tyle obwodów wychodzi na belce. */
fun zonesTotal(zones: Map<String, RoomZones>, rooms: List<RoomShape>): Int =
    rooms.sumOf { zones[it.id]?.count ?: 0 }

/** Suma rury [mb] z podanych pomieszczeń (grzewcza + dobiegi ich pętli). */
fun zonesPipeTotal(zones: Map<String, RoomZones>, rooms: List<RoomShape>): Double {
    var t = 0.0
    for (r in rooms) t += zones[r.id]?.total ?: 0.0
    return r1(t)
}

/** „2 pomieszczenia / 5 pomieszczeń" (bez wyjątku dla jedynki — jak w panelu). */
private fun roomsCountLabel(n: Int): String {
    val last = n % 10
    val teens = n % 100
    val few = last in 2..4 && teens !in 12..14
    return "$n ${if (few) "pomieszczenia" else "pomieszczeń"}"
}

/** Ile nazw wypisujemy wprost, zanim komunikat zrobi się nieczytelny. */
private const val RATIO_NAMES_MAX = 4

/** Pomieszczenie z wydłużoną strefą — tyle wystarczy, żeby ułożyć komunikat. */
data class RatioOverRoom(val label: String, val maxRatio: Double)

/** Ostrzeżenie o wydłużonych strefach — z nazwami pomieszczeń, od najgorszej proporcji. */
fun ratioOverMessage(bad: List<RatioOverRoom>): String? {
    if (bad.isEmpty()) return null
    // Sortowanie stabilne, malejąco — jak `Array.prototype.sort` w V8.
    val sorted = bad.sortedWith { a, b -> b.maxRatio.compareTo(a.maxRatio) }
    val shown = sorted.take(RATIO_NAMES_MAX).joinToString(", ") { "${it.label} (${ratioLabel(it.maxRatio)})" }
    val rest = sorted.size - RATIO_NAMES_MAX
    val names = if (rest > 0) "$shown i $rest więcej" else shown
    val head = if (sorted.size == 1) {
        "Pomieszczenie $names ma strefę wydłużoną ponad zalecane ${ratioLabel(ZONE_MAX_RATIO)}"
    } else {
        "${roomsCountLabel(sorted.size)} mają strefy wydłużone ponad zalecane ${ratioLabel(ZONE_MAX_RATIO)}: $names"
    }
    return "$head — przy tej liczbie pętli kształt nie pozwala pociąć lepiej. Dołóż pętlę albo " +
        "rozdziel pomieszczenie osobnymi obrysami."
}

/** To samo ostrzeżenie dla całej kondygnacji (`null` = wszystkie w normie). */
fun zonesRatioMessage(zones: Map<String, RoomZones>, rooms: List<RoomShape>): String? =
    ratioOverMessage(
        rooms.mapNotNull { zones[it.id] }.filter { it.ratioOver }.map { RatioOverRoom(it.label, it.maxRatio) },
    )

/** Ostrzeżenie o wydłużonych strefach z pozycji rury kondygnacji (blok „Długość rury OP"). */
fun pipeRatioMessage(pipe: FloorPipe): String? =
    ratioOverMessage(
        pipe.items.filter { it.split?.ratioOver == true }.map { RatioOverRoom(it.label, it.split!!.maxRatio) },
    )

/** Zakres dobiegów pętli w pomieszczeniu („8,5–14,0 mb"); `null` = bez dobiegu. */
fun leadRangeLabel(item: PipeItem): String? {
    val lo = item.leadMin ?: return null
    val hi = item.leadMax ?: return null
    if (abs(hi - lo) < 0.05) return "${fmtMb(lo)} mb"
    return "${fmtMb(lo)}–${fmtMb(hi)} mb"
}
