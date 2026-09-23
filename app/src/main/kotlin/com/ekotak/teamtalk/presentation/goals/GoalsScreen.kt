package com.ekotak.teamtalk.presentation.goals

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ekotak.teamtalk.domain.model.Goal
import com.ekotak.teamtalk.domain.model.GoalTrend
import com.ekotak.teamtalk.presentation.components.AppTopBar
import com.ekotak.teamtalk.presentation.theme.EkotakGreen
import com.ekotak.teamtalk.presentation.theme.Red600
import com.ekotak.teamtalk.presentation.theme.SyncBlue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Moduł Cele — pełne 1:1 z `/app/goals` panelu (decyzja 2026-09-23): trzy
 * zakładki, kreator celu, wpis ręczny i zamykanie okresu, wszystko z pełnym
 * offline.
 *
 * Bez zasięgu ekran pokazuje ostatnią migawkę z podpisem „dane z <godzina>",
 * a zapisy czekają w kolejce — tak samo jak Zadania, Serwis i Urlop.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(
    onNavigateBack: () -> Unit,
    viewModel: GoalsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var checkinFor by remember { mutableStateOf<Goal?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = { AppTopBar(title = "Cele", onNavigateBack = onNavigateBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (state.canManage) {
                FloatingActionButton(
                    onClick = viewModel::openEditor,
                    containerColor = EkotakGreen,
                ) { Text("+", style = MaterialTheme.typography.headlineSmall) }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item { TabRow(state = state, onTab = viewModel::setTab) }
                    item { PeriodRow(state = state, onPeriod = viewModel::setPeriod) }

                    if (state.tab == GoalsTab.TEAM) {
                        item { TeamRow(state = state, onTeam = viewModel::setTeam) }
                    }
                    if (state.tab == GoalsTab.PERSONAL) {
                        val managed = state.personal?.managed.orEmpty()
                        if (managed.size > 1) {
                            item { PeopleRow(state = state, onPerson = viewModel::setPerson) }
                        }
                    }

                    item { StatusStrip(state = state) }

                    state.error?.let { error -> item { ErrorStrip(error) } }

                    val items = state.items
                    if (items.isEmpty() && !state.isRefreshing) {
                        item { EmptyState(state) }
                    }

                    items(items, key = { it.id }) { goal ->
                        GoalCard(
                            goal = goal,
                            onCheckin = if (goal.isManual && !goal.isClosed) {
                                { checkinFor = goal }
                            } else null,
                            onClose = if (state.canManage && !goal.isClosed) {
                                { viewModel.closeGoal(goal.id) }
                            } else null,
                            onDelete = if (state.canManage) {
                                { viewModel.deleteGoal(goal.id) }
                            } else null,
                        )
                    }

                    state.trend?.let { trend ->
                        val lead = items.firstOrNull()
                        if (lead != null && trend.points.isNotEmpty()) {
                            item { TrendCard(lead = lead, trend = trend) }
                        }
                    }

                    if (state.tab == GoalsTab.TEAM) {
                        teamMembersSection(state)
                    }
                    if (state.tab == GoalsTab.COMPANY) {
                        departmentsSection(state)
                    }

                    historySection(state)
                }
            }
        }
    }

    if (state.editorOpen) {
        GoalEditorSheet(
            state = state,
            onDismiss = viewModel::closeEditor,
            onSave = viewModel::saveGoal,
        )
    }

    checkinFor?.let { goal ->
        GoalCheckinSheet(
            goal = goal,
            onDismiss = { checkinFor = null },
            onSave = { value, note ->
                viewModel.checkin(goal.id, value, note)
                checkinFor = null
            },
        )
    }
}

// ── Paski sterowania ─────────────────────────────────────────────────────────

