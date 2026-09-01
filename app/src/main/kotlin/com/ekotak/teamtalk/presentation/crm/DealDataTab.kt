package com.ekotak.teamtalk.presentation.crm

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ekotak.teamtalk.domain.model.AssistantMessage
import com.ekotak.teamtalk.domain.model.Client
import com.ekotak.teamtalk.domain.model.ClientTravel
import com.ekotak.teamtalk.domain.model.Deal
import com.ekotak.teamtalk.domain.model.DealBuildingKind
import com.ekotak.teamtalk.domain.model.DealBuyerPersona
import com.ekotak.teamtalk.domain.model.DealDifficulty
import com.ekotak.teamtalk.domain.model.DealSegment
import com.ekotak.teamtalk.domain.model.DealStage
import com.ekotak.teamtalk.domain.model.TravelLeg
import kotlin.math.roundToInt

/**
 * Zakładka „Dane" — odpowiednik pierwszej karty `DealDrawer`. Jedyne miejsce,
 * gdzie telefon edytuje jednocześnie deala i kartotekę klienta: z punktu
 * widzenia handlowca to jedna karta, więc jeden przycisk „Zapisz" obsługuje oba
 * rekordy (rozdzielenie na dwa `PATCH` robi ViewModel).
 */
@Composable
fun DealDataTab(
    state: DealDetailViewModel.UiState,
    candidates: List<Client>,
    viewModel: DealDetailViewModel,
) {
    val detail = state.detail ?: return
    val deal = detail.deal
    val client = detail.client
    var pickingContact by remember { mutableStateOf(false) }

    ContactsCard(
        state = state,
        client = client,
        companions = detail.companions,
        onAddClick = { pickingContact = true },
        viewModel = viewModel,
    )
    SectionGap()

    AddressCard(
        state = state,
        client = client,
        travel = client?.travel,
        onOpenMap = viewModel::openMap,
        viewModel = viewModel,
    )
    SectionGap()

    OwnersCard(state = state, deal = deal, viewModel = viewModel)
    SectionGap()

    BuildingCard(state = state, deal = deal, viewModel = viewModel)
    SectionGap()

    DealFieldsCard(state = state, deal = deal, viewModel = viewModel)
    SectionGap()

    if (deal.stage == DealStage.LOST) {
        SectionCard {
            SectionTitle("Powód utraty")
            SectionGap()
            InfoRow("Kategoria", deal.lostReasonCategory)
            InfoRow("Opis", deal.lostReason)
        }
        SectionGap()
    }

    if (state.canManage) {
        EditActions(state = state, viewModel = viewModel)
        SectionGap()
    }

    AssistantCard(assistant = state.assistant, onAsk = viewModel::askAssistant)

    if (pickingContact) {
        ContactPickerDialog(
            candidates = candidates,
            // Kontakt już przypięty (główny albo towarzyszący) nie ma po co
            // pojawiać się na liście — dopięcie go po raz drugi API odrzuci.
            excludedIds = buildSet {
                client?.id?.let(::add)
                detail.companions.forEach { add(it.id) }
            },
            onQueryChange = viewModel::onContactQueryChange,
            onDismiss = { pickingContact = false },
            onPick = {
                pickingContact = false
                viewModel.addCompanion(it.id)
            },
        )
    }
}

// ── Kontakty ─────────────────────────────────────────────────────────────────

