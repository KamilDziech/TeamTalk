package com.ekotak.teamtalk.presentation.lead

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Construction
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Recommend
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ekotak.teamtalk.domain.model.FloorHeatingVariant
import com.ekotak.teamtalk.domain.model.LEAD_HEAT_SOURCES
import com.ekotak.teamtalk.domain.model.LeadChannel
import com.ekotak.teamtalk.domain.model.LeadConstruction
import com.ekotak.teamtalk.domain.model.LeadInterest
import com.ekotak.teamtalk.domain.model.LeadOccupancy
import com.ekotak.teamtalk.domain.model.LeadOrigin
import com.ekotak.teamtalk.domain.model.LeadProjectKind
import com.ekotak.teamtalk.domain.model.LeadShape
import com.ekotak.teamtalk.domain.model.LeadSubmitResult
import com.ekotak.teamtalk.domain.model.RenovationWorks
import com.ekotak.teamtalk.presentation.components.AppTopBar
import com.ekotak.teamtalk.presentation.theme.Green600
import com.ekotak.teamtalk.presentation.theme.Orange600
import com.ekotak.teamtalk.presentation.theme.Red600

/** Od tylu osób lista „Kto przyjmuje kontakt?" dostaje wyszukiwarkę. */
private const val SEARCHABLE_PEOPLE = 6

/**
 * Kreator LEAD — kafelek pulpitu (makieta `design/mockups/modul-lead.html`,
 * ustalenia 2026-09-13). Pytania idą w kolejności rozmowy: skąd kontakt, czego
 * klient chce, jaki to dom — a imię, e-mail i miejscowość na końcu. Po pytaniu
 * „w budowie czy zamieszkały" ścieżka się rozdziela.
 */
@Composable
fun LeadWizardScreen(
    onNavigateBack: () -> Unit,
    onOpenDeal: (String) -> Unit,
    viewModel: LeadWizardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    // Pasek z „Wróć" jest zdjęty (AppTopBar) — cofanie między planszami idzie gestem.
    BackHandler { if (!viewModel.back()) onNavigateBack() }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Nowy lead",
                onNavigateBack = onNavigateBack,
                actions = {
                    Text(
                        text = if (state.isDone) "gotowe" else "Nowy lead · ${state.step.crumb}  ${state.stepCountLabel}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 4.dp),
                    )
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.Close, contentDescription = "Zamknij kreator")
                    }
                },
            )
        },
        bottomBar = {
            Footer(
                state = state,
                onBack = { if (!viewModel.back()) onNavigateBack() },
                onNext = viewModel::next,
                onSave = viewModel::save,
                onAgain = viewModel::startOver,
                onOpenDeal = onOpenDeal,
                onClose = onNavigateBack,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            if (!state.isDone) StepProgress(state)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.error?.let { ErrorBar(it) }
                if (state.isDone) {
                    StepDone(state)
                } else {
                    when (state.step) {
                        LeadStep.KONTAKT -> StepContact(state, viewModel)
                        LeadStep.ZAKRES -> StepInterest(state, viewModel)
                        LeadStep.PROJEKT -> StepProject(state, viewModel)
                        LeadStep.BRYLA -> StepShape(state, viewModel)
                        LeadStep.PODLOGOWKA -> StepFloorHeating(state, viewModel)
                        LeadStep.REMONT -> StepRenovation(state, viewModel)
                        LeadStep.DANE -> StepClient(state, viewModel)
                        LeadStep.PODSUMOWANIE -> StepSummary(state, viewModel)
                    }
                }
            }
        }
    }
}

// ── Ramy ─────────────────────────────────────────────────────────────────────

@Composable
private fun StepProgress(state: LeadWizardViewModel.UiState) {
    val total = state.steps.size
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(total) { i ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
                    .background(
                        color = when {
                            i <= state.stepIndex -> Green600
                            // Przed rozwidleniem nie wiadomo, ile plansz dojdzie — kreska przygaszona.
                            state.occupancy == null && i == 2 -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = RoundedCornerShape(2.dp),
                    ),
            )
        }
    }
}

