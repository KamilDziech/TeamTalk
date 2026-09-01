package com.ekotak.teamtalk.presentation.crm

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddTask
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ekotak.teamtalk.domain.model.Client
import com.ekotak.teamtalk.domain.model.Deal
import com.ekotak.teamtalk.domain.model.DealStage
import com.ekotak.teamtalk.domain.model.PIPELINE_STAGES
import com.ekotak.teamtalk.domain.model.lostReasonsForStage
import com.ekotak.teamtalk.domain.model.nextStages
import com.ekotak.teamtalk.presentation.components.AppTopBar
import java.util.concurrent.TimeUnit

/**
 * Karta deala — mobilny odpowiednik `DealDrawer` z panelu: nagłówek z klientem
 * i etapem, poziomo przewijany pasek zakładek, treść wybranej zakładki i stopka
 * z akcją domykającą proces.
 *
 * Zakładki, których ekrany jeszcze nie powstały, są widoczne i klikalne —
 * pokazują wtedy, gdzie te dane znaleźć. Ukrycie ich sugerowałoby, że deal ich
 * nie ma, a to nieprawda: w panelu są.
 */
@Composable
fun DealDetailScreen(
    onNavigateBack: () -> Unit,
    onCreateTask: (phone: String, name: String?) -> Unit,
    onEdit: () -> Unit,
    onOpenArticle: (categoryId: String, pathLabel: String) -> Unit,
    viewModel: DealDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val candidates by viewModel.contactCandidates.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var stageToConfirm by remember { mutableStateOf<DealStage?>(null) }
    var showLostDialog by remember { mutableStateOf(false) }
    var showContactPicker by remember { mutableStateOf(false) }
    var confirmComplete by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    // Powrót z formularza pozostałych pól: karta musi pokazać zapisane wartości
    // i nowy wpis historii. Flaga przeżywa zdjęcie ekranu ze stosu, więc
    // odróżnia pierwsze wejście (dane ciągnie już `init` ViewModelu) od powrotu.
    var visitedBefore by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (visitedBefore) viewModel.load(silent = true)
        visitedBefore = true
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Deal",
                onNavigateBack = onNavigateBack,
                actions = {
                    // Formularz pozostałych pól (spotkania, audyt, OZC, faktura)
                    // do czasu, aż dostaną własne zakładki — inaczej te pola
                    // przestałyby być edytowalne z telefonu.
                    if (state.canManage && state.detail != null && !state.editing) {
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Default.Edit, contentDescription = "Pozostałe pola deala")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            val detail = state.detail
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                detail == null -> Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.error ?: "Nie udało się wczytać karty deala",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = { viewModel.load() }) { Text("Spróbuj ponownie") }
                    }
                }

                else -> Column(Modifier.fillMaxSize()) {
                    if (state.isSaving) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }

                    HeaderSection(
                        deal = detail.deal,
                        client = detail.client,
                        canManage = state.canManage,
                        enabled = !state.isSaving,
                        onPickStage = { stage ->
                            if (stage == DealStage.LOST) showLostDialog = true
                            else stageToConfirm = stage
                        },
                        onCall = viewModel::call,
                        onCreateTask = {
                            onCreateTask(
                                detail.client?.primaryPhone.orEmpty(),
                                detail.client?.displayName,
                            )
                        },
                        onSetContact = { showContactPicker = true },
                    )

                    DealTabRow(
                        selected = state.tab,
                        onSelect = viewModel::selectTab,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                    ) {
                        when (state.tab) {
                            DealTab.DANE -> DealDataTab(
                                state = state,
                                candidates = candidates,
                                viewModel = viewModel,
                            )
                            DealTab.LEAD -> DealLeadTab(
                                state = state,
                                onEdit = onEdit,
                                onOpenArticle = { categoryId ->
                                    // Ścieżka w katalogu jest już policzona na
                                    // karcie — ekran artykułu dostaje ją gotową
                                    // zamiast ciągnąć katalog drugi raz.
                                    onOpenArticle(
                                        categoryId,
                                        state.lead.selectedPaths
                                            .firstOrNull { it.categoryId == categoryId }
                                            ?.pathLabel
                                            ?: categoryId,
                                    )
                                },
                                viewModel = viewModel,
                            )
                            DealTab.HISTORIA -> DealHistoryTab(detail.activities, state.members)
                            DealTab.PODSUMOWANIE -> DealSummaryTab(detail, state.members)
                            else -> TabPlaceholder(state.tab)
                        }
                    }

                    DealFooter(
                        state = state,
                        onMarkLost = { showLostDialog = true },
                        onComplete = { confirmComplete = true },
                    )
                }
            }
        }
    }

    stageToConfirm?.let { stage ->
        val current = state.detail?.deal?.stage
        AlertDialog(
            onDismissRequest = { stageToConfirm = null },
            title = { Text("Zmiana etapu") },
            text = {
                Text(
                    if (current != null && isBackward(current, stage)) {
                        "Cofnąć deal na wcześniejszy etap „${stage.label}”?"
                    } else {
                        "Przenieść deal na etap „${stage.label}”?"
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    stageToConfirm = null
                    viewModel.changeStage(stage)
                }) { Text("Przenieś") }
            },
            dismissButton = {
                TextButton(onClick = { stageToConfirm = null }) { Text("Anuluj") }
            },
        )
    }

    if (showLostDialog) {
        LostReasonDialog(
            stage = state.detail?.deal?.stage ?: DealStage.LEAD,
            onDismiss = { showLostDialog = false },
            onConfirm = { category, detailText ->
                showLostDialog = false
                viewModel.changeStage(
                    stage = DealStage.LOST,
                    lostReasonCategory = category,
                    lostReason = detailText,
                )
            },
        )
    }

    if (confirmComplete) {
        AlertDialog(
            onDismissRequest = { confirmComplete = false },
            title = { Text("Zakończenie deala") },
            text = { Text("Oznaczyć deal jako zakończony? To domknięcie procesu po montażu.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmComplete = false
                    viewModel.markCompleted()
                }) { Text("Oznacz jako zakończony") }
            },
            dismissButton = {
                TextButton(onClick = { confirmComplete = false }) { Text("Anuluj") }
            },
        )
    }

    if (showContactPicker) {
        NextContactDialog(
            onDismiss = { showContactPicker = false },
            onPick = { days ->
                showContactPicker = false
                viewModel.setNextContact(
                    System.currentTimeMillis() + TimeUnit.DAYS.toMillis(days),
                )
            },
        )
    }
}

