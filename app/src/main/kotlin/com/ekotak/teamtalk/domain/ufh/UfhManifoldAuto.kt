package com.ekotak.teamtalk.domain.ufh

import com.ekotak.teamtalk.domain.model.UfhFloor

/**
 * AUTOMATYCZNY PRZYDZIAŁ pomieszczeń do rozdzielaczy — port `ufh-manifold-auto.ts`.
 *
 *   1. Jedna kropka na kondygnacji → wszystko idzie do niej.
 *   2. Kilka kropek → NAJBLIŻSZA (odległość do krawędzi obrysu, ta sama miara,
 *      którą liczą się dobiegi).
 *   3. CAŁE pomieszczenie trafia pod jeden rozdzielacz, nawet przy kilku pętlach.
 *   4. Skrzynka ponad limit belek (katalog systemu: KAN 12, TER/RZT 15) → automat
 *      przerzuca na sąsiednią te pomieszczenia, które tracą na tym najmniej dobiegu.
 *
 * Przydział liczymy W LOCIE i NIE zapisujemy: `RoomShape.manifoldId` zostaje
 * wyłącznie dla ręcznych decyzji audytora (te są święte). Decyzja usera: rura
 * liczy się WSZĘDZIE po tym przydziale (audyt, oferta, rozliczenie, montaż) —
 * patrz [floorPipe] dla `UfhFloor`.
 */

/** Wynik automatu — pomieszczenia gotowe do wszystkich dalszych wyliczeń. */
data class AutoManifolds(
    /** Kopie pomieszczeń z wypełnionym `manifoldId`. Do wyliczeń i widoku — NIE do zapisu. */
    val rooms: List<RoomShape>,
    /** Id pomieszczeń, którym rozdzielacz nadał automat (a nie audytor). */
    val auto: Set<String>,
    /** Ile pomieszczeń automat odsunął od najbliższej skrzynki przez limit belek. */
    val moved: Int,
    /** Skrzynki, które i po rebalansie zostają ponad limitem. */
    val overloaded: Int,
)

private fun asIs(rooms: List<RoomShape>) = AutoManifolds(rooms, emptySet(), 0, 0)

/** Proporcja rzutu do odległości: podana → ze skali → kwadrat. */
private fun usableAspect(aspect: Double, scale: PlanScale?): Double {
    if (aspect > 0) return aspect
    if (scale != null && scale.aspect > 0) return scale.aspect
    return 1.0
}

private fun nearestMark(room: RoomShape, marks: List<ManifoldMark>, aspect: Double): Int {
    var best = 0
    var bestD = Double.POSITIVE_INFINITY
    marks.forEachIndexed { i, m ->
        val d = distToRoom(m.point, room, aspect)
        if (d < bestD) {
            bestD = d
            best = i
        }
    }
    return best
}

/**
 * Rozładowanie przepełnionych skrzynek: bierzemy najbardziej przeciążoną i szukamy
 * JEDNEGO ruchu o najmniejszym przyroście dobiegu; powtarzamy, dopóki jest co
 * przenosić. Każde pomieszczenie przenosimy najwyżej raz.
 */
private fun rebalance(
    assign: MutableMap<String, Int>,
    manual: MutableSet<String>,
    rooms: List<RoomShape>,
    marks: List<ManifoldMark>,
    loopsOf: Map<String, Int>,
    aspect: Double,
    max: Int,
): Pair<Int, Int> {
    val load = IntArray(marks.size)
    for (r in rooms) {
        val idx = assign[r.id]
        if (idx != null) load[idx] += loopsOf[r.id] ?: 0
    }

    var moved = 0
    for (guard in rooms.indices) {
        var src = -1
        for (i in load.indices) {
            if (load[i] > max && (src < 0 || load[i] > load[src])) src = i
        }
        if (src < 0) break

        var bestRoom: RoomShape? = null
        var bestDst = -1
        var bestCost = Double.POSITIVE_INFINITY
        for (r in rooms) {
            if (manual.contains(r.id) || assign[r.id] != src) continue
            val loops = loopsOf[r.id] ?: 0
            if (loops < 1) continue
            val from = distToRoom(marks[src].point, r, aspect)
            for (dst in marks.indices) {
                if (dst == src) continue
                if (loopLoad(load[dst] + loops, max).level == LoopLevel.OVER) continue
                val cost = distToRoom(marks[dst].point, r, aspect) - from
                if (cost < bestCost) {
                    bestCost = cost
                    bestRoom = r
                    bestDst = dst
                }
            }
        }
        if (bestRoom == null || bestDst < 0) break

        val loops = loopsOf[bestRoom.id] ?: 0
        assign[bestRoom.id] = bestDst
        load[src] -= loops
        load[bestDst] += loops
        moved += 1
        manual += bestRoom.id // blokada przed cofnięciem ruchu w kolejnej iteracji
    }

    return moved to load.count { it > max }
}

