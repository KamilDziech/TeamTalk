package com.ekotak.teamtalk.presentation.meetings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.ekotak.teamtalk.data.meeting.MeetingRecorder
import com.ekotak.teamtalk.data.remote.dto.MeetingListItemDto
import com.ekotak.teamtalk.presentation.components.AppTopBar
import com.ekotak.teamtalk.presentation.crm.formatDateTime
import com.ekotak.teamtalk.presentation.theme.Red600

/** Lista spotkań — sekcje w kolejności tego, co wymaga ruchu (jak w panelu). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeetingsListScreen(
    onOpen: (String) -> Unit,
    onCreate: () -> Unit,
    viewModel: MeetingsListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val rec by viewModel.recorderState.collectAsState()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Powrót z karty spotkania = świeża lista (status mógł się zmienić).
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { viewModel.refresh() }
    }

    val live = state.items.filter { it.status == "live" || it.status == "paused" }
    val review = state.items.filter {
        it.status in setOf("processing", "review", "failed") || (it.status == "scheduled" && it.needsVoiceSummary)
    }
    val upcoming = state.items.filter { it.status == "scheduled" && !it.needsVoiceSummary }.sortedBy { it.startAt }
    val done = state.items.filter { it.status == "approved" }

    Scaffold(
        topBar = { AppTopBar(title = "Spotkanie") },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreate,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text("Nowe spotkanie") },
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            if (state.isLoading) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
                return@PullToRefreshBox
            }
            LazyColumn(
                contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item {
                    Text("Spotkanie", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "Agenda, nagranie w tle, podsumowanie i zadania do zatwierdzenia.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (rec.phase != MeetingRecorder.Phase.IDLE && rec.meetingId != null) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = Red600.copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, Red600),
                            modifier = Modifier.fillMaxWidth().clickable { onOpen(rec.meetingId!!) },
                        ) {
                            Text(
                                (if (rec.phase == MeetingRecorder.Phase.PAUSED) "Pauza: " else "● Nagrywasz: ") + rec.title,
                                modifier = Modifier.padding(12.dp),
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
                state.error?.let { err -> item { NoticeStrip(err, Red600) } }
                section("Trwa teraz", live, onOpen)
                section("Podsumowania", review, onOpen)
                section("Nadchodzące", upcoming, onOpen, empty = "Brak zaplanowanych spotkań.")
                section("Zamknięte", done, onOpen)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: String,
    items: List<MeetingListItemDto>,
    onOpen: (String) -> Unit,
    empty: String? = null,
) {
    if (items.isEmpty() && empty == null) return
    item {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
    if (items.isEmpty()) {
        item { Text(empty!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
    items(items, key = { it.id }) { m -> MeetingRow(m) { onOpen(m.id) } }
}

@Composable
private fun MeetingRow(m: MeetingListItemDto, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TypeBadge(m.type, 36.dp)
            Column(Modifier.weight(1f)) {
                Text(m.title, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val meta = buildString {
                    append(formatDateTime(m.startAt) ?: "")
                    m.client?.let { append(" · ").append(it.name) }
                    if (m.dayCount > 1) append(" · ").append(daysLabel(m.dayCount))
                    append(" · ").append(m.host.name)
                    append(" · ").append(m.participantCount).append(" os.")
                    if (m.agendaCount > 0) append(" · agenda ${m.agendaDone}/${m.agendaCount}")
                    if (m.status == "scheduled" && !m.isHost && m.myRsvp == "needs_action") append(" · czeka na Twoją odpowiedź")
                }
                Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // D16: wynik oceny AI w kolorze progu.
            m.score?.let { ScoreBadge(it) }
            StatusPill(if (m.needsVoiceSummary) VOICE_SUMMARY_PILL else m.status)
        }
    }
}
