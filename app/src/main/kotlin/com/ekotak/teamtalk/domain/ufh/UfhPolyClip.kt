package com.ekotak.teamtalk.domain.ufh

import kotlin.math.PI
import kotlin.math.abs

/**
 * Część wspólna dwóch wieloboków (Greiner–Hormann) — port `ufh-poly-clip.ts`.
 *
 * Na rzucie potrzebna w jednym miejscu: ŁATKA ZAGĘSZCZENIA narysowana tak, że
 * wystaje poza pomieszczenie, jest docinana do obrysu rodzica. Pomieszczenia
 * bywają wklęsłe (kształt L), więc obcinanie półpłaszczyznami (jak przy strefach)
 * tu nie wystarcza.
 *
 * Degeneracji (wierzchołek na krawędzi, krawędzie współliniowe) nie obsługujemy
 * „w środku": wykrywamy je i liczymy jeszcze raz dla obrysu przesuniętego
 * o ułamek piksela. Kolejność operacji jest przepisana z panelu 1:1 — inaczej
 * docięta łatka różniłaby się wierzchołkami, a więc i metrażem.
 */

/** Poniżej tej wartości uznajemy przecięcie za styczne/degeneracyjne. */
private const val CLIP_EPS = 1e-9

/** Ile razy próbujemy z przesuniętym obrysem, zanim się poddamy. */
private const val JITTER_TRIES = 8

/** Krok przesunięcia (współrzędne względne 0–1) — ułamek piksela rzutu. */
private const val JITTER_STEP = 1e-6

/** Zapora przed zapętleniem obchodu. */
private const val WALK_GUARD = 100000

private class ClipNode(
    val x: Double,
    val y: Double,
    /** Węzeł powstał z przecięcia krawędzi (a nie z wierzchołka obrysu). */
    val intersect: Boolean,
    /** Położenie na krawędzi (0–1) — do sortowania wstawianych przecięć. */
    val alpha: Double,
) {
    var entry = false
    var visited = false
    var neighbor: ClipNode? = null
    var next: ClipNode = this
    var prev: ClipNode = this
}

/** Rzucane, gdy geometria jest styczna — wołający ponawia z przesunięciem. */
private class Degenerate(message: String) : RuntimeException(message) {
    override fun fillInStackTrace(): Throwable = this
}

private fun buildRing(points: List<PlanPoint>): ClipNode {
    val nodes = points.map { ClipNode(it.x, it.y, false, 0.0) }
    for (i in nodes.indices) {
        nodes[i].next = nodes[(i + 1) % nodes.size]
        nodes[i].prev = nodes[(i - 1 + nodes.size) % nodes.size]
    }
    return nodes[0]
}

/** Kolejny węzeł będący wierzchołkiem obrysu (pomija wstawione przecięcia). */
private fun nextVertex(n: ClipNode): ClipNode {
    var c = n.next
    while (c.intersect) c = c.next
    return c
}

/** Przecięcie odcinków a→b i c→d: (alfa na ab, alfa na cd); `null` = brak. */
private fun intersectSegments(a: ClipNode, b: ClipNode, c: ClipNode, d: ClipNode): Pair<Double, Double>? {
    val rx = b.x - a.x
    val ry = b.y - a.y
    val sx = d.x - c.x
    val sy = d.y - c.y
    val denom = rx * sy - ry * sx
    val qpx = c.x - a.x
    val qpy = c.y - a.y
    if (abs(denom) < CLIP_EPS) {
        // Równoległe: degeneracją jest tylko pokrycie się (współliniowość).
        if (abs(qpx * ry - qpy * rx) < CLIP_EPS) throw Degenerate("collinear")
        return null
    }
    val t = (qpx * sy - qpy * sx) / denom
    val u = (qpx * ry - qpy * rx) / denom
    fun outside(v: Double) = v < -CLIP_EPS || v > 1 + CLIP_EPS
    if (outside(t) || outside(u)) return null
    // Trafienie dokładnie w wierzchołek — algorytm nie ma z tego wyjścia.
    fun touches(v: Double) = abs(v) < 1e-7 || abs(v - 1) < 1e-7
    if (touches(t) || touches(u)) throw Degenerate("vertex touch")
    return t to u
}

/** Wstawia przecięcie między `start` a następnym wierzchołkiem, wg `alpha`. */
private fun insertAfter(start: ClipNode, node: ClipNode) {
    var c = start
    while (c.next.intersect && c.next.alpha < node.alpha) c = c.next
    node.next = c.next
    node.prev = c
    c.next.prev = node
    c.next = node
}

