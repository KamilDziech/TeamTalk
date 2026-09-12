package com.ekotak.teamtalk.presentation.map

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ekotak.teamtalk.domain.model.HarshEvent
import com.ekotak.teamtalk.domain.model.RouteHistory
import com.ekotak.teamtalk.domain.model.RoutePalette
import com.ekotak.teamtalk.domain.model.RouteTimelineItem
import com.ekotak.teamtalk.domain.model.SpeedingRun
import com.ekotak.teamtalk.domain.model.timeline
import com.ekotak.teamtalk.presentation.components.AppTopBar
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Historia trasy pojazdu — mobilny odpowiednik panelu historii z board360
 * (`web/src/app/app/map/RouteHistoryPanel.tsx`).
 *
 * Układ jest inny, bo ekran jest inny: panel trzyma mapę i oś czasu obok
 * siebie, telefon kładzie mapę u góry, a oś czasu pod nią — czyta się ją z góry
 * na dół, jak dzień. Treść jest ta sama i pochodzi z tej samej odpowiedzi
 * serwera, więc liczby zgadzają się co do minuty.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RouteHistoryScreen(
    onNavigateBack: () -> Unit,
    viewModel: RouteHistoryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var showDayPicker by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = listOfNotNull(viewModel.assetName, viewModel.registration)
                    .joinToString(" · "),
                onNavigateBack = onNavigateBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RouteWindow.entries.forEach { window ->
                    FilterChip(
                        selected = state.dayMillis == null && state.window == window,
                        onClick = { viewModel.setWindow(window) },
                        label = { Text(window.label) },
                    )
                }
                FilterChip(
                    selected = state.dayMillis != null,
                    onClick = { showDayPicker = true },
                    label = {
                        Text(state.dayMillis?.let { formatDay(it) } ?: "Wybierz dzień")
                    },
                )
            }

            // `clip` obowiązkowe: osmdroid rysuje kafelki poza swoimi granicami,
            // a AndroidView niczego nie przycina — bez tego mapa zasłania chipy.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .clip(RoundedCornerShape(14.dp)),
            ) {
                RouteTrackMap(
                    history = state.history,
                    focus = state.focus,
                    fitRequest = state.fitRequest,
                    modifier = Modifier.fillMaxSize(),
                )
                if (state.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                }
            }

            val history = state.history
            when {
                state.error != null && history == null -> Message(state.error ?: "")
                history == null && !state.isLoading -> Message("Brak danych trasy.")
                history != null && !history.hasTrack ->
                    Message("W tym oknie nie ma zapisanego śladu — auto stało poza zasięgiem albo tracker milczał.")
                history != null -> RouteBody(
                    history = history,
                    error = state.error,
                    onFocus = viewModel::focusOn,
                    onFit = viewModel::requestFit,
                )
            }
        }
    }

    if (showDayPicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = state.dayMillis)
        DatePickerDialog(
            onDismissRequest = { showDayPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let(viewModel::setDay)
                    showDayPicker = false
                }) { Text("Pokaż") }
            },
            dismissButton = {
                TextButton(onClick = { showDayPicker = false }) { Text("Anuluj") }
            },
        ) { DatePicker(state = pickerState) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RouteBody(
    history: RouteHistory,
    error: String?,
    onFocus: (Double, Double) -> Unit,
    onFit: () -> Unit,
) {
    val summary = history.summary
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (error != null) {
            item { Message("Pokazuję ostatnio pobraną trasę. $error") }
        }

        item {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                summary.firstDepartureMillis?.let {
                    Chip("Wyruszył ${formatClock(it)}", strong = true)
                }
                val drivers = history.trips.mapNotNull { it.driverLabel }.distinct()
                if (drivers.isNotEmpty()) Chip("Kierowca: ${drivers.joinToString(", ")}")
                Chip("${format1(summary.distanceKm)} km")
                Chip("${formatDuration(summary.drivingMinutes)} jazdy")
                Chip("${summary.trips} kursów · ${summary.stops} postojów")
                if (summary.idleMinutes > 0) {
                    Chip(
                        "${formatDuration(summary.idleMinutes)} na jałowym ≈ ${format2(summary.idleCostPln)} zł",
                        color = RoutePalette.IDLE,
                    )
                }
                summary.maxSpeed?.let {
                    Chip(
                        "maks ${it.roundToInt()} km/h",
                        color = if (it > history.speedLimitKmh) RoutePalette.OVER_LIMIT else null,
                    )
                }
                if (summary.speedingRuns > 0) {
                    Chip(
                        "${summary.speedingRuns} × ponad ${history.speedLimitKmh} km/h",
                        color = RoutePalette.OVER_LIMIT,
                    )
                }
                Chip(
                    "Styl ${summary.style.score}/100 · ${summary.style.grade}",
                    color = when {
                        summary.style.score >= 85 -> null
                        summary.style.score >= 65 -> RoutePalette.MEDIUM
                        else -> RoutePalette.OVER_LIMIT
                    },
                )
                TextButton(onClick = onFit) { Text("Dopasuj widok") }
            }
        }

        items(history.timeline(), key = { it.atMillis.toString() + it.javaClass.simpleName }) { item ->
            when (item) {
                is RouteTimelineItem.Trip -> Entry(
                    color = RoutePalette.SLOW,
                    title = "${formatClock(item.trip.departedMillis)} → " +
                        if (item.trip.open) "jedzie" else formatClock(item.trip.arrivedMillis),
                    subtitle = buildString {
                        append("${format1(item.trip.distanceKm)} km · ${formatDuration(item.trip.minutes)}")
                        item.trip.avgSpeed?.let { append(" · śr. ${it.roundToInt()} km/h") }
                        item.trip.maxSpeed?.let { append(" · maks ${it.roundToInt()} km/h") }
                        item.trip.driverLabel?.let { append(" · $it") }
                        if (item.trip.speedingCount > 0) {
                            append(" · ${item.trip.speedingCount} × prędkość")
                        }
                    },
                    onClick = { onFocus(item.trip.fromLat, item.trip.fromLng) },
                )

                is RouteTimelineItem.Stop -> Entry(
                    color = if (item.stop.idling) RoutePalette.IDLE else RoutePalette.PARKED,
                    title = "Postój ${formatClock(item.stop.fromMillis)} – " +
                        if (item.stop.open) "nadal" else formatClock(item.stop.toMillis),
                    subtitle = "${formatDuration(item.stop.minutes)} · " +
                        if (item.stop.idling) "silnik pracował (jałowy)" else "silnik zgaszony",
                    onClick = { onFocus(item.stop.lat, item.stop.lng) },
                )
            }
        }

        if (history.speeding.isNotEmpty()) {
            item { SectionTitle("Przekroczenia prędkości (próg ${history.speedLimitKmh} km/h)") }
            items(history.speeding, key = { it.fromMillis }) { run ->
                Entry(
                    color = RoutePalette.OVER_LIMIT,
                    title = "${run.maxSpeed.roundToInt()} km/h o ${formatClock(run.fromMillis)}",
                    subtitle = speedingMeta(run),
                    onClick = { onFocus(run.lat, run.lng) },
                )
            }
        }

        if (history.harsh.isNotEmpty()) {
            item {
                SectionTitle(
                    "Gwałtowna jazda (${history.summary.style.harshFromDevice} z urządzenia, " +
                        "${history.harsh.size - history.summary.style.harshFromDevice} z odczytów)",
                )
            }
            items(history.harsh.take(12), key = { it.atMillis }) { event ->
                Entry(
                    color = RoutePalette.MEDIUM,
                    title = "${event.type.label} o ${formatClock(event.atMillis)}",
                    subtitle = harshMeta(event),
                    onClick = { onFocus(event.lat, event.lng) },
                )
            }
        }

        item { Legend(history.speedLimitKmh) }
    }
}

