package com.ekotak.teamtalk.presentation.leave

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ekotak.teamtalk.domain.leave.countWorkingDays
import com.ekotak.teamtalk.domain.model.LeaveMode
import com.ekotak.teamtalk.domain.model.LeaveRequest
import com.ekotak.teamtalk.domain.model.LeaveStatus
import com.ekotak.teamtalk.presentation.components.AppTopBar
import com.ekotak.teamtalk.presentation.service.WarningBar
import com.ekotak.teamtalk.presentation.theme.SyncBlue

/**
 * Moduł Urlop — mobilny odpowiednik zakładki „Urlop" z `HrView.tsx`.
 *
 * Układ idzie za panelem: liczniki i pasek zużycia u góry, pod nimi kalendarz
 * w wybranej skali, a najniżej własne wnioski. To, co w panelu jest formularzem
 * obok kalendarza, na 360 dp zjeżdża do arkusza od dołu.
 *
 * Ekran ma dwa warianty liczników, bo tryb bierze się z rodzaju umowy: wymiar
 * (pula dni i pasek) albo sam licznik dni bezpłatnego, gdy umowa nie daje
 * urlopu wypoczynkowego (koncepcja urlopów z 2026-09-04).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaveScreen(
    onNavigateBack: () -> Unit,
    /** Wejście z powiadomienia: „team" otwiera skrzynkę zwierzchnika. */
    initialTab: String? = null,
    viewModel: LeaveViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Zakładkę z powiadomienia ustawiamy RAZ — inaczej każde odświeżenie listy
    // odrzucałoby człowieka z powrotem tam, skąd wszedł.
    var tabApplied by remember { mutableStateOf(false) }
    LaunchedEffect(initialTab) {
        if (!tabApplied && initialTab == "team") {
            viewModel.setTab(LeaveViewModel.LeaveTab.TEAM)
            tabApplied = true
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
                title = "Urlop",
                onNavigateBack = onNavigateBack,
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = viewModel::openNew) {
                Icon(Icons.Filled.Add, contentDescription = "Nowy wniosek")
            }
        },
    ) { padding ->
        if (state.isLoading && state.balance == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize().padding(padding),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 88.dp),
            ) {
                state.error?.let { WarningBar(it) }

                // Segment pokazujemy dopiero, gdy jest czyj urlop zatwierdzać —
                // monterowi bez podwładnych zakładka „Zespół" tylko zabierałaby
                // miejsce. Nieobecności widać wtedy w tle kalendarza.
                if (state.inbox.isNotEmpty()) {
                    TabRow(state.tab, state.toDecide.size, viewModel::setTab)
                }

                if (state.tab == LeaveViewModel.LeaveTab.TEAM) {
                    LeaveTeamSection(
                        state = state,
                        onDecide = { id, approve -> viewModel.decide(id, approve) },
                        onSetGroup = viewModel::setGroup,
                    )
                    return@Column
                }

                state.balance?.let { balance ->
                    if (balance.mode == LeaveMode.QUOTA) {
                        QuotaCounters(state)
                        UsageBar(state)
                    } else {
                        UnpaidCounters(state)
                    }
                }

                ScaleTabs(state.scale, viewModel::setScale)
                RangeBar(
                    label = when (state.scale) {
                        LeaveScale.WEEK -> weekLabel(startOfWeek(state.anchor))
                        LeaveScale.MONTH -> monthLabel(state.anchor)
                        LeaveScale.QUARTER -> quarterLabel(state.anchor)
                        LeaveScale.YEAR -> state.anchor.year.toString()
                    },
                    onPrev = { viewModel.step(-1) },
                    onNext = { viewModel.step(1) },
                    onToday = viewModel::goToday,
                )

                LeaveCalendar(
                    state = state,
                    onSelectDay = viewModel::selectDay,
                    onOpenMonth = { day ->
                        viewModel.setScale(LeaveScale.MONTH)
                        viewModel.goToMonth(day)
                    },
                )

                state.selection?.let { selection ->
                    SelectionBar(
                        state = state,
                        selection = selection,
                        onSubmit = viewModel::openNew,
                        onClear = viewModel::clearSelection,
                    )
                }

                CalendarLegend()

                Text(
                    text = "Moje wnioski",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (state.myRequests.isEmpty()) {
                    Text(
                        text = "Brak wniosków w tym roku.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    state.myRequests.forEach { request ->
                        RequestRow(
                            request = request,
                            onEdit = { viewModel.openEdit(request.id) },
                            onCancel = { viewModel.cancelRequest(request.id) },
                        )
                    }
                }
            }
        }
    }

    state.form?.let { form ->
        LeaveRequestSheet(
            form = form,
            state = state,
            onEdit = viewModel::editForm,
            onSave = viewModel::save,
            onCancelRequest = viewModel::cancelRequest,
            onDismiss = viewModel::closeForm,
        )
    }
}

