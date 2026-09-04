package com.ekotak.teamtalk.presentation.service

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ekotak.teamtalk.domain.model.ServiceJobPriority
import com.ekotak.teamtalk.domain.model.ServiceJobStatus
import com.ekotak.teamtalk.presentation.components.AppTopBar
import com.ekotak.teamtalk.presentation.theme.EkotakBlack
import com.ekotak.teamtalk.presentation.theme.EkotakGreen
import com.ekotak.teamtalk.presentation.theme.Orange600

/**
 * Karta zlecenia serwisowego — mobilny odpowiednik szuflady z panelu.
 *
 * Zawartość 1:1: gwiazdka priorytetu i chip SLA w nagłówku, ptaszek „wykonane",
 * edytowalny opis usterki jako tytuł, czerwona lista braków, pola klient /
 * status / serwisant / termin / okno SLA / koniec SLA / deal. Na dole trzy
 * skróty terenowe, których panel nie ma i mieć nie musi: telefon do klienta,
 * nawigacja intentem `geo:` i wybór terminu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceJobScreen(
    onNavigateBack: () -> Unit,
    onOpenDeal: (String) -> Unit,
    viewModel: ServiceJobViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Zlecenie",
                onNavigateBack = onNavigateBack,
                actions = {
                    val high = state.job?.priority == ServiceJobPriority.HIGH
                    IconButton(onClick = viewModel::togglePriority, enabled = state.job != null) {
                        Icon(
                            imageVector = if (high) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = if (high) "Zdejmij priorytet" else "Oznacz jako pilne",
                            tint = if (high) Orange600 else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val job = state.job
        if (job == null) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                if (state.error != null) Notice(state.error!!) else CircularProgressIndicator()
            }
            return@Scaffold
        }

        val meta = rowMeta(job, state.now)
        val hint = if (job.status == ServiceJobStatus.DONE) "" else missingHint(job)
        var note by remember(job.id, job.note) { mutableStateOf(jobTitle(job)) }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f))
                SlaCell(meta)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                DoneCheck(
                    done = job.status == ServiceJobStatus.DONE,
                    enabled = !state.pending,
                    onClick = viewModel::toggleDone,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = jobTitle(job),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                )
            }

            if (hint.isNotEmpty()) WarningBar(hint)

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Opis usterki") },
                minLines = 2,
                enabled = !state.pending,
                modifier = Modifier.fillMaxWidth(),
            )
            if (note.trim() != jobTitle(job)) {
                OutlinedButton(
                    onClick = { viewModel.setNote(note) },
                    enabled = !state.pending,
                    modifier = Modifier.align(Alignment.End),
                ) { Text("Zapisz opis") }
            }

            FieldRow(
                left = { mod ->
                    if (state.client != null) {
                        FieldBox(
                            label = "Klient",
                            value = listOfNotNull(
                                state.client!!.label,
                                state.client!!.city?.let { "($it)" },
                            ).joinToString(" "),
                            link = true,
                            modifier = mod,
                        )
                    } else {
                        SelectField(
                            label = "Klient",
                            value = "— wskaż klienta —",
                            options = state.clients,
                            optionLabel = { it.label },
                            onSelect = { viewModel.setClient(it.id) },
                            enabled = !state.pending,
                            warn = true,
                            modifier = mod,
                        )
                    }
                },
                right = { mod ->
                    SelectField(
                        label = "Status",
                        value = job.status.label,
                        options = ServiceJobStatus.entries,
                        optionLabel = { it.label },
                        onSelect = viewModel::setStatus,
                        enabled = !state.pending,
                        modifier = mod,
                    )
                },
            )

            FieldRow(
                left = { mod ->
                    SelectField(
                        label = "Serwisant",
                        value = state.technicians.firstOrNull { it.id == job.technicianId }
                            ?.displayName ?: "— nikt —",
                        // Pierwsza pozycja czyści przypisanie — panel ma tam „— nikt —".
                        options = listOf<com.ekotak.teamtalk.domain.model.Technician?>(null) +
                            state.technicians,
                        optionLabel = { it?.displayName ?: "— nikt —" },
                        onSelect = { viewModel.setTechnician(it?.id) },
                        enabled = !state.pending,
                        warn = job.technicianId == null && job.status != ServiceJobStatus.DONE,
                        modifier = mod,
                    )
                },
                right = { mod ->
                    FieldBox(
                        label = "Termin",
                        value = formatDay(job.scheduledAt).ifBlank { "— wskaż datę —" },
                        warn = job.scheduledAt == null && job.status != ServiceJobStatus.DONE,
                        onClick = { showDatePicker = true },
                        modifier = mod,
                    )
                },
            )

            FieldRow(
                left = { mod ->
                    SelectField(
                        label = "Okno SLA",
                        value = SLA_CHOICES.firstOrNull { it.first == job.slaHours }?.second
                            ?.let { "SLA: $it" } ?: slaDefaultLabel(job),
                        options = listOf<Pair<Int, String>?>(null) + SLA_CHOICES,
                        optionLabel = { it?.let { c -> "SLA: ${c.second}" } ?: slaDefaultLabel(job) },
                        onSelect = { viewModel.setSlaHours(it?.first) },
                        enabled = !state.pending,
                        modifier = mod,
                    )
                },
                right = { mod ->
                    FieldBox(
                        label = "Koniec SLA",
                        value = formatDayTime(job.slaDueAt).ifBlank { "—" },
                        warn = job.slaBreached,
                        modifier = mod,
                    )
                },
            )

            FieldBox(
                label = "Deal",
                value = if (job.dealId != null) "Otwórz kartę deala" else "Bez deala",
                link = job.dealId != null,
                enabled = job.dealId != null,
                onClick = job.dealId?.let { id -> { onOpenDeal(id) } },
            )

            Text(
                text = "SKRÓTY TERENOWE",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            val phone = state.client?.phone
            OutlinedButton(
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
                },
                enabled = !phone.isNullOrBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Call, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (phone.isNullOrBlank()) "Brak numeru do klienta" else "Zadzwoń do klienta")
            }
            val address = state.client?.address
            OutlinedButton(
                onClick = {
                    // `geo:0,0?q=` — adres tekstowy rozwiązuje aplikacja nawigacji,
                    // bo kartoteka trzyma adres, nie współrzędne.
                    val uri = Uri.parse("geo:0,0?q=" + Uri.encode(address.orEmpty()))
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                },
                enabled = !address.isNullOrBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Navigation, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (address.isNullOrBlank()) "Brak adresu" else "Nawiguj do klienta")
            }
            Spacer(Modifier.height(24.dp))
        }

        if (showDatePicker) {
            DayPickerDialog(
                initialIso = job.scheduledAt,
                onDismiss = { showDatePicker = false },
                onPick = viewModel::setScheduledAt,
            )
        }
    }
}

/** Ptaszek „wykonane" — pusty pierścień, gdy zlecenie jeszcze otwarte. */
@Composable
private fun DoneCheck(done: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(if (done) EkotakGreen else Color.Transparent, CircleShape)
            .border(
                width = if (done) 0.dp else 2.dp,
                color = if (done) Color.Transparent else MaterialTheme.colorScheme.outline,
                shape = CircleShape,
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Cofnij — zlecenie znów w toku",
                tint = EkotakBlack,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
