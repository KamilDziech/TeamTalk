package com.ekotak.teamtalk.presentation.service

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ekotak.teamtalk.domain.model.ServiceClient
import com.ekotak.teamtalk.domain.model.ServiceJobDraft
import com.ekotak.teamtalk.domain.model.ServiceJobType
import com.ekotak.teamtalk.domain.model.Technician
import com.ekotak.teamtalk.domain.model.WarrantyCardDraft
import com.ekotak.teamtalk.domain.model.WarrantyCardStatus

/**
 * Zapis nowych pozycji modułu Serwis.
 *
 * Panel ma pod „+" belkę szybkiego dodawania: jedno pole na opis usterki i
 * rozwinięcie ze szczegółami. Na telefonie to samo mieści się w arkuszu — opis
 * u góry, szczegóły niżej, wszystko opcjonalne poza wymogiem, żeby wiersz dało
 * się na liście przeczytać (opis ALBO klient).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateJobSheet(
    allowedTypes: List<ServiceJobType>,
    clients: List<ServiceClient>,
    technicians: List<Technician>,
    pending: Boolean,
    onDismiss: () -> Unit,
    onCreate: (ServiceJobDraft) -> Unit,
) {
    var note by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(allowedTypes.first()) }
    var client by remember { mutableStateOf<ServiceClient?>(null) }
    var technician by remember { mutableStateOf<Technician?>(null) }
    var scheduledAt by remember { mutableStateOf<String?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // `skipPartiallyExpanded`: bez tego arkusz otwiera sie do polowy ekranu
    // i przyciski „Anuluj"/„Zapisz" wypadaja pod paskiem gestow — trzeba by
    // najpierw przeciagnac arkusz w gore, zeby zobaczyc, czym sie go zapisuje.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .imePadding()
                .padding(bottom = sheetBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = if (type == ServiceJobType.AWARIA) "Nowe zgłoszenie" else "Nowy przegląd",
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedTextField(
                value = note,
                onValueChange = {
                    note = it
                    error = null
                },
                label = { Text("Opis usterki") },
                placeholder = { Text("np. nie grzeje CWU — resztę uzupełnisz później") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            if (allowedTypes.size > 1) {
                SelectField(
                    label = "Typ",
                    value = type.label,
                    options = allowedTypes,
                    optionLabel = { it.label },
                    onSelect = { type = it },
                )
            }
            SelectField(
                label = "Klient",
                value = client?.label ?: "— bez klienta —",
                options = listOf<ServiceClient?>(null) + clients,
                optionLabel = { it?.label ?: "— bez klienta —" },
                onSelect = {
                    client = it
                    error = null
                },
            )
            FieldRow(
                left = { mod ->
                    SelectField(
                        label = "Serwisant",
                        value = technician?.displayName ?: "— nikt —",
                        options = listOf<Technician?>(null) + technicians,
                        optionLabel = { it?.displayName ?: "— nikt —" },
                        onSelect = { technician = it },
                        modifier = mod,
                    )
                },
                right = { mod ->
                    FieldBox(
                        label = "Termin",
                        value = formatDayOrDash(scheduledAt),
                        onClick = { showPicker = true },
                        modifier = mod,
                    )
                },
            )

            error?.let { WarningBar(it) }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) { Text("Anuluj") }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        // Zlecenie wolno zapisać niedouzupełnione, ale musi dać się
                        // przeczytać na liście — panel wymaga tu dokładnie tego samego.
                        if (note.isBlank() && client == null) {
                            error = "Wpisz opis usterki albo wskaż klienta — resztę uzupełnisz później."
                            return@Button
                        }
                        onCreate(
                            ServiceJobDraft(
                                clientId = client?.id,
                                type = type,
                                technicianId = technician?.id,
                                scheduledAt = scheduledAt,
                                note = note.trim().ifBlank { null },
                            ),
                        )
                    },
                    enabled = !pending,
                ) { Text("Zapisz") }
            }
            Spacer(Modifier.height(4.dp))
        }
    }

    if (showPicker) {
        DayPickerDialog(
            initialIso = scheduledAt,
            onDismiss = { showPicker = false },
            onPick = { scheduledAt = it },
        )
    }
}

/** Nowa karta gwarancyjna — odpowiednik `WarrantyCreateForm.tsx`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateCardSheet(
    pending: Boolean,
    onDismiss: () -> Unit,
    onCreate: (WarrantyCardDraft) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var brand by remember { mutableStateOf("Panasonic") }
    var location by remember { mutableStateOf("") }
    var status by remember { mutableStateOf(WarrantyCardStatus.OCZEKUJACE) }
    var commissionedAt by remember { mutableStateOf<String?>(null) }
    var outModel by remember { mutableStateOf("") }
    var outSerial by remember { mutableStateOf("") }
    var inModel by remember { mutableStateOf("") }
    var inSerial by remember { mutableStateOf("") }
    var showPicker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // `skipPartiallyExpanded`: bez tego arkusz otwiera sie do polowy ekranu
    // i przyciski „Anuluj"/„Zapisz" wypadaja pod paskiem gestow — trzeba by
    // najpierw przeciagnac arkusz w gore, zeby zobaczyc, czym sie go zapisuje.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .imePadding()
                .padding(bottom = sheetBottomPadding()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Nowa karta gwarancyjna", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    error = null
                },
                label = { Text("Nazwa *") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            FieldRow(
                left = { mod ->
                    OutlinedTextField(
                        value = brand,
                        onValueChange = { brand = it },
                        label = { Text("Producent") },
                        singleLine = true,
                        modifier = mod,
                    )
                },
                right = { mod ->
                    SelectField(
                        label = "Status",
                        value = status.label,
                        options = WarrantyCardStatus.entries,
                        optionLabel = { it.label },
                        onSelect = { status = it },
                        modifier = mod,
                    )
                },
            )
            OutlinedTextField(
                value = location,
                onValueChange = { location = it },
                label = { Text("Lokalizacja") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            FieldBox(
                label = "Uruchomienie",
                value = formatDayOrDash(commissionedAt),
                onClick = { showPicker = true },
            )
            FieldRow(
                left = { mod ->
                    OutlinedTextField(
                        value = outModel,
                        onValueChange = { outModel = it },
                        label = { Text("Jedn. zewn. — model") },
                        singleLine = true,
                        modifier = mod,
                    )
                },
                right = { mod ->
                    OutlinedTextField(
                        value = outSerial,
                        onValueChange = { outSerial = it },
                        label = { Text("Jedn. zewn. — nr ser.") },
                        singleLine = true,
                        modifier = mod,
                    )
                },
            )
            FieldRow(
                left = { mod ->
                    OutlinedTextField(
                        value = inModel,
                        onValueChange = { inModel = it },
                        label = { Text("Jedn. wewn. — model") },
                        singleLine = true,
                        modifier = mod,
                    )
                },
                right = { mod ->
                    OutlinedTextField(
                        value = inSerial,
                        onValueChange = { inSerial = it },
                        label = { Text("Jedn. wewn. — nr ser.") },
                        singleLine = true,
                        modifier = mod,
                    )
                },
            )

            error?.let { WarningBar(it) }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) { Text("Anuluj") }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = {
                        if (name.isBlank()) {
                            error = "Wymagana nazwa."
                            return@Button
                        }
                        onCreate(
                            WarrantyCardDraft(
                                name = name.trim(),
                                brand = brand.trim().ifBlank { "Panasonic" },
                                location = location.trim().ifBlank { null },
                                commissionedAt = commissionedAt,
                                status = status,
                                outdoorModel = outModel.trim().ifBlank { null },
                                outdoorSerial = outSerial.trim().ifBlank { null },
                                indoorModel = inModel.trim().ifBlank { null },
                                indoorSerial = inSerial.trim().ifBlank { null },
                            ),
                        )
                    },
                    enabled = !pending,
                ) { Text("Utwórz kartę") }
            }
            Spacer(Modifier.height(4.dp))
        }
    }

    if (showPicker) {
        DayPickerDialog(
            initialIso = commissionedAt,
            onDismiss = { showPicker = false },
            onPick = { commissionedAt = it },
        )
    }
}
