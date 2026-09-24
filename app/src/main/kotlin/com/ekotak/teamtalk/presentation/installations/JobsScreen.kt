package com.ekotak.teamtalk.presentation.installations

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ekotak.teamtalk.domain.model.MontazJobRow
import com.ekotak.teamtalk.domain.model.MontazStatus
import com.ekotak.teamtalk.domain.model.MyDays
import com.ekotak.teamtalk.presentation.components.AppTopBar
import com.ekotak.teamtalk.presentation.theme.OkGreen
import com.ekotak.teamtalk.presentation.theme.Orange600
import com.ekotak.teamtalk.presentation.theme.SyncBlue

/**
 * MOJE MONTAŻE — wejście z kafelka „Montaże".
 *
 * Lista prowadzi, a nie tylko pokazuje: sekcje idą po dniach, a prawa krawędź
 * wiersza niesie JEDNĄ informację o stanie — ile spakowane, czy robota trwa,
 * czy został protokół. Makieta: design/mockups/modul-montaz.html.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobsScreen(
    onNavigateBack: () -> Unit,
    onOpenJob: (String) -> Unit,
    viewModel: JobsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    // Lista odświeża się przy wejściu i gestem w dół — karta wyjazdu zmienia
    // stan, którego ekran nie widzi (start roboty, zamknięty protokół).
    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = { AppTopBar(title = "Montaże", onNavigateBack = onNavigateBack) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)) {
            ScopeTabs(selected = state.scope, onSelect = viewModel::setScope)
            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CountChip("Dziś", state.todayCount)
                CountChip("Tydzień", state.weekCount)
            }

            if (state.fromCache) {
                Banner(
                    text = "Bez zasięgu — to kopia z telefonu. Obsada mogła się zmienić w biurze.",
                    color = SyncBlue,
                )
            }
            state.error?.let { Banner(text = it, color = Orange600) }

            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when {
                    state.isLoading -> Box3 { CircularProgressIndicator() }

                    state.sections.isEmpty() -> Box3 {
                        Text(
                            text = when (state.scope) {
                                JobsViewModel.Scope.DONE -> "Nie masz jeszcze zamkniętych montaży."
                                JobsViewModel.Scope.CREW -> "Ekipa nie ma zaplanowanych wyjazdów."
                                else -> "Nie masz przypisanych montaży. Wyjazdy planuje koordynator."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    else -> LazyColumn(Modifier.fillMaxSize()) {
                        state.myDays?.let { days ->
                            val upcoming = upcomingDays(days)
                            if (upcoming.isNotEmpty()) item(key = "my-days") { MyDaysCard(upcoming) }
                        }
                        state.sections.forEach { section ->
                            item(key = "h-${section.label}") { DayHeader(section.label) }
                            items(section.jobs, key = { it.id }) { row ->
                                JobRow(row = row, days = state.myDays, onClick = { onOpenJob(row.id) })
                            }
                        }
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScopeTabs(
    selected: JobsViewModel.Scope,
    onSelect: (JobsViewModel.Scope) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        JobsViewModel.Scope.entries.forEach { scope ->
            FilterChip(
                selected = scope == selected,
                onClick = { onSelect(scope) },
                label = { Text(scope.label) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CountChip(label: String, count: Int) {
    Text(
        text = "$label · $count",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

@Composable
private fun DayHeader(label: String) {
    Text(
        text = label.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.1.sp),
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
    )
}

/**
 * Wiersz wyjazdu: godzina, klient z adresem, zakres i stan. Kolorowy pasek
 * z lewej odróżnia dzisiejszy wyjazd od reszty — na liście przewijanej kciukiem
 * w aucie to jedyna rzecz, którą widać bez czytania.
 */
@Composable
private fun JobRow(row: MontazJobRow, days: MyDays?, onClick: () -> Unit) {
    val accent = when {
        row.status == MontazStatus.IN_PROGRESS -> Orange600
        row.status == MontazStatus.DONE -> OkGreen
        isToday(row.scheduledAt) -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(
            Modifier
                .width(3.dp)
                .height(38.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.width(46.dp)) {
            Text(
                text = hourLabel(row.scheduledAt) ?: "—",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (row.durationDays > 1) "${row.durationDays} dni" else "1 dzień",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = row.clientName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOfNotNull(row.address, row.city)
                    .firstOrNull()
                    ?: "adres w teczce wyjazdu",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            jobSpan(row, days)?.let { span ->
                Text(
                    text = span,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (row.scopeNames.isNotEmpty()) {
                Text(
                    text = row.scopeNames.joinToString(" · ") { it.substringAfterLast(" / ") },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = when {
                row.pending -> "czeka"
                row.status == MontazStatus.DONE -> "✓"
                row.status == MontazStatus.IN_PROGRESS -> "w toku"
                else -> "plan"
            },
            style = MaterialTheme.typography.labelSmall,
            color = when {
                row.pending -> SyncBlue
                row.status == MontazStatus.DONE -> OkGreen
                row.status == MontazStatus.IN_PROGRESS -> Orange600
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

private val DOW_SHORT = listOf("pn", "wt", "śr", "cz", "pt", "sb", "nd")

private fun dmy(d: java.time.LocalDate): String =
    "${DOW_SHORT[d.dayOfWeek.value - 1]} %02d.%02d".format(d.dayOfMonth, d.monthValue)

/**
 * „do pn 05.10 · przerwa: sb, nd" — koniec montażu wg kalendarza z Harmonogramu
 * (weekend, święta, blokady firmy i ekipy, pracujące soboty). Jednodniowy
 * montaż bez przerwy nie potrzebuje tej linijki.
 */
private fun jobSpan(row: MontazJobRow, days: MyDays?): String? {
    if (days == null || row.durationDays <= 1) return null
    val start = row.scheduledAt?.take(10)?.let { runCatching { java.time.LocalDate.parse(it) }.getOrNull() } ?: return null
    val end = days.endOf(start, row.durationDays)
    val off = days.offDaysBetween(start, end)
    val breakText = if (off.isEmpty()) "" else " · przerwa: " + off.joinToString(", ") { d ->
        days.blockOf(d)?.label ?: DOW_SHORT[d.dayOfWeek.value - 1]
    }.let { if (it.length > 40) "${off.size} dni" else it }
    return "do ${dmy(end)}$breakText"
}

/** Nadchodzące dni wolne i pracujące soboty (4 tygodnie) — najbliższe na górze. */
private fun upcomingDays(days: MyDays): List<String> {
    val today = java.time.LocalDate.now()
    val limit = today.plusDays(28)
    val blocks = days.blocks
        .filter { !it.end.isBefore(today) && !it.start.isAfter(limit) }
        .map { b ->
            val range = if (b.start == b.end) dmy(b.start) else "${dmy(b.start)} – ${dmy(b.end)}"
            b.start to "$range — ${b.label}" + when {
                b.scope == com.ekotak.teamtalk.domain.model.BlockScope.USER -> " (Ty)"
                b.crewName != null -> " (${b.crewName})"
                else -> " (cała firma)"
            }
        }
    val work = days.workdays
        .filter { !it.day.isBefore(today) && !it.day.isAfter(limit) }
        .map { it.day to "${dmy(it.day)} — pracujecie (${it.crewName})" }
    return (blocks + work).sortedBy { it.first }.map { it.second }
}

@Composable
private fun MyDaysCard(lines: List<String>) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            "DNI WOLNE I PRACUJĄCE",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.1.sp),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        lines.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
internal fun Banner(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
    )
}

@Composable
private fun Box3(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}
