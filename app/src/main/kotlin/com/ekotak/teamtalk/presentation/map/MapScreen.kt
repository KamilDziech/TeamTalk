package com.ekotak.teamtalk.presentation.map

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ekotak.teamtalk.domain.model.MapPoint
import com.ekotak.teamtalk.presentation.components.AppTopBar
import com.ekotak.teamtalk.presentation.theme.EkotakGreen
import com.ekotak.teamtalk.presentation.theme.Orange600
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Moduł „Mapa" — mobilny odpowiednik mapy zleceń z board360
 * (`web/src/app/app/map`). Te same pięć widoków, te same chipy legendy, te same
 * filtry i ta sama lista „bez lokalizacji"; podkład OSM jak w panelu, tylko
 * rysowany osmdroidem zamiast Leafletu.
 *
 * Pasek szukania panelu rozkłada się na pole u góry i arkusz filtrów — cztery
 * pola w jednej linii nie mieszczą się na telefonie. Dołożone są trzy rzeczy,
 * których panel nie ma, bo nie ma po co: własna lokalizacja jako środek
 * promienia, nawigacja do punktu i telefon do klienta.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    onOpenDeal: (String) -> Unit,
    onOpenClient: (String) -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    viewModel: MapViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showFilters by remember { mutableStateOf(false) }

    // Zgoda na lokalizację pytana dopiero przy „moja lokalizacja" — mapa działa
    // bez niej, więc nie ma powodu pytać na wejściu.
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) viewModel.useMyLocation() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        topBar = {
            // Przełącznik piny/heatmapa siedzi przy pasku szukania, nie w belce:
            // wordmark „ekotak · Mapa zleceń" i tak zjada całą jej szerokość.
            AppTopBar(title = "Mapa zleceń", onNavigateBack = onNavigateBack)
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ViewTabs(state = state, onSelect = viewModel::selectView)

            if (state.isFleet) {
                FleetPlaceholder(modifier = Modifier.weight(1f))
                return@Column
            }

            SearchRow(
                keyword = state.keyword,
                filterCount = state.activeFilterCount,
                mode = state.mode,
                onKeyword = viewModel::setKeyword,
                onMode = viewModel::setMode,
                onFilters = { showFilters = true },
            )

            state.center?.let { center ->
                if (state.radiusKm > 0) {
                    LocationPill(
                        label = "${center.label} +${state.radiusKm} km",
                        onClear = viewModel::clearLocation,
                    )
                }
            }

            Chips(state = state, onChip = viewModel::setChip)

            // `clip` jest tu obowiązkowe: widok osmdroida rysuje kafelki poza
            // swoimi granicami, a AndroidView niczego nie przycina — bez tego
            // mapa zasłania zakładki widoków, pasek szukania i chipy.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp)),
            ) {
                OsmMapView(
                    points = state.shown,
                    mode = state.mode,
                    center = state.center,
                    radiusKm = state.radiusKm,
                    myLocation = state.myLocation,
                    fitRequest = state.fitRequest,
                    onSelect = viewModel::selectPoint,
                    modifier = Modifier.fillMaxSize(),
                )

                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MapButton(
                        icon = Icons.Default.ZoomOutMap,
                        description = "Dopasuj widok",
                        onClick = viewModel::requestFit,
                    )
                    MapButton(
                        icon = Icons.Default.MyLocation,
                        description = "Moja lokalizacja",
                        onClick = {
                            if (viewModel.hasLocationPermission()) {
                                viewModel.useMyLocation()
                            } else {
                                locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                            }
                        },
                    )
                }

                if (state.isLoading || state.isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(12.dp)
                            .size(22.dp),
                        strokeWidth = 2.dp,
                    )
                }

                state.error?.let { error ->
                    Surface(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(16.dp),
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(error, modifier = Modifier.padding(14.dp))
                    }
                }

                if (!state.isLoading && state.error == null && state.shown.isEmpty()) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(
                            "Brak pozycji w tym widoku ze zweryfikowanym adresem.",
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            NoGeoBox(points = state.noGeo, onOpen = { point ->
                point.dealId?.let(onOpenDeal) ?: point.clientId?.let(onOpenClient)
            })

            SyncedAtLabel(state.syncedAt)
        }
    }

    if (showFilters) {
        MapFilterSheet(
            state = state,
            onOwnerMode = viewModel::setOwnerMode,
            onPerson = viewModel::setPerson,
            onInstall = viewModel::setInstall,
            onLocationQuery = viewModel::onLocationQueryChange,
            onSelectPlace = viewModel::selectPlace,
            onMyLocation = {
                if (viewModel.hasLocationPermission()) {
                    viewModel.useMyLocation()
                } else {
                    locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            },
            onClearLocation = viewModel::clearLocation,
            onRadius = viewModel::setRadius,
            onClearAll = viewModel::clearFilters,
            onDismiss = { showFilters = false },
        )
    }

    state.selected?.let { point ->
        MapPointSheet(
            point = point,
            myLocation = state.myLocation,
            onOpenDeal = { id ->
                viewModel.selectPoint(null)
                onOpenDeal(id)
            },
            onOpenClient = { id ->
                viewModel.selectPoint(null)
                onOpenClient(id)
            },
            onDismiss = { viewModel.selectPoint(null) },
        )
    }
}

