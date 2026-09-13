package com.ekotak.teamtalk.presentation.crm

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ekotak.teamtalk.domain.model.AuditConflict
import com.ekotak.teamtalk.domain.model.FloorPlanDoc
import com.ekotak.teamtalk.domain.model.UfhFloor
import com.ekotak.teamtalk.domain.ufh.AreaCat
import com.ekotak.teamtalk.domain.ufh.FloorAuditResult
import com.ekotak.teamtalk.domain.ufh.FloorPipe
import com.ekotak.teamtalk.domain.ufh.FloorPlanPatch
import com.ekotak.teamtalk.domain.ufh.LEAD_IN_SLACK
import com.ekotak.teamtalk.domain.ufh.LEAD_IN_TRIPS
import com.ekotak.teamtalk.domain.ufh.LoopLevel
import com.ekotak.teamtalk.domain.ufh.MEASURE_CATS
import com.ekotak.teamtalk.domain.ufh.NO_PLAN
import com.ekotak.teamtalk.domain.ufh.PipeItem
import com.ekotak.teamtalk.domain.ufh.PipeSource
import com.ekotak.teamtalk.domain.ufh.SUPPLY_FLOOR_GAP_M
import com.ekotak.teamtalk.domain.ufh.SUPPLY_FORMULA
import com.ekotak.teamtalk.domain.ufh.SUPPLY_RESERVE_M
import com.ekotak.teamtalk.domain.ufh.UFH_BOX_COLORS
import com.ekotak.teamtalk.domain.ufh.UfhAuditSummary
import com.ekotak.teamtalk.domain.ufh.WATER_VOLUME_FORMULA
import com.ekotak.teamtalk.domain.ufh.ZONE_MAX_RATIO
import com.ekotak.teamtalk.domain.ufh.authorLabel
import com.ekotak.teamtalk.domain.ufh.boxColor
import com.ekotak.teamtalk.domain.ufh.color
import com.ekotak.teamtalk.domain.ufh.commonBoxType
import com.ekotak.teamtalk.domain.ufh.entrySummary
import com.ekotak.teamtalk.domain.ufh.entryTitle
import com.ekotak.teamtalk.domain.ufh.floorMaxMarks
import com.ekotak.teamtalk.domain.ufh.floorTitle
import com.ekotak.teamtalk.domain.ufh.fmtL
import com.ekotak.teamtalk.domain.ufh.fmtLpm
import com.ekotak.teamtalk.domain.ufh.fmtMb
import com.ekotak.teamtalk.domain.ufh.fmtWhen
import com.ekotak.teamtalk.domain.ufh.groupCatSummary
import com.ekotak.teamtalk.domain.ufh.heatSourceLabel
import com.ekotak.teamtalk.domain.ufh.leadRangeLabel
import com.ekotak.teamtalk.domain.ufh.loadPrepPatch
import com.ekotak.teamtalk.domain.ufh.loopsLabel
import com.ekotak.teamtalk.domain.ufh.manifoldLabel
import com.ekotak.teamtalk.domain.ufh.manifoldShort
import com.ekotak.teamtalk.domain.ufh.markBoxType
import com.ekotak.teamtalk.domain.ufh.markIndexById
import com.ekotak.teamtalk.domain.ufh.parsePlanPrep
import com.ekotak.teamtalk.domain.ufh.pipeRatioMessage
import com.ekotak.teamtalk.domain.ufh.pipeSizeLabel
import com.ekotak.teamtalk.domain.ufh.pipesLabel
import com.ekotak.teamtalk.domain.ufh.planState
import com.ekotak.teamtalk.domain.ufh.prepSummary
import com.ekotak.teamtalk.domain.ufh.ratioLabel
import com.ekotak.teamtalk.domain.ufh.roomLabel
import com.ekotak.teamtalk.domain.ufh.roomM2
import com.ekotak.teamtalk.domain.ufh.roomsNeedingSpacing
import com.ekotak.teamtalk.domain.ufh.sameMarks
import com.ekotak.teamtalk.domain.ufh.saveTypesPatch
import com.ekotak.teamtalk.domain.ufh.scaleReady
import com.ekotak.teamtalk.domain.ufh.stampLabel
import com.ekotak.teamtalk.domain.ufh.supplyCountLabel
import com.ekotak.teamtalk.domain.ufh.ufhLoopMaxM
import com.ekotak.teamtalk.domain.ufh.ufhManifoldMax
import com.ekotak.teamtalk.domain.ufh.ufhPipeMm
import com.ekotak.teamtalk.domain.ufh.withPlanState

