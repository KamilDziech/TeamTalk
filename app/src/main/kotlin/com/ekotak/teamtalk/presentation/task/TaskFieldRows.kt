package com.ekotak.teamtalk.presentation.task

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.automirrored.filled.Segment
import androidx.compose.material.icons.filled.Timelapse
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ekotak.teamtalk.domain.model.SlaOption
import com.ekotak.teamtalk.domain.model.Task
import com.ekotak.teamtalk.domain.model.TaskMember
import com.ekotak.teamtalk.domain.model.TaskSection
import com.ekotak.teamtalk.domain.model.TaskStatus
import com.ekotak.teamtalk.domain.model.slaLabel
import com.ekotak.teamtalk.presentation.crm.formatDate

/**
 * Pola karty zadania — status, wykonawca, termin, sekcja, potrzebny czas, SLA.
 *
 * Każdy wiersz otwiera wybór i wysyła zmianę od razu, jednym polem: nie ma tu
 * trybu edycji ani przycisku „Zapisz". Tak działa karta w panelu, a na telefonie
 * ma to dodatkową zaletę — porcja na jedno pole trafia do kolejki offline
 * osobno, więc zmiana terminu zrobiona bez zasięgu nie cofa statusu
 * odhaczonego wcześniej.
 */
@Composable
fun TaskFieldRows(
    task: Task,
    members: List<TaskMember>,
    saving: Boolean,
    viewModel: TaskDetailViewModel,
) {
    var sheet by remember { mutableStateOf<TaskField?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    val assignee = task.assigneeId?.let { id -> members.firstOrNull { it.id == id } }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            FieldRow(
                icon = Icons.Filled.Schedule,
                label = "Status",
                value = task.status.label,
                enabled = !saving,
                onClick = { sheet = TaskField.STATUS },
            )
            HorizontalDivider()
            FieldRow(
                icon = Icons.Filled.Person,
                label = "Wykonawca",
                value = assignee?.displayName ?: task.assigneeEmail,
                placeholder = "Nieprzypisane",
                enabled = !saving,
                onClick = { sheet = TaskField.ASSIGNEE },
            )
            HorizontalDivider()
            FieldRow(
                icon = Icons.Filled.CalendarMonth,
                label = "Termin",
                value = formatDate(task.dueAt),
                placeholder = "Bez terminu",
                enabled = !saving,
                onClick = { showDatePicker = true },
            )
            HorizontalDivider()
            FieldRow(
                icon = Icons.AutoMirrored.Filled.Segment,
                label = "Sekcja",
                value = task.section?.label,
                placeholder = "Bez sekcji",
                enabled = !saving,
                onClick = { sheet = TaskField.SECTION },
            )
            HorizontalDivider()
            FieldRow(
                icon = Icons.Filled.Timelapse,
                label = "Potrzebny czas",
                value = estimateLabel(task.estimatedMinutes),
                placeholder = "Nie podano",
                enabled = !saving,
                onClick = { sheet = TaskField.ESTIMATE },
            )
            HorizontalDivider()
            FieldRow(
                icon = Icons.Filled.HourglassEmpty,
                label = "SLA",
                value = task.slaHours?.let(::slaLabel),
                placeholder = "Bez SLA",
                enabled = !saving,
                onClick = { sheet = TaskField.SLA },
            )
        }
    }

    when (sheet) {
        TaskField.STATUS -> ChoiceSheet(
            title = "Status zadania",
            options = TaskStatus.entries.map { Choice(it.label, it == task.status) { viewModel.setStatus(it) } },
            onDismiss = { sheet = null },
        )
        TaskField.ASSIGNEE -> ChoiceSheet(
            title = "Wykonawca",
            subtitle = "Osoby z zespołu — Biuro, Montażyści, potem reszta",
            options = buildList {
                add(Choice("Nieprzypisane", task.assigneeId == null) { viewModel.setAssignee(null) })
                members.forEach { member ->
                    add(
                        Choice(
                            label = member.displayName,
                            selected = member.id == task.assigneeId,
                            hint = memberGroupOf(member).label,
                        ) { viewModel.setAssignee(member.id) },
                    )
                }
            },
            onDismiss = { sheet = null },
        )
        TaskField.SECTION -> ChoiceSheet(
            title = "Sekcja",
            subtitle = "Etapy lejka — po nich grupuje się lista zadań",
            options = buildList {
                add(Choice("Bez sekcji", task.section == null) { viewModel.setSection(null) })
                TaskSection.entries.forEach { section ->
                    add(Choice(section.label, section == task.section) { viewModel.setSection(section) })
                }
            },
            onDismiss = { sheet = null },
        )
        TaskField.ESTIMATE -> ChoiceSheet(
            title = "Potrzebny czas",
            subtitle = "Szacowany nakład — pomaga układać dzień, nie liczy SLA",
            options = buildList {
                add(Choice("Nie podano", task.estimatedMinutes == null) { viewModel.setEstimate(null) })
                ESTIMATE_OPTIONS.forEach { minutes ->
                    add(
                        Choice(
                            label = estimateLabel(minutes).orEmpty(),
                            selected = minutes == task.estimatedMinutes,
                        ) { viewModel.setEstimate(minutes) },
                    )
                }
            },
            onDismiss = { sheet = null },
        )
        TaskField.SLA -> ChoiceSheet(
            title = "SLA",
            subtitle = "Czas na realizację liczony od utworzenia zadania",
            options = buildList {
                add(Choice("Bez SLA", task.slaHours == null) { viewModel.setSla(null) })
                SlaOption.entries.forEach { option ->
                    add(
                        Choice(option.label, option.hours == task.slaHours) {
                            viewModel.setSla(option.hours)
                        },
                    )
                }
            },
            onDismiss = { sheet = null },
        )
        null -> Unit
    }

    if (showDatePicker) {
        DueDatePicker(
            current = task.dueAt,
            onPick = { viewModel.setDueAt(it) },
            onClear = { viewModel.setDueAt(null) },
            onDismiss = { showDatePicker = false },
        )
    }
}

