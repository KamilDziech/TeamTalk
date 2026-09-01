package com.ekotak.teamtalk.presentation.task

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ekotak.teamtalk.domain.model.Deal
import com.ekotak.teamtalk.domain.model.TaskMember
import com.ekotak.teamtalk.domain.model.TaskPriority
import com.ekotak.teamtalk.domain.model.TaskTeam
import com.ekotak.teamtalk.presentation.components.AppTopBar
import com.ekotak.teamtalk.presentation.theme.Green600
import com.ekotak.teamtalk.presentation.theme.Orange600
import com.ekotak.teamtalk.presentation.theme.Red600
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Kreator nowego zadania — siedem plansz plus podsumowanie. Zamiast jednego
 * długiego formularza pytamy o jedną rzecz naraz: nazwa, opis (z dyktowaniem),
 * kogo dotyczy, zespół, osoba, priorytet, termin.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateTaskScreen(
    onNavigateBack: () -> Unit,
    viewModel: CreateTaskViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }
    val isDone = state.step == WizardStep.DONE

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                AppTopBar(
                    title = if (isDone) "Zadanie" else "Nowe zadanie",
                    onNavigateBack = {
                        if (isDone || state.isFirstStep) onNavigateBack()
                        else viewModel.back()
                    },
                    actions = {
                        if (!isDone) {
                            Text(
                                text = "${state.stepNumber} / ${state.steps.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 12.dp),
                            )
                        }
                    },
                )
            },
            bottomBar = {
                WizardFooter(
                    state = state,
                    isDone = isDone,
                    canGoNext = viewModel.canGoNext(state),
                    canSkip = viewModel.canSkip(state),
                    onSkip = viewModel::skip,
                    onNext = {
                        if (state.isLastStep) viewModel.createTask() else viewModel.next()
                    },
                    onFinish = onNavigateBack,
                )
            },
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding(),
            ) {
                if (!isDone) StepProgress(state.steps, state.step)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    when (state.step) {
                        WizardStep.TITLE -> StepTitle(state, viewModel)
                        WizardStep.DESCRIPTION -> StepDescription(state, viewModel)
                        WizardStep.SUBJECT -> StepSubject(state, viewModel)
                        WizardStep.TEAM -> StepTeam(state, viewModel)
                        WizardStep.PERSON -> StepPerson(state, viewModel)
                        WizardStep.PRIORITY -> StepPriority(state, viewModel)
                        WizardStep.DUE -> StepDue(state) { showDatePicker = true }
                        WizardStep.DONE -> StepDone(state, viewModel)
                    }
                }
            }
        }

        ResultBanner(
            visible = !isDone && state.error != null,
            message = state.error.orEmpty(),
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = state.dueAtMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onDueAtChange(datePickerState.selectedDateMillis)
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Anuluj") }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

// ── Ramy kreatora ────────────────────────────────────────────────────────────

/** Pasek postępu: kreska na każdy krok, zielone to, co za nami. */
@Composable
private fun StepProgress(steps: List<WizardStep>, step: WizardStep) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        steps.forEach { s ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
                    .background(
                        color = if (s.ordinal <= step.ordinal) Green600
                        else MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(2.dp),
                    ),
            )
        }
    }
}

