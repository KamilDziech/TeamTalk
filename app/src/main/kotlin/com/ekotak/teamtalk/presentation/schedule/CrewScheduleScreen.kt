package com.ekotak.teamtalk.presentation.schedule

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import com.ekotak.teamtalk.domain.model.CrewSchedule
import com.ekotak.teamtalk.domain.model.ScheduleBacklogItem
import com.ekotak.teamtalk.domain.model.ScheduleCrew
import com.ekotak.teamtalk.domain.model.ScheduleStage
import com.ekotak.teamtalk.domain.model.ScheduleStageStatus
import com.ekotak.teamtalk.domain.model.ScheduleUnplannedDeal
import com.ekotak.teamtalk.domain.model.etapy
import com.ekotak.teamtalk.presentation.components.AppTopBar
import com.ekotak.teamtalk.presentation.theme.Orange600
import com.ekotak.teamtalk.presentation.theme.SyncBlue
import java.time.LocalDate
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * HARMONOGRAM EKIP — kafelek „Harmonogram", 1:1 z `/app/schedule` panelu.
 *
 * Siatka: wiersz = ekipa (nazwa przyklejona z lewej), kolumna = dzień, całość
 * przewija się w bok. Pasek etapu:
 *  • stuknięcie — szuflada etapu (termin, długość, ekipa, skład, przerwa),
 *  • przytrzymanie i przeciągnięcie — inny dzień albo inna ekipa,
 *  • uchwyt przy prawej krawędzi — dłużej/krócej.
 * „Do zaplanowania" to pasek kart NAD osią (w panelu kolumna po lewej):
 * kartę się stuka (wybór ekipy i dnia) albo przytrzymuje i upuszcza na wiersz.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CrewScheduleScreen(
    onNavigateBack: () -> Unit,
    onOpenDeal: (String) -> Unit,
    viewModel: CrewScheduleViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val palette = schedulePalette()

    LaunchedEffect(Unit) {
        viewModel.toasts.collect { snackbar.showSnackbar(it) }
    }

    // Wspólny stan przeciągania karty z listy — duch rysuje się nad całym
    // ekranem, a trafienie liczy siatka (zna wiersze i szerokość dnia).
    val drag = remember { BacklogDragState() }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }

    Scaffold(
        topBar = { AppTopBar(title = "Harmonogram", onNavigateBack = onNavigateBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .onGloballyPositioned { rootOrigin = it.positionInRoot() },
        ) {
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                val sch = state.schedule
                when {
                    state.forbidden -> Centered("Harmonogram ekip jest dla koordynatora, zarządu i admina.")
                    state.isLoading && sch == null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                        CircularProgressIndicator()
                    }
                    sch == null -> Centered(
                        state.error ?: "Brak zasięgu, a tego okresu nie ma jeszcze w telefonie.",
                    )
                    else -> Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Header(state, sch, palette, viewModel)
                        BacklogStrip(
                            sch = sch,
                            palette = palette,
                            busy = state.saving,
                            planningDealId = state.planningDealId,
                            drag = drag,
                            onPlanDeal = viewModel::planDeal,
                            onOpenItem = { viewModel.openPlan(it.id) },
                            onOpenDeal = onOpenDeal,
                            onDrop = { item, target -> viewModel.dropFromBacklog(item, target.crewId, target.day) },
                        )
                        ScheduleGrid(
                            state = state,
                            sch = sch,
                            palette = palette,
                            drag = drag,
                            onToggle = viewModel::toggle,
                            onSelect = { viewModel.select(it) },
                            onMove = { s, day, crew -> viewModel.moveStage(s, day, crew) },
                            onResize = viewModel::resizeStage,
                        )
                        Legend(sch.settings.publishEnabled, palette)
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }

            // Duch przeciąganej karty „Do zaplanowania".
            drag.item?.let { item ->
                val target = drag.target
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                (drag.pointer.x - rootOrigin.x + 24).roundToInt(),
                                (drag.pointer.y - rootOrigin.y - 56).roundToInt(),
                            )
                        }
                        .zIndex(20f),
                ) {
                    Text(
                        text = "${item.clientName} · ${item.durationDays} dn." +
                            (target?.let { " → ${dm(it.day)}" } ?: ""),
                        color = MaterialTheme.colorScheme.surface,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }

    state.selected?.let { stage ->
        StageSheet(
            stage = stage,
            sch = state.schedule ?: return@let,
            busy = state.saving,
            palette = palette,
            onPick = { viewModel.select(it) },
            onClose = { viewModel.select(null) },
            onPatch = viewModel::patchStage,
            onPickCrew = viewModel::pickCrew,
            onShift = viewModel::shiftStage,
            onUnassign = viewModel::takeOff,
            onOpenDeal = {
                viewModel.select(null)
                onOpenDeal(it)
            },
        )
    }

    state.planItem?.let { item ->
        val sch = state.schedule ?: return@let
        PlanSheet(
            item = item,
            sch = sch,
            today = state.today,
            onClose = { viewModel.openPlan(null) },
            onPlan = { crewId, day -> viewModel.dropFromBacklog(item, crewId, day) },
            onOpenDeal = {
                viewModel.openPlan(null)
                onOpenDeal(it)
            },
        )
    }

    state.confirm?.let { c ->
        AlertDialog(
            onDismissRequest = viewModel::dismissConfirm,
            text = { Text(c.message) },
            confirmButton = { TextButton(onClick = viewModel::acceptConfirm) { Text("Tak") } },
            dismissButton = { TextButton(onClick = viewModel::dismissConfirm) { Text("Anuluj") } },
        )
    }
}

