package com.ekotak.teamtalk.presentation.calendar

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ekotak.teamtalk.domain.model.CalendarEvent
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Widoki tygodnia i dnia — siatka godzinowa panelu przeniesiona na 360 dp.
 *
 * Rozkład nakładających się wydarzeń na kolumny jest ten sam co w
 * `CalendarView.tsx` (`layoutDay`): wydarzenia zachodzące na siebie dzielą
 * szerokość dnia, a sąsiadujące bez przecięcia wracają na pełną.
 *
 * Godziny rozciąga się dwiema kropkami na rogach kafelka — jak w makiecie.
 * Kafelka jako całości NIE przeciągamy: na dotyku kłóciłoby się to
 * z przewijaniem siatki. Uchwyty dostaje jedno wydarzenie naraz, po
 * przytrzymaniu kafelka (albo zaraz po dodaniu terminu), więc palec ląduje na
 * kropce świadomie, a nie w trakcie przewijania. Krok to [SNAP_MINUTES],
 * dokładniejszą godzinę wpisuje się w karcie.
 */

private val HOUR_HEIGHT = 44.dp
private val GUTTER_WIDTH = 28.dp

/** Krok rozciągania — kwadranse, tak jak łapie siatka panelu. */
private const val SNAP_MINUTES = 15

/** Najkrótsze wydarzenie, jakie da się ustawić kropkami. */
private const val MIN_EVENT_MILLIS = SNAP_MINUTES * 60_000L

private val HANDLE_DOT = 14.dp

/** Pole dotyku kropki — sama kropka jest za mała na palec. */
private val HANDLE_TOUCH = 40.dp

/**
 * Godziny wydarzenia trzymane „w palcu". Rysujemy je zamiast tych z bazy od
 * chwili przeciągnięcia aż do powrotu zapisu — inaczej kafelek odskakiwałby
 * na stare, dopóki nie wróci odpowiedź serwera.
 */
private data class ResizeDraft(
    val eventId: String,
    val startMillis: Long,
    val endMillis: Long,
)

