package com.ekotak.teamtalk.domain.ufh

/**
 * METRAŻ W ROZBICIU NA ROZDZIELACZE — port `ufh-manifold-groups.ts`.
 *
 * Sam przydział liczy [autoManifolds]; tu tylko grupujemy pomieszczenia z już
 * wypełnionym `manifoldId` i sumujemy metraż. Grupa `rest` zbiera powierzchnie
 * bez obwodu i przypisania osierocone po skasowanej kropce (pokazujemy je, nie
 * kasujemy po cichu).
 *
 * Nazwa typu inna niż w panelu (`ManifoldGroup`), bo tak nazywa się już grupa
 * zdjęć rozdzielacza w `domain.model` — ekrany importują oba pakiety naraz.
 */
data class ManifoldRoomGroup(
    /** Id kropki; `null` = grupa pomieszczeń bez rozdzielacza. */
    val id: String?,
    /** Numer kropki (0-based) — `null` dla grupy bez przypisania. */
    val index: Int?,
    val mark: ManifoldMark?,
    val rooms: List<RoomShape>,
    /** m² per kategoria (`null` = brak skali albo brak obrysów tej kategorii). */
    val perCat: Map<AreaCat, Double?>,
    /** Suma m² pod ogrzewaniem podłogowym (bez kategorii bez rozstawu). */
    val m2: Double?,
    /** Ile pomieszczeń grupy wskazuje rozdzielacz, którego już nie ma. */
    val orphans: Int,
)

data class ManifoldSplit(
    /** Grupy w kolejności kropek na rzucie (R1, R2, …). */
    val groups: List<ManifoldRoomGroup>,
    /** Pomieszczenia bez przypisania (w tym osierocone). */
    val rest: ManifoldRoomGroup,
    val orphans: Int,
)

private fun summarize(
    id: String?,
    index: Int?,
    mark: ManifoldMark?,
    rooms: List<RoomShape>,
    scale: PlanScale?,
    orphans: Int,
): ManifoldRoomGroup {
    val perCat = LinkedHashMap<AreaCat, Double?>()
    var ufh: Double? = null
    for (c in AreaCat.entries) {
        val sum = catM2(rooms, scale, c)
        perCat[c] = sum
        if (sum != null && c.spacingM != null) ufh = jsRound(((ufh ?: 0.0) + sum) * 10) / 10
    }
    return ManifoldRoomGroup(id, index, mark, rooms, perCat, ufh, orphans)
}

/** Rozbicie pomieszczeń na rozdzielacze (+ reszta bez przypisania). */
fun splitByManifold(rooms: List<RoomShape>, marks: List<ManifoldMark>, scale: PlanScale?): ManifoldSplit {
    val rest = ArrayList<RoomShape>()
    val buckets = List(marks.size) { ArrayList<RoomShape>() }
    var orphans = 0
    for (r in rooms) {
        val idx = markIndexById(marks, r.manifoldId)
        if (idx < 0) {
            if (r.manifoldId.isNotEmpty()) orphans += 1
            rest += r
            continue
        }
        buckets[idx] += r
    }
    return ManifoldSplit(
        groups = marks.mapIndexed { i, m -> summarize(m.id.ifEmpty { null }, i, m, buckets[i], scale, 0) },
        rest = summarize(null, null, null, rest, scale, orphans),
        orphans = orphans,
    )
}

/** Rozbicie grupy na kategorie w jednej linijce („10 cm 42.5 · 15 cm 8.1"). */
fun groupCatSummary(g: ManifoldRoomGroup): String =
    AreaCat.entries
        .filter { g.perCat[it] != null }
        .joinToString(" · ") { "${it.short} ${jsNum(g.perCat[it]!!)}" }
