package com.ekotak.teamtalk.presentation.crm

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.Locale
import com.ekotak.teamtalk.domain.model.DealDocument
import com.ekotak.teamtalk.domain.ufh.AreaCat
import com.ekotak.teamtalk.domain.ufh.AreaPatch
import com.ekotak.teamtalk.domain.ufh.PlanPoint
import com.ekotak.teamtalk.domain.ufh.PlanPrep
import com.ekotak.teamtalk.domain.ufh.PlanPrepHistoryEntry
import com.ekotak.teamtalk.domain.ufh.PlanScale
import com.ekotak.teamtalk.domain.ufh.RoomShape
import com.ekotak.teamtalk.domain.ufh.newPatchId
import com.ekotak.teamtalk.domain.ufh.newRoomId
import com.ekotak.teamtalk.domain.ufh.nowIso
import com.ekotak.teamtalk.domain.ufh.parsePlanPrep
import com.ekotak.teamtalk.domain.ufh.pointInPoly
import com.ekotak.teamtalk.domain.ufh.prepM2
import com.ekotak.teamtalk.domain.ufh.pushPrepHistory
import com.ekotak.teamtalk.domain.ufh.roomLabel
import com.ekotak.teamtalk.domain.ufh.roomM2
import com.ekotak.teamtalk.domain.ufh.sameRooms
import com.ekotak.teamtalk.domain.ufh.sameScale
import com.ekotak.teamtalk.domain.ufh.scaleReady

/**
 * „PRZYGOTUJ RZUT" — mobilny odpowiednik `PlanPrepModal` panelu, czyli okna
 * pomiaru zawężonego do dwóch narzędzi: kalibracji skali i obrysów pomieszczeń.
 * Rozdzielacz i źródło ciepła to decyzje audytu, więc ich tu nie ma, a obrys
 * dostaje kategorię „rozstaw do ustalenia" — na tym etapie nikt go nie zna.
 *
 * Zapis idzie do `DealDocument.planData` tego pliku i to jedyne miejsce, gdzie
 * telefon zapisuje geometrię rzutu. Audyt wczyta ją PRZYCISKIEM, nigdy sam —
 * przygotowanie nie może po cichu nadpisać pracy audytora.
 *
 * Wobec panelu brakuje tu jednego: przycinania łatki do obrysu pomieszczenia.
 * Panel dokleja wystający fragment do części wspólnej; my wymagamy, żeby cała
 * łatka leżała w środku, a obrys wystający poza pomieszczenie traktujemy jako
 * nowe pomieszczenie. Docinanie wieloboku palcem i tak byłoby zgadywanką, a
 * konsekwencje takiej pomyłki (metraż w cudzym pomieszczeniu) są gorsze niż
 * ponowne obrysowanie.
 */
