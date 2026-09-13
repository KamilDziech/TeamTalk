package com.ekotak.teamtalk.presentation.crm

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.ekotak.teamtalk.domain.ufh.AreaCat
import com.ekotak.teamtalk.domain.ufh.ManifoldMark
import com.ekotak.teamtalk.domain.ufh.PlanPoint
import com.ekotak.teamtalk.domain.ufh.PlanScale
import com.ekotak.teamtalk.domain.ufh.RoomShape
import kotlin.math.hypot

/**
 * PŁÓTNO RZUTU KONDYGNACJI — mobilny odpowiednik `UfhAreaMeasureLayer`
 * + `ManifoldDot` + `HeatSourceDot` z panelu, razem z obsługą palca.
 *
 * Rysuje wyłącznie to, co dostanie (`PlanLayer`), i niczego nie liczy: przydział
 * rozdzielaczy, strefy i kolory przygotowuje edytor z tych samych funkcji, które
 * liczą długość rury — dzięki temu rzut nie może pokazać innego podziału niż
 * wyliczenie pod nim.
 *
 * Gesty:
 *  • dwa palce — powiększenie i przesuwanie (pudełko NADRZĘDNE),
 *  • dotknięcie — punkt na rzucie (`onTap`); edytor decyduje, co znaczy,
 *  • złapanie uchwytu (kropka rozdzielacza, źródło, wierzchołek zaznaczonego
 *    obrysu) i przeciągnięcie — przesunięcie; samo dotknięcie uchwytu to
 *    `onHandleTap`. Uchwyt zjada gest, więc rzut pod palcem wtedy nie jedzie.
 *
 * Współrzędne są względne (0–1), jak w zapisie panelu; pudełko wewnętrzne dostaje
 * dotyk w swoim układzie sprzed powiększenia, więc punkt ląduje pod palcem
 * niezależnie od zoomu. Rozmiary kropek i kresek dzielimy przez zoom — przy
 * powiększeniu rzut ma zyskać szczegół, a nie grube kółka.
 */

/** Uchwyt do przeciągania. `ring`: −1 obrys, ≥0 wycięcie, ≤ −2 łatka (`patchRing`). */
sealed interface PlanHandle {
    data class Mark(val index: Int) : PlanHandle
    data object Source : PlanHandle
    data class Vertex(val roomId: String, val ring: Int, val index: Int) : PlanHandle
}

/** Kodowanie łatki w `PlanHandle.Vertex.ring` — to samo co `patchRingIndex` panelu. */
const val PATCH_RING_BASE = -2

fun patchRing(patchIndex: Int): Int = PATCH_RING_BASE - patchIndex

fun ringToPatch(ring: Int): Int? = if (ring <= PATCH_RING_BASE) PATCH_RING_BASE - ring else null

/** Podział pomieszczenia na strefy do narysowania: linie cięcia i podpisy stref. */
data class ZoneOverlay(
    val cuts: List<Pair<PlanPoint, PlanPoint>> = emptyList(),
    val labels: List<Pair<PlanPoint, String>> = emptyList(),
)

/** Wszystko, co leży na rzucie. */
data class PlanLayer(
    val rooms: List<RoomShape> = emptyList(),
    val scale: PlanScale? = null,
    val marks: List<ManifoldMark> = emptyList(),
    /** Kolor kropki per rozdzielacz (rodzaj skrzynki). */
    val markColors: List<Color> = emptyList(),
    val heatSource: PlanPoint? = null,
    /** Strefy per pomieszczenie (id → podział); brak = bez podziału. */
    val zones: Map<String, ZoneOverlay> = emptyMap(),
    /** Id pomieszczenia → indeks kropki, do której biegnie kreska przydziału. */
    val roomManifold: Map<String, Int> = emptyMap(),
)

/** Obrys w trakcie rysowania. */
data class PlanDraft(val points: List<PlanPoint>, val cat: AreaCat, val hole: Boolean)

