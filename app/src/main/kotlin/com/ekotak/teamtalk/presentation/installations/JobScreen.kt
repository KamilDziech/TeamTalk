package com.ekotak.teamtalk.presentation.installations

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ekotak.teamtalk.domain.model.MontazJob
import com.ekotak.teamtalk.domain.model.MontazStatus
import com.ekotak.teamtalk.presentation.components.AppTopBar
import com.ekotak.teamtalk.presentation.crm.SectionCard
import com.ekotak.teamtalk.presentation.crm.SectionGap
import com.ekotak.teamtalk.presentation.crm.SectionTitle
import com.ekotak.teamtalk.presentation.crm.formatQty
import com.ekotak.teamtalk.presentation.theme.OkGreen
import com.ekotak.teamtalk.presentation.theme.Orange600
import com.ekotak.teamtalk.presentation.theme.Red600
import com.ekotak.teamtalk.presentation.theme.SyncBlue

/**
 * KARTA WYJAZDU — teczka ekipy na jeden montaż.
 *
 * Ekran do CZYTANIA: adres z przyciskiem nawigacji na samej górze, pasek
 * czterech kroków, zakres z PODPISANEJ umowy razem z tym, co jest poza nią,
 * obsada i uwaga. Wpisuje się gdzie indziej — na liście pakowania i w protokole.
 */
