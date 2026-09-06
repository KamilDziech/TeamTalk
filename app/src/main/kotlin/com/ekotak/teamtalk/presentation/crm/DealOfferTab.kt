package com.ekotak.teamtalk.presentation.crm

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ekotak.teamtalk.domain.model.OfferLock
import com.ekotak.teamtalk.domain.model.UfhState
import com.ekotak.teamtalk.domain.ufh.OfferPricing
import com.ekotak.teamtalk.domain.ufh.OfferReason
import com.ekotak.teamtalk.domain.ufh.OfferScope
import com.ekotak.teamtalk.domain.ufh.ScopeRow
import com.ekotak.teamtalk.domain.ufh.TechSection
import com.ekotak.teamtalk.domain.ufh.UfhQuoteInput
import com.ekotak.teamtalk.domain.ufh.auditUsable
import com.ekotak.teamtalk.domain.ufh.fmtAreaM2
import com.ekotak.teamtalk.domain.ufh.fmtPipeMb
import com.ekotak.teamtalk.domain.ufh.offerReasons
import com.ekotak.teamtalk.domain.ufh.offerScope
import com.ekotak.teamtalk.domain.ufh.scopeQty
import com.ekotak.teamtalk.domain.ufh.technicalSections
import com.ekotak.teamtalk.domain.ufh.ufhQuoteInput
import com.ekotak.teamtalk.domain.ufh.zlText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Zakładka „Oferta" karty deala — mobilny odpowiednik `DealOfferPanel` panelu.
 *
 * Trzy widoki tej samej instalacji, w tej samej kolejności co w panelu:
 *  • **Oferta dla klienta** — dlaczego zaproponowaliśmy to rozwiązanie (każda
 *    decyzja audytu przełożona na korzyść) plus zakres w liczbach,
 *  • **Podsumowanie** — zakres pozycja po pozycji z kwotami netto,
 *  • **Widok techniczny** — specyfikacja w układzie konfiguratora z ekotak.pl,
 *    wypełniona danymi audytu tego deala.
 *
 * Nic tu nie jest wpisywane ręcznie: wszystko idzie z audytu OP i z cennika
 * węzła Technologii, a brak danych mówimy WPROST — pusta rubryka w ofercie jest
 * gorsza niż komunikat.
 *
 * Czego świadomie NIE ma na telefonie: „Modyfikuj ofertę". Zmiana po podpisie
 * kończy się nową umową albo aneksem do podpisu, a robi się ją w audycie —
 * telefon o tym mówi (pasek blokady) zamiast otwierać ten ruch na parkingu.
 */
@Composable
fun DealOfferTab(
    state: DealDetailViewModel.UiState,
    viewModel: DealDetailViewModel,
) {
    val offer = state.offer

    // Pasek blokady stoi nad KAŻDYM stanem zakładki (także nad błędem i pustką):
    // „klient to już podpisał" jest ważniejsze niż to, czy udało się policzyć zakres.
    offer.lock?.let { OfferLockedInfo(it) }

    if (!offer.loaded) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }
        return
    }

    if (offer.error != null) {
        SectionCard {
            SectionTitle("Oferta")
            SectionGap()
            Text(
                text = offer.error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { viewModel.loadOffer(force = true) }) {
                Text("Spróbuj ponownie")
            }
        }
        return
    }

    if (offer.installations.isEmpty()) {
        InfoCard(
            title = "Oferta",
            text = "Brak instalacji do wyceny — zaznacz instalacje na pasku etapu " +
                "(Oferta albo Audyt) w panelu.",
        )
        return
    }

    if (offer.installations.size > 1) {
        InstallationPicker(offer, viewModel)
        SectionGap()
    }

    ModePicker(offer.mode) { viewModel.setOfferMode(it) }
    SectionGap()

    val inst = offer.active ?: return
    val form = inst.form
    if (form == null) {
        InfoCard(
            title = inst.name,
            text = "Instalacja „${inst.name}” nie ma jeszcze wypełnionego audytu — nie ma z czego " +
                "złożyć oferty. Wypełnij audyt w zakładce „Audyt”.",
        )
        return
    }

    val calc by rememberOfferCalc(form, offer.activePricing)
    val ready = calc
    if (ready == null) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }
        return
    }

    if (!auditUsable(ready.input)) {
        InfoCard(
            title = inst.name,
            text = "Audyt „${inst.name}” nie ma jeszcze metrażu ani pomiaru z rzutu — bez tego " +
                "nie ma czego opisywać.",
        )
        return
    }

    when (offer.mode) {
        DealDetailViewModel.OfferMode.CLIENT -> ClientOffer(inst, ready, offer.activePricingAsked)
        DealDetailViewModel.OfferMode.SUMMARY -> ScopeTable(
            scope = ready.scope,
            internal = true,
            pricingAsked = offer.activePricingAsked,
        )
        DealDetailViewModel.OfferMode.TECH -> TechnicalOffer(inst, ready)
    }
}