@Composable
fun UfhPlanCanvas(
    image: ImageBitmap,
    aspect: Float,
    layer: PlanLayer,
    catColor: (AreaCat) -> Color,
    modifier: Modifier = Modifier,
    /** Miniatura — bez żetonów, uchwytów i gestów. */
    small: Boolean = false,
    /** Podgląd historii — wszystko przygaszone i nieruchome. */
    ghost: Boolean = false,
    selectedRoomId: String? = null,
    selectedMark: Int? = null,
    draft: PlanDraft? = null,
    /** Punkty kalibracji w toku (0–2). */
    scaleDraft: List<PlanPoint> = emptyList(),
    /** Zapisany odcinek skali tylko w trybie „Skala" — jak w panelu. */
    showScale: Boolean = false,
    /** Które uchwyty da się złapać. */
    dragMarks: Boolean = false,
    dragSource: Boolean = false,
    dragVertices: Boolean = false,
    onTap: ((PlanPoint) -> Unit)? = null,
    onHandleTap: ((PlanHandle) -> Unit)? = null,
    onHandleDrag: ((PlanHandle, PlanPoint) -> Unit)? = null,
    onHandleDragEnd: ((PlanHandle) -> Unit)? = null,
) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val measurer = rememberTextMeasurer()

    // Gest czyta zawsze BIEŻĄCY stan (warstwa zmienia się w trakcie przeciągania).
    val layerNow by rememberUpdatedState(layer)
    val selectedNow by rememberUpdatedState(selectedRoomId)
    val flags by rememberUpdatedState(Triple(dragMarks, dragSource, dragVertices))
    val tapNow by rememberUpdatedState(onTap)
    val handleTapNow by rememberUpdatedState(onHandleTap)
    val dragNow by rememberUpdatedState(onHandleDrag)
    val dragEndNow by rememberUpdatedState(onHandleDragEnd)

    val outer = if (small) {
        modifier
    } else {
        modifier.pointerInput(Unit) {
            detectTransformGestures { _, pan, gestureZoom, _ ->
                zoom = (zoom * gestureZoom).coerceIn(1f, MAX_ZOOM)
                if (zoom <= 1f) {
                    offsetX = 0f
                    offsetY = 0f
                } else {
                    offsetX += pan.x
                    offsetY += pan.y
                }
            }
        }
    }

    Box(modifier = outer, contentAlignment = Alignment.Center) {
        val inner = Modifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .graphicsLayer {
                scaleX = zoom
                scaleY = zoom
                translationX = offsetX
                translationY = offsetY
            }
        Box(
            modifier = if (small || ghost) {
                inner
            } else {
                inner.pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        if (w <= 0f || h <= 0f) return@awaitEachGesture
                        fun toPoint(o: Offset) = PlanPoint(
                            x = (o.x / w).toDouble().coerceIn(0.0, 1.0),
                            y = (o.y / h).toDouble().coerceIn(0.0, 1.0),
                        )

                        val grabPx = HANDLE_GRAB_PX * density / zoom
                        val handle = hitHandle(
                            at = down.position,
                            w = w,
                            h = h,
                            radius = grabPx,
                            layer = layerNow,
                            selectedRoomId = selectedNow,
                            marks = flags.first,
                            source = flags.second,
                            vertices = flags.third,
                        )
                        if (handle == null) {
                            // Zwykłe dotknięcie. Przesunięcie rzutu (rodzic)
                            // zjada zdarzenia, więc po pociągnięciu punktu nie ma.
                            val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                            tapNow?.invoke(toPoint(up.position))
                            return@awaitEachGesture
                        }

                        down.consume()
                        val slop = viewConfiguration.touchSlop / zoom
                        var moved = false
                        while (true) {
                            val event = awaitPointerEvent()
                            // Drugi palec = powiększanie; uchwyt oddaje gest.
                            if (event.changes.size > 1) break
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            change.consume()
                            if (!change.pressed) {
                                if (moved) dragEndNow?.invoke(handle) else handleTapNow?.invoke(handle)
                                return@awaitEachGesture
                            }
                            if (!moved && (change.position - down.position).getDistance() > slop) {
                                moved = true
                            }
                            if (moved) dragNow?.invoke(handle, toPoint(change.position))
                        }
                        if (moved) dragEndNow?.invoke(handle)
                    }
                }
            },
        ) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.fillMaxSize(),
            )
            Canvas(Modifier.fillMaxSize()) {
                drawPlanLayer(
                    layer = layer,
                    catColor = catColor,
                    measurer = measurer,
                    small = small,
                    ghost = ghost,
                    zoom = zoom,
                    selectedRoomId = selectedRoomId,
                    selectedMark = selectedMark,
                    draft = draft,
                    scaleDraft = scaleDraft,
                    showScale = showScale,
                )
            }
        }
    }
}

// ── Trafienie w uchwyt ───────────────────────────────────────────────────────

