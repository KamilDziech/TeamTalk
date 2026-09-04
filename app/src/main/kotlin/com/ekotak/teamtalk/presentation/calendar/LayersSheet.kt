package com.ekotak.teamtalk.presentation.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ekotak.teamtalk.domain.model.Calendar
import com.ekotak.teamtalk.domain.model.CalendarType
import com.ekotak.teamtalk.domain.model.OverlaySource
import com.ekotak.teamtalk.domain.model.TaskMember
import com.ekotak.teamtalk.presentation.components.PersonScope
import com.ekotak.teamtalk.presentation.components.PersonTree
import com.ekotak.teamtalk.presentation.service.sheetBottomPadding

/**
 * Warstwy i filtr — lewa kolumna panelu przeniesiona do arkusza.
 *
 * Kalendarze z kolorem i poziomem dostępu, siedem nakładek operacyjnych
 * i filtr osoby. Zakładanie kalendarza oraz zmiana nazwy i koloru zostają
 * na telefonie; matryca udostępnień („osoba / rola / wszyscy" × cztery poziomy)
 * jest pracą przy biurku i zostaje w panelu (ustalenie 2026-09-03).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayersSheet(
    calendars: List<Calendar>,
    hiddenLayers: Set<String>,
    overlaysOn: Set<OverlaySource>,
    members: List<TaskMember>,
    person: PersonScope,
    currentUserId: String?,
    onToggleLayer: (String) -> Unit,
    onToggleOverlay: (OverlaySource) -> Unit,
    busyOn: Boolean,
    onToggleBusy: () -> Unit,
    onSetPerson: (PersonScope) -> Unit,
    onCreateCalendar: (String, CalendarType, String) -> Unit,
    onRenameCalendar: (String, String, String) -> Unit,
    onArchiveCalendar: (String, Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var editing by remember { mutableStateOf<CalendarEdit?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = sheetBottomPadding()),
        ) {
            Text("Warstwy i filtr", style = MaterialTheme.typography.titleMedium)

            // Osoba: Moje / Wszyscy, a niżej działy (Biuro, Serwis, Montaż,
            // Pozostali) rozwijane w miejscu. Ten sam komponent co w Zadaniach
            // i na Mapie — filtr osoby ma wyglądać wszędzie tak samo.
            Label("Osoba")
            PersonTree(
                members = members,
                selected = person,
                onSelect = onSetPerson,
                me = members.firstOrNull { it.id == currentUserId },
            )

            Label("Kalendarze")
            if (calendars.isEmpty()) {
                Text(
                    "Brak kalendarzy.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            calendars.forEach { calendar ->
                LayerRow(
                    color = parseHexColor(calendar.color) ?: colorForId(calendar.id),
                    label = calendar.name,
                    note = when {
                        calendar.type == CalendarType.RESOURCE -> "zasób"
                        !calendar.canWrite -> "tylko odczyt"
                        else -> null
                    },
                    checked = calendar.id !in hiddenLayers,
                    onCheckedChange = { onToggleLayer(calendar.id) },
                    trailing = {
                        if (calendar.isOwner) {
                            IconButton(onClick = { editing = CalendarEdit.of(calendar) }) {
                                Icon(
                                    Icons.Filled.Settings,
                                    contentDescription = "Ustawienia kalendarza",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                )
            }
            TextButton(onClick = { editing = CalendarEdit() }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Nowy kalendarz")
            }

            Label("Nakładki operacyjne · podgląd")
            OverlaySource.entries.forEach { source ->
                LayerRow(
                    color = parseHexColor(source.color) ?: Color.Gray,
                    label = source.label,
                    note = null,
                    checked = source in overlaysOn,
                    onCheckedChange = { onToggleOverlay(source) },
                )
            }
            Text(
                text = "Nakładki są tylko do podglądu i wymagają zasięgu — rekordy edytuje się " +
                    "w macierzystym module.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Label("Prywatna zajętość")
            LayerRow(
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                label = "Szare pola „Zajęte”",
                note = "Zajętość wybranej osoby (bez wyboru — Twoja)",
                checked = busyOn,
                onCheckedChange = { onToggleBusy() },
            )
            Text(
                text = "Zajętość z prywatnego kalendarza podpiętego w panelu — same godziny, " +
                    "bez tytułów. Blokuje przypisanie wykonawcy na ten czas; podpina się " +
                    "w panelu board360, nie na telefonie.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    editing?.let { edit ->
        CalendarEditSheet(
            edit = edit,
            onChange = { editing = it },
            onSave = {
                if (edit.id == null) {
                    onCreateCalendar(edit.name, edit.type, edit.color)
                } else {
                    onRenameCalendar(edit.id, edit.name, edit.color)
                }
                editing = null
            },
            onArchive = {
                edit.id?.let { onArchiveCalendar(it, !edit.archived) }
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun LayerRow(
    color: Color,
    label: String,
    note: String?,
    checked: Boolean,
    onCheckedChange: () -> Unit,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCheckedChange)
            .padding(vertical = 2.dp),
    ) {
        Box(modifier = Modifier.size(12.dp).background(color, RoundedCornerShape(3.dp)))
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (note != null) {
            Text(
                text = note,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        trailing()
        Switch(checked = checked, onCheckedChange = { onCheckedChange() })
    }
}

/** Stan arkusza „kalendarz": pusty = nowy, z `id` = edycja istniejącego. */
data class CalendarEdit(
    val id: String? = null,
    val name: String = "",
    val type: CalendarType = CalendarType.TEAM,
    val color: String = "#2a78d6",
    val archived: Boolean = false,
) {
    companion object {
        fun of(calendar: Calendar) = CalendarEdit(
            id = calendar.id,
            name = calendar.name,
            type = calendar.type,
            color = calendar.color,
            archived = calendar.isArchived,
        )
    }
}

