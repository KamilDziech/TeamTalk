package com.ekotak.teamtalk.presentation.meetings

import androidx.activity.compose.BackHandler
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
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
import com.ekotak.teamtalk.data.remote.dto.MeetingContractorCreateRequest
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

    // D15: mikrofon „Co chcesz omówić?" — prośba o RECORD_AUDIO przy pierwszym dotknięciu.
    val context = LocalContext.current
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.startDictation()
    }
    fun micClick() {
        when (s.dictation) {
            MeetingFormViewModel.DictationPhase.RECORDING -> viewModel.stopDictation()
            MeetingFormViewModel.DictationPhase.UPLOADING -> Unit
            MeetingFormViewModel.DictationPhase.IDLE ->
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    viewModel.startDictation()
                } else {
                    micPermission.launch(Manifest.permission.RECORD_AUDIO)
                }
        }
    }

    s.pendingProposal?.let { items ->
        AlertDialog(
            onDismissRequest = viewModel::dismissProposal,
            title = { Text("Zastąpić obecną agendę?") },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    Text(
                        "AI proponuje ${items.size} pkt na ${items.sumOf { it.durationMin }} min:\n" +
                            items.joinToString("\n") { "• ${it.text} (${it.durationMin} min)" },
                    )
                }
            },
            confirmButton = { TextButton(onClick = viewModel::confirmProposal) { Text("Zastąp") } },
            dismissButton = { TextButton(onClick = viewModel::dismissProposal) { Text("Zostaw obecną") } },
        )
    }

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
                val pick = rememberDateTimePicker(
                    if (s.customDuration) "Pierwszy dzień — od godziny" else "Początek spotkania",
                    s.startAtMs,
                ) { viewModel.setStart(it) }
                OutlinedButton(onClick = pick, modifier = Modifier.fillMaxWidth()) {
                    Text((if (s.customDuration) "Pierwszy dzień, od: " else "Początek: ") + formatMillisDateTime(s.startAtMs))
                }
                Text("Czas trwania", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MEETING_DURATION_PRESETS.forEach { m ->
                        FilterChip(
                            selected = !s.customDuration && s.durationMin == m,
                            onClick = { viewModel.setDuration(m) },
                            label = { Text(if (m < 60) "$m min" else "${m / 60.0}".removeSuffix(".0") + " h") },
                        )
                    }
                    FilterChip(
                        selected = s.customDuration,
                        onClick = viewModel::setCustomDuration,
                        label = { Text("Własny…") },
                    )
                }
                if (s.customDuration) CustomDurationPanel(s, viewModel)
                OutlinedTextField(
                    value = s.location,
                    onValueChange = viewModel::setLocation,
                    label = { Text("Miejsce (opcjonalnie)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (typeDef?.needsContractor == true) {
                MeetingCard("Kontrahent") { ContractorPicker(s, viewModel) }
            }

            MeetingCard(if (typeDef?.needsContractor == true) "Osoby z firmy" else "Osoby") {
                // Spotkanie zarządu: do wyboru wyłącznie członkowie zarządu.
                val pool = if (typeDef?.boardOnly == true) meta.people.filter { it.isBoard } else meta.people
                val hosts = pool
                if (typeDef?.boardOnly == true) {
                    Text(
                        "Do wyboru wyłącznie członkowie zarządu.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
                    pool
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
                BriefSection(s, viewModel, onMic = ::micClick)
                // D15: suma czasów = czas spotkania; przekroczenie tylko ostrzega.
                val over = s.scheduledMin > s.plannedMin
                Text(
                    "Rozplanowano ${s.scheduledMin} / ${s.plannedMin} min" +
                        if (over) " — o ${s.scheduledMin - s.plannedMin} min za dużo" else "",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (over) Orange600 else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                s.agenda.forEachIndexed { i, a ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}.", modifier = Modifier.padding(end = 6.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedTextField(
                            value = a.text,
                            onValueChange = { viewModel.setAgendaText(a.key, it) },
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = a.minutes,
                            onValueChange = { viewModel.setAgendaMinutes(a.key, it) },
                            label = { Text("min") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.padding(start = 6.dp).width(68.dp),
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
                enabled = !s.isSaving && s.title.isNotBlank() &&
                    (s.type != "employee" || s.participantIds.isNotEmpty()) &&
                    (typeDef?.needsContractor != true || s.contractor != null),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (s.isSaving) "Zapisuję…" else if (viewModel.editId != null) "Zapisz zmiany" else "Zaplanuj spotkanie")
            }
        }
    }
}

/**
 * Kontrahent z kartoteki (grupy Kontrahenci + Inne) albo szybkie dodanie
 * nowego wpisu do grupy „Inne" (decyzje usera 2026-09-28).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContractorPicker(s: MeetingFormViewModel.State, viewModel: MeetingFormViewModel) {
    val picked = s.contractor
    if (picked != null) {
        Text(picked.name, fontWeight = FontWeight.SemiBold)
        contractorLine(picked).takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedButton(onClick = { viewModel.pickContractor(null) }) { Text("Zmień") }
        return
    }
    if (s.isAddingContractor) {
        var company by remember { mutableStateOf(s.contractorQuery.trim()) }
        var person by remember { mutableStateOf("") }
        var role by remember { mutableStateOf("") }
        var phone by remember { mutableStateOf("") }
        var email by remember { mutableStateOf("") }
        Text(
            "Nowy wpis trafi do kartoteki, do grupy „Inne”.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(company, { company = it }, label = { Text("Firma / pracownia") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(person, { person = it }, label = { Text("Imię i nazwisko") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(role, { role = it }, label = { Text("Kim jest (np. architekt, dostawca)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(phone, { phone = it }, label = { Text("Telefon") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(email, { email = it }, label = { Text("E-mail") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        s.contractorError?.let { NoticeStrip(it, Red600) }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { viewModel.setAddingContractor(false) }) { Text("Wróć do listy") }
            Button(
                enabled = company.isNotBlank() || person.isNotBlank(),
                onClick = {
                    viewModel.createContractor(
                        MeetingContractorCreateRequest(
                            companyName = company.trim().ifBlank { null },
                            personName = person.trim().ifBlank { null },
                            businessRole = role.trim().ifBlank { null },
                            phone = phone.trim().ifBlank { null },
                            email = email.trim().ifBlank { null },
                        ),
                    )
                },
            ) { Text("Dodaj do kartoteki") }
        }
        return
    }
    OutlinedTextField(
        value = s.contractorQuery,
        onValueChange = viewModel::setContractorQuery,
        label = { Text("Szukaj w kartotece (Kontrahenci, Inne)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    if (s.contractorResults.isEmpty()) {
        Text("Brak wyników.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    s.contractorResults.take(20).forEach { c ->
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.fillMaxWidth().clickable { viewModel.pickContractor(c) },
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(c.name, fontWeight = FontWeight.SemiBold)
                contractorLine(c).takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    OutlinedButton(onClick = { viewModel.setAddingContractor(true) }) { Text("+ Nowy kontrahent") }
}

/**
 * „Własny…" (D14): dni × godziny dnia. Początek pierwszego dnia ustawia przycisk
 * „Pierwszy dzień, od" nad chipami; tu liczba dni i godzina końca — ta sama
 * każdego dnia, a durationMin = do − od.
 */
@Composable
private fun CustomDurationPanel(s: MeetingFormViewModel.State, vm: MeetingFormViewModel) {
    var pickingEnd by remember { mutableStateOf(false) }
    val from = minuteOfDay(s.startAtMs)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Liczba dni", modifier = Modifier.weight(1f))
        IconButton(onClick = { vm.setDayCount(s.dayCount - 1) }, enabled = s.dayCount > 1) {
            Icon(Icons.Filled.Remove, contentDescription = "Mniej dni")
        }
        Text("${s.dayCount}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = { vm.setDayCount(s.dayCount + 1) }, enabled = s.dayCount < 14) {
            Icon(Icons.Filled.Add, contentDescription = "Więcej dni")
        }
    }
    OutlinedButton(onClick = { pickingEnd = true }, modifier = Modifier.fillMaxWidth()) {
        Text("Do godziny: ${formatClock(from + s.durationMin)}")
    }
    Text(
        (if (s.dayCount > 1) "${daysLabel(s.dayCount)} · ${dayRangeLabel(s.startAtMs, s.durationMin)} każdego dnia"
        else dayRangeLabel(s.startAtMs, s.durationMin)) + " · razem ${s.plannedMin} min",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (pickingEnd) {
        EndTimeDialog(
            initialMinuteOfDay = (from + s.durationMin).coerceAtMost(23 * 60 + 59),
            onDismiss = { pickingEnd = false },
            onPick = { h, m -> vm.setEndClock(h, m); pickingEnd = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EndTimeDialog(initialMinuteOfDay: Int, onDismiss: () -> Unit, onPick: (Int, Int) -> Unit) {
    val state = rememberTimePickerState(
        initialHour = initialMinuteOfDay / 60,
        initialMinute = initialMinuteOfDay % 60,
        is24Hour = true,
    )
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(20.dp)) {
                Text("Koniec każdego dnia", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                TimePicker(state = state)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Anuluj") }
                    TextButton(onClick = { onPick(state.hour, state.minute) }) { Text("Ustaw") }
                }
            }
        }
    }
}

/**
 * „Co chcesz omówić?" (D15): wpis albo dyktowanie (Whisper u nas), tekst zawsze
 * edytowalny; „Zaproponuj agendę" rozpisuje go na punkty z minutami.
 */
@Composable
private fun BriefSection(s: MeetingFormViewModel.State, vm: MeetingFormViewModel, onMic: () -> Unit) {
    val phase = s.dictation
    OutlinedTextField(
        value = s.brief,
        onValueChange = vm::setBrief,
        label = { Text("Co chcesz omówić?") },
        placeholder = { Text("Wpisz albo podyktuj — AI rozpisze to na punkty z czasem.") },
        minLines = 3,
        enabled = phase != MeetingFormViewModel.DictationPhase.UPLOADING,
        trailingIcon = {
            IconButton(onClick = onMic, enabled = phase != MeetingFormViewModel.DictationPhase.UPLOADING) {
                when (phase) {
                    MeetingFormViewModel.DictationPhase.RECORDING ->
                        Icon(Icons.Filled.Stop, contentDescription = "Zatrzymaj dyktowanie", tint = Red600)
                    MeetingFormViewModel.DictationPhase.UPLOADING ->
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    MeetingFormViewModel.DictationPhase.IDLE ->
                        Icon(Icons.Filled.Mic, contentDescription = "Dyktuj")
                }
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
    when (phase) {
        MeetingFormViewModel.DictationPhase.RECORDING -> Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = Red600, modifier = Modifier.size(8.dp)) {}
            Text(
                "  Nagrywam… ${formatElapsed(s.dictationSec.toLong())} — dotknij ■, żeby spisać",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        MeetingFormViewModel.DictationPhase.UPLOADING ->
            Text("Spisuję…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MeetingFormViewModel.DictationPhase.IDLE -> s.dictationError?.let { NoticeStrip(it, Orange600) }
    }
    OutlinedButton(
        onClick = vm::proposeAgenda,
        enabled = s.brief.isNotBlank() && !s.isProposing && phase == MeetingFormViewModel.DictationPhase.IDLE,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (s.isProposing) {
            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
            Text("  Układam agendę…")
        } else {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
            Text("  Zaproponuj agendę (${s.plannedMin} min)")
        }
    }
    s.proposalError?.let { NoticeStrip(it, Orange600) }
}