@Composable
private fun Footer(
    state: LeadWizardViewModel.UiState,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSave: () -> Unit,
    onAgain: () -> Unit,
    onOpenDeal: (String) -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (state.isDone) {
            OutlinedButton(
                onClick = onAgain,
                modifier = Modifier.weight(1f).height(52.dp),
                shape = RoundedCornerShape(12.dp),
            ) { Text("Dodaj kolejny") }
            val created = state.result as? LeadSubmitResult.Created
            PrimaryButton(
                text = if (created != null) "Otwórz kartę" else "Gotowe",
                enabled = true,
                onClick = { if (created != null) onOpenDeal(created.dealId) else onClose() },
            )
            return@Row
        }

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.height(52.dp),
            enabled = !state.isSaving,
            shape = RoundedCornerShape(12.dp),
        ) { Text(if (state.stepIndex == 0) "Anuluj" else "Wstecz") }

        val last = state.step == LeadStep.PODSUMOWANIE
        PrimaryButton(
            text = if (last) "Zapisz lead" else "Dalej",
            enabled = state.canLeave(state.step) && !state.isSaving,
            loading = state.isSaving,
            onClick = if (last) onSave else onNext,
        )
    }
}

@Composable
private fun RowScope.PrimaryButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    loading: Boolean = false,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.weight(1f).height(52.dp),
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = Green600),
        shape = RoundedCornerShape(12.dp),
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = MaterialTheme.colorScheme.onPrimary,
                strokeWidth = 2.dp,
            )
        } else {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

// ── 1. Kontakt ───────────────────────────────────────────────────────────────

@Composable
private fun StepContact(state: LeadWizardViewModel.UiState, vm: LeadWizardViewModel) {
    if (state.pendingCount > 0) {
        Notice(
            icon = Icons.Default.CloudOff,
            text = if (state.pendingCount == 1) "1 lead czeka na zasięg — wyśle się sam."
            else "${state.pendingCount} leady czekają na zasięg — wyślą się same.",
        )
    }

    Question("Skąd jest kontakt?")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LeadChannel.WIZARD.forEach { channel ->
            Tile(
                label = channel.label,
                icon = channelIcon(channel),
                selected = state.channel == channel,
                onClick = { vm.onChannel(channel) },
                modifier = Modifier.weight(1f),
                compact = true,
            )
        }
    }

    if (state.channel == LeadChannel.TARGI) {
        Label("Które targi?")
        when {
            !state.eventsLoaded -> Hint("Wczytuję listę wydarzeń…")
            state.events.isEmpty() -> Hint("Nie ma listy wydarzeń (brak zasięgu albo pusty Marketing) — lead zapisze się jako „targi”.")
            else -> ChipFlow {
                state.events.forEach { ev ->
                    Chip(ev.name, selected = state.eventId == ev.id, onClick = { vm.onEvent(ev.id) })
                }
            }
        }
    }
    if (state.channel == LeadChannel.POLECENIE) {
        Hint("W danych klienta od razu zaznaczymy rekomendację i zapytamy, kto polecił.")
    }

    SubQuestion("Kto przyjmuje kontakt?")
    val people = state.peopleSorted
    if (people.isEmpty()) {
        // Pierwsze uruchomienie bez sieci: listy osób jeszcze nie ma, ale siebie znamy z sesji.
        state.selfId?.let { self ->
            PersonRow(
                name = state.selfName ?: "Ty",
                subtitle = "lista zespołu wczyta się po połączeniu",
                initials = initialsOf(state.selfName ?: "Ty"),
                selected = state.takenById == self,
                isSelf = true,
                onClick = { vm.onTaker(self) },
            )
        }
        return
    }

    val collapsed = !state.showAllPeople && people.size > SEARCHABLE_PEOPLE
    val visible = when {
        // Zwykle przyjmuje ten, kto trzyma telefon — zamiast przewijać cały zespół
        // pokazujemy wybraną osobę i przycisk do zmiany.
        collapsed -> people.filter { it.id == state.takenById }.ifEmpty { people.take(1) }
        state.peopleQuery.isBlank() -> people
        else -> people.filter { it.displayName.contains(state.peopleQuery.trim(), ignoreCase = true) }
    }
    if (!collapsed && people.size > SEARCHABLE_PEOPLE) {
        OutlinedTextField(
            value = state.peopleQuery,
            onValueChange = vm::onPeopleQuery,
            placeholder = { Text("Szukaj osoby") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        )
    }
    visible.forEach { person ->
        PersonRow(
            name = person.displayName,
            subtitle = person.role.orEmpty(),
            initials = person.initials,
            selected = state.takenById == person.id,
            isSelf = person.id == state.selfId,
            onClick = { vm.onTaker(person.id) },
        )
    }
    if (collapsed) {
        OutlinedButton(
            onClick = vm::onShowAllPeople,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        ) { Text("Ktoś inny z zespołu") }
    }
}

// ── 2. Zainteresowanie ───────────────────────────────────────────────────────

@Composable
private fun StepInterest(state: LeadWizardViewModel.UiState, vm: LeadWizardViewModel) {
    Question("Czym jest zainteresowany klient?")
    LeadInterest.entries.forEach { interest ->
        WideTile(
            title = interest.label,
            subtitle = "wodne, pod wylewkę albo system suchy",
            icon = Icons.Default.Waves,
            selected = state.interest == interest,
            onClick = { vm.onInterest(interest) },
        )
    }
    Hint("Kolejne instalacje dojdą tu później — pompy ciepła, rekuperacja, fotowoltaika…")

    SubQuestion("Dom jest w budowie czy już zamieszkały?")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Tile(
            label = LeadOccupancy.W_BUDOWIE.label,
            hint = "nowy dom",
            icon = Icons.Default.Construction,
            selected = state.occupancy == LeadOccupancy.W_BUDOWIE,
            onClick = { vm.onOccupancy(LeadOccupancy.W_BUDOWIE) },
            modifier = Modifier.weight(1f),
        )
        Tile(
            label = LeadOccupancy.ZAMIESZKALY.label,
            hint = "modernizacja",
            icon = Icons.Default.Home,
            selected = state.occupancy == LeadOccupancy.ZAMIESZKALY,
            onClick = { vm.onOccupancy(LeadOccupancy.ZAMIESZKALY) },
            modifier = Modifier.weight(1f),
        )
    }
}

