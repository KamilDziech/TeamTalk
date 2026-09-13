package com.ekotak.teamtalk.presentation.crm

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.ekotak.teamtalk.domain.model.FloorPlanDoc
import com.ekotak.teamtalk.domain.ufh.AreaCat
import com.ekotak.teamtalk.domain.ufh.FloorPlanPatch
import com.ekotak.teamtalk.domain.ufh.FloorPlanState
import com.ekotak.teamtalk.domain.ufh.HeatSourceMark
import com.ekotak.teamtalk.domain.ufh.MEASURE_CATS
import com.ekotak.teamtalk.domain.ufh.ManifoldMark
import com.ekotak.teamtalk.domain.ufh.PlanHistoryEntry
import com.ekotak.teamtalk.domain.ufh.PlanHistoryKind
import com.ekotak.teamtalk.domain.ufh.PlanPoint
import com.ekotak.teamtalk.domain.ufh.PlanScale
import com.ekotak.teamtalk.domain.ufh.ROOMS_MAX
import com.ekotak.teamtalk.domain.ufh.ROOM_M2_MAX
import com.ekotak.teamtalk.domain.ufh.RoomShape
import com.ekotak.teamtalk.domain.ufh.UFH_BOX_COLORS
import com.ekotak.teamtalk.domain.ufh.UFH_HEAT_SOURCE_DEFAULT_KIND
import com.ekotak.teamtalk.domain.ufh.UFH_HEAT_SOURCE_KINDS
import com.ekotak.teamtalk.domain.ufh.addPatch
import com.ekotak.teamtalk.domain.ufh.authorLabel
import com.ekotak.teamtalk.domain.ufh.autoManifolds
import com.ekotak.teamtalk.domain.ufh.boxColor
import com.ekotak.teamtalk.domain.ufh.catSums
import com.ekotak.teamtalk.domain.ufh.clearAssignments
import com.ekotak.teamtalk.domain.ufh.cmPerUnit
import com.ekotak.teamtalk.domain.ufh.color
import com.ekotak.teamtalk.domain.ufh.commonBoxType
import com.ekotak.teamtalk.domain.ufh.convertRoomToPatch
import com.ekotak.teamtalk.domain.ufh.emptyRoom
import com.ekotak.teamtalk.domain.ufh.entrySummary
import com.ekotak.teamtalk.domain.ufh.entryTitle
import com.ekotak.teamtalk.domain.ufh.findPatchTarget
import com.ekotak.teamtalk.domain.ufh.fmtMb
import com.ekotak.teamtalk.domain.ufh.fmtWhen
import com.ekotak.teamtalk.domain.ufh.freeLoopsElsewhere
import com.ekotak.teamtalk.domain.ufh.largestIntersection
import com.ekotak.teamtalk.domain.ufh.loopLoad
import com.ekotak.teamtalk.domain.ufh.loopsLabel
import com.ekotak.teamtalk.domain.ufh.manifoldLabel
import com.ekotak.teamtalk.domain.ufh.manifoldOverMessage
import com.ekotak.teamtalk.domain.ufh.manifoldShort
import com.ekotak.teamtalk.domain.ufh.manualAssignedCount
import com.ekotak.teamtalk.domain.ufh.markBoxType
import com.ekotak.teamtalk.domain.ufh.markIndexById
import com.ekotak.teamtalk.domain.ufh.newMarkId
import com.ekotak.teamtalk.domain.ufh.patchLabel
import com.ekotak.teamtalk.domain.ufh.patchM2
import com.ekotak.teamtalk.domain.ufh.patchParentOf
import com.ekotak.teamtalk.domain.ufh.patchProblem
import com.ekotak.teamtalk.domain.ufh.patchTitle
import com.ekotak.teamtalk.domain.ufh.planZones
import com.ekotak.teamtalk.domain.ufh.floorLoopMessage
import com.ekotak.teamtalk.domain.ufh.pointInPoly
import com.ekotak.teamtalk.domain.ufh.restoreMarks
import com.ekotak.teamtalk.domain.ufh.roomLabel
import com.ekotak.teamtalk.domain.ufh.roomM2
import com.ekotak.teamtalk.domain.ufh.sameMarks
import com.ekotak.teamtalk.domain.ufh.sameRooms
import com.ekotak.teamtalk.domain.ufh.sameScale
import com.ekotak.teamtalk.domain.ufh.sameSource
import com.ekotak.teamtalk.domain.ufh.saveAreaPatch
import com.ekotak.teamtalk.domain.ufh.saveMarksPatch
import com.ekotak.teamtalk.domain.ufh.saveSourcePatch
import com.ekotak.teamtalk.domain.ufh.saveTypesPatch
import com.ekotak.teamtalk.domain.ufh.scaleReady
import com.ekotak.teamtalk.domain.ufh.splitByManifold
import com.ekotak.teamtalk.domain.ufh.ufhLoopMaxM
import com.ekotak.teamtalk.domain.ufh.ufhManifoldMax
import com.ekotak.teamtalk.domain.ufh.ufhPipeMm
import com.ekotak.teamtalk.domain.ufh.zonesPipeTotal
import com.ekotak.teamtalk.domain.ufh.zonesRatioMessage
import com.ekotak.teamtalk.domain.ufh.zonesTotal
import com.ekotak.teamtalk.domain.model.ufhMinSpacingCm
import java.util.Locale
import kotlin.math.hypot

/**
 * EDYTOR RZUTU KONDYGNACJI w audycie OP — mobilny odpowiednik `UfhPlanLightbox`
 * + `UfhRoomsPanel` z panelu, z czterema narzędziami na jednym obrazie:
 * rozdzielacz, źródło ciepła, skala i pomieszczenia, plus historia rzutu.
 *
 * Rachunek jest wspólny z panelem (`domain/ufh`): przydział rozdzielaczy liczony
 * w locie, strefy, łatki docinane do obrysu, zapis z historią. Na 360 dp różni
 * się tylko obsługa:
 *  • dwuklik domykający wielobok zastępuje dotknięcie PIERWSZEGO punktu obrysu
 *    (albo „Domknij"),
 *  • przeciąganie wierzchołków działa przy wyłączonym piórze — z piórem palec
 *    przy ścianie sąsiedniego pomieszczenia ma stawiać punkt, a nie łapać róg,
 *  • panel boczny ląduje pod rzutem, a zapis bieżącego narzędzia na belce.
 *
 * Stan jest ROBOCZY do „Zapisz…" — tak jak w panelu zapis idzie od razu do
 * audytu (`onCommit`) i odkłada poprzedni stan w historii.
 */
