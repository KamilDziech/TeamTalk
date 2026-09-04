package com.ekotak.teamtalk.presentation.task

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ekotak.teamtalk.domain.model.TaskPriority
import com.ekotak.teamtalk.domain.model.TaskStatus
import com.ekotak.teamtalk.presentation.components.PersonScope
import com.ekotak.teamtalk.presentation.components.PersonTree

/**
 * Filtry i sortowanie w arkuszu od dołu. Pasek filtrów z panelu board360 ma
 * szerokość biurka — na telefonie ten sam zestaw mieści się tylko tak, i tylko
 * tak da się go obsłużyć kciukiem. Wybory wracają na listę jako chipy.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskFilterSheet(
    state: TaskListViewModel.UiState,
    onDismiss: () -> Unit,
    onPersonChange: (PersonScope) -> Unit,
    onToggleStatus: (TaskStatus) -> Unit,
    onStatusesChange: (Set<TaskStatus>) -> Unit,
    onPriorityChange: (TaskPriority?) -> Unit,
    onDueChange: (DueScope) -> Unit,
    onSourceChange: (SourceScope) -> Unit,
    onSortChange: (TaskSort) -> Unit,
    onGroupBySectionChange: (Boolean) -> Unit,
    onClear: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "Filtruj i sortuj",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )

            // ── Osoba ────────────────────────────────────────────────────────
            // Moje / Wszyscy / Nieprzypisane, a niżej drzewo działów (Biuro,
            // Serwis, Montaż, Pozostali) — ten sam komponent co w Kalendarzu
            // i na Mapie. Płaska lista „konkretna osoba" zniknęła: przy
            // kilkunastu ludziach chipy zajmowały pół arkusza.
            Text(
                text = "OSOBA",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PersonTree(
                members = state.members,
                selected = state.person,
                onSelect = onPersonChange,
                me = state.members.firstOrNull { it.id == state.currentUserId },
                unassignedLabel = "Nieprzypisane",
            )

            Group(label = "Status") {
                Chip("Wszystkie", state.statuses == TaskListViewModel.ALL_STATUSES) {
                    onStatusesChange(TaskListViewModel.ALL_STATUSES)
                }
                TaskStatus.entries.forEach { status ->
                    Chip(status.label, status in state.statuses) { onToggleStatus(status) }
                }
            }

            Group(label = "Priorytet") {
                Chip("Wszystkie", state.priority == null) { onPriorityChange(null) }
                TaskPriority.entries.reversed().forEach { priority ->
                    Chip(priority.label, state.priority == priority) { onPriorityChange(priority) }
                }
            }

            Group(label = "Termin") {
                DueScope.entries.forEach { due ->
                    Chip(due.label, state.due == due) { onDueChange(due) }
                }
            }

            Group(label = "Źródło") {
                SourceScope.entries.forEach { source ->
                    Chip(source.label, state.source == source) { onSourceChange(source) }
                }
            }

            Group(label = "Sortowanie") {
                TaskSort.entries.forEach { sort ->
                    Chip(sort.label, state.sort == sort) { onSortChange(sort) }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Grupuj sekcjami", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "Nagłówki etapów lejka zamiast jednej listy",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.groupBySection, onCheckedChange = onGroupBySectionChange)
            }

            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onClear, modifier = Modifier.weight(1f)) {
                    Text("Wyczyść")
                }
                Button(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                    Text(
                        if (state.visibleCount == 1) {
                            "Pokaż 1 zadanie"
                        } else {
                            "Pokaż ${state.visibleCount} zadań"
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun Group(label: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Przewijanie w poziomie zamiast zawijania: przy dłuższej liście osób
        // zawinięte chipy zjadłyby pół arkusza.
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(label) })
}