// ── Nowy dom ─────────────────────────────────────────────────────────────────

@Composable
private fun StepProject(state: LeadWizardViewModel.UiState, vm: LeadWizardViewModel) {
    Branch("Nowy dom")
    Question("Czy dom jest z projektu?")
    LeadProjectKind.entries.forEach { kind ->
        OptionRow(
            title = kind.label,
            subtitle = kind.hint,
            selected = state.projectKind == kind,
            onClick = { vm.onProjectKind(kind) },
        )
    }
    if (state.projectKind == LeadProjectKind.KATALOG) {
        OutlinedTextField(
            value = state.projectName,
            onValueChange = vm::onProjectName,
            placeholder = { Text("Nazwa projektu") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        )
    }

    SubQuestion("Z czego jest dom?")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        LeadConstruction.entries.forEach { c ->
            Tile(
                label = c.short,
                selected = state.construction == c,
                onClick = { vm.onConstruction(c) },
                modifier = Modifier.weight(1f),
                compact = true,
            )
        }
    }

    SubQuestion("Piwnica lub garaż?")
    ChipFlow {
        Chip("Piwnica", selected = state.basement, onClick = vm::onBasement)
        Chip("Garaż w bryle", selected = state.garage, onClick = vm::onGarage)
        Chip("Brak", selected = !state.basement && !state.garage, onClick = vm::onNoBasementGarage)
    }

    if (state.needsShape) {
        Hint("Bez nazwy projektu nie pobierzemy rzutu — na następnej planszy zapytamy o bryłę i metraż.")
    }
}

