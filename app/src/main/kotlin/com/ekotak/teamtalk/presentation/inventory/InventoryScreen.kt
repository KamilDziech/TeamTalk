package com.ekotak.teamtalk.presentation.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ekotak.teamtalk.domain.model.InstallationType
import com.ekotak.teamtalk.domain.model.Product
import com.ekotak.teamtalk.domain.model.StockLevel
import com.ekotak.teamtalk.domain.model.StockType
import com.ekotak.teamtalk.presentation.components.AppTopBar

/**
 * Zakładka „Stan" modułu Magazyn — pierwszy ekran mobilnego magazynu (etap E1).
 *
 * Układ wprost z makiety `design/mockups/modul-magazyn.html`: przełącznik dwóch
 * światów towaru (Magazyn / Pod klienta), wyszukiwarka, szyna chipów poziomu
 * stanu i lista ułożona pod człowieka idącego po hali — <b>pasek stanu, półka
 * i licznik</b>, a cena i dystrybutor dopiero w karcie.
 *
 * Zdjęć pozycji na razie nie pokazujemy: `imageUrl` z panelu to adres zewnętrzny,
 * a wczytywanie obrazów z sieci wymagałoby nowej zależności (Coil), której
 * w projekcie nie ma. Zamiast tego kolorowa zaślepka z inicjałem — jak
 * `ProductThumb` w panelu przy pozycji bez zdjęcia.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    onNavigateBack: () -> Unit,
    onOpenProduct: (String) -> Unit,
    viewModel: InventoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showSearch by remember { mutableStateOf(false) }
    var showFilters by remember { mutableStateOf(false) }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Magazyn",
                onNavigateBack = onNavigateBack,
                actions = {
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Default.Search, contentDescription = "Szukaj")
                    }
                    BadgedBox(
                        badge = {
                            if (state.activeFilterCount > 0) {
                                Badge { Text(state.activeFilterCount.toString()) }
                            }
                        },
                    ) {
                        IconButton(onClick = { showFilters = true }) {
                            Icon(Icons.Default.FilterList, contentDescription = "Filtry")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            WorldSwitch(
                selected = state.stockType,
                onSelect = viewModel::setStockType,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            if (showSearch) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    singleLine = true,
                    placeholder = { Text("nazwa, kod, producent, półka…") },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Wyczyść")
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            LevelChips(
                counts = state.levelCounts,
                selected = state.level,
                onSelect = viewModel::setLevel,
            )

            SyncLine(state)

            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    state.isLoading && state.rows.isEmpty() -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }

                    state.rows.isEmpty() -> EmptyState(state)

                    else -> LazyColumn(
                        contentPadding = PaddingValues(bottom = 24.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(state.rows, key = { it.id }) { product ->
                            ProductRow(product = product, onClick = { onOpenProduct(product.id) })
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showFilters) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { showFilters = false }, sheetState = sheetState) {
            FilterSheet(
                state = state,
                onInstallation = viewModel::setInstallation,
                onZone = viewModel::setZone,
                onClear = viewModel::clearFilters,
            )
        }
    }
}

/** Przełącznik dwóch światów towaru — `stockType` z panelu, nie filtr. */
@Composable
private fun WorldSwitch(
    selected: StockType,
    onSelect: (StockType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = selected == StockType.STOCK,
            onClick = { onSelect(StockType.STOCK) },
            label = { Text("Magazyn") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = inventoryAccent().copy(alpha = 0.22f),
                selectedLabelColor = inventoryAccent(),
            ),
        )
        FilterChip(
            selected = selected == StockType.CLIENT,
            onClick = { onSelect(StockType.CLIENT) },
            label = { Text("Pod klienta") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = reservedColor().copy(alpha = 0.22f),
                selectedLabelColor = reservedColor(),
            ),
        )
    }
}

/** Szyna poziomów stanu z licznikami — odpowiednik `LevelFilterBar` z panelu. */
@Composable
private fun LevelChips(
    counts: Map<StockLevel, Int>,
    selected: StockLevel?,
    onSelect: (StockLevel?) -> Unit,
) {
    val present = StockLevel.entries.filter { (counts[it] ?: 0) > 0 }
    if (present.isEmpty()) return
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
    ) {
        items(present, key = { it.name }) { level ->
            FilterChip(
                selected = selected == level,
                onClick = { onSelect(level) },
                leadingIcon = {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(levelColor(level), RoundedCornerShape(5.dp)),
                    )
                },
                label = { Text("${level.label} ${counts[level] ?: 0}") },
            )
        }
    }
}

