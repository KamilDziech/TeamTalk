package com.ekotak.teamtalk.presentation.projects

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ekotak.teamtalk.domain.model.ProjectMember
import com.ekotak.teamtalk.domain.model.ProjectMilestone
import com.ekotak.teamtalk.domain.model.ProjectTask
import com.ekotak.teamtalk.presentation.components.AppTopBar

/**
 * Karta projektu na telefonie. Trzy zakładki: kamienie milowe (zamiast Gantta —
 * oś pozioma nie czyta się kciukiem), moje zadania i zespół. Uzasadnienie jest
 * zwinięte pod „Po co to robimy", bo technik czyta je raz.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: ProjectDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = state.detail?.project?.name ?: "Projekt",
                onNavigateBack = onNavigateBack,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            val detail = state.detail
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }

                detail == null -> Text(
                    "Nie mam tego projektu w pamięci telefonu. Wróć w zasięgu, żeby go pobrać.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(24.dp),
                )

                else -> PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = viewModel::refresh,
                ) {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item { OfflineStripPublic(state.offline, state.pendingCount) }

                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = state.tab == ProjectTab.MILESTONES,
                                    onClick = { viewModel.setTab(ProjectTab.MILESTONES) },
                                    label = { Text("Kamienie") },
                                )
                                FilterChip(
                                    selected = state.tab == ProjectTab.MY_TASKS,
                                    onClick = { viewModel.setTab(ProjectTab.MY_TASKS) },
                                    label = { Text("Moje (${state.myTasks.count { !it.done }})") },
                                )
                                FilterChip(
                                    selected = state.tab == ProjectTab.TEAM,
                                    onClick = { viewModel.setTab(ProjectTab.TEAM) },
                                    label = { Text("Zespół") },
                                )
                            }
                        }

                        when (state.tab) {
                            ProjectTab.MILESTONES -> {
                                if (detail.milestones.isEmpty()) {
                                    item {
                                        EmptyNote(
                                            "Projekt nie ma kamieni milowych. Postęp liczy się wtedy " +
                                                "liczbą zadań, a to nie mówi, czy jest na czas.",
                                        )
                                    }
                                }
                                items(detail.milestones, key = { it.id }) { MilestoneRow(it) }
                            }

                            ProjectTab.MY_TASKS -> {
                                if (state.myTasks.isEmpty()) {
                                    item { EmptyNote("Nie masz w tym projekcie żadnego zadania.") }
                                }
                                items(state.myTasks, key = { it.id }) { task ->
                                    TaskRow(task = task, onClose = { viewModel.askHours(task) })
                                }
                            }

                            ProjectTab.TEAM -> {
                                if (detail.members.isEmpty()) {
                                    item { EmptyNote("Zespół nie jest jeszcze uzupełniony.") }
                                }
                                items(detail.members, key = { it.userId }) { MemberRow(it) }
                            }
                        }

                        item { PurposeCard(detail.project.problemStatement, detail.project.metricName, detail.project.metricBaseline, detail.project.metricTarget) }
                    }
                }
            }
        }
    }

    state.closing?.let { task ->
        CloseTaskSheet(
            task = task,
            onDismiss = viewModel::cancelClose,
            onConfirm = { minutes -> viewModel.closeTask(task, minutes) },
        )
    }
}

/** Ten sam pasek co na liście — publiczny, żeby nie dublować logiki. */
@Composable
fun OfflineStripPublic(offline: Boolean, pending: Int) {
    if (!offline && pending == 0) return
    val text = when {
        pending > 0 && offline -> "Brak sieci · $pending w kolejce"
        pending > 0 -> "$pending zmian czeka na wysłanie"
        else -> "Brak sieci — dane z ostatniej synchronizacji"
    }
    Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun EmptyNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 12.dp),
    )
}

@Composable
private fun MilestoneRow(milestone: ProjectMilestone) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            // Romb — ten sam znak co na osi czasu w panelu, żeby oba widoki
            // mówiły tym samym językiem.
            Text(
                "◆",
                color = if (milestone.done) Color(0xFF2A7A1C) else Color(0xFFD6A72C),
                style = MaterialTheme.typography.titleMedium,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    milestone.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    buildString {
                        append(if (milestone.done) "odebrany ${shortDate(milestone.doneAt)}" else "do ${shortDate(milestone.dueAt)}")
                        if (milestone.taskCount > 0) {
                            append(" · ${milestone.doneCount}/${milestone.taskCount} zadań")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (milestone.taskCount > 0) {
                    LinearProgressIndicator(
                        progress = { milestone.doneCount.toFloat() / milestone.taskCount },
                        modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                    )
                }
                milestone.acceptanceCriteria?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskRow(task: ProjectTask, onClose: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    textDecoration = if (task.done) TextDecoration.LineThrough else null,
                )
                Text(
                    buildString {
                        append(if (task.dueAt != null) "do ${shortDate(task.dueAt)}" else "bez terminu")
                        task.estimatedMinutes?.let { append(" · ${it / 60} h") }
                        if (task.planned) append(" · planowane")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!task.done) {
                TextButton(onClick = onClose) { Text("Zrobione") }
            } else {
                Text(
                    task.actualMinutes?.let { "${it / 60} h" } ?: "✓",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun MemberRow(member: ProjectMember) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(member.displayName, style = MaterialTheme.typography.bodyMedium)
            member.email?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            member.roleLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** „Po co to robimy" — zwinięte, bo czyta się je raz, ale musi być pod ręką. */
@Composable
private fun PurposeCard(problem: String?, metric: String?, baseline: String?, target: String?) {
    if (problem == null && metric == null) return
    var expanded by remember { mutableStateOf(false) }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (expanded) "▾ Po co to robimy" else "▸ Po co to robimy",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (expanded) {
                problem?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                if (metric != null) {
                    HorizontalDivider()
                    Text(
                        "$metric: ${baseline ?: "?"} → ${target ?: "?"}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

/** „2026-11-30T00:00:00Z" → „30.11". Na telefonie rok rzadko coś wnosi. */
internal fun shortDate(iso: String?): String {
    if (iso == null || iso.length < 10) return "—"
    return "${iso.substring(8, 10)}.${iso.substring(5, 7)}"
}