/** Rachunek jednej instalacji — liczony raz, poza wątkiem głównym. */
private class OfferCalc(
    val input: UfhQuoteInput,
    val reasons: List<OfferReason>,
    val sections: List<TechSection>,
    /** `null` = ten węzeł nie ma formuły ceny albo cennika jeszcze nie ma. */
    val scope: OfferScope?,
)

/**
 * Podział pomieszczeń na pętle to prawdziwa geometria (ta sama, co w panelu),
 * więc rachunek idzie w tle i przelicza się dopiero, gdy zmieni się audyt albo
 * cennik — inaczej każde przewinięcie listy liczyłoby strefy od nowa.
 */
@Composable
private fun rememberOfferCalc(form: UfhState, pricing: OfferPricing?): State<OfferCalc?> =
    produceState<OfferCalc?>(initialValue = null, form, pricing) {
        value = withContext(Dispatchers.Default) {
            val input = ufhQuoteInput(form)
            OfferCalc(
                input = input,
                reasons = offerReasons(form, input),
                sections = technicalSections(form, input),
                scope = pricing?.let { offerScope(form, input, it) },
            )
        }
    }

// ── Wybór instalacji i widoku ────────────────────────────────────────────────

@Composable
private fun InstallationPicker(
    offer: DealDetailViewModel.OfferState,
    viewModel: DealDetailViewModel,
) {
    SectionCard {
        SectionTitle("Instalacja", accent = "${offer.installations.size}")
        SectionGap()
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(offer.installations) { index, item ->
                ChoicePill(
                    // Kropka przy nazwie = instalacja bez wypełnionego audytu.
                    label = item.name + if (item.form == null) " ·" else "",
                    selected = index == offer.selectedIndex,
                    onClick = { viewModel.selectOfferInstallation(index) },
                )
            }
        }
    }
}

@Composable
private fun ModePicker(
    mode: DealDetailViewModel.OfferMode,
    onSelect: (DealDetailViewModel.OfferMode) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        itemsIndexed(DealDetailViewModel.OfferMode.entries) { _, item ->
            ChoicePill(
                label = item.label,
                selected = item == mode,
                onClick = { onSelect(item) },
            )
        }
    }
}

// ── Oferta dla klienta ───────────────────────────────────────────────────────

