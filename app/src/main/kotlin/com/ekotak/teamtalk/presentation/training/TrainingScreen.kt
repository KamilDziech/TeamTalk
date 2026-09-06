package com.ekotak.teamtalk.presentation.training

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ekotak.teamtalk.domain.model.AssignmentStatus
import com.ekotak.teamtalk.presentation.components.AppTopBar
import com.ekotak.teamtalk.presentation.theme.EkotakGreen
import com.ekotak.teamtalk.presentation.theme.Orange600
import com.ekotak.teamtalk.presentation.theme.Red600
import com.ekotak.teamtalk.presentation.theme.SyncBlue

/**
 * „Moje szkolenia" — mobilny odpowiednik zakładki HR → Szkolenia z panelu.
 * Ta sama kolejność wierszy i te same pigułki co w `TrainingList.tsx`, tylko
 * z belką pilności po lewej: na 318 px pigułka po prawej ginie przy długim
 * tytule, a to ona niesie „zrób to dziś".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrainingScreen(
    onNavigateBack: () -> Unit,
    onOpenLesson: (String) -> Unit,
    viewModel: TrainingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = { AppTopBar(title = "Szkolenia", onNavigateBack = onNavigateBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            } else {
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = viewModel::refresh,
                ) {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                FilterChip(
                                    selected = state.tab == TrainingTab.LESSONS,
                                    onClick = { viewModel.setTab(TrainingTab.LESSONS) },
                                    label = { Text("Moje szkolenia") },
                                )
                                FilterChip(
                                    selected = state.tab == TrainingTab.LEVELS,
                                    onClick = { viewModel.setTab(TrainingTab.LEVELS) },
                                    label = { Text("Moje poziomy") },
                                )
                            }
                        }

                        state.error?.let { error ->
                            item { ErrorStrip(error) }
                        }

                        when (state.tab) {
                            TrainingTab.LESSONS -> {
                                item { Counters(state) }
                                if (state.rows.isEmpty()) {
                                    item {
                                        Empty(
                                            "Nie masz jeszcze przypisanych szkoleń. " +
                                                "Pojawią się tutaj, gdy opiekun je zada.",
                                        )
                                    }
                                }
                                items(state.rows, key = { it.assignment.id }) { row ->
                                    TrainingRow(
                                        row = row,
                                        onClick = { onOpenLesson(row.assignment.lessonId) },
                                        onCertificate = {
                                            viewModel.openCertificate(
                                                row.assignment.lessonId,
                                                context.cacheDir,
                                            ) { file -> context.openPdf(file) }
                                        },
                                    )
                                }
                            }

                            TrainingTab.LEVELS -> skillLevelItems(this, state)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Counters(state: TrainingViewModel.UiState) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Counter("do zrobienia", state.todoCount, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
        Counter("po terminie", state.overdueCount, Orange600, Modifier.weight(1f))
        Counter("zaliczone", state.passedCount, EkotakGreen, Modifier.weight(1f))
    }
}

@Composable
private fun Counter(label: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier,
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                value.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = color,
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Wiersz szkolenia. Kolor belki i pigułki jest wspólny: czerwony = do poprawy,
 * pomarańczowy = po terminie albo do odnowienia, niebieski = do zrobienia,
 * zielony = zaliczone i ważne.
 */
@Composable
private fun TrainingRow(
    row: TrainingViewModel.Row,
    onClick: () -> Unit,
    onCertificate: () -> Unit,
) {
    val accent = when {
        row.assignment.status == AssignmentStatus.FAILED -> Red600
        row.expired || row.overdue -> Orange600
        row.assignment.status == AssignmentStatus.PASSED -> EkotakGreen
        else -> SyncBlue
    }
    val meta = buildList {
        if (row.assignment.questionCount > 0) add("${row.assignment.questionCount} pyt.")
        row.assignment.assignedBy?.let { add("przypisał: $it") }
        row.assignment.dueDate?.let { add("termin ${formatDayOrDash(it)}") }
        if (row.assignment.status == AssignmentStatus.PASSED) {
            row.assignment.score?.let { add("wynik $it%") }
            row.assignment.expiresAt?.let { add("ważne do ${formatDayOrDash(it)}") }
        }
    }.joinToString(" · ")

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(IntrinsicSize.Min),
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(accent),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Text(
                    row.assignment.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (meta.isNotEmpty()) {
                    Text(
                        meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (row.assignment.status == AssignmentStatus.PASSED) {
                TextButton(onClick = onCertificate) { Text("PDF") }
            }
            Pill(text = row.statusLabel, color = accent)
            Box(Modifier.width(8.dp))
        }
    }
}

@Composable
internal fun Pill(text: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.12f),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(alpha = 0.55f)),
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun ErrorStrip(message: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Red600.copy(alpha = 0.12f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            message,
            style = MaterialTheme.typography.bodySmall,
            color = Red600,
            modifier = Modifier.padding(10.dp),
        )
    }
}

@Composable
private fun Empty(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 24.dp),
    )
}

