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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TextFields
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.ekotak.teamtalk.presentation.theme.Green600
import com.ekotak.teamtalk.presentation.theme.Red600
import com.ekotak.teamtalk.presentation.voicereport.NoteMode
import com.ekotak.teamtalk.presentation.voicereport.RecordingState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PostCallNoteScreen(
    onNavigateBack: () -> Unit,
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

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onNavigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notatka z rozmowy") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Wróć",
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            // ── Caller info ────────────────────────────────────────────────
            if (viewModel.phone.isNotBlank()) {
                CallerHeader(
                    name = state.displayName ?: state.client?.name,
                    phone = viewModel.phone,
                )
            }

            // ── Mode tab selector ──────────────────────────────────────────
            val showTabs = recordingState is RecordingState.Idle || recordingState is RecordingState.Processing
            AnimatedVisibility(visible = showTabs, enter = fadeIn(), exit = fadeOut()) {
                TabRow(selectedTabIndex = if (noteMode == NoteMode.VOICE) 0 else 1) {
                    Tab(
                        selected = noteMode == NoteMode.VOICE,
                        onClick = { viewModel.setNoteMode(NoteMode.VOICE) },
                        icon = { Icon(Icons.Default.Mic, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        text = { Text("Głosowa") },
                    )
                    Tab(
                        selected = noteMode == NoteMode.TEXT,
                        onClick = { viewModel.setNoteMode(NoteMode.TEXT) },
                        icon = { Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        text = { Text("Tekstowa") },
                    )
                }
            }

            // ── Content area ───────────────────────────────────────────────
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
                            onTextChange = viewModel::onNoteTextChange,
                            onSave = viewModel::saveNote,
                            onSkip = onNavigateBack,
                            onRecordAgain = viewModel::resetToVoice,
                        )
                    else ->
                        VoiceNoteContent(
                            recordingState = recordingState,
                            onMicClick = ::handleMicClick,
                            onStop = viewModel::stopRecording,
                            onSkip = onNavigateBack,
                        )
                }
            }
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

@Composable
private fun TextNoteContent(
    text: String,
    error: String?,
    isLoading: Boolean,
    isProcessingVoice: Boolean,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit,
    onSkip: () -> Unit,
    onRecordAgain: (() -> Unit)? = null,
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
                label = { Text("Notatka") },
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
                    Text("Zapisz notatkę", style = MaterialTheme.typography.labelLarge)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (onRecordAgain != null) {
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
                Spacer(modifier = Modifier.height(8.dp))
            }

            OutlinedButton(
                onClick = onSkip,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                enabled = !isLoading,
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Pomiń")
            }
        }
    }
}

@Composable
private fun VoiceNoteContent(
    recordingState: RecordingState,
    onMicClick: () -> Unit,
    onStop: () -> Unit,
    onSkip: () -> Unit,
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
            is RecordingState.Recording -> RecordingActivePanel(state = recordingState, onStop = onStop)
            else -> {}
        }

        Spacer(modifier = Modifier.weight(1f))

        if (recordingState is RecordingState.Idle) {
            OutlinedButton(
                onClick = onSkip,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Pomiń")
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
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
private fun RecordingActivePanel(state: RecordingState.Recording, onStop: () -> Unit) {
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
                text = "Nagrywanie  ${state.durationSeconds.toTimeString()}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Red600,
            )
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


private fun Int.toTimeString(): String {
    val m = this / 60
    val s = this % 60
    return "%d:%02d".format(m, s)
}