/**
 * Bloki rzutu i wyników w zakładce „Audyt" — mobilne odpowiedniki
 * `UfhFloorPlanMarker` (miniatura z podsumowaniem), `UfhPipeLengths`,
 * `UfhSupplyLengths`, `UfhWaterVolume` i belki sum z panelu.
 *
 * Wszystkie liczby biorą się z `floorAudit` / `ufhAuditSummary` — jednego
 * przelotu po formularzu, tego samego, który liczy Ofertę. Te komponenty tylko
 * je pokazują.
 */

// ── Rzut kondygnacji (miniatura + podsumowanie) ─────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UfhFloorPlanBlock(
    index: Int,
    floor: UfhFloor,
    floorCount: Int,
    pipeSystem: String,
    plans: List<FloorPlanDoc>,
    hasBasement: Boolean,
    result: FloorAuditResult,
    /** Podpis kondygnacji, na której stoi źródło ciepła (gdy nie na tej). */
    sourceElsewhere: String,
    enabled: Boolean,
    saving: Boolean,
    onEditFloor: ((UfhFloor) -> UfhFloor) -> Unit,
    onCommit: (FloorPlanPatch) -> Unit,
) {
    val state = result.plan
    val autoSlot = remember(floor.name, index, floorCount, hasBasement, plans) {
        com.ekotak.teamtalk.domain.ufh.resolveFloorPlanSlot(
            floor.name, index, floorCount, hasBasement, plans.map { it.slot }.toSet(),
        )
    }
    val effSlot = state.planSlot.ifEmpty { autoSlot.orEmpty() }
    val plan = if (effSlot.isNotEmpty() && effSlot != NO_PLAN) plans.firstOrNull { it.slot == effSlot } else null
    val maxMarks = floorMaxMarks(floor)
    val label = floorTitle(index, floor.name)

    var editorTool by remember { mutableStateOf<PlanTool?>(null) }
    var askPrep by remember { mutableStateOf(false) }

    Spacer(Modifier.height(12.dp))
    ToolTitle("Rzut kondygnacji — rozdzielacz i metraż")
    Spacer(Modifier.height(4.dp))
    val slotOptions = buildList {
        add("")
        plans.forEach { add(it.slot) }
        if (state.planSlot.isNotEmpty() && state.planSlot != NO_PLAN && plans.none { it.slot == state.planSlot }) {
            add(state.planSlot)
        }
        add(NO_PLAN)
    }
    AuditChoiceField(
        label = "Który rzut dotyczy tej kondygnacji",
        options = slotOptions,
        selected = state.planSlot,
        optionLabel = { slot ->
            when {
                slot.isEmpty() -> autoSlot?.let { a -> "automatycznie — ${plans.firstOrNull { it.slot == a }?.label ?: a}" }
                    ?: "automatycznie (brak dopasowania)"
                slot == NO_PLAN -> "brak / nie dotyczy"
                else -> plans.firstOrNull { it.slot == slot }?.label ?: "$slot (brak pliku)"
            }
        },
        onSelect = { v ->
            onEditFloor { f -> f.withPlanState(f.planState().copy(planSlot = v.orEmpty(), planDocId = "")) }
        },
        enabled = enabled,
    )
    Spacer(Modifier.height(8.dp))

    when {
        state.planSlot == NO_PLAN -> {
            Hint("Rzut nie dotyczy tej kondygnacji (rozdzielacz gdzie indziej — np. w garażu).")
            return
        }

        plan == null -> {
            Hint(
                "Brak zdjęcia rzutu dla tej kondygnacji. Wgraj obraz do sekcji „Projekt domu” " +
                    "(zakładka „Pliki”) albo przypisz tam stronę PDF-a — pojawi się tutaj.",
            )
            return
        }
    }
    plan!!

    // Miniatura — dotknięcie otwiera edytor.
    val thumb = rememberFloorPlanImage(plan, targetPx = 900)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF101010))
            .clickable(enabled = thumb.bitmap != null) { editorTool = PlanTool.MANIFOLD },
        contentAlignment = Alignment.Center,
    ) {
        val bmp = thumb.bitmap
        when {
            bmp != null -> UfhPlanCanvas(
                image = bmp,
                aspect = bmp.width.toFloat() / bmp.height.toFloat(),
                layer = PlanLayer(
                    rooms = result.auto.rooms,
                    scale = state.scale,
                    marks = state.marks,
                    markColors = state.marks.map { Color(boxColor(markBoxType(it, floor.boxType))) },
                    heatSource = state.heatSource?.point,
                    zones = result.zones.mapValues { (_, z) -> ZoneOverlay(cuts = z.cuts.map { it.a to it.b }) },
                ),
                catColor = { Color(it.color) },
                small = true,
                modifier = Modifier.fillMaxWidth(),
            )

            thumb.loading -> CircularProgressIndicator(Modifier.padding(24.dp), color = Color.White)
            else -> Text(
                "Rzutu nie ma na telefonie — otwórz go raz w zasięgu.",
                color = Color.White,
                modifier = Modifier.padding(16.dp),
            )
        }
    }
    Spacer(Modifier.height(6.dp))
    Hint(plan.fileName)
    Text(
        text = when {
            maxMarks < 1 -> "Ilość rozdzielaczy = 0 → brak rozdzielacza na tej kondygnacji"
            state.marks.isNotEmpty() -> "Rozdzielacz zaznaczony (${state.marks.size} z $maxMarks)"
            else -> "Miejsce rozdzielacza nie zaznaczone"
        },
        style = MaterialTheme.typography.bodySmall,
        color = if (state.marks.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    state.marksAuthor?.let { Hint("rozdzielacze zapisał: ${stampLabel(it)}") }
    if (state.planDocId.isNotEmpty() && state.planDocId != plan.docId && (state.marks.isNotEmpty() || state.rooms.isNotEmpty())) {
        Warn("Rzut został podmieniony po zaznaczeniu — sprawdź kropki i obrysy.")
    }
    Text(
        text = when {
            state.heatSource != null -> heatSourceLabel(state.heatSource)
            sourceElsewhere.isNotEmpty() -> "Źródło ciepła — na innej kondygnacji ($sourceElsewhere)"
            else -> "Źródło ciepła — nie zaznaczone w budynku"
        },
        style = MaterialTheme.typography.bodySmall,
        color = if (state.heatSource != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    )

    val prep = remember(plan.docId, plan.planData) { parsePlanPrep(plan.planData) }
    Spacer(Modifier.height(6.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(onClick = { editorTool = if (scaleReady(state.scale)) PlanTool.AREA else PlanTool.SCALE }, enabled = enabled) {
            Text(if (scaleReady(state.scale)) "📐 Mierz metraż" else "📐 Ustaw skalę i mierz")
        }
        OutlinedButton(onClick = { editorTool = PlanTool.SOURCE }, enabled = enabled) {
            Text(if (state.heatSource != null) "🟢 Przestaw źródło" else "🟢 Zaznacz źródło ciepła")
        }
        OutlinedButton(onClick = { editorTool = PlanTool.MANIFOLD }, enabled = enabled && maxMarks >= 1) {
            Text("📍 Rozdzielacze")
        }
        if (prep != null && (prep.rooms.isNotEmpty() || scaleReady(prep.scale))) {
            OutlinedButton(
                onClick = {
                    if (state.rooms.isNotEmpty() || scaleReady(state.scale)) {
                        askPrep = true
                    } else {
                        onCommit(loadPrepPatch(state, prep, plan.docId, state.planSlot.ifEmpty { effSlot }))
                    }
                },
                enabled = enabled && !saving,
            ) { Text("⬇ Wczytaj przygotowanie (${prepSummary(prep)})") }
        }
    }

    // Pomiar metrażu z rzutu.
    val needSpacing = roomsNeedingSpacing(state.rooms)
    val zones = result.zones
    val shown = result.auto.rooms
    PlanCollapsible(
        title = "Pomiar metrażu z rzutu (${state.rooms.size})",
        badge = when {
            needSpacing > 0 -> "$needSpacing × rozstaw do ustalenia"
            scaleReady(state.scale) -> "skala ${pl1(state.scale!!.cm)} cm"
            else -> "brak skali"
        },
        initiallyExpanded = scaleReady(state.scale) && state.rooms.isNotEmpty(),
    ) {
        if (needSpacing > 0) {
            Warn("$needSpacing pom. z przygotowania czeka na rozstaw — otwórz edytor, zaznacz pomieszczenie i wybierz rozstaw.")
        }
        when {
            !scaleReady(state.scale) -> Hint("Najpierw skala: w edytorze zaznacz wiadomy odcinek i podaj jego długość w cm.")
            state.rooms.isEmpty() -> Hint("Skala gotowa — obwódź pomieszczenia w edytorze.")
            else -> {
                state.areaAuthor?.let { Hint("pomiar zapisał: ${stampLabel(it)}") }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    (listOfNotNull(AreaCat.TODO_CAT.takeIf { (result.sums[it]?.count ?: 0) > 0 }) + MEASURE_CATS).forEach { c ->
                        Text(
                            text = "● ${c.short} ${result.sums[c]?.m2?.let { "${pl1(it)} m²" } ?: "—"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(c.color),
                        )
                    }
                }
                val many = state.marks.size > 1
                shown.forEachIndexed { i, r ->
                    val z = zones[r.id]
                    val mfIdx = markIndexById(state.marks, r.manifoldId)
                    RoomRow(
                        number = "${i + 1}",
                        color = Color(r.cat.color),
                        title = roomLabel(r, i),
                        detail = buildString {
                            append(roomM2(r, state.scale)?.let { "${pl2(it)} m²" } ?: "—")
                            append(" · ${r.cat.short}")
                            if (r.patches.isNotEmpty()) append(" +${r.patches.size} zagęszczenie")
                            if (r.holes.isNotEmpty()) append(" · −${r.holes.size} wycięcie")
                            if (many) append(" · ${if (mfIdx >= 0) manifoldShort(mfIdx) else "bez rozdzielacza"}")
                        },
                        value = z?.let { "${fmtMb(it.total)} mb · ${loopsLabel(it.count)}" },
                        selected = false,
                        onClick = null,
                    )
                }
                if (result.floorLoops > 0) {
                    SumRow("rura OP razem · ${loopsLabel(result.floorLoops)}", "${fmtMb(result.floorPipeM)} mb")
                }
                result.floorLoopWarning?.let { Warn(it) }
                result.ratioWarning?.let { Warn(it) }
            }
        }
    }

    // Metraż per rozdzielacz.
    if (state.marks.size > 1 || maxMarks > 1) {
        val manifoldMax = ufhManifoldMax(pipeSystem)
        PlanCollapsible(
            title = "Metraż per rozdzielacz",
            badge = when {
                state.marks.size <= 1 -> "zaznaczone ${state.marks.size} z $maxMarks"
                state.rooms.isEmpty() -> "brak pomiaru"
                result.overloaded > 0 -> "${result.overloaded}× ponad $manifoldMax pętli"
                result.auto.moved > 0 -> "${result.auto.moved}× przepięte przez limit"
                else -> "komplet"
            },
            initiallyExpanded = false,
        ) {
            when {
                state.marks.size <= 1 -> Hint(
                    "Ilość rozdzielaczy kondygnacji: $maxMarks · zaznaczonych na rzucie: ${state.marks.size}. " +
                        "Postaw kropki wszystkich rozdzielaczy — pomieszczenia przypiszą się do nich same.",
                )

                state.rooms.isEmpty() -> Hint("Najpierw obwódź pomieszczenia w edytorze — do rozdzielaczy przypiszą się same.")
                else -> {
                    result.manifolds.forEach { m ->
                        val g = m.group
                        RoomRow(
                            number = g.index?.let { manifoldShort(it) } ?: "—",
                            color = g.mark?.let { Color(boxColor(markBoxType(it, floor.boxType))) } ?: Color(0xFF8A8A8A),
                            title = g.index?.let { manifoldLabel(it) } ?: "bez OP",
                            detail = "${g.m2?.let { "${pl1(it)} m²" } ?: "—"} · ${g.rooms.size} pom." +
                                if (m.loops > 0) " · ${fmtMb(m.pipeM)} mb · ${loopsLabel(m.loops)}" else "",
                            value = m.load.badge.takeIf { m.loops > 0 },
                            selected = m.load.level == LoopLevel.OVER,
                            onClick = null,
                        )
                        groupCatSummary(g).takeIf { it.isNotEmpty() }?.let { Hint(it) }
                        m.overMessage?.let { Warn(it) }
                        if (g.orphans > 0) Warn("${g.orphans} pom. wskazywało rozdzielacz, którego już nie ma — przypisz ponownie.")
                    }
                    OutlinedButton(onClick = { editorTool = PlanTool.AREA }, enabled = enabled) { Text("🔗 Sprawdź przydział") }
                }
            }
        }
    }

    // Rodzaj skrzynki.
    PlanCollapsible(title = "Rodzaj skrzynki rozdzielacza", badge = null, initiallyExpanded = false) {
        var draft by remember(state.marks) { mutableStateOf(state.marks) }
        var sel by remember(state.marks) { mutableStateOf<Int?>(null) }
        if (draft.isEmpty()) {
            Hint("Rodzaj skrzynki ustawisz po zaznaczeniu miejsca rozdzielacza.")
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                draft.forEachIndexed { i, m ->
                    DotChip(
                        label = manifoldShort(i),
                        color = Color(boxColor(markBoxType(m, floor.boxType))),
                        selected = sel == i,
                        enabled = enabled,
                        onClick = { sel = if (sel == i) null else i },
                    )
                }
            }
            Hint(if (sel != null) "Rozdzielacz ${sel!! + 1} zaznaczony — wybierz kolor, potem „Zapisz typ”." else "Wybierz rozdzielacz, aby zmienić rodzaj jego skrzynki.")
            val active = sel?.let { draft.getOrNull(it) }?.let { markBoxType(it, floor.boxType) } ?: commonBoxType(draft, floor.boxType)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                UFH_BOX_COLORS.forEach { b ->
                    DotChip(
                        label = b.label,
                        color = Color(b.color),
                        selected = b.type == active,
                        enabled = enabled && sel != null,
                        onClick = {
                            val s = sel ?: return@DotChip
                            draft = draft.mapIndexed { i, m -> if (i == s) m.copy(boxType = b.type) else m }
                        },
                    )
                }
            }
            if (!sameMarks(state.marks, draft)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onCommit(saveTypesPatch(draft)) }, enabled = enabled && !saving) { Text("Zapisz typ") }
                    TextButton(onClick = {
                        draft = state.marks
                        sel = null
                    }) { Text("Anuluj") }
                }
            }
        }
    }

    // Historia rzutu.
    PlanCollapsible(title = "Historia rzutu (${state.history.size})", badge = null, initiallyExpanded = false) {
        if (state.history.isEmpty()) {
            Hint("Brak wpisów — historia zapisuje się przy każdej zmianie na rzucie.")
        } else {
            Hint("Wpis = stan sprzed zapisu; osoba = konto, które zapisało zmianę.")
            state.history.forEachIndexed { i, h ->
                RoomRow(
                    number = "${i + 1}",
                    color = MaterialTheme.colorScheme.outline,
                    title = fmtWhen(h.at),
                    detail = "${entryTitle(h)} · ${authorLabel(h)} · ${entrySummary(h)}",
                    value = null,
                    selected = false,
                    onClick = { editorTool = PlanTool.HISTORY },
                )
            }
        }
    }

    editorTool?.let { tool ->
        UfhPlanEditor(
            floorLabel = label,
            plan = plan,
            current = state,
            planSlot = state.planSlot.ifEmpty { effSlot },
            boxType = floor.boxType,
            pipeSystem = pipeSystem,
            maxMarks = maxMarks,
            sourceElsewhere = sourceElsewhere,
            initialTool = tool,
            enabled = enabled,
            saving = saving,
            onCommit = onCommit,
            onClose = { editorTool = null },
        )
    }

    if (askPrep && prep != null) {
        AlertDialog(
            onDismissRequest = { askPrep = false },
            title = { Text("Wczytać przygotowanie?") },
            text = {
                Text(
                    "Wczytanie przygotowania z zakładki „Pliki” zastąpi bieżący pomiar tej kondygnacji " +
                        "(${state.rooms.size} pom.). Poprzedni stan zostanie w historii rzutu.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    askPrep = false
                    onCommit(loadPrepPatch(state, prep, plan.docId, state.planSlot.ifEmpty { effSlot }))
                }) { Text("Wczytaj") }
            },
            dismissButton = { TextButton(onClick = { askPrep = false }) { Text("Anuluj") } },
        )
    }
}

