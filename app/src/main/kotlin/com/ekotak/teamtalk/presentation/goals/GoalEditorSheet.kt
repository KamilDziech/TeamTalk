package com.ekotak.teamtalk.presentation.goals

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ekotak.teamtalk.domain.model.Goal
import com.ekotak.teamtalk.domain.model.GoalDraft
import com.ekotak.teamtalk.domain.model.GoalScope
import com.ekotak.teamtalk.domain.model.GoalUnit
import com.ekotak.teamtalk.presentation.theme.EkotakGreen
import com.ekotak.teamtalk.presentation.theme.Red600

/**
 * Kreator celu — jeden formularz dla wszystkich trzech zakresów, jak w panelu.
 * Zakres bierzemy z otwartej zakładki: człowiek otworzył „Zespołu", więc zakłada
 * cel działu, a nie wybiera tego drugi raz z listy.
 *
 * Wartość podaje się w jednostce miernika (złotówki, sztuki, procenty), bez
 * przeliczania — tak samo jak w panelu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalEditorSheet(
    state: GoalsViewModel.UiState,
    onDismiss: () -> Unit,
    onSave: (GoalDraft) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val metrics = remember(state.catalog, state.scope) {
        state.catalog?.metrics?.filter { it.scopes.contains(state.scope) }.orEmpty()
    }

    var metric by remember(metrics) { mutableStateOf(metrics.firstOrNull()?.code.orEmpty()) }
    var target by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    // Nagroda tylko przy celu OSOBISTYM: punkty są indywidualne, a celu działu
    // dowozi kilka osób w różnym stopniu (decyzja 2026-09-23).
    var ruleId by remember { mutableStateOf("") }
    var ownerId by remember(state.personal) {
        mutableStateOf(state.personal?.person?.id.orEmpty())
    }
    var error by remember { mutableStateOf<String?>(null) }

    val selected = metrics.firstOrNull { it.code == metric }
    val unitHint = when (selected?.unit) {
        GoalUnit.PLN -> "w złotówkach"
        GoalUnit.PCT -> "w procentach (0–100)"
        GoalUnit.PKT -> "w punktach"
        else -> "w sztukach"
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text(
                text = when (state.scope) {
                    GoalScope.PERSONAL -> "Nowy cel osobisty"
                    GoalScope.TEAM -> "Nowy cel działu ${DEPARTMENT_LABELS[state.team].orEmpty()}"
                    GoalScope.COMPANY -> "Nowy cel firmy"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "Okres: ${periodLabel(state.period)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            if (metrics.isEmpty()) {
                Text(
                    text = "Katalog mierników nie jest jeszcze pobrany. Odśwież moduł " +
                        "przy zasięgu i spróbuj ponownie.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Red600,
                )
            }

            if (state.scope == GoalScope.PERSONAL) {
                val managed = state.personal?.managed.orEmpty()
                if (managed.size > 1) {
                    Text("Dla kogo", style = MaterialTheme.typography.labelMedium)
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        managed.forEach { person ->
                            FilterChip(
                                selected = ownerId == person.id,
                                onClick = { ownerId = person.id },
                                label = { Text(person.name) },
                            )
                        }
                    }
                }
            }

            Text("Miernik", style = MaterialTheme.typography.labelMedium)
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                metrics.forEach { def ->
                    FilterChip(
                        selected = metric == def.code,
                        onClick = { metric = def.code },
                        label = { Text(def.label) },
                    )
                }
            }
            selected?.let {
                Text(
                    text = it.hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            OutlinedTextField(
                value = target,
                onValueChange = { target = it; error = null },
                label = { Text("Wartość docelowa ($unitHint)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                isError = error != null,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nazwa własna (opcjonalnie)") },
                placeholder = { Text(selected?.label.orEmpty()) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )

            if (state.scope == GoalScope.PERSONAL && state.catalog?.rules?.isNotEmpty() == true) {
                Text(
                    text = "Nagroda za osiągnięcie (opcjonalnie)",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = ruleId.isBlank(),
                        onClick = { ruleId = "" },
                        label = { Text("bez punktów") },
                    )
                    state.catalog.rules.forEach { rule ->
                        FilterChip(
                            selected = ruleId == rule.id,
                            onClick = { ruleId = rule.id },
                            label = { Text("${rule.name} (+${rule.points})") },
                        )
                    }
                }
                Text(
                    text = "Zamknięcie okresu z wynikiem ≥ 100% założy propozycję punktów. " +
                        "Punkty zatwierdza zarząd.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = Red600,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("Anuluj") }
                Button(
                    onClick = {
                        val value = target.replace(" ", "").replace(",", ".").toDoubleOrNull()
                        if (value == null || value <= 0.0) {
                            error = "Podaj wartość docelową większą od zera."
                            return@Button
                        }
                        if (metric.isBlank()) {
                            error = "Wybierz miernik."
                            return@Button
                        }
                        if (state.scope == GoalScope.PERSONAL && ownerId.isBlank()) {
                            error = "Wskaż osobę, której dotyczy cel."
                            return@Button
                        }
                        onSave(
                            GoalDraft(
                                scope = state.scope,
                                ownerUserId = ownerId.takeIf { state.scope == GoalScope.PERSONAL },
                                teamKey = state.team.takeIf { state.scope == GoalScope.TEAM },
                                metric = metric,
                                name = name.trim(),
                                target = value,
                                direction = selected?.direction,
                                periodKey = state.period,
                                ruleId = ruleId.takeIf {
                                    state.scope == GoalScope.PERSONAL && it.isNotBlank()
                                },
                            ),
                        )
                    },
                ) { Text("Zapisz cel") }
            }
        }
    }
}

/**
 * Wpis ręczny do celu `manual` — STAN na dziś, nie przyrost. Najczęstsza
 * czynność w terenie: dlatego jest osobnym, jednopolowym arkuszem, a nie
 * schowana w edycji celu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalCheckinSheet(
    goal: Goal,
    onDismiss: () -> Unit,
    onSave: (Double, String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var value by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text(
                text = goal.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = "Cel: ${goalValueText(goal.target, goal.unit)} · " +
                    "ostatnio: ${goalValueText(goal.value, goal.unit)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp),
            )

            OutlinedTextField(
                value = value,
                onValueChange = { value = it; error = null },
                label = { Text("Stan na dziś") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                isError = error != null,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Notatka (opcjonalnie)") },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )

            error?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = Red600,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onDismiss) { Text("Anuluj") }
                Button(
                    onClick = {
                        val parsed = value.replace(" ", "").replace(",", ".").toDoubleOrNull()
                        if (parsed == null) {
                            error = "Podaj liczbę."
                            return@Button
                        }
                        onSave(parsed, note.trim())
                    },
                ) { Text("Zapisz wpis") }
            }
        }
    }
}