@Composable
private fun StepShape(state: LeadWizardViewModel.UiState, vm: LeadWizardViewModel) {
    Branch(if (state.projectKind == LeadProjectKind.WLASNY) "Nowy dom · projekt własny" else "Nowy dom · projekt bez nazwy")
    Question("Jaki to dom?")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        LeadShape.entries.forEach { shape ->
            Tile(
                label = shape.wire,
                selected = state.shape == shape,
                onClick = { vm.onShape(shape) },
                modifier = Modifier.weight(1f),
                compact = true,
            )
        }
    }

    SubQuestion("Ile m² ma dom?")
    AreaField(value = state.area, onChange = vm::onArea, placeholder = "np. 140")
    ChipFlow {
        listOf("100", "120", "140", "160", "200").forEach { v ->
            Chip(v, selected = state.area == v, onClick = { vm.onArea(v) })
        }
    }
    Hint("Powierzchnia użytkowa z projektu albo „na oko” od klienta — doprecyzuje ją audyt.")
}

@Composable
private fun StepFloorHeating(state: LeadWizardViewModel.UiState, vm: LeadWizardViewModel) {
    val construction = state.construction
    Branch("Nowy dom · ${construction?.short?.lowercase().orEmpty()}")

    val parter = state.shape == LeadShape.PARTEROWY
    if (state.lightFloor) {
        Question(
            if (parter) "Czy wycenić system suchy (podłogówka lekka)?"
            else "Czy na piętrze wycenić system suchy (podłogówka lekka)?",
            if (state.lightSlab) "strop jest lekki" else "dom ${construction?.short?.lowercase()} — strop lekki",
        )
    } else {
        Question("Czy wszędzie standardowe ogrzewanie podłogowe?", "rury w wylewce na styropianie")
    }

    state.variantOptions.forEach { v ->
        val (title, subtitle) = when (v) {
            FloorHeatingVariant.SUCHY ->
                (if (parter) "Tak, system suchy" else "Tak, system suchy na piętrze") to
                    (if (parter) null else "parter standardowo pod wylewkę")
            FloorHeatingVariant.STANDARD ->
                (if (state.lightFloor) "Nie, wszędzie standardowe" else "Tak, wszędzie standardowe") to null
            FloorHeatingVariant.OPIS ->
                (if (state.lightFloor) "Jeszcze inaczej — opiszę" else "Nie — opiszę, czego potrzeba") to null
        }
        OptionRow(title = title, subtitle = subtitle, selected = state.variant == v, onClick = { vm.onVariant(v) })
    }
    if (state.variant == FloorHeatingVariant.OPIS) {
        OutlinedTextField(
            value = state.variantNote,
            onValueChange = vm::onVariantNote,
            placeholder = { Text("Czego potrzebuje klient? np. łazienki na drabinkach, salon z podłogówką") },
            minLines = 3,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        )
    }

    if (construction != null && !construction.light) {
        CheckRow(
            title = "Strop jest lekki (drewniany)",
            subtitle = "zmienia pytanie na system suchy",
            checked = state.lightSlab,
            onClick = vm::onLightSlab,
        )
    }

    Label("Opcja")
    CheckRow(
        title = "Mam gotowe wylewki od dewelopera",
        subtitle = "trzeba je frezować",
        checked = state.milling,
        onClick = vm::onMilling,
    )
}

// ── Dom zamieszkały ──────────────────────────────────────────────────────────

@Composable
private fun StepRenovation(state: LeadWizardViewModel.UiState, vm: LeadWizardViewModel) {
    Branch("Dom zamieszkały")
    Question("Co chcesz zrobić?")
    RenovationWorks.entries.forEach { w ->
        OptionRow(title = w.label, subtitle = w.hint, selected = state.works == w, onClick = { vm.onWorks(w) })
    }

    SubQuestion(state.works?.areaQuestion ?: "Ile m² obejmą prace?")
    AreaField(value = state.worksArea, onChange = vm::onWorksArea, placeholder = "np. 90")

    SubQuestion("Źródło grzewcze")
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        SegmentedButton(
            selected = !state.heatPlanned,
            onClick = { vm.onHeatPlanned(false) },
            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
        ) { Text("jest teraz") }
        SegmentedButton(
            selected = state.heatPlanned,
            onClick = { vm.onHeatPlanned(true) },
            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
        ) { Text("ma być") }
    }
    ChipFlow {
        LEAD_HEAT_SOURCES.forEach { source ->
            Chip(source, selected = state.heatSource == source, onClick = { vm.onHeatSource(source) })
        }
    }
}