@Composable
private fun Centered(text: String) {
    Box(Modifier.fillMaxSize().padding(24.dp), Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

// ── Nagłówek: okres, skala, liczniki, publikacja ─────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Header(
    state: CrewScheduleViewModel.UiState,
    sch: CrewSchedule,
    palette: SchedulePalette,
    vm: CrewScheduleViewModel,
) {
    val warned = sch.stages.count { it.warnings.isNotEmpty() }
    val drafts = sch.stages.count { it.draft }
    val weeks = draftWeeks(sch)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column {
            Text("OPERACJE · MONTAŻE", style = MaterialTheme.typography.labelSmall, color = muted, letterSpacing = 0.6.sp)
            Text("Harmonogram ekip", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = vm::previous, contentPadding = SmallPad) { Text("‹") }
            Text(
                "${range(sch.from, sch.to)}.${sch.to.year}",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            OutlinedButton(onClick = vm::next, contentPadding = SmallPad) { Text("›") }
            OutlinedButton(onClick = vm::goToday, contentPadding = SmallPad) { Text("Dziś") }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ZOOMS.forEach { (z, label) ->
                FilterChip(
                    selected = z == state.zoom,
                    onClick = { vm.setZoom(z) },
                    label = { Text(label, maxLines = 1) },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (state.saving) Chip("Zapisuję…", palette.surf2, MaterialTheme.colorScheme.onSurface)
            if (warned > 0) Chip("⚠ $warned z ostrzeżeniem", palette.warnBg, palette.warn)
            if (sch.settings.publishEnabled && drafts > 0) Chip("Szkic: $drafts", palette.plannedBg, palette.planned)
            if (state.pendingCount > 0) Chip("⏳ ${state.pendingCount} czeka na wysłanie", palette.surf2, SyncBlue)
        }

        if (sch.settings.publishEnabled) {
            Button(
                onClick = vm::publish,
                enabled = !state.saving && weeks.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    (if (state.zoom == 7) "Opublikuj tydzień" else "Opublikuj widoczne tygodnie") +
                        if (drafts > 0) " ($drafts)" else "",
                )
            }
            if (weeks.isNotEmpty()) {
                Text(
                    "Do opublikowania: " + weeks.joinToString(", ") { range(it, it.plusDays(6)) },
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (sch.settings.publishEnabled) "Ekipy widzą wersję opublikowaną; szkice mają przerywaną ramkę."
                else "Zmiany widzą ekipy od razu — publikacja tygodni jest wyłączona.",
                style = MaterialTheme.typography.bodySmall,
                color = muted,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = vm::togglePublishing, enabled = !state.saving) {
                Text(if (sch.settings.publishEnabled) "Wyłącz publikację" else "Włącz publikację")
            }
        }

        if (state.fromCache) {
            Banner("Bez zasięgu — to kopia z telefonu. Ostrzeżenia przeliczy biuro po wysłaniu zmian.", SyncBlue)
        }
        state.error?.let { Banner(it, Orange600) }
    }
}

private val SmallPad = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp)

@Composable
private fun Chip(text: String, bg: Color, fg: Color) {
    Text(
        text = text,
        color = fg,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun Banner(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

// ── Do zaplanowania ──────────────────────────────────────────────────────────

/** Cel upuszczenia: ekipa (`null` = „Bez ekipy") i dzień startu. */
internal data class DropTarget(val crewId: String?, val day: LocalDate)

/** Przeciąganie karty z listy — pozycja palca w układzie całego okna. */
internal class BacklogDragState {
    var item by mutableStateOf<ScheduleBacklogItem?>(null)
    var pointer by mutableStateOf(Offset.Zero)
    var target by mutableStateOf<DropTarget?>(null)

    /** Trafienie liczy siatka — ustawia je przy każdym rozkładzie. */
    var hitTest: (Offset) -> DropTarget? = { null }
}

@Composable
private fun BacklogStrip(
    sch: CrewSchedule,
    palette: SchedulePalette,
    busy: Boolean,
    planningDealId: String?,
    drag: BacklogDragState,
    onPlanDeal: (String, String) -> Unit,
    onOpenItem: (ScheduleBacklogItem) -> Unit,
    onOpenDeal: (String) -> Unit,
    onDrop: (ScheduleBacklogItem, DropTarget) -> Unit,
) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(
            "Do zaplanowania",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Text(
            if (sch.unplanned.isNotEmpty()) "Deala z lejka rozpisz „Zaplanuj”, montaż stuknij albo przytrzymaj i upuść na ekipę i dzień"
            else "Stuknij kartę albo przytrzymaj i upuść na ekipę i dzień",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Spacer(Modifier.height(6.dp))
        if (sch.backlog.isEmpty() && sch.unplanned.isEmpty()) {
            Text(
                "Wszystko rozpisane.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
            return@Column
        }
        LazyRow(
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(sch.unplanned, key = { "deal-${it.dealId}" }) { d ->
                UnplannedCard(d, palette, busy, planningDealId == d.dealId, onPlanDeal, onOpenDeal)
            }
            items(sch.backlog, key = { it.id }) { b ->
                BacklogCard(b, palette, drag, onOpenItem, onOpenDeal, onDrop)
            }
        }
    }
}

@Composable
private fun UnplannedCard(
    d: ScheduleUnplannedDeal,
    palette: SchedulePalette,
    busy: Boolean,
    planning: Boolean,
    onPlan: (String, String) -> Unit,
    onOpenDeal: (String) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = palette.surf2,
        border = BorderStroke(1.dp, palette.new),
        modifier = Modifier.width(210.dp),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CardTitle(d.clientName, d.city)
            Text(
                d.installationNames.takeIf { it.isNotEmpty() }?.joinToString(", ") ?: "Bez wybranych instalacji",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Tag("Brak montażu · w montażu od ${dm(d.since)}", palette.newBg, palette.new)
            Button(
                onClick = { onPlan(d.dealId, d.clientName) },
                enabled = !busy,
                contentPadding = SmallPad,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (planning) "Zakładam…"
                    else if (d.installationNames.size > 1) "Zaplanuj · ${etapy(d.installationNames.size)}"
                    else "Zaplanuj",
                )
            }
            DealLink(d.dealId, onOpenDeal)
        }
    }
}

@Composable
private fun BacklogCard(
    b: ScheduleBacklogItem,
    palette: SchedulePalette,
    drag: BacklogDragState,
    onOpen: (ScheduleBacklogItem) -> Unit,
    onOpenDeal: (String) -> Unit,
    onDrop: (ScheduleBacklogItem, DropTarget) -> Unit,
) {
    var origin by remember { mutableStateOf(Offset.Zero) }
    val item by rememberUpdatedState(b)
    val dragging = drag.item?.id == b.id
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = palette.surf2,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .width(190.dp)
            .alpha(if (dragging) 0.4f else 1f)
            .onGloballyPositioned { origin = it.positionInRoot() }
            .pointerInput(Unit) { detectTapGestures(onTap = { onOpen(item) }) }
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { at ->
                        drag.item = item
                        drag.pointer = origin + at
                        drag.target = drag.hitTest(drag.pointer)
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        drag.pointer += amount
                        drag.target = drag.hitTest(drag.pointer)
                    },
                    onDragEnd = {
                        val target = drag.hitTest(drag.pointer)
                        drag.item = null
                        drag.target = null
                        if (target != null) onDrop(item, target)
                    },
                    onDragCancel = {
                        drag.item = null
                        drag.target = null
                    },
                )
            },
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CardTitle(b.clientName, b.city)
            Text(
                "${b.title} · ${b.durationDays} dn.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (b.status == ScheduleStageStatus.RESERVED) {
                Tag(
                    "Rezerwacja: ${windowLabel(b)}" + if (b.reservationConfirmed) "" else " · wstępna",
                    palette.resBg,
                    palette.res,
                )
            } else {
                Tag("Bez ekipy · od ${dm(b.scheduledAt)}", palette.warnBg, palette.warn)
            }
            if (b.pending) Text("⏳ czeka na wysłanie", style = MaterialTheme.typography.labelSmall, color = SyncBlue)
            DealLink(b.dealId, onOpenDeal)
        }
    }
}