private fun hitHandle(
    at: Offset,
    w: Float,
    h: Float,
    radius: Float,
    layer: PlanLayer,
    selectedRoomId: String?,
    marks: Boolean,
    source: Boolean,
    vertices: Boolean,
): PlanHandle? {
    fun dist(p: PlanPoint) = hypot(p.x.toFloat() * w - at.x, p.y.toFloat() * h - at.y)
    var best: PlanHandle? = null
    var bestD = radius
    if (marks) {
        layer.marks.forEachIndexed { i, m ->
            val d = dist(m.point)
            if (d <= bestD) {
                bestD = d
                best = PlanHandle.Mark(i)
            }
        }
    }
    if (source) {
        layer.heatSource?.let {
            val d = dist(it)
            if (d <= bestD) {
                bestD = d
                best = PlanHandle.Source
            }
        }
    }
    // Kropki mają pierwszeństwo: stoją zwykle na ścianie, tuż przy wierzchołkach.
    if (best != null) return best
    if (vertices && selectedRoomId != null) {
        val room = layer.rooms.firstOrNull { it.id == selectedRoomId } ?: return null
        room.outline.forEachIndexed { i, p ->
            val d = dist(p)
            if (d <= bestD) {
                bestD = d
                best = PlanHandle.Vertex(room.id, -1, i)
            }
        }
        room.holes.forEachIndexed { hi, hole ->
            hole.forEachIndexed { i, p ->
                val d = dist(p)
                if (d <= bestD) {
                    bestD = d
                    best = PlanHandle.Vertex(room.id, hi, i)
                }
            }
        }
        room.patches.forEachIndexed { pi, patch ->
            patch.outline.forEachIndexed { i, p ->
                val d = dist(p)
                if (d <= bestD) {
                    bestD = d
                    best = PlanHandle.Vertex(room.id, patchRing(pi), i)
                }
            }
        }
    }
    return best
}

// ── Rysowanie ────────────────────────────────────────────────────────────────