@Composable
fun PlanPrepEditor(
    document: DealDocument,
    slotLabel: String,
    saving: Boolean,
    onSave: (PlanPrep) -> Unit,
    onClose: () -> Unit,
) {
    val store = rememberDocumentFileStore()
    val base = remember(document.id, document.planData) {
        parsePlanPrep(document.planData) ?: PlanPrep()
    }

    var bitmap by remember(document.id) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(document.id) {
        bitmap = store.image(document, page = 1, targetPx = EDITOR_PX)
    }

    var mode by remember { mutableStateOf(PrepMode.AREA) }
    var pen by remember { mutableStateOf(AreaCat.TODO_CAT) }
    var rooms by remember(base) { mutableStateOf(base.rooms) }
    var scale by remember(base) { mutableStateOf(base.scale) }
    var selected by remember { mutableStateOf<String?>(null) }
    var outline by remember { mutableStateOf<List<PlanPoint>>(emptyList()) }
    var scalePoints by remember { mutableStateOf<List<PlanPoint>>(emptyList()) }
    var note by remember { mutableStateOf<String?>(null) }

    var askCm by remember { mutableStateOf(false) }
    var askShape by remember { mutableStateOf<ShapeQuestion?>(null) }
    var renaming by remember { mutableStateOf<RoomShape?>(null) }
    var confirmClose by remember { mutableStateOf(false) }

    val aspect = bitmap?.let { it.width.toFloat() / it.height.toFloat() }?.takeIf { it > 0f } ?: 1f
    val ready = scaleReady(scale)
    val dirty = !sameRooms(base.rooms, rooms) || !sameScale(base.scale, scale)

    /** Domknięcie obrysu: nowe pomieszczenie, łatka albo wycięcie. */
    fun closeShape() {
        if (outline.size < 3) {
            note = "Wielobok potrzebuje co najmniej 3 punktów."
            return
        }
        if (rooms.size >= ROOMS_LIMIT) {
            note = "Limit $ROOMS_LIMIT pomieszczeń na kondygnację."
            return
        }
        // Obrys w całości wewnątrz istniejącego pomieszczenia to nie jest nowe
        // pomieszczenie — pytamy, czym ma być, dokładnie jak panel.
        val host = rooms.lastOrNull { room -> outline.all { pointInPoly(it, room.outline) } }
        if (host != null) {
            askShape = ShapeQuestion(host, outline)
            return
        }
        val room = RoomShape(id = newRoomId(), name = "", cat = pen, outline = outline)
        rooms = rooms + room
        selected = room.id
        outline = emptyList()
        note = roomM2(room, scale)?.let { "Dodano ${it.pl2()} m²." }
    }

    Dialog(
        onDismissRequest = { if (dirty) confirmClose = true else onClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            // ── Belka ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { if (dirty) confirmClose = true else onClose() }) {
                    Icon(Icons.Default.Close, contentDescription = "Zamknij")
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = slotLabel,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = document.displayName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Button(
                    onClick = {
                        onSave(
                            PlanPrep(
                                scale = scale,
                                rooms = rooms,
                                // Historia odkłada stan SPRZED zapisu, więc da
                                // się wrócić do poprzednich obrysów w panelu.
                                history = pushPrepHistory(
                                    base.history,
                                    PlanPrepHistoryEntry(
                                        at = nowIso(),
                                        kind = "area",
                                        rooms = base.rooms,
                                        scale = base.scale,
                                    ),
                                ),
                            ),
                        )
                        onClose()
                    },
                    enabled = dirty && !saving,
                ) {
                    if (saving) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Zapisz")
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // ── Rzut ─────────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF101010)),
                contentAlignment = Alignment.Center,
            ) {
                val image = bitmap
                if (image == null) {
                    CircularProgressIndicator(color = Color.White)
                } else {
                    PlanCanvas(
                        image = image,
                        aspect = aspect,
                        rooms = rooms,
                        scale = scale,
                        selectedId = selected,
                        outline = outline,
                        scalePoints = scalePoints,
                        primary = MaterialTheme.colorScheme.primary,
                        onTap = { point ->
                            note = null
                            when (mode) {
                                PrepMode.SCALE -> {
                                    scalePoints =
                                        if (scalePoints.size >= 2) listOf(point)
                                        else scalePoints + point
                                    if (scalePoints.size == 2) askCm = true
                                }

                                PrepMode.AREA -> {
                                    if (!ready) {
                                        note = "Najpierw skalibruj skalę rzutu."
                                    } else if (outline.size >= OUTLINE_MAX) {
                                        note = "Za dużo punktów w jednym obrysie."
                                    } else {
                                        outline = outline + point
                                    }
                                }
                            }
                        },
                    )
                }
            }

            // ── Narzędzia ────────────────────────────────────────────────────
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoicePill(
                        label = "Skala",
                        selected = mode == PrepMode.SCALE,
                        onClick = {
                            mode = PrepMode.SCALE
                            outline = emptyList()
                        },
                    )
                    ChoicePill(
                        label = "Obrysy",
                        selected = mode == PrepMode.AREA,
                        onClick = {
                            mode = PrepMode.AREA
                            scalePoints = emptyList()
                        },
                    )
                }

                when (mode) {
                    PrepMode.SCALE -> ScaleTools(
                        scale = scale,
                        points = scalePoints,
                        onClear = {
                            scale = null
                            scalePoints = emptyList()
                        },
                    )

                    PrepMode.AREA -> AreaTools(
                        ready = ready,
                        pen = pen,
                        onPen = { pen = it },
                        outlineSize = outline.size,
                        onUndo = { outline = outline.dropLast(1) },
                        onClose = { closeShape() },
                    )
                }

                note?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                if (mode == PrepMode.AREA) {
                    RoomList(
                        rooms = rooms,
                        scale = scale,
                        selectedId = selected,
                        onSelect = { selected = if (selected == it.id) null else it.id },
                        onRename = { renaming = it },
                        onDelete = { room ->
                            rooms = rooms.filterNot { it.id == room.id }
                            if (selected == room.id) selected = null
                        },
                    )
                    val total = prepM2(PlanPrep(scale = scale, rooms = rooms))
                    Text(
                        text = if (total != null) {
                            "${rooms.size} pom. · ${total.pl2()} m²"
                        } else {
                            "${rooms.size} pom. · bez skali"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }

    // ── Okna pomocnicze ──────────────────────────────────────────────────────

    if (askCm) {
        LengthDialog(
            onDismiss = {
                askCm = false
                scalePoints = emptyList()
            },
            onConfirm = { cm ->
                askCm = false
                scale = PlanScale(
                    a = scalePoints[0],
                    b = scalePoints[1],
                    cm = cm,
                    aspect = aspect.toDouble(),
                    planDocId = document.id,
                )
                scalePoints = emptyList()
                mode = PrepMode.AREA
                note = "Skala ustawiona: $cm cm."
            },
        )
    }

    askShape?.let { question ->
        val index = rooms.indexOfFirst { it.id == question.host.id }
        AlertDialog(
            onDismissRequest = { askShape = null },
            title = { Text("Obrys wewnątrz pomieszczenia") },
            text = {
                Text(
                    "Ten wielobok leży w całości w „${roomLabel(question.host, index)}”. " +
                        "Czym ma być?",
                )
            },
            confirmButton = {
                Column {
                    TextButton(onClick = {
                        // Wycięcie — kominek, schody, słup. Metraż znika z sumy.
                        rooms = rooms.map {
                            if (it.id == question.host.id) {
                                it.copy(holes = it.holes + listOf(question.outline))
                            } else {
                                it
                            }
                        }
                        outline = emptyList()
                        askShape = null
                        note = "Wycięcie odjęte od powierzchni."
                    }) { Text("Wycięcie (odejmij metraż)") }

                    TextButton(onClick = {
                        // Łatka — ten sam obwód, inny rozstaw; metraż zostaje.
                        rooms = rooms.map {
                            if (it.id == question.host.id) {
                                it.copy(
                                    patches = it.patches + AreaPatch(
                                        id = newPatchId(),
                                        cat = pen,
                                        outline = question.outline,
                                    ),
                                )
                            } else {
                                it
                            }
                        }
                        outline = emptyList()
                        askShape = null
                        note = "Zagęszczenie dopisane do pomieszczenia."
                    }) { Text("Zagęszczenie (łatka)") }

                    TextButton(onClick = {
                        val room = RoomShape(
                            id = newRoomId(),
                            name = "",
                            cat = pen,
                            outline = question.outline,
                        )
                        rooms = rooms + room
                        selected = room.id
                        outline = emptyList()
                        askShape = null
                    }) { Text("Osobne pomieszczenie") }
                }
            },
            dismissButton = { TextButton(onClick = { askShape = null }) { Text("Anuluj") } },
        )
    }

    renaming?.let { room ->
        RenameDialog(
            room = room,
            onDismiss = { renaming = null },
            onConfirm = { name, cat ->
                rooms = rooms.map { if (it.id == room.id) it.copy(name = name, cat = cat) else it }
                renaming = null
            },
        )
    }

    if (confirmClose) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            title = { Text("Porzucić zmiany?") },
            text = { Text("Obrysy i skala z tej sesji nie zostaną zapisane.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClose = false
                    onClose()
                }) { Text("Porzuć") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClose = false }) { Text("Wróć do rysowania") }
            },
        )
    }
}