// ── Liczniki ─────────────────────────────────────────────────────────────────

/**
 * Wymiar urlopu. Etykiety są krótsze niż w panelu („Wymiar" zamiast
 * „Przysługuje"), bo na 360 dp cztery pełne słowa ucinają się w połowie —
 * pełne nazwy zostają w legendzie paska tuż pod spodem.
 */
@Composable
private fun QuotaCounters(state: LeaveViewModel.UiState) {
    val balance = state.balance ?: return
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
        Counter("Wymiar", balance.entitled.toString(), Modifier.weight(1f))
        Counter("Użyte", balance.used.toString(), Modifier.weight(1f), LeavePending)
        Counter("Plan", balance.planned.toString(), Modifier.weight(1f), LeaveTeam)
        // Wymiar bywa przekroczony (przeniesione dni nieuzupełnione w kartotece,
        // urlop na zapas). Zielone „−13" czytało się jak błąd aplikacji, więc
        // ujemna reszta zmienia i nazwę, i kolor — zobaczone na urządzeniu
        // 2026-09-07, przy prawdziwych danych.
        val over = balance.remaining < 0
        Counter(
            label = if (over) "Ponad wymiar" else "Zostało",
            value = if (over) (-balance.remaining).toString() else balance.remaining.toString(),
            modifier = Modifier.weight(1f),
            valueColor = if (over) MaterialTheme.colorScheme.error else LeaveMine,
            highlighted = true,
            alarming = over,
        )
    }
}

/** Umowa bez wymiaru: nie ma puli do wyczerpania, więc nie ma czego dzielić. */
@Composable
private fun UnpaidCounters(state: LeaveViewModel.UiState) {
    val balance = state.balance ?: return
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.fillMaxWidth()) {
            Counter(
                label = "Bezpłatny",
                value = "${balance.unpaidDays} dni",
                modifier = Modifier.weight(1f),
                valueColor = LeaveMine,
                highlighted = true,
            )
            Counter("Użyte", balance.used.toString(), Modifier.weight(1f), LeavePending)
            Counter("Plan", balance.planned.toString(), Modifier.weight(1f), LeaveTeam)
        }
        Text(
            text = "Twoja umowa${balance.employmentType?.let { " ($it)" }.orEmpty()} nie daje wymiaru " +
                "urlopu — nie ma limitu dni, liczymy je jako urlop bezpłatny.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Counter(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color? = null,
    highlighted: Boolean = false,
    /** Stan wymagający uwagi — tło idzie w czerwień zamiast w zieleń. */
    alarming: Boolean = false,
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = when {
            alarming -> MaterialTheme.colorScheme.error.copy(alpha = 0.12f)
            highlighted -> LeaveMine.copy(alpha = 0.10f)
            else -> MaterialTheme.colorScheme.surfaceVariant
        },
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(horizontal = 7.dp, vertical = 6.dp)) {
            Text(
                text = label.uppercase(),
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = value,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                color = valueColor ?: MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
        }
    }
}

