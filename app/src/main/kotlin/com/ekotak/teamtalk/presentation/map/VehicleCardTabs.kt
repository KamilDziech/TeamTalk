package com.ekotak.teamtalk.presentation.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ekotak.teamtalk.domain.model.VEHICLE_FILE_CATEGORIES
import com.ekotak.teamtalk.domain.model.VehicleFile
import com.ekotak.teamtalk.domain.model.VehicleHistoryItem
import com.ekotak.teamtalk.domain.model.VehicleRule
import com.ekotak.teamtalk.domain.model.VehicleTaskItem
import com.ekotak.teamtalk.domain.model.VehicleTasks
import com.ekotak.teamtalk.presentation.crm.rememberFilePickers
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * Zakładki karty auta poza trasą (flota-karta-auta.md, E5) — te same co w panelu:
 * Zadania (zaplanowane wyszarzone), Pliki (z aparatem), Historia (jedna oś z
 * filtrem), Reguły (podgląd — ustawia się je w panelu).
 */

@Composable
fun VehicleTabRow(selected: VehicleTab, onSelect: (VehicleTab) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(VehicleTab.entries) { tab ->
            FilterChip(selected = tab == selected, onClick = { onSelect(tab) }, label = { Text(tab.label) })
        }
    }
}

@Composable
private fun <T> SlotBody(
    slot: VehicleCardViewModel.Slot<T>,
    onRetry: () -> Unit,
    content: @Composable (T) -> Unit,
) {
    val data = slot.data
    when {
        data != null -> Column {
            slot.error?.let { Note("Pokazuję ostatnio pobrane. $it") }
            content(data)
        }
        slot.error != null -> Column {
            Note(slot.error)
            TextButton(onClick = onRetry) { Text("Spróbuj ponownie") }
        }
        else -> Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
            CircularProgressIndicator()
        }
    }
}

// ── Zadania ─────────────────────────────────────────────────────────────────

@Composable
fun VehicleTasksTab(
    slot: VehicleCardViewModel.Slot<VehicleTasks>,
    onRetry: () -> Unit,
    onOpenTask: (String) -> Unit,
) = SlotBody(slot, onRetry) { data ->
    val active = data.items.filter { it.state == VehicleTaskItem.State.ACTIVE }
    val planned = data.items.filter { it.state == VehicleTaskItem.State.PLANNED }
    val done = data.items.filter { it.state == VehicleTaskItem.State.DONE }
    val activeIds = active.mapNotNull { it.task?.id }.toSet()
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            Note("Zadanie trafia do opiekuna 14 dni przed terminem albo 1 000 km przed progiem. Wcześniej jest tylko zaplanowane.")
        }
        item { Section("Aktywne (${active.size})") }
        if (active.isEmpty()) item { Note("Nic nie czeka na zrobienie.") }
        items(active, key = { it.deadlineId }) { TaskRow(it, onOpenTask) }
        item { Section("Zaplanowane (${planned.size})") }
        items(planned, key = { it.deadlineId }) { TaskRow(it, onOpenTask) }
        if (done.isNotEmpty()) {
            item { Section("Zamknięte (${done.size})") }
            items(done, key = { it.deadlineId }) { TaskRow(it, onOpenTask) }
        }
        val history = data.history.filter { it.id !in activeIds }
        if (history.isNotEmpty()) {
            item { Section("Wcześniejsze zadania auta") }
            items(history, key = { "h-" + it.id }) { t ->
                Line(
                    title = t.title,
                    subtitle = listOfNotNull(statusLabel(t.status), t.assigneeLabel).joinToString(" · "),
                    trailing = t.updatedMillis?.let(::day),
                    onClick = { onOpenTask(t.id) },
                )
            }
        }
    }
}

@Composable
private fun TaskRow(item: VehicleTaskItem, onOpenTask: (String) -> Unit) {
    val planned = item.state == VehicleTaskItem.State.PLANNED
    val subtitle = when (item.state) {
        VehicleTaskItem.State.ACTIVE -> item.task?.let {
            "u ${it.assigneeLabel ?: "nikogo"} · ${statusLabel(it.status)}"
        } ?: "aktywne"
        VehicleTaskItem.State.DONE -> "zrobione ${item.doneMillis?.let(::day) ?: ""}"
        VehicleTaskItem.State.PLANNED -> buildString {
            val parts = listOfNotNull(
                item.activatesMillis?.let { "od ${day(it)}" },
                item.kmToActivation?.let { if (it > 0) "za ${km(it)}" else "przy najbliższym przebiegu" },
            )
            if (parts.isNotEmpty()) append("aktywne ${parts.joinToString(" albo ")} · ")
            append("dostanie: ${item.ownerLabel ?: "brak opiekuna"}${if (item.ownerIsDriver) " (kierowca)" else ""}")
        }
    }
    val cycle = listOfNotNull(
        item.recurrenceMonths?.let { "co $it mies." },
        item.recurrenceKm?.let { "co ${km(it)}" },
    ).joinToString(" / ")
    Line(
        title = listOfNotNull(item.kindLabel, item.label).joinToString(" — "),
        subtitle = if (cycle.isEmpty()) subtitle else "$subtitle · $cycle",
        trailing = listOfNotNull(item.dueMillis?.let(::day), item.dueMileage?.let(::km)).joinToString("\n"),
        modifier = if (planned) Modifier.alpha(0.55f) else Modifier,
        onClick = item.task?.let { t -> { onOpenTask(t.id) } },
    )
}

