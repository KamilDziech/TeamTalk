package com.ekotak.teamtalk.presentation.task

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ekotak.teamtalk.presentation.components.AppTopBar
import com.ekotak.teamtalk.presentation.components.PersonScope
import com.ekotak.teamtalk.presentation.theme.EkotakGreen

/**
 * Moduł „Zadania" — lista zespołowa z board360 na telefonie. Panel jest tablicą
 * do planowania, telefon ma być listą do odhaczania, więc z tablicy zostaje
 * filtr roli i osoby, sekcje jako nagłówki oraz jedno dotknięcie zamykające
 * zadanie. Reszta filtrów siedzi w arkuszu, bo pasek panelu nie mieści się na
 * szerokość telefonu.
 *
 * Kartę zadania (podgląd i edycja pól) dokłada etap E2 — na razie z wiersza
 * działa odhaczenie i priorytet, a nowe zadanie zakłada istniejący kreator.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskListScreen(
    onCreateTask: () -> Unit,
    onOpenTask: (String) -> Unit = {},
    onNavigateBack: (() -> Unit)? = null,
    viewModel: TaskListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showFilters by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        val message = state.message
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }

    // Błąd pobrania przy niepustej liście to sprawa na snackbar — pod spodem
    // wciąż widać ostatni znany stan z cache.
    LaunchedEffect(state.error) {
        val error = state.error
        if (error != null && state.sections.isNotEmpty()) {
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Zadania",
                onNavigateBack = onNavigateBack,
                actions = {
                    IconButton(onClick = { showFilters = true }) {
                        Box {
                            Icon(Icons.Default.FilterList, contentDescription = "Filtruj i sortuj")
                            if (state.activeFilterCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .size(8.dp)
                                        .background(EkotakGreen, CircleShape),
                                )
                            }
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateTask) {
                Icon(Icons.Default.Add, contentDescription = "Nowe zadanie")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::onSearchQueryChange,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Szukaj: zadanie, opis, osoba, projekt…") },
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

            RoleSwitch(selected = state.role, onSelect = viewModel::onRoleChange)

            QuickFilters(
                state = state,
                onToggleMine = viewModel::onToggleMine,
                onToggleOpenOnly = viewModel::onToggleOpenOnly,
                onToggleOverdue = viewModel::onToggleOverdue,
            )

            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp, end = 16.dp, top = 4.dp, bottom = 88.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (state.sections.isEmpty()) {
                            item(key = "empty") {
                                EmptyTasks(
                                    modifier = Modifier.fillParentMaxSize(),
                                    state = state,
                                )
                            }
                        }
                        state.sections.forEach { section ->
                            if (section.label.isNotEmpty()) {
                                item(key = "header-${section.section?.wire ?: "none"}") {
                                    TaskSectionHeader(label = section.label, count = section.items.size)
                                }
                            }
                            items(section.items, key = { it.id }) { task ->
                                TaskRow(
                                    task = task,
                                    assignee = task.assigneeId?.let { state.membersById[it] },
                                    pending = task.id in state.pendingIds,
                                    queued = task.id in state.queuedIds,
                                    onToggleDone = { viewModel.onToggleDone(task) },
                                    onTogglePriority = { viewModel.onTogglePriority(task) },
                                    onOpen = { onOpenTask(task.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFilters) {
        TaskFilterSheet(
            state = state,
            onDismiss = { showFilters = false },
            onPersonChange = viewModel::onPersonChange,
            onToggleStatus = viewModel::onToggleStatus,
            onStatusesChange = viewModel::onStatusesChange,
            onPriorityChange = viewModel::onPriorityChange,
            onDueChange = viewModel::onDueChange,
            onSourceChange = viewModel::onSourceChange,
            onSortChange = viewModel::onSortChange,
            onGroupBySectionChange = viewModel::onGroupBySectionChange,
            onClear = viewModel::clearFilters,
        )
    }
}

/** Filtr roli: czyj filtr osoby stosujemy — wykonawcy czy zlecającego. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoleSwitch(selected: RoleScope, onSelect: (RoleScope) -> Unit) {
    val roles = RoleScope.entries
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    ) {
        roles.forEachIndexed { index, role ->
            SegmentedButton(
                selected = selected == role,
                onClick = { onSelect(role) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = roles.size),
                label = {
                    Text(
                        text = role.label,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

/** Skróty do trzech najczęstszych zawężeń plus licznik po filtrach. */
@Composable
private fun QuickFilters(
    state: TaskListViewModel.UiState,
    onToggleMine: () -> Unit,
    onToggleOpenOnly: () -> Unit,
    onToggleOverdue: () -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    ) {
        item(key = "mine") {
            FilterChip(
                selected = state.person == PersonScope.Mine,
                onClick = onToggleMine,
                label = { Text("Moje") },
            )
        }
        item(key = "open") {
            FilterChip(
                selected = state.statuses == TaskListViewModel.OPEN_STATUSES,
                onClick = onToggleOpenOnly,
                label = { Text("Otwarte") },
            )
        }
        item(key = "overdue") {
            FilterChip(
                selected = state.due == DueScope.OVERDUE,
                onClick = onToggleOverdue,
                label = { Text("Zaległe") },
            )
        }
        item(key = "count") {
            Text(
                text = "${state.visibleCount} z ${state.totalCount}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 10.dp),
            )
        }
    }
}

/**
 * Pusto po filtrach i pusto w ogóle to dwa różne komunikaty — inaczej człowiek
 * szuka awarii tam, gdzie sam sobie wszystko odfiltrował.
 */
@Composable
private fun EmptyTasks(modifier: Modifier, state: TaskListViewModel.UiState) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.Assignment,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = when {
                state.error != null -> "Nie udało się wczytać zadań"
                state.searchQuery.isNotBlank() -> "Brak wyników dla \"${state.searchQuery}\""
                state.totalCount > 0 -> "Brak zadań dla wybranych filtrów"
                else -> "Nie masz żadnych zadań"
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = state.error ?: "Pociągnij w dół, aby odświeżyć.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