@Composable
private fun Entry(color: Long, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                color = Color(color),
                shape = CircleShape,
                modifier = Modifier.size(10.dp),
            ) {}
            Column {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Chip(label: String, color: Long? = null, strong: Boolean = false) {
    Surface(
        color = color?.let { Color(it).copy(alpha = 0.14f) } ?: MaterialTheme.colorScheme.surfaceVariant,
        shape = MaterialTheme.shapes.small,
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal,
            color = color?.let { Color(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        modifier = Modifier.padding(top = 10.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Message(text: String) {
    Text(
        text,
        modifier = Modifier.padding(vertical = 12.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legend(limitKmh: Int) {
    FlowRow(
        modifier = Modifier.padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LegendItem(RoutePalette.SLOW, "do 40 km/h")
        LegendItem(RoutePalette.MEDIUM, "40–70 km/h")
        LegendItem(RoutePalette.FAST, "powyżej 70 km/h")
        LegendItem(RoutePalette.OVER_LIMIT, "ponad $limitKmh km/h")
        LegendItem(RoutePalette.GAP, "luka w sygnale")
    }
}

@Composable
private fun LegendItem(color: Long, label: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(color = Color(color), shape = CircleShape, modifier = Modifier.size(8.dp)) {}
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private fun speedingMeta(run: SpeedingRun): String {
    val czas = if (run.seconds < 60) "${run.seconds} s" else formatDuration(run.seconds / 60)
    val km = if (run.distanceKm > 0) " · ${format1(run.distanceKm)} km" else ""
    return "$czas$km ponad progiem ${run.limit.roundToInt()} km/h"
}

private fun harshMeta(event: HarshEvent): String {
    if (event.fromDevice) {
        return "zgłoszone przez lokalizator (Green Driving) — rodzaju urządzenie nie podaje"
    }
    val zmiana = if (event.fromSpeed != null && event.toSpeed != null) {
        "${event.fromSpeed.roundToInt()} → ${event.toSpeed.roundToInt()} km/h · "
    } else {
        ""
    }
    return "$zmiana${format1(abs(event.accelMs2 ?: 0.0))} m/s² (szacunek z odczytów)"
}

/** „45 min" / „2 h 15 min" — tak samo jak w panelu. */
private fun formatDuration(minutes: Int): String {
    val m = maxOf(0, minutes)
    if (m < 60) return "$m min"
    val h = m / 60
    val rest = m % 60
    return if (rest == 0) "$h h" else "$h h $rest min"
}

private fun formatClock(millis: Long): String =
    SimpleDateFormat("HH:mm", Locale("pl")).format(Date(millis))

private fun formatDay(millis: Long): String =
    SimpleDateFormat("d MMM", Locale("pl")).format(Date(millis))

private fun format1(value: Double): String = String.format(Locale("pl"), "%.1f", value)

private fun format2(value: Double): String = String.format(Locale("pl"), "%.2f", value)