/** Kolory kalendarzy — ta sama paleta co w panelu. */
private val CALENDAR_COLORS = listOf(
    "#2a78d6", "#1baf7a", "#eb6834", "#8a6df0", "#e0a500", "#d55181", "#0ca30c",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarEditSheet(
    edit: CalendarEdit,
    onChange: (CalendarEdit) -> Unit,
    onSave: () -> Unit,
    onArchive: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = sheetBottomPadding()),
        ) {
            Text(
                text = if (edit.id == null) "Nowy kalendarz" else "Kalendarz",
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedTextField(
                value = edit.name,
                onValueChange = { onChange(edit.copy(name = it)) },
                label = { Text("Nazwa") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (edit.id == null) {
                // Typu nie da się zmienić po założeniu — API też na to nie pozwala.
                val types = listOf(CalendarType.TEAM, CalendarType.RESOURCE)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    types.forEachIndexed { index, type ->
                        SegmentedButton(
                            selected = edit.type == type,
                            onClick = { onChange(edit.copy(type = type)) },
                            shape = SegmentedButtonDefaults.itemShape(index, types.size),
                        ) { Text(if (type == CalendarType.TEAM) "Zespołowy" else "Zasób") }
                    }
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()),
            ) {
                CALENDAR_COLORS.forEach { hex ->
                    val color = parseHexColor(hex) ?: Color.Gray
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .background(color, CircleShape)
                            .clickable { onChange(edit.copy(color = hex)) }
                            .padding(3.dp),
                    ) {
                        if (edit.color == hex) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .align(Alignment.Center)
                                    .background(MaterialTheme.colorScheme.surface, CircleShape),
                            )
                        }
                    }
                }
            }
            Button(
                onClick = onSave,
                enabled = edit.name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (edit.id == null) "Dodaj kalendarz" else "Zapisz") }
            if (edit.id != null) {
                TextButton(onClick = onArchive, modifier = Modifier.fillMaxWidth()) {
                    Text(if (edit.archived) "Przywróć z archiwum" else "Zarchiwizuj")
                }
            }
            Text(
                text = "Udostępnianie (kto i na jakim poziomie widzi kalendarz) ustawia się w panelu.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