private fun DrawScope.drawPlanLayer(
    layer: PlanLayer,
    catColor: (AreaCat) -> Color,
    measurer: TextMeasurer,
    small: Boolean,
    ghost: Boolean,
    zoom: Float,
    selectedRoomId: String?,
    selectedMark: Int?,
    draft: PlanDraft?,
    scaleDraft: List<PlanPoint>,
    showScale: Boolean,
) {
    val k = (if (small) 0.6f else 1f) * density / zoom
    val alpha = if (ghost) 0.55f else 1f
    fun o(p: PlanPoint) = Offset(p.x.toFloat() * size.width, p.y.toFloat() * size.height)
    val anyActive = !ghost && selectedRoomId != null
    val manyMarks = layer.marks.size > 1

    layer.rooms.forEachIndexed { i, room ->
        val color = catColor(room.cat)
        val act = !ghost && room.id == selectedRoomId
        val dim = anyActive && !act
        val a = alpha * if (dim) 0.42f else 1f
        val mid = centroidOf(room.outline)

        // Kreska „pomieszczenie → jego rozdzielacz".
        if (manyMarks) {
            layer.roomManifold[room.id]?.let { idx ->
                layer.marks.getOrNull(idx)?.let { m ->
                    drawLine(
                        color = color.copy(alpha = 0.75f * a),
                        start = o(mid),
                        end = o(m.point),
                        strokeWidth = 1.5f * k,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f * k, 4f * k)),
                    )
                }
            }
        }

        val path = ringPath(room.outline, room.holes)
        drawPath(path, color.copy(alpha = (if (act) 0.46f else if (dim) 0.14f else 0.26f) * a))
        drawPath(
            path,
            color.copy(alpha = a),
            style = Stroke(width = (if (act) 3f else 2f) * k, join = StrokeJoin.Round),
        )

        // Wycięcia — przerywana obwódka na przybielonym tle.
        room.holes.filter { it.size >= 3 }.forEach { hole ->
            val hp = ringPath(hole, emptyList())
            drawPath(hp, Color.White.copy(alpha = 0.72f * a))
            drawPath(
                hp,
                color.copy(alpha = a),
                style = Stroke(
                    width = 2f * k,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f * k, 5f * k)),
                ),
            )
        }

        // Łatki zagęszczenia — kreskowanie w kolorze kategorii łatki.
        room.patches.forEachIndexed { pi, patch ->
            if (patch.outline.size < 3) return@forEachIndexed
            val pc = catColor(patch.cat)
            val pp = ringPath(patch.outline, emptyList())
            drawPath(pp, pc.copy(alpha = 0.3f * a))
            clipPath(pp) { hatch(pc.copy(alpha = 0.9f * a), 10f * k, 3f * k) }
            drawPath(pp, pc.copy(alpha = a), style = Stroke(width = 2f * k))
            if (!small) {
                badge(
                    measurer = measurer,
                    center = o(centroidOf(patch.outline)),
                    text = "${i + 1}${('a' + pi)}",
                    fill = pc.copy(alpha = a),
                    radius = 9f * k,
                    fontPx = 9f * k,
                    dashed = true,
                )
            }
            if (act && !small) {
                patch.outline.forEach { drawHandle(o(it), pc, 5f * k) }
            }
        }

        // Podział na strefy (pętle).
        layer.zones[room.id]?.let { z ->
            z.cuts.forEach { (ca, cb) ->
                drawLine(Color.White.copy(alpha = 0.85f * a), o(ca), o(cb), strokeWidth = 3f * k, cap = StrokeCap.Round)
                drawLine(
                    color.copy(alpha = a),
                    o(ca),
                    o(cb),
                    strokeWidth = 1.5f * k,
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f * k, 5f * k)),
                )
            }
            if (!small && z.labels.size > 1) {
                z.labels.forEach { (at, text) ->
                    label(measurer, o(at), text, color.copy(alpha = a), 9f * k)
                }
            }
        }

        if (!small) {
            badge(
                measurer = measurer,
                center = o(mid),
                text = "${i + 1}",
                fill = color.copy(alpha = a),
                radius = (if (act) 13f else 11f) * k,
                fontPx = 11f * k,
                dashed = false,
            )
        }

        if (act && !small) {
            room.outline.forEach { drawHandle(o(it), color, 6f * k, hollow = true) }
            room.holes.forEach { hole -> hole.forEach { drawHandle(o(it), color, 5f * k) } }
        }
    }

    // Obrys rysowany teraz.
    if (draft != null && draft.points.isNotEmpty()) {
        val c = catColor(draft.cat)
        val pts = draft.points.map { o(it) }
        if (pts.size > 2) {
            drawPath(ringPath(draft.points, emptyList()), c.copy(alpha = 0.18f))
        }
        for (j in 1 until pts.size) {
            drawLine(
                color = if (draft.hole) Color(0xFF080808) else c,
                start = pts[j - 1],
                end = pts[j],
                strokeWidth = 2.5f * k,
                pathEffect = if (draft.hole) PathEffect.dashPathEffect(floatArrayOf(7f * k, 5f * k)) else null,
            )
        }
        pts.forEachIndexed { j, p ->
            // Pierwszy punkt biały — dotknięcie go domyka wielobok.
            if (j == 0) drawHandle(p, c, 7f * k, hollow = true) else drawHandle(p, c, 5f * k)
        }
    }

    // Odcinek kalibracji.
    val scale = layer.scale
    if (showScale && scale != null) {
        drawLine(SCALE_GREEN, o(scale.a), o(scale.b), strokeWidth = 3f * k)
        listOf(scale.a, scale.b).forEach { drawHandle(o(it), SCALE_GREEN, 5f * k, ring = Color(0xFF080808)) }
        if (!small) {
            val m = PlanPoint((scale.a.x + scale.b.x) / 2, (scale.a.y + scale.b.y) / 2)
            label(measurer, o(m) - Offset(0f, 14f * k), "skala ${fmtCm(scale.cm)} cm", Color(0xFF080808), 10f * k)
        }
    }
    if (scaleDraft.isNotEmpty()) {
        if (scaleDraft.size == 2) drawLine(SCALE_GREEN, o(scaleDraft[0]), o(scaleDraft[1]), strokeWidth = 3f * k)
        scaleDraft.forEach { drawHandle(o(it), SCALE_GREEN, 6f * k, ring = Color(0xFF080808)) }
    }

    // Źródło ciepła — zielony romb z „Ź".
    layer.heatSource?.let { src ->
        val c = o(src)
        val r = (if (small) 7f else 11f) * k
        rotate(45f, pivot = c) {
            drawRect(
                color = HEAT_SOURCE.copy(alpha = alpha),
                topLeft = c - Offset(r * 0.75f, r * 0.75f),
                size = androidx.compose.ui.geometry.Size(r * 1.5f, r * 1.5f),
            )
            drawRect(
                color = Color.White.copy(alpha = alpha),
                topLeft = c - Offset(r * 0.75f, r * 0.75f),
                size = androidx.compose.ui.geometry.Size(r * 1.5f, r * 1.5f),
                style = Stroke(width = 1.5f * k),
            )
        }
        if (!small) label(measurer, c, "Ź", Color(0xFF080808), 10f * k, halo = false)
    }

    // Kropki rozdzielaczy — numerowane, w kolorze skrzynki.
    layer.marks.forEachIndexed { i, m ->
        val c = o(m.point)
        val fill = (layer.markColors.getOrNull(i) ?: Color(0xFF1565C0)).copy(alpha = alpha)
        val r = (if (small) 7f else 13f) * k
        if (selectedMark == i && !ghost) {
            drawCircle(Color(0xFFFFC107), radius = r + 4f * k, center = c)
        }
        drawCircle(fill, radius = r, center = c)
        drawCircle(Color.White.copy(alpha = alpha), radius = r, center = c, style = Stroke(width = 2f * k))
        if (!small) label(measurer, c, "${i + 1}", textOn(fill), 11f * k, halo = false)
    }
}

