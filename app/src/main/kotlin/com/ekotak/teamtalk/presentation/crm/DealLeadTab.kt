package com.ekotak.teamtalk.presentation.crm

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ekotak.teamtalk.domain.model.ArticleGate
import com.ekotak.teamtalk.domain.model.Deal
import com.ekotak.teamtalk.domain.model.DealBuildingKind
import com.ekotak.teamtalk.domain.model.KnowledgeArticle
import com.ekotak.teamtalk.domain.model.LeadBuilding
import com.ekotak.teamtalk.domain.model.LeadChannel
import com.ekotak.teamtalk.domain.model.LeadIntake
import com.ekotak.teamtalk.domain.model.MeetingKind
import com.ekotak.teamtalk.domain.model.TaskMember

/**
 * Zakładka „LEAD" karty deala — mobilny odpowiednik zakładki `lead`
 * z `DealDrawer` panelu. Kolejność bloków idzie od tego, co decyduje o losie
 * leada, do tego, co go tylko opisuje:
 *
 *  1. baner auto-kwalifikacji (lead czeka na decyzję człowieka — bez niej
 *     board360 sam go odrzuci po upływie okna),
 *  2. rodzaj budynku — jedno dotknięcie, a rozstrzyga, którym torem idzie
 *     dalej cała rozmowa (dom nowy vs modernizacja),
 *  3. zakres instalacji wybrany przez klienta, edytowalny na miejscu,
 *  4. artykuł wiedzy dla każdej wybranej instalacji, z wysyłką do klienta,
 *  5. spotkanie wstępne (jedyna akcja, którą handlowiec robi tu z telefonu),
 *  6. archiwum zgłoszenia z leadowni — zwinięte, bo przed spotkaniem czyta się
 *     je rzadko, a rozwinięte spychałoby pracę z leadem poza ekran.
 *
 * Bloki 2, 3 i 5 zapisują się od razu, bez trybu edycji: to pojedyncze wybory,
 * które handlowiec ustala przy kliencie, a nie formularz do wypełnienia.
 */
@Composable
fun DealLeadTab(
    state: DealDetailViewModel.UiState,
    onEdit: () -> Unit,
    onOpenArticle: (categoryId: String) -> Unit,
    viewModel: DealDetailViewModel,
) {
    val detail = state.detail ?: return
    val lead = state.lead
    val intake = lead.intake
    val error = lead.error

    QualificationBanner(detail.deal)

    BuildingKindCard(
        deal = detail.deal,
        canManage = state.canManage,
        isSaving = state.isSaving,
        onSelect = viewModel::setBuildingKind,
    )
    SectionGap()

    InstallationsCard(
        state = state,
        onToggleSelection = viewModel::toggleInstallation,
        onToggleBranch = viewModel::toggleInstallationBranch,
    )
    SectionGap()

    KnowledgeArticleCards(
        state = state,
        onOpenArticle = onOpenArticle,
        onAskSend = viewModel::askSendArticle,
    )

    MeetingCard(
        deal = detail.deal,
        members = state.members,
        canManage = state.canManage,
        isSaving = state.isSaving,
        onKindSelect = viewModel::setMeetingKind,
        onTermChange = viewModel::setMeetingAt,
        onEdit = onEdit,
    )
    SectionGap()

    when {
        // Dopóki pierwszy odczyt nie wróci, pokazujemy spinner zamiast treści —
        // inaczej między wejściem w zakładkę a startem żądania mignąłby
        // komunikat „deal nie pochodzi z leadowni".
        !lead.loaded -> Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }

        error != null -> SectionCard {
            SectionTitle("Zgłoszenie")
            SectionGap()
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { viewModel.loadLead(force = true) }) {
                Text("Spróbuj ponownie")
            }
        }

        intake == null -> SectionCard {
            SectionTitle("Zgłoszenie")
            SectionGap()
            Text(
                text = "Ten deal nie pochodzi z leadowni — został wpisany ręcznie " +
                    "w panelu. Dane kontaktowe i opis są w zakładce „Dane”.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        else -> {
            IntakeCard(intake)
            SectionGap()
            NoteCard(state = state, intake = intake, viewModel = viewModel)
            intake.building?.let { building ->
                SectionGap()
                LeadBuildingCard(building)
            }
        }
    }

    SendArticleDialog(state = state, viewModel = viewModel)
}

// ── Auto-kwalifikacja ────────────────────────────────────────────────────────

/**
 * Lead, którego automat nie zakwalifikował. Baner jest czerwony i stoi na samej
 * górze, bo to jedyny stan karty z terminem: bez decyzji człowieka board360
 * przenosi deal na „Stracone" po upływie okna (domyślnie 3 dni).
 */
