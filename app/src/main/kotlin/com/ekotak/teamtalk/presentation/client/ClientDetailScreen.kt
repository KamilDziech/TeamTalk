package com.ekotak.teamtalk.presentation.client

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.AddTask
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ekotak.teamtalk.domain.model.AssistantMessage
import com.ekotak.teamtalk.domain.model.Client
import com.ekotak.teamtalk.domain.model.ClientCategory
import com.ekotak.teamtalk.domain.model.ClientDeal
import com.ekotak.teamtalk.presentation.components.AppTopBar

/** Podpowiedzi startowe asystenta — te same pytania co w panelu. */
private val ASSISTANT_SUGGESTIONS = listOf(
    "Podsumuj historię współpracy z klientem.",
    "Jakie instalacje ma lub rozważał klient?",
    "O co klient ostatnio pytał?",
    "Czego klient jeszcze oczekuje?",
)

private enum class ClientTab(val label: String) {
    DANE("Dane"),
    DEALE("Deale"),
    HISTORIA("Historia"),
    ASYSTENT("Asystent"),
}

/**
 * Karta klienta. Panel mieści dwie zakładki w szufladzie obok tabeli; na
 * telefonie karta jest osobnym ekranem, a zakładek są cztery: do „Danych" i
 * „Deali" dochodzi historia połączeń (dotąd osobny ekran) i asystent, który w
 * webie siedzi w „Danych" — czat wciśnięty w przewijaną listę pól byłby na
 * wąskim ekranie nieczytelny.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientDetailScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDeal: (String) -> Unit,
    onNavigateToEdit: (String) -> Unit,
    onNavigateToCallDetail: (String) -> Unit,
    onCreateTask: (phone: String, name: String?) -> Unit,
    viewModel: ClientDetailViewModel = hiltViewModel(),
    timelineViewModel: ClientTimelineViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val timeline by timelineViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableStateOf(ClientTab.DANE) }
    var menuOpen by remember { mutableStateOf(false) }
    var eraseDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    val client = state.client

    Scaffold(
        topBar = {
            AppTopBar(title = client?.displayName ?: "Klient", onNavigateBack = onNavigateBack) {
                if (state.canManage && client != null) {
                    IconButton(onClick = { onNavigateToEdit(client.id) }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edytuj dane")
                    }
                }
                if (state.canErase && client != null) {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Więcej")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Usuń dane (RODO)") },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                },
                                onClick = {
                                    menuOpen = false
                                    eraseDialog = true
                                },
                            )
                        }
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (client == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                if (state.isLoading) {
                    CircularProgressIndicator()
                } else {
                    Text(
                        text = "Nie znaleziono klienta w kartotece.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            return@Scaffold
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ClientHeader(client = client, dealCount = state.deals.size)

            TabRow(selectedTabIndex = tab.ordinal) {
                ClientTab.entries.forEach { entry ->
                    Tab(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        text = {
                            Text(
                                text = if (entry == ClientTab.DEALE && state.deals.isNotEmpty()) {
                                    "${entry.label} ${state.deals.size}"
                                } else {
                                    entry.label
                                },
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }

            when (tab) {
                ClientTab.DANE -> DataTab(
                    client = client,
                    installations = state.installations,
                    sharedWith = state.sharedWith,
                    onCall = viewModel::call,
                    onSms = viewModel::sms,
                    onNavigate = viewModel::navigate,
                    onCreateTask = {
                        onCreateTask(client.primaryPhone.orEmpty(), client.displayName)
                    },
                )

                ClientTab.DEALE -> DealsTab(deals = state.deals, onOpenDeal = onNavigateToDeal)

                ClientTab.HISTORIA -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (timeline.entries.isEmpty()) {
                        item(key = "empty-timeline") {
                            Text(
                                text = "Brak historii połączeń",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(timeline.entries, key = { it.callLog.id }) { entry ->
                        TimelineEntryCard(
                            entry = entry,
                            onOpenDetail = { onNavigateToCallDetail(entry.callLog.id) },
                        )
                    }
                }

                ClientTab.ASYSTENT -> AssistantTab(
                    log = state.assistantLog,
                    pending = state.assistantPending,
                    notice = state.assistantNotice,
                    error = state.assistantError,
                    onAsk = viewModel::ask,
                )
            }
        }
    }

    if (eraseDialog && client != null) {
        AlertDialog(
            onDismissRequest = { eraseDialog = false },
            title = { Text("Usunąć dane osobowe?") },
            text = {
                Text(
                    "Dane klienta „${client.displayName}” zostaną trwale zanonimizowane " +
                        "(RODO). Rekord zostanie w kartotece, żeby deale się nie rozjechały. " +
                        "Operacji nie można cofnąć.",
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !state.isErasing,
                    onClick = {
                        eraseDialog = false
                        viewModel.erase(onDone = {})
                    },
                ) {
                    Text("Anonimizuj", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { eraseDialog = false }) { Text("Anuluj") }
            },
        )
    }
}

@Composable
private fun ClientHeader(client: Client, dealCount: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = client.initials,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = client.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${client.categoryHeadLabel} · deale: $dealCount",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DataTab(
    client: Client,
    installations: List<String>,
    sharedWith: List<String>,
    onCall: () -> Unit,
    onSms: () -> Unit,
    onNavigate: () -> Unit,
    onCreateTask: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
    ) {
        item(key = "actions") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val hasPhone = !client.primaryPhone.isNullOrBlank()
                QuickAction(
                    icon = Icons.Default.Phone,
                    label = "Zadzwoń",
                    enabled = hasPhone,
                    onClick = onCall,
                    modifier = Modifier.weight(1f),
                )
                QuickAction(
                    icon = Icons.AutoMirrored.Filled.Message,
                    label = "SMS",
                    enabled = hasPhone,
                    onClick = onSms,
                    modifier = Modifier.weight(1f),
                )
                QuickAction(
                    icon = Icons.Default.Navigation,
                    label = "Nawiguj",
                    enabled = client.hasGeo || !client.address.isNullOrBlank(),
                    onClick = onNavigate,
                    modifier = Modifier.weight(1f),
                )
                QuickAction(
                    icon = Icons.Default.AddTask,
                    label = "Zadanie",
                    enabled = hasPhone,
                    onClick = onCreateTask,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        item(key = "fields") {
            InfoRow("Telefon", client.phone ?: "—")
            client.phone2?.takeIf { it.isNotBlank() }?.let { InfoRow("Telefon 2", it) }
            InfoRow("E-mail", client.email ?: "—")
            client.email2?.takeIf { it.isNotBlank() }?.let { InfoRow("E-mail 2", it) }
            InfoRow("Adres", client.address ?: "—")
            InfoRow(
                label = "Adres zwalidowany",
                value = if (client.hasGeo) {
                    listOfNotNull(
                        "tak",
                        client.geoCity,
                        client.geoMunicipality?.let { "g. $it" },
                    ).joinToString(" · ")
                } else {
                    "nie"
                },
            )
            client.travel?.let { travel ->
                val legs = listOfNotNull(
                    travel.kobiernice?.let { "Kobiernice ${formatTravelLeg(it.km, it.min)}" },
                    travel.gliwice?.let { "Gliwice ${formatTravelLeg(it.km, it.min)}" },
                )
                if (legs.isNotEmpty()) InfoRow("Dojazd z bazy", legs.joinToString("\n"))
            }
            InfoRow("Kategoria", client.category.detailLabel)
            if (client.category == ClientCategory.KLIENT) {
                InfoRow("Typ", client.type.label)
            }
        }

        item(key = "installations") {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Instalacje (suma z deali)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            if (installations.isEmpty()) {
                Text(text = "—", style = MaterialTheme.typography.bodyMedium)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    installations.forEach { name ->
                        InstallBadgeChip(name = name)
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        if (sharedWith.isNotEmpty()) {
            item(key = "shared") {
                Spacer(Modifier.height(12.dp))
                InfoRow("Deal wspólny z", sharedWith.joinToString(", "))
            }
        }

        item(key = "bottom") { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun DealsTab(deals: List<ClientDeal>, onOpenDeal: (String) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (deals.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = "Klient nie ma jeszcze deali.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(deals, key = { it.id }) { deal ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                modifier = Modifier.fillMaxWidth().clickable { onOpenDeal(deal.id) },
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MainStageChip(deal.stage)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = formatPln(deal.value),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        Icons.Default.ChevronRight,
                        contentDescription = "Otwórz kartę deala",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssistantTab(
    log: List<AssistantMessage>,
    pending: Boolean,
    notice: String?,
    error: String?,
    onAsk: (String) -> Unit,
) {
    var input by rememberSaveable { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "hint") {
                Text(
                    text = "Odpowiada na podstawie notatek i komunikacji ze wszystkich deali klienta.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (log.isEmpty()) {
                item(key = "suggestions") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        ASSISTANT_SUGGESTIONS.forEach { suggestion ->
                            SuggestionChip(
                                onClick = { if (!pending) onAsk(suggestion) },
                                label = { Text(suggestion) },
                            )
                        }
                    }
                }
            }

            items(log.size, key = { "msg-$it" }) { index ->
                val message = log[index]
                val isUser = message.role == AssistantMessage.ROLE_USER
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
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
                }
            }

            if (pending) {
                item(key = "pending") {
                    Text(
                        text = "…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            error?.let {
                item(key = "error") {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }

            if (error == null && notice != null) {
                item(key = "notice") {
                    Text(
                        text = notice,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        HorizontalDivider()
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Zapytaj o tego klienta…") },
                enabled = !pending,
                singleLine = true,
            )
            IconButton(
                enabled = !pending && input.isNotBlank(),
                onClick = {
                    onAsk(input)
                    input = ""
                },
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Zapytaj")
            }
        }
    }
}

@Composable
private fun QuickAction(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.clickable(enabled = enabled, onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.42f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(0.58f),
        )
    }
}
