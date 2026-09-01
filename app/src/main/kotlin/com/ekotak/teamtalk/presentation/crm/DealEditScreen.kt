package com.ekotak.teamtalk.presentation.crm

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ekotak.teamtalk.domain.model.AuditAddressKind
import com.ekotak.teamtalk.domain.model.DealBuildingKind
import com.ekotak.teamtalk.domain.model.DealBuyerPersona
import com.ekotak.teamtalk.domain.model.DealDifficulty
import com.ekotak.teamtalk.domain.model.DealSegment
import com.ekotak.teamtalk.domain.model.MeetingKind
import com.ekotak.teamtalk.presentation.components.AppTopBar

/**
 * Edycja karty deala — pełen zakres pól przyjmowanych przez `PATCH /api/deals/:id`.
 * Poza zasięgiem zostaje `buildingPhoto`: zdjęcie budynku ustawia osobny endpoint
 * (`POST /:id/building-photo`) albo referencja `doc:<id>` z audytu, więc ręcznie
 * wpisany URL tylko rozjeżdżałby to z panelem.
 *
 * Zapis idzie jednym żądaniem i tylko z polami, które faktycznie się zmieniły.
 */
@Composable
fun DealEditScreen(
    onNavigateBack: () -> Unit,
    viewModel: DealEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmDiscard by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved) {
        if (state.saved) onNavigateBack()
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    // Wyjście z niezapisanymi zmianami wymaga potwierdzenia — formularz jest
    // długi i przypadkowy gest „wstecz" kosztowałby całą pracę.
    fun attemptBack() {
        if (state.isDirty) confirmDiscard = true else onNavigateBack()
    }

    BackHandler(enabled = state.isDirty) { confirmDiscard = true }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Edycja deala",
                onNavigateBack = { attemptBack() },
                actions = {
                    TextButton(
                        onClick = viewModel::save,
                        enabled = state.isDirty && !state.isSaving,
                    ) { Text("Zapisz") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }

                state.original == null -> Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.error ?: "Nie udało się wczytać karty deala",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = { viewModel.load() }) { Text("Spróbuj ponownie") }
                    }
                }

                else -> Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.isSaving) LinearProgressIndicator(Modifier.fillMaxWidth())

                    state.clientName?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }

                    BasicsCard(state, viewModel)
                    ContactCard(state, viewModel)
                    BuildingCard(state, viewModel)
                    OzcCard(state, viewModel)
                    MeetingCard(state, viewModel)
                    AuditCard(state, viewModel)
                    OwnersCard(state, viewModel)
                    BillingCard(state, viewModel)
                    IntegrationsCard(state, viewModel)

                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    if (confirmDiscard) {
        AlertDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text("Niezapisane zmiany") },
            text = { Text("Opuścić edycję bez zapisywania?") },
            confirmButton = {
                TextButton(onClick = { confirmDiscard = false; onNavigateBack() }) {
                    Text("Odrzuć zmiany")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) { Text("Wróć do edycji") }
            },
        )
    }
}

// ── Sekcje formularza ────────────────────────────────────────────────────────

@Composable
private fun BasicsCard(state: DealEditViewModel.UiState, vm: DealEditViewModel) {
    val draft = state.draft
    FormCard("Dane podstawowe") {
        FormTextField("Źródło", draft.source) { v -> vm.edit { it.copy(source = v) } }
        FormTextField("Nazwa projektu", draft.projectName) { v ->
            vm.edit { it.copy(projectName = v) }
        }
        FormTextField(
            label = "Opis",
            value = draft.description,
            onValueChange = { v -> vm.edit { it.copy(description = v) } },
            singleLine = false,
            minLines = 3,
        )
        FormTextField("Kod rabatowy", draft.discountCode) { v ->
            vm.edit { it.copy(discountCode = v) }
        }
        FormChoiceRow(
            label = "Segment",
            options = DealSegment.entries.toList(),
            selected = draft.segment,
            optionLabel = { it.label },
            onSelect = { v -> v?.let { s -> vm.edit { it.copy(segment = s) } } },
        )
        FormChoiceRow(
            label = "Rodzaj budynku",
            options = DealBuildingKind.entries.toList(),
            selected = draft.buildingKind,
            optionLabel = { it.label },
            onSelect = { v -> v?.let { k -> vm.edit { it.copy(buildingKind = k) } } },
        )
        FormChoiceRow(
            label = "Trudność",
            options = DealDifficulty.entries.toList(),
            selected = draft.difficulty,
            optionLabel = { it.label },
            onSelect = { v -> vm.edit { it.copy(difficulty = v) } },
            nullLabel = "brak",
        )
        FormChoiceRow(
            label = "Buyer persona",
            options = DealBuyerPersona.entries.toList(),
            selected = draft.buyerPersona,
            optionLabel = { it.label },
            onSelect = { v -> vm.edit { it.copy(buyerPersona = v) } },
            nullLabel = "brak",
        )
        FormSwitch("Zgoda RODO", draft.rodoConsent) { v ->
            vm.edit { it.copy(rodoConsent = v) }
        }
        FormSwitch("Wyjątek: osoba starsza", draft.elderlyContactException) { v ->
            vm.edit { it.copy(elderlyContactException = v) }
        }
    }
}

