package com.ekotak.teamtalk.presentation.leave

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ekotak.teamtalk.domain.leave.isPolishHoliday
import com.ekotak.teamtalk.domain.leave.isWeekend
import com.ekotak.teamtalk.domain.model.LeaveStatus
import java.time.LocalDate
import java.time.YearMonth

/**
 * Kalendarz urlopowy w czterech skalach — te same, które ma panel (ustalenie
 * 2026-09-06). Każda odpowiada na inne pytanie, więc każda ma własny układ:
 *
 *  • tydzień  — czy ktoś z brygady już nie wziął tych dni (siedem dużych kafli),
 *  • miesiąc  — codzienne planowanie i zaznaczanie zakresu,
 *  • kwartał  — urlop na przełomie miesięcy; komórki spłaszczone, ale nadal
 *    na tyle szerokie, żeby trafił w nie palec,
 *  • rok      — czy urlop nie zebrał się w jednym kwartale; numerów dni tu nie
 *    ma, bo na tej skali i tak są nieczytelne, a pytanie jest inne.
 *
 * Zakres zaznacza się DWOMA DOTKNIĘCIAMI (pierwszy dzień, ostatni dzień) —
 * przeciąganie po siatce gryzłoby się z przewijaniem ekranu.
 */

/** Co dzieje się danego dnia — tyle wystarczy, żeby narysować komórkę. */
private data class DayMarks(
    val mineApproved: Boolean = false,
    val minePending: Boolean = false,
    val teamCount: Int = 0,
)

/** Mapa dzień → oznaczenia, budowana raz na widoczny zakres. */
private fun buildMarks(
    state: LeaveViewModel.UiState,
    from: LocalDate,
    to: LocalDate,
): Map<LocalDate, DayMarks> {
    val marks = HashMap<LocalDate, DayMarks>()
    fun edit(day: LocalDate, change: (DayMarks) -> DayMarks) {
        if (day.isBefore(from) || day.isAfter(to)) return
        marks[day] = change(marks[day] ?: DayMarks())
    }
    for (request in state.activeRequests) {
        var day = maxOf(request.start, from)
        val last = minOf(request.end, to)
        while (!day.isAfter(last)) {
            if (request.status == LeaveStatus.ZATWIERDZONY) {
                edit(day) { it.copy(mineApproved = true) }
            } else {
                edit(day) { it.copy(minePending = true) }
            }
            day = day.plusDays(1)
        }
    }
    for (absence in state.absences) {
        var day = maxOf(absence.start, from)
        val last = minOf(absence.end, to)
        while (!day.isAfter(last)) {
            edit(day) { it.copy(teamCount = it.teamCount + 1) }
            day = day.plusDays(1)
        }
    }
    return marks
}

@Composable
fun LeaveCalendar(
    state: LeaveViewModel.UiState,
    onSelectDay: (LocalDate) -> Unit,
    onOpenMonth: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state.scale) {
        LeaveScale.WEEK -> WeekStrip(state, onSelectDay, modifier)
        LeaveScale.MONTH -> MonthGrid(state, onSelectDay, modifier)
        LeaveScale.QUARTER -> QuarterGrid(state, onSelectDay, modifier)
        LeaveScale.YEAR -> YearGrid(state, onOpenMonth, modifier)
    }
}

// ── Miesiąc ──────────────────────────────────────────────────────────────────

