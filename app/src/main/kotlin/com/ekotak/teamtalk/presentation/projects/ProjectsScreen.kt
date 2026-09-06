package com.ekotak.teamtalk.presentation.projects

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ekotak.teamtalk.domain.model.Project
import com.ekotak.teamtalk.domain.model.ProjectStage
import com.ekotak.teamtalk.presentation.components.AppTopBar

/**
 * Lista projektów — mobilny odpowiednik kafli z panelu, ale w jednej kolumnie
 * i pogrupowana po etapie. Technik ma zobaczyć: gdzie projekt stoi, ile zostało
 * i kiedy najbliższy termin. Kwot tu nie ma — API ich nie wysyła bez
 * `projects.finance`, a ekran ogląda się przy kliencie.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsScreen(
    onNavigateBack: () -> Unit,
    onOpenProject: (String) -> Unit,
    viewModel: ProjectsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showIdea by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = { AppTopBar(title = "Projekty", onNavigateBack = onNavigateBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showIdea = true }) {
                Icon(Icons.Default.Add, contentDescription = "Zgłoś pomysł")
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }

                else -> PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = viewModel::refresh,
                ) {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item {
                            OfflineStrip(offline = state.offline, pending = state.pendingCount)
                        }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = state.filter == ProjectsFilter.MINE,
                                    onClick = { viewModel.setFilter(ProjectsFilter.MINE) },
                                    label = { Text("Moje") },
                                )
                                FilterChip(
                                    selected = state.filter == ProjectsFilter.ALL,
                                    onClick = { viewModel.setFilter(ProjectsFilter.ALL) },
                                    label = { Text("Wszystkie") },
                                )
                            }
                        }

                        if (state.groups.isEmpty()) {
                            item {
                                Text(
                                    "Nie ma jeszcze żadnego projektu. Pomysł zgłosisz plusem w prawym dolnym rogu.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 24.dp),
                                )
                            }
                        }

                        state.groups.forEach { group ->
                            item(key = "head-${group.stage.name}") {
                                Text(
                                    "${group.stage.label.uppercase()} · ${group.projects.size}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                            items(group.projects, key = { it.id }) { project ->
                                ProjectCard(project = project, onClick = { onOpenProject(project.id) })
                            }
                        }
                    }
                }
            }
        }
    }

    if (showIdea) {
        IdeaSheet(
            onDismiss = { showIdea = false },
            onSubmit = { name, description, department ->
                viewModel.submitIdea(name, description, department) { ok ->
                    if (ok) showIdea = false
                }
            },
        )
    }
}

/** Pasek stanu łączności — ten sam język co w Zadaniach i Serwisie. */
@Composable
private fun OfflineStrip(offline: Boolean, pending: Int) {
    if (!offline && pending == 0) return
    val text = when {
        pending > 0 && offline -> "Brak sieci · $pending ${changesWord(pending)} w kolejce"
        pending > 0 -> "$pending ${changesWord(pending)} czeka na wysłanie"
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

private fun changesWord(count: Int): String = if (count == 1) "zmiana" else "zmiany"

/** Kolor okładki z koloru projektu; bez niego marka ekotak (zieleń). */
private fun coverColor(raw: String?): Color {
    val hex = raw?.removePrefix("#")?.takeIf { it.length == 6 } ?: "44d62c"
    return runCatching { Color(("ff$hex").toLong(16)) }.getOrDefault(Color(0xFF44D62C))
}

@Composable
private fun ProjectCard(project: Project, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Column {
            // Pasek koloru zamiast grafiki: okładki z MinIO nie ciągniemy na
            // telefon, a kolor i tak niesie rozpoznanie projektu.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .background(coverColor(project.color)),
            )
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        project.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                    )
                    if (project.localOnly) {
                        StageChip(text = "czeka", color = MaterialTheme.colorScheme.tertiary)
                    } else {
                        StageChip(
                            text = project.stage.label,
                            color = stageColor(project.stage),
                        )
                    }
                }

                if (project.taskCount > 0) {
                    LinearProgressIndicator(
                        progress = { project.progressPercent / 100f },
                        modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                    )
                }

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        if (project.taskCount > 0) {
                            "${project.doneCount}/${project.taskCount} zadań · ${project.progressPercent}%"
                        } else {
                            "bez zadań"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        project.department ?: "—",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun StageChip(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.18f), shape = RoundedCornerShape(6.dp)) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 10.sp,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun stageColor(stage: ProjectStage): Color = when (stage) {
    ProjectStage.ACTIVE -> Color(0xFF2A7A1C)
    ProjectStage.APPROVAL -> Color(0xFF1C5C8A)
    ProjectStage.APPRAISAL -> Color(0xFF8A6A12)
    ProjectStage.CLOSED -> MaterialTheme.colorScheme.onSurfaceVariant
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun Spacer8() = Spacer(Modifier.height(8.dp))