@Composable
private fun QualificationBanner(deal: Deal) {
    if (!deal.qualReview) return

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "Lead do weryfikacji — niezakwalifikowany automatycznie",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = deal.qualReviewReason ?: "Wymaga decyzji osoby odpowiedzialnej za etap.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = buildString {
                    formatDateTime(deal.qualReviewAt)?.let {
                        append("Zgłoszono do decyzji: ").append(it).append(". ")
                    }
                    append(
                        "Przenieś na „Kwalifikacja” albo oznacz jako stracony — " +
                            "bez decyzji lead zostanie odrzucony automatycznie.",
                    )
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
    SectionGap()
}

// ── Rodzaj budynku ───────────────────────────────────────────────────────────

/**
 * Dom nowy czy modernizacja. Pole żyje na dealu (nie w zgłoszeniu), więc zapis
 * idzie zwykłym `PATCH`-em karty; bez `deal.manage` chipy pokazują wybór, ale
 * go nie przyjmują.
 */
@Composable
private fun BuildingKindCard(
    deal: Deal,
    canManage: Boolean,
    isSaving: Boolean,
    onSelect: (DealBuildingKind) -> Unit,
) {
    SectionCard {
        SectionTitle("Rodzaj budynku")
        SectionGap()
        PillChoiceRow(
            options = DealBuildingKind.entries,
            selected = deal.buildingKind,
            optionLabel = { it.label },
            onSelect = onSelect,
            enabled = canManage && !isSaving,
        )
    }
}

// ── Zakres instalacji ────────────────────────────────────────────────────────

/**
 * Drzewo katalogu z zaznaczeniem instalacji dla etapu LEAD. W panelu ta sama
 * migawka jest drzewem z drill-downem do marek — powtarzamy ten kształt, bo
 * wybór na telefonie i w panelu musi znaczyć dokładnie to samo.
 *
 * Prawo do zmiany daje `editable` z odpowiedzi API (etap przeszły bywa
 * zamrożony) w parze z `deal.manage`. Gdy zmieniać nie wolno, drzewo dalej się
 * rozwija — sam podgląd zakresu jest przydatny bez prawa edycji.
 */
