package com.ekotak.teamtalk.presentation.assistant

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.ekotak.teamtalk.domain.model.AssistantAction
import com.ekotak.teamtalk.domain.model.AssistantMessage
import com.ekotak.teamtalk.presentation.components.AppTopBar

/**
 * Asystent firmowy — kafelek „Asystent" pulpitu.
 *
 * Odpowiedzi liczy serwer: dokłada Kontekst organizacji i sam sięga do CRM
 * (kartoteka, karty deali) w granicach widoczności zalogowanego. Akcje zapisu
 * asystent tylko PROPONUJE — wykonują się po kliknięciu „Zatwierdź", jednym
 * żądaniem, którego uprawnienia API sprawdza po raz drugi.
 *
 * Świadomie bez historii: wątek żyje do wyjścia z ekranu (ustalenie z
 * zamawiającym). Bez sieci ekran mówi to wprost — nie ma kolejki, bo odpowiedź
 * i tak powstaje po stronie serwera.
 */
private val EXAMPLES = listOf(
    "Co ustaliliśmy z klientem Nowak?",
    "Pokaż moje deale w etapie Oferta",
    "Jakie mamy zasady rabatów?",
    "Utwórz zadanie: zadzwonić do klienta jutro o 10:00",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(
    onNavigateBack: () -> Unit,
    onOpenLeadWizard: () -> Unit = {},
    onOpenClient: (String) -> Unit = {},
    viewModel: AssistantViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val cardPickers = rememberCardPhotoPickers(onPhoto = viewModel::onCardPhoto)
    var cardMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.navigation.collect { nav ->
            when (nav) {
                AssistantViewModel.Navigation.OpenLeadWizard -> onOpenLeadWizard()
                is AssistantViewModel.Navigation.OpenClient -> onOpenClient(nav.clientId)
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) viewModel.toggleMic() }

    fun handleMicClick() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) viewModel.toggleMic() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    // Nowa wiadomość (albo „…” w trakcie liczenia) zawsze na widoku.
    LaunchedEffect(state.log.size, state.pending, state.scanning) {
        val last = state.log.size
        if (last > 0) listState.animateScrollToItem(last)
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Asystent",
                onNavigateBack = onNavigateBack,
                actions = {
                    IconButton(onClick = viewModel::toggleSpeakReplies) {
                        Icon(
                            imageVector = if (state.speakReplies) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                            contentDescription = if (state.speakReplies) {
                                "Czytanie odpowiedzi: włączone"
                            } else {
                                "Czytanie odpowiedzi: wyłączone"
                            },
                            tint = if (state.speakReplies) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (state.log.isEmpty()) {
                    item(key = "intro") {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Cześć! Jestem asystentem ekotak.app.",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = "Odpowiadam na podstawie wiedzy firmy i danych z systemu — " +
                                    "kartoteki klientów i kart deali. Mogę też zaproponować " +
                                    "zadanie, wydarzenie, notatkę, zlecenie serwisowe albo wniosek urlopowy. " +
                                    "Wizytówkę (zdjęcie albo kod QR) dodasz przyciskiem aparatu.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            SuggestionChip(
                                onClick = { cardPickers.takePhoto() },
                                label = { Text("📇 Zeskanuj wizytówkę") },
                            )
                            EXAMPLES.forEach { example ->
                                SuggestionChip(
                                    onClick = { if (!state.pending) viewModel.ask(example) },
                                    label = { Text(example) },
                                )
                            }
                        }
                    }
                }

                itemsIndexed(state.log) { index, entry ->
                    val scan = entry.scan
                    if (scan != null) {
                        CardScanCard(
                            scan = scan,
                            onKind = { viewModel.chooseKind(index, it) },
                            onLead = { viewModel.chooseLead(index, it) },
                            onRole = { viewModel.chooseRole(index, it) },
                            onOtherRole = { viewModel.chooseOtherRole(index) },
                            onOtherRoleText = { viewModel.onOtherRoleText(index, it) },
                            onToggleEdit = { viewModel.toggleCardEdit(index) },
                            onField = { field, value -> viewModel.onCardField(index, field, value) },
                            onSave = { viewModel.saveScan(index) },
                            onOpenLead = { viewModel.openLead(index) },
                            onOpenClient = { viewModel.openClient(index) },
                        )
                    } else {
                        MessageBubble(
                            message = entry.message,
                            actions = entry.actions,
                            onRunAction = { actionIndex -> viewModel.runAction(index, actionIndex) },
                        )
                    }
                }

                if (state.scanning) {
                    item(key = "scanning") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text(
                                text = "Czytam wizytówkę…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                if (state.pending) {
                    item(key = "pending") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text(
                                text = "Sprawdzam…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                state.error?.let { message ->
                    item(key = "error") {
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }

                if (state.error == null) {
                    state.notice?.let { message ->
                        item(key = "notice") {
                            Text(
                                text = message,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = state.input,
                    onValueChange = viewModel::onInputChange,
                    modifier = Modifier.weight(1f),
                    placeholder = {
                        Text(if (state.listening) "Słucham…" else "Zapytaj o cokolwiek…")
                    },
                    enabled = !state.pending,
                )
                Box {
                    IconButton(
                        onClick = { cardMenu = true },
                        enabled = !state.scanning,
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = "Zeskanuj wizytówkę")
                    }
                    DropdownMenu(expanded = cardMenu, onDismissRequest = { cardMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Zrób zdjęcie wizytówki") },
                            leadingIcon = { Icon(Icons.Default.PhotoCamera, contentDescription = null) },
                            onClick = {
                                cardMenu = false
                                cardPickers.takePhoto()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Wybierz z galerii") },
                            leadingIcon = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                            onClick = {
                                cardMenu = false
                                cardPickers.pickFromGallery()
                            },
                        )
                    }
                }
                IconButton(
                    onClick = { handleMicClick() },
                    enabled = !state.pending && state.micAvailable,
                ) {
                    Icon(
                        imageVector = if (state.micAvailable) Icons.Default.Mic else Icons.Default.MicOff,
                        contentDescription = if (state.listening) "Zakończ dyktowanie" else "Dyktuj pytanie",
                        tint = if (state.listening) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                IconButton(
                    onClick = { viewModel.ask(state.input) },
                    enabled = !state.pending && state.input.isNotBlank(),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Wyślij")
                }
            }
        }
    }
}

/** Dymek rozmowy + ewentualne propozycje akcji pod odpowiedzią asystenta. */
@Composable
private fun MessageBubble(
    message: AssistantMessage,
    actions: List<AssistantAction>,
    onRunAction: (Int) -> Unit,
) {
    val isUser = message.role == AssistantMessage.ROLE_USER
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (isUser) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ) {
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        actions.forEachIndexed { index, action ->
            ActionCard(action = action, onRun = { onRunAction(index) })
        }
    }
}

/**
 * Propozycja akcji: opis + jeden przycisk. Dopóki człowiek nie kliknie, na
 * serwerze nic się nie dzieje — po wykonaniu przycisk gaśnie i zostaje
 * podsumowanie z serwera (albo powód odmowy, np. brak uprawnienia).
 */
@Composable
private fun ActionCard(action: AssistantAction, onRun: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = action.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            when (action.status) {
                AssistantAction.Status.DONE -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = action.result ?: "Wykonano.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                AssistantAction.Status.ERROR -> Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = action.result ?: "Nie udało się wykonać akcji.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(onClick = onRun) { Text("Spróbuj ponownie") }
                }

                AssistantAction.Status.RUNNING -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Text(
                        text = "Wykonuję…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                AssistantAction.Status.IDLE -> Button(onClick = onRun) { Text("Zatwierdź") }
            }
        }
    }
}