// ── Długość rury OP ──────────────────────────────────────────────────────────

@Composable
fun UfhPipeLengthsBlock(pipe: FloorPipe, floorNo: Int, pipeSystem: String) {
    val loopMaxM = ufhLoopMaxM(pipeSystem)
    val pipeMm = ufhPipeMm(pipeSystem)
    val title = "Długość rury OP — kondygnacja $floorNo"
    if (pipe.source == PipeSource.NONE) {
        PlanCollapsible(title = title, badge = "brak danych", initiallyExpanded = false) {
            Hint("Nie ma z czego liczyć — zmierz pomieszczenia na rzucie albo wpisz powierzchnie wg rozstawu rur.")
        }
        return
    }
    PlanCollapsible(title = "$title (${pipe.items.size})", badge = "${fmtMb(pipe.total)} mb", initiallyExpanded = false) {
        Hint(
            "rura grzewcza = powierzchnia / rozstaw · dobieg pętli = odległość rozdzielacz → jej strefa × " +
                "$LEAD_IN_TRIPS × ${pl2(LEAD_IN_SLACK).trimEnd('0').trimEnd(',')} · pętla max ${pl1(loopMaxM)} mb " +
                "(rura $pipeMm mm, dobieg wliczony) · 1 pętla = 1 strefa zaznaczona na rzucie",
        )
        pipe.items.forEach { PipeRow(it) }
        SumRow(
            "grzewcza ${fmtMb(pipe.heating)} mb" +
                (if (pipe.lead > 0) " + dobiegi ${fmtMb(pipe.lead)} mb" else "") + " · ${loopsLabel(pipe.loops)}",
            "${fmtMb(pipe.total)} mb",
        )
        if (pipe.source == PipeSource.AREAS) {
            Hint("Liczone z pól metrażu — bez dobiegu od rozdzielacza. Obrysuj pomieszczenia na rzucie i zaznacz rozdzielacz, żeby doliczyć dobiegi.")
        }
        if (pipe.source == PipeSource.PLAN && pipe.noLead > 0) {
            Warn(
                if (pipe.noLead == 1) "1 pomieszczenie policzone bez dobiegu — brak rozdzielacza na rzucie."
                else "${pipe.noLead} pomieszczeń policzonych bez dobiegu — brak rozdzielacza na rzucie.",
            )
        }
        pipeRatioMessage(pipe)?.let { Warn(it) }
        if (pipe.impossible > 0) {
            Warn(
                "Sam dobieg przekracza ${pl1(loopMaxM)} mb — takiego pomieszczenia nie zasilisz rurą $pipeMm mm " +
                    "z tego rozdzielacza (potrzebna inna skrzynka albo grubsza rura).",
            )
        }
    }
}