@Composable
fun JobScreen(
    onNavigateBack: () -> Unit,
    onOpenPacking: () -> Unit,
    onOpenProtocol: () -> Unit,
    viewModel: JobViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var confirmLeaving by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = { AppTopBar(title = "Montaż", onNavigateBack = onNavigateBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        val job = state.job
        when {
            state.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            job == null -> Box(
                Modifier.fillMaxSize().padding(padding).padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = state.error ?: "Nie udało się otworzyć tego wyjazdu.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 12.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (state.fromCache) {
                    Banner(
                        text = "Bez zasięgu — teczka z pamięci telefonu.",
                        color = SyncBlue,
                    )
                }

                JobHeader(
                    job = job,
                    onNavigate = { navigateTo(context, job) },
                )

                StepBar(step = state.step)

                // ── Pakowanie ──
                SectionCard(modifier = Modifier.clickable(onClick = onOpenPacking)) {
                    SectionTitle(
                        text = "Zabrać z magazynu",
                        accent = "${state.packedTotal} / ${state.itemsTotal}",
                    )
                    SectionGap()
                    Text(
                        text = "Materiał ${state.materialsPacked}/${state.materials.size} · " +
                            "narzędzia ${state.toolsPacked}/${state.tools.size}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.shortages.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Brakuje na stanie: " +
                                state.shortages.joinToString(", ") { it.itemName },
                            style = MaterialTheme.typography.bodySmall,
                            color = Red600,
                        )
                    }
                }

                // ── Co wykonać ──
                job.contract?.let { contract ->
                    SectionCard {
                        SectionTitle(text = "Co wykonać", accent = "umowa ${contract.numer}")
                        SectionGap()
                        contract.pozycje.forEach { p ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                Text(
                                    text = "✓",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OkGreen,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    text = "${p.opis} — ${formatQty(p.ilosc)} ${p.jm}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        if (contract.wylaczony.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            Text(
                                text = "POZA UMOWĄ — nie robimy bez zgody biura:",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = Red600,
                            )
                            contract.wylaczony.forEach {
                                Text(
                                    text = "✗ $it",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Red600,
                                )
                            }
                        }
                    }
                }

                // ── Uwaga dla ekipy ──
                job.teamNote?.takeIf { it.isNotBlank() }?.let { note ->
                    SectionCard {
                        SectionTitle(text = "Uwaga dla ekipy")
                        SectionGap()
                        Text(text = note, style = MaterialTheme.typography.bodyMedium, color = Orange600)
                    }
                }

                // ── Obsada ──
                if (job.assignees.isNotEmpty()) {
                    SectionCard {
                        SectionTitle(text = "Obsada", accent = "${job.assignees.size} os.")
                        SectionGap()
                        Text(
                            text = job.assignees.joinToString(" · ") { a ->
                                listOfNotNull(a.name, a.role).joinToString(" ")
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                // ── Protokół ──
                SectionCard(modifier = Modifier.clickable(onClick = onOpenProtocol)) {
                    val (done, total) = state.protocolProgress
                    SectionTitle(
                        text = "Protokół odbioru",
                        accent = if (state.protocol.closed) "zamknięty" else "$done / $total",
                    )
                    SectionGap()
                    Text(
                        text = when {
                            state.protocol.closed ->
                                "Podpisany. Montaż oznaczony jako gotowy."
                            state.protocol.items.isEmpty() ->
                                "Węzły zakresu nie mają pytań — zostaje odbiór i podpis."
                            else ->
                                "Pytania z zakresu zlecenia. Bez wymaganych odpowiedzi " +
                                    "i zdjęć nie da się go zamknąć."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                // ── Akcja kroku ──
                if (job.row.status == MontazStatus.PLANNED) {
                    Button(
                        onClick = { confirmLeaving = true },
                        enabled = !state.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Wyjeżdżamy — zacznij robotę") }
                } else if (job.row.status == MontazStatus.IN_PROGRESS) {
                    OutlinedButton(
                        onClick = onOpenProtocol,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Wypełnij protokół odbioru") }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (confirmLeaving) {
        val job = state.job
        LeavingSheet(
            state = state,
            onDismiss = { confirmLeaving = false },
            onConfirm = {
                confirmLeaving = false
                viewModel.startWork()
                job?.let { navigateTo(context, it) }
            },
        )
    }
}

/** Nagłówek: klient, adres, termin i wielki przycisk nawigacji. */
@Composable
private fun JobHeader(job: MontazJob, onNavigate: () -> Unit) {
    SectionCard {
        Text(text = job.row.clientName, style = MaterialTheme.typography.titleMedium)
        Text(
            text = listOfNotNull(
                job.row.address ?: job.row.city,
                hourLabel(job.row.scheduledAt)?.let { "godz. $it" },
                if (job.row.durationDays > 1) "${job.row.durationDays} dni" else null,
                job.row.difficulty?.label,
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (job.row.scopeNames.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = job.row.scopeNames.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onNavigate,
            enabled = !job.row.address.isNullOrBlank() || job.lat != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Nawiguj pod adres budowy") }
    }
}

/**
 * Pasek czterech kroków dnia. Nie jest ozdobą: ekipa widzi po nim, co zostało,
 * bez wchodzenia w każdą sekcję po kolei.
 */
@Composable
private fun StepBar(step: JobViewModel.Step) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        JobViewModel.Step.entries.forEachIndexed { index, item ->
            val done = item.ordinal < step.ordinal
            val now = item == step
            val color = when {
                done -> OkGreen
                now -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.outlineVariant
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (done) "✓" else "${index + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = color,
                )
                Text(
                    text = item.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (now) MaterialTheme.colorScheme.onSurface else color,
                )
            }
        }
    }
}

/**
 * Potwierdzenie wyjazdu. Braki na stanie i niespakowany sprzęt OSTRZEGAJĄ,
 * ale nie blokują — decyzja, czy jechać, należy do ekipy, nie do telefonu.
 */
@Composable
private fun LeavingSheet(
    state: JobViewModel.UiState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Gotowe do wyjazdu?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Spakowane: ${state.packedTotal} / ${state.itemsTotal}",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (state.toolsRequiredLeft > 0) {
                    Text(
                        text = "Brakuje ${state.toolsRequiredLeft} wymaganych narzędzi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Orange600,
                    )
                }
                if (state.shortages.isNotEmpty()) {
                    Text(
                        text = "Magazyn nie ma pełnej ilości: " +
                            state.shortages.joinToString(", ") { it.itemName },
                        style = MaterialTheme.typography.bodySmall,
                        color = Red600,
                    )
                }
                Text(
                    text = "Klikając „Wyjeżdżamy” potwierdzasz, że ekipa o tym wie — " +
                        "koordynator zobaczy ten montaż jako w realizacji.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { Button(onClick = onConfirm) { Text("Wyjeżdżamy") } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text("Jeszcze nie") } },
    )
}

/**
 * Nawigacja pod budowę. `geo:` z adresem tekstowym, a nie link do konkretnej
 * apki: aplikację wybiera system, tak samo jak w Serwisie i na karcie audytu.
 * Współrzędne bierzemy, gdy kartoteka je ma — adres z umowy bywa opisowy
 * („działka nr 114/2"), a pin nie pyta o nic.
 */
private fun navigateTo(context: android.content.Context, job: MontazJob) {
    val uri = when {
        job.lat != null && job.lng != null ->
            Uri.parse("geo:${job.lat},${job.lng}?q=${job.lat},${job.lng}(${Uri.encode(job.row.clientName)})")
        else -> Uri.parse("geo:0,0?q=${Uri.encode(job.row.address ?: job.row.city.orEmpty())}")
    }
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