private enum class PrepMode { SCALE, AREA }

private data class ShapeQuestion(val host: RoomShape, val outline: List<PlanPoint>)

// ── Płótno ───────────────────────────────────────────────────────────────────

/**
 * Rzut z warstwą obrysów. Punkty są WZGLĘDNE (0–1 wobec boków obrazu), więc
 * powiększenie ani obrót ekranu niczego nie przesuwają — to ta sama konwencja,
 * w której panel zapisuje geometrię.
 *
 * Powiększanie i przesuwanie siedzi na pudełku NADRZĘDNYM, a dotknięcia łapie
 * pudełko wewnętrzne: Compose podaje mu współrzędne sprzed przekształcenia,
 * więc punkt ląduje tam, gdzie palec, niezależnie od zoomu.
 */
@Composable
private fun PlanCanvas(
    image: ImageBitmap,
    aspect: Float,
    rooms: List<RoomShape>,
    scale: PlanScale?,
    selectedId: String?,
    outline: List<PlanPoint>,
    scalePoints: List<PlanPoint>,
    primary: Color,
    onTap: (PlanPoint) -> Unit,
) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, gestureZoom, _ ->
                    zoom = (zoom * gestureZoom).coerceIn(1f, MAX_PLAN_ZOOM)
                    if (zoom <= 1f) {
                        offsetX = 0f
                        offsetY = 0f
                    } else {
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspect)
                .graphicsLayer {
                    scaleX = zoom
                    scaleY = zoom
                    translationX = offsetX
                    translationY = offsetY
                }
                .pointerInput(Unit) {
                    detectTapGestures { position ->
                        val width = size.width.toFloat()
                        val height = size.height.toFloat()
                        if (width <= 0f || height <= 0f) return@detectTapGestures
                        onTap(
                            PlanPoint(
                                x = (position.x / width).toDouble().coerceIn(0.0, 1.0),
                                y = (position.y / height).toDouble().coerceIn(0.0, 1.0),
                            ),
                        )
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
                rooms.forEach { room ->
                    val color = if (room.cat == AreaCat.NONE) NO_UFH_COLOR else primary
                    val emphasised = room.id == selectedId
                    drawRing(
                        points = room.outline,
                        holes = room.holes,
                        color = color,
                        fillAlpha = if (emphasised) 0.35f else 0.18f,
                        strokeWidth = if (emphasised) 3f else 2f,
                    )
                    room.patches.forEach { patch ->
                        drawRing(
                            points = patch.outline,
                            holes = emptyList(),
                            color = if (patch.cat == AreaCat.NONE) NO_UFH_COLOR else PATCH_COLOR,
                            fillAlpha = 0.30f,
                            strokeWidth = 2f,
                        )
                    }
                    // Numer pomieszczenia nie mieści się czytelnie na 360 dp —
                    // kolejność mówi lista pod rzutem, tu wystarczy kropka.
                    room.outline.firstOrNull()?.let {
                        drawCircle(
                            color = color,
                            radius = if (emphasised) 7f else 4f,
                            center = it.toOffset(size.width, size.height),
                        )
                    }
                }

                // Obrys w budowie — linie i punkty, żeby było widać, gdzie palec
                // faktycznie postawił wierzchołek.
                if (outline.isNotEmpty()) {
                    val points = outline.map { it.toOffset(size.width, size.height) }
                    for (i in 1 until points.size) {
                        drawLine(primary, points[i - 1], points[i], strokeWidth = 3f)
                    }
                    points.forEach { drawCircle(primary, radius = 6f, center = it) }
                }

                // Odcinek kalibracji: zapisany na biało, stawiany na żółto.
                scale?.let {
                    val a = it.a.toOffset(size.width, size.height)
                    val b = it.b.toOffset(size.width, size.height)
                    drawLine(Color.White, a, b, strokeWidth = 3f)
                    drawCircle(Color.White, radius = 6f, center = a)
                    drawCircle(Color.White, radius = 6f, center = b)
                }
                scalePoints.forEach {
                    drawCircle(
                        color = SCALE_COLOR,
                        radius = 7f,
                        center = it.toOffset(size.width, size.height),
                    )
                }
            }
        }
    }
}