/** Które pole karty jest właśnie wybierane (żadne = `null`). */
private enum class TaskField { STATUS, ASSIGNEE, SECTION, ESTIMATE, SLA }

/** Wartości szacowanego nakładu — kwadranse do dwóch godzin, potem pół dnia. */
private val ESTIMATE_OPTIONS = listOf(15, 30, 45, 60, 90, 120, 240, 480)

@Composable
private fun FieldRow(
    icon: ImageVector,
    label: String,
    value: String?,
    placeholder: String = "",
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value?.takeIf { it.isNotBlank() } ?: placeholder,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (value.isNullOrBlank()) FontWeight.Normal else FontWeight.Medium,
            color = if (value.isNullOrBlank()) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

private data class Choice(
    val label: String,
    val selected: Boolean,
    val hint: String? = null,
    val onPick: () -> Unit,
)

/** Wspólny arkusz wyboru: jedno dotknięcie wybiera i zamyka. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceSheet(
    title: String,
    options: List<Choice>,
    onDismiss: () -> Unit,
    subtitle: String? = null,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            options.forEach { choice ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            choice.onPick()
                            onDismiss()
                        }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = choice.label,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = if (choice.selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (choice.selected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                        modifier = Modifier.weight(1f),
                    )
                    choice.hint?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Wybór terminu. „Zdejmij termin" jest tu, a nie w osobnym menu, bo zdjęcie
 * terminu to wariant tej samej decyzji co jego ustawienie.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DueDatePicker(
    current: String?,
    onPick: (Long) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = com.ekotak.teamtalk.presentation.crm.parseIsoMillis(current),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let(onPick)
                onDismiss()
            }) { Text("OK") }
        },
        dismissButton = {
            Row {
                if (current != null) {
                    TextButton(onClick = {
                        onClear()
                        onDismiss()
                    }) { Text("Zdejmij termin") }
                }
                TextButton(onClick = onDismiss) { Text("Anuluj") }
            }
        },
    ) {
        DatePicker(state = state)
    }
}