@Composable
private fun ContactCard(state: DealEditViewModel.UiState, vm: DealEditViewModel) {
    FormCard("Następny kontakt") {
        FormDateTimeField("Termin kontaktu", state.draft.nextContactAt) { v ->
            vm.edit { it.copy(nextContactAt = v) }
        }
    }
}

@Composable
private fun BuildingCard(state: DealEditViewModel.UiState, vm: DealEditViewModel) {
    val draft = state.draft
    FormCard("Dane budynku") {
        FormNumberField("Liczba osób", state.numbers.people, vm::onPeopleChange)
        FormNumberField("Powierzchnia ogrzewana [m²]", state.numbers.areaM2, vm::onAreaChange)
        FormNumberField("Liczba kondygnacji", state.numbers.floors, vm::onFloorsChange)
        FormTextField("Rodzaj budynku", draft.shape) { v -> vm.edit { it.copy(shape = v) } }
        FormTextField("Konstrukcja", draft.construction) { v ->
            vm.edit { it.copy(construction = v) }
        }
        FormTextField("Etap budowy", draft.buildingStage) { v ->
            vm.edit { it.copy(buildingStage = v) }
        }
        FormTextField("Okna / termin montażu", draft.windows) { v ->
            vm.edit { it.copy(windows = v) }
        }
        FormSwitch("Ogrzewana piwnica", draft.heatedBasement == true) { v ->
            vm.edit { it.copy(heatedBasement = v) }
        }
        FormSwitch("Ogrzewany garaż", draft.heatedGarage == true) { v ->
            vm.edit { it.copy(heatedGarage = v) }
        }
    }
}

@Composable
private fun OzcCard(state: DealEditViewModel.UiState, vm: DealEditViewModel) {
    FormCard("OZC — zapotrzebowanie na ciepło") {
        FormNumberField(
            label = "Moc budynku [kW]",
            text = state.numbers.ozcBuildingKw,
            onTextChange = vm::onOzcBuildingKwChange,
            decimal = true,
        )
        FormNumberField(
            label = "Moc CWU [kW]",
            text = state.numbers.ozcDhwKw,
            onTextChange = vm::onOzcDhwKwChange,
            decimal = true,
        )
        FormTextField(
            label = "Link do obliczenia (cieplo.app)",
            value = state.draft.ozcSourceUrl,
            onValueChange = { v -> vm.edit { it.copy(ozcSourceUrl = v) } },
            keyboardType = KeyboardType.Uri,
        )
        // Potwierdzenie dotyczy wyniku spoza zakresu 40–50 W/m²; moment i autora
        // stempluje API z zalogowanego użytkownika.
        FormSwitch("Wynik potwierdzony przez audytora", state.draft.ozcConfirmed) { v ->
            vm.edit { it.copy(ozcConfirmed = v) }
        }
    }
}