@Composable
fun UfhPlanEditor(
    floorLabel: String,
    plan: FloorPlanDoc,
    current: FloorPlanState,
    /** Slot do utrwalenia przy zapisie (ręczny wybór albo dopasowany automatycznie). */
    planSlot: String,
    boxType: String,
    pipeSystem: String,
    maxMarks: Int,
    sourceElsewhere: String,
    initialTool: PlanTool,
    enabled: Boolean,
    saving: Boolean,
    onCommit: (FloorPlanPatch) -> Unit,
    onClose: () -> Unit,
) {
    val image = rememberFloorPlanImage(plan)
    val planDocId = plan.docId
    val history = current.history

    var tool by remember { mutableStateOf(initialTool) }
    var viewIdx by remember { mutableStateOf<Int?>(null) }
    var note by remember { mutableStateOf("") }

    var draftMarks by remember { mutableStateOf(current.marks) }
    var editMarks by remember { mutableStateOf(false) }
    var selMark by remember { mutableStateOf<Int?>(null) }
    var draftSource by remember { mutableStateOf(current.heatSource) }
    var draftRooms by remember { mutableStateOf(current.rooms) }
    var draftScale by remember { mutableStateOf(current.scale) }
    var selRoom by remember { mutableStateOf<String?>(null) }
    var pen by remember { mutableStateOf<AreaCat?>(null) }
    var penManifold by remember { mutableStateOf<String?>(null) }
    var outline by remember { mutableStateOf<List<PlanPoint>>(emptyList()) }
    var holeFor by remember { mutableStateOf<String?>(null) }
    var askPatch by remember { mutableStateOf<AskPatch?>(null) }
    var scalePts by remember { mutableStateOf<List<PlanPoint>>(emptyList()) }
    var askCm by remember { mutableStateOf(false) }
    var confirmClose by remember { mutableStateOf(false) }

    // Zapis/przeładowanie audytu → porzucamy robocze zmiany (jak `useEffect` panelu).
    LaunchedEffect(current.marks) {
        draftMarks = current.marks
        selMark = null
    }
    LaunchedEffect(current.rooms) {
        draftRooms = current.rooms
        selRoom = null
    }
    LaunchedEffect(current.scale) { draftScale = current.scale }
    LaunchedEffect(current.heatSource) { draftSource = current.heatSource }

    val bitmap = image.bitmap
    val aspect = bitmap?.let { it.width.toDouble() / it.height.toDouble() } ?: (draftScale?.aspect ?: 0.0)
    val viewing: PlanHistoryEntry? = viewIdx?.let { history.getOrNull(it) }
    val shownMarks = when {
        viewing == null -> draftMarks
        viewing.kind == PlanHistoryKind.MANIFOLD -> viewing.marks
        else -> current.marks
    }
    val shownRooms = when {
        viewing == null -> draftRooms
        viewing.kind == PlanHistoryKind.AREA -> viewing.rooms
        else -> current.rooms
    }
    val shownScale = if (viewing != null && viewing.kind == PlanHistoryKind.AREA) viewing.scale else draftScale
    val shownSource = when {
        viewing == null -> draftSource
        viewing.kind == PlanHistoryKind.SOURCE -> viewing.source
        else -> current.heatSource
    }
    val shownBoxType = viewing?.boxType ?: boxType

    val loopMaxM = ufhLoopMaxM(pipeSystem)
    val manifoldMax = ufhManifoldMax(pipeSystem)
    val autoPlan = remember(shownRooms, shownMarks, shownScale, aspect, loopMaxM, manifoldMax) {
        autoManifolds(shownRooms, shownMarks, shownScale, aspect, loopMaxM, manifoldMax)
    }
    val zones = remember(autoPlan, shownMarks, shownScale, loopMaxM) {
        planZones(autoPlan.rooms, shownMarks, shownScale, loopMaxM)
    }

    val readOnly = viewing != null
    val canEditMarks = enabled && maxMarks >= 1 && !readOnly
    val measureReady = scaleReady(draftScale) && aspect > 0
    val canDraw = enabled && !readOnly && tool == PlanTool.AREA && measureReady && pen != null
    val marksDirty = !sameMarks(current.marks, draftMarks)
    val sourceDirty = !sameSource(current.heatSource, draftSource)
    val areaDirty = !sameRooms(current.rooms, draftRooms) || !sameScale(current.scale, draftScale)
    val anyDirty = marksDirty || sourceDirty || areaDirty

    fun resetDrawing() {
        outline = emptyList()
        holeFor = null
        askPatch = null
    }

    fun commitRoom(pts: List<PlanPoint>) {
        if (draftRooms.size >= ROOMS_MAX) {
            note = "Limit $ROOMS_MAX pomieszczeń na kondygnację."
            return
        }
        // Przypisanie do rozdzielacza ma sens tylko przy kilku na kondygnacji.
        val mfId = if (draftMarks.size > 1) penManifold.orEmpty() else ""
        val room = emptyRoom(pen ?: AreaCat.NONE, pts, mfId)
        val mfIdx = markIndexById(draftMarks, mfId)
        val m2 = roomM2(room, draftScale)
        note = when {
            m2 != null && m2 > ROOM_M2_MAX -> "Wyszło ${pl2(m2)} m² — sprawdź kalibrację skali."
            m2 == null -> ""
            else -> "Dodano ${pl2(m2)} m² (${room.cat.short}${if (mfIdx >= 0) ", ${manifoldShort(mfIdx)}" else ""})."
        }
        draftRooms = draftRooms + room
        selRoom = room.id
        outline = emptyList()
        askPatch = null
    }

    fun commitPatch(roomId: String, cat: AreaCat, pts: List<PlanPoint>) {
        val room = draftRooms.firstOrNull { it.id == roomId } ?: return
        patchProblem(room, cat, pts)?.let {
            note = it
            askPatch = null
            return
        }
        val next = addPatch(draftRooms, roomId, cat, pts)
        val added = next.firstOrNull { it.id == roomId }?.patches?.lastOrNull()
        val m2 = added?.let { patchM2(it, draftScale) }
        val idx = draftRooms.indexOfFirst { it.id == roomId }.coerceAtLeast(0)
        draftRooms = next
        selRoom = roomId
        outline = emptyList()
        askPatch = null
        note = "Zagęszczenie ${cat.short}${m2?.let { " na ${pl2(it)} m²" }.orEmpty()} " +
            "w „${roomLabel(room, idx)}” — ta sama pętla, gęstszy rozstaw w tym miejscu."
    }

    fun closeShape() {
        if (!canDraw) return
        val pts = outline
        if (pts.size < 3) {
            note = "Wielobok potrzebuje co najmniej 3 punktów."
            return
        }
        holeFor?.let { hid ->
            val room = draftRooms.firstOrNull { it.id == hid }
            if (room == null) {
                holeFor = null
                return
            }
            if (!pointInPoly(pts[0], room.outline)) {
                note = "Wycięcie musi leżeć wewnątrz obrysu pomieszczenia."
                return
            }
            draftRooms = draftRooms.map { if (it.id == hid) it.copy(holes = it.holes + listOf(pts)) else it }
            outline = emptyList()
            holeFor = null
            note = "Wycięcie odjęte od powierzchni."
            return
        }
        // Obrys narysowany NA pomieszczeniu = zagęszczenie tej samej pętli.
        val target = findPatchTarget(pts, draftRooms)
        if (target != null) {
            val cat = pen ?: AreaCat.NONE
            if (target.inside) {
                commitPatch(target.room.id, cat, target.clipped)
            } else {
                val idx = draftRooms.indexOfFirst { it.id == target.room.id }.coerceAtLeast(0)
                askPatch = AskPatch(target.room.id, roomLabel(target.room, idx), cat, pts, target.clipped)
                note = ""
            }
            return
        }
        commitRoom(pts)
    }

    fun onTap(p: PlanPoint) {
        if (!enabled || readOnly) return
        when (tool) {
            PlanTool.MANIFOLD -> {
                if (!editMarks || !canEditMarks) return
                // Przy komplecie kropek dotknięcie PRZESTAWIA ostatnią (id zostaje).
                draftMarks = if (draftMarks.size < maxMarks) {
                    draftMarks + ManifoldMark(p.x, p.y, newMarkId())
                } else {
                    draftMarks.take(maxMarks - 1) +
                        ManifoldMark(p.x, p.y, draftMarks.getOrNull(maxMarks - 1)?.id ?: newMarkId())
                }
            }

            PlanTool.SOURCE -> {
                draftSource = HeatSourceMark(p.x, p.y, draftSource?.kind?.ifEmpty { null } ?: UFH_HEAT_SOURCE_DEFAULT_KIND)
            }

            PlanTool.SCALE -> {
                note = ""
                scalePts = if (scalePts.size >= 2) listOf(p) else scalePts + p
                if (scalePts.size == 2) askCm = true
            }

            PlanTool.AREA -> {
                if (canDraw) {
                    val first = outline.firstOrNull()
                    if (first != null && outline.size >= 3 && hypot(first.x - p.x, (first.y - p.y) / aspect.coerceAtLeast(0.1)) < CLOSE_RADIUS) {
                        closeShape()
                    } else if (outline.size < OUTLINE_MAX) {
                        outline = outline + p
                    }
                } else if (pen == null) {
                    val hit = draftRooms.lastOrNull { pointInPoly(p, it.outline) }
                    selRoom = if (hit == null || hit.id == selRoom) null else hit.id
                } else if (!measureReady) {
                    note = "Brak skali — najpierw skalibruj rzut w trybie „Skala”."
                }
            }

            PlanTool.HISTORY -> Unit
        }
    }

    fun onDrag(h: PlanHandle, p: PlanPoint) {
        when (h) {
            is PlanHandle.Mark -> if (editMarks && canEditMarks) {
                draftMarks = draftMarks.mapIndexed { i, m -> if (i == h.index) m.copy(x = p.x, y = p.y) else m }
            }

            PlanHandle.Source -> draftSource = draftSource?.copy(x = p.x, y = p.y)
            is PlanHandle.Vertex -> draftRooms = draftRooms.map { room ->
                if (room.id != h.roomId) return@map room
                val patchIdx = ringToPatch(h.ring)
                when {
                    patchIdx != null -> room.copy(
                        patches = room.patches.mapIndexed { pi, patch ->
                            if (pi == patchIdx) {
                                patch.copy(outline = patch.outline.mapIndexed { i, q -> if (i == h.index) p else q })
                            } else {
                                patch
                            }
                        },
                    )

                    h.ring < 0 -> room.copy(outline = room.outline.mapIndexed { i, q -> if (i == h.index) p else q })
                    else -> room.copy(
                        holes = room.holes.mapIndexed { hi, hole ->
                            if (hi == h.ring) hole.mapIndexed { i, q -> if (i == h.index) p else q } else hole
                        },
                    )
                }
            }
        }
    }

    fun onDragEnd(h: PlanHandle) {
        // Wierzchołek łatki wyciągnięty poza pomieszczenie → wracamy do części wspólnej.
        val v = h as? PlanHandle.Vertex ?: return
        val patchIdx = ringToPatch(v.ring) ?: return
        draftRooms = draftRooms.map { room ->
            if (room.id != v.roomId) return@map room
            val patch = room.patches.getOrNull(patchIdx) ?: return@map room
            val clipped = largestIntersection(patch.outline, room.outline)
            if (clipped == null || clipped.size < 3) room
            else room.copy(patches = room.patches.mapIndexed { i, q -> if (i == patchIdx) q.copy(outline = clipped) else q })
        }
    }

    fun restore(entry: PlanHistoryEntry) {
        when (entry.kind) {
            PlanHistoryKind.SOURCE -> {
                draftSource = entry.source
                tool = PlanTool.SOURCE
            }

            PlanHistoryKind.MANIFOLD -> {
                draftMarks = restoreMarks(entry, current.marks, maxMarks)
                tool = PlanTool.MANIFOLD
                editMarks = true
            }

            PlanHistoryKind.AREA -> {
                draftRooms = entry.rooms
                entry.scale?.let { draftScale = it }
                tool = PlanTool.AREA
            }
        }
        viewIdx = null
        note = "Przywrócono — sprawdź i zapisz."
    }

    val layer = PlanLayer(
        rooms = autoPlan.rooms,
        scale = shownScale,
        marks = shownMarks,
        markColors = shownMarks.map { Color(boxColor(markBoxType(it, shownBoxType))) },
        heatSource = shownSource?.point,
        zones = autoPlan.rooms.mapNotNull { room ->
            val z = zones[room.id] ?: return@mapNotNull null
            val act = room.id == selRoom
            room.id to ZoneOverlay(
                cuts = z.cuts.map { it.a to it.b },
                labels = z.zones.map { zone ->
                    zone.at to if (act) "S${zone.n} · ${pl2(zone.m2)} m² · ${zone.pipe.toInt()} mb" else "S${zone.n}"
                },
            )
        }.toMap(),
        roomManifold = if (shownMarks.size > 1) {
            autoPlan.rooms.mapNotNull { r ->
                markIndexById(shownMarks, r.manifoldId).takeIf { it >= 0 }?.let { r.id to it }
            }.toMap()
        } else {
            emptyMap()
        },
    )

    val primarySave: Pair<String, () -> Unit>? = when {
        readOnly || !enabled -> null
        tool == PlanTool.MANIFOLD && editMarks -> "Zapisz rozmieszczenie" to {
            onCommit(saveMarksPatch(current, draftMarks, boxType, planDocId, planSlot))
            editMarks = false
            onClose()
        }

        tool == PlanTool.MANIFOLD && marksDirty -> "Zapisz typ" to { onCommit(saveTypesPatch(draftMarks)) }
        tool == PlanTool.SOURCE && sourceDirty -> "Zapisz źródło" to {
            onCommit(saveSourcePatch(current, draftSource, planDocId, planSlot))
            onClose()
        }

        (tool == PlanTool.AREA || tool == PlanTool.SCALE) && areaDirty -> "Zapisz pomiar" to {
            onCommit(saveAreaPatch(current, draftRooms, draftScale, planDocId, planSlot))
            resetDrawing()
            onClose()
        }

        else -> null
    }

    Dialog(
        onDismissRequest = { if (anyDirty) confirmClose = true else onClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            // ── Belka ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { if (anyDirty) confirmClose = true else onClose() }) {
                    Icon(Icons.Default.Close, contentDescription = "Zamknij")
                }
                Column(Modifier.weight(1f)) {
                    Text(floorLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        text = plan.fileName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                primarySave?.let { (label, action) ->
                    Button(onClick = action, enabled = !saving) {
                        if (saving) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Text(label)
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                PlanTool.entries.forEach { t ->
                    ChoicePill(label = t.label, selected = tool == t) {
                        tool = t
                        note = ""
                        if (t != PlanTool.AREA) resetDrawing()
                        if (t != PlanTool.MANIFOLD) editMarks = false
                        if (t != PlanTool.HISTORY) viewIdx = null
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // ── Rzut ─────────────────────────────────────────────────────────
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f).background(Color(0xFF101010)),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    bitmap != null && aspect > 0 -> UfhPlanCanvas(
                        image = bitmap,
                        aspect = aspect.toFloat(),
                        layer = layer,
                        catColor = { Color(it.color) },
                        modifier = Modifier.fillMaxSize(),
                        ghost = readOnly,
                        selectedRoomId = if (tool == PlanTool.AREA) selRoom else null,
                        selectedMark = if (tool == PlanTool.MANIFOLD) selMark else null,
                        draft = if (canDraw && (outline.isNotEmpty() || holeFor != null)) {
                            PlanDraft(outline, pen ?: AreaCat.NONE, holeFor != null)
                        } else {
                            null
                        },
                        scaleDraft = if (tool == PlanTool.SCALE) scalePts else emptyList(),
                        showScale = tool == PlanTool.SCALE,
                        dragMarks = tool == PlanTool.MANIFOLD && enabled,
                        dragSource = tool == PlanTool.SOURCE && enabled,
                        dragVertices = tool == PlanTool.AREA && enabled && pen == null,
                        onTap = ::onTap,
                        onHandleTap = { h ->
                            if (h is PlanHandle.Mark && tool == PlanTool.MANIFOLD) {
                                selMark = if (selMark == h.index) null else h.index
                            }
                        },
                        onHandleDrag = ::onDrag,
                        onHandleDragEnd = ::onDragEnd,
                    )

                    image.loading -> CircularProgressIndicator(color = Color.White)
                    else -> Text(
                        text = "Rzutu nie ma na telefonie — otwórz go raz w zasięgu (zakładka „Pliki”).",
                        color = Color.White,
                        modifier = Modifier.padding(24.dp),
                    )
                }
            }

            // ── Podpowiedź trybu ─────────────────────────────────────────────
            Text(
                text = listOf(
                    modeInfo(
                        tool, viewing, maxMarks, editMarks, draftMarks.size, draftSource, sourceElsewhere,
                        scalePts.size, measureReady, pen, holeFor != null, penManifoldInfo(draftMarks, penManifold),
                    ),
                    note,
                ).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // ── Narzędzia ────────────────────────────────────────────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (tool) {
                    PlanTool.MANIFOLD -> ManifoldTools(
                        editMarks = editMarks,
                        canEdit = canEditMarks,
                        count = draftMarks.size,
                        maxMarks = maxMarks,
                        selMark = selMark,
                        active = if (selMark != null) {
                            draftMarks.getOrNull(selMark!!)?.let { markBoxType(it, boxType) } ?: boxType
                        } else {
                            commonBoxType(shownMarks, shownBoxType)
                        },
                        onEdit = { editMarks = true },
                        onDeleteSelected = {
                            selMark?.let { s -> draftMarks = draftMarks.filterIndexed { i, _ -> i != s } }
                            selMark = null
                        },
                        onUndoLast = {
                            draftMarks = draftMarks.dropLast(1)
                            selMark = null
                        },
                        onFinish = {
                            draftMarks = current.marks
                            editMarks = false
                            selMark = null
                        },
                        onPickType = { type ->
                            val s = selMark ?: return@ManifoldTools
                            if (!enabled || readOnly) return@ManifoldTools
                            draftMarks = draftMarks.mapIndexed { i, m -> if (i == s) m.copy(boxType = type) else m }
                        },
                    )

                    PlanTool.SOURCE -> SourceTools(
                        source = draftSource,
                        enabled = enabled && !readOnly,
                        sourceElsewhere = sourceElsewhere,
                        onKind = { k -> draftSource = draftSource?.copy(kind = k) },
                        onDelete = { draftSource = null },
                    )

                    PlanTool.SCALE -> ScaleInfo(
                        scale = draftScale,
                        aspect = aspect,
                        planDocId = planDocId,
                        points = scalePts.size,
                        onClear = { scalePts = emptyList() },
                    )

                    PlanTool.AREA -> AreaTools(
                        ready = measureReady,
                        enabled = enabled && !readOnly,
                        pipeSystem = pipeSystem,
                        rooms = autoPlan.rooms,
                        draftRooms = draftRooms,
                        scale = draftScale,
                        marks = draftMarks,
                        boxType = boxType,
                        autoIds = autoPlan.auto,
                        moved = autoPlan.moved,
                        overloadedAuto = autoPlan.overloaded,
                        zones = zones,
                        pen = pen,
                        penManifold = penManifold,
                        selectedId = selRoom,
                        outlineSize = outline.size,
                        drawingHole = holeFor != null,
                        onCalibrate = {
                            tool = PlanTool.SCALE
                            scalePts = emptyList()
                            note = ""
                        },
                        onPickCat = { cat ->
                            pen = if (pen == cat) null else cat
                            resetDrawing()
                        },
                        onPickManifold = { id ->
                            selRoom?.let { rid ->
                                draftRooms = draftRooms.map { if (it.id == rid) it.copy(manifoldId = id.orEmpty()) else it }
                            }
                            penManifold = if (penManifold == id) null else id
                        },
                        onClearAssign = {
                            draftRooms = clearAssignments(draftRooms)
                            note = "Ręczne przypisania skasowane — przydział policzył się od nowa wg najbliższych rozdzielaczy."
                        },
                        onSelectRoom = { id -> selRoom = if (selRoom == id) null else id },
                        onPatchRoom = { id, edit -> draftRooms = draftRooms.map { if (it.id == id) edit(it) else it } },
                        onDeleteRoom = { id ->
                            draftRooms = draftRooms.filterNot { it.id == id }
                            selRoom = null
                        },
                        onDeletePatch = { rid, pid ->
                            draftRooms = draftRooms.map { r ->
                                if (r.id == rid) r.copy(patches = r.patches.filterNot { it.id == pid }) else r
                            }
                            note = "Zagęszczenie usunięte — pole wróciło do rozstawu pomieszczenia."
                        },
                        onConvertToPatch = { id ->
                            val res = convertRoomToPatch(draftRooms, id)
                            val nextRooms = res.rooms
                            val parent = res.parent
                            if (nextRooms == null || parent == null) {
                                note = res.problem ?: "Nie udało się zamienić obrysu na zagęszczenie."
                            } else {
                                val idx = nextRooms.indexOfFirst { it.id == parent.id }
                                val added = nextRooms.getOrNull(idx)?.patches?.lastOrNull()
                                val m2 = added?.let { patchM2(it, draftScale) }
                                draftRooms = nextRooms
                                selRoom = parent.id
                                note = "Obrys wszedł jako ${patchLabel(idx, (nextRooms[idx].patches.size) - 1)} — " +
                                    "zagęszczenie w „${roomLabel(parent, idx)}”${m2?.let { " na ${pl2(it)} m²" }.orEmpty()}." +
                                    if (res.clipped == true) " Obrys docięto do pomieszczenia." else ""
                            }
                        },
                        onStartHole = { id ->
                            holeFor = id
                            pen = draftRooms.firstOrNull { it.id == id }?.cat ?: AreaCat.NONE
                            outline = emptyList()
                            note = "Dotykaj punktów wycięcia wewnątrz obrysu; pierwszy punkt domyka."
                        },
                        onUndoHole = { id ->
                            draftRooms = draftRooms.map { if (it.id == id) it.copy(holes = it.holes.dropLast(1)) else it }
                        },
                        onClose = ::closeShape,
                        onUndoPoint = { outline = outline.dropLast(1) },
                        onDiscard = { resetDrawing() },
                    )

                    PlanTool.HISTORY -> HistoryTools(
                        history = history,
                        viewIdx = viewIdx,
                        planDocId = planDocId,
                        currentSummary = "${draftMarks.size} × rozdzielacz · ${draftRooms.size} pom.",
                        canRestore = enabled,
                        onView = { viewIdx = it },
                        onRestore = { viewing?.let(::restore) },
                    )
                }
            }
        }
    }

    // ── Okna pomocnicze ──────────────────────────────────────────────────────

    if (askCm) {
        ScaleLengthDialog(
            onDismiss = {
                askCm = false
                scalePts = emptyList()
            },
            onConfirm = { cm ->
                askCm = false
                if (aspect <= 0 || scalePts.size != 2) {
                    note = "Rzut jeszcze się wczytuje — spróbuj po chwili."
                } else {
                    draftScale = PlanScale(a = scalePts[0], b = scalePts[1], cm = cm, aspect = aspect, planDocId = planDocId)
                    scalePts = emptyList()
                    tool = PlanTool.AREA
                    note = "Skala ustawiona — wybierz kategorię i obwódź pomieszczenia."
                }
            },
        )
    }

    askPatch?.let { q ->
        AlertDialog(
            onDismissRequest = { askPatch = null },
            title = { Text("Obrys wystaje poza pomieszczenie") },
            text = {
                Text("Obrys wystaje poza „${q.roomLabel}” — zagęszczenie tego pomieszczenia (${q.cat.short}) czy osobne pomieszczenie?")
            },
            confirmButton = {
                Column {
                    TextButton(onClick = { commitPatch(q.roomId, q.cat, q.clipped) }) { Text("Zagęszczenie (dotnij)") }
                    TextButton(onClick = { commitRoom(q.outline) }) { Text("Osobne pomieszczenie") }
                    TextButton(onClick = {
                        askPatch = null
                        outline = emptyList()
                    }) { Text("Porzuć obrys") }
                }
            },
        )
    }

    if (confirmClose) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            title = { Text("Porzucić zmiany?") },
            text = { Text("Niezapisane zmiany na rzucie przepadną.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClose = false
                    onClose()
                }) { Text("Porzuć") }
            },
            dismissButton = { TextButton(onClick = { confirmClose = false }) { Text("Wróć do rzutu") } },
        )
    }
}