@Composable
private fun WizardFooter(
    state: CreateTaskViewModel.UiState,
    isDone: Boolean,
    canGoNext: Boolean,
    canSkip: Boolean,
    onSkip: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (isDone) {
            Button(
                onClick = onFinish,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Green600),
                shape = RoundedCornerShape(12.dp),
            ) { Text("Gotowe", style = MaterialTheme.typography.labelLarge) }
            return@Row
        }

        if (canSkip) {
            OutlinedButton(
                onClick = onSkip,
                modifier = Modifier.height(52.dp),
                enabled = !state.isSaving,
                shape = RoundedCornerShape(12.dp),
            ) { Text("Pomiń") }
        }

        Button(
            onClick = onNext,
            modifier = Modifier
                .weight(1f)
                .height(52.dp),
            enabled = canGoNext && !state.isSaving,
            colors = ButtonDefaults.buttonColors(containerColor = Green600),
            shape = RoundedCornerShape(12.dp),
        ) {
            if (state.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                )
            } else {
                Text(
                    text = if (state.isLastStep) "Zakończ" else "Dalej",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

// ── Kroki ────────────────────────────────────────────────────────────────────

@Composable
private fun StepTitle(state: CreateTaskViewModel.UiState, vm: CreateTaskViewModel) {
    Question("Jak nazwiemy to zadanie?", "Krótko — szczegóły dopiszesz na następnym ekranie.")
    OutlinedTextField(
        value = state.title,
        onValueChange = vm::onTitleChange,
        placeholder = { Text("np. Wymiana pompy obiegowej") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
    )
}

@Composable
private fun StepDescription(state: CreateTaskViewModel.UiState, vm: CreateTaskViewModel) {
    Question("Opisz szerzej zadanie", "Możesz podyktować — tekst pojawi się poniżej do poprawki.")

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        MicButton(isRecording = state.isRecording, onClick = vm::toggleRecording)
        Text(
            text = when {
                state.isRecording -> "Słucham… ${formatSeconds(state.recordingSeconds)} — dotknij, by zakończyć"
                state.description.isNotBlank() -> "Dotknij, by dyktować dalej"
                else -> "Dotknij i mów po polsku"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }

    OutlinedTextField(
        value = state.description,
        onValueChange = vm::onDescriptionChange,
        placeholder = { Text("…albo wpisz opis ręcznie") },
        modifier = Modifier.fillMaxWidth(),
        minLines = 4,
        maxLines = 10,
        shape = RoundedCornerShape(12.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StepSubject(state: CreateTaskViewModel.UiState, vm: CreateTaskViewModel) {
    Question("Kogo dotyczy zadanie?")

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        SubjectMode.entries.forEach { mode ->
            FilterChip(
                selected = state.subjectMode == mode,
                onClick = { vm.onSubjectModeChange(mode) },
                label = {
                    Text(
                        when (mode) {
                            SubjectMode.CLIENT -> "Klient"
                            SubjectMode.PROJECT -> "Projekt"
                            SubjectMode.INTERNAL -> "Wewnętrzne"
                        }
                    )
                },
            )
        }
    }

    when (state.subjectMode) {
        SubjectMode.CLIENT -> ClientPicker(state, vm)
        SubjectMode.PROJECT -> ProjectPicker(state, vm)
        SubjectMode.INTERNAL -> Hint(
            "Zadanie bez powiązania — na przykład porządki w magazynie albo zamówienie materiałów."
        )
    }
}

@Composable
private fun ClientPicker(state: CreateTaskViewModel.UiState, vm: CreateTaskViewModel) {
    OutlinedTextField(
        value = state.clientQuery,
        onValueChange = vm::onClientQueryChange,
        placeholder = { Text("Szukaj w kartotece") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            if (state.clientQuery.isNotBlank()) {
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "Wyczyść",
                    modifier = Modifier.clickable { vm.onClientQueryChange("") },
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
    )

    state.clients.take(if (state.selectedClient == null) 6 else 3).forEach { client ->
        ChoiceRow(
            title = client.displayName,
            subtitle = listOfNotNull(client.primaryPhone, client.city).joinToString(" · "),
            initials = initialsOf(client.displayName),
            selected = state.selectedClient?.id == client.id,
            onClick = { vm.onClientSelect(client) },
        )
    }

    state.selectedClient?.let { ClientDeals(state, vm) }

    Label("…albo wpisz nowy kontakt")
    val contact = state.newContact
    OutlinedTextField(
        value = contact.firstName,
        onValueChange = { vm.onNewContactChange(contact.copy(firstName = it)) },
        placeholder = { Text("Imię *") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
    )
    OutlinedTextField(
        value = contact.lastName,
        onValueChange = { vm.onNewContactChange(contact.copy(lastName = it)) },
        placeholder = { Text("Nazwisko *") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
    )
    OutlinedTextField(
        value = contact.phone,
        onValueChange = { vm.onNewContactChange(contact.copy(phone = it)) },
        placeholder = { Text("Telefon") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
    )
    OutlinedTextField(
        value = contact.email,
        onValueChange = { vm.onNewContactChange(contact.copy(email = it)) },
        placeholder = { Text("E-mail (opcjonalnie)") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
    )
    if (!contact.isEmpty) {
        Hint("Nowy kontakt zostanie założony w kartotece przy zapisie zadania.")
    }
}

/** Deale wybranego klienta — dopiero one wiążą zadanie z klientem. */
@Composable
private fun ClientDeals(state: CreateTaskViewModel.UiState, vm: CreateTaskViewModel) {
    when {
        state.isLoadingDeals -> Hint("Szukam projektów tego klienta…")
        state.clientDeals.isEmpty() ->
            Hint("Ten klient nie ma żadnego projektu — zadanie powstanie bez powiązania.")
        state.clientDeals.size == 1 -> {
            val deal = state.clientDeals.first()
            Hint("Powiązane z: ${vm.dealLabel(deal)} — jedyny projekt tego klienta.")
        }
        else -> {
            Label("Którego projektu dotyczy?")
            state.clientDeals.forEach { deal: Deal ->
                ChoiceRow(
                    title = vm.dealLabel(deal),
                    subtitle = "etap: ${deal.stage.label}",
                    initials = "▸",
                    selected = state.selectedDealId == deal.id,
                    onClick = { vm.onDealSelect(deal.id) },
                )
            }
            ChoiceRow(
                title = "Żadnego",
                subtitle = "zadanie bez powiązania",
                initials = "—",
                selected = state.selectedDealId == null,
                onClick = { vm.onDealSelect(null) },
            )
        }
    }
}

@Composable
private fun ProjectPicker(state: CreateTaskViewModel.UiState, vm: CreateTaskViewModel) {
    when {
        state.isLoadingProjects -> Hint("Wczytuję projekty…")
        state.projectsError != null -> Hint(state.projectsError)
        state.projects.isEmpty() -> Hint("Brak aktywnych projektów.")
        else -> state.projects.forEach { project ->
            ChoiceRow(
                title = project.name,
                subtitle = project.taskCount?.let { "$it zadań" } ?: "",
                initials = "▸",
                selected = state.selectedProjectId == project.id,
                onClick = { vm.onProjectSelect(project.id) },
            )
        }
    }
}

@Composable
private fun StepTeam(state: CreateTaskViewModel.UiState, vm: CreateTaskViewModel) {
    Question("Jaki zespół?", "Jeden wybór. Zawęzi listę osób w następnym kroku.")
    TaskTeam.entries.chunked(3).forEach { row ->
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { team ->
                TeamTile(
                    team = team,
                    selected = state.team == team,
                    onClick = { vm.onTeamChange(team) },
                    modifier = Modifier.weight(1f),
                )
            }
            // Domknięcie niepełnego wiersza, żeby kafelki nie rozjechały się szerokością.
            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun StepPerson(state: CreateTaskViewModel.UiState, vm: CreateTaskViewModel) {
    Question(
        "Kto się tym zajmie?",
        state.team?.let { "Zespół: ${it.label}" } ?: "Najpierw wybierz zespół",
    )
    if (state.isLoadingMembers) {
        Hint("Wczytuję listę osób…")
        return
    }
    val people = state.teamMembers
    if (people.isEmpty()) {
        Hint("Nikt nie ma przypisanej tej funkcji w module Zespół.")
    }
    people.forEach { member: TaskMember ->
        ChoiceRow(
            title = member.displayName,
            subtitle = member.email,
            initials = initialsOf(member.displayName),
            selected = state.assigneeId == member.id,
            onClick = { vm.onAssigneeChange(member.id) },
        )
    }
    ChoiceRow(
        title = "Bez przypisania",
        subtitle = "ktoś z zespołu odbierze",
        initials = "—",
        selected = state.assigneeCleared,
        onClick = { vm.onAssigneeChange(null) },
    )
}

@Composable
private fun StepPriority(state: CreateTaskViewModel.UiState, vm: CreateTaskViewModel) {
    Question("Priorytet")
    priorityRows().forEach { (priority, labels) ->
        ChoiceRow(
            title = labels.first,
            subtitle = labels.second,
            initials = priorityMark(priority),
            initialsColor = priorityColor(priority),
            selected = state.priority == priority,
            onClick = { vm.onPriorityChange(priority) },
        )
    }
}

@Composable
private fun StepDue(state: CreateTaskViewModel.UiState, onPickDate: () -> Unit) {
    Question("Graniczna data")
    OutlinedButton(
        onClick = onPickDate,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Icon(Icons.Default.DateRange, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(state.dueAtMillis?.let(::formatDate) ?: "Bez terminu")
    }
    Hint(
        if (state.dueAtMillis == null) {
            "Zadanie bez terminu trafia na koniec listy i nie generuje przypomnień."
        } else {
            "Termin możesz zmienić później na karcie zadania."
        }
    )
}

@Composable
private fun StepDone(state: CreateTaskViewModel.UiState, vm: CreateTaskViewModel) {
    Surface(
        color = Green600,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
            Spacer(Modifier.width(10.dp))
            Text(
                text = "Zadanie utworzone",
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }

    SummaryLine("Tytuł", state.title)
    SummaryLine("Opis", state.description.ifBlank { "—" })
    SummaryLine("Dotyczy", vm.subjectLabel(state))
    SummaryLine("Zespół", state.team?.label ?: "—")
    SummaryLine(
        "Osoba",
        state.teamMembers.firstOrNull { it.id == state.assigneeId }?.displayName
            ?: "bez przypisania",
    )
    SummaryLine("Priorytet", priorityLabel(state.priority))
    SummaryLine("Termin", state.dueAtMillis?.let(::formatDate) ?: "Bez terminu")
}

// ── Elementy wspólne ─────────────────────────────────────────────────────────

@Composable
private fun Question(text: String, subtitle: String? = null) {
    Spacer(Modifier.height(2.dp))
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
    )
    if (subtitle != null) {
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text = text.uppercase(Locale("pl")),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.Medium,
    )
}

@Composable
private fun Hint(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
        )
    }
}

@Composable
private fun TeamTile(
    team: TaskTeam,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val border = if (selected) Green600 else MaterialTheme.colorScheme.outlineVariant
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (selected) Green600.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
        modifier = modifier
            .height(94.dp)
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = team.icon,
                contentDescription = null,
                tint = if (selected) Green600 else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = team.label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) Green600 else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = team.hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Wiersz wyboru: awatar/znacznik + tytuł + podpis. Używany dla osób, klientów, projektów. */
@Composable
private fun ChoiceRow(
    title: String,
    subtitle: String,
    initials: String,
    selected: Boolean,
    onClick: () -> Unit,
    initialsColor: Color? = null,
) {
    val border = if (selected) Green600 else MaterialTheme.colorScheme.outlineVariant
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (selected) Green600.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(
                        color = initialsColor
                            ?: if (selected) Green600 else MaterialTheme.colorScheme.surfaceVariant,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (selected || initialsColor != null) Color.White
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** Duży przycisk mikrofonu; w trakcie nagrywania pulsuje czerwoną obwódką. */
@Composable
private fun MicButton(isRecording: Boolean, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "mic")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = if (isRecording) 1.12f else 1f,
        animationSpec = infiniteRepeatable(tween(750), RepeatMode.Reverse),
        label = "pulse",
    )
    Surface(
        shape = CircleShape,
        color = if (isRecording) Red600 else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .size(72.dp)
            .scale(if (isRecording) pulse else 1f)
            .clickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = if (isRecording) "Zakończ dyktowanie" else "Dyktuj opis",
                tint = if (isRecording) Color.White else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(84.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Banner błędu zsuwający się od góry. */
@Composable
private fun ResultBanner(visible: Boolean, message: String, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier,
    ) {
        Surface(
            color = Red600,
            shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
            shadowElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = message,
                color = Color.White,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            )
        }
    }
}

// ── Formatowanie ─────────────────────────────────────────────────────────────

private fun priorityRows(): List<Pair<TaskPriority, Pair<String, String>>> = listOf(
    TaskPriority.LOW to ("Niski" to "gdy będzie czas"),
    TaskPriority.NORMAL to ("Normalny" to "zwykły tryb"),
    TaskPriority.HIGH to ("Wysoki" to "na dziś, pilne"),
)

private fun priorityLabel(priority: TaskPriority): String =
    priorityRows().first { it.first == priority }.second.first

private fun priorityMark(priority: TaskPriority): String = when (priority) {
    TaskPriority.LOW -> "↓"
    TaskPriority.NORMAL -> "="
    TaskPriority.HIGH -> "↑"
}

private fun priorityColor(priority: TaskPriority): Color = when (priority) {
    TaskPriority.LOW -> Color(0xFF5B8DEF)
    TaskPriority.NORMAL -> Orange600
    TaskPriority.HIGH -> Red600
}

private fun initialsOf(name: String): String {
    val parts = name.trim().split(" ").filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(1).uppercase(Locale("pl"))
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase(Locale("pl"))
    }
}

private fun formatSeconds(seconds: Int): String =
    "%d:%02d".format(seconds / 60, seconds % 60)

private fun formatDate(millis: Long): String =
    SimpleDateFormat("d MMMM yyyy", Locale("pl")).format(Date(millis))