@Composable
private fun MeetingCard(state: DealEditViewModel.UiState, vm: DealEditViewModel) {
    val draft = state.draft
    FormCard("Spotkanie wstępne / wizja") {
        FormChoiceRow(
            label = "Miejsce",
            options = MeetingKind.entries.toList(),
            selected = draft.meetingKind,
            optionLabel = { it.label },
            onSelect = { v -> vm.edit { it.copy(meetingKind = v) } },
            nullLabel = "brak",
        )
        FormDateTimeField("Termin spotkania", draft.meetingAt) { v ->
            vm.edit { it.copy(meetingAt = v) }
        }
        FormNumberField(
            label = "Czas trwania [min]",
            text = state.numbers.meetingDurationMin,
            onTextChange = vm::onMeetingDurationChange,
        )
        FormTextField(
            label = "Link do spotkania online",
            value = draft.meetingUrl,
            onValueChange = { v -> vm.edit { it.copy(meetingUrl = v) } },
            keyboardType = KeyboardType.Uri,
        )
        if (state.members.isNotEmpty()) {
            FormMemberPicker(
                label = "Osoba wykonująca wizję",
                members = state.members,
                selectedId = draft.meetingOwnerId,
                onSelect = { v -> vm.edit { it.copy(meetingOwnerId = v) } },
            )
        }
    }
}

@Composable
private fun AuditCard(state: DealEditViewModel.UiState, vm: DealEditViewModel) {
    val draft = state.draft
    FormCard("Audyt") {
        FormChoiceRow(
            label = "Miejsce",
            options = AuditAddressKind.entries.toList(),
            selected = draft.auditAddressKind,
            optionLabel = { it.label },
            onSelect = { v -> vm.edit { it.copy(auditAddressKind = v) } },
            nullLabel = "brak",
        )
        FormTextField("Adres / doprecyzowanie", draft.auditAddress) { v ->
            vm.edit { it.copy(auditAddress = v) }
        }
        FormDateTimeField("Termin audytu", draft.auditMeetingAt) { v ->
            vm.edit { it.copy(auditMeetingAt = v) }
        }
        if (state.members.isNotEmpty()) {
            FormMemberPicker(
                label = "Opiekun audytu",
                members = state.members,
                selectedId = draft.auditOwnerId,
                onSelect = { v -> vm.edit { it.copy(auditOwnerId = v) } },
            )
        }
    }
}

@Composable
private fun OwnersCard(state: DealEditViewModel.UiState, vm: DealEditViewModel) {
    // Bez listy zespołu (brak `tasks.view`) nie da się sensownie wybrać osoby —
    // pokazanie pustego selektora tylko myliłoby.
    if (state.members.isEmpty()) return
    FormCard("Opiekunowie") {
        FormMemberPicker(
            label = "Opiekun deala",
            members = state.members,
            selectedId = state.draft.ownerId.ifBlank { null },
            onSelect = { v -> vm.edit { it.copy(ownerId = v ?: it.ownerId) } },
            allowEmpty = false,
        )
        FormMemberPicker(
            label = "Opiekun etapu",
            members = state.members,
            selectedId = state.draft.stageOwnerId,
            onSelect = { v -> vm.edit { it.copy(stageOwnerId = v) } },
        )
    }
}

@Composable
private fun BillingCard(state: DealEditViewModel.UiState, vm: DealEditViewModel) {
    val draft = state.draft
    FormCard("Dane do faktury") {
        FormSwitch("Adres jak instalacji", draft.billingSameAsInstall) { v ->
            vm.edit { it.copy(billingSameAsInstall = v) }
        }
        // Osobne dane mają sens tylko wtedy, gdy faktura NIE idzie na adres
        // instalacji — inaczej API i tak bierze adres klienta.
        if (!draft.billingSameAsInstall) {
            FormTextField("Odbiorca", draft.billingName) { v ->
                vm.edit { it.copy(billingName = v) }
            }
            FormTextField("Firma", draft.billingCompany) { v ->
                vm.edit { it.copy(billingCompany = v) }
            }
            FormTextField(
                label = "NIP",
                value = draft.billingNip,
                onValueChange = { v -> vm.edit { it.copy(billingNip = v) } },
                keyboardType = KeyboardType.Number,
            )
            FormTextField("Adres do faktury", draft.billingAddress) { v ->
                vm.edit { it.copy(billingAddress = v) }
            }
        }
    }
}

@Composable
private fun IntegrationsCard(state: DealEditViewModel.UiState, vm: DealEditViewModel) {
    FormCard("Integracje") {
        FormTextField("Folder Drive", state.draft.driveFolder) { v ->
            vm.edit { it.copy(driveFolder = v) }
        }
    }
}