/** Narzędzia okna — kolejność jak przełącznik trybów w panelu, z historią na końcu. */
enum class PlanTool(val label: String) {
    MANIFOLD("Rozdzielacz"),
    SOURCE("Źródło"),
    SCALE("Skala"),
    AREA("Pomieszczenia"),
    HISTORY("Historia"),
}

private data class AskPatch(
    val roomId: String,
    val roomLabel: String,
    val cat: AreaCat,
    val outline: List<PlanPoint>,
    val clipped: List<PlanPoint>,
)

private fun penManifoldInfo(marks: List<ManifoldMark>, penManifold: String?): String {
    if (marks.size <= 1) return ""
    val idx = markIndexById(marks, penManifold)
    return if (idx >= 0) " → ${manifoldShort(idx)}" else " → automat"
}

private fun modeInfo(
    tool: PlanTool,
    viewing: PlanHistoryEntry?,
    maxMarks: Int,
    editMarks: Boolean,
    marks: Int,
    source: HeatSourceMark?,
    sourceElsewhere: String,
    scalePts: Int,
    measureReady: Boolean,
    pen: AreaCat?,
    hole: Boolean,
    penManifold: String,
): String = when {
    viewing != null -> "podgląd historii z ${fmtWhen(viewing.at)} (${entryTitle(viewing)}) — edycja zablokowana"
    tool == PlanTool.MANIFOLD -> when {
        maxMarks < 1 -> "ilość rozdzielaczy = 0 (brak) — zmień pole, aby zaznaczać"
        editMarks -> "kropki: $marks / $maxMarks · dotknij rzutu = dodaj/przestaw, przeciągnij kropkę = przesuń"
        else -> "dotknij kropki, aby ją zaznaczyć; przesuwanie po „Edycja”"
    }

    tool == PlanTool.SOURCE -> if (source != null) {
        "źródło ciepła: ${source.kind.ifEmpty { "rodzaj nieokreślony" }} · dotknij rzutu = przestaw"
    } else {
        "dotknij rzutu, aby postawić źródło ciepła" +
            if (sourceElsewhere.isNotEmpty()) " (teraz stoi na: $sourceElsewhere — postawienie tu je stamtąd zabierze)" else ""
    }

    tool == PlanTool.SCALE -> when (scalePts) {
        0 -> "dotknij pierwszego końca odcinka o znanej długości"
        1 -> "dotknij drugiego końca odcinka"
        else -> "podaj długość odcinka w cm"
    }

    tool == PlanTool.AREA -> when {
        !measureReady -> "brak skali — najpierw skalibruj rzut w trybie „Skala”"
        pen == null -> "wybierz kategorię (to ona zbiera metry), potem obwódź pomieszczenia · bez pióra dotknięcie zaznacza pomieszczenie"
        hole -> "rysujesz wycięcie — punkty wewnątrz obrysu, dotknięcie pierwszego domyka"
        else -> "pióro: ${pen.short}$penManifold · dotknięcie pierwszego punktu domyka · obrys NA pomieszczeniu = zagęszczenie"
    }

    else -> "wybierz wpis, aby zobaczyć stan rzutu"
}