@Composable
private fun ClientOffer(
    inst: DealDetailViewModel.OfferInstallation,
    calc: OfferCalc,
    pricingAsked: Boolean,
) {
    SectionCard {
        Text(
            text = inst.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (inst.path.isNotEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = inst.path.joinToString(" › "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    SectionGap()

    if (calc.reasons.isNotEmpty()) {
        SectionCard {
            SectionTitle("Dlaczego zaproponowaliśmy to rozwiązanie?")
            Text(
                text = "każda pozycja oferty wynika z tego, co zastaliśmy na audycie",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SectionGap()
            calc.reasons.forEachIndexed { index, reason ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
                ReasonBlock(reason)
            }
        }
        SectionGap()
    }

    SectionCard {
        SectionTitle("Zakres w liczbach")
        Text(
            text = "wprost z audytu — nic wpisywanego z ręki",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SectionGap()
        val input = calc.input
        InfoRow("Powierzchnia ogrzewana", fmtAreaM2(input.areas.heated))
        InfoRow("Rura w instalacji", fmtPipeMb(input.pipeM))
        InfoRow("Obwody", "${input.loops}")
        InfoRow("Rozdzielacze", "${input.manifoldsToBuy}")
        InfoRow("Szafki", "${input.cabinetsToBuy}")
        input.warnings.forEach { WarningNote(it) }
    }
    SectionGap()

    ScopeTable(scope = calc.scope, internal = false, pricingAsked = pricingAsked)
}

@Composable
private fun ReasonBlock(reason: OfferReason) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = reason.topic,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = reason.choice,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = reason.why,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Widok techniczny ─────────────────────────────────────────────────────────

@Composable
private fun TechnicalOffer(inst: DealDetailViewModel.OfferInstallation, calc: OfferCalc) {
    SectionCard {
        Text(
            text = "Specyfikacja instalacji „${inst.name}” — parametry w tym samym układzie, " +
                "co konfigurator na ekotak.pl, wypełnione danymi z audytu.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        calc.input.warnings.forEach { WarningNote(it) }
    }
    SectionGap()

    calc.sections.forEach { section ->
        SectionCard {
            SectionTitle(section.title)
            section.subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SectionGap()
            section.rows.forEach { row ->
                InfoRow(row.label, row.value)
                row.note?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
        }
        SectionGap()
    }
}

// ── Podsumowanie oferty (kwoty) ──────────────────────────────────────────────

/**
 * Tabela „Podsumowanie oferty" w układzie na 360 dp: pozycja i kwota w jednym
 * wierszu, a specyfikacja i „ilość × cena jednostkowa" pod spodem. Kolumn
 * z panelu nie da się zmieścić obok siebie bez zjadania kwoty — a to ona jest
 * tu treścią.
 *
 * @param internal widok wewnętrzny — z rozbiciem materiał / robocizna i listą
 *   braków cennika. Klient ma widzieć sam zakres i kwoty.
 */
@Composable
private fun ScopeTable(scope: OfferScope?, internal: Boolean, pricingAsked: Boolean) {
    if (!pricingAsked) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }
        return
    }

    if (scope == null) {
        InfoCard(
            title = "Podsumowanie oferty",
            text = "Dla tej instalacji nie ma cennika jednostkowego — formuła ceny jest rozpisana " +
                "na razie wyłącznie dla ogrzewania podłogowego. Zakres bez kwot pokazuje widok " +
                "„Oferta dla klienta”.",
        )
        return
    }

    if (scope.rows.isEmpty()) {
        InfoCard(
            title = "Podsumowanie oferty",
            text = "Audyt nie daje jeszcze żadnej pozycji do wyceny — uzupełnij metraż " +
                "i parametry instalacji w zakładce „Audyt”.",
        )
        return
    }

    SectionCard {
        SectionTitle("Podsumowanie oferty")
        Text(
            text = "ilości z audytu × ceny jednostkowe z formuły ceny węzła",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SectionGap()

        var lastGroup: String? = null
        scope.rows.forEach { row ->
            if (row.group != lastGroup) {
                lastGroup = row.group
                Spacer(Modifier.height(8.dp))
                Text(
                    text = row.group,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
            }
            ScopeRowBlock(row)
        }

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 10.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Razem netto",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${zlText(scope.net)} zł",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        if (internal) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = "w tym materiał ${zlText(scope.material)} zł · " +
                    "robocizna ${zlText(scope.labor)} zł",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = "Kwoty netto, bez podatku VAT. Ceny jednostkowe pochodzą z karty „🧮 Formuła " +
                "ceny” węzła Technologii — zmiana cennika albo narzutu przelicza tę tabelę od razu.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    if (internal && scope.gaps.isNotEmpty()) {
        SectionGap()
        SectionCard {
            SectionTitle("Braki cennika", accent = "${scope.gaps.size}")
            SectionGap()
            Text(
                text = "Pozycje bez pełnego pokrycia w cennikach — kwota jest przez to zaniżona:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            scope.gaps.forEach {
                Text(
                    text = "• $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ScopeRowBlock(row: ScopeRow) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Text(
                text = row.name,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = if (row.zeroed != null) "0,00 zł" else "${zlText(row.net)} zł",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.End,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = row.spec,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val qty = scopeQty(row.qty)
        val unitPrice = row.unitNet
        val math = when {
            row.zeroed != null -> row.zeroed
            qty.isEmpty() -> null
            unitPrice != null -> "$qty ${row.unit} × ${zlText(unitPrice)} zł"
            else -> "$qty ${row.unit}"
        }
        math?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ── Drobne części wspólne ────────────────────────────────────────────────────

/**
 * Pasek „oferta zamknięta umową". Zmianę zakresu i kwot robi się w panelu:
 * kończy się nową umową albo aneksem do podpisu, a przed taką decyzją trzeba
 * widzieć rozpis i kwotę.
 */
@Composable
private fun OfferLockedInfo(lock: OfferLock) {
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
                    append("Umowa jest podpisana ")
                    append(formatDate(lock.podpisana) ?: "")
                    append("— zakresu ani kwot nie zmienia się po cichu.")
                    if (lock.zmianaWToku != null) {
                        append(" Zmiana jest już w toku: dokument ")
                        append(lock.zmianaWToku)
                        append(" czeka na akceptację albo na podpis klienta.")
                    } else {
                        append(" Zmianę wprowadzisz w audycie instalacji w panelu, ")
                        append("a klient musi podpisać nową umowę albo aneks.")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun InfoCard(title: String, text: String) {
    SectionCard {
        SectionTitle(title)
        SectionGap()
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Ostrzeżenie rachunku — to samo zdanie, które panel pokazuje nad zakresem. */
@Composable
private fun WarningNote(text: String) {
    Spacer(Modifier.height(6.dp))
    Text(
        text = "⚠ $text",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}