// ── Nagłówek ─────────────────────────────────────────────────────────────────

/**
 * Klient, etap i akcje robione jedną ręką. Etap jest przyciskiem otwierającym
 * listę wszystkich etapów lejka — dozwolone są aktywne, reszta wyszarzona.
 * Dzięki temu widać nie tylko dokąd można pójść, ale i gdzie deal stoi w całym
 * procesie; sam rząd przycisków „do przodu" tego nie pokazywał.
 */
@Composable
private fun HeaderSection(
    deal: Deal,
    client: Client?,
    canManage: Boolean,
    enabled: Boolean,
    onPickStage: (DealStage) -> Unit,
    onCall: (String) -> Unit,
    onCreateTask: () -> Unit,
    onSetContact: () -> Unit,
) {
    var stageMenuOpen by remember { mutableStateOf(false) }
    val phone = client?.primaryPhone

    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
        Text(
            text = client?.displayName?.takeIf { it.isNotBlank() }
                ?: deal.projectName?.takeIf { it.isNotBlank() }
                ?: "Klient bez kartoteki",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        listOfNotNull(client?.place, phone).joinToString(" · ").takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(stageColor(deal.stage).copy(alpha = 0.16f))
                        .clickable(enabled = canManage && enabled) { stageMenuOpen = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = deal.stage.label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = stageColor(deal.stage),
                    )
                    if (canManage) {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Zmień etap",
                            tint = stageColor(deal.stage),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                StageMenu(
                    expanded = stageMenuOpen,
                    current = deal.stage,
                    onDismiss = { stageMenuOpen = false },
                    onPick = {
                        stageMenuOpen = false
                        onPickStage(it)
                    },
                )
            }

            stageAgeLabel(deal.stageEnteredAt)?.let {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "w etapie: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { phone?.let(onCall) },
                enabled = !phone.isNullOrBlank(),
                modifier = Modifier.weight(1f),
            ) {
                Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Zadzwoń")
            }
            OutlinedButton(onClick = onCreateTask, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.AddTask, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Zadanie")
            }
        }

        if (canManage) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatDate(deal.nextContactAt)
                        ?.let { "Następny kontakt: $it" }
                        ?: "Brak terminu kontaktu",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isOverdue(deal.nextContactAt)) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onSetContact, enabled = enabled) { Text("Ustaw termin") }
            }
        }
    }
}