@Composable
private fun InstallationsCard(
    state: DealDetailViewModel.UiState,
    onToggleSelection: (String) -> Unit,
    onToggleBranch: (String) -> Unit,
) {
    val lead = state.lead
    val selected = lead.selectedInstallations
    val editable = state.canManage && lead.installationsEditable && !lead.isSavingInstallations

    SectionCard {
        SectionTitle(
            text = "Zakres instalacji",
            accent = selected?.size?.takeIf { it > 0 }?.let { "$it wybrane" },
        )
        SectionGap()

        when {
            selected == null && !lead.loaded -> Text(
                text = "Wczytuję…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            selected == null || lead.catalog.isEmpty() -> Text(
                text = "Nie udało się wczytać katalogu instalacji.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            else -> {
                InstallationTree(
                    nodes = lead.catalog,
                    selected = selected,
                    expanded = lead.expanded,
                    editable = editable,
                    onToggleSelection = onToggleSelection,
                    onToggleBranch = onToggleBranch,
                )
                if (!state.canManage || !lead.installationsEditable) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (!state.canManage) {
                            "Podgląd — zmiana zakresu wymaga uprawnienia do edycji dealów."
                        } else {
                            "Migawka tego etapu jest zamknięta — zakres zmienia się " +
                                "na etapie bieżącym."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ── Artykuł wiedzy ───────────────────────────────────────────────────────────

/**
 * Kafel na każdą wybraną instalację. Nagłówkiem jest ścieżka w katalogu, bo to
 * ona mówi, o czym artykuł będzie — sama nazwa liścia („Powietrzna, split")
 * poza kontekstem gałęzi nic nie znaczy.
 *
 * Wiersz otwiera pełny artykuł na osobnym ekranie; link po prawej wysyła go
 * klientowi, ale dopiero po potwierdzeniu — to wiadomość wychodząca, więc
 * jedno przypadkowe dotknięcie nie może jej wysłać.
 */
@Composable
private fun KnowledgeArticleCards(
    state: DealDetailViewModel.UiState,
    onOpenArticle: (String) -> Unit,
    onAskSend: (String) -> Unit,
) {
    val lead = state.lead
    val selections = lead.selectedPaths
    if (selections.isEmpty()) return

    selections.forEach { selection ->
        val article = lead.articles[selection.categoryId]

        SectionCard {
            SectionTitle(
                text = selection.pathLabel,
                action = if (article != null && state.canManage) "wyślij klientowi ›" else null,
                onAction = if (article != null && state.canManage) {
                    { onAskSend(selection.categoryId) }
                } else {
                    null
                },
            )
            SectionGap()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onOpenArticle(selection.categoryId) }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Artykuł wiedzy",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = articleStatusLabel(article, lead.articleGate),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "otwórz ›",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        SectionGap()
    }
}

/**
 * Podpis pod „Artykuł wiedzy". Rozróżnia „nie ma jeszcze" od „nie da się
 * wygenerować" — pierwsze znaczy „otwórz i wygeneruj", drugie „uzupełnij dane".
 */
private fun articleStatusLabel(
    article: KnowledgeArticle?,
    gate: ArticleGate,
): String = when {
    article != null -> listOfNotNull(
        article.title.takeIf { it.isNotBlank() },
        formatDateTime(article.generatedAt)?.let { "wygenerowany $it" },
    ).joinToString(" · ").ifBlank { "Gotowy do wysłania" }

    gate.ready -> "Jeszcze niewygenerowany — otwórz, żeby przygotować"
    gate.reasons.isNotEmpty() -> gate.reasons.joinToString(" ")
    else -> "Jeszcze niewygenerowany"
}

/**
 * Potwierdzenie wysyłki artykułu. Pokazujemy kanał i tytuł, bo wiadomość
 * wychodzi do klienta na wątek deala i zobaczy ją potem cały zespół.
 */
@Composable
private fun SendArticleDialog(
    state: DealDetailViewModel.UiState,
    viewModel: DealDetailViewModel,
) {
    val categoryId = state.lead.sendingArticleFor ?: return
    val article = state.lead.articles[categoryId] ?: return
    val busy = state.lead.isSendingArticle

    AlertDialog(
        onDismissRequest = { if (!busy) viewModel.askSendArticle(null) },
        title = { Text("Wysłać artykuł klientowi?") },
        text = {
            Column {
                Text(
                    text = article.title.ifBlank { "Artykuł wiedzy" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Treść pójdzie wiadomością WhatsApp na wątek tego deala. " +
                        "Poza oknem 24 h od ostatniej wiadomości klienta WhatsApp " +
                        "odrzuci wysyłkę — wtedy odezwij się do klienta inaczej.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { viewModel.sendArticleToClient(categoryId) },
                enabled = !busy,
            ) { Text(if (busy) "Wysyłam…" else "Wyślij") }
        },
        dismissButton = {
            TextButton(
                onClick = { viewModel.askSendArticle(null) },
                enabled = !busy,
            ) { Text("Anuluj") }
        },
    )
}

// ── Spotkanie wstępne ────────────────────────────────────────────────────────

/**
 * Miejsce i termin spotkania wstępnego — dwa pola, które handlowiec ustala
 * w trakcie rozmowy, więc zmieniają się tutaj, jednym dotknięciem. Reszta
 * formularza (prowadzący, czas trwania, link) została na ekranie „pozostałych
 * pól"; nagłówek prowadzi tam skrótem, żeby nie szukać go po menu karty.
 */
@Composable
private fun MeetingCard(
    deal: Deal,
    members: List<TaskMember>,
    canManage: Boolean,
    isSaving: Boolean,
    onKindSelect: (MeetingKind) -> Unit,
    onTermChange: (Long?) -> Unit,
    onEdit: () -> Unit,
) {
    val pickTerm = rememberDateTimePicker(
        label = "Termin spotkania",
        millis = parseIsoMillis(deal.meetingAt),
    ) { onTermChange(it) }

    SectionCard {
        SectionTitle(
            text = "Spotkanie wstępne",
            action = if (canManage) "pozostałe pola" else null,
            onAction = if (canManage) onEdit else null,
        )
        SectionGap()

        PillChoiceRow(
            options = MeetingKind.entries,
            selected = deal.meetingKind,
            optionLabel = { it.label },
            onSelect = onKindSelect,
            enabled = canManage && !isSaving,
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = canManage && !isSaving, onClick = pickTerm)
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Termin",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = formatDateTime(deal.meetingAt) ?: "Ustal termin",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (deal.meetingAt != null) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }

        // Pozostałe pola spotkania tylko do odczytu — formularz mają na ekranie
        // edycji, a tutaj liczy się jedno spojrzenie przed wyjazdem do klienta.
        val byId = members.associateBy { it.id }
        InfoRow("Czas trwania", deal.meetingDurationMin?.let { "$it min" })
        InfoRow("Prowadzi", deal.meetingOwnerId?.let { byId[it]?.displayName ?: it })
        InfoRow("Link", deal.meetingUrl)
    }
}

// ── Zgłoszenie z leadowni ────────────────────────────────────────────────────

/**
 * Dane formularza z cennikinstalacji.pl. Kontakt i miejscowość zostają, choć są
 * też w zakładce „Dane": to wersja PODANA przez klienta, a nie poprawiona
 * później w kartotece — rozjazd między nimi bywa istotny.
 *
 * Blok jest zwinięty, bo to archiwum: czyta się je przy pierwszym kontakcie
 * i przy rozbieżności danych, a nie za każdym wejściem w kartę.
 */
@Composable
private fun IntakeCard(intake: LeadIntake) {
    CollapsibleSectionCard(
        title = "Zgłoszenie · ${intake.channelLabel}",
        summary = listOfNotNull(
            intake.sourceLabel ?: intake.source,
            formatDateTime(intake.createdAt),
        ).joinToString(" · ").ifBlank { null },
    ) {
        InfoRow("Źródło", intake.sourceLabel ?: intake.source)
        InfoRow("Zgłoszono", formatDateTime(intake.createdAt))
        InfoRow("Przyjął", intake.submittedBy)
        InfoRow("Podane nazwisko", intake.fullName)
        InfoRow("Podany telefon", intake.phone)
        InfoRow("Podany e-mail", intake.email)
        InfoRow("Miejscowość", intake.city)
        InfoRow("Zainteresowanie", intake.interest)
        InfoRow("Budżet", intake.budget)
        InfoRow("Zgoda marketingowa", if (intake.consent) "tak" else "nie")
    }
}

/**
 * Notatka z rozmowy (kanał „tel") albo uwagi klienta (targi/web). Jedyne pole
 * zgłoszenia, które wolno zmienić z telefonu — reszta to archiwum formularza.
 * „Zapisz" pojawia się dopiero przy realnej zmianie, tak jak w panelu.
 *
 * Rozwinięta, gdy notatka już jest: wtedy niesie ustalenia z ostatniej rozmowy,
 * czyli dokładnie to, co handlowiec chce zobaczyć przed następną.
 */
@Composable
private fun NoteCard(
    state: DealDetailViewModel.UiState,
    intake: LeadIntake,
    viewModel: DealDetailViewModel,
) {
    val lead = state.lead

    CollapsibleSectionCard(
        title = intake.noteLabel,
        summary = intake.note?.takeIf { it.isNotBlank() } ?: "Brak notatki.",
        initiallyExpanded = !intake.note.isNullOrBlank(),
    ) {
        if (!state.canManage) {
            Text(
                text = intake.note ?: "Brak notatki.",
                style = MaterialTheme.typography.bodyMedium,
                color = if (intake.note == null) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        } else {
            OutlinedTextField(
                value = lead.noteDraft,
                onValueChange = viewModel::onLeadNoteChange,
                modifier = Modifier.fillMaxWidth(),
                enabled = !lead.isSavingNote,
                minLines = 3,
                placeholder = {
                    Text(
                        text = if (intake.channel == LeadChannel.TEL) {
                            "Czego dotyczy, ustalenia, na co zwrócić uwagę…"
                        } else {
                            "Dodatkowe uwagi / wymagania klienta…"
                        },
                    )
                },
            )

            if (lead.isNoteDirty || lead.isSavingNote) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = viewModel::saveLeadNote, enabled = !lead.isSavingNote) {
                        Text(if (lead.isSavingNote) "Zapisuję…" else "Zapisz")
                    }
                    OutlinedButton(
                        onClick = { viewModel.onLeadNoteChange(lead.savedNote) },
                        enabled = !lead.isSavingNote,
                    ) { Text("Cofnij") }
                }
            }
        }
    }
}

/**
 * Dane budynku z kreatora /targi. To NIE są zweryfikowane dane budynku deala
 * (te stoją w zakładce „Dane") — klient wybierał tu opisowe warianty, więc
 * pokazujemy je dosłownie, bez przeliczania na liczby.
 */
@Composable
private fun LeadBuildingCard(building: LeadBuilding) {
    CollapsibleSectionCard(
        title = "Budynek wg zgłoszenia",
        summary = listOfNotNull(building.shape, building.area).joinToString(" · ")
            .ifBlank { null },
    ) {
        InfoRow("Rodzaj", building.shape)
        InfoRow("Konstrukcja", building.construction)
        InfoRow("Powierzchnia", building.area)
        InfoRow("Mieszkańcy", building.people)
        InfoRow("Kondygnacje", building.floors?.toString())
        InfoRow("Etap budowy", building.stage)
        InfoRow("Okna", building.windows)
        InfoRow("Piwnica", if (building.heatedBasement) "ogrzewana" else null)
        InfoRow("Garaż", if (building.heatedGarage) "ogrzewany" else null)
    }
}
