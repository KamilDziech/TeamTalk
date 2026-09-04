package com.ekotak.teamtalk.presentation.service

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ekotak.teamtalk.domain.model.ServiceDomain
import com.ekotak.teamtalk.domain.model.ServiceView
import com.ekotak.teamtalk.presentation.components.AppTopBar
import com.ekotak.teamtalk.presentation.map.MapScreen
import com.ekotak.teamtalk.presentation.map.MapViewTab
import kotlinx.coroutines.delay

/**
 * Moduł Serwis — odwzorowanie `web/src/app/app/service` na telefonie.
 *
 * Na górze zakładki widoku (Lista / Kalendarz / Mapa), niżej filtry właściwe dla
 * dziedziny. Lewa kolumna klientów z panelu nie mieści się na 360 dp, więc
 * w dziedzinie Serwis zastępuje ją poziomy pasek chipów z licznikami — ta sama
 * funkcja, inna forma (ustalenie z makiety `design/mockups/modul-serwis.html`).
 *
 * [domain] przychodzi z kafelka pulpitu i jest stała przez cały czas życia
 * ekranu: „Przeglądy" pokazują wyłącznie przeglądy, „Serwis" wyłącznie awarie.
 * Przełącznika dziedziny u góry nie ma — panel ma na to szerokość, telefon nie,
 * a dwa kafelki na pulpicie robią to samo bez dodatkowego kliknięcia
 * (ustalenie 2026-09-03).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceScreen(
    onNavigateBack: () -> Unit,
    onOpenJob: (String) -> Unit,
    onOpenCard: (String) -> Unit,
    /** Wyjścia z osadzonej mapy — punkt prowadzi do deala albo kartoteki. */
    onOpenDeal: (String) -> Unit,
    onOpenClient: (String) -> Unit,
    domain: ServiceDomain = ServiceDomain.SERWIS,
    viewModel: ServiceViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showSearch by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showCreateJob by remember { mutableStateOf(false) }
    var showCreateCard by remember { mutableStateOf(false) }

    LaunchedEffect(domain) { viewModel.openDomain(domain) }

    // Chip SLA odlicza — bez tykania „SLA 4 h" zostałoby na ekranie do rana.
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            viewModel.tick()
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = domain.title,
                onNavigateBack = onNavigateBack,
                actions = {
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Filled.Search, contentDescription = "Szukaj")
                    }
                    FilterAction(
                        count = state.activeFilterCount,
                        onClick = { showFilters = true },
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (state.view != ServiceView.MAPA) {
                FloatingActionButton(
                    onClick = {
                        if (state.isPrzeglad && state.warrantyAvailable) {
                            showAddMenu = true
                        } else {
                            showCreateJob = true
                        }
                    },
                ) {
                    Icon(Icons.Filled.Add, contentDescription = state.domain.addLabel)
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ViewTabs(selected = state.view, onSelect = viewModel::setView)

            if (showSearch) {
                SearchField(
                    value = state.query,
                    placeholder = if (state.isPrzeglad) {
                        "Klient, adres, model, nr seryjny…"
                    } else {
                        "Opis usterki, klient, miejscowość"
                    },
                    onChange = viewModel::setQuery,
                    onClear = {
                        viewModel.setQuery("")
                        showSearch = false
                    },
                )
            }

            if (state.isPrzeglad && state.view != ServiceView.MAPA) {
                SourceChips(
                    showRegular = state.showRegular,
                    showWarranty = state.showWarranty,
                    onToggleRegular = viewModel::toggleRegular,
                    onToggleWarranty = viewModel::toggleWarranty,
                )
            }
            if (!state.isPrzeglad && state.view == ServiceView.LISTA) {
                ClientChips(
                    groups = state.clientGroups,
                    pinned = state.clientPin,
                    onPin = viewModel::setClientPin,
                )
            }

            if (state.isPrzeglad && !state.warrantyAvailable) {
                Notice(
                    "Przeglądy gwarancyjne wymagają migracji i importu danych — pojawią się po " +
                        "wdrożeniu backendu.",
                )
            }
            state.error?.let { Notice(it) }

            when (state.view) {
                ServiceView.LISTA -> ServiceList(
                    state = state,
                    isRefreshing = state.isRefreshing,
                    onRefresh = viewModel::refresh,
                    onOpenJob = onOpenJob,
                    onOpenCard = onOpenCard,
                    onToggleDone = viewModel::setJobDone,
                    onTogglePriority = viewModel::toggleJobPriority,
                )
                ServiceView.KALENDARZ -> ServiceCalendar(
                    state = state,
                    onOpenJob = onOpenJob,
                    onOpenCard = onOpenCard,
                )
                // Ta sama mapa co w module Mapa, zawężona do jednego rodzaju
                // punktów — panel robi dokładnie to samo (klon mapy bez zakładek
                // Flota i Klienci).
                ServiceView.MAPA -> MapScreen(
                    onOpenDeal = onOpenDeal,
                    onOpenClient = onOpenClient,
                    lockedView = if (state.isPrzeglad) {
                        MapViewTab.INSPECTION
                    } else {
                        MapViewTab.SERVICE
                    },
                )
            }
        }
    }

    if (showAddMenu) {
        AddChoiceSheet(
            addLabel = state.domain.addLabel,
            onDismiss = { showAddMenu = false },
            onJob = {
                showAddMenu = false
                showCreateJob = true
            },
            onCard = {
                showAddMenu = false
                showCreateCard = true
            },
        )
    }

    if (showCreateJob) {
        CreateJobSheet(
            allowedTypes = state.domain.types,
            clients = state.clients.values.sortedBy { it.label.lowercase() },
            technicians = state.technicians,
            pending = state.isRefreshing,
            onDismiss = { showCreateJob = false },
            onCreate = { draft ->
                showCreateJob = false
                viewModel.createJob(draft)
            },
        )
    }

    if (showCreateCard) {
        CreateCardSheet(
            pending = state.isRefreshing,
            onDismiss = { showCreateCard = false },
            onCreate = { draft ->
                showCreateCard = false
                viewModel.createCard(draft)
            },
        )
    }

    if (showFilters) {
        ServiceFilterSheet(
            state = state,
            onDismiss = { showFilters = false },
            onToggleRegular = viewModel::toggleRegular,
            onToggleWarranty = viewModel::toggleWarranty,
            onWarrantyStatus = viewModel::setWarrantyStatus,
            onClientPin = viewModel::setClientPin,
        )
    }
}