// ── Rozdzielacz ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ManifoldTools(
    editMarks: Boolean,
    canEdit: Boolean,
    count: Int,
    maxMarks: Int,
    selMark: Int?,
    active: String,
    onEdit: () -> Unit,
    onDeleteSelected: () -> Unit,
    onUndoLast: () -> Unit,
    onFinish: () -> Unit,
    onPickType: (String) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (!editMarks) {
            OutlinedButton(onClick = onEdit, enabled = canEdit) { Text("✏ Edycja") }
        } else {
            OutlinedButton(onClick = onDeleteSelected, enabled = selMark != null) { Text("Usuń zaznaczoną") }
            OutlinedButton(onClick = onUndoLast, enabled = count > 0) { Text("Cofnij ostatnią") }
            OutlinedButton(onClick = onFinish) { Text("Zakończ edycję") }
        }
    }
    Hint("Rozdzielacze na rzucie: $count z $maxMarks.")
    ToolTitle("Rodzaj skrzynki")
    Hint(if (selMark != null) "Zaznaczony rozdzielacz ${selMark + 1} — wybierz kolor." else "Dotknij kropki rozdzielacza, aby wybrać kolor.")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        UFH_BOX_COLORS.forEach { b ->
            DotChip(
                label = b.label,
                color = Color(b.color),
                selected = b.type == active,
                enabled = selMark != null,
                onClick = { onPickType(b.type) },
            )
        }
    }
}

