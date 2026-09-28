package com.ekotak.teamtalk.presentation.meetings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.ekotak.teamtalk.data.meeting.MeetingRecorder
import com.ekotak.teamtalk.data.remote.dto.MeetingDto
import com.ekotak.teamtalk.data.remote.dto.MeetingMetaDto
import com.ekotak.teamtalk.presentation.components.AppTopBar
import com.ekotak.teamtalk.presentation.crm.formatDateTime
import com.ekotak.teamtalk.presentation.theme.Orange600
import com.ekotak.teamtalk.presentation.theme.Red600
import kotlinx.coroutines.delay

/**
 * Karta spotkania — jeden ekran na cały cykl: plan (RSVP, agenda, „Włącz"),
 * przebieg (zegar, odhaczanie, Pauza/Wznów/Zakończ), przetwarzanie i akceptacja
 * podsumowania z propozycjami zadań.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MeetingDetailScreen(
    onNavigateBack: () -> Unit,
    onEdit: (String) -> Unit,
    viewModel: MeetingDetailViewModel = hiltViewModel(),
) {
    val s by viewModel.state.collectAsState()
    val rec by viewModel.recorderState.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var confirm by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }

    LaunchedEffect(s.deleted) { if (s.deleted) onNavigateBack() }
    LaunchedEffect(s.message) {
        s.message?.let { snackbar.showSnackbar(it); viewModel.clearMessage() }
    }

    val permissions = remember {
        buildList {
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        }.toTypedArray()
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.RECORD_AUDIO] == true) viewModel.startRecording()
    }
    fun startWithPermissions() {
        val mic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val notif = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (mic && notif) viewModel.startRecording() else launcher.launch(permissions)
    }
    // D9: podsumowanie głosowe — sam mikrofon, nagrywa przy otwartym oknie.
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.startVoice()
    }
    fun voiceWithPermission() {
        val mic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (mic) viewModel.startVoice() else voiceLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    Scaffold(
        topBar = { AppTopBar(title = "Spotkanie", onNavigateBack = onNavigateBack) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val m = s.meeting
        if (m == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (s.isLoading) CircularProgressIndicator() else Text(s.error ?: "Nie ma takiego spotkania.")
            }
            return@Scaffold
        }
        val recordingHere = rec.meetingId == m.id && rec.phase != MeetingRecorder.Phase.IDLE
        // D9: brak nagrania → okno „Nagraj podsumowanie" (samo, gdy wymagane; wcześniej z przycisku).
        val need = m.voiceSummary
        if (need != null && (need.required || s.voiceOpen) && !s.voiceQueued && !recordingHere) {
            MeetingVoiceSummaryDialog(
                m = m,
                need = need,
                voice = s.voice,
                busy = s.isBusy,
                elapsedMs = viewModel::voiceElapsedMs,
                onRecord = ::voiceWithPermission,
                onStop = viewModel::stopVoice,
                onSend = viewModel::sendVoice,
                onRetry = viewModel::retry,
                onReschedule = { onEdit(m.id) },
                onDelete = { confirm = "Usunąć spotkanie? Wpisy znikną z kalendarzy uczestników." to viewModel::delete },
                onClose = if (need.required) null else viewModel::closeVoice,
            )
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
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TypeBadge(m.type, 44.dp)
                Column(Modifier.weight(1f)) {
                    Text(m.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        m.typeLabel + (m.client?.let { " · ${it.name}" } ?: "") +
                            " · ${formatDateTime(m.startAt) ?: ""} · ${m.durationMin} min" +
                            (m.location?.let { " · $it" } ?: "") + if (m.confidential) " · poufne" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                StatusPill(m.status)
            }

            s.error?.let { NoticeStrip(it, Red600) }

            if (m.status == "live" || m.status == "paused") {
                LivePanel(m, s.fetchedAtMs, rec, recordingHere, onPause = viewModel::pause, onResume = viewModel::resume) {
                    confirm = "Zakończyć spotkanie? Nagranie trafi do transkrypcji." to viewModel::finish
                }
            }

            when (m.status) {
                "processing" -> MeetingCard {
                    Text(
                        when {
                            m.recordingKind == "summary" && m.transcriptionStatus == "processing" -> "Trwa transkrypcja podsumowania głosowego… Podsumowanie pojawi się tu samo."
                            m.recordingKind == "summary" -> "Podsumowanie głosowe czeka w kolejce do transkrypcji."
                            m.hasRecording && m.transcriptionStatus == "processing" -> "Trwa transkrypcja nagrania… Podsumowanie pojawi się tu samo."
                            m.hasRecording -> "Nagranie czeka w kolejce do transkrypcji."
                            m.recordingDevice == "phone" -> "Czekamy na nagranie z telefonu — wyśle się samo, gdy będzie sieć."
                            else -> "Czekamy na nagranie z urządzenia, które nagrywało."
                        },
                    )
                    m.transcriptionError?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    if (s.voiceQueued) {
                        NoticeStrip("Podsumowanie głosowe czeka na sieć — wyśle się samo.", Orange600)
                    } else if (m.voiceSummary != null) {
                        OutlinedButton(onClick = viewModel::openVoice) { Text("Nagranie przepadło — nagraj podsumowanie") }
                    }
                }
                "failed" -> MeetingCard {
                    NoticeStrip(m.transcriptionError ?: "Nie udało się spisać nagrania.", Red600)
                    val voice = m.voiceSummary
                    if (s.voiceQueued) {
                        NoticeStrip("Podsumowanie głosowe czeka na sieć — wyśle się samo.", Orange600)
                    } else if (voice != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (voice.canRetry) {
                                OutlinedButton(onClick = viewModel::retry, enabled = !s.isBusy) { Text("Ponów") }
                            }
                            Button(onClick = viewModel::openVoice, enabled = !s.isBusy) { Text("●  Nagraj podsumowanie") }
                        }
                    }
                }
                "review" -> if (m.canApprove && s.meta != null) {
                    ReviewSection(s, s.meta!!, viewModel) {
                        confirm = "Zaakceptować podsumowanie? Nagranie i transkrypcja zostaną trwale usunięte, a zatwierdzone zadania trafią do modułu Zadania." to viewModel::approve
                    }
                } else {
                    NoticeStrip("Podsumowanie czeka na akceptację prowadzącego (${m.host.name}).", Orange600)
                }
                "approved" -> ApprovedSection(m)
            }

            if (m.status != "review") {
                MeetingCard("Agenda (${m.agenda.count { it.done }}/${m.agenda.size})") {
                    if (m.agenda.isEmpty()) {
                        NoticeStrip("Brak agendy — dodaj co najmniej jeden punkt, zanim włączysz spotkanie.", Orange600)
                    }
                    m.agenda.forEachIndexed { i, a ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(enabled = m.canControl && m.status != "approved") { viewModel.toggleAgenda(a.id, !a.done) },
                        ) {
                            Checkbox(
                                checked = a.done,
                                onCheckedChange = { viewModel.toggleAgenda(a.id, it) },
                                enabled = m.canControl && m.status != "approved",
                            )
                            Text(
                                "${i + 1}. ${a.text}",
                                textDecoration = if (a.done) TextDecoration.LineThrough else null,
                                color = if (a.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            if (m.status == "scheduled") {
                if (!m.isHost && m.myRsvp != null) {
                    MeetingCard("Twoja obecność: ${rsvpLabel(m.myRsvp)}") {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = m.myRsvp == "accepted", onClick = { viewModel.rsvp("accepted") }, label = { Text("Będę") })
                            FilterChip(selected = m.myRsvp == "tentative", onClick = { viewModel.rsvp("tentative") }, label = { Text("Może") })
                            FilterChip(selected = m.myRsvp == "declined", onClick = { viewModel.rsvp("declined") }, label = { Text("Nie będę") })
                        }
                    }
                }
                if (m.canControl) {
                    MeetingCard("Włączenie spotkania") {
                        Text(
                            "Telefon nagrywa w tle — także przy zablokowanym ekranie. Pauza i Zakończ są też w powiadomieniu.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Button(
                            onClick = ::startWithPermissions,
                            enabled = !s.isBusy && m.agenda.isNotEmpty(),
                            colors = ButtonDefaults.buttonColors(containerColor = Red600),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("●  Włącz i nagrywaj") }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onEdit(m.id) }, modifier = Modifier.weight(1f)) { Text("Edytuj") }
                            OutlinedButton(
                                onClick = { confirm = "Usunąć spotkanie? Wpisy znikną z kalendarzy uczestników." to viewModel::delete },
                                modifier = Modifier.weight(1f),
                            ) { Text("Usuń") }
                        }
                    }
                }
            }

            m.client?.let { c ->
                MeetingCard("Kontrahent") {
                    Text(c.name, fontWeight = FontWeight.SemiBold)
                    contractorLine(c).takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    c.email?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            MeetingCard("Uczestnicy") {
                m.participants.forEach { p ->
                    Row(Modifier.fillMaxWidth()) {
                        Text(p.name + if (p.role == "host") " · prowadzi" else "", modifier = Modifier.weight(1f))
                        Text(rsvpLabel(p.rsvp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    confirm?.let { (text, action) ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { confirm = null; action() }) { Text("Tak") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Anuluj") } },
        )
    }
}

@Composable
private fun LivePanel(
    m: MeetingDto,
    fetchedAtMs: Long,
    rec: MeetingRecorder.State,
    recordingHere: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onFinish: () -> Unit,
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(m.status) {
        while (true) { now = System.currentTimeMillis(); delay(1000) }
    }
    val elapsed = when {
        recordingHere -> rec.recordedNowMs() / 1000
        m.status == "live" -> m.elapsedSec + (now - fetchedAtMs) / 1000
        else -> m.elapsedSec
    }
    val paused = m.status == "paused"
    MeetingCard {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = if (paused) Orange600 else Red600, modifier = Modifier.size(10.dp)) {}
                Text(
                    "  " + (if (paused) "Pauza — nagrywanie wstrzymane" else "Nagrywanie trwa") +
                        when {
                            recordingHere -> " (ten telefon)"
                            m.recordingDevice == "web" -> " (przeglądarka)"
                            m.recordingDevice == "phone" -> " (telefon)"
                            else -> ""
                        },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(formatElapsed(elapsed), fontSize = 44.sp, fontWeight = FontWeight.Bold)
            if (m.canControl) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    if (paused) {
                        OutlinedButton(onClick = onResume, modifier = Modifier.weight(1f)) { Text("▶  Wznów") }
                    } else {
                        OutlinedButton(onClick = onPause, modifier = Modifier.weight(1f)) { Text("❚❚  Pauza") }
                    }
                    Button(
                        onClick = onFinish,
                        colors = ButtonDefaults.buttonColors(containerColor = Red600),
                        modifier = Modifier.weight(1f),
                    ) { Text("■  Zakończ") }
                }
            }
            if (recordingHere) {
                Text(
                    "Możesz zablokować ekran albo wyjść z aplikacji — nagranie trwa w tle.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewSection(
    s: MeetingDetailViewModel.State,
    meta: MeetingMetaDto,
    vm: MeetingDetailViewModel,
    onApprove: () -> Unit,
) {
    val m = s.meeting ?: return
    m.transcriptionError?.let { NoticeStrip(it, Orange600) }
    MeetingCard("Podsumowanie") {
        OutlinedTextField(
            value = s.summary,
            onValueChange = vm::setSummary,
            label = { Text("O czym było spotkanie i co z niego wynikło") },
            minLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = s.decisions,
            onValueChange = vm::setDecisions,
            label = { Text("Ustalenia i decyzje (każde w osobnej linii)") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
    }
    MeetingCard("Agenda — co omówiono") {
        m.agenda.forEachIndexed { i, a ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Checkbox(checked = s.agendaDone[a.id] == true, onCheckedChange = { vm.setAgendaDone(a.id, it) })
                Text("${i + 1}. ${a.text}", modifier = Modifier.weight(1f))
                a.aiDiscussed?.let {
                    Text(
                        if (it) "AI: omówiony" else "AI: nie",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    val undecided = s.proposals.count { it.status == "proposed" }
    MeetingCard("Propozycje zadań" + if (undecided > 0) " · $undecided bez decyzji" else "") {
        if (s.proposals.isEmpty()) Text("Brak propozycji. Możesz dopisać zadanie ręcznie.", style = MaterialTheme.typography.bodySmall)
        val participants = m.participants.map { it.id }.toSet()
        val people = meta.people.sortedByDescending { it.id in participants }
        s.proposals.forEach { p ->
            val border = when (p.status) {
                "accepted" -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.outline
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (p.status == "rejected") 0.4f else 1f),
                border = BorderStroke(1.dp, border),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = p.title,
                        onValueChange = { v -> vm.patchProposal(p.key) { it.copy(title = v) } },
                        label = { Text(if (p.source == "ai") "Nazwa (propozycja AI)" else "Nazwa") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PickerButton(
                        label = "Obszar",
                        value = p.area ?: "— bez obszaru —",
                        options = listOf<String?>(null) + meta.departments,
                        optionLabel = { it ?: "— bez obszaru —" },
                    ) { v -> vm.patchProposal(p.key) { it.copy(area = v) } }
                    PickerButton(
                        label = "Osoba odpowiedzialna",
                        value = people.firstOrNull { it.id == p.assigneeId }?.name ?: "— nieprzypisane —",
                        options = listOf<String?>(null) + people.map { it.id },
                        optionLabel = { id ->
                            if (id == null) "— nieprzypisane —"
                            else (people.firstOrNull { it.id == id }?.name ?: "—") + if (id in participants) " · uczestnik" else ""
                        },
                    ) { v -> vm.patchProposal(p.key) { it.copy(assigneeId = v) } }
                    OutlinedTextField(
                        value = p.durationDays,
                        onValueChange = { v -> vm.patchProposal(p.key) { it.copy(durationDays = v.filter(Char::isDigit).take(3)) } },
                        label = { Text("Czas na realizację (dni)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = p.description,
                        onValueChange = { v -> vm.patchProposal(p.key) { it.copy(description = v) } },
                        label = { Text("Opis (opcjonalnie)") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = p.status == "accepted",
                            onClick = { vm.patchProposal(p.key) { it.copy(status = "accepted") } },
                            label = { Text("✓ Zatwierdź") },
                        )
                        FilterChip(
                            selected = p.status == "rejected",
                            onClick = { vm.patchProposal(p.key) { it.copy(status = "rejected") } },
                            label = { Text("✕ Odrzuć") },
                        )
                    }
                }
            }
        }
        OutlinedButton(onClick = vm::addProposal) { Text("+ Dodaj zadanie") }
    }
    m.transcript?.let { t ->
        var open by remember { mutableStateOf(false) }
        MeetingCard {
            Text(
                (if (open) "▾ " else "▸ ") + "Transkrypcja (zniknie po akceptacji)",
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { open = !open },
            )
            if (open) Text(t, style = MaterialTheme.typography.bodySmall)
        }
    }
    NoticeStrip("Akceptacja trwale usuwa nagranie i transkrypcję. Zostaje podsumowanie i zadania.", Orange600)
    Button(onClick = onApprove, enabled = !s.isBusy && undecided == 0, modifier = Modifier.fillMaxWidth()) {
        Text(if (s.isBusy) "Zapisuję…" else "Akceptuj podsumowanie")
    }
}

@Composable
private fun <T> PickerButton(
    label: String,
    value: String,
    options: List<T>,
    optionLabel: (T) -> String,
    onPick: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text("$label: $value") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { o ->
                DropdownMenuItem(text = { Text(optionLabel(o)) }, onClick = { onPick(o); open = false })
            }
        }
    }
}

@Composable
private fun ApprovedSection(m: MeetingDto) {
    MeetingCard("Podsumowanie") {
        Text(
            "Zatwierdził(a) ${m.approvedByName ?: "—"} · ${formatDateTime(m.approvedAt) ?: ""}. Nagranie i transkrypcja zostały usunięte.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(m.summary ?: "Bez podsumowania.")
        if (m.decisions.isNotEmpty()) {
            Text("Ustalenia", fontWeight = FontWeight.SemiBold)
            m.decisions.forEach { Text("• $it") }
        }
        val accepted = m.proposals.filter { it.status == "accepted" }
        if (accepted.isNotEmpty()) {
            Text("Zadania", fontWeight = FontWeight.SemiBold)
            accepted.forEach { p ->
                Text(
                    "• ${p.title}" + (p.area?.let { " · $it" } ?: "") + " — ${p.assigneeName ?: "bez osoby"}" +
                        (p.durationDays?.let { " · $it dni" } ?: ""),
                )
            }
        }
    }
}
