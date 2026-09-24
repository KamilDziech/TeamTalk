package com.ekotak.teamtalk.presentation.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.ekotak.teamtalk.domain.model.BlockReason
import com.ekotak.teamtalk.domain.model.BlockScope
import com.ekotak.teamtalk.domain.model.CrewSchedule
import com.ekotak.teamtalk.domain.model.ScheduleBlock
import com.ekotak.teamtalk.domain.model.ScheduleBlockInput
import com.ekotak.teamtalk.domain.model.pickerCrews
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * DNI NIEAKTYWNE I BLOKADY — decyzje usera 2026-09-24, 1:1 z panelem.
 *
 *  - sobota, niedziela i święta są wolne; pojedynczej ekipie można włączyć
 *    pracę w konkretny dzień wolny (też w dzień blokady firmy — wyjątek wygrywa),
 *  - blokada całej firmy (bez ekip zewnętrznych), ekipy albo osoby,
 *  - montaż, który trafia na dzień wolny, przeskakuje go i kończy się później.
 *
 * Wszystko tylko w zasięgu — skutek (przesunięte końce montaży) liczy serwer.
 */

/** Arkusz dnia ekipy stukniętego na osi: „pracujemy" albo blokada. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DaySheet(
    sch: CrewSchedule,
    pick: CrewScheduleViewModel.DayPick,
    busy: Boolean,
    onClose: () -> Unit,
    onWorkday: (Boolean) -> Unit,
    onBlock: (BlockScope) -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val crew = sch.crews.firstOrNull { it.id == pick.crewId }
    val cal = sch.calendar
    val own = cal.crewDay(pick.crewId, pick.day)
    val base = sch.days.firstOrNull { it.date == pick.day }
    val work = cal.isWork(pick.day, pick.crewId)
    val crewBlocked = sch.blocks.any {
        it.scope == BlockScope.CREW && it.crewId == pick.crewId && !it.removed &&
            !pick.day.isBefore(it.start) && !pick.day.isAfter(it.end)
    }
    val status = when {
        own?.exception == true && work -> "Ekipa pracuje (${base?.label ?: "dzień wolny"})"
        work -> "Dzień roboczy"
        else -> own?.label ?: base?.label ?: "Dzień wolny"
    }

    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheet) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                "${crew?.name ?: "Ekipa"} · ${DOW[pick.day.dayOfWeek.value - 1]} ${dm(pick.day)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Note(status)
            when {
                own?.exception == true -> Button(onClick = { onWorkday(false) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Zdejmij pracę w ten dzień")
                }
                !work && !crewBlocked -> Button(onClick = { onWorkday(true) }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text("Ekipa pracuje w ten dzień")
                }
                else -> Unit
            }
            OutlinedButton(onClick = { onBlock(BlockScope.CREW) }, modifier = Modifier.fillMaxWidth()) {
                Text("Zablokuj ekipie…")
            }
            OutlinedButton(onClick = { onBlock(BlockScope.COMPANY) }, modifier = Modifier.fillMaxWidth()) {
                Text("Zablokuj całej firmie…")
            }
            if (sch.settings.publishEnabled) Note("Ekipa zobaczy zmianę po „Opublikuj tydzień”.")
        }
    }
}

/** Arkusz „Blokady dni": lista w oknie osi + formularz nowej albo zmienianej. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BlocksSheet(
    sch: CrewSchedule,
    form: CrewScheduleViewModel.BlockForm?,
    defaultDay: LocalDate,
    busy: Boolean,
    onClose: () -> Unit,
    onEdit: (CrewScheduleViewModel.BlockForm?) -> Unit,
    onSave: (CrewScheduleViewModel.BlockForm) -> Unit,
    onDelete: (ScheduleBlock) -> Unit,
    onRestore: (ScheduleBlock) -> Unit,
) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var confirmDelete by remember { mutableStateOf<ScheduleBlock?>(null) }
    val crewName = { id: String? -> sch.crews.firstOrNull { it.id == id }?.name ?: "ekipa" }
    val personName = { id: String? -> sch.people.firstOrNull { it.id == id }?.name ?: "osoba" }

    ModalBottomSheet(onDismissRequest = onClose, sheetState = sheet) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Blokady dni", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Note("Szkolenie, targi i inne dni bez montaży" + if (busy) " · zapisuję…" else "")

            if (form != null) {
                BlockFormFields(sch, form, busy, onChange = onEdit, onSave = onSave, onCancel = { onEdit(null) })
            } else {
                Button(
                    onClick = {
                        onEdit(
                            CrewScheduleViewModel.BlockForm(
                                id = null,
                                input = ScheduleBlockInput(
                                    BlockScope.COMPANY, null, null, defaultDay, defaultDay, BlockReason.TRAINING, null,
                                ),
                            ),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("+ Nowa blokada") }
            }

            Section("W widocznym okresie") {
                if (sch.blocks.isEmpty()) Note("Brak blokad.")
                sch.blocks.sortedBy { it.start }.forEach { b ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                b.label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                textDecoration = if (b.removed) TextDecoration.LineThrough else null,
                            )
                            val whom = when (b.scope) {
                                BlockScope.COMPANY -> "Cała firma"
                                BlockScope.CREW -> crewName(b.crewId)
                                BlockScope.USER -> personName(b.userId)
                            }
                            val state = if (sch.settings.publishEnabled && b.draft) {
                                if (b.removed) " · usunięta (szkic)" else " · szkic"
                            } else {
                                ""
                            }
                            Note("$whom · ${range(b.start, b.end)}$state")
                        }
                        if (b.removed) {
                            TextButton(onClick = { onRestore(b) }, enabled = !busy) { Text("Przywróć") }
                        } else {
                            TextButton(
                                onClick = {
                                    onEdit(
                                        CrewScheduleViewModel.BlockForm(
                                            b.id,
                                            ScheduleBlockInput(b.scope, b.crewId, b.userId, b.start, b.end, b.reason, b.note),
                                        ),
                                    )
                                },
                                enabled = !busy,
                            ) { Text("Zmień") }
                            TextButton(onClick = { confirmDelete = b }, enabled = !busy) { Text("Usuń") }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    confirmDelete?.let { b ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = null },
            text = { Text("Usunąć blokadę „${b.label}” (${range(b.start, b.end)})?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    onDelete(b)
                }) { Text("Usuń") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Anuluj") } },
        )
    }
}

@Composable
private fun BlockFormFields(
    sch: CrewSchedule,
    form: CrewScheduleViewModel.BlockForm,
    busy: Boolean,
    onChange: (CrewScheduleViewModel.BlockForm) -> Unit,
    onSave: (CrewScheduleViewModel.BlockForm) -> Unit,
    onCancel: () -> Unit,
) {
    val i = form.input
    val set = { next: ScheduleBlockInput -> onChange(form.copy(input = next)) }

    Section(if (form.id == null) "Nowa blokada" else "Zmień blokadę") {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BlockScope.entries.forEach { s ->
                FilterChip(
                    selected = i.scope == s,
                    onClick = { set(i.copy(scope = s)) },
                    label = { Text(s.label) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        when (i.scope) {
            BlockScope.CREW -> Picker(
                label = "Ekipa",
                value = sch.crews.firstOrNull { it.id == i.crewId }?.name ?: "wybierz",
                options = pickerCrews(sch.crews).map { it.id to (it.name + if (it.external) " (zewnętrzna)" else "") },
                onSelect = { set(i.copy(crewId = it)) },
            )
            BlockScope.USER -> Picker(
                label = "Osoba",
                value = sch.people.firstOrNull { it.id == i.userId }?.name ?: "wybierz",
                options = sch.people.sortedBy { it.name.lowercase() }.map { it.id to it.name },
                onSelect = { set(i.copy(userId = it)) },
            )
            BlockScope.COMPANY -> Unit
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateField("Od", i.start, Modifier.weight(1f)) { d -> set(i.copy(start = d, end = if (i.end.isBefore(d)) d else i.end)) }
            DateField("Do", i.end, Modifier.weight(1f)) { d -> set(i.copy(end = if (d.isBefore(i.start)) i.start else d)) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            BlockReason.entries.forEach { r ->
                FilterChip(
                    selected = i.reason == r,
                    onClick = { set(i.copy(reason = r)) },
                    label = { Text(r.label) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        OutlinedTextField(
            value = i.note.orEmpty(),
            onValueChange = { set(i.copy(note = it.take(200).ifEmpty { null })) },
            label = { Text("Opis") },
            placeholder = { Text(if (i.reason == BlockReason.OTHER) "np. inwentaryzacja" else "np. BHP, Viessmann") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Note(
            when (i.scope) {
                BlockScope.COMPANY -> "Zatrzymuje nasze ekipy (zewnętrznych nie). Montaże na te dni przesuną koniec."
                BlockScope.CREW -> "Zatrzymuje tę ekipę. Montaże na te dni przesuną koniec."
                BlockScope.USER -> "Montaż się nie przesuwa — pojawi się ostrzeżenie, jak przy urlopie."
            } + if (sch.settings.publishEnabled) " Ekipy zobaczą to po „Opublikuj tydzień”." else "",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Anuluj") }
            Button(onClick = { onSave(form) }, enabled = !busy, modifier = Modifier.weight(1f)) {
                Text(if (form.id == null) "Dodaj blokadę" else "Zapisz")
            }
        }
    }
}

/** Data w przycisku; stuknięcie otwiera kalendarz Material. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(label: String, value: LocalDate, modifier: Modifier, onPick: (LocalDate) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = modifier) {
        Text("$label: ${DOW[value.dayOfWeek.value - 1]} ${dm(value)}.${value.year}")
    }
    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = value.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }) { Text("Wybierz") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Anuluj") } },
        ) {
            DatePicker(state = state)
        }
    }
}
