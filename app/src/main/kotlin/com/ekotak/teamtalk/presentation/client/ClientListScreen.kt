package com.ekotak.teamtalk.presentation.client

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ekotak.teamtalk.domain.model.ClientCategory
import com.ekotak.teamtalk.domain.model.ClientListEntry
import com.ekotak.teamtalk.domain.model.DealStage
import com.ekotak.teamtalk.presentation.components.AppTopBar
import com.ekotak.teamtalk.presentation.crm.stageColor

/**
 * Kartoteka klientów. Panel ma na to szeroką tabelę z dwoma selectami i
 * przełącznikiem kategorii; na telefonie te same wymiary rozkładamy inaczej:
 * kategoria zostaje rzędem chipów z licznikami (cztery pozycje nie zmieściłyby
 * się w segmentach), a filtry etapu i instalacji chowamy w arkuszach — chip
 * pokazuje wybór, więc
 * lista nie traci wysokości na kontrolki, których zwykle się nie rusza.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientListScreen(
    onNavigateToDetail: (String) -> Unit,
    onNavigateToNew: (ClientCategory) -> Unit,
    onNavigateToMerge: () -> Unit,
    // Ekran jest osiągalny z pulpitu (kafelek „Klienci") — wtedy potrzebna jest
    // strzałka powrotu; jako zakładka dolnego paska wchodzi bez niej.
    onNavigateBack: (() -> Unit)? = null,
    /** Komunikat z ekranu potomnego (dodano / zapisano / scalono). */
    message: String? = null,
    onMessageShown: () -> Unit = {},
    viewModel: ClientListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var openSheet by rememberSaveable { mutableStateOf<FilterSheet?>(null) }

    // Błąd pokazujemy w snackbarze tylko wtedy, gdy pod spodem jest lista;
    // przy pustej lepiej działa pełnoekranowy stan błędu.
    LaunchedEffect(state.error) {
        val error = state.error
        if (error != null && state.entries.isNotEmpty()) {
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }
    // Po powrocie z formularza / scalania: pokaż wynik i odśwież dane lejka
    // (klienci wracają sami ze strumienia Room, etapy i instalacje nie).
    LaunchedEffect(message) {
        message?.let {
            viewModel.showMessage(it)
            viewModel.refresh()
            onMessageShown()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(title = "Klienci", onNavigateBack = onNavigateBack) {
                IconButton(onClick = onNavigateToMerge) {
                    BadgedBox(
                        badge = {
                            if (state.duplicateGroups.isNotEmpty()) {
                                Badge { Text("${state.duplicateGroups.size}") }
                            }
                        },
                    ) {
                        Icon(Icons.Default.Merge, contentDescription = "Scal duplikaty")
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (state.canManage) {
                ExtendedFloatingActionButton(
                    onClick = { onNavigateToNew(state.category) },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Nowy ${state.category.oneLabel}") },
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Szukaj: imię, telefon, e-mail…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChange("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Wyczyść")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                singleLine = true,
            )

            CategorySwitch(
                selected = state.category,
                counts = state.categoryCounts,
                onSelect = viewModel::onCategoryChange,
            )

            FilterRow(
                stageFilter = state.stageFilter,
                installFilter = state.installFilter,
                filtersActive = state.filtersActive,
                onOpenSheet = { openSheet = it },
                onClear = viewModel::clearFilters,
            )

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                // Lista zostaje LazyColumn także w stanie pustym i błędu — inaczej
                // gest „pociągnij, by odświeżyć" nie miałby czego przewijać.
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = 88.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (state.entries.isEmpty()) {
                            item(key = "empty") {
                                // Pusto po filtrach a pusto w ogóle to dwa różne
                                // komunikaty — inaczej użytkownik szuka awarii
                                // tam, gdzie sam sobie wszystko odfiltrował.
                                EmptyState(
                                    modifier = Modifier.fillParentMaxSize(),
                                    title = when {
                                        state.error != null -> "Nie udało się wczytać kartoteki"
                                        state.searchQuery.isNotBlank() ->
                                            "Brak wyników dla \"${state.searchQuery}\""
                                        state.filtersActive ->
                                            "Brak: ${state.category.tabLabel.lowercase()} przy wybranych filtrach"
                                        else -> "Brak: ${state.category.tabLabel.lowercase()}"
                                    },
                                    subtitle = state.error ?: "Pociągnij w dół, aby odświeżyć.",
                                )
                            }
                        }
                        items(state.entries, key = { it.client.id }) { entry ->
                            ClientCard(
                                entry = entry,
                                callCount = state.callCounts[entry.client.id] ?: 0,
                                onClick = { onNavigateToDetail(entry.client.id) },
                                onCall = { phone -> viewModel.call(phone) },
                            )
                        }
                    }
                }
            }
        }
    }

    when (openSheet) {
        FilterSheet.STAGE -> StageFilterSheet(
            selected = state.stageFilter,
            counts = state.stageCounts,
            onSelect = {
                viewModel.onStageFilterChange(it)
                openSheet = null
            },
            onDismiss = { openSheet = null },
        )
        FilterSheet.INSTALL -> InstallFilterSheet(
            selected = state.installFilter,
            options = state.installOptions,
            onSelect = {
                viewModel.onInstallFilterChange(it)
                openSheet = null
            },
            onDismiss = { openSheet = null },
        )
        null -> Unit
    }
}

/** Który arkusz filtra jest otwarty (żaden = `null`). */
enum class FilterSheet { STAGE, INSTALL }

/**
 * Zakładki kategorii z licznikami — te same cztery co w panelu. Segmenty dzielą
 * szerokość po równo, więc przy czterech pozycjach „Kontrahenci" obcinałoby się
 * do wielokropka; kategoria jedzie przewijanym rzędem chipów, jak filtry niżej.
 */
@Composable
private fun CategorySwitch(
    selected: ClientCategory,
    counts: Map<ClientCategory, Int>,
    onSelect: (ClientCategory) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        items(ClientCategory.entries.toList(), key = { it.name }) { category ->
            FilterChip(
                selected = selected == category,
                onClick = { onSelect(category) },
                label = {
                    Text(
                        text = "${category.tabLabel} ${counts[category] ?: 0}",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

@Composable
private fun FilterRow(
    stageFilter: DealStage?,
    installFilter: String?,
    filtersActive: Boolean,
    onOpenSheet: (FilterSheet) -> Unit,
    onClear: () -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    ) {
        item(key = "stage") {
            FilterChip(
                selected = stageFilter != null,
                onClick = { onOpenSheet(FilterSheet.STAGE) },
                label = { Text(stageFilter?.let(::mainStageLabel) ?: "Etap") },
            )
        }
        item(key = "install") {
            FilterChip(
                selected = installFilter != null,
                onClick = { onOpenSheet(FilterSheet.INSTALL) },
                label = { Text(installFilter ?: "Instalacja") },
            )
        }
        if (filtersActive) {
            item(key = "clear") {
                FilterChip(selected = false, onClick = onClear, label = { Text("Wyczyść") })
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StageFilterSheet(
    selected: DealStage?,
    counts: Map<DealStage, Int>,
    onSelect: (DealStage?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            SheetTitle(title = "Główny etap", subtitle = "Etap najświeższej szansy klienta")
            SheetRow(
                label = "Wszystkie etapy",
                selected = selected == null,
                onClick = { onSelect(null) },
            )
            DIRECTORY_STAGES.forEach { stage ->
                SheetRow(
                    label = mainStageLabel(stage),
                    count = counts[stage],
                    dotColor = stageColor(stage),
                    selected = selected == stage,
                    onClick = { onSelect(stage) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstallFilterSheet(
    selected: String?,
    options: List<String>,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            SheetTitle(title = "Instalacja", subtitle = "Z deali klienta (suma wszystkich szans)")
            SheetRow(
                label = "Wszystkie instalacje",
                selected = selected == null,
                onClick = { onSelect(null) },
            )
            if (options.isEmpty()) {
                Text(
                    text = "Brak instalacji w lejku.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            options.forEach { name ->
                SheetRow(
                    label = name,
                    badge = name,
                    selected = selected == name,
                    onClick = { onSelect(name) },
                )
            }
        }
    }
}

@Composable
private fun SheetTitle(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SheetRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    count: Int? = null,
    dotColor: Color? = null,
    badge: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dotColor != null) {
            Box(modifier = Modifier.size(8.dp).background(dotColor, RoundedCornerShape(4.dp)))
            Spacer(Modifier.width(10.dp))
        }
        if (badge != null) {
            InstallBadgeChip(name = badge)
            Spacer(Modifier.width(10.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f),
        )
        count?.let {
            Text(
                text = "$it",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ClientCard(
    entry: ClientListEntry,
    callCount: Int,
    onClick: () -> Unit,
    onCall: (String) -> Unit,
) {
    val client = entry.client
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = client.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val secondLine = listOfNotNull(client.primaryPhone, client.place)
                    .joinToString(" · ")
                    .ifBlank { client.email.orEmpty() }
                if (secondLine.isNotBlank()) {
                    Text(
                        text = secondLine,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    entry.mainStage?.let { MainStageChip(it) }
                    entry.installations.forEach { InstallBadgeChip(name = it) }
                    if (entry.deals.size > 1) {
                        Text(
                            text = "${entry.deals.size} deale",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (callCount > 0) {
                        Icon(
                            Icons.Default.Phone,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            text = "$callCount",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (entry.sharedWith.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Group,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "deal wspólny: ${entry.sharedWith.joinToString(", ")}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // Dzwonienie prosto z listy — najczęstsza akcja w terenie.
            client.primaryPhone?.let { phone ->
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { onCall(phone) }) {
                    Icon(
                        Icons.Default.Phone,
                        contentDescription = "Zadzwoń do ${client.displayName}",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/** Chip głównego etapu — ten sam wzór co na kartach lejka. */
@Composable
internal fun MainStageChip(stage: DealStage) {
    val color = stageColor(stage)
    Row(
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
            .border(1.dp, color.copy(alpha = 0.32f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size(6.dp).background(color, RoundedCornerShape(3.dp)))
        Spacer(Modifier.width(5.dp))
        Text(
            text = mainStageLabel(stage),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Kwadratowy skrót instalacji („PV", „O") — 1:1 z oznaczeniami panelu. */
@Composable
internal fun InstallBadgeChip(name: String) {
    val badge = installBadge(name)
    Box(
        modifier = Modifier
            .heightIn(min = 18.dp)
            .background(badge.background, RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = badge.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = badge.foreground,
        )
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    Box(modifier = modifier.padding(horizontal = 32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