/**
 * Główny kontakt plus towarzyszące. W trybie edycji pola głównego są
 * formularzem kartoteki, a każdy kontakt towarzyszący dostaje krzyżyk
 * (odpięcie) i akcję „ustaw głównym" — komplet z paska `ContactsStrip` panelu.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContactsCard(
    state: DealDetailViewModel.UiState,
    client: Client?,
    companions: List<Client>,
    onAddClick: () -> Unit,
    viewModel: DealDetailViewModel,
) {
    SectionCard {
        SectionTitle("Kontakty")
        SectionGap()

        val draft = state.clientDraft
        if (state.editing && draft != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FormTextField("Imię", draft.firstName, modifier = Modifier.weight(1f)) { v ->
                    viewModel.editClient { it.copy(firstName = v.orEmpty()) }
                }
                FormTextField("Nazwisko", draft.lastName, modifier = Modifier.weight(1f)) { v ->
                    viewModel.editClient { it.copy(lastName = v.orEmpty()) }
                }
            }
            Spacer(Modifier.height(8.dp))
            FormTextField("E-mail", draft.email) { v -> viewModel.editClient { it.copy(email = v) } }
            Spacer(Modifier.height(8.dp))
            FormTextField("Telefon", draft.phone) { v -> viewModel.editClient { it.copy(phone = v) } }
            Spacer(Modifier.height(8.dp))
            // Wyjątek stoi przy e-mailu, bo to tutaj decyduje się, czy adres
            // w ogóle będzie wpisany — a od niego zależy przejście do kwalifikacji.
            FormSwitch("Osoba starsza — kwalifikacja bez e-maila", state.dealDraft.elderlyContactException) { v ->
                viewModel.editDeal { it.copy(elderlyContactException = v) }
            }
        } else {
            InfoRow("Główny", client?.displayName)
            InfoRow("E-mail", client?.email)
            InfoRow("Telefon", client?.primaryPhone)
            if (state.detail?.deal?.elderlyContactException == true) {
                InfoRow("Wyjątek", "osoba starsza — bez e-maila")
            }
        }

        if (companions.isNotEmpty() || state.canManage) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Kontakty towarzyszące",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                companions.forEach { companion ->
                    InputChip(
                        selected = false,
                        enabled = !state.isSaving,
                        // Dotknięcie etykiety promuje kontakt na główny; krzyżyk
                        // go odpina. Dwie różne operacje na jednym chipie, bo
                        // osobny przycisk na każdy kontakt zjadłby całą szerokość.
                        onClick = {
                            if (state.canManage) viewModel.setPrimaryContact(companion.id)
                        },
                        label = { Text(companion.displayName) },
                        trailingIcon = if (state.canManage) {
                            {
                                IconButton(
                                    onClick = { viewModel.removeCompanion(companion.id) },
                                    enabled = !state.isSaving,
                                    modifier = Modifier.size(20.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Odepnij ${companion.displayName}",
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                        } else {
                            null
                        },
                    )
                }
                if (state.canManage) {
                    AssistChip(
                        onClick = onAddClick,
                        enabled = !state.isSaving,
                        label = { Text("+ dodaj") },
                    )
                }
            }
            if (companions.isNotEmpty() && state.canManage) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "Dotknij nazwiska, aby ustawić kontakt jako główny.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Wyszukiwarka kartoteki — kontakt towarzyszący to zawsze istniejący rekord. */