@Composable
private fun ViewTabs(selected: ServiceView, onSelect: (ServiceView) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        // Górny odstęp po przełączniku dziedziny, który tu był — bez niego
        // zakładki kleją się do paska.
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    ) {
        items(ServiceView.entries.toList(), key = { it.name }) { view ->
            FilterChip(
                selected = selected == view,
                onClick = { onSelect(view) },
                label = { Text(view.label) },
            )
        }
    }
}

@Composable
private fun SourceChips(
    showRegular: Boolean,
    showWarranty: Boolean,
    onToggleRegular: () -> Unit,
    onToggleWarranty: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(selected = showRegular, onClick = onToggleRegular, label = { Text("Zwykłe") })
        FilterChip(
            selected = showWarranty,
            onClick = onToggleWarranty,
            label = { Text("Gwarancyjne") },
        )
    }
}

/** Pasek grup klientów — mobilny odpowiednik lewej kolumny „źródeł" z panelu. */
@Composable
private fun ClientChips(
    groups: List<ServiceViewModel.ClientGroup>,
    pinned: String?,
    onPin: (String?) -> Unit,
) {
    if (groups.isEmpty()) return
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(vertical = 4.dp),
    ) {
        item(key = "all") {
            FilterChip(
                selected = pinned == null,
                onClick = { onPin(null) },
                label = { Text("Wszyscy ${groups.sumOf { it.count }}") },
            )
        }
        items(groups, key = { it.id }) { group ->
            FilterChip(
                selected = pinned == group.id,
                onClick = { onPin(group.id) },
                label = {
                    Text(
                        text = "${group.label} ${group.count}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

@Composable
private fun SearchField(
    value: String,
    placeholder: String,
    onChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(placeholder) },
        singleLine = true,
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            IconButton(onClick = onClear) {
                Icon(Icons.Filled.Clear, contentDescription = "Wyczyść szukanie")
            }
        },
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun FilterAction(count: Int, onClick: () -> Unit) {
    BadgedBox(
        badge = { if (count > 0) Badge { Text("$count") } },
    ) {
        IconButton(onClick = onClick) {
            Icon(Icons.Filled.FilterList, contentDescription = "Filtry")
        }
    }
}

@Composable
fun Notice(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** Lista dziedziny: scalone wiersze Przeglądu albo same awarie Serwisu. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ServiceList(
    state: ServiceViewModel.UiState,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onOpenJob: (String) -> Unit,
    onOpenCard: (String) -> Unit,
    onToggleDone: (com.ekotak.teamtalk.domain.model.ServiceJob) -> Unit,
    onTogglePriority: (com.ekotak.teamtalk.domain.model.ServiceJob) -> Unit,
) {
    if (state.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
    ) {
        if (state.rows.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = state.domain.emptyLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@PullToRefreshBox
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.rows, key = { rowKey(it) }) { row ->
                when (row) {
                    is ServiceRow.Job -> {
                        val job = row.job
                        val client = job.clientId?.let { state.clients[it] }
                        ServiceJobRow(
                            job = job,
                            label = jobRowLabel(job, client?.label, client?.city),
                            technician = state.technicians.firstOrNull { it.id == job.technicianId },
                            meta = rowMeta(job, state.now),
                            isMine = state.currentUserId != null &&
                                job.technicianId == state.currentUserId,
                            pending = job.id in state.pendingIds,
                            queued = job.pendingSync,
                            onOpen = { onOpenJob(job.id) },
                            onToggleDone = { onToggleDone(job) },
                            onTogglePriority = { onTogglePriority(job) },
                        )
                    }
                    is ServiceRow.Warranty -> WarrantyRow(
                        view = row.view,
                        onOpen = { onOpenCard(row.view.card.id) },
                    )
                }
            }
        }
    }
}

private fun rowKey(row: ServiceRow): String = when (row) {
    is ServiceRow.Job -> "job:${row.job.id}"
    is ServiceRow.Warranty -> "card:${row.view.card.id}"
}