// ── Źródło ciepła ────────────────────────────────────────────────────────────

@Composable
private fun SourceTools(
    source: HeatSourceMark?,
    enabled: Boolean,
    sourceElsewhere: String,
    onKind: (String) -> Unit,
    onDelete: () -> Unit,
) {
    AuditChoiceField(
        label = "Rodzaj źródła",
        options = UFH_HEAT_SOURCE_KINDS,
        selected = source?.kind?.ifEmpty { null } ?: UFH_HEAT_SOURCE_DEFAULT_KIND,
        onSelect = { it?.let(onKind) },
        enabled = enabled && source != null,
    )
    Hint("Rodzaj to podpis kropki — na wyliczenie podejść nie wpływa.")
    OutlinedButton(onClick = onDelete, enabled = enabled && source != null) { Text("Usuń źródło") }
    if (sourceElsewhere.isNotEmpty() && source == null) Hint("Źródło stoi teraz na: $sourceElsewhere")
}

// ── Skala ────────────────────────────────────────────────────────────────────

@Composable
private fun ScaleInfo(scale: PlanScale?, aspect: Double, planDocId: String, points: Int, onClear: () -> Unit) {
    if (scaleReady(scale) && aspect > 0) {
        val s = scale!!
        val w = kotlin.math.round(cmPerUnit(s) / 100 * 10) / 10
        val h = kotlin.math.round(cmPerUnit(s) / aspect / 100 * 10) / 10
        Hint("odcinek ${pl1(s.cm)} cm · rzut ≈ ${pl1(w)} × ${pl1(h)} m")
        if (s.byName.isNotEmpty()) Hint("kalibrował: ${s.byName}${if (s.at.isNotEmpty()) " · ${fmtWhen(s.at)}" else ""}")
        if (s.planDocId.isNotEmpty() && s.planDocId != planDocId) {
            Warn("Skala kalibrowana na innym pliku rzutu — sprawdź ją ponownie.")
        }
    } else {
        Hint(
            "Bez skali nie da się policzyć metrażu. Dotknij dwóch końców odcinka o znanej długości " +
                "(np. ściany z wymiarem) i podaj, ile ma centymetrów.",
        )
    }
    if (points > 0) OutlinedButton(onClick = onClear) { Text("Wyczyść odcinek") }
}

