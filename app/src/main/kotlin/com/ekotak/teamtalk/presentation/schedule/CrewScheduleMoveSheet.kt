package com.ekotak.teamtalk.presentation.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ekotak.teamtalk.domain.model.CrewSchedule
import com.ekotak.teamtalk.domain.model.PersonMove
import com.ekotak.teamtalk.domain.model.ScheduleCalendar
import com.ekotak.teamtalk.domain.model.ScheduleStageStatus
import com.ekotak.teamtalk.domain.model.moveConflicts
import com.ekotak.teamtalk.domain.model.presence
import com.ekotak.teamtalk.domain.model.workdays

/**
 * Arkusz przeniesienia osoby do innej ekipy — 1:1 z okienkiem panelu
 * (`MoveDialog.tsx`, decyzje usera 2026-09-24):
 *  • montaż docelowy (upuszczony pasek albo wybór z listy po „Przenieś do…”),
 *  • cały montaż ALBO wybrane dni,
 *  • pytanie o etapy, na których osoba jest w tych dniach — za każdym razem,
 *  • przypomnienie, że to szkic do „Opublikuj tydzień”.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun MoveSheet(
    req: CrewScheduleViewModel.MoveRequest,
    sch: CrewSchedule,
    busy: Boolean,
    onClose: () -> Unit,
    onConfirm: (PersonMove) -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val cal = remember(sch.days) { ScheduleCalendar(sch.days) }
    val name = sch.people.firstOrNull { it.id == req.userId }?.name ?: "Monter"
    val crewName = { id: String? -> id?.let { c -> sch.crews.firstOrNull { it.id == c }?.name } ?: "bez ekipy" }
    val from = req.fromId?.let { id -> sch.stages.firstOrNull { it.id == id } }

    // Wejście z arkusza etapu: cele = montaże INNYCH ekip w tym samym czasie.
    val candidates = remember(req, sch.stages) {
        if (req.targetId != null) {
            emptyList()
        } else {
            sch.stages.filter { s ->
                s.id != from?.id &&
                    (s.status == ScheduleStageStatus.PLANNED || s.status == ScheduleStageStatus.IN_PROGRESS) &&
                    s.crewId != from?.crewId &&
                    (from == null || (!s.scheduledAt.isAfter(from.endDate) && !s.endDate.isBefore(from.scheduledAt)))
            }.sortedBy { it.scheduledAt }
        }
    }

    var targetId by remember(req) { mutableStateOf(req.targetId) }
    val target = targetId?.let { id -> sch.stages.firstOrNull { it.id == id } }
    val tDays = remember(target, cal) { target?.workdays(cal).orEmpty() }
    // Upuszczenie na konkretny dzień = domyślnie tylko ten dzień.
    val dropDay = req.day?.takeIf { it in tDays }
    var whole by remember(targetId) { mutableStateOf(dropDay == null || tDays.size == 1) }
    var picked by remember(targetId) { mutableStateOf(if (dropDay != null) listOf(dropDay) else tDays) }

    val moved = if (whole) tDays else tDays.filter { it in picked }
    val already = target?.assignees?.firstOrNull { it.userId == req.userId }
    val alreadyDays = already?.let { presence(tDays, it) }.orEmpty()
    val nothingNew = already != null && moved.all { it in alreadyDays }
    val conflicts = if (target != null) moveConflicts(sch, req.userId, target.id, moved) else emptyList()
    val ask = target != null && conflicts.isNotEmpty() && !nothingNew && moved.isNotEmpty()

    fun confirm(removeFrom: List<String>) {
        val t = target ?: return
        if (moved.isEmpty()) return
        onConfirm(PersonMove(req.userId, t.id, if (whole) null else moved, removeFrom))
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
                Text("Przenieś: $name", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                from?.let {
                    Note("z: ${it.clientName} · ${crewName(it.crewId)}, ${range(it.scheduledAt, it.endDate)}")
                }
            }

            if (req.targetId == null) {
                Section("Na montaż") {
                    if (candidates.isEmpty()) {
                        Note(
                            "W tym czasie inne ekipy nie mają montaży na osi. Przytrzymaj osobę w składzie " +
                                "ekipy i upuść ją na pasek montażu w innym terminie.",
                        )
                    }
                    candidates.forEach { s ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { targetId = s.id },
                        ) {
                            RadioButton(selected = targetId == s.id, onClick = { targetId = s.id })
                            Column {
                                Text("${crewName(s.crewId)} · ${s.clientName}", style = MaterialTheme.typography.bodyMedium)
                                Note("${s.title} · ${range(s.scheduledAt, s.endDate)}")
                            }
                        }
                    }
                }
            } else if (target != null) {
                Section("Na montaż") {
                    Text("${crewName(target.crewId)} · ${target.clientName}", style = MaterialTheme.typography.bodyMedium)
                    Note("${target.title} · ${range(target.scheduledAt, target.endDate)}")
                }
            }

            if (target != null) {
                Section("Kiedy") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable { whole = true },
                    ) {
                        RadioButton(selected = whole, onClick = { whole = true })
                        Text(
                            "Cały montaż (${range(target.scheduledAt, target.endDate)})",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (tDays.size > 1) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { whole = false },
                        ) {
                            RadioButton(selected = !whole, onClick = { whole = false })
                            Text("Wybrane dni", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    if (!whole) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            tDays.forEach { d ->
                                val on = d in picked
                                FilterChip(
                                    selected = on,
                                    onClick = { picked = if (on) picked - d else picked + d },
                                    label = { Text("${DOW[d.dayOfWeek.value - 1]} ${dm(d)}") },
                                )
                            }
                        }
                    }
                    if (nothingNew && moved.isNotEmpty()) Note("$name jest już na tym montażu w te dni.")
                }

                if (ask) {
                    Section("Zdjąć z innych montaży?") {
                        conflicts.forEach { c ->
                            Text(
                                "• ${c.stage.clientName} · ${crewName(c.stage.crewId)} — " +
                                    c.days.joinToString(", ") { dm(it) },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Note("„Nie” zostawia tę osobę w obu miejscach — harmonogram pokaże ostrzeżenie.")
                    }
                }
            }

            if (sch.settings.publishEnabled) Note("Ekipy zobaczą zmianę po „Opublikuj tydzień”.")

            if (ask) {
                Button(
                    onClick = { confirm(conflicts.map { it.stage.id }) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Tak, przenieś") }
                OutlinedButton(
                    onClick = { confirm(emptyList()) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Nie, zostaw w obu") }
            } else {
                Button(
                    onClick = { confirm(emptyList()) },
                    enabled = !busy && target != null && moved.isNotEmpty() && !nothingNew,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Przenieś") }
            }
            TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("Anuluj") }
            Spacer(Modifier.height(12.dp))
        }
    }
}