@Composable
private fun TabRow(state: GoalsViewModel.UiState, onTab: (GoalsTab) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = state.tab == GoalsTab.PERSONAL,
            onClick = { onTab(GoalsTab.PERSONAL) },
            label = { Text("Osobiste") },
        )
        FilterChip(
            selected = state.tab == GoalsTab.TEAM,
            onClick = { onTab(GoalsTab.TEAM) },
            label = { Text("Zespołu") },
        )
        FilterChip(
            selected = state.tab == GoalsTab.COMPANY,
            onClick = { onTab(GoalsTab.COMPANY) },
            label = { Text("Firmy") },
        )
    }
}

@Composable
private fun PeriodRow(state: GoalsViewModel.UiState, onPeriod: (String) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GoalsViewModel.periodOptions().forEach { key ->
            FilterChip(
                selected = state.period == key,
                onClick = { onPeriod(key) },
                label = { Text(periodLabel(key)) },
            )
        }
    }
}

@Composable
private fun TeamRow(state: GoalsViewModel.UiState, onTeam: (String) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DEPARTMENT_LABELS.forEach { (key, label) ->
            FilterChip(
                selected = state.team == key,
                onClick = { onTeam(key) },
                label = { Text(label) },
            )
        }
    }
}

/** Przełącznik osoby — widoczny dla zwierzchnika i zarządu. */
@Composable
private fun PeopleRow(state: GoalsViewModel.UiState, onPerson: (String?) -> Unit) {
    val managed = state.personal?.managed.orEmpty()
    val current = state.personal?.person?.id
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        managed.forEach { person ->
            FilterChip(
                selected = current == person.id,
                onClick = { onPerson(person.id) },
                label = { Text(person.name) },
            )
        }
    }
}

/**
 * Pasek stanu danych: czyje cele, z kiedy migawka i ile zapisów czeka
 * w kolejce. Bez tego człowiek bez zasięgu nie wie, czy patrzy na dziś,
 * czy na wczoraj.
 */
@Composable
private fun StatusStrip(state: GoalsViewModel.UiState) {
    val who = when (state.tab) {
        GoalsTab.PERSONAL -> state.personal?.person?.name ?: "Moje cele"
        GoalsTab.TEAM -> "Dział ${state.teamGoals?.teamLabel ?: DEPARTMENT_LABELS[state.team].orEmpty()}"
        GoalsTab.COMPANY -> "Cele firmy"
    }
    val synced = state.syncedAt?.let {
        "dane z " + SimpleDateFormat("HH:mm", Locale("pl", "PL")).format(Date(it))
    }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = "$who · ${periodLabel(state.period)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = listOfNotNull(
                    synced,
                    "kreska na pasku = tempo (ile powinno być na dziś)",
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.tab == GoalsTab.TEAM && state.teamGoals?.detailed == false) {
                Text(
                    text = "Imienne wyniki działu widzi zwierzchnik i zarząd.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.pendingCount > 0) {
                Text(
                    text = "${state.pendingCount} zapis(y) czekają na wysyłkę",
                    style = MaterialTheme.typography.labelSmall,
                    color = SyncBlue,
                )
            }
        }
    }
}

@Composable
private fun ErrorStrip(text: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Red600.copy(alpha = 0.12f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.bodySmall,
            color = Red600,
        )
    }
}

