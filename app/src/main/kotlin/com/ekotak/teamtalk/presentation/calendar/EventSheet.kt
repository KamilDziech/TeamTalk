package com.ekotak.teamtalk.presentation.calendar

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ekotak.teamtalk.domain.model.Calendar
import com.ekotak.teamtalk.domain.model.RecurFreq
import com.ekotak.teamtalk.domain.model.RecurrenceScope
import com.ekotak.teamtalk.domain.model.RsvpStatus
import com.ekotak.teamtalk.domain.model.TaskMember
import com.ekotak.teamtalk.presentation.crm.parseIsoMillis
import com.ekotak.teamtalk.presentation.service.DayPickerDialog
import com.ekotak.teamtalk.presentation.service.FieldBox
import com.ekotak.teamtalk.presentation.service.FieldRow
import com.ekotak.teamtalk.presentation.service.SelectField
import com.ekotak.teamtalk.presentation.service.WarningBar
import com.ekotak.teamtalk.presentation.service.sheetBottomPadding
import com.ekotak.teamtalk.presentation.theme.Red600
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Karta wydarzenia — jeden arkusz na podgląd, edycję i tworzenie, jak modal
 * panelu. Wchodzą tu wszystkie pola panelu plus jedno mobilne: „Nawiguj" obok
 * miejsca (intent `geo:`, tak jak w module Mapa).
 *
 * `skipPartiallyExpanded` jest obowiązkowe: bez niego arkusz otwiera się do
 * połowy ekranu i przyciski akcji wypadają pod paskiem gestów — insety tego
 * nie naprawią, patrz komentarz przy [sheetBottomPadding].
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EventSheet(
    form: CalendarViewModel.EventForm,
    calendars: List<Calendar>,
    members: List<TaskMember>,
    currentUserId: String?,
    onEdit: ((CalendarViewModel.EventForm) -> CalendarViewModel.EventForm) -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onRsvp: (RsvpStatus) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showDatePicker by remember { mutableStateOf<DateField?>(null) }
    var showTimePicker by remember { mutableStateOf<TimeField?>(null) }

    val writable = calendars.filter { it.canWrite }
    val calendar = calendars.firstOrNull { it.id == form.calendarId }
    val editable = !form.readOnly

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = sheetBottomPadding()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (form.isNew) "Nowe wydarzenie" else "Wydarzenie",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                if (!form.isNew && editable) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, contentDescription = "Usuń", tint = Red600)
                    }
                }
            }

            if (form.conflictForce) {
                WarningBar(
                    "Zasób jest w tym czasie zajęty. Zapisz ponownie, aby wpisać termin mimo kolizji.",
                    color = Color(0xFFB4680A),
                )
            }
            if (form.pendingSync) {
                WarningBar("Zmiana czeka na wysyłkę — poleci, gdy wróci zasięg.", color = Color(0xFFB4680A))
            }
            if (form.readOnly) {
                WarningBar("Ten kalendarz masz tylko do odczytu.", color = Color(0xFF5A6B7C))
            }

            OutlinedTextField(
                value = form.title,
                onValueChange = { value -> onEdit { it.copy(title = value) } },
                label = { Text("Tytuł") },
                singleLine = true,
                enabled = editable,
                modifier = Modifier.fillMaxWidth(),
            )

            SelectField(
                label = "Kalendarz",
                value = calendar?.name ?: "—",
                options = writable,
                optionLabel = { "${it.name} (${it.type.label})" },
                onSelect = { picked -> onEdit { it.copy(calendarId = picked.id) } },
                enabled = editable && form.isNew,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Cały dzień", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Switch(
                    checked = form.allDay,
                    onCheckedChange = { value -> onEdit { it.copy(allDay = value) } },
                    enabled = editable,
                )
            }

            FieldRow(
                left = { modifier ->
                    FieldBox(
                        label = "Od dnia",
                        value = form.date.format(DAY_FORMAT),
                        enabled = editable,
                        onClick = { showDatePicker = DateField.START },
                        modifier = modifier,
                    )
                },
                right = { modifier ->
                    FieldBox(
                        label = "Do dnia",
                        value = form.endDate.format(DAY_FORMAT),
                        enabled = editable,
                        onClick = { showDatePicker = DateField.END },
                        modifier = modifier,
                    )
                },
            )

            if (!form.allDay) {
                FieldRow(
                    left = { modifier ->
                        FieldBox(
                            label = "Od godz.",
                            value = form.startTime.format(TIME_FORMAT),
                            enabled = editable,
                            onClick = { showTimePicker = TimeField.START },
                            modifier = modifier,
                        )
                    },
                    right = { modifier ->
                        FieldBox(
                            label = "Do godz.",
                            value = form.endTime.format(TIME_FORMAT),
                            enabled = editable,
                            onClick = { showTimePicker = TimeField.END },
                            modifier = modifier,
                        )
                    },
                )
            }

            SelectField(
                label = "Właściciel",
                value = members.firstOrNull { it.id == form.assigneeId }?.displayName ?: "— (nieprzypisane)",
                options = listOf<TaskMember?>(null) + members,
                optionLabel = { it?.displayName ?: "— (nieprzypisane)" },
                onSelect = { picked -> onEdit { it.copy(assigneeId = picked?.id) } },
                enabled = editable,
            )

            SectionLabel("Uczestnicy")
            if (members.isEmpty()) {
                Text(
                    "Brak członków zespołu.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    members.forEach { member ->
                        val on = member.id in form.attendeeIds
                        val response = form.attendeeResponses[member.id]
                        FilterChip(
                            selected = on,
                            onClick = {
                                if (!editable) return@FilterChip
                                onEdit { state ->
                                    val ids = state.attendeeIds.toMutableList()
                                    if (!ids.remove(member.id)) ids.add(member.id)
                                    state.copy(attendeeIds = ids)
                                }
                            },
                            label = {
                                Text(
                                    text = member.displayName +
                                        (response?.takeIf { on && it != RsvpStatus.NEEDS_ACTION }
                                            ?.let { " · ${it.label.lowercase()}" } ?: ""),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            },
                        )
                    }
                }
            }

            // RSVP widać tylko wtedy, gdy sami jesteśmy uczestnikiem — panel tak samo.
            if (!form.isNew && currentUserId != null && currentUserId in form.attendeeIds) {
                SectionLabel("Twoja odpowiedź")
                val options = listOf(RsvpStatus.ACCEPTED, RsvpStatus.TENTATIVE, RsvpStatus.DECLINED)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    options.forEachIndexed { index, status ->
                        SegmentedButton(
                            selected = form.attendeeResponses[currentUserId] == status,
                            onClick = { onRsvp(status) },
                            shape = SegmentedButtonDefaults.itemShape(index, options.size),
                        ) { Text(status.label) }
                    }
                }
            }

            OutlinedTextField(
                value = form.location,
                onValueChange = { value -> onEdit { it.copy(location = value) } },
                label = { Text("Miejsce") },
                singleLine = true,
                enabled = editable,
                trailingIcon = {
                    if (form.location.isNotBlank()) {
                        IconButton(
                            onClick = {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("geo:0,0?q=${Uri.encode(form.location)}"),
                                    ),
                                )
                            },
                        ) {
                            Icon(Icons.Filled.Navigation, contentDescription = "Nawiguj")
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )

            SectionLabel("Kolor")
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                ColorDot(
                    color = parseHexColor(calendar?.color) ?: colorForId(form.calendarId),
                    selected = form.color == null,
                    outlined = true,
                    onClick = { onEdit { it.copy(color = null) } },
                )
                EVENT_COLORS.forEach { hex ->
                    ColorDot(
                        color = parseHexColor(hex) ?: Color.Gray,
                        selected = form.color == hex,
                        outlined = false,
                        onClick = { if (editable) onEdit { it.copy(color = hex) } },
                    )
                }
            }

            // Serię zakłada się przy tworzeniu; edycja wystąpienia zmienia zakres,
            // a nie regułę — dokładnie jak w panelu.
            if (form.isNew) {
                SelectField(
                    label = "Powtarzanie",
                    value = form.recurFreq?.label ?: "Nie powtarzaj",
                    options = listOf<RecurFreq?>(null) + RecurFreq.entries,
                    optionLabel = { it?.label ?: "Nie powtarzaj" },
                    onSelect = { picked -> onEdit { it.copy(recurFreq = picked) } },
                    enabled = editable,
                )
                if (form.recurFreq != null) {
                    FieldRow(
                        left = { modifier ->
                            SelectField(
                                label = "Co ile",
                                value = "co ${form.recurInterval}",
                                options = (1..12).toList(),
                                optionLabel = { "co $it" },
                                onSelect = { picked -> onEdit { it.copy(recurInterval = picked) } },
                                modifier = modifier,
                            )
                        },
                        right = { modifier ->
                            SelectField(
                                label = "Ile razy",
                                value = form.recurCount?.toString() ?: "bez końca",
                                options = listOf<Int?>(null) + listOf(2, 3, 4, 5, 6, 8, 10, 12, 24, 52),
                                optionLabel = { it?.toString() ?: "bez końca" },
                                onSelect = { picked -> onEdit { it.copy(recurCount = picked) } },
                                modifier = modifier,
                            )
                        },
                    )
                }
            }

            if (form.recurGroupId != null) {
                SectionLabel("Zastosuj do")
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    RecurrenceScope.entries.forEachIndexed { index, scope ->
                        SegmentedButton(
                            selected = form.scope == scope,
                            onClick = { onEdit { it.copy(scope = scope) } },
                            shape = SegmentedButtonDefaults.itemShape(index, RecurrenceScope.entries.size),
                            enabled = editable,
                        ) { Text(scope.label, style = MaterialTheme.typography.labelSmall) }
                    }
                }
            }

            OutlinedTextField(
                value = form.description,
                onValueChange = { value -> onEdit { it.copy(description = value) } },
                label = { Text("Opis") },
                enabled = editable,
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            if (editable) {
                Button(
                    onClick = onSave,
                    enabled = !form.saving,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        when {
                            form.conflictForce -> "Zapisz mimo kolizji"
                            form.isNew -> "Dodaj wydarzenie"
                            else -> "Zapisz"
                        },
                    )
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("Zamknij") }
        }
    }

    showDatePicker?.let { field ->
        val current = if (field == DateField.START) form.date else form.endDate
        DayPickerDialog(
            initialIso = isoUtc(dateToMillis(current)),
            onDismiss = { showDatePicker = null },
            onPick = { iso ->
                val picked = parseIsoMillis(iso)?.let { millisToDate(it) } ?: return@DayPickerDialog
                onEdit { state ->
                    when (field) {
                        // Przesunięcie początku ciągnie koniec: inaczej wydarzenie
                        // kończyłoby się przed swoim początkiem.
                        DateField.START -> state.copy(
                            date = picked,
                            endDate = if (state.endDate < picked) picked else state.endDate,
                        )
                        DateField.END -> state.copy(
                            endDate = if (picked < state.date) state.date else picked,
                        )
                    }
                }
            },
        )
    }

    showTimePicker?.let { field ->
        val initial = if (field == TimeField.START) form.startTime else form.endTime
        TimePickerDialog(
            initial = initial,
            onDismiss = { showTimePicker = null },
            onPick = { time ->
                onEdit { state ->
                    when (field) {
                        TimeField.START -> state.copy(
                            startTime = time,
                            endTime = if (state.endTime <= time) time.plusHours(1) else state.endTime,
                        )
                        TimeField.END -> state.copy(
                            endTime = if (time <= state.startTime) state.startTime.plusHours(1) else time,
                        )
                    }
                }
            },
        )
    }
}

