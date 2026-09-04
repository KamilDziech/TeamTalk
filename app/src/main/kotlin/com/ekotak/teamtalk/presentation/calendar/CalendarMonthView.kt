package com.ekotak.teamtalk.presentation.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ekotak.teamtalk.domain.model.CalendarEvent
import com.ekotak.teamtalk.domain.model.CalendarOverlay
import java.time.LocalDate
import java.time.YearMonth

/**
 * Widok miesiąca — odwzorowanie siatki 6×7 z panelu.
 *
 * Panel wpisuje w komórkę chipy z tytułami; na 360 dp komórka ma ~44 dp, więc
 * mieszczą się w niej wyłącznie paski kolorów, a treść dnia przenosimy pod
 * siatkę (ustalenie z makiety, ten sam zabieg co w kalendarzu modułu Serwis).
 */
@Composable
fun CalendarMonthView(
    state: CalendarViewModel.UiState,
    onSelectDay: (LocalDate) -> Unit,
    onOpenEvent: (CalendarEvent) -> Unit,
    onOpenOverlay: ((CalendarOverlay) -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val month = remember(state.anchor) { YearMonth.from(state.anchor) }
    val today = remember(state.now) { millisToDate(state.now) }

    val eventsByDay = remember(state.visibleEvents) {
        state.visibleEvents
            .flatMap { event -> event.coveredDays().map { it to event } }
            .groupBy({ it.first }, { it.second })
    }
    val overlaysByDay = remember(state.overlays, state.overlaysOn) {
        state.visibleOverlays
            .mapNotNull { overlay ->
                com.ekotak.teamtalk.presentation.crm.parseIsoMillis(overlay.startAt)
                    ?.let { millisToDate(it) to overlay }
            }
            .groupBy({ it.first }, { it.second })
    }

    // Dni, w których osoba z filtra ma prywatną zajętość — w komórce siatki
    // wystarczy szara kropka, godziny pokazujemy w liście pod spodem.
    val busyDays = remember(state.visibleBusy, month) {
        val start = startOfWeek(month.atDay(1))
        (0 until 42).map { start.plusDays(it.toLong()) }
            .filter { busySlotsFor(state.visibleBusy, it).isNotEmpty() }
            .toSet()
    }

    Column(modifier = modifier.fillMaxSize()) {
        WeekdayHeader()
        MonthGrid(
            month = month,
            today = today,
            selected = state.selectedDay,
            eventsByDay = eventsByDay,
            overlaysByDay = overlaysByDay,
            busyDays = busyDays,
            onSelect = onSelectDay,
        )
        val day = state.selectedDay
        if (day == null) {
            Text(
                text = "Dotknij dnia, aby zobaczyć terminy.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
            return@Column
        }
        val events = eventsByDay[day].orEmpty().sortedBy { it.startMillis() ?: 0L }
        val overlays = overlaysByDay[day].orEmpty()
        val busy = busySlotsFor(state.visibleBusy, day)
        // Zajętości NIE liczymy do licznika terminów — to nie są terminy, tylko
        // godziny, w których człowiek jest niedostępny.
        DayHeader(dayLabel(day), events.size + overlays.size)
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        ) {
            items(events, key = { "ev:${it.id}" }) { event ->
                EventRow(
                    event = event,
                    currentUserId = state.currentUserId,
                    onClick = { onOpenEvent(event) },
                )
            }
            items(overlays, key = { "ov:${it.source.wire}:${it.id}" }) { overlay ->
                OverlayRow(overlay = overlay, onClick = onOpenOverlay?.let { open -> { open(overlay) } })
            }
            items(busy, key = { "busy:${it.startMin}" }) { span ->
                BusyRow(label = busyLabel(span, day))
            }
            if (events.isEmpty() && overlays.isEmpty() && busy.isEmpty()) {
                item {
                    Text(
                        text = "Wolny dzień.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekdayHeader() {
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        WEEKDAY_LABELS.forEach { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    today: LocalDate,
    selected: LocalDate?,
    eventsByDay: Map<LocalDate, List<CalendarEvent>>,
    overlaysByDay: Map<LocalDate, List<CalendarOverlay>>,
    busyDays: Set<LocalDate>,
    onSelect: (LocalDate) -> Unit,
) {
    val start = startOfWeek(month.atDay(1))
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        for (week in 0 until 6) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (dow in 0 until 7) {
                    val day = start.plusDays((week * 7 + dow).toLong())
                    DayCell(
                        day = day,
                        inMonth = YearMonth.from(day) == month,
                        isToday = day == today,
                        isSelected = day == selected,
                        events = eventsByDay[day].orEmpty(),
                        overlays = overlaysByDay[day].orEmpty(),
                        hasBusy = day in busyDays,
                        onClick = { onSelect(day) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    events: List<CalendarEvent>,
    overlays: List<CalendarOverlay>,
    hasBusy: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.aspectRatio(0.8f).padding(1.dp)) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = if (isSelected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
            border = BorderStroke(
                width = if (isToday) 1.5.dp else 1.dp,
                color = if (isToday) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.outlineVariant
                },
            ),
            modifier = Modifier.fillMaxSize().clickable(onClick = onClick),
        ) {
            Column(modifier = Modifier.padding(horizontal = 3.dp, vertical = 2.dp)) {
                Text(
                    text = "${day.dayOfMonth}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                        alpha = if (inMonth) 1f else 0.35f,
                    ),
                )
                Spacer(Modifier.height(1.dp))
                // Najwyżej trzy paski — reszta dnia jest w liście pod siatką.
                val bars = events.take(3)
                bars.forEach { event ->
                    // Całodniowe rysujemy obwódką: inaczej dzień urlopu wyglądałby
                    // jak spotkanie o konkretnej godzinie.
                    Bar(eventColor(event), outlined = event.allDay)
                }
                // Szary pasek „zajęte" idzie po wydarzeniach — jest tłem dnia,
                // nie terminem, więc nie ma prawa wypchnąć wpisu z kalendarza.
                if (hasBusy && bars.size < 3) Bar(MaterialTheme.colorScheme.onSurfaceVariant, outlined = true)
                overlays.take(3 - bars.size).forEach { overlay ->
                    Bar(
                        parseHexColor(overlay.color) ?: parseHexColor(overlay.source.color) ?: Color.Gray,
                        outlined = true,
                    )
                }
                val hidden = events.size + overlays.size - 3
                if (hidden > 0) {
                    Text(
                        text = "+$hidden",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun Bar(color: Color, outlined: Boolean = false) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .padding(top = 1.dp)
            .background(if (outlined) Color.Transparent else color, RoundedCornerShape(2.dp))
            .border(
                width = if (outlined) 1.dp else 0.dp,
                color = if (outlined) color else Color.Transparent,
                shape = RoundedCornerShape(2.dp),
            ),
    )
}