@Composable
private fun PipeRow(item: PipeItem) {
    val lead = leadRangeLabel(item)
    val calc = item.parts.joinToString(" + ") { "${fmtMb(it.m2)} m² / ${kotlin.math.round(it.spacing * 100).toInt()} cm" }
    val detail = buildString {
        append("$calc = ${fmtMb(item.heating)} mb")
        if (item.loops > 1) append(" · strefa ${if (item.parts.size > 1) "~" else ""}${fmtMb(item.m2 / item.loops)} m²")
        if (lead != null) {
            append(" · dobiegi $lead (razem ${fmtMb(item.leadTotal)} mb)")
            item.manifoldIndex?.let { append(" (${manifoldShort(it)})") }
        } else {
            append(" · bez dobiegu")
        }
        item.split?.takeIf { it.ratioOver }?.let {
            append(" · strefy ${ratioLabel(it.maxRatio)} — ponad ${ratioLabel(ZONE_MAX_RATIO)}")
        }
    }
    Column {
        RoomRow(
            number = "",
            color = Color(item.cat.color),
            title = item.label,
            detail = detail,
            value = "${fmtMb(item.total)} mb · ${loopsLabel(item.loops)}",
            selected = item.impossible,
            onClick = null,
        )
    }
}

// ── Budynek: sumy, podejścia, pojemność wodna ────────────────────────────────

