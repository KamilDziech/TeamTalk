package com.ekotak.teamtalk.presentation.crm

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ekotak.teamtalk.domain.model.Audit
import com.ekotak.teamtalk.domain.model.AuditAddressKind
import com.ekotak.teamtalk.domain.model.BuildingStandard
import com.ekotak.teamtalk.domain.model.Deal
import com.ekotak.teamtalk.domain.model.HeatloadMode
import com.ekotak.teamtalk.domain.model.OfferLock
import com.ekotak.teamtalk.domain.model.TaskMember

/**
 * Zakładka „Audyt" karty deala — mobilny odpowiednik zakładki `audyt`
 * z `DealDrawer`. Trzy bloki, w kolejności, w jakiej audytor ich używa:
 *
 *  1. **Spotkanie audytowe** — termin, miejsce i nawigacja. To jedyna rzecz,
 *     której się szuka JADĄC do klienta, więc stoi na górze (w panelu jest tak
 *     samo: zielony pasek nad resztą zakładki).
 *  2. **Audyt instalacji** — formularz dziedziczony z katalogu Technologia,
 *     wypełniany NA MIEJSCU. To on jest podstawą oferty, więc to on ma tu
 *     najwięcej miejsca.
 *  3. **Heizlast** — zapotrzebowanie budynku na ciepło; osobna lista wpisów,
 *     dokładana zwykle po obejściu domu.
 *
 * Czego świadomie NIE ma na telefonie:
 *  • rysowania po rzucie kondygnacji (kropki rozdzielaczy, pomiar metrażu) —
 *    patrz `UfhAudit.kt`; zapisane wartości przechodzą przez telefon nietknięte,
 *  • automatu zmiany oferty po podpisie umowy (nowa umowa / aneks). Formularz
 *    jest wtedy do odczytu i mówi wprost, że zmianę robi się w panelu: to ruch
 *    kończący się dokumentem do podpisu, a nie coś, co robi się na parkingu.
 */
@Composable
fun DealAuditTab(
    state: DealDetailViewModel.UiState,
    onEdit: () -> Unit,
    viewModel: DealDetailViewModel,
) {
    val detail = state.detail ?: return
    val audit = state.audit

    AuditMeetingCard(
        deal = detail.deal,
        members = state.members,
        canManage = state.canManage,
        isSaving = state.isSaving,
        onKindSelect = viewModel::setAuditAddressKind,
        onTermChange = viewModel::setAuditMeetingAt,
        onEdit = onEdit,
    )
    SectionGap()

    when {
        // Dopóki pierwszy odczyt nie wróci, spinner zamiast treści — inaczej
        // między wejściem w zakładkę a startem żądania mignąłby komunikat
        // „ten deal nie ma instalacji do audytu".
        !audit.loaded -> Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }

        else -> {
            InstallationAuditCard(state = state, viewModel = viewModel)
            SectionGap()
            HeatloadCard(state = state, viewModel = viewModel)
        }
    }

    if (audit.loaded && audit.error != null) {
        SectionGap()
        SectionCard {
            SectionTitle("Audyty")
            SectionGap()
            Text(
                text = audit.error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { viewModel.loadAudit(force = true) }) {
                Text("Spróbuj ponownie")
            }
        }
    }
}

// ── Spotkanie audytowe ───────────────────────────────────────────────────────

/**
 * Termin i miejsce audytu. Miejsce i termin zapisują się od razu, bez trybu
 * edycji — to pojedyncze wybory ustalane w rozmowie z klientem. Adres, czas
 * i osoba wykonująca zostają do odczytu: mają swój formularz („pozostałe pola"),
 * a tutaj liczy się jedno spojrzenie przed wyjazdem.
 */
@Composable
private fun AuditMeetingCard(
    deal: Deal,
    members: List<TaskMember>,
    canManage: Boolean,
    isSaving: Boolean,
    onKindSelect: (AuditAddressKind) -> Unit,
    onTermChange: (Long?) -> Unit,
    onEdit: () -> Unit,
) {
    val context = LocalContext.current
    val pickTerm = rememberDateTimePicker(
        label = "Termin audytu",
        millis = parseIsoMillis(deal.auditMeetingAt),
    ) { onTermChange(it) }

    SectionCard {
        SectionTitle(
            text = "Spotkanie audytowe",
            action = if (canManage) "pozostałe pola" else null,
            onAction = if (canManage) onEdit else null,
        )
        SectionGap()

        PillChoiceRow(
            options = AuditAddressKind.entries,
            selected = deal.auditAddressKind,
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
                text = formatDateTime(deal.auditMeetingAt) ?: "Ustal termin",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (deal.auditMeetingAt != null) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }

        val byId = members.associateBy { it.id }
        InfoRow("Adres", deal.auditAddress)
        InfoRow("Wykonuje", deal.auditOwnerId?.let { byId[it]?.displayName ?: it })

        // Akcja miejsca — dokładnie jak w panelu: u klienta prowadzimy trasę,
        // online otwieramy link spotkania. Przy audycie w biurze nie ma czego
        // otwierać, więc przycisku po prostu nie ma.
        val target = when (deal.auditAddressKind) {
            AuditAddressKind.INSTALACJA -> deal.auditAddress?.takeIf { it.isNotBlank() }
                ?.let { "Wyznacz trasę" to Uri.parse("geo:0,0?q=${Uri.encode(it)}") }
            AuditAddressKind.ONLINE -> deal.meetingUrl?.takeIf { it.isNotBlank() }
                ?.let { "Otwórz spotkanie" to Uri.parse(it) }
            else -> null
        }
        if (target != null) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, target.second)) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(target.first) }
        }
    }
}

