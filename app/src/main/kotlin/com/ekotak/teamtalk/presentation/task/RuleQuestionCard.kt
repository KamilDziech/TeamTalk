package com.ekotak.teamtalk.presentation.task

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.ekotak.teamtalk.domain.model.RuleQuestion

/**
 * Pytanie reguły w karcie zadania. Zadanie założone akcją „Zapytaj człowieka"
 * nie jest do odhaczenia, tylko do ODPOWIEDZENIA — pola formularza przychodzą
 * z serwera, bo to reguła decyduje, o co pyta.
 *
 * Odpowiedź wędruje tam, gdzie wskazuje jej treść: koszt, przebieg albo
 * warsztat robią z notatki wpis w historii napraw, sama notatka — zdarzenie
 * pojazdu (decyzja D3 z docs/tasks/modul-reguly.md). Bez zasięgu odpowiedź
 * czeka w kolejce modułu Zadania, a zadanie zamyka się od razu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleQuestionCard(
    question: RuleQuestion,
    saving: Boolean,
    onAnswer: (Map<String, String>) -> Unit,
) {
    val values = remember(question.runId) { mutableStateMapOf<String, String>() }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "Pytanie reguły · ${question.ruleName}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(text = question.question, style = MaterialTheme.typography.bodyMedium)
            question.subjectLabel?.let {
                Text(
                    text = "Dotyczy: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (question.answered) {
                Text(
                    text = "Odpowiedziano: ${question.answerNote ?: "—"}",
                    style = MaterialTheme.typography.bodySmall,
                )
                return@Column
            }

            question.fields.forEach { field ->
                if (field.options.isNotEmpty()) {
                    RuleSelectField(
                        label = field.label,
                        options = field.options.map { it.value to it.label },
                        value = values[field.key].orEmpty(),
                        onChange = { values[field.key] = it },
                    )
                } else {
                    OutlinedTextField(
                        value = values[field.key].orEmpty(),
                        onValueChange = { values[field.key] = it },
                        label = {
                            Text(field.label + if (field.unit != null) " [${field.unit}]" else "")
                        },
                        singleLine = field.key != "note",
                        minLines = if (field.key == "note") 2 else 1,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (field.type == "number") {
                                KeyboardType.Number
                            } else {
                                KeyboardType.Text
                            },
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Button(
                onClick = { onAnswer(values.toMap()) },
                enabled = !saving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (saving) "Zapisywanie…" else "Odpowiedz i zamknij zadanie")
            }
            Text(
                text = "Koszt, przebieg albo warsztat sprawią, że wpis trafi do historii napraw. " +
                    "Sama notatka — do zdarzeń pojazdu.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Lista wyboru dla pola słownikowego formularza odpowiedzi. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RuleSelectField(
    label: String,
    options: List<Pair<String, String>>,
    value: String,
    onChange: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val shown = options.firstOrNull { it.first == value }?.second.orEmpty()

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = shown,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (optValue, optLabel) ->
                DropdownMenuItem(
                    text = { Text(optLabel) },
                    onClick = {
                        onChange(optValue)
                        expanded = false
                    },
                )
            }
        }
    }
}