/**
 * Przydział pomieszczeń do rozdzielaczy — najbliższa kropka + rozładowanie
 * przepełnionych skrzynek. Powierzchnie bez rozstawu rur zostają nieprzypisane.
 *
 * @param aspect proporcja obrazu; `0` = weź ze skali (tak liczy audyt i oferta)
 * @param loopMaxM limit długości pętli systemu rur ([ufhLoopMaxM])
 * @param manifoldMax limit pętli na skrzynkę ([ufhManifoldMax])
 */
fun autoManifolds(
    rooms: List<RoomShape>,
    marks: List<ManifoldMark>,
    scale: PlanScale?,
    aspect: Double = 0.0,
    loopMaxM: Double = PIPE_LOOP_MAX_M,
    manifoldMax: Int = MANIFOLD_LOOP_MAX,
): AutoManifolds {
    if (marks.isEmpty() || rooms.isEmpty()) return asIs(rooms)
    val a = usableAspect(aspect, scale)

    val assign = LinkedHashMap<String, Int>()
    val manual = LinkedHashSet<String>()
    val auto = LinkedHashSet<String>()
    for (r in rooms) {
        if (!roomHeats(r)) continue
        val idx = markIndexById(marks, r.manifoldId)
        if (idx >= 0) {
            assign[r.id] = idx
            manual += r.id
            continue
        }
        // Także przypisanie osierocone po skasowanej kropce wraca do automatu.
        assign[r.id] = nearestMark(r, marks, a)
        auto += r.id
    }
    if (assign.isEmpty()) return asIs(rooms)

    fun applied(): List<RoomShape> = rooms.map { r ->
        val idx = assign[r.id]
        val id = if (idx == null) "" else marks[idx].id
        if (id.isNotEmpty() && r.manifoldId != id) r.copy(manifoldId = id) else r
    }

    // Bez skali nie ma metrów, więc i pętli — zostaje sam „najbliższy".
    if (!scaleReady(scale) || marks.size < 2) {
        return AutoManifolds(applied(), auto, 0, 0)
    }

    val pipes = roomPipeMap(applied(), marks, scale, loopMaxM)
    val loopsOf = LinkedHashMap<String, Int>()
    for (r in rooms) loopsOf[r.id] = pipes[r.id]?.loops ?: 0
    val (moved, overloaded) = rebalance(assign, LinkedHashSet(manual), rooms, marks, loopsOf, a, manifoldMax)

    return AutoManifolds(applied(), auto, moved, overloaded)
}

/** Ile pomieszczeń pod OP przypiął ręcznie audytor (0 = wszystko z automatu). */
fun manualAssignedCount(rooms: List<RoomShape>, marks: List<ManifoldMark>): Int =
    rooms.count { roomHeats(it) && markIndexById(marks, it.manifoldId) >= 0 }

/** Kasuje ręczne przypisania — „przypisz od nowa", wszystko wraca pod automat. */
fun clearAssignments(rooms: List<RoomShape>): List<RoomShape> =
    rooms.map { if (it.manifoldId.isEmpty()) it else it.copy(manifoldId = "") }

/**
 * Pomieszczenia kondygnacji z przydziałem automatu dla systemu rur audytu —
 * WSPÓLNE wejście każdego rachunku z rzutu: rura i dobór w audycie, Oferta
 * ([ufhFloorPipes]), punkty ekipy, Montaż. Port `floorPlanRooms` panelu.
 */
fun floorPlanRooms(floor: UfhFloor, pipeSystem: String?): List<RoomShape> {
    val plan = floor.plan()
    return autoManifolds(
        plan.rooms,
        plan.marks,
        plan.scale,
        0.0,
        ufhLoopMaxM(pipeSystem),
        ufhManifoldMax(pipeSystem),
    ).rooms
}
