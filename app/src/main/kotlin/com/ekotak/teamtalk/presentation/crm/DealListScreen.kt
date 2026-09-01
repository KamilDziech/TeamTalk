package com.ekotak.teamtalk.presentation.crm

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ekotak.teamtalk.domain.model.DealListItem
import com.ekotak.teamtalk.domain.model.DealStage
import com.ekotak.teamtalk.domain.model.FunnelGroup
import com.ekotak.teamtalk.presentation.components.AppTopBar
import com.ekotak.teamtalk.presentation.theme.Orange600

/**
 * Lejek sprzedaży — mobilny odpowiednik tablicy Kanban z board360. Na wąskim
 * ekranie kolumny nie mają szans, więc lejek jest listą pogrupowaną nagłówkami
 * etapów, a chipy faz (BOW / Sprzedaż / Etap montażowy / Po montażu) zawężają
 * widok do jednego kawałka lejka.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DealListScreen(
    onNavigateToDeal: (String) -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    viewModel: DealListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Błąd akcji pokazujemy w snackbarze tylko wtedy, gdy jest co pokazać pod
    // spodem; przy pustej liście lepiej działa pełnoekranowy stan błędu.
    LaunchedEffect(state.error) {
        val error = state.error
        if (error != null && state.sections.isNotEmpty()) {
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = { AppTopBar(title = "CRM", onNavigateBack = onNavigateBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Szukaj klienta, miasta, źródła...") },
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

            FilterRow(
                group = state.group,
                onlyOverdue = state.onlyOverdue,
                onlyMine = state.onlyMine,
                onGroupChange = viewModel::onGroupChange,
                onToggleOverdue = viewModel::onToggleOverdue,
                onToggleMine = viewModel::onToggleMine,
            )

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                // Lista jest LazyColumn także w stanie pustym i błędu — inaczej
                // gest „pociągnij, by odświeżyć" nie miałby czego przewijać i
                // nie dałoby się ponowić nieudanego pobrania.
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (state.sections.isEmpty()) {
                            item(key = "empty") {
                                // Pusto po filtrach czy pusto w ogóle — to dwa
                                // różne komunikaty; inaczej użytkownik szuka
                                // awarii tam, gdzie sam sobie wszystko odfiltrował.
                                EmptyState(
                                    modifier = Modifier.fillParentMaxSize(),
                                    title = when {
                                        state.error != null -> "Nie udało się wczytać lejka"
                                        state.searchQuery.isNotBlank() ->
                                            "Brak wyników dla \"${state.searchQuery}\""
                                        state.onlyOverdue || state.onlyMine || state.group != null ->
                                            "Brak dealów dla wybranych filtrów"
                                        else -> "Lejek jest pusty"
                                    },
                                    subtitle = state.error ?: "Pociągnij w dół, aby odświeżyć.",
                                )
                            }
                        }
                        state.sections.forEach { section ->
                            item(key = "header-${section.stage.wire}") {
                                StageHeader(stage = section.stage, count = section.items.size)
                            }
                            items(section.items, key = { it.deal.id }) { item ->
                                DealCard(
                                    item = item,
                                    onClick = { onNavigateToDeal(item.deal.id) },
                                    onCall = { phone -> viewModel.call(phone) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterRow(
    group: FunnelGroup?,
    onlyOverdue: Boolean,
    onlyMine: Boolean,
    onGroupChange: (FunnelGroup) -> Unit,
    onToggleOverdue: () -> Unit,
    onToggleMine: () -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 4.dp),
    ) {
        item(key = "overdue") {
            FilterChip(
                selected = onlyOverdue,
                onClick = onToggleOverdue,
                label = { Text("Zaległe") },
                leadingIcon = {
                    Icon(
                        Icons.Default.NotificationsActive,
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                    )
                },
            )
        }
        item(key = "mine") {
            FilterChip(selected = onlyMine, onClick = onToggleMine, label = { Text("Moje") })
        }
        items(FunnelGroup.entries.toList(), key = { it.name }) { entry ->
            FilterChip(
                selected = group == entry,
                onClick = { onGroupChange(entry) },
                label = { Text(entry.label) },
            )
        }
    }
}

@Composable
private fun StageHeader(stage: DealStage, count: Int) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 16.dp)
                .background(stageColor(stage), RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stage.label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DealCard(
    item: DealListItem,
    onClick: () -> Unit,
    onCall: (String) -> Unit,
) {
    val deal = item.deal
    val overdue = isOverdue(deal.nextContactAt)

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
                    text = item.clientName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                item.place?.let {
                    Text(
                        text = it,
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
                    StageChip(deal.stage)
                    stageAgeLabel(deal.stageEnteredAt)?.let { age ->
                        Icon(
                            Icons.Default.Schedule,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = age,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (overdue) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Zaległy kontakt · ${formatDate(deal.nextContactAt).orEmpty()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Orange600,
                    )
                }
            }

            // Dzwonienie prosto z lejka — najczęstsza akcja w terenie, nie ma
            // powodu zmuszać do wejścia w kartę.
            item.phone?.let { phone ->
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { onCall(phone) }) {
                    Icon(
                        Icons.Default.Phone,
                        contentDescription = "Zadzwoń do ${item.clientName}",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/** Etykieta etapu w kolorze fazy — ten sam wzór co badge modułu na pulpicie. */
@Composable
internal fun StageChip(stage: DealStage) {
    val color = stageColor(stage)
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
            .border(1.dp, color.copy(alpha = 0.32f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = stage.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun EmptyState(title: String, subtitle: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
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
