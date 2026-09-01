package com.ekotak.teamtalk.presentation.postcallnote

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.ekotak.teamtalk.presentation.components.AppTopBar
import com.ekotak.teamtalk.presentation.theme.Green600
import com.ekotak.teamtalk.presentation.theme.Red600
import com.ekotak.teamtalk.presentation.voicereport.NoteMode
import com.ekotak.teamtalk.presentation.voicereport.RecordingState

/**
 * Kreator po zakończonej rozmowie — trzy plansze: z kim rozmawiałeś, streszczenie
 * rozmowy, decyzja o zadaniu. Jedno pytanie na ekran, bo wywołuje go powiadomienie
 * tuż po odłożeniu słuchawki, a nie świadome wejście w formularz.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostCallNoteScreen(
    onNavigateBack: () -> Unit,
    onAddContact: (phone: String, suggestedName: String?) -> Unit = { _, _ -> },
    onCreateTask: (PostCallNoteViewModel.TaskHandoff) -> Unit = {},
    /** Id klienta założonego na formularzu, wracające z ekranu potomnego. */
    newClientId: String? = null,
    onNewClientConsumed: () -> Unit = {},
    viewModel: PostCallNoteViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val noteMode by viewModel.noteMode.collectAsState()
    val recordingState by viewModel.recordingState.collectAsState()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) viewModel.startRecording() }

    fun handleMicClick() {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.startRecording()
        else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(newClientId) {
        if (newClientId != null) {
            viewModel.onContactCreated(newClientId)
            onNewClientConsumed()
        }
    }

    LaunchedEffect(state.isFinished) {
        if (state.isFinished) onNavigateBack()
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = when (state.step) {
                    PostCallStep.CONTACT -> "Rozmówca"
                    PostCallStep.SUMMARY -> "Streść rozmowę"
                    PostCallStep.TASK -> "Zadanie"
                },
                onNavigateBack = {
                    when (state.step) {
                        PostCallStep.CONTACT -> viewModel.askSkip()
                        PostCallStep.SUMMARY ->
                            if (state.canBackToContact) viewModel.backToContact()
                            else onNavigateBack()
                        // Notatka jest już zapisana — cofać nie ma po co.
                        PostCallStep.TASK -> onNavigateBack()
                    }
                },
                actions = {
                    Text(
                        text = "${state.step.ordinal + 1} / ${PostCallStep.entries.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            StepProgress(state.step)

            if (viewModel.phone.isNotBlank()) {
                CallerHeader(
                    name = state.displayName ?: state.client?.displayName,
                    phone = viewModel.phone,
                )
            }

            when (state.step) {
                PostCallStep.CONTACT -> ContactStep(
                    state = state,
                    phone = viewModel.phone,
                    onYes = viewModel::confirmContact,
                    onAddContact = { onAddContact(viewModel.phone, state.suggestedName) },
                    onSkip = viewModel::askSkip,
                )

                PostCallStep.SUMMARY -> SummaryStep(
                    state = state,
                    noteMode = noteMode,
                    recordingState = recordingState,
                    onModeChange = viewModel::setNoteMode,
                    onTextChange = viewModel::onNoteTextChange,
                    onSave = viewModel::saveNote,
                    onMicClick = ::handleMicClick,
                    onStop = viewModel::stopRecording,
                    onRecordAgain = viewModel::resetToVoice,
                )

                PostCallStep.TASK -> TaskStep(
                    onYes = { onCreateTask(viewModel.taskHandoff()) },
                    onNo = viewModel::declineTask,
                )
            }
        }
    }

    if (state.showSkipConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissSkip,
            title = { Text("Na pewno chcesz pominąć dodanie notatki?") },
            text = {
                Text(
                    "Rozmowa zostanie w historii połączeń, ale bez streszczenia " +
                        "w komunikacji klienta.",
                )
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmSkip) { Text("Tak, pomiń") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissSkip) { Text("Wróć") }
            },
        )
    }
}

// ── Ramy kreatora ────────────────────────────────────────────────────────────

/** Pasek postępu: kreska na każdą planszę, zielone to, co za nami. */
@Composable
private fun StepProgress(step: PostCallStep) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PostCallStep.entries.forEach { s ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
                    .background(
                        color = if (s.ordinal <= step.ordinal) Green600
                        else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(2.dp),
                    ),
            )
        }
    }
}

