package com.ekotak.teamtalk.presentation.service

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ekotak.teamtalk.domain.model.ServiceJob
import com.ekotak.teamtalk.domain.model.ServiceJobStatus
import com.ekotak.teamtalk.presentation.crm.parseIsoMillis
import com.ekotak.teamtalk.presentation.theme.Red600
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/**
 * Kalendarz miesiąca modułu Serwis — odwzorowanie `ServiceCalendar.tsx`.
 *
 * Panel wpisuje w komórkę dnia chipy z nazwą klienta; na 360 dp mieszczą się
 * tylko paski koloru, więc treść dnia przenosimy pod siatkę: dotknięcie dnia
 * rozwija listę zleceń i przeglądów z tą datą. Kolory pasków są te same co w
 * panelu (status zlecenia, światło przeglądu), a pozycje bez terminu się nie
 * pojawiają — tak samo jak tam.
 */
@Composable
fun ServiceCalendar(
    state: ServiceViewModel.UiState,
    onOpenJob: (String) -> Unit,
    onOpenCard: (String) -> Unit,
) {
    val zone = remember { ZoneId.systemDefault() }
    val today = remember(state.now) { Instant.ofEpochMilli(state.now).atZone(zone).toLocalDate() }
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    var selected by remember { mutableStateOf<LocalDate?>(null) }

    val jobsByDay = remember(state.jobs, state.showRegular) {
        if (state.isPrzeglad && !state.showRegular) {
            emptyMap()
        } else {
            state.jobs
                .mapNotNull { job -> parseIsoMillis(job.scheduledAt)?.let { dayOf(it, zone) to job } }
                .groupBy({ it.first }, { it.second })
        }
    }
    val warrantyByDay = remember(state.rows, state.showWarranty, state.now) {
        if (!state.isPrzeglad || !state.showWarranty) {
            emptyMap()
        } else {
            val cards = state.rows.filterIsInstance<ServiceRow.Warranty>().map { it.view.card }
            flattenWarranty(cards, state.now)
                .mapNotNull { item -> parseIsoMillis(item.date)?.let { dayOf(it, zone) to item } }
                .groupBy({ it.first }, { it.second })
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        MonthHeader(
            month = month,
            onPrev = { month = month.minusMonths(1) },
            onNext = { month = month.plusMonths(1) },
            onToday = {
                month = YearMonth.from(today)
                selected = today
            },
        )
        WeekdayHeader()
        MonthGrid(
            month = month,
            today = today,
            selected = selected,
            jobsByDay = jobsByDay,
            warrantyByDay = warrantyByDay,
            onSelect = { selected = if (selected == it) null else it },
        )
        DayList(
            day = selected,
            jobs = selected?.let { jobsByDay[it] }.orEmpty(),
            warranty = selected?.let { warrantyByDay[it] }.orEmpty(),
            state = state,
            onOpenJob = onOpenJob,
            onOpenCard = onOpenCard,
        )
    }
}

private fun dayOf(millis: Long, zone: ZoneId): LocalDate =
    Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

@Composable
private fun MonthHeader(
    month: YearMonth,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    val pl = remember { Locale("pl", "PL") }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Poprzedni miesiąc")
        }
        Text(
            text = "${month.month.getDisplayName(TextStyle.FULL_STANDALONE, pl)
                .replaceFirstChar { it.titlecase(pl) }} ${month.year}",
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Następny miesiąc")
        }
        TextButton(onClick = onToday) { Text("Dziś") }
    }
}

@Composable
private fun WeekdayHeader() {
    val labels = listOf("pon", "wt", "śr", "czw", "pt", "sob", "niedz")
    Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        labels.forEach { label ->
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
    jobsByDay: Map<LocalDate, List<ServiceJob>>,
    warrantyByDay: Map<LocalDate, List<WarrantyListItem>>,
    onSelect: (LocalDate) -> Unit,
) {
    // Siatka 6×7 od poniedziałku — jak w panelu (offset liczony od `getDay()`).
    val first = month.atDay(1)
    val offset = (first.dayOfWeek.value + 6) % 7
    val start = first.minusDays(offset.toLong())
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
                        jobs = jobsByDay[day].orEmpty(),
                        warranty = warrantyByDay[day].orEmpty(),
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
    jobs: List<ServiceJob>,
    warranty: List<WarrantyListItem>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.aspectRatio(0.85f).padding(1.dp)) {
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
                jobs.take(3).forEach { job -> Bar(jobBarColor(job)) }
                warranty.take(3 - minOf(jobs.size, 3)).forEach { item ->
                    Bar(lightColor(item.light), outlined = item.light == WarrantyLight.FUTURE)
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
                color = if (outlined) MaterialTheme.colorScheme.outline else Color.Transparent,
                shape = RoundedCornerShape(2.dp),
            ),
    )
}

/** Kolory pasków zleceń = `SERVICE_STATUS_COLOR` z panelu; po SLA czerwień. */
private fun jobBarColor(job: ServiceJob): Color = when {
    job.slaBreached && job.status != ServiceJobStatus.DONE -> Red600
    job.status == ServiceJobStatus.NEW -> Color(0xFFFFA657)
    job.status == ServiceJobStatus.IN_PROGRESS -> Color(0xFF4AA3FF)
    else -> Color(0xFF17B3A3)
}

/** Treść wybranego dnia — na telefonie zastępuje chipy z komórek panelu. */
@Composable
private fun DayList(
    day: LocalDate?,
    jobs: List<ServiceJob>,
    warranty: List<WarrantyListItem>,
    state: ServiceViewModel.UiState,
    onOpenJob: (String) -> Unit,
    onOpenCard: (String) -> Unit,
) {
    if (day == null) {
        Text(
            text = "Dotknij dnia, aby zobaczyć zaplanowane wizyty.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            textAlign = TextAlign.Center,
        )
        return
    }
    val pl = remember { Locale("pl", "PL") }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "${day.dayOfMonth} ${day.month.getDisplayName(TextStyle.FULL, pl)} · " +
                "${jobs.size + warranty.size} poz.",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
        )
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        ) {
            items(jobs, key = { "job:${it.id}" }) { job ->
                val client = job.clientId?.let { state.clients[it] }
                DayRow(
                    color = jobBarColor(job),
                    outlined = false,
                    text = jobRowLabel(job, client?.label, client?.city),
                    onClick = { onOpenJob(job.id) },
                )
            }
            items(warranty, key = { it.key }) { item ->
                DayRow(
                    color = lightColor(item.light),
                    outlined = item.light == WarrantyLight.FUTURE,
                    text = "${item.card.name} · przegląd #${item.ordinal}",
                    onClick = { onOpenCard(item.card.id) },
                )
            }
        }
    }
}

@Composable
private fun DayRow(color: Color, outlined: Boolean, text: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .background(if (outlined) Color.Transparent else color, CircleShape)
                    .border(
                        width = if (outlined) 1.5.dp else 0.dp,
                        color = if (outlined) MaterialTheme.colorScheme.outline else Color.Transparent,
                        shape = CircleShape,
                    ),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