private enum class DateField { START, END }

private enum class TimeField { START, END }

private val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
private val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

/** Paleta kolorów wydarzeń — te same wartości co `CHIP_COLORS` w panelu. */
private val EVENT_COLORS = listOf(
    "#2a78d6", "#1baf7a", "#eb6834", "#8a6df0", "#e0a500", "#d55181", "#0ca30c",
)

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/**
 * Kropka koloru. Wariant [outlined] to „automatyczny" — pusty krążek w kolorze
 * kalendarza, bo wydarzenie bez własnego koloru dziedziczy właśnie ten.
 */
@Composable
private fun ColorDot(color: Color, selected: Boolean, outlined: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .background(if (outlined) Color.Transparent else color, CircleShape)
            .border(
                width = if (selected) 2.5.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface else color,
                shape = CircleShape,
            )
            .clickable(onClick = onClick),
    )
}

/** Wybór godziny — Material 3 nie ma gotowego dialogu, więc składamy go sami. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initial: LocalTime,
    onDismiss: () -> Unit,
    onPick: (LocalTime) -> Unit,
) {
    val state = rememberTimePickerState(
        initialHour = initial.hour,
        initialMinute = initial.minute,
        is24Hour = true,
    )
    Dialog(onDismissRequest = onDismiss) {
        androidx.compose.material3.Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp),
            ) {
                TimePicker(state = state)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Anuluj") }
                    TextButton(
                        onClick = {
                            onPick(LocalTime.of(state.hour, state.minute))
                            onDismiss()
                        },
                    ) { Text("Ustaw") }
                }
            }
        }
    }
}