@Composable
fun CalendarTimeGrid(
    state: CalendarViewModel.UiState,
    days: List<LocalDate>,
    onOpenEvent: (CalendarEvent) -> Unit,
    onCreateAt: (LocalDate, Int) -> Unit,
    onArmResize: (CalendarEvent) -> Unit = {},
    onResize: (String, Long, Long) -> Unit = { _, _, _ -> },
    onCancelResize: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val today = remember(state.now) { millisToDate(state.now) }
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    // Siatka otwiera się na 7:00 — tak samo jak panel.
    LaunchedEffect(days.firstOrNull(), state.view) {
        scroll.scrollTo(with(density) { (HOUR_HEIGHT * 7).roundToPx() })
    }

    val byDay = remember(state.visibleEvents, days) {
        days.associateWith { day ->
            state.visibleEvents.filter { event ->
                !event.allDay && event.coveredDays().contains(day)
            }
        }
    }
    val allDay = remember(state.visibleEvents, days) {
        state.visibleEvents.filter { event ->
            event.allDay && event.coveredDays().any { it in days }
        }
    }
    // Prywatna zajętość pocięta na dni — blok 22:00–01:00 daje kawałek w dwóch
    // kolumnach, więc nie da się tego policzyć raz na całym przedziale.
    val busyByDay = remember(state.visibleBusy, days) {
        days.associateWith { day -> busySlotsFor(state.visibleBusy, day) }
    }

    val armedEvent = state.visibleEvents.firstOrNull { it.id == state.resizeEventId }
    var draft by remember(state.resizeEventId) { mutableStateOf<ResizeDraft?>(null) }
    // Zapis wrócił z tymi samymi godzinami, które trzymamy w palcu — dane
    // dogoniły ekran, więc oddajemy rysowanie danym.
    LaunchedEffect(armedEvent?.startAt, armedEvent?.endAt, draft) {
        val live = draft ?: return@LaunchedEffect
        val event = armedEvent ?: return@LaunchedEffect
        if (event.startMillis() == live.startMillis && event.endMillis() == live.endMillis) {
            draft = null
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        DayHeaders(days = days, today = today)
        if (allDay.isNotEmpty()) {
            AllDayStrip(events = allDay, onOpenEvent = onOpenEvent)
        }
        if (armedEvent != null) {
            ResizeBar(
                startMillis = draft?.startMillis ?: armedEvent.startMillis(),
                endMillis = draft?.endMillis ?: armedEvent.endMillis(),
                onDone = onCancelResize,
            )
        }
        Row(modifier = Modifier.fillMaxWidth().verticalScroll(scroll)) {
            HourGutter()
            days.forEach { day ->
                DayColumn(
                    day = day,
                    events = byDay[day].orEmpty(),
                    busy = busyByDay[day].orEmpty(),
                    isToday = day == today,
                    nowMillis = state.now,
                    resizeEventId = state.resizeEventId,
                    draft = draft,
                    onOpenEvent = onOpenEvent,
                    onCreateAt = onCreateAt,
                    onArmResize = onArmResize,
                    onDraftChange = { draft = it },
                    onCommitResize = onResize,
                    onCancelResize = onCancelResize,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Pasek nad siatką z godzinami spod palca. Kolumna dnia w tygodniu ma ~47 dp,
 * więc „12:00 – 16:00" nie zmieściłoby się przy samym kafelku.
 */
@Composable
private fun ResizeBar(startMillis: Long?, endMillis: Long?, onDone: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(start = 10.dp, end = 2.dp),
    ) {
        Text(
            text = if (startMillis != null && endMillis != null) {
                "${formatHour(startMillis)} – ${formatHour(endMillis)} · " +
                    durationLabel(endMillis - startMillis)
            } else {
                "Przeciągnij kropki, aby zmienić godziny"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDone) { Text("Gotowe") }
    }
}

/** „1 godz. 30 min" — długość spotkania obok jego godzin. */
private fun durationLabel(millis: Long): String {
    val minutes = (millis / 60_000L).toInt().coerceAtLeast(0)
    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours > 0 && rest > 0 -> "$hours godz. $rest min"
        hours > 0 -> "$hours godz."
        else -> "$rest min"
    }
}

@Composable
private fun DayHeaders(days: List<LocalDate>, today: LocalDate) {
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp)) {
        Box(modifier = Modifier.width(GUTTER_WIDTH))
        days.forEach { day ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = WEEKDAY_LABELS[(day.dayOfWeek.value + 6) % 7],
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${day.dayOfMonth}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (day == today) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
            }
        }
    }
}

/** Pasek wydarzeń całodniowych nad siatką — tak samo jak w panelu. */
@Composable
private fun AllDayStrip(events: List<CalendarEvent>, onOpenEvent: (CalendarEvent) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = GUTTER_WIDTH, end = 4.dp, bottom = 4.dp),
    ) {
        events.take(3).forEach { event ->
            val color = eventColor(event)
            Surface(
                onClick = { onOpenEvent(event) },
                shape = RoundedCornerShape(5.dp),
                color = color,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.labelSmall,
                    color = onColor(color),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                )
            }
        }
        if (events.size > 3) {
            Text(
                text = "+${events.size - 3}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HourGutter() {
    Column(modifier = Modifier.width(GUTTER_WIDTH)) {
        for (hour in 0 until 24) {
            Box(modifier = Modifier.height(HOUR_HEIGHT)) {
                Text(
                    text = "%02d".format(hour),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth().padding(end = 3.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayColumn(
    day: LocalDate,
    events: List<CalendarEvent>,
    busy: List<BusySpan>,
    isToday: Boolean,
    nowMillis: Long,
    resizeEventId: String?,
    draft: ResizeDraft?,
    onOpenEvent: (CalendarEvent) -> Unit,
    onCreateAt: (LocalDate, Int) -> Unit,
    onArmResize: (CalendarEvent) -> Unit,
    onDraftChange: (ResizeDraft) -> Unit,
    onCommitResize: (String, Long, Long) -> Unit,
    onCancelResize: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val positioned = remember(events, day) { layoutDay(events, day) }
    val dayStart = remember(day) { dateToMillis(day) }
    val dayEnd = remember(day) { dateToMillis(day.plusDays(1)) }
    val handleColor = MaterialTheme.colorScheme.onSurface

    /** Minuty kafelka: spod palca, jeśli właśnie go ciągniemy — inaczej z danych. */
    fun rangeOf(item: Positioned): Pair<Int, Int> {
        val live = draft?.takeIf { it.eventId == item.event.id }
            ?: return item.startMin to item.endMin
        return ((live.startMillis - dayStart) / 60_000L).toInt() to
            ((live.endMillis - dayStart) / 60_000L).toInt()
    }

    BoxWithConstraints(modifier = modifier.height(HOUR_HEIGHT * 24)) {
        val columnWidth = maxWidth
        // Godzinowe linie siatki + puste miejsce, w które można dotknąć,
        // żeby dodać wydarzenie o tej godzinie.
        Column(modifier = Modifier.fillMaxSize()) {
            for (hour in 0 until 24) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(HOUR_HEIGHT)
                        .clickable { onCreateAt(day, hour) },
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    )
                }
            }
        }
        // Prywatna zajętość — pod wydarzeniami i BEZ przechwytywania dotyku:
        // to tylko informacja „tu nie planuj", a nie kafelek do klikania.
        // Napisu nie ma; przy 47 dp kolumny tygodnia i tak by się nie zmieścił,
        // a treści prywatnego wpisu nie znamy (i nie chcemy znać).
        busy.forEach { span ->
            Box(
                modifier = Modifier
                    .offset(y = HOUR_HEIGHT * (span.startMin / 60f))
                    .fillMaxWidth()
                    .height((HOUR_HEIGHT * ((span.endMin - span.startMin) / 60f)).coerceAtLeast(3.dp))
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))
                    .semantics { contentDescription = "Zajęte — prywatny kalendarz" },
            )
        }
        // Przy uzbrojonych kropkach dotknięcie pustej siatki chowa uchwyty,
        // zamiast zakładać nowy termin pod palcem.
        if (resizeEventId != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { onCancelResize() } },
            )
        }
        positioned.forEach { item ->
            val color = eventColor(item.event)
            val armed = item.event.id == resizeEventId
            val (startMin, endMin) = rangeOf(item)
            val slotWidth = columnWidth / item.columns
            val height = (HOUR_HEIGHT * (endMin - startMin) / 60f).coerceAtLeast(16.dp)
            Box(
                modifier = Modifier
                    .zIndex(if (armed) 1f else 0f)
                    .offset(x = slotWidth * item.column, y = HOUR_HEIGHT * startMin / 60f)
                    .width(slotWidth)
                    .height(height)
                    .padding(end = 1.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(color)
                    .then(
                        if (armed) {
                            Modifier.border(2.dp, handleColor, RoundedCornerShape(5.dp))
                        } else {
                            Modifier
                        },
                    )
                    .combinedClickable(
                        onClick = { onOpenEvent(item.event) },
                        onLongClickLabel = "Rozciągnij godziny",
                        // Wydarzenie przyciętego dnia (wielodniowe) kropkami się
                        // nie ustawi — dla niego przytrzymanie otwiera kartę.
                        onLongClick = {
                            if (item.clipped) onOpenEvent(item.event) else onArmResize(item.event)
                        },
                    ),
            ) {
                Column(modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)) {
                    Text(
                        text = item.event.title,
                        style = MaterialTheme.typography.labelSmall,
                        color = onColor(color),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (height > 34.dp) {
                        Text(
                            text = if (armed) {
                                "${formatHour(dayStart + startMin * 60_000L)}–" +
                                    formatHour(dayStart + endMin * 60_000L)
                            } else {
                                formatHour(item.event.startAt)
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = onColor(color).copy(alpha = 0.85f),
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        // Kropki uzbrojonego wydarzenia — rysowane po wszystkich kafelkach,
        // żeby sąsiad z nakładającej się kolumny ich nie przykrył.
        val armedItem = positioned.firstOrNull { it.event.id == resizeEventId && !it.clipped }
        if (armedItem != null) {
            val eventId = armedItem.event.id
            val (startMin, endMin) = rangeOf(armedItem)
            val slotWidth = columnWidth / armedItem.columns
            val left = slotWidth * armedItem.column
            val startMillis by rememberUpdatedState(dayStart + startMin * 60_000L)
            val endMillis by rememberUpdatedState(dayStart + endMin * 60_000L)
            // Godziny z chwili złapania kropki — przyrost liczymy od nich,
            // więc drobne drgania palca nie kumulują się w minuty.
            var anchorStart by remember(eventId) { mutableStateOf(0L) }
            var anchorEnd by remember(eventId) { mutableStateOf(0L) }

            ResizeHandle(
                centerX = left,
                centerY = HOUR_HEIGHT * startMin / 60f,
                color = handleColor,
                label = "Początek wydarzenia",
                onGrab = {
                    anchorStart = startMillis
                    anchorEnd = endMillis
                },
                onDeltaMinutes = { minutes ->
                    val next = (anchorStart + minutes * 60_000L)
                        .coerceIn(dayStart, anchorEnd - MIN_EVENT_MILLIS)
                    onDraftChange(ResizeDraft(eventId, next, anchorEnd))
                },
                onRelease = { onCommitResize(eventId, startMillis, endMillis) },
            )
            ResizeHandle(
                centerX = left + slotWidth,
                centerY = HOUR_HEIGHT * endMin / 60f,
                color = handleColor,
                label = "Koniec wydarzenia",
                onGrab = {
                    anchorStart = startMillis
                    anchorEnd = endMillis
                },
                onDeltaMinutes = { minutes ->
                    val next = (anchorEnd + minutes * 60_000L)
                        .coerceIn(anchorStart + MIN_EVENT_MILLIS, dayEnd)
                    onDraftChange(ResizeDraft(eventId, anchorStart, next))
                },
                onRelease = { onCommitResize(eventId, startMillis, endMillis) },
            )
        }
        if (isToday) {
            val minutes = millisToDateTime(nowMillis).let { it.hour * 60 + it.minute }
            Box(
                modifier = Modifier
                    .offset(y = HOUR_HEIGHT * minutes / 60f)
                    .fillMaxWidth()
                    .height(1.5.dp)
                    .background(MaterialTheme.colorScheme.primary),
            )
            Box(
                modifier = Modifier
                    .offset(y = HOUR_HEIGHT * minutes / 60f - 3.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}

/**
 * Kropka do rozciągania: osobno pole dotyku, osobno sama kropka. Pole dotyku
 * musi zostać w kolumnie (poza jej granicami Compose nie dowozi zdarzeń),
 * a kropka siedzi dokładnie w rogu kafelka — także wtedy, gdy wychodzi na
 * marginesie z godzinami.
 *
 * [onDeltaMinutes] dostaje przyrost od złapania kropki, zaokrąglony do
 * [SNAP_MINUTES] — nie kolejne piksele.
 */
@Composable
private fun ResizeHandle(
    centerX: Dp,
    centerY: Dp,
    color: Color,
    label: String,
    onGrab: () -> Unit,
    onDeltaMinutes: (Int) -> Unit,
    onRelease: () -> Unit,
) {
    // Gest żyje dłużej niż jedna rekompozycja, więc czyta bieżące lambdy,
    // a nie te sprzed przeciągnięcia.
    val grab by rememberUpdatedState(onGrab)
    val delta by rememberUpdatedState(onDeltaMinutes)
    val release by rememberUpdatedState(onRelease)

    Box(
        modifier = Modifier
            .zIndex(2f)
            .offset(
                x = (centerX - HANDLE_TOUCH / 2).coerceAtLeast(0.dp),
                y = (centerY - HANDLE_TOUCH / 2).coerceAtLeast(0.dp),
            )
            .size(HANDLE_TOUCH)
            .semantics { contentDescription = label }
            .pointerInput(Unit) {
                val minutesPerPx = 60f / HOUR_HEIGHT.toPx()
                var moved = 0f
                detectDragGestures(
                    onDragStart = {
                        moved = 0f
                        grab()
                    },
                    onDragEnd = { release() },
                    onDragCancel = { release() },
                ) { change, amount ->
                    change.consume()
                    moved += amount.y
                    delta(snapMinutes(moved * minutesPerPx))
                }
            },
    )
    Box(
        modifier = Modifier
            .zIndex(2f)
            .offset(x = centerX - HANDLE_DOT / 2, y = centerY - HANDLE_DOT / 2)
            .size(HANDLE_DOT)
            .clip(CircleShape)
            .background(color)
            .border(2.5.dp, MaterialTheme.colorScheme.surface, CircleShape),
    )
}

private fun snapMinutes(minutes: Float): Int =
    (minutes / SNAP_MINUTES).roundToInt() * SNAP_MINUTES

/** Wydarzenie z policzonym miejscem w siatce dnia. */
private data class Positioned(
    val event: CalendarEvent,
    val startMin: Int,
    val endMin: Int,
    val column: Int,
    val columns: Int,
    /** Przycięte do granic dnia — czyli wydarzenie wchodzi w sąsiedni dzień. */
    val clipped: Boolean,
)

/**
 * Rozkłada nakładające się wydarzenia dnia na kolumny — port `layoutDay`
 * z panelu. Wydarzenie wielodniowe przycinamy do granic tego dnia.
 */
private fun layoutDay(events: List<CalendarEvent>, day: LocalDate): List<Positioned> {
    val dayStart = dateToMillis(day)
    val dayEnd = dateToMillis(day.plusDays(1))
    val items = events.mapNotNull { event ->
        val start = event.startMillis() ?: return@mapNotNull null
        val end = event.endMillis() ?: return@mapNotNull null
        val from = maxOf(start, dayStart)
        val to = minOf(end, dayEnd)
        if (to <= from) return@mapNotNull null
        val startMin = ((from - dayStart) / 60_000L).toInt()
        val endMin = ((to - dayStart) / 60_000L).toInt().coerceAtLeast(startMin + 15)
        Slot(event, startMin, endMin, clipped = start < dayStart || end > dayEnd)
    }.sortedWith(compareBy({ it.startMin }, { it.endMin }))

    val out = mutableListOf<Positioned>()
    var cluster = mutableListOf<Slot>()
    var clusterEnd = -1

    fun flush() {
        if (cluster.isEmpty()) return
        val columnEnds = mutableListOf<Int>()
        val placed = cluster.map { item ->
            var col = 0
            while (col < columnEnds.size && columnEnds[col] > item.startMin) col++
            if (col == columnEnds.size) columnEnds.add(item.endMin) else columnEnds[col] = item.endMin
            item to col
        }
        val columns = columnEnds.size
        placed.forEach { (item, col) ->
            out += Positioned(item.event, item.startMin, item.endMin, col, columns, item.clipped)
        }
        cluster = mutableListOf()
        clusterEnd = -1
    }

    for (item in items) {
        if (cluster.isNotEmpty() && item.startMin >= clusterEnd) flush()
        cluster.add(item)
        clusterEnd = maxOf(clusterEnd, item.endMin)
    }
    flush()
    return out
}

/** Wydarzenie przycięte do dnia, zanim dostanie kolumnę. */
private data class Slot(
    val event: CalendarEvent,
    val startMin: Int,
    val endMin: Int,
    val clipped: Boolean,
)