@Composable
fun UfhBuildingSummaryBlock(summary: UfhAuditSummary) {
    Spacer(Modifier.height(8.dp))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
    ) {
        Column {
            SumRow("Suma powierzchni OP — wszystkie kondygnacje", "${pl1(summary.totalM2)} m²")
            if (summary.totalPipe.total > 0) Hint("rura OP: ${fmtMb(summary.totalPipe.total)} mb · ${loopsLabel(summary.totalPipe.loops)}")
            if (summary.supply.items.isNotEmpty()) {
                Hint("podejścia: ${fmtMb(summary.supply.total)} mb · ${supplyCountLabel(summary.supply.count)}")
            }
            if (summary.water.total > 0) Hint("pojemność wodna: ${fmtL(summary.water.total)} l")
        }
    }

    // Podejścia do rozdzielaczy.
    val supply = summary.supply
    val blocked = supply.items.isEmpty()
    PlanCollapsible(
        title = "Podejścia do rozdzielaczy — źródło ciepła → rozdzielacz (${supply.items.size})",
        badge = if (blocked) "brak źródła" else "${fmtMb(supply.total)} mb",
        initiallyExpanded = !supply.ok,
    ) {
        if (blocked) {
            supply.problem?.let { Warn(it) }
        } else {
            Hint("$SUPPLY_FORMULA · 1 podejście = 2 rury na tej samej trasie")
            supply.items.forEach { it ->
                RoomRow(
                    number = "",
                    color = Color(boxColor(it.boxType)),
                    title = it.label,
                    detail = if (it.horizM == null) {
                        "brak skali rzutu — nie ma czym zmierzyć odległości"
                    } else {
                        "${fmtMb(it.horizM)} m w poziomie" +
                            (if (it.floorGap > 0) " + ${it.floorGap} × $SUPPLY_FLOOR_GAP_M m pionu" else " (ta sama kondygnacja co źródło)") +
                            " · zasilanie i powrót + $SUPPLY_RESERVE_M mb zapasu"
                    },
                    value = "${it.mb?.let { mb -> "${fmtMb(mb)} mb" } ?: "—"} · 2 rury",
                    selected = it.mb == null,
                    onClick = null,
                )
            }
            SumRow("${supplyCountLabel(supply.count)} · ${pipesLabel(supply.pipes)} od źródła", "${fmtMb(supply.total)} mb")
            val sp = summary.supplyPipe
            SumRow(sp.name + (sp.code?.let { " · $it" } ?: ""), "${fmtMb(supply.total)} mb")
            if (sp.missingMm) {
                Warn("Nie wybrano średnicy rury dobiegowej — uzupełnij „Rura dobiegowa do rozdzielacza” w sekcji „Dane instalacji”.")
            }
            if (sp.missingProduct && !sp.missingMm) {
                Hint("Pod tę średnicę nie mamy przyjętej rury dobiegowej (${sp.producer}) — metry są policzone, kartoteka dojdzie po uzupełnieniu Magazynu.")
            }
            supply.problem?.let { Warn(it) }
        }
    }

    // Pojemność wodna.
    val water = summary.water
    PlanCollapsible(
        title = "Pojemność wodna instalacji — rura podłogowa + podejścia",
        badge = if (water.lines.isEmpty()) "brak danych" else "${fmtL(water.total)} l",
        initiallyExpanded = false,
    ) {
        if (water.lines.isEmpty()) {
            Hint("Nie ma z czego liczyć — pojemność wychodzi z długości rur, więc najpierw zmierz instalację (metraż OP i podejścia do rozdzielaczy).")
        } else {
            Hint(WATER_VOLUME_FORMULA)
            water.lines.forEach { l ->
                RoomRow(
                    number = "",
                    color = MaterialTheme.colorScheme.primary,
                    title = l.label,
                    detail = "${pipeSizeLabel(l)} · ${fmtLpm(l.lPerM)} l/mb × ${fmtMb(l.m)} mb",
                    value = "${fmtL(l.liters)} l",
                    selected = false,
                    onClick = null,
                )
            }
            SumRow("woda w rurach instalacji${if (water.lines.size == 1) " (tylko policzone pozycje)" else ""}", "${fmtL(water.total)} l")
            Hint("Sama objętość RUR — bez rozdzielaczy, źródła ciepła i sprzęgła.")
            summary.biocide?.let { d ->
                SumRow("inhibitor biobójczy — ${fmtL(d.liters)} l, starcza na ${d.coversL} l", "${d.packs} × butelka")
            }
        }
        water.problems.forEach { Warn(it) }
    }

    summary.areaToFix?.let {
        Spacer(Modifier.height(8.dp))
        Warn(it)
    }
}