@Composable
private fun ScaleLengthDialog(onDismiss: () -> Unit, onConfirm: (Double) -> Unit) {
    var text by remember { mutableStateOf("") }
    val value = text.replace(',', '.').toDoubleOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Długość odcinka") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("centymetry") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { value?.let(onConfirm) }, enabled = value != null && value > 0) { Text("Ustaw skalę") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

// ── Pomieszczenia ────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AreaTools(
    ready: Boolean,
    enabled: Boolean,
    pipeSystem: String,
    /** Pomieszczenia PO automacie przydziału (do wyświetlania). */
    rooms: List<RoomShape>,
    /** Pomieszczenia robocze (tylko ręczne decyzje o rozdzielaczu). */
    draftRooms: List<RoomShape>,
    scale: PlanScale?,
    marks: List<ManifoldMark>,
    boxType: String,
    autoIds: Set<String>,
    moved: Int,
    overloadedAuto: Int,
    zones: Map<String, com.ekotak.teamtalk.domain.ufh.RoomZones>,
    pen: AreaCat?,
    penManifold: String?,
    selectedId: String?,
    outlineSize: Int,
    drawingHole: Boolean,
    onCalibrate: () -> Unit,
    onPickCat: (AreaCat) -> Unit,
    onPickManifold: (String?) -> Unit,
    onClearAssign: () -> Unit,
    onSelectRoom: (String) -> Unit,
    onPatchRoom: (String, (RoomShape) -> RoomShape) -> Unit,
    onDeleteRoom: (String) -> Unit,
    onDeletePatch: (String, String) -> Unit,
    onConvertToPatch: (String) -> Unit,
    onStartHole: (String) -> Unit,
    onUndoHole: (String) -> Unit,
    onClose: () -> Unit,
    onUndoPoint: () -> Unit,
    onDiscard: () -> Unit,
) {
    val loopMaxM = ufhLoopMaxM(pipeSystem)
    val manifoldMax = ufhManifoldMax(pipeSystem)
    val pipeMm = ufhPipeMm(pipeSystem)
    val minSpacingCm = ufhMinSpacingCm(pipeSystem)
    val sums = catSums(draftRooms, scale)
    fun tooDense(cat: AreaCat) = cat.spacingM != null && cat.spacingM * 100 < minSpacingCm - 1e-9

    if (!ready) {
        Warn("Bez skali nie da się policzyć metrażu.")
        OutlinedButton(onClick = onCalibrate, enabled = enabled) { Text("Zmierz skalę") }
        return
    }

    // Rysowanie.
    if (pen != null) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = onClose, enabled = outlineSize >= 3) {
                Text(if (drawingHole) "Domknij wycięcie" else "Domknij wielobok")
            }
            OutlinedButton(onClick = onUndoPoint, enabled = outlineSize > 0) { Text("Cofnij punkt") }
            if (outlineSize > 0 || drawingHole) OutlinedButton(onClick = onDiscard) { Text("Porzuć obrys") }
        }
    }
    if (marks.size > 1 && overloadedAuto > 0) {
        Warn("$overloadedAuto× rozdzielacz ponad limit pętli — dołóż skrzynkę")
    } else if (marks.size > 1 && moved > 0) {
        Hint("$moved pom. przepięte na dalszą skrzynkę (limit belek)")
    }

    ToolTitle("Kategoria zbierająca metry — dotknij, potem obwódź")
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MEASURE_CATS.forEach { c ->
            DotChip(
                label = "${c.short} ${sums[c]?.m2?.let { "${pl1(it)} m²" } ?: "—"}",
                color = Color(c.color),
                selected = pen == c,
                enabled = enabled && !tooDense(c),
                onClick = { onPickCat(c) },
            )
        }
    }
    Hint(
        "Obrys narysowany NA istniejącym pomieszczeniu = zagęszczenie: ten sam obwód ułożony gęściej. " +
            "Strefa = jedna pętla rury $pipeMm mm (max ${pl1(loopMaxM)} mb z dobiegiem).",
    )

    // Lista pomieszczeń.
    ToolTitle("Pomieszczenia (${rooms.size})")
    if (rooms.isEmpty()) Hint("Brak obrysów — nic jeszcze nie zmierzono.")
    val many = marks.size > 1
    rooms.forEachIndexed { i, r ->
        val z = zones[r.id]
        val mfIdx = markIndexById(marks, r.manifoldId)
        val m2 = roomM2(r, scale)
        RoomRow(
            number = "${i + 1}",
            color = Color(r.cat.color),
            title = roomLabel(r, i),
            detail = buildString {
                append(m2?.let { "${pl2(it)} m²" } ?: "—")
                append(" · ${r.cat.short}")
                if (z != null) append(" · strefa ${if (z.mixed) "~" else ""}${pl2(z.perZoneM2)} m²")
                if (r.holes.isNotEmpty()) append(" · −${r.holes.size} wycięcie")
                if (many) append(" · ${if (mfIdx >= 0) manifoldShort(mfIdx) else "R?"}${if (r.id in autoIds) " (automat)" else " (ręcznie)"}")
            },
            value = z?.let { "${fmtMb(it.total)} mb · ${loopsLabel(it.count)}" },
            selected = r.id == selectedId,
            onClick = { onSelectRoom(r.id) },
        )
        r.patches.forEachIndexed { pi, p ->
            val pm2 = patchM2(p, scale)
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "${patchLabel(i, pi)} · ${patchTitle(p)}${pm2?.let { " · ${pl2(it)} m²" }.orEmpty()}" +
                        (p.cat.spacingM?.let { sp -> pm2?.let { " · ${fmtMb(it / sp)} mb" } } ?: " · bez rury"),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (enabled) TextButton(onClick = { onDeletePatch(r.id, p.id) }) { Text("✕") }
            }
        }
    }
    val floorLoops = zonesTotal(zones, rooms)
    if (floorLoops > 0) SumRow("rura OP razem · ${loopsLabel(floorLoops)}", "${fmtMb(zonesPipeTotal(zones, rooms))} mb")
    floorLoopMessage(floorLoops, marks.size, manifoldMax)?.let { Warn(it) }
    zonesRatioMessage(zones, rooms)?.let { Warn(it) }

    // Edycja zaznaczonego.
    val selected = rooms.firstOrNull { it.id == selectedId }
    if (selected != null && enabled) {
        val draft = draftRooms.firstOrNull { it.id == selected.id } ?: selected
        val idx = rooms.indexOf(selected)
        ToolTitle("Zaznaczone: ${roomLabel(selected, idx)}")
        AuditTextField(
            label = "Nazwa pomieszczenia",
            value = draft.name,
            onValueChange = { v -> onPatchRoom(selected.id) { it.copy(name = v.take(60)) } },
        )
        val catOptions = if (selected.cat in MEASURE_CATS) MEASURE_CATS else listOf(selected.cat) + MEASURE_CATS
        AuditChoiceField(
            label = "Kategoria",
            options = catOptions.filter { !tooDense(it) || it == selected.cat },
            selected = selected.cat,
            optionLabel = { it.label },
            onSelect = { c -> c?.let { cat -> onPatchRoom(selected.id) { it.copy(cat = cat) } } },
        )
        if (many) {
            val autoIdx = if (selected.id in autoIds) markIndexById(marks, selected.manifoldId) else -1
            val options = listOf("") + marks.map { it.id }
            AuditChoiceField(
                label = "Rozdzielacz zasilający",
                options = options,
                selected = if (selected.id in autoIds) "" else draft.manifoldId,
                optionLabel = { id ->
                    if (id.isEmpty()) {
                        if (autoIdx >= 0) "automat → ${manifoldShort(autoIdx)} (najbliższy)" else "— automat —"
                    } else {
                        val mi = markIndexById(marks, id)
                        if (mi >= 0) "${manifoldShort(mi)} · ${manifoldLabel(mi)}" else "rozdzielacz usunięty z rzutu"
                    }
                },
                onSelect = { id -> onPatchRoom(selected.id) { it.copy(manifoldId = id.orEmpty()) } },
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = { onStartHole(selected.id) }) { Text("+ wycięcie") }
            if (draft.holes.isNotEmpty()) OutlinedButton(onClick = { onUndoHole(selected.id) }) { Text("Cofnij wycięcie") }
            patchParentOf(draftRooms, selected.id)?.let { parent ->
                OutlinedButton(onClick = { onConvertToPatch(selected.id) }) {
                    Text("Zamień na zagęszczenie ${roomLabel(parent, draftRooms.indexOf(parent))}")
                }
            }
            OutlinedButton(onClick = { onDeleteRoom(selected.id) }) { Text("Usuń obrys") }
        }
    }

    // Metraż per rozdzielacz.
    if (many) {
        val split = splitByManifold(rooms, marks, scale)
        val loopsPer = split.groups.map { zonesTotal(zones, it.rooms) }
        ToolTitle("Metraż per rozdzielacz")
        Hint(
            if (selected != null) {
                "Dotknij rozdzielacza, aby przypiąć do niego ${roomLabel(selected, rooms.indexOf(selected))} na sztywno."
            } else {
                "Pomieszczenia przypisują się SAME do najbliższego rozdzielacza. Dotknięcie skrzynki ustawia, " +
                    "gdzie trafią kolejne obrysy. Max $manifoldMax pętli z jednej skrzynki."
            },
        )
        (split.groups + split.rest).forEach { g ->
            val gi = g.index
            val loops = zonesTotal(zones, g.rooms)
            val load = gi?.let { loopLoad(loops, manifoldMax) }
            RoomRow(
                number = gi?.let { manifoldShort(it) } ?: "—",
                color = g.mark?.let { Color(boxColor(markBoxType(it, boxType))) } ?: Color(0xFF8A8A8A),
                title = gi?.let { manifoldLabel(it) } ?: "automat (najbliższy)",
                detail = "${g.m2?.let { "${pl1(it)} m²" } ?: "—"} · ${g.rooms.size} pom." +
                    if (loops > 0) " · ${fmtMb(zonesPipeTotal(zones, g.rooms))} mb · ${loopsLabel(loops)}" else "",
                value = load?.takeIf { loops > 0 }?.badge,
                selected = enabled && (g.id ?: null) == penManifold,
                onClick = { if (enabled) onPickManifold(g.id) },
            )
            if (gi != null) {
                manifoldOverMessage(loops, split.groups.size, freeLoopsElsewhere(loopsPer, gi, manifoldMax), manifoldMax)
                    ?.let { Warn(it) }
            }
            if (g.orphans > 0) Warn("${g.orphans} pom. wskazywało rozdzielacz, którego już nie ma — przypisz ponownie.")
        }
        val manual = manualAssignedCount(draftRooms, marks)
        OutlinedButton(onClick = onClearAssign, enabled = enabled && manual > 0) {
            Text("Przypisz od nowa ($manual ręcznie)")
        }
    }
}

