package com.ekotak.teamtalk.presentation.service

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ekotak.teamtalk.domain.model.MAX_WARRANTY_INSPECTIONS
import com.ekotak.teamtalk.domain.model.WarrantyCard
import com.ekotak.teamtalk.domain.model.WarrantyCardStatus
import com.ekotak.teamtalk.domain.model.WarrantyInspection
import com.ekotak.teamtalk.presentation.components.AppTopBar
import com.ekotak.teamtalk.presentation.theme.Orange600

/**
 * Karta przeglądów gwarancyjnych. Zawartość 1:1 z modalem panelu: producent,
 * status, uruchomienie, licznik przeglądów, numery seryjne obu jednostek,
 * harmonogram pięciu przeglądów (stałe pozycje 1..5) i notatka.
 *
 * Wiersz harmonogramu zapisuje się jednym przyciskiem — tak jak w panelu, bo
 * `PUT .../inspections` jest upsertem kompletu trzech pól, a nie edycją pola.
 */
@Composable
fun WarrantyCardScreen(
    onNavigateBack: () -> Unit,
    viewModel: WarrantyCardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = { AppTopBar(title = "Karta gwarancyjna", onNavigateBack = onNavigateBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val card = state.card
        if (card == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                if (state.error != null) Notice(state.error!!) else CircularProgressIndicator()
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Header(card)
            Meta(card = card, state = state, viewModel = viewModel)

            SectionTitle("Harmonogram przeglądów")
            val byOrdinal = card.inspections.associateBy { it.ordinal }
            for (ordinal in 1..MAX_WARRANTY_INSPECTIONS) {
                InspectionRow(
                    ordinal = ordinal,
                    inspection = byOrdinal[ordinal],
                    now = state.now,
                    pending = state.pending,
                    onSave = { planned, done, price ->
                        viewModel.saveInspection(ordinal, planned, done, price)
                    },
                )
            }

            SectionTitle("Notatka")
            NoteField(
                initial = card.note.orEmpty(),
                pending = state.pending,
                onSave = viewModel::setNote,
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Header(card: WarrantyCard) {
    Column {
        Text(text = card.name, style = MaterialTheme.typography.headlineSmall)
        if (!card.location.isNullOrBlank()) {
            Text(
                text = card.location,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Meta(
    card: WarrantyCard,
    state: WarrantyCardViewModel.UiState,
    viewModel: WarrantyCardViewModel,
) {
    var showCommissionPicker by remember { mutableStateOf(false) }
    var brand by remember(card.brand) { mutableStateOf(card.brand) }

    FieldRow(
        left = { mod ->
            Column(modifier = mod) {
                OutlinedTextField(
                    value = brand,
                    onValueChange = { brand = it },
                    label = { Text("Producent") },
                    singleLine = true,
                    enabled = !state.pending,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (brand.trim() != card.brand && brand.isNotBlank()) {
                    OutlinedButton(
                        onClick = { viewModel.setBrand(brand) },
                        enabled = !state.pending,
                    ) { Text("Zapisz") }
                }
            }
        },
        right = { mod ->
            SelectField(
                label = "Status karty",
                value = card.status.label,
                options = WarrantyCardStatus.entries,
                optionLabel = { it.label },
                onSelect = viewModel::setStatus,
                enabled = !state.pending,
                modifier = mod,
            )
        },
    )

    FieldRow(
        left = { mod ->
            FieldBox(
                label = "Uruchomienie",
                value = formatDayOrDash(card.commissionedAt),
                onClick = { showCommissionPicker = true },
                modifier = mod,
            )
        },
        right = { mod ->
            FieldBox(
                label = "Przeglądy",
                value = buildString {
                    append("${card.doneCount}/$MAX_WARRANTY_INSPECTIONS")
                    if (card.overdueCount > 0) append(" · ${card.overdueCount} po terminie")
                },
                warn = card.overdueCount > 0,
                modifier = mod,
            )
        },
    )

    UnitsFields(card = card, pending = state.pending, onSave = viewModel::setUnits)

    if (showCommissionPicker) {
        DayPickerDialog(
            initialIso = card.commissionedAt,
            onDismiss = { showCommissionPicker = false },
            onPick = viewModel::setCommissionedAt,
        )
    }
}

/** Numery jednostek — cztery pola zapisywane razem, bo wpisuje się je z tabliczki. */
@Composable
private fun UnitsFields(
    card: WarrantyCard,
    pending: Boolean,
    onSave: (String?, String?, String?, String?) -> Unit,
) {
    var outModel by remember(card.outdoorModel) { mutableStateOf(card.outdoorModel.orEmpty()) }
    var outSerial by remember(card.outdoorSerial) { mutableStateOf(card.outdoorSerial.orEmpty()) }
    var inModel by remember(card.indoorModel) { mutableStateOf(card.indoorModel.orEmpty()) }
    var inSerial by remember(card.indoorSerial) { mutableStateOf(card.indoorSerial.orEmpty()) }
    val dirty = outModel != card.outdoorModel.orEmpty() ||
        outSerial != card.outdoorSerial.orEmpty() ||
        inModel != card.indoorModel.orEmpty() ||
        inSerial != card.indoorSerial.orEmpty()

    Column(modifier = Modifier.fillMaxWidth()) {
    SectionTitle("Jednostka zewnętrzna")
    FieldRow(
        left = { mod ->
            OutlinedTextField(
                value = outModel,
                onValueChange = { outModel = it },
                label = { Text("Model") },
                singleLine = true,
                enabled = !pending,
                modifier = mod,
            )
        },
        right = { mod ->
            OutlinedTextField(
                value = outSerial,
                onValueChange = { outSerial = it },
                label = { Text("Nr seryjny") },
                singleLine = true,
                enabled = !pending,
                modifier = mod,
            )
        },
    )
    SectionTitle("Jednostka wewnętrzna")
    FieldRow(
        left = { mod ->
            OutlinedTextField(
                value = inModel,
                onValueChange = { inModel = it },
                label = { Text("Model") },
                singleLine = true,
                enabled = !pending,
                modifier = mod,
            )
        },
        right = { mod ->
            OutlinedTextField(
                value = inSerial,
                onValueChange = { inSerial = it },
                label = { Text("Nr seryjny") },
                singleLine = true,
                enabled = !pending,
                modifier = mod,
            )
        },
    )
    if (dirty) {
        OutlinedButton(
            onClick = { onSave(outModel, outSerial, inModel, inSerial) },
            enabled = !pending,
            modifier = Modifier.align(Alignment.End),
        ) { Text("Zapisz dane jednostek") }
    }
    }
}

/**
 * Jeden rok gwarancji. Światło po lewej daje stan bez czytania, obok stoi
 * etykieta słowna — te same cztery stany co w panelu.
 */
@Composable
private fun InspectionRow(
    ordinal: Int,
    inspection: WarrantyInspection?,
    now: Long,
    pending: Boolean,
    onSave: (String?, String?, Int?) -> Unit,
) {
    var planned by remember(inspection?.plannedAt) { mutableStateOf(inspection?.plannedAt) }
    var done by remember(inspection?.doneAt) { mutableStateOf(inspection?.doneAt) }
    var price by remember(inspection?.price) {
        mutableStateOf(inspection?.price?.toString().orEmpty())
    }
    var pickPlanned by remember { mutableStateOf(false) }
    var pickDone by remember { mutableStateOf(false) }

    val status = inspection?.computedStatus
        ?: com.ekotak.teamtalk.domain.model.WarrantyInspectionStatus.UNSCHEDULED
    val light = warrantyLight(status, inspection?.plannedAt, now)
    val dirty = planned != inspection?.plannedAt ||
        done != inspection?.doneAt ||
        price != inspection?.price?.toString().orEmpty()

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LightStrip(listOf(light), dotSize = 12)
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "#$ordinal · ${warrantyLightLabel(light, status)}",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                if (inspection?.suspect == true) {
                    Text(
                        text = "⚠ data przed uruchomieniem",
                        style = MaterialTheme.typography.labelSmall,
                        color = Orange600,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            FieldRow(
                left = { mod ->
                    FieldBox(
                        label = "Termin planowany",
                        value = formatDayOrDash(planned),
                        onClick = { pickPlanned = true },
                        modifier = mod,
                    )
                },
                right = { mod ->
                    FieldBox(
                        label = "Wykonano",
                        value = formatDayOrDash(done),
                        onClick = { pickDone = true },
                        modifier = mod,
                    )
                },
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = price,
                    onValueChange = { new -> price = new.filter { it.isDigit() } },
                    label = { Text("Cena (zł)") },
                    singleLine = true,
                    enabled = !pending,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { onSave(planned, done, price.toIntOrNull()) },
                    enabled = !pending && dirty,
                ) { Text("Zapisz") }
            }
        }
    }

    if (pickPlanned) {
        DayPickerDialog(
            initialIso = planned,
            onDismiss = { pickPlanned = false },
            onPick = { planned = it },
        )
    }
    if (pickDone) {
        DayPickerDialog(
            initialIso = done,
            onDismiss = { pickDone = false },
            onPick = { done = it },
        )
    }
}

@Composable
private fun NoteField(initial: String, pending: Boolean, onSave: (String) -> Unit) {
    var note by remember(initial) { mutableStateOf(initial) }
    Column {
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            label = { Text("Notatka do karty") },
            minLines = 2,
            enabled = !pending,
            modifier = Modifier.fillMaxWidth(),
        )
        if (note != initial) {
            OutlinedButton(
                onClick = { onSave(note) },
                enabled = !pending,
                modifier = Modifier.align(Alignment.End),
            ) { Text("Zapisz notatkę") }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}