/** Przewijany pasek widoków z licznikami — odpowiednik zakładek panelu. */
@Composable
private fun ViewTabs(state: MapViewModel.UiState, onSelect: (MapViewTab) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(MapViewTab.entries) { tab ->
            val count = tab.kind?.let { state.counts[it] ?: 0 }
            FilterChip(
                selected = state.view == tab,
                onClick = { onSelect(tab) },
                label = {
                    Text(
                        if (count == null) tab.label else "${tab.label} $count",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchRow(
    keyword: String,
    filterCount: Int,
    mode: MapMode,
    onKeyword: (String) -> Unit,
    onMode: (MapMode) -> Unit,
    onFilters: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = keyword,
            onValueChange = onKeyword,
            modifier = Modifier.weight(1f),
            singleLine = true,
            // Pełna podpowiedź panelu („Klient, instalacja, miasto…") zawija się
            // w dwie linie i rozpycha pole — na telefonie zostaje sam początek.
            placeholder = { Text("Klient, miasto…", maxLines = 1) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (keyword.isNotEmpty()) {
                    IconButton(onClick = { onKeyword("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Wyczyść")
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )
        IconButton(
            onClick = { onMode(if (mode == MapMode.PINS) MapMode.HEAT else MapMode.PINS) },
        ) {
            Icon(
                if (mode == MapMode.PINS) Icons.Default.Whatshot else Icons.Default.Place,
                contentDescription = if (mode == MapMode.PINS) "Pokaż heatmapę" else "Pokaż piny",
            )
        }
        BadgedBox(
            badge = { if (filterCount > 0) Badge { Text(filterCount.toString()) } },
        ) {
            IconButton(onClick = onFilters) {
                Icon(Icons.Default.FilterList, contentDescription = "Filtry")
            }
        }
    }
}

@Composable
private fun LocationPill(label: String, onClear: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = CircleShape,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            IconButton(onClick = onClear, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Default.Clear,
                    contentDescription = "Wyczyść lokalizację",
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** Chipy statusów = legenda i filtr naraz, w kolejności i kolorach z panelu. */
@Composable
private fun Chips(state: MapViewModel.UiState, onChip: (String?) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        item {
            FilterChip(
                selected = state.chip == null,
                onClick = { onChip(null) },
                label = { Text("Wszystkie (${state.baseCount})") },
            )
        }
        items(state.chips) { chip ->
            val color = Color(chip.colorArgb)
            FilterChip(
                selected = state.chip == chip.key,
                onClick = { onChip(if (state.chip == chip.key) null else chip.key) },
                label = { Text("${chip.label} (${chip.count})") },
                leadingIcon = {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .background(color, CircleShape),
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = color,
                    selectedLabelColor = Color(0xFF0B0B0B),
                    selectedLeadingIconColor = Color(0xFF0B0B0B),
                ),
            )
        }
    }
}

/** Pozycje bez zweryfikowanego adresu — jak rozwijany pasek w panelu. */
@Composable
private fun NoGeoBox(points: List<MapPoint>, onOpen: (MapPoint) -> Unit) {
    if (points.isEmpty()) return
    var open by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(12.dp),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { open = !open }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${if (open) "▾" else "▸"} Bez lokalizacji: ${points.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = Orange600,
                )
                Text(
                    " — adres niezweryfikowany",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (open) {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 180.dp),
                    contentPadding = PaddingValues(bottom = 8.dp),
                ) {
                    items(points) { point ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpen(point) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                point.name,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                point.city ?: "—",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "Zwaliduj →",
                                style = MaterialTheme.typography.labelSmall,
                                color = EkotakGreen,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Kiedy pobrano migawkę — jedyna informacja, która ma sens bez zasięgu. */
@Composable
private fun SyncedAtLabel(syncedAt: Long?) {
    if (syncedAt == null) return
    val stamp = remember(syncedAt) {
        SimpleDateFormat("HH:mm", Locale("pl")).format(Date(syncedAt))
    }
    Text(
        "Dane z $stamp",
        modifier = Modifier.padding(bottom = 6.dp),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun MapButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        shape = RoundedCornerShape(10.dp),
        shadowElevation = 2.dp,
    ) {
        IconButton(onClick = onClick, modifier = Modifier.size(40.dp)) {
            Icon(icon, contentDescription = description, modifier = Modifier.size(20.dp))
        }
    }
}

/** Flota: panel obiecuje GPS pojazdów „wkrótce" — mobilnie tak samo. */
@Composable
private fun FleetPlaceholder(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(24.dp),
        ) {
            Text("🚚", style = MaterialTheme.typography.displaySmall)
            Text(
                "Lokalizacja pojazdów wkrótce",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Śledzenie floty pojawi się po podłączeniu GPS. Dane pojazdów prowadzisz " +
                    "w module Zasoby w panelu board360.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