// ── Historia ─────────────────────────────────────────────────────────────────

@Composable
private fun HistoryTools(
    history: List<PlanHistoryEntry>,
    viewIdx: Int?,
    planDocId: String,
    currentSummary: String,
    canRestore: Boolean,
    onView: (Int?) -> Unit,
    onRestore: () -> Unit,
) {
    RoomRow(number = "•", color = MaterialTheme.colorScheme.primary, title = "Aktualne", detail = currentSummary, value = null, selected = viewIdx == null, onClick = { onView(null) })
    if (history.isEmpty()) Hint("Brak wpisów — historia zapisuje się przy każdej zmianie na rzucie.")
    history.forEachIndexed { i, h ->
        RoomRow(
            number = "${i + 1}",
            color = MaterialTheme.colorScheme.outline,
            title = "${fmtWhen(h.at)} · ${entryTitle(h)}",
            detail = "${authorLabel(h)} · ${entrySummary(h)}" +
                if (!h.planDocId.isNullOrEmpty() && h.planDocId != planDocId) " · inny rzut" else "",
            value = null,
            selected = viewIdx == i,
            onClick = { onView(i) },
        )
    }
    if (viewIdx != null) {
        Button(onClick = onRestore, enabled = canRestore, modifier = Modifier.fillMaxWidth()) { Text("Przywróć ten stan") }
    }
}