// ── Audyt instalacji ─────────────────────────────────────────────────────────

@Composable
private fun InstallationAuditCard(
    state: DealDetailViewModel.UiState,
    viewModel: DealDetailViewModel,
) {
    val audit = state.audit

    SectionCard {
        SectionTitle(
            text = "Audyt instalacji",
            accent = when {
                // Kolejka przed stanem zapisu: „czeka na wysyłkę" jest dla
                // audytora ważniejsze niż to, że rekord w ogóle istnieje.
                audit.isFormPending -> "czeka na wysyłkę"
                audit.formAuditId != null -> "zapisany"
                else -> null
            },
        )
        SectionGap()

        if (audit.installations.isEmpty()) {
            Text(
                text = "Na etapie „Audyt” nie ma jeszcze wybranych instalacji. " +
                    "Zakres ustala się w zakładce „LEAD”, a potem przenosi na kolejne etapy.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }

        // Wybór instalacji jako pasek — w panelu robi to drzewo katalogu obok
        // formularza, na telefonie drzewo i formularz nie zmieszczą się obok
        // siebie, a instalacji objętych audytem są jednostki, nie dziesiątki.
        if (audit.installations.size > 1) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(audit.installations, key = { it.categoryId }) { installation ->
                    ChoicePill(
                        label = installation.pathLabel.substringAfterLast(" › "),
                        selected = installation.categoryId == audit.selectedInstallationId,
                        onClick = { viewModel.selectAuditInstallation(installation.categoryId) },
                    )
                }
            }
            SectionGap()
        }

        val selected = audit.installations
            .firstOrNull { it.categoryId == audit.selectedInstallationId }
        if (selected != null) {
            Text(
                text = selected.pathLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
        }

        val form = audit.form
        if (form == null) {
            Text(
                text = "Ta technologia nie ma w katalogu formularza audytu. " +
                    "Definiuje się go w panelu, w karcie węzła katalogu.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }

        audit.lock?.let { OfferLockedBanner(it) }

        UfhAuditForm(
            form = form,
            enabled = state.canManage && !audit.isFormLocked && !audit.isSavingForm,
            hasHeatPump = audit.hasHeatPump,
            onEdit = viewModel::editAuditForm,
        )

        val missing = audit.missing
        if (missing.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            MissingAnswers(missing)
        }

        if (state.canManage && !audit.isFormLocked) {
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = viewModel::saveAuditForm,
                enabled = !audit.isSavingForm,
                modifier = Modifier.fillMaxWidth(),
                // Braki nie blokują zapisu (audyt bywa uzupełniany na raty),
                // ale kolor ma mówić, że to jeszcze nie jest komplet.
                colors = if (missing.isEmpty()) {
                    ButtonDefaults.buttonColors()
                } else {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary,
                        contentColor = MaterialTheme.colorScheme.onTertiary,
                    )
                },
            ) {
                Text(
                    when {
                        audit.isSavingForm -> "Zapisuję…"
                        missing.isEmpty() && audit.formAuditId != null -> "Zapisz zmiany"
                        missing.isEmpty() -> "Zapisz audyt"
                        else -> "Zapisz — brakuje ${missing.size}"
                    },
                )
            }
        }
    }
}

/**
 * Pasek blokady: klient podpisał konkretny zakres i konkretną kwotę, a oferta
 * liczy się z tego audytu. Zmiana kończy się nową umową albo aneksem do
 * podpisu — dlatego robi się ją w panelu, gdzie widać rozpis i kwotę przed
 * decyzją. Telefon ma o tym powiedzieć, a nie udawać, że pola są edytowalne.
 */
@Composable
private fun OfferLockedBanner(lock: OfferLock) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
    ) {
        Column {
            Text(
                text = "Oferta zamknięta umową ${lock.numer}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = buildString {
                    append("Klient podpisał ")
                    append(formatDate(lock.podpisana) ?: "ten dokument")
                    append(" konkretny zakres i konkretną kwotę, więc audyt jest tylko ")
                    append("do odczytu.")
                    if (lock.zmianaWToku != null) {
                        append(" Zmiana jest już w toku — dokument ")
                        append(lock.zmianaWToku)
                        append(" czeka na akceptację albo na podpis klienta.")
                    } else {
                        append(" Zmianę oferty zgłasza się w panelu: kończy się nową umową ")
                        append("albo aneksem, które klient musi podpisać.")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Spacer(Modifier.height(12.dp))
}

/** Wypis pytań bez odpowiedzi — ten sam tekst co w panelu pod formularzem. */
@Composable
private fun MissingAnswers(missing: List<String>) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(12.dp),
    ) {
        Column {
            Text(
                text = "Brakuje odpowiedzi (${missing.size}) — audyt zapiszesz, " +
                    "ale jest niekompletny:",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            missing.forEach { item ->
                Text(
                    text = "• $item",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── Heizlast ─────────────────────────────────────────────────────────────────

/**
 * Zapotrzebowanie budynku na ciepło. Deal może mieć kilka wpisów (kolejne
 * pomiary, korekta po projekcie), więc to lista, a nie jedno pole — pokazujemy
 * je od najnowszego, tak jak API je zwraca.
 */
@Composable
private fun HeatloadCard(
    state: DealDetailViewModel.UiState,
    viewModel: DealDetailViewModel,
) {
    val audit = state.audit
    val draft = audit.draft

    SectionCard {
        SectionTitle("Heizlast", accent = audit.heatloads.size.takeIf { it > 0 }?.toString())
        SectionGap()

        if (audit.heatloads.isEmpty()) {
            Text(
                text = "Brak audytów.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            audit.heatloads.forEachIndexed { index, entry ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
                HeatloadRow(entry)
            }
        }

        if (!state.canManage) return@SectionCard

        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(16.dp))

        Text(
            text = "Nowy audyt / Heizlast",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))

        PillChoiceRow(
            options = HeatloadMode.entries,
            selected = draft.mode,
            optionLabel = { it.label },
            onSelect = { mode ->
                // Powtórne dotknięcie wybranego trybu go zdejmuje — bez tego
                // nie dałoby się wrócić do samej notatki bez Heizlast.
                viewModel.editHeatloadDraft {
                    it.copy(mode = if (it.mode == mode) null else mode)
                }
            },
            enabled = !audit.isSavingHeatload,
        )
        Spacer(Modifier.height(12.dp))

        when (draft.mode) {
            HeatloadMode.SZYBKI -> {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FormNumberField(
                        label = "Powierzchnia m²",
                        text = draft.areaM2,
                        onTextChange = { v -> viewModel.editHeatloadDraft { it.copy(areaM2 = v) } },
                        modifier = Modifier.weight(1f),
                        decimal = true,
                    )
                    FormNumberField(
                        label = "Wys. m (opc.)",
                        text = draft.heightM,
                        onTextChange = { v -> viewModel.editHeatloadDraft { it.copy(heightM = v) } },
                        modifier = Modifier.weight(1f),
                        decimal = true,
                    )
                }
                Spacer(Modifier.height(8.dp))
                AuditChoiceField(
                    label = "Standard budynku",
                    options = BuildingStandard.entries,
                    selected = draft.standard,
                    optionLabel = { it.label },
                    onSelect = { v -> viewModel.editHeatloadDraft { it.copy(standard = v) } },
                    enabled = !audit.isSavingHeatload,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    // Wynik zapisuje serwer ze swojej domeny — to tylko podgląd,
                    // żeby audytor wiedział, czego się spodziewać.
                    text = "Szacunek: ${draft.preview?.let { "$it kW" } ?: "—"} " +
                        "(wskaźnikowy, nie pełny DIN)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HeatloadMode.DIN -> FormNumberField(
                label = "Heizlast kW (z obliczenia DIN)",
                text = draft.kw,
                onTextChange = { v -> viewModel.editHeatloadDraft { it.copy(kw = v) } },
                decimal = true,
            )

            null -> Unit
        }

        Spacer(Modifier.height(8.dp))
        AuditTextField(
            label = "Notatka z audytu",
            value = draft.note,
            singleLine = false,
            minLines = 2,
            enabled = !audit.isSavingHeatload,
            onValueChange = { v -> viewModel.editHeatloadDraft { it.copy(note = v) } },
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = viewModel::saveHeatload,
            enabled = !audit.isSavingHeatload && draft.isSubmittable,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (audit.isSavingHeatload) "Zapisuję…" else "Zapisz audyt")
        }
    }
}

@Composable
private fun HeatloadRow(entry: Audit) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Audyt",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = when {
                    // Wpis z kolejki: kW szybkiego szacunku liczy serwer, więc
                    // do wysyłki nie ma czego pokazać — mówimy o tym wprost,
                    // zamiast udawać „bez Heizlast".
                    entry.pendingSince != null -> "czeka na wysyłkę"
                    entry.heatloadKw != null -> "Heizlast ${entry.heatloadKw} kW"
                    else -> "bez Heizlast"
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (entry.heatloadKw != null && entry.pendingSince == null) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = listOfNotNull(
                "tryb: ${entry.heatloadMode?.wire ?: "—"}",
                formatDate(entry.createdAt),
                entry.note,
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
