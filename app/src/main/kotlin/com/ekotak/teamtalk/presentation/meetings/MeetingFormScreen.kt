package com.ekotak.teamtalk.presentation.meetings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ekotak.teamtalk.presentation.components.AppTopBar
import com.ekotak.teamtalk.presentation.crm.formatMillisDateTime
import com.ekotak.teamtalk.presentation.crm.formatDateTime
import com.ekotak.teamtalk.presentation.crm.rememberDateTimePicker
import com.ekotak.teamtalk.presentation.theme.Orange600
import com.ekotak.teamtalk.presentation.theme.Red600

/**
 * Kreator spotkania: kafelek rodzaju → termin, osoby → agenda. Ta sama forma
 * edytuje zaplanowane spotkanie. Kolizje tylko ostrzegają (D4); agenda może
 * zostać pusta, ale bez niej spotkania nie da się włączyć (D5).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MeetingFormScreen(
    onNavigateBack: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: MeetingFormViewModel = hiltViewModel(),
) {
    val s by viewModel.state.collectAsState()
    LaunchedEffect(s.savedId) { s.savedId?.let(onSaved) }
    BackHandler(enabled = s.type != null && viewModel.editId == null) { viewModel.clearType() }

    Scaffold(topBar = { AppTopBar(title = "Nowe spotkanie", onNavigateBack = onNavigateBack) }) { padding ->
        val meta = s.meta
        if (s.isLoading || meta == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (s.isLoading) CircularProgressIndicator() else Text(s.error ?: "")
            }
            return@Scaffold
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (viewModel.editId != null) "Edycja spotkania" else "Nowe spotkanie",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            if (s.type == null || viewModel.editId != null) {
                if (s.type == null) Text("Wybierz rodzaj spotkania.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                meta.types.forEach { t ->
                    val c = meetingTypeColor(t.key)
                    val selected = t.key == s.type
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) c else MaterialTheme.colorScheme.outline),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = t.canCreate) { viewModel.pickType(t.key) },
                    ) {
                        Row(
                            Modifier.padding(14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TypeBadge(t.key, 44.dp)
                            Column(Modifier.weight(1f)) {
                                Text(t.label, fontWeight = FontWeight.SemiBold)
                                Text(
                                    if (t.canCreate) meetingTypeHint(t.key) else "Tylko dla zarządu.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            if (s.type == null) return@Column

            val typeDef = meta.types.firstOrNull { it.key == s.type }
            MeetingCard("Termin") {
                OutlinedTextField(
                    value = s.title,
                    onValueChange = viewModel::setTitle,
                    label = { Text("Temat") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                val pick = rememberDateTimePicker("Początek spotkania", s.startAtMs) { viewModel.setStart(it) }
                OutlinedButton(onClick = pick, modifier = Modifier.fillMaxWidth()) {
                    Text("Początek: ${formatMillisDateTime(s.startAtMs)}")
                }
                Text("Czas trwania", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(30, 45, 60, 90, 120, 180).forEach { m ->
                        FilterChip(
                            selected = s.durationMin == m,
                            onClick = { viewModel.setDuration(m) },
                            label = { Text(if (m < 60) "$m min" else "${m / 60.0}".removeSuffix(".0") + " h") },
                        )
                    }
                }
                OutlinedTextField(
                    value = s.location,
                    onValueChange = viewModel::setLocation,
                    label = { Text("Miejsce (opcjonalnie)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            MeetingCard("Osoby") {
                val hosts = if (typeDef?.boardOnly == true) meta.people.filter { it.isBoard } else meta.people
                var hostOpen by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(onClick = { hostOpen = true }, modifier = Modifier.fillMaxWidth()) {
                        val name = meta.people.firstOrNull { it.id == s.hostId }?.name ?: "—"
                        Text("Prowadzi: $name" + if (s.hostId == meta.me.id) " (Ty)" else "")
                    }
                    DropdownMenu(expanded = hostOpen, onDismissRequest = { hostOpen = false }) {
                        hosts.forEach { p ->
                            DropdownMenuItem(
                                text = { Text(p.name + if (p.id == meta.me.id) " (Ty)" else "") },
                                onClick = { viewModel.setHost(p.id); hostOpen = false },
                            )
                        }
                    }
                }
                Text(
                    if (s.type == "employee") "Pracownik i osoby towarzyszące" else "Osoby towarzyszące",
                    style = MaterialTheme.typography.labelMedium,
                )
                var query by remember { mutableStateOf("") }
                if (s.participantIds.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        s.participantIds.forEach { id ->
                            FilterChip(
                                selected = true,
                                onClick = { viewModel.toggleParticipant(id) },
                                label = { Text((meta.people.firstOrNull { it.id == id }?.name ?: "—") + "  ✕") },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Szukaj osoby") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    meta.people
                        .filter { it.id != s.hostId && it.id !in s.participantIds && it.name.contains(query.trim(), ignoreCase = true) }
                        .take(if (query.isBlank()) 12 else 40)
                        .forEach { p ->
                            FilterChip(selected = false, onClick = { viewModel.toggleParticipant(p.id) }, label = { Text("+ ${p.name}") })
                        }
                }
                if (s.conflicts.isNotEmpty()) {
                    NoticeStrip(
                        "Kolizje w kalendarzu (spotkanie da się zapisać):\n" +
                            s.conflicts.joinToString("\n") { c ->
                                "• ${c.name}: ${formatDateTime(c.startAt)?.takeLast(5)}–${formatDateTime(c.endAt)?.takeLast(5)}"
                            },
                        Orange600,
                    )
                }
            }

            MeetingCard("Agenda") {
                Text(
                    "Lista do odhaczania w trakcie. Bez co najmniej jednego punktu spotkania nie da się włączyć.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                s.agenda.forEachIndexed { i, a ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}.", modifier = Modifier.padding(end = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value = a.text,
                            onValueChange = { viewModel.setAgendaText(a.key, it) },
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { viewModel.moveAgendaUp(a.key) }, enabled = i > 0) {
                            Icon(Icons.Filled.ArrowUpward, contentDescription = "W górę")
                        }
                        IconButton(onClick = { viewModel.removeAgenda(a.key) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Usuń punkt")
                        }
                    }
                }
                OutlinedButton(onClick = viewModel::addAgenda) { Text("+ Punkt agendy") }
            }

            s.error?.let { NoticeStrip(it, Red600) }
            Button(
                onClick = viewModel::save,
                enabled = !s.isSaving && s.title.isNotBlank() && (s.type != "employee" || s.participantIds.isNotEmpty()),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (s.isSaving) "Zapisuję…" else if (viewModel.editId != null) "Zapisz zmiany" else "Zaplanuj spotkanie")
            }
        }
    }
}