private fun clipOnce(subject: List<PlanPoint>, clip: List<PlanPoint>): List<List<PlanPoint>> {
    val s0 = buildRing(subject)
    val c0 = buildRing(clip)

    // 1) przecięcia krawędzi — wstawiane do obu list i połączone parami
    var found = false
    var sv = s0
    do {
        val sNext = nextVertex(sv)
        var cv = c0
        do {
            val cNext = nextVertex(cv)
            val hit = intersectSegments(sv, sNext, cv, cNext)
            if (hit != null) {
                val x = sv.x + (sNext.x - sv.x) * hit.first
                val y = sv.y + (sNext.y - sv.y) * hit.first
                val a = ClipNode(x, y, true, hit.first)
                val b = ClipNode(x, y, true, hit.second)
                a.neighbor = b
                b.neighbor = a
                insertAfter(sv, a)
                insertAfter(cv, b)
                found = true
            }
            cv = cNext
        } while (cv !== c0)
        sv = sNext
    } while (sv !== s0)

    if (!found) {
        // Bez przecięć: albo jeden leży w drugim, albo są rozłączne.
        if (pointInPoly(subject[0], clip)) return listOf(subject.toList())
        if (pointInPoly(clip[0], subject)) return listOf(clip.toList())
        return emptyList()
    }

    // 2) wejście/wyjście — naprzemiennie od tego, czy start jest w środku
    fun markEntries(start: ClipNode, other: List<PlanPoint>) {
        var inside = pointInPoly(PlanPoint(start.x, start.y), other)
        var c = start
        do {
            if (c.intersect) {
                c.entry = !inside
                inside = !inside
            }
            c = c.next
        } while (c !== start)
    }
    markEntries(s0, clip)
    markEntries(c0, subject)

    // 3) obchód części wspólnej
    val pieces = ArrayList<List<PlanPoint>>()
    while (true) {
        var start: ClipNode? = null
        var c = s0
        do {
            if (c.intersect && !c.visited && c.entry) {
                start = c
                break
            }
            c = c.next
        } while (c !== s0)
        if (start == null) break

        val piece = ArrayList<PlanPoint>()
        var cur: ClipNode = start
        var guard = 0
        do {
            cur.visited = true
            cur.neighbor?.visited = true
            if (cur.entry) {
                do {
                    cur = cur.next
                    piece += PlanPoint(cur.x, cur.y)
                    guard += 1
                } while (!cur.intersect && guard < WALK_GUARD)
            } else {
                do {
                    cur = cur.prev
                    piece += PlanPoint(cur.x, cur.y)
                    guard += 1
                } while (!cur.intersect && guard < WALK_GUARD)
            }
            cur.visited = true
            val nb = cur.neighbor ?: throw Degenerate("dangling intersection")
            cur = nb
            if (guard >= WALK_GUARD) throw Degenerate("no progress")
        } while (cur !== start)

        if (piece.size >= 3) pieces += piece
    }

    if (pieces.isEmpty()) {
        // Krawędzie się dotknęły, ale części wspólnej nie ma.
        if (pointInPoly(subject[0], clip)) return listOf(subject.toList())
        return emptyList()
    }
    return pieces
}

/** Pole wieloboku (wzór Gaussa) we współrzędnych względnych. */
fun ringArea(points: List<PlanPoint>): Double {
    if (points.size < 3) return 0.0
    var sum = 0.0
    for (i in points.indices) {
        val p = points[i]
        val q = points[(i + 1) % points.size]
        sum += p.x * q.y - q.x * p.y
    }
    return abs(sum) / 2
}

private fun shifted(points: List<PlanPoint>, dx: Double, dy: Double): List<PlanPoint> =
    points.map { PlanPoint(it.x + dx, it.y + dy) }

/**
 * Część wspólna `subject ∩ clip` — lista kawałków. Pusta lista = brak części
 * wspólnej; `null` = geometria styczna nawet po przesunięciach (wołający ma
 * powiedzieć „nie umiem dociąć", zamiast zapisać byle co).
 */
fun polyIntersection(subject: List<PlanPoint>, clip: List<PlanPoint>): List<List<PlanPoint>>? {
    if (subject.size < 3 || clip.size < 3) return null
    for (i in 0 until JITTER_TRIES) {
        // Kierunki rozłożone po okręgu złotym kątem. `StrictMath` = fdlibm, czyli
        // dokładnie to, czego używa V8 — przesunięcie wychodzi co do bitu.
        val angle = (i * 2.399963) % (PI * 2)
        val r = JITTER_STEP * (i + 1)
        val s = if (i == 0) subject else shifted(subject, StrictMath.cos(angle) * r, StrictMath.sin(angle) * r)
        try {
            return clipOnce(s, clip).filter { ringArea(it) > 1e-12 }
        } catch (e: Degenerate) {
            // następna próba z przesunięciem
        }
    }
    return null
}

/** Największy kawałek części wspólnej — tego używamy jako dociętej łatki. */
fun largestIntersection(subject: List<PlanPoint>, clip: List<PlanPoint>): List<PlanPoint>? {
    val parts = polyIntersection(subject, clip)
    if (parts.isNullOrEmpty()) return null
    var best = parts[0]
    for (p in parts) if (ringArea(p) > ringArea(best)) best = p
    return best
}
