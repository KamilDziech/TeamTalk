package com.ekotak.teamtalk.presentation.service

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ekotak.teamtalk.presentation.crm.parseIsoMillis
import com.ekotak.teamtalk.presentation.theme.Red600

/**
 * Pola-chipy kart modułu Serwis: etykieta u góry, wartość pod nią, całość
 * klikalna. Odpowiednik `q.chip` z panelu — kartę czyta się jak formularz,
 * ale każde pole zapisuje się osobno, od razu po wyborze.
 */

@Composable
fun FieldBox(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    warn: Boolean = false,
    link: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    val content: @Composable () -> Unit = {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = when {
                    warn -> Red600
                    link -> MaterialTheme.colorScheme.primary
                    !enabled -> MaterialTheme.colorScheme.onSurfaceVariant
                    else -> MaterialTheme.colorScheme.onSurface
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
    val shape = RoundedCornerShape(12.dp)
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    if (onClick != null && enabled) {
        Surface(
            onClick = onClick,
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = border,
            modifier = modifier.fillMaxWidth(),
        ) { content() }
    } else {
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = border,
            modifier = modifier.fillMaxWidth(),
        ) { content() }
    }
}

/** Dwie kolumny pól — układ karty z panelu (`q.twoCol`). */
@Composable
fun FieldRow(
    left: @Composable (Modifier) -> Unit,
    right: @Composable (Modifier) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        left(Modifier.weight(1f))
        right(Modifier.weight(1f))
    }
}

/**
 * Pole z listą wyboru. Menu rozwija się pod polem — na telefonie to wygodniejsze
 * niż arkusz, bo opcji jest kilka, nie kilkadziesiąt.
 */
@Composable
fun <T> SelectField(
    label: String,
    value: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    warn: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        FieldBox(
            label = label,
            value = value,
            warn = warn,
            enabled = enabled,
            onClick = { open = true },
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    onClick = {
                        open = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

/**
 * Wybór dnia. Termin serwisu ustawiamy z dokładnością do DNIA — godzinę ustala
 * serwisant z klientem telefonicznie, więc nigdzie jej nie pokazujemy (tak samo
 * jak panel, patrz `SERVICE_DAY_HOUR`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayPickerDialog(
    initialIso: String?,
    onDismiss: () -> Unit,
    onPick: (String?) -> Unit,
) {
    val state = rememberDatePickerState(initialSelectedDateMillis = parseIsoMillis(initialIso))
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onPick(state.selectedDateMillis?.let { dayMillisToIso(it) })
                    onDismiss()
                },
            ) { Text("Zapisz") }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = {
                        onPick(null)
                        onDismiss()
                    },
                ) { Text("Wyczyść") }
                TextButton(onClick = onDismiss) { Text("Anuluj") }
            }
        },
    ) {
        DatePicker(state = state)
    }
}

/**
 * Odstęp, o jaki arkusz odsuwa swoją zawartość od dołu ekranu — żeby ostatni
 * wiersz (przyciski, ostatni chip) nie kleił się do paska gestów.
 *
 * Wartość jest STAŁA, a nie liczona z insetów, i to celowo: `TeamTalkNavGraph`
 * konsumuje dolny inset dla całego drzewa (żeby klawiatura nie wypychała pól
 * dwa razy — patrz komentarz przy `consumeWindowInsets`), więc wewnątrz arkusza
 * `navigationBarsPadding()` daje zero, a `ModalBottomSheet` rysuje się w swoim
 * własnym oknie, do którego insety też nie docierają.
 *
 * 56 dp pokrywa i pasek gestów, i wyższy pasek trójprzyciskowy, z marginesem.
 */
@Composable
fun sheetBottomPadding(): Dp = 56.dp

/** Belka informacyjna karty — braki do uzupełnienia albo błąd zapisu. */
@Composable
fun WarningBar(text: String, color: Color = Red600) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = color.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = color,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
        )
    }
}
