package com.ekotak.teamtalk.presentation.leave

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ekotak.teamtalk.domain.model.LeaveRequest
import com.ekotak.teamtalk.domain.model.LeaveStatus
import com.ekotak.teamtalk.presentation.theme.SyncBlue
import java.time.LocalDate
import java.time.YearMonth

/**
 * Zakładka „Zespół": skrzynka zwierzchnika i oś czasu nieobecności.
 *
 * W panelu to dwie kolumny obok siebie; na 360 dp jadą pod sobą, bo odpowiadają
 * na dwa różne pytania w tej samej kolejności: najpierw „co czeka na moją
 * decyzję", potem „kto i kiedy będzie poza firmą".
 *
 * Zawartość zależy od RELACJI, nie od roli: skrzynka niesie tylko podwładnych
 * pytającego (plus osoby zastępowane przy nieobecności ich zwierzchnika), więc
 * ekran nie jest podglądem urlopów całej firmy.
 */
@Composable
fun LeaveTeamSection(
    state: LeaveViewModel.UiState,
    onDecide: (String, Boolean) -> Unit,
    onSetGroup: (LeaveViewModel.LeaveGroup) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        if (state.inbox.isEmpty()) {
            Text(
                text = "Nikt Ci nie podlega albo nikt nie złożył wniosku. " +
                    "Nieobecności zespołu widać niżej.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.toDecide.isNotEmpty()) {
            SectionLabel("Do decyzji (${state.toDecide.size})")
            state.toDecide.forEach { request ->
                InboxRow(
                    request = request,
                    warning = state.coverageWarning(request),
                    onDecide = onDecide,
                )
            }
        }

        if (state.inboxContext.isNotEmpty()) {
            SectionLabel("Pozostałe wnioski zespołu")
            state.inboxContext.forEach { request ->
                InboxRow(request = request, warning = null, onDecide = onDecide)
            }
        }

        SectionLabel("Nieobecności · ${monthLabel(state.anchor)}")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LeaveViewModel.LeaveGroup.entries.forEach { group ->
                FilterChip(
                    selected = group == state.group,
                    onClick = { onSetGroup(group) },
                    label = { Text(group.label) },
                )
            }
        }
        AbsenceTimeline(state)
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp),
    )
}

/**
 * Wiersz skrzynki. Wnioski do mojej decyzji dostają przyciski; pozostałe —
 * plakietkę „Czeka na: …", żeby było wiadomo, czyja to sprawa. Ostrzeżenie
 * o obsadzie stoi PRZY przycisku, a nie pod listą.
 */