/** Pasek zużycia wymiaru — te same trzy kolory co legenda w panelu. */
@Composable
private fun UsageBar(state: LeaveViewModel.UiState) {
    val balance = state.balance ?: return
    val free = maxOf(0, balance.remaining)
    val total = maxOf(balance.entitled, balance.used + balance.planned).coerceAtLeast(1)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (balance.used > 0) {
                Box(Modifier.weight(balance.used.toFloat() / total).fillMaxSize().background(LeavePending))
            }
            if (balance.planned > 0) {
                Box(Modifier.weight(balance.planned.toFloat() / total).fillMaxSize().background(LeaveTeam))
            }
            if (free > 0) {
                Box(Modifier.weight(free.toFloat() / total).fillMaxSize().background(LeaveMine))
            }
            val rest = total - balance.used - balance.planned - free
            if (rest > 0) Box(Modifier.weight(rest.toFloat() / total).fillMaxSize())
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            LegendItem(LeavePending, "Wykorzystane ${balance.used}")
            LegendItem(LeaveTeam, "Zaplanowane ${balance.planned}")
            LegendItem(LeaveMine, "Wolne $free")
        }
        if (balance.onDemandTotal > 0) {
            Text(
                text = "Na żądanie ${balance.onDemandUsed}/${balance.onDemandTotal}" +
                    if (balance.pending > 0) " · oczekujące ${balance.pending} dni" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Text(label, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Co znaczą kolory w siatce — bez tego kalendarz jest zagadką. */
@Composable
private fun CalendarLegend() {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        LegendItem(LeaveMine.copy(alpha = 0.45f), "mój urlop")
        LegendItem(LeavePending.copy(alpha = 0.55f), "mój wniosek")
        LegendItem(LeaveTeam, "zespół")
    }
}

// ── Belki nawigacji ──────────────────────────────────────────────────────────

/**
 * Przełącznik „Moje / Zespół". Licznik przy zakładce zespołu mówi, ile wniosków
 * czeka na MOJĄ decyzję — nie ile ich w ogóle jest, bo tylko to wymaga reakcji.
 */
@Composable
private fun TabRow(
    selected: LeaveViewModel.LeaveTab,
    pendingCount: Int,
    onSelect: (LeaveViewModel.LeaveTab) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        LeaveViewModel.LeaveTab.entries.forEach { tab ->
            FilterChip(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                label = {
                    Text(
                        if (tab == LeaveViewModel.LeaveTab.TEAM && pendingCount > 0) {
                            "${tab.label} ($pendingCount)"
                        } else {
                            tab.label
                        },
                    )
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ScaleTabs(selected: LeaveScale, onSelect: (LeaveScale) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
    ) {
        LeaveScale.entries.forEach { scale ->
            FilterChip(
                selected = scale == selected,
                onClick = { onSelect(scale) },
                label = { Text(scale.label) },
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
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        IconButton(onClick = onPrev, modifier = Modifier.size(36.dp)) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Poprzedni")
        }
        Text(
            text = label.replaceFirstChar { it.titlecase(PL_LEAVE) },
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

/**
 * Podsumowanie zaznaczenia. Liczba dni roboczych jest tu policzona lokalnie tą
 * samą arytmetyką co na serwerze — człowiek musi ją zobaczyć, zanim naciśnie
 * „Złóż wniosek", także bez zasięgu.
 */
@Composable
private fun SelectionBar(
    state: LeaveViewModel.UiState,
    selection: LeaveViewModel.Selection,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
) {
    val days = countWorkingDays(selection.from, selection.to)
    val remaining = state.balance
        ?.takeIf { it.mode == LeaveMode.QUOTA }
        ?.let { it.remaining - days }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = LeaveMine.copy(alpha = 0.14f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = rangeLabel(selection.from, selection.to),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = buildString {
                        append(workingDaysLabel(days))
                        if (!selection.isComplete) {
                            append(" · dotknij dzień końcowy")
                        } else if (remaining != null) {
                            if (remaining < 0) append(" · ponad wymiar o ${-remaining}")
                            else append(" · zostanie $remaining")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onClear) { Text("Wyczyść") }
            if (days > 0) {
                TextButton(onClick = onSubmit) { Text("Złóż wniosek") }
            }
        }
    }
}

// ── Lista wniosków ───────────────────────────────────────────────────────────

@Composable
private fun RequestRow(
    request: LeaveRequest,
    onEdit: () -> Unit,
    onCancel: () -> Unit,
) {
    val accent = if (request.pendingSync) SyncBlue else statusColor(request.status)

    // Potwierdzenie jak w panelu: „Anuluj" stoi tuż obok „Zmień", a anulowania
    // zatwierdzonego urlopu nie da się cofnąć — trzeba złożyć wniosek od nowa
    // i czekać na kolejną decyzję.
    var confirming by remember { mutableStateOf(false) }
    if (confirming) {
        AlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("Anulować wniosek?") },
            text = {
                Text(
                    "${request.type.label} · ${rangeLabel(request.start, request.end)} " +
                        "(${daysLabel(request.workingDays)}). " +
                        if (request.status == LeaveStatus.ZATWIERDZONY) {
                            "Urlop jest zatwierdzony — przywrócenie go wymaga nowego wniosku."
                        } else {
                            "Wniosek zniknie z listy zwierzchnika."
                        },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = false
                        onCancel()
                    },
                ) { Text("Anuluj wniosek", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirming = false }) { Text("Zostaw") }
            },
        )
    }
    Surface(
        shape = RoundedCornerShape(11.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Kreska statusu z lewej — ten sam zabieg co `.reqRow` w panelu.
            Box(Modifier.width(3.dp).height(52.dp).background(accent))
            Column(modifier = Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(
                    text = "${request.type.label} · ${daysLabel(request.workingDays)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = rangeLabel(request.start, request.end),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Notatka decyzji wchodzi do wiersza, a nie chowa się w szczegółach:
                // przy odmowie to zwykle jedyna informacja, co dalej robić.
                request.decisionNote?.let { note ->
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodySmall,
                        color = LeaveAccent,
                    )
                }
            }
            Text(
                text = if (request.pendingSync) "W kolejce" else request.status.label,
                fontSize = 10.sp,
                color = accent,
                modifier = Modifier.padding(end = 8.dp),
            )
            if (request.status.isOpen) {
                TextButton(onClick = onEdit, contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp)) {
                    Text("Zmień", fontSize = 12.sp)
                }
                TextButton(
                    onClick = { confirming = true },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp),
                    modifier = Modifier.padding(end = 4.dp),
                ) {
                    Text("Anuluj", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}