private fun DrawScope.ringPath(points: List<PlanPoint>, holes: List<List<PlanPoint>>): Path =
    Path().apply {
        fillType = PathFillType.EvenOdd
        fun ring(list: List<PlanPoint>) {
            if (list.size < 3) return
            moveTo(list[0].x.toFloat() * size.width, list[0].y.toFloat() * size.height)
            for (i in 1 until list.size) lineTo(list[i].x.toFloat() * size.width, list[i].y.toFloat() * size.height)
            close()
        }
        ring(points)
        holes.forEach { ring(it) }
    }

/** Ukośne kreskowanie całego płótna (wołane w obrębie `clipPath`). */
private fun DrawScope.hatch(color: Color, step: Float, width: Float) {
    val span = size.width + size.height
    var x = -size.height
    while (x < span) {
        drawLine(color, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = width)
        x += step
    }
}

private fun DrawScope.drawHandle(
    at: Offset,
    color: Color,
    radius: Float,
    hollow: Boolean = false,
    ring: Color = Color.White,
) {
    drawCircle(if (hollow) Color.White else color, radius = radius, center = at)
    drawCircle(if (hollow) color else ring, radius = radius, center = at, style = Stroke(width = radius * 0.35f))
}

private fun DrawScope.badge(
    measurer: TextMeasurer,
    center: Offset,
    text: String,
    fill: Color,
    radius: Float,
    fontPx: Float,
    dashed: Boolean,
) {
    drawCircle(fill, radius = radius, center = center)
    drawCircle(
        Color.White,
        radius = radius,
        center = center,
        style = Stroke(
            width = radius * 0.18f,
            pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(radius * 0.4f, radius * 0.3f)) else null,
        ),
    )
    label(measurer, center, text, textOn(fill), fontPx, halo = false)
}

private fun DrawScope.label(
    measurer: TextMeasurer,
    center: Offset,
    text: String,
    color: Color,
    fontPx: Float,
    halo: Boolean = true,
) {
    val style = TextStyle(
        color = color,
        fontSize = (fontPx / density / fontScale).sp,
        fontWeight = FontWeight.SemiBold,
    )
    val layout = measurer.measure(text, style)
    val topLeft = center - Offset(layout.size.width / 2f, layout.size.height / 2f)
    if (halo) {
        drawRoundRect(
            color = Color.White.copy(alpha = 0.8f),
            topLeft = topLeft - Offset(2f, 0f),
            size = androidx.compose.ui.geometry.Size(layout.size.width + 4f, layout.size.height.toFloat()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f),
        )
    }
    drawText(layout, topLeft = topLeft)
}

/** Środek wieloboku do żetonu — średnia wierzchołków, jak `centroid` panelu. */
fun centroidOf(points: List<PlanPoint>): PlanPoint {
    if (points.isEmpty()) return PlanPoint(0.5, 0.5)
    return PlanPoint(points.sumOf { it.x } / points.size, points.sumOf { it.y } / points.size)
}

private fun textOn(fill: Color): Color = if (fill.luminance() > 0.5f) Color(0xFF080808) else Color.White

private fun fmtCm(cm: Double): String =
    if (cm % 1.0 == 0.0) cm.toLong().toString() else cm.toString().replace('.', ',')

/** Promień złapania uchwytu w dp — palec to nie kursor. */
private const val HANDLE_GRAB_PX = 22f
private const val MAX_ZOOM = 8f

private val SCALE_GREEN = Color(0xFF44D62C)
private val HEAT_SOURCE = Color(0xFF44D62C)