private fun PlanPoint.toOffset(width: Float, height: Float) =
    Offset(x.toFloat() * width, y.toFloat() * height)

/** Wielobok z wycięciami — `EvenOdd` sprawia, że dziury naprawdę są dziurami. */
private fun DrawScope.drawRing(
    points: List<PlanPoint>,
    holes: List<List<PlanPoint>>,
    color: Color,
    fillAlpha: Float,
    strokeWidth: Float,
) {
    if (points.size < 3) return
    val path = Path().apply {
        fillType = PathFillType.EvenOdd
        moveTo(points[0].x.toFloat() * size.width, points[0].y.toFloat() * size.height)
        for (i in 1 until points.size) {
            lineTo(points[i].x.toFloat() * size.width, points[i].y.toFloat() * size.height)
        }
        close()
        holes.filter { it.size >= 3 }.forEach { hole ->
            moveTo(hole[0].x.toFloat() * size.width, hole[0].y.toFloat() * size.height)
            for (i in 1 until hole.size) {
                lineTo(hole[i].x.toFloat() * size.width, hole[i].y.toFloat() * size.height)
            }
            close()
        }
    }
    drawPath(path, color.copy(alpha = fillAlpha))
    drawPath(path, color, style = Stroke(width = strokeWidth))
}

// ── Narzędzia pod rzutem ─────────────────────────────────────────────────────