@Composable
private fun Question(text: String, hint: String? = null) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        if (hint != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = hint,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun CallerHeader(name: String?, phone: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = (name?.firstOrNull() ?: phone.firstOrNull() ?: '?').uppercaseChar().toString(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = name ?: phone,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                if (name != null) {
                    Text(
                        text = phone,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

// ── Plansza 1: rozmówca ──────────────────────────────────────────────────────

/**
 * Pytanie różni się w zależności od tego, czy numer jest w kartotece: przy znanym
 * kliencie potwierdzamy rozmówcę, przy nieznanym nie ma czego potwierdzać —
 * pytamy wprost o założenie kontaktu.
 */
@Composable
private fun ContactStep(
    state: PostCallNoteViewModel.UiState,
    phone: String,
    onYes: () -> Unit,
    onAddContact: () -> Unit,
    onSkip: () -> Unit,
) {
    if (state.isResolvingClient) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))

        if (state.isKnownClient) {
            Question(
                text = "Czy rozmawiałeś z ${state.client?.displayName.orEmpty()}?",
                hint = "Ten numer jest przypisany do tej osoby w module Klienci.",
            )
        } else {
            Question(
                text = "Numer $phone nie jest w module Klienci",
                hint = state.suggestedName
                    ?.let { "W kontaktach telefonu: $it. Dodaj go do kartoteki, żeby streszczenie rozmowy trafiło pod klienta." }
                    ?: "Dodaj kontakt, żeby streszczenie rozmowy trafiło pod klienta.",
            )
        }

        Spacer(Modifier.height(32.dp))

        if (state.isKnownClient) {
            Button(
                onClick = onYes,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Green600),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Tak", style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.height(8.dp))
        }

        OutlinedButton(
            onClick = onAddContact,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(Icons.Default.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (state.isKnownClient) "Nie — dodaj kontakt" else "Dodaj kontakt")
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = onSkip,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Text("Pomiń")
        }
    }
}

// ── Plansza 2: streszczenie ──────────────────────────────────────────────────

@Composable
private fun SummaryStep(
    state: PostCallNoteViewModel.UiState,
    noteMode: NoteMode,
    recordingState: RecordingState,
    onModeChange: (NoteMode) -> Unit,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onMicClick: () -> Unit,
    onStop: () -> Unit,
    onRecordAgain: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        val showTabs =
            recordingState is RecordingState.Idle || recordingState is RecordingState.Processing
        AnimatedVisibility(visible = showTabs, enter = fadeIn(), exit = fadeOut()) {
            TabRow(selectedTabIndex = if (noteMode == NoteMode.VOICE) 0 else 1) {
                Tab(
                    selected = noteMode == NoteMode.VOICE,
                    onClick = { onModeChange(NoteMode.VOICE) },
                    icon = { Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    text = { Text("Głosowa") },
                )
                Tab(
                    selected = noteMode == NoteMode.TEXT,
                    onClick = { onModeChange(NoteMode.TEXT) },
                    icon = { Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    text = { Text("Tekstowa") },
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            when {
                noteMode == NoteMode.TEXT || recordingState is RecordingState.Processing ->
                    TextNoteContent(
                        text = state.noteText,
                        error = state.error,
                        isLoading = state.isLoading,
                        isProcessingVoice = recordingState is RecordingState.Processing,
                        onTextChange = onTextChange,
                        onSave = onSave,
                        onRecordAgain = onRecordAgain,
                    )
                else ->
                    VoiceNoteContent(
                        recordingState = recordingState,
                        liveText = state.noteText,
                        onMicClick = onMicClick,
                        onStop = onStop,
                    )
            }
        }
    }
}

@Composable
private fun TextNoteContent(
    text: String,
    error: String?,
    isLoading: Boolean,
    isProcessingVoice: Boolean,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onRecordAgain: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .imePadding()
            .verticalScroll(rememberScrollState()),
    ) {
        if (isProcessingVoice) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Text("Transkrypcja nagrania...", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                label = { Text("Streszczenie rozmowy") },
                placeholder = { Text("Co ustalono podczas rozmowy?") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 5,
                maxLines = 12,
                isError = error != null,
                supportingText = error?.let { { Text(it) } },
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onSave,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = !isLoading && text.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Green600),
                shape = RoundedCornerShape(12.dp),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Zapisz i dalej", style = MaterialTheme.typography.labelLarge)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onRecordAgain,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = !isLoading,
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Nagraj ponownie")
            }
        }
    }
}

@Composable
private fun VoiceNoteContent(
    recordingState: RecordingState,
    liveText: String,
    onMicClick: () -> Unit,
    onStop: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(1f))

        when (recordingState) {
            is RecordingState.Idle -> IdleVoicePanel(onMicClick = onMicClick)
            is RecordingState.Recording -> RecordingActivePanel(
                state = recordingState,
                liveText = liveText,
                onStop = onStop,
            )
            else -> {}
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun IdleVoicePanel(onMicClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(onClick = onMicClick, modifier = Modifier.size(88.dp)) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Nagraj",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(44.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Naciśnij aby nagrać",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RecordingActivePanel(
    state: RecordingState.Recording,
    liveText: String,
    onStop: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Red600),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Słucham  ${state.durationSeconds.toTimeString()}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Red600,
            )
        }
        if (liveText.isNotBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(
                    text = liveText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(Red600),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(onClick = onStop, modifier = Modifier.size(88.dp)) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Zatrzymaj",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Naciśnij aby zakończyć",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Plansza 3: zadanie ───────────────────────────────────────────────────────

@Composable
private fun TaskStep(onYes: () -> Unit, onNo: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))

        Question(
            text = "Czy chcesz utworzyć zadanie?",
            hint = "Streszczenie jest już zapisane w komunikacji klienta. " +
                "Zadanie zapytamy o zespół, osobę, priorytet i termin.",
        )

        Spacer(Modifier.height(32.dp))

        Button(
            onClick = onYes,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Green600),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Tak", style = MaterialTheme.typography.labelLarge)
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = onNo,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(12.dp),
        ) {
            Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Nie")
        }
    }
}

private fun Int.toTimeString(): String {
    val m = this / 60
    val s = this % 60
    return "%d:%02d".format(m, s)
}
