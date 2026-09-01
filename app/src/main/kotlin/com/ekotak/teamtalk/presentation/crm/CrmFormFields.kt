package com.ekotak.teamtalk.presentation.crm

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ekotak.teamtalk.domain.model.TaskMember
import java.util.Calendar
import java.util.TimeZone

/**
 * Elementy formularza edycji karty deala. Wspólna zasada: puste pole tekstowe
 * oznacza `null`, czyli wyczyszczenie wartości po stronie API — dlatego callbacki
 * oddają `String?`, a nie `String`.
 */

@Composable
fun FormCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(10.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), content = content)
        }
    }
}

/**
 * Pole tekstowe; puste = `null` = wyczyszczenie wartości.
 * `onValueChange` jest ostatnie, żeby wywołania mogły użyć trailing lambdy.
 */
@Composable
fun FormTextField(
    label: String,
    value: String?,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    onValueChange: (String?) -> Unit,
) {
    OutlinedTextField(
        value = value.orEmpty(),
        onValueChange = { onValueChange(it.trim().ifBlank { null }) },
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = if (singleLine) ImeAction.Next else ImeAction.Default,
        ),
    )
}

/** Pole liczbowe — trzyma surowy tekst, bo w trakcie pisania bywa niepełny. */
@Composable
fun FormNumberField(
    label: String,
    text: String,
    onTextChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    decimal: Boolean = false,
) {
    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
            imeAction = ImeAction.Next,
        ),
    )
}

/**
 * Wybór jednej z kilku opcji jako chipy. `nullLabel` dodaje opcję „brak" —
 * potrzebną tam, gdzie API dopuszcza wyczyszczenie pola (np. trudność, persona).
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun <T> FormChoiceRow(
    label: String,
    options: List<T>,
    selected: T?,
    optionLabel: (T) -> String,
    onSelect: (T?) -> Unit,
    nullLabel: String? = null,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (nullLabel != null) {
                FilterChip(
                    selected = selected == null,
                    onClick = { onSelect(null) },
                    label = { Text(nullLabel) },
                )
            }
            options.forEach { option ->
                FilterChip(
                    selected = selected == option,
                    onClick = { onSelect(option) },
                    label = { Text(optionLabel(option)) },
                )
            }
        }
    }
}

@Composable
fun FormSwitch(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Wybór osoby z zespołu; „Bez przypisania" czyści pole. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormMemberPicker(
    label: String,
    members: List<TaskMember>,
    selectedId: String?,
    onSelect: (String?) -> Unit,
    allowEmpty: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = members.firstOrNull { it.id == selectedId }?.displayName
        ?: if (selectedId == null) "Bez przypisania" else "Osoba spoza listy"

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
            trailingIcon = { Icon(Icons.Default.ArrowDropDown, contentDescription = null) },
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (allowEmpty) {
                DropdownMenuItem(
                    text = { Text("Bez przypisania") },
                    onClick = { onSelect(null); expanded = false },
                )
            }
            members.forEach { member ->
                DropdownMenuItem(
                    text = { Text(member.displayName) },
                    onClick = { onSelect(member.id); expanded = false },
                )
            }
        }
    }
}

/**
 * Data + godzina. Dwa kroki (kalendarz, potem zegar), bo Material 3 nie ma
 * jednego dialogu na oba; „Wyczyść" ustawia `null`, czyli kasuje termin w API.
 */
@Composable
fun FormDateTimeField(label: String, millis: Long?, onChange: (Long?) -> Unit) {
    val pickDateTime = rememberDateTimePicker(label, millis) { onChange(it) }

    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = pickDateTime, modifier = Modifier.weight(1f)) {
                Text(millis?.let { formatMillisDateTime(it) } ?: "Ustaw termin")
            }
            if (millis != null) {
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = { onChange(null) }) {
                    Icon(Icons.Default.Clear, contentDescription = "Wyczyść $label")
                }
            }
        }
    }
}

/**
 * Dwuetapowy wybór terminu (dzień, potem godzina) oderwany od kontrolki, która
 * go otwiera: formularz uruchamia go przyciskiem, kafel spotkania na zakładce
 * LEAD — dotknięciem wiersza. Zwraca funkcję otwierającą pierwszy dialog;
 * oba dialogi mieszkają tutaj, więc wywołujący rysuje wyłącznie swój wyzwalacz.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun rememberDateTimePicker(
    label: String,
    millis: Long?,
    onChange: (Long) -> Unit,
): () -> Unit {
    var pickingDate by remember { mutableStateOf(false) }
    var pendingDate by remember { mutableStateOf<Long?>(null) }

    if (pickingDate) {
        // DatePicker pracuje w UTC — przesuwamy o offset strefy, żeby użytkownik
        // wybrał dzień, który widzi w kalendarzu, a nie sąsiedni.
        val state = rememberDatePickerState(
            initialSelectedDateMillis = (millis ?: System.currentTimeMillis()).toUtcDay(),
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            confirmButton = {
                TextButton(onClick = {
                    pendingDate = state.selectedDateMillis?.fromUtcDay()
                    pickingDate = false
                }) { Text("Dalej") }
            },
            dismissButton = {
                TextButton(onClick = { pickingDate = false }) { Text("Anuluj") }
            },
        ) { DatePicker(state = state) }
    }

    pendingDate?.let { date ->
        val previous = millis ?: date
        val cal = Calendar.getInstance().apply { timeInMillis = previous }
        val state = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE),
            is24Hour = true,
        )
        Dialog(onDismissRequest = { pendingDate = null }) {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(label, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))
                    TimePicker(state = state)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { pendingDate = null }) { Text("Anuluj") }
                        TextButton(onClick = {
                            onChange(date.withTime(state.hour, state.minute))
                            pendingDate = null
                        }) { Text("Ustaw") }
                    }
                }
            }
        }
    }

    return { pickingDate = true }
}

// ── Konwersje czasu dla pickerów ─────────────────────────────────────────────

/** Lokalny moment → północ tego samego dnia w UTC (wejście dla `DatePicker`). */
private fun Long.toUtcDay(): Long {
    val cal = Calendar.getInstance().apply { timeInMillis = this@toUtcDay }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

/** Północ UTC z `DatePicker` → ten sam dzień o północy czasu lokalnego. */
private fun Long.fromUtcDay(): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = this@fromUtcDay }
    return Calendar.getInstance().apply {
        clear()
        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

private fun Long.withTime(hour: Int, minute: Int): Long =
    Calendar.getInstance().apply {
        timeInMillis = this@withTime
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