@Composable
private fun ScaleTools(scale: PlanScale?, points: List<PlanPoint>, onClear: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = when {
                scaleReady(scale) -> "Skala ustawiona: ${scale!!.cm.pl2()} cm. " +
                    "Dotknij dwóch punktów, aby skalibrować od nowa."
                points.size == 1 -> "Dotknij drugiego końca odcinka."
                else -> "Dotknij dwóch końców odcinka o znanej długości " +
                    "(np. ściany z wymiarem na rzucie)."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (scaleReady(scale)) {
            TextButton(onClick = onClear) { Text("Wyczyść skalę") }
        }
    }
}

@Composable
private fun AreaTools(
    ready: Boolean,
    pen: AreaCat,
    onPen: (AreaCat) -> Unit,
    outlineSize: Int,
    onUndo: () -> Unit,
    onClose: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!ready) {
            Text(
                text = "Obrysy wymagają skali — zacznij od zakładki „Skala”.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        // Dwie kategorie, tak jak `PREP_CATS` w panelu: rozstawu na tym etapie
        // nikt nie zna, więc obrys wpada jako „do ustalenia".
        PillChoiceRow(
            options = PREP_CATS,
            selected = pen,
            optionLabel = { it.label },
            onSelect = onPen,
            enabled = ready,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = onUndo, enabled = outlineSize > 0) { Text("Cofnij punkt") }
            TextButton(onClick = onClose, enabled = outlineSize >= 3) { Text("Zamknij obrys") }
        }
    }
}

@Composable
private fun RoomList(
    rooms: List<RoomShape>,
    scale: PlanScale?,
    selectedId: String?,
    onSelect: (RoomShape) -> Unit,
    onRename: (RoomShape) -> Unit,
    onDelete: (RoomShape) -> Unit,
) {
    if (rooms.isEmpty()) {
        Text(
            text = "Brak obrysów. Dotknij rzutu, aby postawić wierzchołki, " +
                "potem „Zamknij obrys”.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    rooms.forEachIndexed { index, room ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelect(room) }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = if (room.id == selectedId) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                        shape = CircleShape,
                    ),
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = roomLabel(room, index),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(
                        roomM2(room, scale)?.let { "${it.pl2()} m²" },
                        room.cat.short,
                        "wycięcia: ${room.holes.size}".takeIf { room.holes.isNotEmpty() },
                        "łatki: ${room.patches.size}".takeIf { room.patches.isNotEmpty() },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { onRename(room) }, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Zmień nazwę i kategorię",
                    modifier = Modifier.size(16.dp),
                )
            }
            IconButton(onClick = { onDelete(room) }, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Usuń obrys",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

// ── Okna ─────────────────────────────────────────────────────────────────────

@Composable
private fun LengthDialog(onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var text by remember { mutableStateOf("") }
    val value = text.toDecimalOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Długość odcinka") },
        text = {
            Column {
                Text(
                    text = "Ile centymetrów ma w rzeczywistości zaznaczony odcinek?",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.isNumericInput()) text = it },
                    label = { Text("centymetry") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { value?.let(onConfirm) },
                enabled = value != null && value > 0,
            ) { Text("Ustaw skalę") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

@Composable
private fun RenameDialog(
    room: RoomShape,
    onDismiss: () -> Unit,
    onConfirm: (String, AreaCat) -> Unit,
) {
    var name by remember(room.id) { mutableStateOf(room.name) }
    var cat by remember(room.id) { mutableStateOf(room.cat) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pomieszczenie") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nazwa (np. salon)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                PillChoiceRow(
                    options = PREP_CATS,
                    selected = cat,
                    optionLabel = { it.label },
                    onSelect = { cat = it },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name.trim(), cat) }) { Text("Zapisz") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

// ── Stałe ────────────────────────────────────────────────────────────────────

/**
 * Kategorie przygotowania — odpowiednik `PREP_CATS` panelu. Rozstaw dopisuje
 * audytor, więc obrys wychodzi stąd jako „do ustalenia" albo „bez OP".
 */
private val PREP_CATS = listOf(AreaCat.TODO_CAT, AreaCat.NONE)

/** Zapory z panelu: `ROOMS_MAX` i `OUTLINE_MAX`. */
private const val ROOMS_LIMIT = 40
private const val OUTLINE_MAX = 60

private const val EDITOR_PX = 1800
private const val MAX_PLAN_ZOOM = 8f

private val NO_UFH_COLOR = Color(0xFF9E9E9E)
private val PATCH_COLOR = Color(0xFF42A5F5)
private val SCALE_COLOR = Color(0xFFFFC107)

/** Metraż po polsku — przecinek dziesiętny, dwie cyfry po przecinku. */
private fun Double.pl2(): String = String.format(Locale("pl"), "%.2f", this)