// ── Pliki ───────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VehicleFilesTab(
    state: VehicleCardViewModel.UiState,
    onRetry: () -> Unit,
    onCategory: (String) -> Unit,
    onUpload: (List<com.ekotak.teamtalk.presentation.crm.PickedFile>) -> Unit,
    onOpen: (VehicleFile) -> Unit,
) {
    val pickers = rememberFilePickers(onPicked = onUpload)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Do kategorii:", style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            VEHICLE_FILE_CATEGORIES.filter { it.first != "polisa" }.forEach { (key, label) ->
                FilterChip(
                    selected = state.uploadCategory == key,
                    onClick = { onCategory(key) },
                    label = { Text(label) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = pickers::takePhoto, enabled = !state.uploading) { Text("Zrób zdjęcie") }
            OutlinedButton(onClick = pickers::pickFiles, enabled = !state.uploading) { Text("Wybierz plik") }
            if (state.uploading) CircularProgressIndicator(Modifier.padding(start = 4.dp))
        }
        SlotBody(state.files, onRetry) { files ->
            if (files.isEmpty()) Note("Brak plików. Zrób zdjęcie dowodu rejestracyjnego albo faktury.")
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val grouped = VEHICLE_FILE_CATEGORIES.mapNotNull { (key, label) ->
                    files.filter { it.category == key }.takeIf { it.isNotEmpty() }?.let { label to it }
                }
                grouped.forEach { (label, list) ->
                    item(key = "c-$label") { Section(label) }
                    items(list, key = { it.id }) { f ->
                        Line(
                            title = "📄 ${f.name}",
                            subtitle = listOfNotNull(f.note, f.size?.let(::size)).joinToString(" · "),
                            trailing = f.createdMillis?.let(::day),
                            onClick = { onOpen(f) },
                        )
                    }
                }
            }
        }
    }
}

// ── Historia ────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VehicleHistoryTab(slot: VehicleCardViewModel.Slot<List<VehicleHistoryItem>>, onRetry: () -> Unit) =
    SlotBody(slot, onRetry) { items ->
        var filter by remember { mutableStateOf<String?>(null) }
        val kinds = listOf("alarm" to "Alarmy", "obd" to "Usterki OBD", "rule" to "Reguły", "maintenance" to "Serwis", "event" to "Zdarzenia")
        Column {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("Wszystko (${items.size})") })
                kinds.forEach { (key, label) ->
                    val n = items.count { it.kind == key }
                    if (n > 0) FilterChip(selected = filter == key, onClick = { filter = key }, label = { Text("$label ($n)") })
                }
            }
            if (items.isEmpty()) Note("Pusto. Alarmy lokalizatora, kody usterek i wpisy z reguł pojawią się tu same.")
            LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(items.filter { filter == null || it.kind == filter }, key = { it.id }) { h ->
                    Line(
                        title = h.title,
                        subtitle = listOfNotNull(h.kindLabel, h.detail).joinToString(" · "),
                        trailing = if (h.kind == "maintenance") day(h.atMillis) else dayTime(h.atMillis),
                    )
                }
            }
        }
    }

// ── Reguły ──────────────────────────────────────────────────────────────────

@Composable
fun VehicleRulesTab(slot: VehicleCardViewModel.Slot<List<VehicleRule>>, onRetry: () -> Unit) =
    SlotBody(slot, onRetry) { rules ->
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            item { Note("Reguły ustawia się w panelu (Zasoby → karta auta → Reguły). Przejazdy ponad limit trafiają do Historii.") }
            if (rules.isEmpty()) item { Note("To auto nie ma reguł.") }
            items(rules, key = { it.id }) { r ->
                Line(
                    title = "${r.name} — max ${r.speedLimitKmh} km/h",
                    subtitle = "odcinek ${r.points} pkt, korytarz ${r.widthM} m" + if (r.active) "" else " · wyłączona",
                    trailing = r.lastTriggeredMillis?.let { "ostatnio ${day(it)}" } ?: "nie zadziałała",
                    modifier = if (r.active) Modifier else Modifier.alpha(0.55f),
                )
            }
        }
    }

// ── wspólne ─────────────────────────────────────────────────────────────────

@Composable
private fun Line(
    title: String,
    subtitle: String,
    trailing: String?,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val body: @Composable () -> Unit = {
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                if (subtitle.isNotBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (!trailing.isNullOrBlank()) {
                Text(trailing, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (onClick != null) {
        Surface(onClick = onClick, color = MaterialTheme.colorScheme.surface, modifier = modifier.fillMaxWidth()) { body() }
    } else {
        Surface(color = MaterialTheme.colorScheme.surface, modifier = modifier.fillMaxWidth()) { body() }
    }
}

@Composable
private fun Section(text: String) = Text(
    text,
    modifier = Modifier.padding(top = 10.dp),
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)

@Composable
private fun Note(text: String) = Text(
    text,
    modifier = Modifier.padding(vertical = 6.dp),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)

private fun statusLabel(status: String) = when (status) {
    "open" -> "do zrobienia"
    "in_progress" -> "w toku"
    "done" -> "zrobione"
    else -> status
}

private fun day(millis: Long): String = SimpleDateFormat("d.MM.yyyy", Locale("pl")).format(Date(millis))
private fun dayTime(millis: Long): String = SimpleDateFormat("d.MM HH:mm", Locale("pl")).format(Date(millis))
private fun km(n: Int): String = String.format(Locale("pl"), "%,d km", n)
private fun size(bytes: Long): String =
    if (bytes < 1024 * 1024) "${maxOf(1, bytes / 1024)} KB" else String.format(Locale("pl"), "%.1f MB", bytes / 1048576.0)
