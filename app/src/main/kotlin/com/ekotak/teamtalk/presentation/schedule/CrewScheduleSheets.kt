package com.ekotak.teamtalk.presentation.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ekotak.teamtalk.domain.model.Change
import com.ekotak.teamtalk.domain.model.CrewSchedule
import com.ekotak.teamtalk.domain.model.ScheduleBacklogItem
import com.ekotak.teamtalk.domain.model.ScheduleCalendar
import com.ekotak.teamtalk.domain.model.SchedulePerson
import com.ekotak.teamtalk.domain.model.ScheduleStage
import com.ekotak.teamtalk.domain.model.ScheduleStageStatus
import com.ekotak.teamtalk.domain.model.StageAssignee
import com.ekotak.teamtalk.domain.model.StagePatch
import com.ekotak.teamtalk.domain.model.pickerCrews
import com.ekotak.teamtalk.presentation.theme.SyncBlue
import java.time.LocalDate

// ── Paleta ───────────────────────────────────────────────────────────────────

/** Kolory osi z `schedule.module.css` panelu — Noc (domyślny) i Dzień. */
@Immutable
internal data class SchedulePalette(
    val surf2: Color,
    val line: Color,
    val off: Color,
    val muted: Color,
    val planned: Color,
    val plannedBg: Color,
    val progress: Color,
    val progressBg: Color,
    val done: Color,
    val doneBg: Color,
    val warn: Color,
    val warnBg: Color,
    val res: Color,
    val resBg: Color,
    val new: Color,
    val newBg: Color,
    val leave: Color,
)

@Composable
internal fun schedulePalette(): SchedulePalette {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    return if (dark) {
        SchedulePalette(
            surf2 = Color(0xFF171D28),
            line = Color.White.copy(alpha = 0.08f),
            off = Color.White.copy(alpha = 0.035f),
            muted = muted,
            planned = Color(0xFF7FA9FF), plannedBg = Color(0xFF1B2A47),
            progress = Color(0xFF6FE25A), progressBg = Color(0xFF16301A),
            done = Color(0xFF8D99A8), doneBg = Color(0xFF1F2530),
            warn = Color(0xFFFB923C), warnBg = Color(0xFF3B2213),
            res = Color(0xFFF0B35A), resBg = Color(0xFF3A2C14),
            new = Color(0xFFC4B5FD), newBg = Color(0xFF2A2247),
            leave = Color(0xFF6B7785),
        )
    } else {
        SchedulePalette(
            surf2 = Color(0xFFF5F8FB),
            line = Color(0xFF080808).copy(alpha = 0.10f),
            off = Color(0xFF0F1720).copy(alpha = 0.045f),
            muted = muted,
            planned = Color(0xFF2F6FDB), plannedBg = Color(0xFFDCE8FC),
            progress = Color(0xFF2E8A1C), progressBg = Color(0xFFDCF5D6),
            done = Color(0xFF5F6B78), doneBg = Color(0xFFE6EAEF),
            warn = Color(0xFFC2410C), warnBg = Color(0xFFFFEDD5),
            res = Color(0xFF9A6412), resBg = Color(0xFFFBEFD9),
            new = Color(0xFF6D28D9), newBg = Color(0xFFEDE9FE),
            leave = Color(0xFF8B97A6),
        )
    }
}

// ── Szuflada etapu (StageDrawer panelu) ──────────────────────────────────────