@Composable
private fun CardTitle(name: String, city: String?) {
    Text(
        name + (city?.let { " · $it" } ?: ""),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
internal fun Tag(text: String, bg: Color, fg: Color) {
    Text(
        text,
        color = fg,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
private fun DealLink(dealId: String, onOpenDeal: (String) -> Unit) {
    Text(
        "karta deala →",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.clickable { onOpenDeal(dealId) }.padding(vertical = 2.dp),
    )
}

// ── Siatka ───────────────────────────────────────────────────────────────────

private val NAME_W = 120.dp
private val HEAD_H = 40.dp
private val CAP_H = 24.dp
private val CREW_H = 56.dp
private val MEMBER_H = 30.dp
private val POOL_H = 40.dp

/** Szerokość kolumny dnia wg skali — gęściej przy dłuższym oknie (panel: 124/64/30 px). */
private fun dayWidth(zoom: Int): Dp = when (zoom) {
    35 -> 26.dp
    14 -> 44.dp
    else -> 64.dp
}

private sealed interface GridRow {
    val height: Dp

    data object Head : GridRow { override val height = HEAD_H }
    data object Cap : GridRow { override val height = CAP_H }
    data class Crew(val crew: ScheduleCrew) : GridRow { override val height = CREW_H }
    data class Member(val personId: String, val home: ScheduleCrew?) : GridRow { override val height = MEMBER_H }
    data object Loose : GridRow { override val height = CREW_H }
    data object Pool : GridRow { override val height = POOL_H }
}

/** Przeciągany pasek: przesunięcie w px od startu gestu. */
private data class BarDrag(val stageId: String, val resize: Boolean, val dx: Float, val target: DropTarget?)

@Composable
private fun ScheduleGrid(
    state: CrewScheduleViewModel.UiState,
    sch: CrewSchedule,
    palette: SchedulePalette,
    drag: BacklogDragState,
    onToggle: (String) -> Unit,
    onSelect: (String) -> Unit,
    onMove: (ScheduleStage, LocalDate, String?) -> Unit,
    onResize: (ScheduleStage, Int) -> Unit,
) {
    val density = LocalDensity.current
    val dayW = dayWidth(state.zoom)
    val dayPx = with(density) { dayW.toPx() }
    val start = sch.from
    val totalW = dayW * sch.days.size
    val poolPeople = pool(sch)

    val rows = buildList {
        add(GridRow.Head)
        add(GridRow.Cap)
        sch.crews.forEach { c ->
            add(GridRow.Crew(c))
            if (!c.external && c.id in state.expanded) c.memberIds.forEach { add(GridRow.Member(it, c)) }
        }
        add(GridRow.Loose)
        if (poolPeople.isNotEmpty()) {
            add(GridRow.Pool)
            if ("__pool" in state.expanded) poolPeople.forEach { add(GridRow.Member(it.id, null)) }
        }
    }

    // Położenie siatki w oknie — do trafiania palcem przy przeciąganiu.
    var cellsOrigin by remember { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(Rect.Zero) }
    var barDrag by remember { mutableStateOf<BarDrag?>(null) }

    val hitTest: (Offset) -> DropTarget? = hit@{ p ->
        if (!viewport.contains(p)) return@hit null
        val local = p - cellsOrigin
        var y = 0f
        var row: GridRow? = null
        for (r in rows) {
            val h = with(density) { r.height.toPx() }
            if (local.y >= y && local.y < y + h) {
                row = r
                break
            }
            y += h
        }
        val crewId = when (row) {
            is GridRow.Crew -> row.crew.id
            GridRow.Loose -> null
            else -> return@hit null
        }
        val idx = floor(local.x / dayPx).toInt().coerceIn(0, sch.days.size - 1)
        DropTarget(crewId, start.plusDays(idx.toLong()))
    }
    drag.hitTest = hitTest
    val hotCrew: Any? = drag.target?.let { it.crewId ?: "" } ?: barDrag?.target?.let { it.crewId ?: "" }

    val stagesByCrew = sch.stages.groupBy { s -> s.crewId?.takeIf { id -> sch.crews.any { it.id == id } } }

    Row(Modifier.padding(top = 10.dp)) {
        // Kolumna nazw — przyklejona z lewej.
        Column(Modifier.width(NAME_W)) {
            rows.forEach { r ->
                NameCell(r, sch, palette, state.expanded, poolPeople.size, hotCrew, onToggle)
            }
        }
        Box(
            Modifier
                .weight(1f)
                .onGloballyPositioned { viewport = it.boundsInRoot() }
                .horizontalScroll(rememberScrollState()),
        ) {
            Column(
                Modifier
                    .width(totalW)
                    .onGloballyPositioned { cellsOrigin = it.positionInRoot() },
            ) {
                rows.forEach { r ->
                    when (r) {
                        GridRow.Head -> HeadRow(sch, state, palette, dayW)
                        GridRow.Cap -> CapRow(sch, state.zoom, palette, dayW)
                        is GridRow.Crew -> StageRow(
                            height = r.height,
                            stages = stagesByCrew[r.crew.id].orEmpty(),
                            sch = sch,
                            state = state,
                            palette = palette,
                            dayW = dayW,
                            hot = hotCrew == r.crew.id,
                            barDrag = barDrag,
                            onBarDrag = { barDrag = it },
                            hitTest = hitTest,
                            onSelect = onSelect,
                            onMove = onMove,
                            onResize = onResize,
                        )
                        GridRow.Loose -> StageRow(
                            height = r.height,
                            stages = stagesByCrew[null].orEmpty(),
                            sch = sch,
                            state = state,
                            palette = palette,
                            dayW = dayW,
                            hot = hotCrew == "",
                            barDrag = barDrag,
                            onBarDrag = { barDrag = it },
                            hitTest = hitTest,
                            onSelect = onSelect,
                            onMove = onMove,
                            onResize = onResize,
                        )
                        GridRow.Pool -> DayCells(sch, state.today, palette, dayW, POOL_H)
                        is GridRow.Member -> PersonRow(r, sch, state, palette, dayW, onSelect)
                    }
                }
            }
        }
    }
    if (sch.crews.isEmpty()) {
        Text(
            "Brak aktywnych ekip. Załóż je w Ustawienia → Zespół → Ekipy.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
private fun NameCell(
    r: GridRow,
    sch: CrewSchedule,
    palette: SchedulePalette,
    expanded: Set<String>,
    poolSize: Int,
    hotCrew: Any?,
    onToggle: (String) -> Unit,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val base = Modifier
        .fillMaxWidth()
        .height(r.height)
        .background(MaterialTheme.colorScheme.surface)
        .drawBehind { bottomLine(palette.line) }
        .padding(horizontal = 8.dp)
    when (r) {
        GridRow.Head -> Box(base, Alignment.CenterStart) {
            Text("Ekipa / monter", style = MaterialTheme.typography.labelSmall, color = muted)
        }
        GridRow.Cap -> Box(base, Alignment.CenterStart) {
            Text("Obłożenie", style = MaterialTheme.typography.labelSmall, color = muted)
        }
        is GridRow.Crew -> {
            val c = r.crew
            val open = c.id in expanded
            Column(
                base
                    .background(if (hotCrew == c.id) palette.planned.copy(alpha = 0.14f) else Color.Transparent)
                    .clickable(enabled = !c.external) { onToggle(c.id) },
                verticalArrangement = Arrangement.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(crewColor(c.color, muted)))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        c.name,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("${crewUtil(sch, c)}%", style = MaterialTheme.typography.labelSmall, color = muted)
                }
                Text(
                    if (c.external) "zewnętrzna" else "${c.memberIds.size} os. ${if (open) "▾" else "▸"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = muted,
                )
            }
        }
        GridRow.Loose -> Column(
            base.background(if (hotCrew == "") palette.planned.copy(alpha = 0.14f) else Color.Transparent),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Bez ekipy", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Text("sama obsada imienna", style = MaterialTheme.typography.labelSmall, color = muted)
        }
        GridRow.Pool -> Column(
            base.clickable { onToggle("__pool") },
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Wolni monterzy", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "$poolSize os. poza ekipami ${if ("__pool" in expanded) "▾" else "▸"}",
                style = MaterialTheme.typography.labelSmall,
                color = muted,
            )
        }
        is GridRow.Member -> {
            val person = sch.people.firstOrNull { it.id == r.personId }
            Box(base.padding(start = 10.dp), Alignment.CenterStart) {
                Text(
                    (person?.name ?: "Monter") + if (r.home?.leaderId == r.personId) " · lider" else "",
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.bottomLine(color: Color) {
    drawLine(color, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1f)
}

/** Tło dni: weekend/święto przygaszone, dziś podbite, pionowe kreski między dniami. */
private fun Modifier.dayCells(sch: CrewSchedule, today: LocalDate, palette: SchedulePalette, dayPx: Float): Modifier =
    drawBehind {
        sch.days.forEachIndexed { i, d ->
            val x = i * dayPx
            if (!d.workday) drawRect(palette.off, Offset(x, 0f), Size(dayPx, size.height))
            if (d.date == today) drawRect(palette.planned.copy(alpha = 0.10f), Offset(x, 0f), Size(dayPx, size.height))
            drawLine(palette.line, Offset(x + dayPx - 0.5f, 0f), Offset(x + dayPx - 0.5f, size.height), 1f)
        }
        bottomLine(palette.line)
    }

@Composable
private fun DayCells(sch: CrewSchedule, today: LocalDate, palette: SchedulePalette, dayW: Dp, height: Dp) {
    val dayPx = with(LocalDensity.current) { dayW.toPx() }
    Box(Modifier.fillMaxWidth().height(height).dayCells(sch, today, palette, dayPx))
}

@Composable
private fun HeadRow(sch: CrewSchedule, state: CrewScheduleViewModel.UiState, palette: SchedulePalette, dayW: Dp) {
    val dayPx = with(LocalDensity.current) { dayW.toPx() }
    Row(Modifier.fillMaxWidth().height(HEAD_H).dayCells(sch, state.today, palette, dayPx)) {
        sch.days.forEach { d ->
            Column(
                Modifier.width(dayW).fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val color = if (d.date == state.today) palette.planned else MaterialTheme.colorScheme.onSurfaceVariant
                if (state.zoom == 35) {
                    Text("${d.date.dayOfMonth}", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color)
                } else {
                    Text(DOW[d.date.dayOfWeek.value - 1], style = MaterialTheme.typography.labelSmall, color = color)
                    Text(dm(d.date), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color)
                }
            }
        }
    }
}

@Composable
private fun CapRow(sch: CrewSchedule, zoom: Int, palette: SchedulePalette, dayW: Dp) {
    Row(Modifier.fillMaxWidth().height(CAP_H).drawBehind { bottomLine(palette.line) }) {
        sch.days.forEach { d ->
            val over = d.workday && d.load > d.limit
            Box(
                Modifier
                    .width(dayW)
                    .fillMaxHeight()
                    .background(if (!d.workday) palette.off else if (over) palette.warnBg else Color.Transparent),
                contentAlignment = Alignment.Center,
            ) {
                if (d.workday) {
                    Text(
                        if (zoom == 35) "${d.load}" else "${d.load}/${d.limit}",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        color = if (over) palette.warn else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Położenie odcinka w wierszu (px od lewej, szerokość) albo `null`, gdy poza oknem. */
private fun place(sch: CrewSchedule, a: LocalDate, b: LocalDate, dayPx: Float): Pair<Float, Float>? {
    val first = sch.from
    val last = sch.from.plusDays(sch.days.size - 1L)
    val s = if (a.isBefore(first)) first else a
    val e = if (b.isAfter(last)) last else b
    if (s.isAfter(e)) return null
    val si = java.time.temporal.ChronoUnit.DAYS.between(first, s)
    val ei = java.time.temporal.ChronoUnit.DAYS.between(first, e)
    return si * dayPx to (ei - si + 1) * dayPx
}

@Composable
private fun StageRow(
    height: Dp,
    stages: List<ScheduleStage>,
    sch: CrewSchedule,
    state: CrewScheduleViewModel.UiState,
    palette: SchedulePalette,
    dayW: Dp,
    hot: Boolean,
    barDrag: BarDrag?,
    onBarDrag: (BarDrag?) -> Unit,
    hitTest: (Offset) -> DropTarget?,
    onSelect: (String) -> Unit,
    onMove: (ScheduleStage, LocalDate, String?) -> Unit,
    onResize: (ScheduleStage, Int) -> Unit,
) {
    val density = LocalDensity.current
    val dayPx = with(density) { dayW.toPx() }
    Box(
        Modifier
            .fillMaxWidth()
            .height(height)
            .background(if (hot) palette.planned.copy(alpha = 0.14f) else Color.Transparent)
            .dayCells(sch, state.today, palette, dayPx),
    ) {
        // Przerwa technologiczna między etapami tego samego deala w tej ekipie.
        stages.forEach { s ->
            if (s.stageNo < 2) return@forEach
            val prev = sch.stages.firstOrNull { it.dealId == s.dealId && it.stageNo == s.stageNo - 1 } ?: return@forEach
            if (prev.crewId != s.crewId) return@forEach
            val a = prev.endDate.plusDays(1)
            val b = s.scheduledAt.minusDays(1)
            if (a.isAfter(b)) return@forEach
            val (left, width) = place(sch, a, b, dayPx) ?: return@forEach
            Box(
                Modifier
                    .offset { IntOffset(left.roundToInt(), 0) }
                    .width(with(density) { width.toDp() })
                    .fillMaxHeight()
                    .drawBehind {
                        drawLine(
                            palette.muted,
                            Offset(0f, size.height / 2),
                            Offset(size.width, size.height / 2),
                            strokeWidth = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx())),
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                if (state.zoom != 35) {
                    Text(
                        s.gapLabel ?: "przerwa",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        color = palette.muted,
                        maxLines = 1,
                        modifier = Modifier.background(MaterialTheme.colorScheme.surface).padding(horizontal = 3.dp),
                    )
                }
            }
        }
        stages.forEach { s ->
            StageBar(s, sch, state, palette, dayPx, barDrag, onBarDrag, hitTest, onSelect, onMove, onResize)
        }
    }
}

@Composable
private fun StageBar(
    s: ScheduleStage,
    sch: CrewSchedule,
    state: CrewScheduleViewModel.UiState,
    palette: SchedulePalette,
    dayPx: Float,
    barDrag: BarDrag?,
    onBarDrag: (BarDrag?) -> Unit,
    hitTest: (Offset) -> DropTarget?,
    onSelect: (String) -> Unit,
    onMove: (ScheduleStage, LocalDate, String?) -> Unit,
    onResize: (ScheduleStage, Int) -> Unit,
) {
    val density = LocalDensity.current
    val (left, width) = place(sch, s.scheduledAt, s.endDate, dayPx) ?: return
    // Gest żyje dłużej niż jedna kompozycja — wiersze zmieniają się po
    // rozwinięciu ekipy, więc trafienie i zapis czytamy zawsze świeże.
    val stage by rememberUpdatedState(s)
    val hit by rememberUpdatedState(hitTest)
    val dragTo by rememberUpdatedState(onBarDrag)
    val move by rememberUpdatedState(onMove)
    val resize by rememberUpdatedState(onResize)
    val select by rememberUpdatedState(onSelect)
    val mine = barDrag?.takeIf { it.stageId == s.id }
    val snapped = mine?.let { (it.dx / dayPx).roundToInt() * dayPx } ?: 0f
    val shift = if (mine != null && !mine.resize) snapped else 0f
    val stretch = if (mine != null && mine.resize) snapped else 0f
    val pad = with(density) { 2.dp.toPx() }
    val w = maxOf(dayPx - 2 * pad, width - 2 * pad + stretch)

    val (bg, fg, border) = when (s.status) {
        ScheduleStageStatus.IN_PROGRESS -> Triple(palette.progressBg, palette.progress, palette.progress.copy(alpha = 0.5f))
        ScheduleStageStatus.DONE -> Triple(palette.doneBg, palette.done, Color.Transparent)
        ScheduleStageStatus.RESERVED -> Triple(palette.resBg, palette.res, palette.res.copy(alpha = 0.45f))
        ScheduleStageStatus.PLANNED -> Triple(palette.plannedBg, palette.planned, palette.planned.copy(alpha = 0.45f))
    }
    val selected = state.selectedId == s.id
    var origin by remember { mutableStateOf(Offset.Zero) }
    var startPointer by remember { mutableStateOf(Offset.Zero) }
    var cumulative by remember { mutableStateOf(Offset.Zero) }

    Box(
        Modifier
            .offset { IntOffset((left + pad + shift).roundToInt(), 6.dp.roundToPx()) }
            .width(with(density) { w.toDp() })
            .height(CREW_H - 12.dp)
            .zIndex(if (mine != null) 10f else 2f)
            .alpha(if (mine != null && !mine.resize) 0.8f else 1f)
            .onGloballyPositioned { if (barDrag == null) origin = it.positionInRoot() }
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .drawBehind {
                val stroke = if (s.draft) {
                    Stroke(2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 3.dp.toPx())))
                } else {
                    Stroke(1.dp.toPx())
                }
                val c = when {
                    selected -> fg
                    s.draft -> palette.planned
                    else -> border
                }
                drawRoundRect(c, style = stroke, cornerRadius = CornerRadius(6.dp.toPx()))
            }
            .pointerInput(s.id) { detectTapGestures(onTap = { select(stage.id) }) }
            .pointerInput(s.id, dayPx) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { at ->
                        startPointer = origin + at
                        cumulative = Offset.Zero
                        dragTo(BarDrag(stage.id, resize = false, dx = 0f, target = hit(startPointer)))
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        cumulative += amount
                        dragTo(
                            BarDrag(stage.id, resize = false, dx = cumulative.x, target = hit(startPointer + cumulative)),
                        )
                    },
                    onDragEnd = {
                        val delta = (cumulative.x / dayPx).roundToInt()
                        val target = hit(startPointer + cumulative)
                        dragTo(null)
                        val crew = if (target != null) target.crewId else stage.crewId
                        if (delta != 0 || crew != stage.crewId) {
                            move(stage, stage.scheduledAt.plusDays(delta.toLong()), crew)
                        }
                    },
                    onDragCancel = { dragTo(null) },
                )
            },
    ) {
        Column(Modifier.padding(start = 6.dp, end = 12.dp, top = 3.dp)) {
            Text(
                (if (s.pending) "⏳ " else "") + s.clientName + (s.city?.let { " · $it" } ?: ""),
                color = fg,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                style = NoPad,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
            if (state.zoom != 35) {
                Text(
                    s.title + (if (s.stageCount > 1) " ${s.stageNo}/${s.stageCount}" else "") +
                        " · ${s.durationDays} dn." + if (s.locked) " · 🔒" else "",
                    color = fg.copy(alpha = 0.85f),
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    style = NoPad,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                )
            }
        }
        if (s.warnings.isNotEmpty()) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 2.dp, end = 12.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(palette.warn),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "!",
                    color = Color.White,
                    fontSize = 11.sp,
                    lineHeight = 11.sp,
                    style = NoPad,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        // Uchwyt długości — ciągnięcie bez przytrzymania, jak w panelu.
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .width(12.dp)
                .fillMaxHeight()
                .pointerInput(s.id, dayPx) {
                    var dx = 0f
                    detectHorizontalDragGestures(
                        onDragStart = {
                            dx = 0f
                            dragTo(BarDrag(stage.id, resize = true, dx = 0f, target = null))
                        },
                        onHorizontalDrag = { change, amount ->
                            change.consume()
                            dx += amount
                            dragTo(BarDrag(stage.id, resize = true, dx = dx, target = null))
                        },
                        onDragEnd = {
                            dragTo(null)
                            val delta = (dx / dayPx).roundToInt()
                            if (delta != 0) resize(stage, delta)
                        },
                        onDragCancel = { dragTo(null) },
                    )
                }
                .drawBehind {
                    drawLine(
                        fg.copy(alpha = 0.45f),
                        Offset(size.width - 5.dp.toPx(), 10.dp.toPx()),
                        Offset(size.width - 5.dp.toPx(), size.height - 10.dp.toPx()),
                        strokeWidth = 2.dp.toPx(),
                    )
                },
        )
    }
}

@Composable
private fun PersonRow(
    r: GridRow.Member,
    sch: CrewSchedule,
    state: CrewScheduleViewModel.UiState,
    palette: SchedulePalette,
    dayW: Dp,
    onSelect: (String) -> Unit,
) {
    val density = LocalDensity.current
    val dayPx = with(density) { dayW.toPx() }
    val pid = r.personId
    Box(
        Modifier
            .fillMaxWidth()
            .height(MEMBER_H)
            .dayCells(sch, state.today, palette, dayPx),
    ) {
        sch.leaves.filter { it.userId == pid }.forEach { l ->
            val (left, width) = place(sch, l.start, l.end, dayPx) ?: return@forEach
            Box(
                Modifier
                    .offset { IntOffset(left.roundToInt(), 4.dp.roundToPx()) }
                    .width(with(density) { width.toDp() })
                    .height(MEMBER_H - 8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .drawBehind {
                        clipRect {
                            val step = 8.dp.toPx()
                            var x = -size.height
                            while (x < size.width) {
                                drawLine(palette.leave, Offset(x, size.height), Offset(x + size.height, 0f), 4.dp.toPx() * 0.5f)
                                x += step
                            }
                        }
                    },
                contentAlignment = Alignment.CenterStart,
            ) {
                if (state.zoom != 35) {
                    Text(
                        l.label,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
        sch.stages.filter { s -> s.assignees.any { it.userId == pid } }.forEach { s ->
            val (left, width) = place(sch, s.scheduledAt, s.endDate, dayPx) ?: return@forEach
            val crew = s.crewId?.let { id -> sch.crews.firstOrNull { it.id == id } }
            val borrowed = crew != null && crew.id != r.home?.id
            val clash = s.warnings.any { w -> w.userId == pid && (w.code == "leave" || w.code == "double_booking") }
            val c = crewColor(crew?.color, palette.muted)
            Box(
                Modifier
                    .offset { IntOffset(left.roundToInt(), 5.dp.roundToPx()) }
                    .width(with(density) { width.toDp() })
                    .height(MEMBER_H - 10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(c.copy(alpha = 0.22f))
                    .border(1.dp, if (clash) palette.warn else c.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                    .clickable { onSelect(s.id) },
                contentAlignment = Alignment.CenterStart,
            ) {
                if (state.zoom != 35) {
                    Text(
                        s.clientName + if (borrowed) " ↪ ${crew?.name}" else "",
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend(publishEnabled: Boolean, palette: SchedulePalette) {
    FlowRow(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LegendItem("Zaplanowany", palette.plannedBg, palette.planned)
        LegendItem("W trakcie", palette.progressBg, palette.progress)
        LegendItem("Zakończony", palette.doneBg, palette.done)
        if (publishEnabled) LegendItem("Szkic", Color.Transparent, palette.planned, dashed = true)
        LegendItem("Urlop", palette.leave.copy(alpha = 0.5f), palette.leave)
        LegendItem("Przerwa technologiczna", Color.Transparent, palette.muted, dashed = true)
        LegendItem("Czeka na wysłanie", Color.Transparent, SyncBlue, text = "⏳")
    }
}

@Composable
private fun LegendItem(label: String, bg: Color, fg: Color, dashed: Boolean = false, text: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(width = 16.dp, height = 10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(bg)
                .drawBehind {
                    if (text == null) {
                        drawRoundRect(
                            fg,
                            style = Stroke(
                                1.5.dp.toPx(),
                                pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx())) else null,
                            ),
                            cornerRadius = CornerRadius(3.dp.toPx()),
                        )
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (text != null) Text(text, fontSize = 8.sp, color = fg)
        }
        Spacer(Modifier.width(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Kolor ekipy z modułu Zespół (`#rrggbb`); brak albo śmieć = wyciszony. */
internal fun crewColor(hex: String?, fallback: Color): Color =
    hex?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() } ?: fallback


/**
 * Tekst bez dodatkowego marginesu fontu: w pasku o stałej wysokości Android
 * dokłada nad i pod literami kilka pikseli i drugi wiersz wypadał poza pasek.
 */
private val NoPad = androidx.compose.ui.text.TextStyle(
    platformStyle = androidx.compose.ui.text.PlatformTextStyle(includeFontPadding = false),
)
