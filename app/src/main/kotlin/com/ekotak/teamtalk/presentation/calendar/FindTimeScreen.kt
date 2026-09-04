package com.ekotak.teamtalk.presentation.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ekotak.teamtalk.presentation.components.AppTopBar
import com.ekotak.teamtalk.presentation.crm.parseIsoMillis
import com.ekotak.teamtalk.presentation.service.WarningBar
import com.ekotak.teamtalk.presentation.theme.Red600

/**
 * „Znajdź termin" — zajętość zaznaczonych osób i wolne okna w godzinach pracy.
 *
 * Paski pokazują zajętość bez treści wydarzeń: `freebusy` z definicji nie
 * zdradza, co ktoś ma w kalendarzu, tylko że jest zajęty. Wybór slotu wraca
 * do kalendarza i otwiera arkusz nowego wydarzenia z wpisanymi uczestnikami.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FindTimeScreen(
    onNavigateBack: () -> Unit,
    onPickSlot: (Long, Long, List<String>) -> Unit,
    viewModel: FindTimeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = { AppTopBar(title = "Znajdź termin", onNavigateBack = onNavigateBack) },
    ) { padding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Label("Kogo szukamy")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                state.members.forEach { member ->
                    FilterChip(
                        selected = member.id in state.selected,
                        onClick = { viewModel.toggleMember(member.id) },
                        label = { Text(member.displayName, style = MaterialTheme.typography.labelMedium) },
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.stepDay(-1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Poprzedni dzień")
                }
                Text(
                    text = dayLabel(state.day).replaceFirstChar { it.titlecase(PL) },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { viewModel.stepDay(1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Następny dzień")
                }
            }

            state.error?.let { WarningBar(it, color = Red600) }

            Label("Zajętość · 7:00 – 18:00")
            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxWidth().padding(12.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.busy.forEach { person ->
                val name = state.members.firstOrNull { it.id == person.userId }?.displayName
                    ?: "Osoba"
                BusyTrack(
                    name = name,
                    dayStartMillis = dateTimeToMillis(state.day.atTime(7, 0)),
                    dayEndMillis = dateTimeToMillis(state.day.atTime(18, 0)),
                    busy = person.busy.mapNotNull { slot ->
                        val from = parseIsoMillis(slot.startAt) ?: return@mapNotNull null
                        val to = parseIsoMillis(slot.endAt) ?: return@mapNotNull null
                        from to to
                    },
                )
            }
            if (state.selected.isEmpty()) {
                Text(
                    "Zaznacz osoby, dla których szukamy wspólnego okna.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Label("Propozycje")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(30, 60, 90, 120).forEach { minutes ->
                    FilterChip(
                        selected = state.durationMinutes == minutes,
                        onClick = { viewModel.setDuration(minutes) },
                        label = { Text("$minutes min") },
                    )
                }
            }
            if (state.slots.isEmpty() && state.selected.isNotEmpty() && !state.isLoading) {
                Text(
                    "Brak wspólnego okna tego dnia — spróbuj następnego albo krótszego spotkania.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            state.slots.forEach { slot ->
                Surface(
                    onClick = { onPickSlot(slot.startMillis, slot.endMillis, state.selected.toList()) },
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = "${formatHour(slot.startMillis)} – ${formatHour(slot.endMillis)}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "wszyscy wolni",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Pasek zajętości jednej osoby — czerwone bloki na tle godzin pracy. */
@Composable
private fun BusyTrack(
    name: String,
    dayStartMillis: Long,
    dayEndMillis: Long,
    busy: List<Pair<Long, Long>>,
) {
    val span = (dayEndMillis - dayStartMillis).coerceAtLeast(1L)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(76.dp),
        )
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .height(18.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(5.dp),
                )
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(5.dp)),
        ) {
            val trackWidth = maxWidth
            busy.forEach { (from, to) ->
                val start = ((from - dayStartMillis).coerceAtLeast(0L).toFloat() / span)
                val end = ((to - dayStartMillis).coerceAtMost(span).toFloat() / span)
                if (end <= 0f || start >= 1f || end <= start) return@forEach
                Box(
                    modifier = Modifier
                        .offset(x = trackWidth * start)
                        .width(trackWidth * (end - start))
                        .fillMaxHeight()
                        .background(Red600.copy(alpha = 0.45f), RoundedCornerShape(4.dp)),
                )
            }
        }
        Box(modifier = Modifier.size(4.dp))
    }
}