/**
 * Karta montażu na osi: etapy deala, termin, ekipa, skład i ostrzeżenia.
 * Skład zmienia się TYLKO na tym etapie — stały skład ekipy zostaje nietknięty
 * (decyzja usera 2026-09-22), dlatego zamiana pisze `assignees` montażu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StageSheet(
    stage: ScheduleStage,
    sch: CrewSchedule,
    busy: Boolean,
    palette: SchedulePalette,
    onPick: (String) -> Unit,
    onClose: () -> Unit,
    onPatch: (String, StagePatch) -> Unit,
    onPickCrew: (String, String?) -> Unit,
    onShift: (String, Int) -> Unit,
    onUnassign: (String) -> Unit,
    onOpenDeal: (String) -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val nameOf = { id: String -> sch.people.firstOrNull { it.id == id }?.name ?: "Monter" }
    val crewOf = { id: String? -> id?.let { c -> sch.crews.firstOrNull { it.id == c } } }
    val crew = crewOf(stage.crewId)
    val siblings = sch.stages.filter { it.dealId == stage.dealId }.sortedBy { it.stageNo }
    val team = stage.assignees.map { it.userId }
    val free = sch.people.filter { it.id !in team }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    fun personLabel(p: SchedulePerson): String {
        val c = p.crewIds.firstOrNull()?.let { id -> sch.crews.firstOrNull { it.id == id }?.name }
        return if (c != null) "${p.name} ($c)" else p.name
    }

    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheet) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column {
                Text(
                    stage.clientName + (stage.city?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stage.status.label + (if (busy) " · zapisuję…" else "") +
                        if (stage.pending) " · czeka na wysłanie" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (stage.pending) SyncBlue else muted,
                )
            }

            if (siblings.size > 1) {
                Section("Etapy") {
                    siblings.forEach { s ->
                        val cur = s.id == stage.id
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (cur) palette.plannedBg else palette.surf2)
                                .clickable { onPick(s.id) }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text("${s.stageNo}. ${s.title}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${range(s.scheduledAt, s.endDate)} · ${crewOf(s.crewId)?.name ?: "bez ekipy"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = muted,
                            )
                        }
                    }
                    if (stage.stageCount > siblings.size) Note("Pozostałe etapy są poza widocznym okresem.")
                }
            }

            Section(stage.title) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Stepper(
                        label = range(stage.scheduledAt, stage.endDate),
                        onMinus = { onShift(stage.id, -1) },
                        onPlus = { onShift(stage.id, 1) },
                        modifier = Modifier.weight(1.3f),
                    )
                    Stepper(
                        label = "${stage.durationDays} dn.",
                        onMinus = { onPatch(stage.id, StagePatch(durationDays = maxOf(1, stage.durationDays - 1))) },
                        onPlus = { onPatch(stage.id, StagePatch(durationDays = stage.durationDays + 1)) },
                        minusText = "−",
                        plusText = "+",
                        modifier = Modifier.weight(1f),
                    )
                }
                Picker(
                    label = "Ekipa",
                    value = crew?.let { it.name + if (it.external) " (zewnętrzna)" else "" } ?: "bez ekipy",
                    options = listOf<Pair<String?, String>>(null to "bez ekipy") +
                        pickerCrews(sch.crews).map { it.id to (it.name + if (it.external) " (zewnętrzna)" else "") },
                    onSelect = { onPickCrew(stage.id, it) },
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onPatch(stage.id, StagePatch(locked = !stage.locked)) },
                ) {
                    Checkbox(checked = stage.locked, onCheckedChange = { onPatch(stage.id, StagePatch(locked = it)) })
                    Text("Termin uzgodniony z klientem", style = MaterialTheme.typography.bodyMedium)
                }
                if (stage.requiredRoles.isNotEmpty()) Note("Wymagane role: ${stage.requiredRoles.joinToString(", ")}")
            }

            if (stage.stageNo > 1) {
                Section("Przerwa przed etapem") {
                    GapEditor(stage, sch.settings.defaultGapDays, onPatch)
                }
            }

            Section("Skład na ten etap" + (crew?.let { " · ${it.name}" } ?: "")) {
                if (stage.assignees.isEmpty()) {
                    Note(
                        if (crew?.external == true) "Ekipa zewnętrzna jedzie własnym składem. Możesz dopisać naszego montera."
                        else "Bez imiennej obsady.",
                    )
                } else {
                    stage.assignees.forEach { a ->
                        val warn = stage.warnings.firstOrNull { it.userId == a.userId }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    nameOf(a.userId) + (a.role?.let { " · $it" } ?: ""),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                warn?.let { Text(it.message, style = MaterialTheme.typography.bodySmall, color = palette.warn) }
                            }
                            MenuButton(
                                text = "Zamień na…",
                                options = free.map { it.id to personLabel(it) },
                                onSelect = { into ->
                                    onPatch(
                                        stage.id,
                                        StagePatch(
                                            assignees = stage.assignees.map {
                                                if (it.userId == a.userId) StageAssignee(into, it.role) else it
                                            },
                                        ),
                                    )
                                },
                            )
                            TextButton(
                                onClick = {
                                    onPatch(stage.id, StagePatch(assignees = stage.assignees.filter { it.userId != a.userId }))
                                },
                            ) { Text("×") }
                        }
                    }
                }
                MenuButton(
                    text = "+ Dopisz montera…",
                    options = free.map { it.id to personLabel(it) },
                    onSelect = { id -> onPatch(stage.id, StagePatch(assignees = stage.assignees + StageAssignee(id, null))) },
                )
                Note("Zmiana dotyczy tylko tego etapu; stały skład ekipy zostaje.")
            }

            if (stage.warnings.isNotEmpty()) {
                Section("Ostrzeżenia") {
                    stage.warnings.forEach { w ->
                        Row(verticalAlignment = Alignment.Top) {
                            Box(Modifier.padding(top = 6.dp).size(6.dp).clip(CircleShape).background(palette.warn))
                            Spacer(Modifier.width(8.dp))
                            Text(w.message, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    if (stage.pending) Note("Ostrzeżenia przeliczy serwer, gdy zmiana dotrze do biura.")
                }
            }

            if (sch.settings.publishEnabled && stage.draft) {
                Section("Szkic") {
                    val p = stage.published
                    Note(
                        if (p != null) "Ekipy widzą jeszcze: ${range(p.scheduledAt, p.endDate)}, ${crewOf(p.crewId)?.name ?: "bez ekipy"}."
                        else "Ekipy jeszcze nie widzą tego montażu.",
                    )
                }
            }

            HorizontalDivider()
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { onUnassign(stage.id) }) { Text("Zdejmij z osi") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onOpenDeal(stage.dealId) }) { Text("Karta deala →") }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun GapEditor(stage: ScheduleStage, defaultGapDays: Int, onPatch: (String, StagePatch) -> Unit) {
    var open by remember(stage.id) { mutableStateOf(stage.minGapDays != null) }
    var days by remember(stage.id, stage.minGapDays) { mutableStateOf((stage.minGapDays ?: defaultGapDays).toString()) }
    var label by remember(stage.id, stage.gapLabel) { mutableStateOf(stage.gapLabel.orEmpty()) }

    if (!open) {
        TextButton(onClick = { open = true }) { Text("+ Wymagaj przerwy (np. wylewka)") }
        return
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = days,
            onValueChange = { v -> days = v.filter(Char::isDigit).take(3) },
            label = { Text("dni rob.") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(96.dp),
        )
        OutlinedTextField(
            value = label,
            onValueChange = { label = it },
            label = { Text("nazwa, np. wylewka") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
    Row {
        Button(
            onClick = {
                val n = days.toIntOrNull()?.coerceIn(0, 120) ?: return@Button
                val text = label.trim().ifEmpty { null }
                onPatch(
                    stage.id,
                    StagePatch(
                        minGapDays = if (n != stage.minGapDays) Change(n) else null,
                        gapLabel = if (text != stage.gapLabel) Change(text) else null,
                    ),
                )
            },
        ) { Text("Zapisz przerwę") }
        if (stage.minGapDays != null) {
            TextButton(
                onClick = {
                    open = false
                    onPatch(stage.id, StagePatch(minGapDays = Change(null), gapLabel = Change(null)))
                },
            ) { Text("Usuń wymóg") }
        }
    }
}

// ── Arkusz „Do zaplanowania" → oś ────────────────────────────────────────────

/**
 * Stuknięta karta z listy: wybór ekipy i dnia startu. To samo, co upuszczenie
 * karty na wiersz w panelu — tylko bez celowania palcem w wąską kolumnę dnia.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PlanSheet(
    item: ScheduleBacklogItem,
    sch: CrewSchedule,
    today: LocalDate,
    onClose: () -> Unit,
    onPlan: (String?, LocalDate) -> Unit,
    onOpenDeal: (String) -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val cal = remember(sch.days) { ScheduleCalendar(sch.days) }
    var crewId by remember(item.id) { mutableStateOf(pickerCrews(sch.crews).firstOrNull()?.id) }
    var day by remember(item.id) {
        val base = listOf(item.scheduledAt, today, sch.from).max()
        mutableStateOf(cal.nextWork(base))
    }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheet) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                item.clientName + (item.city?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                "${item.title} · ${item.durationDays} dn." +
                    if (item.status == ScheduleStageStatus.RESERVED) " · rezerwacja: ${windowLabel(item)}" else "",
                style = MaterialTheme.typography.bodySmall,
                color = muted,
            )

            Section("Start") {
                Stepper(
                    label = "${DOW[day.dayOfWeek.value - 1]} ${dm(day)}.${day.year}",
                    onMinus = { day = cal.nextWork(day.minusDays(1), -1) },
                    onPlus = { day = cal.nextWork(day.plusDays(1)) },
                )
                Note("Koniec: ${dm(cal.endOf(day, item.durationDays))} (${item.durationDays} dni rob.)")
            }

            Section("Ekipa") {
                (pickerCrews(sch.crews).map { it.id to (it.name + if (it.external) " (zewnętrzna)" else "") } +
                    listOf<Pair<String?, String>>(null to "Bez ekipy")).forEach { (id, name) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { crewId = id },
                    ) {
                        RadioButton(selected = crewId == id, onClick = { crewId = id })
                        Text(name, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                if (crewId == null) Note("Bez ekipy i bez obsady pozycja zostanie na liście „Do zaplanowania”.")
            }

            Button(onClick = { onPlan(crewId, day) }, modifier = Modifier.fillMaxWidth()) { Text("Wstaw na oś") }
            TextButton(onClick = { onOpenDeal(item.dealId) }) { Text("Karta deala →") }
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ── Klocki arkuszy ───────────────────────────────────────────────────────────

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
        content()
    }
}

@Composable
private fun Note(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Stepper(
    label: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
    minusText: String = "‹",
    plusText: String = "›",
) {
    Row(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onMinus) { Text(minusText, style = MaterialTheme.typography.titleMedium) }
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        TextButton(onClick = onPlus) { Text(plusText, style = MaterialTheme.typography.titleMedium) }
    }
}

@Composable
private fun Picker(
    label: String,
    value: String,
    options: List<Pair<String?, String>>,
    onSelect: (String?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: $value", maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("▾")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = {
                    open = false
                    onSelect(id)
                })
            }
        }
    }
}

@Composable
private fun MenuButton(text: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { open = true }, enabled = options.isNotEmpty()) { Text(text) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (id, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = {
                    open = false
                    onSelect(id)
                })
            }
        }
    }
}
