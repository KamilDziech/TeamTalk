package com.ekotak.teamtalk.presentation.projects

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.ekotak.teamtalk.domain.model.ProjectTask

/**
 * „Ile zajęło?" — arkusz domykania zadania. Domyślną wartością jest estymata,
 * więc najczęstszy przypadek to jedno kliknięcie w „Tyle, ile planowałem".
 *
 * Pominięcie godzin jest DOZWOLONE: zadanie policzy się wtedy po estymacie
 * i będzie oznaczone jako szacowane. Wymuszanie liczby odstraszyłoby ludzi od
 * domykania zadań, a otwarte zadania szkodzą bardziej niż szacunek.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloseTaskSheet(
    task: ProjectTask,
    onDismiss: () -> Unit,
    onConfirm: (Int?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    val suggested = task.estimatedMinutes?.let { it / 60f } ?: 1f
    var hours by remember { mutableStateOf(formatHours(suggested)) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Zamykasz zadanie", style = MaterialTheme.typography.labelMedium)
            Text(task.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            Text("Ile to zajęło?", style = MaterialTheme.typography.labelMedium)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(onClick = { hours = stepHours(hours, -0.5f) }) { Text("−") }
                OutlinedTextField(
                    value = hours,
                    onValueChange = { hours = it },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.width(120.dp),
                    suffix = { Text("h") },
                )
                OutlinedButton(onClick = { hours = stepHours(hours, 0.5f) }) { Text("+") }
            }

            task.estimatedMinutes?.let {
                Text(
                    "Planowano ${formatHours(it / 60f)} h",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Button(
                onClick = { onConfirm(parseMinutes(hours)) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Zamknij zadanie") }

            TextButton(
                onClick = { onConfirm(null) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Nie podaję godzin") }

            Text(
                "Bez podanych godzin zadanie policzy się po estymacie i będzie oznaczone jako szacowane.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Start,
            )
        }
    }
}

/** Zgłoszenie pomysłu do Poczekalni — trzy pola, bo robi się to na parkingu. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdeaSheet(
    onDismiss: () -> Unit,
    onSubmit: (String, String?, String?) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var department by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Zgłoś pomysł", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Trafi do Poczekalni. Ktoś go oceni i odezwie się, gdy weźmie do realizacji.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Co cię wkurza albo co by pomogło") },
                singleLine = false,
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Szczegóły (opcjonalnie)") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = department,
                onValueChange = { department = it },
                label = { Text("Dział (opcjonalnie)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onSubmit(name, description, department) },
                enabled = name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Wyślij do Poczekalni") }
        }
    }
}

/** „2,5" i „2.5" znaczą to samo — technik wpisze przecinek. */
internal fun parseMinutes(raw: String): Int? {
    val value = raw.trim().replace(',', '.').toFloatOrNull() ?: return null
    if (value < 0f) return null
    return (value * 60).toInt()
}

internal fun formatHours(hours: Float): String =
    if (hours % 1f == 0f) hours.toInt().toString() else String.format("%.1f", hours).replace('.', ',')

private fun stepHours(raw: String, delta: Float): String {
    val current = raw.trim().replace(',', '.').toFloatOrNull() ?: 0f
    return formatHours((current + delta).coerceAtLeast(0f))
}