/** Podpis wieku danych — bez zasięgu to jedyna informacja, na czym pracujemy. */
@Composable
private fun SyncLine(state: InventoryViewModel.UiState) {
    val parts = buildList {
        syncedLabel(state.syncedAt)?.let { add(it) }
        if (!state.reservationsAvailable) add("bez rezerwacji")
        if (!state.ordersAvailable) add("bez zamówień")
    }
    if (parts.isEmpty()) return
    Text(
        text = "${state.rows.size} z ${state.totalInWorld} · ${parts.joinToString(" · ")}",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
    )
}

/**
 * Wiersz pozycji. Kolejność informacji jest celowa: nazwa (co to), pasek stanu
 * (czy starczy), półka (gdzie to leży), licznik (ile). Cena i dystrybutor
 * schodzą do karty — przy regale nikt ich nie czyta.
 */
@Composable
private fun ProductRow(product: Product, onClick: () -> Unit) {
    val color = levelColor(product.level)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Thumb(product)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = product.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            StockBar(product)
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = product.locationLabel ?: "bez lokalizacji",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (product.locationLabel == null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (product.reserved > 0) {
                    Text(
                        text = "zarez. ${formatQty(product.reserved)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = reservedColor(),
                        maxLines = 1,
                    )
                }
                if (product.inTransit > 0) {
                    Text(
                        text = "w drodze ${formatQty(product.inTransit)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = transitColor(),
                        maxLines = 1,
                    )
                }
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatQty(product.stock),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (product.level == StockLevel.OK) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    color
                },
            )
            Text(
                text = when {
                    product.isNoGoal -> "pod klienta"
                    product.min > 0 && product.stock < product.min -> "min ${formatQty(product.min)}"
                    product.target > 0 -> "z ${formatQty(product.target)}"
                    else -> "bez progów"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Zaślepka zdjęcia z inicjałem — kolor bierze się z poziomu stanu. */
@Composable
private fun Thumb(product: Product) {
    val color = levelColor(product.level)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(38.dp)
            .background(color.copy(alpha = 0.18f), RoundedCornerShape(9.dp)),
    ) {
        Text(
            text = product.name.trim().take(1).uppercase(),
            style = MaterialTheme.typography.titleSmall,
            color = color,
        )
    }
}

/** Pasek stanu — wypełnienie względem celu, kolor wg poziomu (jak w panelu). */
@Composable
private fun StockBar(product: Product) {
    val fraction = barFraction(product.stock, product.target, product.level)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(2.dp),
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction)
                .background(levelColor(product.level), RoundedCornerShape(2.dp)),
        )
    }
}

@Composable
private fun EmptyState(state: InventoryViewModel.UiState) {
    val text = when {
        state.totalInWorld == 0 && state.stockType == StockType.CLIENT ->
            "Nie ma pozycji zamawianych pod klienta"
        state.totalInWorld == 0 -> "Magazyn jest pusty albo nie udało się pobrać kartoteki"
        state.query.isNotEmpty() -> "Nic nie pasuje do „${state.query}\""
        else -> "Żadna pozycja nie przechodzi przez ustawione filtry"
    }
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Arkusz filtrów: typ instalacji (dwupoziomowo, jak w panelu) i obiekt magazynu. */
@Composable
private fun FilterSheet(
    state: InventoryViewModel.UiState,
    onInstallation: (String?) -> Unit,
    onZone: (String?) -> Unit,
    onClear: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Filtry",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            if (state.activeFilterCount > 0) {
                TextButton(onClick = onClear) { Text("Wyczyść") }
            }
        }

        SheetLabel("Typ instalacji")
        ChipFlow(
            options = state.installations.map { it.api to it.label },
            selected = state.installation,
            onSelect = onInstallation,
            indent = { key -> InstallationType.from(key)?.parent != null },
        )

        if (state.zones.isNotEmpty()) {
            SheetLabel("Obiekt magazynu")
            ChipFlow(
                options = state.zones.map { it to it },
                selected = state.zone,
                onSelect = onZone,
            )
        }
    }
}

@Composable
private fun SheetLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontSize = 10.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
}

/**
 * Chipy w wierszach po dwa — `FlowRow` jest wciąż eksperymentalny, a lista opcji
 * jest krótka i znana z góry, więc łamiemy ją sami.
 */
@Composable
private fun ChipFlow(
    options: List<Pair<String, String>>,
    selected: String?,
    onSelect: (String?) -> Unit,
    indent: (String) -> Boolean = { false },
) {
    options.chunked(2).forEach { pair ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            pair.forEach { (key, label) ->
                if (indent(key)) Spacer(Modifier.width(12.dp))
                FilterChip(
                    selected = selected == key,
                    onClick = { onSelect(key) },
                    label = {
                        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
            if (pair.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}