// ── Dane klienta ─────────────────────────────────────────────────────────────

@Composable
private fun StepClient(state: LeadWizardViewModel.UiState, vm: LeadWizardViewModel) {
    Question("Ok, jeszcze dane do przygotowania oferty")

    Label("Kod pocztowy / miejscowość")
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = state.postalCode,
            onValueChange = vm::onPostalCode,
            placeholder = { Text("00-000") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.width(112.dp),
            shape = RoundedCornerShape(12.dp),
        )
        OutlinedTextField(
            value = state.city,
            onValueChange = vm::onCity,
            placeholder = { Text("Miejscowość") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(12.dp),
        )
    }

    TextInput("Imię i nazwisko", state.fullName, vm::onFullName, "Jan Kowalski",
        KeyboardOptions(capitalization = KeyboardCapitalization.Words))
    TextInput("Telefon", state.phone, vm::onPhone, "600 000 000",
        KeyboardOptions(keyboardType = KeyboardType.Phone))
    TextInput("E-mail", state.email, vm::onEmail, "jan@example.pl",
        KeyboardOptions(keyboardType = KeyboardType.Email))
    Hint("Wystarczy telefon albo e-mail.")

    SubQuestion("Skąd o nas wie?")
    ChipFlow {
        LeadOrigin.entries.forEach { o ->
            Chip(o.label, selected = state.origin == o, onClick = { vm.onOrigin(o) })
        }
    }
    if (state.origin == LeadOrigin.REKOMENDACJA) {
        OutlinedTextField(
            value = state.referralFrom,
            onValueChange = vm::onReferral,
            placeholder = { Text("Kto polecił?") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
        )
    }
}

// ── Podsumowanie i koniec ────────────────────────────────────────────────────

@Composable
private fun StepSummary(state: LeadWizardViewModel.UiState, vm: LeadWizardViewModel) {
    Question("Sprawdź i zapisz")
    summaryGroups(state).forEach { group ->
        SummaryCard(title = group.title, onEdit = { vm.goTo(group.step) }, lines = group.lines)
    }
}

@Composable
private fun StepDone(state: LeadWizardViewModel.UiState) {
    val queued = state.result is LeadSubmitResult.Queued
    Surface(color = Green600, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color.White)
            Spacer(Modifier.width(10.dp))
            Column {
                Text(
                    text = if (queued) "Lead zapisany — czeka na wysyłkę" else "Lead zapisany",
                    color = Color.White,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = if (queued) "wyśle się sam, gdy wróci zasięg" else "karta trafiła do lejka, etap LEAD",
                    color = Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
    if (queued) {
        Notice(
            icon = Icons.Default.CloudOff,
            text = "Brak zasięgu. Jeśli serwer nie przyjmie leada, dostaniesz powiadomienie z nazwiskiem klienta.",
        )
    }
    SummaryCard(
        title = state.fullName.trim().ifBlank { "Nowy klient" },
        onEdit = null,
        lines = listOf(
            "Instalacja" to (state.interest?.label ?: "—"),
            "Dom" to (state.occupancy?.let { if (it == LeadOccupancy.W_BUDOWIE) "w budowie" else "zamieszkały" } ?: "—"),
            "Miejscowość" to listOf(state.postalCode, state.city).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "—" },
            "Kontakt" to listOf(state.phone, state.email).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "—" },
        ),
    )
    Hint("Powiadomienie o nowym leadzie dostaje biuro — tak samo jak z leadowni.")
}

private data class SummaryGroup(val title: String, val step: LeadStep, val lines: List<Pair<String, String>>)

private fun summaryGroups(s: LeadWizardViewModel.UiState): List<SummaryGroup> = buildList {
    val event = s.events.firstOrNull { it.id == s.eventId }?.name
    add(
        SummaryGroup(
            "Kontakt", LeadStep.KONTAKT,
            listOf(
                "Kanał" to listOfNotNull(s.channel?.label, event).joinToString(" · ").ifBlank { "—" },
                "Przyjmuje" to (s.takerName ?: "—"),
            ),
        ),
    )
    when (s.occupancy) {
        LeadOccupancy.W_BUDOWIE -> {
            val project = when (s.projectKind) {
                LeadProjectKind.KATALOG -> s.projectName.trim()
                LeadProjectKind.WLASNY -> "projekt własny"
                LeadProjectKind.NIE_PAMIETA -> "z projektu, bez nazwy"
                null -> "—"
            }
            val extras = listOfNotNull("piwnica".takeIf { s.basement }, "garaż".takeIf { s.garage })
            val variant = when (s.variant) {
                FloorHeatingVariant.STANDARD -> "wszędzie standardowe"
                FloorHeatingVariant.SUCHY -> if (s.shape == LeadShape.PARTEROWY) "system suchy" else "system suchy na piętrze"
                FloorHeatingVariant.OPIS -> s.variantNote.trim().ifBlank { "opis" }
                null -> "—"
            }
            val lines = mutableListOf(
                "Instalacja" to (s.interest?.label ?: "—"),
                "Dom" to "w budowie",
                "Projekt" to project,
                "Konstrukcja" to (s.construction?.wire ?: "—"),
                "Piwnica / garaż" to extras.joinToString(", ").ifBlank { "brak" },
            )
            if (s.needsShape) lines += "Bryła" to listOfNotNull(s.shape?.wire, s.areaM2?.let { "$it m²" }).joinToString(" · ")
            lines += "Podłogówka" to variant + if (s.lightSlab) " · lekki strop" else ""
            if (s.milling) lines += "Opcja" to "frezowanie wylewek dewelopera"
            add(SummaryGroup("Dom w budowie", LeadStep.PROJEKT, lines))
        }
        LeadOccupancy.ZAMIESZKALY -> add(
            SummaryGroup(
                "Dom zamieszkały", LeadStep.REMONT,
                listOf(
                    "Instalacja" to (s.interest?.label ?: "—"),
                    "Prace" to (s.works?.label ?: "—"),
                    "Powierzchnia" to (s.worksAreaM2?.let { "$it m²" } ?: "—"),
                    "Źródło ciepła" to "${s.heatSource ?: "—"} (${if (s.heatPlanned) "ma być" else "jest"})",
                ),
            ),
        )
        null -> Unit
    }
    add(
        SummaryGroup(
            "Klient", LeadStep.DANE,
            listOf(
                "Imię i nazwisko" to s.fullName.trim().ifBlank { "—" },
                "Miejscowość" to listOf(s.postalCode, s.city).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "—" },
                "Telefon" to s.phone.trim().ifBlank { "—" },
                "E-mail" to s.email.trim().ifBlank { "—" },
                "Skąd o nas wie" to (
                    s.origin?.let { o ->
                        o.label + if (o == LeadOrigin.REKOMENDACJA && s.referralFrom.isNotBlank()) ": ${s.referralFrom.trim()}" else ""
                    } ?: "—"
                    ),
            ),
        ),
    )
}