// ── Klocki ───────────────────────────────────────────────────────────────────

@Composable
internal fun RoomRow(
    number: String,
    color: Color,
    title: String,
    detail: String,
    value: String?,
    selected: Boolean,
    onClick: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.10f) else Color.Transparent)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(22.dp).clip(CircleShape).background(color),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = number,
                style = MaterialTheme.typography.labelSmall,
                color = if (color.luminance() > 0.5f) Color(0xFF080808) else Color.White,
                maxLines = 1,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        value?.let {
            Spacer(Modifier.width(8.dp))
            Text(it, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
internal fun DotChip(label: String, color: Color, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(999.dp)
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) colors.primary.copy(alpha = 0.12f) else Color.Transparent)
            .border(1.dp, (if (selected) colors.primary else colors.outline).copy(alpha = if (enabled) 1f else 0.5f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = colors.onSurface.copy(alpha = if (enabled) 1f else 0.5f),
        )
    }
}

@Composable
internal fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
internal fun Warn(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

@Composable
internal fun ToolTitle(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
}

@Composable
internal fun SumRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
    }
}

internal fun pl2(v: Double): String = String.format(Locale("pl"), "%.2f", v)

internal fun pl1(v: Double): String {
    val r = kotlin.math.round(v * 10) / 10
    return if (r % 1.0 == 0.0) r.toLong().toString() else String.format(Locale("pl"), "%.1f", r)
}

/** Dotknięcie pierwszego punktu domyka obrys (jednostki szerokości obrazu). */
private const val CLOSE_RADIUS = 0.025
private const val OUTLINE_MAX = 60