// ── Konflikt zapisu ──────────────────────────────────────────────────────────

/**
 * Pasek konfliktu: moja wersja audytu czeka w telefonie, a panel zapisał swoją
 * w międzyczasie. Decyzję podejmuje audytor — po cichu nie wygrywa żadna strona.
 */
@Composable
fun AuditConflictBanner(conflicts: List<AuditConflict>, onResolve: (String, Boolean) -> Unit) {
    conflicts.forEach { c ->
        var confirmOverwrite by remember(c.auditId) { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(12.dp),
        ) {
            Column {
                Text(
                    "Audyt zmieniono w panelu, zanim wysłał się zapis z telefonu",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Wersja z panelu: ${c.serverUpdatedAt?.let { fmtWhen(it) } ?: "nieznany czas"}. " +
                        "Twoja wersja czeka w telefonie. Nadpisanie skasuje zmiany z panelu, porzucenie — Twoje.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onResolve(c.auditId, false) }) { Text("Porzuć moje") }
                    Button(onClick = { confirmOverwrite = true }) { Text("Nadpisz") }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (confirmOverwrite) {
            AlertDialog(
                onDismissRequest = { confirmOverwrite = false },
                title = { Text("Nadpisać zmiany z panelu?") },
                text = { Text("Audyt zapisany w panelu zostanie zastąpiony Twoją wersją z telefonu.") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmOverwrite = false
                        onResolve(c.auditId, true)
                    }) { Text("Nadpisz") }
                },
                dismissButton = { TextButton(onClick = { confirmOverwrite = false }) { Text("Anuluj") } },
            )
        }
    }
}

// ── Zwijana sekcja ───────────────────────────────────────────────────────────

@Composable
internal fun PlanCollapsible(
    title: String,
    badge: String?,
    initiallyExpanded: Boolean,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    Column(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                listOfNotNull(badge, if (expanded) "zwiń" else "rozwiń").joinToString(" · "),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
        }
    }
}