@Composable
private fun InboxRow(
    request: LeaveRequest,
    warning: String?,
    onDecide: (String, Boolean) -> Unit,
) {
    val accent = if (request.pendingSync) SyncBlue else statusColor(request.status)
    Surface(
        shape = RoundedCornerShape(11.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(3.dp).height(38.dp).background(accent))
                Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(
                        text = request.employeeName ?: "—",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = buildString {
                            append(request.type.label)
                            append(" · ")
                            append(rangeLabel(request.start, request.end))
                            append(" · ")
                            append(daysLabel(request.workingDays))
                            roleShort(request.employeeRole).takeIf { it.isNotBlank() }?.let {
                                append(" · ")
                                append(it)
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                when {
                    request.pendingSync -> StatusPill("W kolejce", SyncBlue)
                    request.status != LeaveStatus.OCZEKUJE -> StatusPill(request.status.label, accent)
                    !request.canDecide -> StatusPill(
                        text = "Czeka na: ${request.awaitingName ?: "zwierzchnika"}" +
                            if (request.awaitingIsBackup) " (backup)" else "",
                        color = LeavePending,
                    )
                    else -> Unit
                }
            }
            warning?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = LeaveAccent,
                    modifier = Modifier.padding(start = 13.dp, top = 2.dp, end = 10.dp),
                )
            }
            if (request.status == LeaveStatus.OCZEKUJE && request.canDecide && !request.pendingSync) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(start = 13.dp, end = 10.dp, top = 6.dp),
                ) {
                    Button(
                        onClick = { onDecide(request.id, true) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = LeaveMine,
                            contentColor = Color(0xFF080808),
                        ),
                        modifier = Modifier.weight(1f),
                    ) { Text("Zatwierdź") }
                    OutlinedButton(
                        onClick = { onDecide(request.id, false) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Odrzuć", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusPill(text: String, color: Color) {
    Text(
        text = text,
        fontSize = 10.sp,
        color = color,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.width(96.dp).padding(end = 10.dp),
    )
}

/** Jedna nieobecność na torze miesiąca — pozycje w dniach, nie w pikselach. */
private data class TimelineBar(
    val from: Int,
    val to: Int,
    val pending: Boolean,
    val mine: Boolean,
)

private data class TimelineRow(
    val name: String,
    val role: String?,
    val bars: List<TimelineBar>,
)

/**
 * Oś czasu miesiąca: wiersz na osobę, pasek na każdą nieobecność. Odpowiada na
 * jedno pytanie — czy w którymś tygodniu brygada się nie rozjedzie — więc
 * pionowa kreska „dziś" i podpis o najciaśniejszym tygodniu są tu ważniejsze
 * niż dokładne daty, które i tak widać w kalendarzu.
 */
@Composable
private fun AbsenceTimeline(state: LeaveViewModel.UiState) {
    val month = YearMonth.from(state.anchor)
    val days = month.lengthOfMonth()

    val rows = remember(state.absences, state.myRequests, state.group, month) {
        val entries = buildList {
            state.absences.forEach { absence ->
                add(
                    Triple(
                        absence.employeeName ?: "—",
                        absence.employeeRole,
                        TimelineBar(
                            from = clampDay(absence.start, month),
                            to = clampDay(absence.end, month),
                            pending = absence.status == LeaveStatus.OCZEKUJE,
                            mine = false,
                        ),
                    ),
                )
            }
            // Własne wnioski nie przychodzą w nieobecnościach (byłyby dublem),
            // a bez nich zwierzchnik nie widziałby, że sam też jest wtedy poza.
            state.activeRequests.forEach { request ->
                add(
                    Triple(
                        "Ty",
                        null,
                        TimelineBar(
                            from = clampDay(request.start, month),
                            to = clampDay(request.end, month),
                            pending = request.status == LeaveStatus.OCZEKUJE,
                            mine = true,
                        ),
                    ),
                )
            }
        }
            .filter { (_, role, bar) ->
                bar.from <= days && bar.to >= 1 && matchesGroup(role, state.group)
            }
            .groupBy { it.first to it.second }

        entries.map { (key, list) ->
            TimelineRow(key.first, key.second, list.map { it.third })
        }.sortedBy { it.name }
    }

    if (rows.isEmpty()) {
        Text(
            text = "W tym miesiącu nikt z tej grupy nie ma zaplanowanego urlopu.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    val today = LocalDate.now()
    val nowFraction = if (YearMonth.from(today) == month) {
        (today.dayOfMonth - 1).toFloat() / days
    } else {
        null
    }

    Column(verticalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(start = 62.dp)) {
            listOf(1, 8, 15, 22, days).forEach { label ->
                Text(
                    text = label.toString(),
                    fontSize = 8.5.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        rows.forEach { row ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.width(56.dp)) {
                    Text(
                        text = row.name,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    roleShort(row.role).takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            fontSize = 8.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(18.dp)
                        .padding(start = 6.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    row.bars.forEach { bar ->
                        val startF = (bar.from - 1).coerceAtLeast(0).toFloat() / days
                        val widthF = ((bar.to - bar.from + 1).coerceAtLeast(1)).toFloat() / days
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(widthF)
                                .height(14.dp)
                                .align(Alignment.CenterStart)
                                .offsetFraction(startF)
                                .clip(RoundedCornerShape(3.dp))
                                .background(
                                    when {
                                        bar.mine -> LeaveMine.copy(alpha = 0.7f)
                                        bar.pending -> LeavePending.copy(alpha = 0.6f)
                                        else -> LeaveTeam.copy(alpha = 0.7f)
                                    },
                                ),
                        )
                    }
                    nowFraction?.let { fraction ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.004f)
                                .height(18.dp)
                                .align(Alignment.CenterStart)
                                .offsetFraction(fraction)
                                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f)),
                        )
                    }
                }
            }
        }
        tightestWeek(state, month)?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = LeaveAccent,
            )
        }
    }
}

/** Przesunięcie paska w poziomie jako ułamek szerokości toru. */
private fun Modifier.offsetFraction(fraction: Float): Modifier = this.then(
    Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            placeable.placeRelative((constraints.maxWidth * fraction).toInt(), 0)
        }
    },
)

private fun clampDay(day: LocalDate, month: YearMonth): Int = when {
    YearMonth.from(day) < month -> 0
    YearMonth.from(day) > month -> month.lengthOfMonth() + 1
    else -> day.dayOfMonth
}

private fun matchesGroup(role: String?, group: LeaveViewModel.LeaveGroup): Boolean = when (group) {
    LeaveViewModel.LeaveGroup.ALL -> true
    LeaveViewModel.LeaveGroup.FIELD -> isFieldRole(role)
    LeaveViewModel.LeaveGroup.OFFICE -> !isFieldRole(role)
}

/**
 * Tydzień miesiąca z największą liczbą nieobecnych — jedno zdanie, po które
 * koordynator sięga w drodze na budowę.
 */
private fun tightestWeek(state: LeaveViewModel.UiState, month: YearMonth): String? {
    val absences = state.absences.filter { matchesGroup(it.employeeRole, state.group) }
    if (absences.isEmpty()) return null
    var bestStart: LocalDate? = null
    var bestCount = 0
    var day = month.atDay(1)
    while (!day.isAfter(month.atEndOfMonth())) {
        val weekStart = startOfWeek(day)
        val weekEnd = weekStart.plusDays(6)
        val count = absences
            .filter { !it.start.isAfter(weekEnd) && !it.end.isBefore(weekStart) }
            .map { it.userId }
            .distinct()
            .size
        if (count > bestCount) {
            bestCount = count
            bestStart = weekStart
        }
        day = day.plusDays(7)
    }
    val start = bestStart ?: return null
    if (bestCount < 2) return null
    return "${rangeLabel(start, start.plusDays(6))}: $bestCount osoby poza firmą — " +
        "najciaśniejszy tydzień miesiąca."
}