/**
 * Lista etapów lejka z zaznaczonym bieżącym. Niedozwolone przejścia zostają
 * widoczne, ale nieaktywne — autorytatywną bramką i tak jest API, a wyszarzenie
 * tłumaczy, czemu nie da się przeskoczyć etapu.
 */
@Composable
private fun StageMenu(
    expanded: Boolean,
    current: DealStage,
    onDismiss: () -> Unit,
    onPick: (DealStage) -> Unit,
) {
    val allowed = nextStages(current)
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        (PIPELINE_STAGES + DealStage.LOST + DealStage.ZAKONCZONY).distinct().forEach { stage ->
            val isCurrent = stage == current
            DropdownMenuItem(
                enabled = isCurrent || stage in allowed,
                onClick = { if (!isCurrent) onPick(stage) },
                text = {
                    Text(
                        text = stage.label,
                        fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                    )
                },
                trailingIcon = if (isCurrent) {
                    { Text("✓", color = MaterialTheme.colorScheme.primary) }
                } else {
                    null
                },
            )
        }
    }
}

/**
 * Czy przejście cofa deal w lejku. Służy wyłącznie do sformułowania pytania —
 * cofnięcie jest zwykłą, dozwoloną korektą, ale warto o nie zapytać inaczej niż
 * o ruch do przodu.
 */
private fun isBackward(from: DealStage, to: DealStage): Boolean {
    val fromIndex = PIPELINE_STAGES.indexOf(from)
    val toIndex = PIPELINE_STAGES.indexOf(to)
    return fromIndex >= 0 && toIndex >= 0 && toIndex < fromIndex
}

// ── Stopka ───────────────────────────────────────────────────────────────────

/** Akcja domykająca proces — czerwona utrata albo zielone zakończenie. */
@Composable
private fun DealFooter(
    state: DealDetailViewModel.UiState,
    onMarkLost: () -> Unit,
    onComplete: () -> Unit,
) {
    if (!state.canComplete && !state.canMarkLost) return

    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Box(Modifier.fillMaxWidth().padding(12.dp)) {
        when {
            state.canComplete -> Button(
                onClick = onComplete,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Oznacz jako zakończony") }

            else -> OutlinedButton(
                onClick = onMarkLost,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) { Text("Oznacz jako stracony") }
        }
    }
}

// ── Okna dialogowe ───────────────────────────────────────────────────────────

/**
 * Powód utraty: kategoria (wymagana przez API) plus opcjonalny opis słowny.
 * Zestaw kategorii zależy od etapu — odrzucenie surowego leada ma inne powody
 * niż przegrana oferta.
 */
@Composable
private fun LostReasonDialog(
    stage: DealStage,
    onDismiss: () -> Unit,
    onConfirm: (category: String, detail: String?) -> Unit,
) {
    val reasons = lostReasonsForStage(stage)
    var selected by remember { mutableStateOf(reasons.first().first) }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Powód utraty") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                reasons.forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = value }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = selected == value, onClick = { selected = value })
                        Spacer(Modifier.width(6.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Szczegóły (opcjonalnie)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected, note.trim().ifBlank { null }) }) {
                Text("Oznacz jako stracony")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

/** Skróty „oddzwonię za…" — najczęstsze odstępy z pracy w terenie. */
@Composable
private fun NextContactDialog(onDismiss: () -> Unit, onPick: (Long) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Następny kontakt") },
        text = {
            Column {
                listOf(1L to "jutro", 3L to "za 3 dni", 7L to "za tydzień", 14L to "za 2 tygodnie")
                    .forEach { (days, label) ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(days) }
                                .padding(vertical = 10.dp),
                        )
                    }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}