@Composable
private fun ContactPickerDialog(
    candidates: List<Client>,
    excludedIds: Set<String>,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onPick: (Client) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val visible = candidates.filter { it.id !in excludedIds }.take(MAX_CONTACT_RESULTS)

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Dodaj kontakt") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        onQueryChange(it)
                    },
                    label = { Text("Szukaj w kartotece") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                if (visible.isEmpty()) {
                    Text(
                        text = if (query.isBlank()) {
                            "Wpisz nazwisko lub telefon."
                        } else {
                            "Brak pasujących wpisów w kartotece."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                visible.forEach { candidate ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(candidate) }
                            .padding(vertical = 8.dp),
                    ) {
                        Text(candidate.displayName, style = MaterialTheme.typography.bodyMedium)
                        val subtitle = listOfNotNull(candidate.primaryPhone, candidate.place)
                            .joinToString(" · ")
                        if (subtitle.isNotBlank()) {
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Zamknij") }
        },
    )
}

// ── Adres i dojazd ───────────────────────────────────────────────────────────

@Composable
private fun AddressCard(
    state: DealDetailViewModel.UiState,
    client: Client?,
    travel: ClientTravel?,
    onOpenMap: () -> Unit,
    viewModel: DealDetailViewModel,
) {
    SectionCard {
        SectionTitle("Adres instalacji")
        SectionGap()

        val draft = state.clientDraft
        if (state.editing && draft != null) {
            FormTextField(
                label = "Adres",
                value = draft.address,
                onValueChange = { v -> viewModel.editClient { it.copy(address = v) } },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Zmiana adresu uruchamia ponowną walidację i przeliczenie dojazdu.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            InfoRow("Adres", client?.address)
            if (client?.hasGeo == true) {
                InfoRow("Lokalizacja", "potwierdzona ✓")
            } else if (!client?.address.isNullOrBlank()) {
                InfoRow("Lokalizacja", "brak współrzędnych")
            }
            InfoRow("Dojazd Kobiernice", travel?.kobiernice.describe())
            InfoRow("Dojazd Gliwice", travel?.gliwice.describe())

            if (client?.hasGeo == true) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onOpenMap) { Text("Pokaż na mapie") }
            }
        }

        // Adres do faktury pokazujemy tylko wtedy, gdy różni się od instalacji —
        // w komplecie zajmuje pół ekranu, a w większości deali jest ten sam.
        val deal = state.detail?.deal
        if (deal != null && !deal.billingSameAsInstall) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Dane do faktury",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            InfoRow("Odbiorca", deal.billingName)
            InfoRow("Firma", deal.billingCompany)
            InfoRow("NIP", deal.billingNip)
            InfoRow("Adres", deal.billingAddress)
        }
    }
}

/** „18,4 km · 24 min"; `null` gdy trasy nie udało się wyznaczyć. */
private fun TravelLeg?.describe(): String? {
    val leg = this ?: return null
    val km = (leg.km * 10).roundToInt() / 10.0
    return "$km km · ${leg.min.roundToInt()} min"
}

// ── Opiekunowie ──────────────────────────────────────────────────────────────

@Composable
private fun OwnersCard(
    state: DealDetailViewModel.UiState,
    deal: Deal,
    viewModel: DealDetailViewModel,
) {
    SectionCard {
        SectionTitle("Opiekunowie")
        SectionGap()

        if (state.editing && state.members.isNotEmpty()) {
            FormMemberPicker(
                label = "Opiekun deala",
                members = state.members,
                selectedId = state.dealDraft.ownerId.ifBlank { null },
                onSelect = { id -> viewModel.editDeal { it.copy(ownerId = id.orEmpty()) } },
                allowEmpty = false,
            )
            Spacer(Modifier.height(8.dp))
            FormMemberPicker(
                label = "Opiekun etapu",
                members = state.members,
                selectedId = state.dealDraft.stageOwnerId,
                onSelect = { id -> viewModel.editDeal { it.copy(stageOwnerId = id) } },
            )
        } else {
            val byId = state.members.associateBy { it.id }
            InfoRow("Opiekun", byId[deal.ownerId]?.displayName ?: deal.ownerId.takeIf { it.isNotBlank() })
            InfoRow("Opiekun etapu", deal.stageOwnerId?.let { byId[it]?.displayName ?: it })
        }
    }
}

// ── Dane budynku ─────────────────────────────────────────────────────────────

