package com.ekotak.teamtalk.presentation.leave

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ekotak.teamtalk.domain.model.LeaveMode
import com.ekotak.teamtalk.domain.model.LeaveType
import com.ekotak.teamtalk.presentation.service.FieldBox
import com.ekotak.teamtalk.presentation.service.FieldRow
import com.ekotak.teamtalk.presentation.service.WarningBar
import com.ekotak.teamtalk.presentation.service.sheetBottomPadding
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Arkusz wniosku urlopowego — jeden na składanie i na zmianę, jak modal panelu.
 *
 * Dwie rzeczy, których panel nie pokazuje przed wysłaniem, a które na telefonie
 * decydują o tym, czy człowiek w ogóle naciśnie przycisk (makieta, ekran 01):
 * ile dni wymiaru ZOSTANIE po zatwierdzeniu i KTO ten wniosek rozpatrzy.
 *
 * Wariant po odmowie 409 ([LeaveViewModel.LeaveForm.conflictWith]) nie jest
 * osobnym arkuszem: to ten sam formularz przestawiony na zmianę wniosku,
 * z którym okres się nałożył — bo drugiego API i tak nie przyjmie.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun LeaveRequestSheet(
    form: LeaveViewModel.LeaveForm,
    state: LeaveViewModel.UiState,
    onEdit: ((LeaveViewModel.LeaveForm) -> LeaveViewModel.LeaveForm) -> Unit,
    onSave: () -> Unit,
    onCancelRequest: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var picking by remember { mutableStateOf<DayField?>(null) }

    val conflict = form.conflictWith
    val remainingAfter = state.remainingAfter(form)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = sheetBottomPadding()),
        ) {
            Text(
                text = when {
                    conflict != null -> "Okres się nakłada"
                    form.isNew -> "Nowy wniosek"
                    else -> "Zmiana wniosku"
                },
                style = MaterialTheme.typography.titleMedium,
            )

            if (conflict != null) {
                WarningBar(
                    "Masz już wniosek na ${rangeLabel(conflict.start, conflict.end)} " +
                        "(${conflict.type.label.lowercase()}, ${conflict.status.label.lowercase()}). " +
                        "Zmień istniejący zamiast składać drugi — zapis cofa go do akceptacji.",
                    color = LeaveAccent,
                )
            } else if (!form.isNew) {
                WarningBar(
                    "Zapis cofa wniosek do akceptacji zwierzchnika.",
                    color = LeaveAccent,
                )
            }

            FieldRow(
                left = { modifier ->
                    FieldBox(
                        label = "Od",
                        value = dayLabel(form.start),
                        modifier = modifier,
                        onClick = { picking = DayField.START },
                    )
                },
                right = { modifier ->
                    FieldBox(
                        label = "Do",
                        value = dayLabel(form.end),
                        modifier = modifier,
                        onClick = { picking = DayField.END },
                    )
                },
            )

            Text(
                text = "Rodzaj",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                // Poza umową o pracę zostaje sam bezpłatny — pozostałe rodzaje
                // pokazujemy wyszarzone, żeby było widać, czego brakuje i dlaczego.
                LeaveType.entries.forEach { type ->
                    val available = type in state.availableTypes
                    FilterChip(
                        selected = form.type == type,
                        enabled = available,
                        onClick = { onEdit { it.copy(type = type) } },
                        label = { Text(type.label) },
                    )
                }
            }
            if (state.mode == LeaveMode.UNPAID) {
                Text(
                    text = "Twoja umowa (${state.balance?.employmentType ?: "inna niż o pracę"}) " +
                        "nie daje wymiaru urlopu — dostępny jest wyłącznie bezpłatny.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            InfoCard(
                title = workingDaysLabel(form.workingDays),
                body = buildString {
                    append("Weekendy i święta pominięte.")
                    if (remainingAfter != null) {
                        // Ujemna reszta to nie „zostanie −14", tylko przekroczenie
                        // wymiaru — tak też trzeba to powiedzieć, bo liczba ze
                        // znakiem minus czyta się jak błąd aplikacji, nie jak stan
                        // kartoteki (zobaczone na urządzeniu 2026-09-07).
                        if (remainingAfter < 0) {
                            append(" Przekroczysz wymiar o ")
                            append(daysLabel(-remainingAfter))
                            append(" — urlop ponad pulę wymaga zgody kadr.")
                        } else {
                            append(" Po zatwierdzeniu zostanie ")
                            append(remainingAfter)
                            append(" z ")
                            append(state.balance?.entitled ?: 0)
                            append(" dni wymiaru.")
                        }
                    }
                },
                warn = form.workingDays == 0 || (remainingAfter != null && remainingAfter < 0),
            )

            OutlinedTextField(
                value = form.reason,
                onValueChange = { text -> onEdit { it.copy(reason = text) } },
                label = { Text("Powód (opcjonalnie)") },
                singleLine = false,
                modifier = Modifier.fillMaxWidth(),
            )

            state.approverName?.let { approver ->
                Text(
                    text = "Decyzję podejmie $approver.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Button(
                onClick = onSave,
                enabled = !form.saving && form.workingDays > 0,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (form.saving) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                }
                Text(
                    when {
                        conflict != null -> "Zapisz zmianę"
                        form.isNew -> "Złóż wniosek"
                        else -> "Zapisz zmianę"
                    },
                )
            }

            // Anulowanie stoi tu, a nie w liście: po odmowie 409 to często
            // szybsza droga niż przestawianie dat wniosku, który i tak przeszkadza.
            val cancellable = form.id ?: conflict?.id
            if (cancellable != null) {
                TextButton(
                    onClick = { onCancelRequest(cancellable) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = if (conflict != null) "Anuluj tamten wniosek" else "Anuluj wniosek",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    picking?.let { field ->
        DayPicker(
            initial = if (field == DayField.START) form.start else form.end,
            onDismiss = { picking = null },
            onPick = { day ->
                onEdit { current ->
                    if (field == DayField.START) {
                        // Przesunięcie początku za koniec zabiera ze sobą koniec —
                        // odwrotny zakres API i tak odrzuca (422).
                        current.copy(
                            start = day,
                            end = if (current.end.isBefore(day)) day else current.end,
                        )
                    } else {
                        current.copy(end = if (day.isBefore(current.start)) current.start else day)
                    }
                }
                picking = null
            },
        )
    }
}

private enum class DayField { START, END }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayPicker(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onPick: (LocalDate) -> Unit,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { millis ->
                        onPick(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    } ?: onDismiss()
                },
            ) { Text("Wybierz") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    ) {
        DatePicker(state = state)
    }
}

/** Ramka z liczbą dni i skutkiem dla wymiaru — najważniejsza treść arkusza. */
@Composable
private fun InfoCard(title: String, body: String, warn: Boolean) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
        color = if (warn) {
            MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (warn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
