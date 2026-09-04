package com.ekotak.teamtalk.presentation.calendar

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ekotak.teamtalk.presentation.components.AppTopBar
import com.ekotak.teamtalk.presentation.service.WarningBar
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.YearMonth

/**
 * Moduł Kalendarz — odwzorowanie `web/src/app/app/calendar` na telefonie.
 *
 * Układ powtarza panel: zakładki widoku, belka zakresu, siatka. Lewa kolumna
 * panelu (mini-kalendarz, warstwy, nakładki) nie mieści się na 360 dp, więc
 * warstwy zjeżdżają do arkusza pod ikoną, a mini-kalendarz zastępuje sam widok
 * miesiąca — ustalenie z makiety `design/mockups/modul-kalendarz.html`.
 *
 * [deepLinkEventId] = wejście z przypomnienia o wydarzeniu: ten sam ekran
 * z otwartą kartą tego terminu.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    onNavigateBack: () -> Unit,
    onOpenFindTime: () -> Unit,
    deepLinkEventId: String? = null,
    /** Slot wybrany na ekranie „Znajdź termin": początek, koniec, uczestnicy. */
    pickedSlot: Triple<Long, Long, List<String>>? = null,
    onSlotConsumed: () -> Unit = {},
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showLayers by remember { mutableStateOf(false) }

    // Linia „teraz" w siatce godzinowej musi się przesuwać.
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            viewModel.tick()
        }
    }

    // Wydarzenie z powiadomienia otwieramy RAZ — dane dochodzą partiami, a bez
    // tego znacznika arkusz otwierałby się na nowo przy każdym odświeżeniu
    // listy, także po tym, jak człowiek go zamknie.
    var deepLinkOpened by remember { mutableStateOf(false) }
    LaunchedEffect(deepLinkEventId, state.events.size) {
        if (deepLinkEventId != null && !deepLinkOpened && state.events.any { it.id == deepLinkEventId }) {
            viewModel.openEvent(deepLinkEventId)
            deepLinkOpened = true
        }
    }

    LaunchedEffect(pickedSlot) {
        pickedSlot?.let { (start, end, attendees) ->
            viewModel.openFromSlot(start, end, attendees)
            onSlotConsumed()
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Kalendarz",
                onNavigateBack = onNavigateBack,
                actions = {
                    IconButton(onClick = onOpenFindTime) {
                        Icon(Icons.Filled.Search, contentDescription = "Znajdź termin")
                    }
                    BadgedBox(
                        badge = {
                            val count = state.visibleLayerCount + state.overlaysOn.size
                            if (count > 0) Badge { Text("$count") }
                        },
                    ) {
                        IconButton(onClick = { showLayers = true }) {
                            Icon(Icons.Filled.Layers, contentDescription = "Warstwy i filtr")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.openNew() }) {
                Icon(Icons.Filled.Add, contentDescription = "Nowe wydarzenie")
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            ViewTabs(selected = state.view, onSelect = viewModel::setView)
            RangeBar(
                label = rangeLabel(state),
                onPrev = { viewModel.step(-1) },
                onNext = { viewModel.step(1) },
                onToday = viewModel::goToday,
            )
            state.error?.let { WarningBar(it) }

            if (state.isLoading && state.events.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                return@Column
            }

            PullToRefreshBox(
                isRefreshing = state.isRefreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                when (state.view) {
                    CalendarViewKind.MONTH -> CalendarMonthView(
                        state = state,
                        onSelectDay = viewModel::selectDay,
                        onOpenEvent = viewModel::openEvent,
                        onOpenOverlay = null,
                    )
                    CalendarViewKind.WEEK -> CalendarTimeGrid(
                        state = state,
                        days = weekDays(state.anchor),
                        onOpenEvent = viewModel::openEvent,
                        onCreateAt = { day, hour -> viewModel.openNew(day, hour) },
                        onArmResize = viewModel::armResize,
                        onResize = viewModel::resizeEvent,
                        onCancelResize = viewModel::cancelResize,
                    )
                    CalendarViewKind.DAY -> CalendarTimeGrid(
                        state = state,
                        days = listOf(state.anchor),
                        onOpenEvent = viewModel::openEvent,
                        onCreateAt = { day, hour -> viewModel.openNew(day, hour) },
                        onArmResize = viewModel::armResize,
                        onResize = viewModel::resizeEvent,
                        onCancelResize = viewModel::cancelResize,
                    )
                    CalendarViewKind.AGENDA -> CalendarAgendaView(
                        state = state,
                        onOpenEvent = viewModel::openEvent,
                        onOpenOverlay = null,
                    )
                }
            }
        }
    }

    state.form?.let { form ->
        EventSheet(
            form = form,
            calendars = state.activeCalendars,
            members = state.members,
            currentUserId = state.currentUserId,
            onEdit = viewModel::editForm,
            onSave = viewModel::save,
            onDelete = viewModel::deleteEvent,
            onRsvp = viewModel::setRsvp,
            onDismiss = viewModel::closeForm,
        )
    }

    if (showLayers) {
        LayersSheet(
            calendars = state.activeCalendars,
            hiddenLayers = state.hiddenLayers,
            overlaysOn = state.overlaysOn,
            members = state.members,
            person = state.person,
            currentUserId = state.currentUserId,
            onToggleLayer = viewModel::toggleLayer,
            onToggleOverlay = viewModel::toggleOverlay,
            busyOn = state.busyOn,
            onToggleBusy = viewModel::toggleBusyLayer,
            onSetPerson = viewModel::setPersonFilter,
            onCreateCalendar = viewModel::createCalendar,
            onRenameCalendar = viewModel::renameCalendar,
            onArchiveCalendar = viewModel::archiveCalendar,
            onDismiss = { showLayers = false },
        )
    }
}

@Composable
private fun ViewTabs(selected: CalendarViewKind, onSelect: (CalendarViewKind) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        CalendarViewKind.entries.forEach { kind ->
            FilterChip(
                selected = kind == selected,
                onClick = { onSelect(kind) },
                label = { Text(kind.label) },
            )
        }
    }
}

@Composable
private fun RangeBar(
    label: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp),
    ) {
        IconButton(onClick = onPrev, modifier = Modifier.size(36.dp)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Poprzedni")
        }
        Text(
            text = label.replaceFirstChar { it.titlecase(PL) },
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNext, modifier = Modifier.size(36.dp)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Następny")
        }
        TextButton(onClick = onToday) { Text("Dziś") }
    }
}

/** Etykieta zakresu — te same formaty co w panelu. */
private fun rangeLabel(state: CalendarViewModel.UiState): String = when (state.view) {
    CalendarViewKind.MONTH -> monthLabel(YearMonth.from(state.anchor))
    CalendarViewKind.DAY -> dayLabel(state.anchor)
    CalendarViewKind.WEEK -> {
        val start = startOfWeek(state.anchor)
        "${shortDay(start)} – ${shortDay(start.plusDays(6))}"
    }
    CalendarViewKind.AGENDA ->
        "${shortDay(state.anchor)} – ${shortDay(state.anchor.plusDays((AGENDA_DAYS - 1).toLong()))}"
}

private fun weekDays(anchor: LocalDate): List<LocalDate> {
    val start = startOfWeek(anchor)
    return (0 until 7).map { start.plusDays(it.toLong()) }
}