@Composable
private fun EmptyState(state: GoalsViewModel.UiState) {
    val text = when (state.tab) {
        GoalsTab.PERSONAL ->
            "Na ten okres nie masz ustawionych celów. Cele osobiste ustawia zwierzchnik albo zarząd."
        GoalsTab.TEAM -> "Dział nie ma celów na ten okres."
        GoalsTab.COMPANY -> "Firma nie ma celów na ten okres."
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Wykres narastający ───────────────────────────────────────────────────────

/**
 * Realizacja narastająco na tle planu liniowego — ten sam wykres, co w panelu,
 * dla celu wiodącego zakładki. Plan to prosta od zera do celu: nie udaje
 * sezonowości, odpowiada na pytanie „jesteśmy przed czy za?".
 */
@Composable
private fun TrendCard(lead: Goal, trend: GoalTrend) {
    val planColor = MaterialTheme.colorScheme.onSurfaceVariant
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = "${lead.name} — narastająco",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
            )
            Canvas(
                Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .padding(top = 10.dp),
            ) {
                val max = maxOf(trend.target, trend.points.maxOf { it.value }) * 1.05
                if (max <= 0.0) return@Canvas
                val n = trend.points.size
                fun x(i: Int) = size.width * i / maxOf(1, n - 1)
                fun y(v: Double) = (size.height - (size.height * (v / max))).toFloat()

                drawLine(
                    color = planColor,
                    start = Offset(0f, y(0.0)),
                    end = Offset(size.width, y(trend.target)),
                    strokeWidth = 2f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)),
                )
                for (i in 0 until n - 1) {
                    drawLine(
                        color = EkotakGreen,
                        start = Offset(x(i), y(trend.points[i].value)),
                        end = Offset(x(i + 1), y(trend.points[i + 1].value)),
                        strokeWidth = 5f,
                        cap = StrokeCap.Round,
                    )
                }
            }
            Text(
                text = "zielone — realizacja, kreskowane — plan liniowy",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Sekcje zakładek ──────────────────────────────────────────────────────────

/** Tabela wkładów działu — tylko gdy serwer dał imienne wyniki. */
private fun androidx.compose.foundation.lazy.LazyListScope.teamMembersSection(
    state: GoalsViewModel.UiState,
) {
    val view = state.teamGoals ?: return
    if (!view.detailed || view.items.isEmpty() || view.members.isEmpty()) return
    val lead = view.items.first()

    item {
        Text(
            text = "Wkład osób — ${lead.name}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
    items(view.members, key = { "member-${it.id}" }) { member ->
        val own = view.personalGoals.firstOrNull { it.ownerUserId == member.id }
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(member.name, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        goalValueText(member.contribution, lead.unit),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                }
                if (own != null) {
                    PaceBar(
                        pct = own.pct,
                        pacePct = own.pacePct,
                        color = goalStatusColor(own.status),
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Text(
                        text = "${goalValueText(own.value, own.unit)} / ${goalValueText(own.target, own.unit)} · ${Math.round(own.pct)}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = "bez celu na ten okres",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Wkład działów w cel firmowy — słupki, jak w panelu. */
private fun androidx.compose.foundation.lazy.LazyListScope.departmentsSection(
    state: GoalsViewModel.UiState,
) {
    val view = state.company ?: return
    if (view.byDepartment.isEmpty() || view.items.isEmpty()) return
    val lead = view.items.first()
    val max = view.byDepartment.maxOf { it.value }.coerceAtLeast(1.0)

    item {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(Modifier.padding(14.dp)) {
                Text(
                    text = "Wkład działów — ${view.leadMetricLabel ?: lead.name}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                view.byDepartment.forEach { dept ->
                    Row(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(dept.label, style = MaterialTheme.typography.bodySmall)
                        Text(
                            goalValueText(dept.value, lead.unit),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    PaceBar(
                        pct = dept.value / max * 100.0,
                        pacePct = 0.0,
                        color = EkotakGreen,
                    )
                }
                Text(
                    text = "Suma działów bywa inna niż wynik firmy — cel firmowy liczy się " +
                        "z całej organizacji.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/** Zamknięte okresy — ta sama tabela, co w panelu. */
private fun androidx.compose.foundation.lazy.LazyListScope.historySection(
    state: GoalsViewModel.UiState,
) {
    val rows = when (state.tab) {
        GoalsTab.PERSONAL -> state.personal?.history
        GoalsTab.TEAM -> state.teamGoals?.history
        GoalsTab.COMPANY -> state.company?.history
    }.orEmpty()
    if (rows.isEmpty()) return

    item {
        Text(
            text = "Poprzednie okresy",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
    items(rows, key = { "history-${it.id}" }) { row ->
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(periodLabel(row.periodKey), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "${goalValueText(row.value, row.unit)} / ${goalValueText(row.target, row.unit)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                AssistChip(
                    onClick = {},
                    label = { Text("${Math.round(row.pct)}%") },
                )
            }
        }
    }
}