// ── Klocki ───────────────────────────────────────────────────────────────────

private fun channelIcon(channel: LeadChannel): ImageVector = when (channel) {
    LeadChannel.SPOTKANIE -> Icons.Default.Groups
    LeadChannel.POLECENIE -> Icons.Default.Recommend
    LeadChannel.TARGI -> Icons.Default.Storefront
    else -> Icons.Default.Phone
}

@Composable
private fun Question(text: String, subtitle: String? = null) {
    Spacer(Modifier.height(2.dp))
    Text(text = text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    if (subtitle != null) {
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SubQuestion(text: String) {
    Spacer(Modifier.height(4.dp))
    Text(text = text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun Label(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Znacznik gałęzi — przypomina, na której ścieżce kreatora jesteśmy. */
@Composable
private fun Branch(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(14.dp).height(2.dp).background(MaterialTheme.colorScheme.primary))
        Spacer(Modifier.width(8.dp))
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
    }
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
private fun Notice(icon: ImageVector, text: String) {
    Surface(
        color = Orange600.copy(alpha = 0.14f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = Orange600, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ErrorBar(message: String) {
    Surface(color = Red600, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            text = message,
            color = Color.White,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun Tile(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    hint: String? = null,
    compact: Boolean = false,
) {
    val shape = RoundedCornerShape(if (compact) 12.dp else 14.dp)
    Surface(
        shape = shape,
        color = if (selected) Green600.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
        modifier = modifier
            .heightIn(min = if (compact) 64.dp else 88.dp)
            .border(1.dp, if (selected) Green600 else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (selected) Green600 else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.height(6.dp))
            }
            Text(
                text = label,
                style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleSmall,
                color = if (selected) Green600 else MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (hint != null) {
                Text(
                    text = hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun WideTile(title: String, subtitle: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        shape = shape,
        color = if (selected) Green600.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (selected) Green600 else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = if (selected) Green600 else MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) Green600 else MaterialTheme.colorScheme.onSurface,
                )
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (selected) Icon(Icons.Default.Check, contentDescription = null, tint = Green600)
        }
    }
}

/** Wiersz jednokrotnego wyboru (kółko) albo przełącznika (kwadrat). */
@Composable
private fun SelectRow(
    title: String,
    subtitle: String?,
    selected: Boolean,
    square: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        shape = shape,
        color = if (selected) Green600.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (selected) Green600 else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            val markShape = if (square) RoundedCornerShape(5.dp) else CircleShape
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .border(2.dp, if (selected) Green600 else MaterialTheme.colorScheme.outline, markShape)
                    .background(if (selected && square) Green600 else Color.Transparent, markShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
                    if (square) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    } else {
                        Box(Modifier.size(9.dp).background(Green600, CircleShape))
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                if (!subtitle.isNullOrBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun OptionRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit) =
    SelectRow(title, subtitle, selected, square = false, onClick = onClick)

@Composable
private fun CheckRow(title: String, subtitle: String?, checked: Boolean, onClick: () -> Unit) =
    SelectRow(title, subtitle, checked, square = true, onClick = onClick)

@Composable
private fun PersonRow(
    name: String,
    subtitle: String,
    initials: String,
    selected: Boolean,
    isSelf: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        shape = shape,
        color = if (selected) Green600.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (selected) Green600 else MaterialTheme.colorScheme.outlineVariant, shape)
            .clickable(onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(if (selected) Green600 else MaterialTheme.colorScheme.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    initials,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (subtitle.isNotBlank()) {
                    Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                }
            }
            if (isSelf) {
                Surface(color = Green600, shape = RoundedCornerShape(50)) {
                    Text(
                        "TY",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChipFlow(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) { content() }
}

@Composable
private fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = Green600.copy(alpha = 0.15f),
            selectedLabelColor = Green600,
        ),
    )
}

@Composable
private fun AreaField(value: String, onChange: (String) -> Unit, placeholder: String) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(placeholder) },
        suffix = { Text("m²") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    )
}

@Composable
private fun TextInput(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    keyboard: KeyboardOptions,
) {
    Label(label)
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(placeholder) },
        singleLine = true,
        keyboardOptions = keyboard,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
    )
}

@Composable
private fun SummaryCard(title: String, onEdit: (() -> Unit)?, lines: List<Pair<String, String>>) {
    val shape = RoundedCornerShape(14.dp)
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (onEdit != null) {
                    Text(
                        "zmień",
                        style = MaterialTheme.typography.labelMedium,
                        color = Green600,
                        modifier = Modifier.clickable(onClick = onEdit).padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
            }
            lines.forEach { (label, value) ->
                Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(110.dp),
                    )
                    Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

private fun initialsOf(name: String): String =
    name.split(' ', '.', '-').filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