@Composable
private fun BuildingCard(
    state: DealDetailViewModel.UiState,
    deal: Deal,
    viewModel: DealDetailViewModel,
) {
    SectionCard {
        SectionTitle("Dane budynku")
        SectionGap()

        if (state.editing) {
            val draft = state.dealDraft
            FormTextField("Nazwa projektu", draft.projectName) { v ->
                viewModel.editDeal { it.copy(projectName = v) }
            }
            Spacer(Modifier.height(8.dp))
            FormChoiceRow(
                label = "Rodzaj budynku",
                options = DealBuildingKind.entries.toList(),
                selected = draft.buildingKind,
                optionLabel = { it.label },
                onSelect = { v -> v?.let { k -> viewModel.editDeal { it.copy(buildingKind = k) } } },
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FormNumberField(
                    label = "Osoby",
                    text = state.numbers.people,
                    onTextChange = viewModel::onPeopleChange,
                    modifier = Modifier.weight(1f),
                )
                FormNumberField(
                    label = "Pow. [m²]",
                    text = state.numbers.areaM2,
                    onTextChange = viewModel::onAreaChange,
                    modifier = Modifier.weight(1f),
                )
                FormNumberField(
                    label = "Kondygn.",
                    text = state.numbers.floors,
                    onTextChange = viewModel::onFloorsChange,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            FormTextField("Kształt / rodzaj", draft.shape) { v ->
                viewModel.editDeal { it.copy(shape = v) }
            }
            Spacer(Modifier.height(8.dp))
            FormTextField("Konstrukcja", draft.construction) { v ->
                viewModel.editDeal { it.copy(construction = v) }
            }
            Spacer(Modifier.height(8.dp))
            FormTextField("Etap budowy", draft.buildingStage) { v ->
                viewModel.editDeal { it.copy(buildingStage = v) }
            }
            Spacer(Modifier.height(8.dp))
            FormTextField("Okna / termin montażu", draft.windows) { v ->
                viewModel.editDeal { it.copy(windows = v) }
            }
            Spacer(Modifier.height(4.dp))
            FormSwitch("Ogrzewana piwnica", draft.heatedBasement == true) { v ->
                viewModel.editDeal { it.copy(heatedBasement = v) }
            }
            FormSwitch("Ogrzewany garaż", draft.heatedGarage == true) { v ->
                viewModel.editDeal { it.copy(heatedGarage = v) }
            }
        } else {
            InfoRow("Projekt", deal.projectName)
            InfoRow("Rodzaj", deal.buildingKind.label)
            deal.buildingData?.takeIf { !it.isEmpty }?.let { data ->
                InfoRow("Osoby", data.people?.toString())
                InfoRow("Powierzchnia", data.areaM2?.let { "${it.toInt()} m²" })
                InfoRow("Kondygnacje", data.floors?.toString())
                InfoRow("Kształt", data.shape)
                InfoRow("Konstrukcja", data.construction)
                InfoRow("Etap budowy", data.stage)
                InfoRow("Okna", data.windows)
                InfoRow("Piwnica", data.heatedBasement?.let { if (it) "ogrzewana" else "nieogrzewana" })
                InfoRow("Garaż", data.heatedGarage?.let { if (it) "ogrzewany" else "nieogrzewany" })
            }
            deal.ozcData?.takeIf { !it.isEmpty }?.let { ozc ->
                InfoRow("OZC budynek", ozc.buildingKw?.let { "${it.toPlainText()} kW" })
                InfoRow("OZC CWU", ozc.dhwKw?.let { "${it.toPlainText()} kW" })
                InfoRow("OZC potwierdzone", if (ozc.confirmed) "tak" else null)
            }
        }
    }
}

// ── Pozostałe pola deala ─────────────────────────────────────────────────────

@Composable
private fun DealFieldsCard(
    state: DealDetailViewModel.UiState,
    deal: Deal,
    viewModel: DealDetailViewModel,
) {
    SectionCard {
        SectionTitle("Deal")
        SectionGap()

        if (state.editing) {
            val draft = state.dealDraft
            FormChoiceRow(
                label = "Segment",
                options = DealSegment.entries.toList(),
                selected = draft.segment,
                optionLabel = { it.label },
                onSelect = { v -> v?.let { s -> viewModel.editDeal { it.copy(segment = s) } } },
            )
            Spacer(Modifier.height(8.dp))
            FormChoiceRow(
                label = "Trudność",
                options = DealDifficulty.entries.toList(),
                selected = draft.difficulty,
                optionLabel = { it.label },
                onSelect = { v -> viewModel.editDeal { it.copy(difficulty = v) } },
                nullLabel = "brak",
            )
            Spacer(Modifier.height(8.dp))
            FormChoiceRow(
                label = "Buyer persona",
                options = DealBuyerPersona.entries.toList(),
                selected = draft.buyerPersona,
                optionLabel = { it.label },
                onSelect = { v -> viewModel.editDeal { it.copy(buyerPersona = v) } },
                nullLabel = "brak",
            )
            Spacer(Modifier.height(8.dp))
            FormTextField("Źródło", draft.source) { v -> viewModel.editDeal { it.copy(source = v) } }
            Spacer(Modifier.height(8.dp))
            FormTextField("Kod rabatowy", draft.discountCode) { v ->
                viewModel.editDeal { it.copy(discountCode = v) }
            }
            Spacer(Modifier.height(8.dp))
            FormTextField(
                label = "Opis",
                value = draft.description,
                singleLine = false,
                minLines = 3,
                onValueChange = { v -> viewModel.editDeal { it.copy(description = v) } },
            )
            Spacer(Modifier.height(4.dp))
            FormSwitch("Zgoda RODO", draft.rodoConsent) { v ->
                viewModel.editDeal { it.copy(rodoConsent = v) }
            }
        } else {
            InfoRow("Segment", deal.segment.label)
            InfoRow("Trudność", deal.difficulty?.label)
            InfoRow("Buyer persona", deal.buyerPersona?.label)
            InfoRow("Źródło", deal.source)
            InfoRow("Kod rabatowy", deal.discountCode)
            InfoRow(
                label = "Zgoda RODO",
                value = if (deal.rodoConsent) {
                    listOfNotNull("Tak", formatDate(deal.rodoConsentAt)).joinToString(" · ")
                } else {
                    "Nie"
                },
            )
            InfoRow("Data zgłoszenia", formatDateTime(deal.createdAt))
            InfoRow("Następny kontakt", formatDate(deal.nextContactAt))
            deal.description?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

// ── Przyciski edycji ─────────────────────────────────────────────────────────

@Composable
private fun EditActions(state: DealDetailViewModel.UiState, viewModel: DealDetailViewModel) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (state.editing) {
            OutlinedButton(
                onClick = viewModel::cancelEdit,
                enabled = !state.isSaving,
                modifier = Modifier.weight(1f),
            ) { Text("Anuluj") }
            Button(
                onClick = viewModel::saveEdit,
                enabled = !state.isSaving && state.isDirty,
                modifier = Modifier.weight(1f),
            ) { Text(if (state.isSaving) "Zapisuję…" else "Zapisz") }
        } else {
            OutlinedButton(
                onClick = viewModel::startEdit,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Edytuj dane") }
        }
    }
}

// ── Asystent karty ───────────────────────────────────────────────────────────

/**
 * Q&A ograniczone do komunikacji w tym dealu. Wątek jest krótki z założenia —
 * to narzędzie do jednego pytania przed rozmową, nie czat.
 */
@Composable
private fun AssistantCard(
    assistant: DealDetailViewModel.AssistantState,
    onAsk: (String) -> Unit,
) {
    var question by remember { mutableStateOf("") }

    SectionCard {
        SectionTitle("Asystent karty")
        SectionGap()

        if (!assistant.configured) {
            Text(
                text = "Asystent nie jest skonfigurowany na serwerze — odpowiedzi są informacyjne.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
        }

        assistant.messages.forEach { message ->
            val isUser = message.role == AssistantMessage.ROLE_USER
            Text(
                text = message.content,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isUser) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isUser) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = question,
                onValueChange = { question = it },
                label = { Text("Zapytaj o tego klienta") },
                singleLine = true,
                enabled = !assistant.isAsking,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            if (assistant.isAsking) {
                CircularProgressIndicator(Modifier.size(24.dp))
            } else {
                IconButton(
                    onClick = {
                        onAsk(question)
                        question = ""
                    },
                    enabled = question.isNotBlank(),
                ) {
                    Icon(Icons.Default.Send, contentDescription = "Wyślij pytanie")
                }
            }
        }
    }
}

/** Ile wpisów kartoteki pokazujemy w oknie wyboru kontaktu. */
private const val MAX_CONTACT_RESULTS = 12