@Composable
private fun MonthGrid(
    state: LeaveViewModel.UiState,
    onSelectDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val month = YearMonth.from(state.anchor)
    val first = month.atDay(1)
    val gridStart = startOfWeek(first)
    // Sześć tygodni pokrywa każdy układ miesiąca — panel rysuje tak samo.
    val days = remember(month) { (0 until 42).map { gridStart.plusDays(it.toLong()) } }
    val marks = remember(state.myRequests, state.absences, month) {
        buildMarks(state, gridStart, gridStart.plusDays(41))
    }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        WeekdayHeader()
        days.chunked(7).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                week.forEach { day ->
                    DayCell(
                        day = day,
                        marks = marks[day] ?: DayMarks(),
                        inRange = YearMonth.from(day) == month,
                        selected = state.selection?.covers(day) == true,
                        onClick = { onSelectDay(day) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun WeekdayHeader() {
    Row {
        WEEKDAY_SHORT.forEach { label ->
            Text(
                text = label,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Pole dnia. Kolejność ważna: zaznaczenie przykrywa wszystko (właśnie je
 * ustawiamy), potem mój urlop, potem wniosek w toku. Cudza nieobecność jest
 * tylko kreską u dołu — nie może zasłonić własnego urlopu.
 */
@Composable
private fun DayCell(
    day: LocalDate,
    marks: DayMarks,
    inRange: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val holiday = isPolishHoliday(day)
    val nonWorking = holiday || isWeekend(day)
    val background = when {
        selected -> LeaveMine
        marks.mineApproved -> LeaveMine.copy(alpha = 0.32f)
        marks.minePending -> LeavePending.copy(alpha = 0.34f)
        nonWorking -> scheme.onSurface.copy(alpha = 0.06f)
        else -> scheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val textColor = when {
        selected -> Color(0xFF080808)
        !inRange -> scheme.onSurfaceVariant.copy(alpha = 0.4f)
        holiday -> MaterialTheme.colorScheme.error
        nonWorking -> scheme.onSurfaceVariant
        else -> scheme.onSurface
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .then(
                if (day == LocalDate.now()) {
                    Modifier.border(1.5.dp, scheme.onSurface, RoundedCornerShape(8.dp))
                } else {
                    Modifier
                },
            )
            .clickable(onClick = onClick),
    ) {
        Text(
            text = day.dayOfMonth.toString(),
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = textColor,
        )
        if (marks.teamCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 3.dp)
                    .fillMaxWidth(0.55f)
                    .height(2.5.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(LeaveTeam.copy(alpha = if (marks.teamCount > 1) 1f else 0.7f)),
            )
        }
    }
}

// ── Tydzień ──────────────────────────────────────────────────────────────────

@Composable
private fun WeekStrip(
    state: LeaveViewModel.UiState,
    onSelectDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val start = startOfWeek(state.anchor)
    val days = remember(start) { (0 until 7).map { start.plusDays(it.toLong()) } }
    val marks = remember(state.myRequests, state.absences, start) {
        buildMarks(state, start, start.plusDays(6))
    }

    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        days.forEachIndexed { index, day ->
            val mark = marks[day] ?: DayMarks()
            val selected = state.selection?.covers(day) == true
            val scheme = MaterialTheme.colorScheme
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(
                        when {
                            selected -> LeaveMine.copy(alpha = 0.26f)
                            isWeekend(day) || isPolishHoliday(day) -> scheme.onSurface.copy(alpha = 0.06f)
                            else -> scheme.surfaceVariant.copy(alpha = 0.5f)
                        },
                    )
                    .then(
                        if (day == LocalDate.now()) {
                            Modifier.border(1.5.dp, scheme.onSurface, RoundedCornerShape(9.dp))
                        } else {
                            Modifier
                        },
                    )
                    .clickable { onSelectDay(day) }
                    .padding(vertical = 6.dp),
            ) {
                Text(
                    text = WEEKDAY_SHORT[index],
                    fontSize = 9.sp,
                    color = scheme.onSurfaceVariant,
                )
                Text(
                    text = day.dayOfMonth.toString(),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isPolishHoliday(day)) scheme.error else scheme.onSurface,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            when {
                                mark.mineApproved -> LeaveMine
                                mark.minePending -> LeavePending
                                mark.teamCount > 0 -> LeaveTeam.copy(alpha = 0.65f)
                                else -> Color.Transparent
                            },
                        ),
                )
            }
        }
    }
}

// ── Kwartał ──────────────────────────────────────────────────────────────────

@Composable
private fun QuarterGrid(
    state: LeaveViewModel.UiState,
    onSelectDay: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val firstMonth = YearMonth.of(state.anchor.year, ((state.anchor.monthValue - 1) / 3) * 3 + 1)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        (0 until 3).forEach { offset ->
            val month = firstMonth.plusMonths(offset.toLong())
            MiniMonth(
                state = state,
                month = month,
                cellHeight = 23.dp,
                showWeekdays = offset == 0,
                showDayNumbers = true,
                onClickDay = onSelectDay,
                onClickMonth = null,
            )
        }
    }
}

// ── Rok ──────────────────────────────────────────────────────────────────────

@Composable
private fun YearGrid(
    state: LeaveViewModel.UiState,
    onOpenMonth: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val months = remember(state.anchor.year) {
        (1..12).map { YearMonth.of(state.anchor.year, it) }
    }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        months.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { month ->
                    Box(modifier = Modifier.weight(1f)) {
                        MiniMonth(
                            state = state,
                            month = month,
                            cellHeight = 9.dp,
                            showWeekdays = false,
                            showDayNumbers = false,
                            onClickDay = null,
                            onClickMonth = { onOpenMonth(month.atDay(1)) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Mini-miesiąc: siedem kolumn, pięć albo sześć wierszy. W kwartale komórki są
 * spłaszczone (szerokość zostaje, bo w nią trafia palec), a w roku schodzą do
 * samych kwadracików — tam liczy się rozkład, nie konkretny dzień.
 */
@Composable
private fun MiniMonth(
    state: LeaveViewModel.UiState,
    month: YearMonth,
    cellHeight: androidx.compose.ui.unit.Dp,
    showWeekdays: Boolean,
    showDayNumbers: Boolean,
    onClickDay: ((LocalDate) -> Unit)?,
    onClickMonth: (() -> Unit)?,
) {
    val gridStart = startOfWeek(month.atDay(1))
    val weeks = remember(month) {
        val last = month.atEndOfMonth()
        val total = ((last.toEpochDay() - gridStart.toEpochDay()) / 7 + 1).toInt()
        total
    }
    val marks = remember(state.myRequests, state.absences, month) {
        buildMarks(state, gridStart, gridStart.plusDays(weeks * 7L - 1))
    }
    val scheme = MaterialTheme.colorScheme

    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = if (showDayNumbers) monthLabel(month.atDay(1)) else monthShort(month.monthValue),
                fontSize = if (showDayNumbers) 12.sp else 10.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onSurface,
                modifier = Modifier
                    .then(if (onClickMonth != null) Modifier.clickable { onClickMonth() } else Modifier),
            )
        }
        if (showWeekdays) WeekdayHeader()
        (0 until weeks).forEach { week ->
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                (0 until 7).forEach { index ->
                    val day = gridStart.plusDays(week * 7L + index)
                    val inMonth = YearMonth.from(day) == month
                    val mark = marks[day] ?: DayMarks()
                    val selected = state.selection?.covers(day) == true && inMonth
                    val background = when {
                        !inMonth -> Color.Transparent
                        selected -> LeaveMine
                        mark.mineApproved -> LeaveMine.copy(alpha = if (showDayNumbers) 0.32f else 1f)
                        mark.minePending -> LeavePending.copy(alpha = if (showDayNumbers) 0.34f else 1f)
                        isPolishHoliday(day) -> scheme.error.copy(alpha = 0.45f)
                        isWeekend(day) -> scheme.onSurface.copy(alpha = 0.06f)
                        else -> scheme.surfaceVariant.copy(alpha = 0.5f)
                    }
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .weight(1f)
                            .height(cellHeight)
                            .clip(RoundedCornerShape(if (showDayNumbers) 4.dp else 2.dp))
                            .background(background)
                            .then(
                                if (inMonth && day == LocalDate.now()) {
                                    Modifier.border(1.dp, scheme.onSurface, RoundedCornerShape(3.dp))
                                } else {
                                    Modifier
                                },
                            )
                            .then(
                                if (inMonth && onClickDay != null) {
                                    Modifier.clickable { onClickDay(day) }
                                } else if (onClickMonth != null) {
                                    Modifier.clickable { onClickMonth() }
                                } else {
                                    Modifier
                                },
                            ),
                    ) {
                        if (showDayNumbers && inMonth) {
                            Text(
                                text = day.dayOfMonth.toString(),
                                fontSize = 10.sp,
                                color = if (selected) Color(0xFF080808) else scheme.onSurface,
                            )
                        }
                        if (inMonth && mark.teamCount > 0 && !selected) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth(0.7f)
                                    .height(2.dp)
                                    .background(LeaveTeam),
                            )
                        }
                    }
                }
            }
        }
    }
}
