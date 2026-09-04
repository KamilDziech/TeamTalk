package com.ekotak.teamtalk.presentation.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ekotak.teamtalk.domain.model.CalendarEvent
import com.ekotak.teamtalk.domain.model.CalendarOverlay
import com.ekotak.teamtalk.presentation.crm.parseIsoMillis
import java.time.LocalDate
import java.time.format.TextStyle

/**
 * Agenda — 30 dni od kotwicy, dni bez terminów wypadają. To widok, który
 * w terenie zastępuje panel: jedno przewinięcie kciukiem i wiadomo, co jest
 * do końca miesiąca.
 */
@Composable
fun CalendarAgendaView(
    state: CalendarViewModel.UiState,
    onOpenEvent: (CalendarEvent) -> Unit,
    onOpenOverlay: ((CalendarOverlay) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val today = remember(state.now) { millisToDate(state.now) }
    val days = remember(
        state.visibleEvents,
        state.overlays,
        state.overlaysOn,
        state.visibleBusy,
        state.anchor,
    ) {
        val from = state.anchor
        val to = state.anchor.plusDays(AGENDA_DAYS.toLong())
        val events = state.visibleEvents
            .flatMap { event -> event.coveredDays().map { it to event } }
            .filter { (day, _) -> day >= from && day < to }
            .groupBy({ it.first }, { it.second })
        val overlays = state.visibleOverlays
            .mapNotNull { overlay -> parseIsoMillis(overlay.startAt)?.let { millisToDate(it) to overlay } }
            .filter { (day, _) -> day >= from && day < to }
            .groupBy({ it.first }, { it.second })
        // Dzień z samą zajętością też musi być na liście — inaczej agenda
        // pokazywałaby wolne popołudnie, na które i tak nic nie wejdzie.
        val busy = (0 until AGENDA_DAYS)
            .map { from.plusDays(it.toLong()) }
            .associateWith { busySlotsFor(state.visibleBusy, it) }
            .filterValues { it.isNotEmpty() }
        (events.keys + overlays.keys + busy.keys).sorted().map { day ->
            AgendaDay(
                day = day,
                events = events[day].orEmpty().sortedBy { it.startMillis() ?: 0L },
                overlays = overlays[day].orEmpty(),
                busy = busy[day].orEmpty(),
            )
        }
    }

    if (days.isEmpty()) {
        Text(
            text = "Brak terminów w tym zakresie.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = modifier.fillMaxWidth().padding(24.dp),
        )
        return
    }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = 6.dp,
            bottom = 88.dp,
        ),
        modifier = modifier.fillMaxSize(),
    ) {
        items(days.size, key = { days[it].day.toString() }) { index ->
            val item = days[index]
            Row(modifier = Modifier.fillMaxWidth()) {
                DayStamp(day = item.day, isToday = item.day == today)
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    item.events.forEach { event ->
                        EventRow(
                            event = event,
                            currentUserId = state.currentUserId,
                            onClick = { onOpenEvent(event) },
                        )
                    }
                    item.overlays.forEach { overlay ->
                        OverlayRow(
                            overlay = overlay,
                            onClick = onOpenOverlay?.let { open -> { open(overlay) } },
                        )
                    }
                    item.busy.forEach { span ->
                        BusyRow(label = busyLabel(span, item.day))
                    }
                }
            }
        }
    }
}

private data class AgendaDay(
    val day: LocalDate,
    val events: List<CalendarEvent>,
    val overlays: List<CalendarOverlay>,
    val busy: List<BusySpan> = emptyList(),
)

@Composable
private fun DayStamp(day: LocalDate, isToday: Boolean) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(38.dp).padding(top = 4.dp, end = 4.dp),
    ) {
        Text(
            text = "${day.dayOfMonth}",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (isToday) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        Text(
            text = day.dayOfWeek.getDisplayName(TextStyle.SHORT, PL),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
