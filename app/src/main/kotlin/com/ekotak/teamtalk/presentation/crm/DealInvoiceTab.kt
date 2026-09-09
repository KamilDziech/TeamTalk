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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ekotak.teamtalk.domain.model.DealDifficulty
import com.ekotak.teamtalk.domain.model.DealMontaz
import com.ekotak.teamtalk.domain.model.InvoiceMatch
import com.ekotak.teamtalk.domain.model.InvoiceRachunek
import com.ekotak.teamtalk.domain.model.KsefInvoice
import com.ekotak.teamtalk.domain.model.cyfrySlowo
import com.ekotak.teamtalk.domain.model.nipDoFaktur
import com.ekotak.teamtalk.presentation.theme.OkGreen
import com.ekotak.teamtalk.presentation.theme.Orange600
import com.ekotak.teamtalk.presentation.theme.SyncBlue

/**
 * Zakładka „Faktura" karty deala.
 *
 * PANEL MA TU DZIŚ ATRAPĘ: `DealDrawer` rysuje na tej zakładce drzewo
 * instalacji z etapu „montaz" i listę montaży deala — to samo, co na karcie
 * „Montaż" — a prawdziwe faktury żyją w osobnym module „Faktury KSeF", na
 * poziomie organizacji i bez związku z kartą. Telefon powtarza ten kształt
 * (sekcje „Montaże" i „Zakres"), ale nie kończy na nim: handlowiec wchodzi tu
 * u klienta po odpowiedź na trzy pytania, których atrapa nie daje.
 *
 * Stąd kolejność sekcji — od pytania najczęstszego do kontekstu:
 *  1. **Do zafakturowania** — kwoty z AKTUALNEJ umowy (zaliczka, płatność
 *     końcowa, termin). Liczy je ten sam kod, co § 7 dokumentu, więc przy
 *     kliencie nie może paść inna kwota niż na papierze, który on trzyma.
 *  2. **Dane do faktury** — na kogo idzie faktura. Jedyne miejsce tej zakładki,
 *     które cokolwiek zapisuje; bez zasięgu zapis ląduje we wspólnej kolejce
 *     karty deala, bo dane do faktury poprawia się w kuchni klienta, a nie
 *     w biurze.
 *  3. **Faktury** — czy dokument już wyszedł. Lista chodzi pod `ksef.view`
 *     (księgowość); bez tego prawa sekcja mówi wprost, czego brakuje, zamiast
 *     udawać, że faktur nie ma.
 *  4. **Montaże** i 5. **Zakres** — część 1:1 z panelem. Drzewo jest tu
 *     kontekstem („co klient kupił"), więc stoi zwinięte na dole: na 360 dp
 *     rozwinięte zepchnęłoby wszystko powyżej pod dolną krawędź.
 *
 * Czego świadomie NIE MA: wystawiania faktury i oznaczania zapłaty. Faktury
 * wystawia się w KSeF, a nie w CRM-ie; przycisk „zapłacona" w telefonie
 * musiałby zapisywać stan, którego board360 dziś nie ma, i po tygodniu
 * rozjechałby się z księgowością.
 */
@Composable
fun DealInvoiceTab(
    state: DealDetailViewModel.UiState,
    viewModel: DealDetailViewModel,
) {
    val invoices = state.invoices

    if (!invoices.loaded) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }
        return
    }

    invoices.error?.let { error ->
        SectionCard {
            SectionTitle("Faktura")
            SectionGap()
            Text(
                text = error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
        SectionGap()
    }

    if (invoices.data?.fromCache == true) {
        StatusBanner(
            text = "Kopia z telefonu — nie było zasięgu. Kwoty z umowy są aktualne, " +
                "lista faktur może być starsza.",
            color = SyncBlue,
        )
        SectionGap()
    }

    RachunekCard(invoices.rachunek)
    SectionGap()

    BillingCard(state, viewModel)
    SectionGap()

    InvoicesCard(state)
    SectionGap()

    MontazeCard(invoices.data?.montaze.orEmpty())
    SectionGap()

    ScopeCard(state, viewModel)
}

// ── Do zafakturowania ────────────────────────────────────────────────────────

/**
 * Rachunek z umowy. Rozbicie jest to samo, co w § 7: netto, VAT wg stawki
 * z dokumentu, brutto, a niżej podział na zaliczkę i płatność końcową — bo
 * właśnie na te dwie kwoty wystawia się dwie faktury.
 */
@Composable
private fun RachunekCard(rachunek: InvoiceRachunek?) {
    SectionCard {
        SectionTitle(
            text = "Do zafakturowania",
            accent = rachunek?.let { if (it.podpisana) "podpisana" else "szkic" },
        )
        SectionGap()

        if (rachunek == null) {
            Text(
                text = "Deal nie ma jeszcze umowy — kwoty do faktury biorą się z niej " +
                    "(zakładka „Umowa”), a nie z oferty.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }

        val k = rachunek.kwoty
        InfoRow("Umowa", rachunek.numerUmowy)
        InfoRow("Netto", formatZl(k.netto))
        InfoRow("VAT ${rachunek.vatStawka}%", formatZl(k.vat))
        InfoRow("Brutto", formatZl(k.brutto))

        HorizontalDivider(
            modifier = Modifier.padding(vertical = 8.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )

        InfoRow("Zaliczka ${rachunek.zaliczkaProc}%", formatZl(k.zaliczka))
        InfoRow("Płatność końcowa", formatZl(k.reszta))
        Text(
            text = "Zaliczka płatna przy zawarciu umowy; fakturę końcową klient " +
                "opłaca w ${rachunek.terminKoncowyDni} dni od odbioru prac.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (!rachunek.podpisana) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Umowa nie jest jeszcze podpisana — to propozycja, nie podstawa faktury.",
                style = MaterialTheme.typography.bodySmall,
                color = Orange600,
            )
        }
    }
}

// ── Dane do faktury ──────────────────────────────────────────────────────────

/**
 * Na kogo idzie faktura. Domyślnie na dane klienta („adres jak instalacji");
 * odznaczenie odsłania odrębnego odbiorcę, którym przy B2B jest firma z NIP-em.
 *
 * To ten sam komplet pól, co w formularzu karty deala — zakładka nie zakłada
 * własnych. Zapis idzie wspólną drogą karty (`PATCH` z różnicy draftu), więc
 * bez zasięgu ląduje w kolejce i wychodzi, gdy telefon wróci w zasięg.
 */
@Composable
private fun BillingCard(
    state: DealDetailViewModel.UiState,
    viewModel: DealDetailViewModel,
) {
    val deal = state.detail?.deal ?: return
    val client = state.detail?.client
    val form = state.invoices.form

    SectionCard {
        SectionTitle(
            text = "Dane do faktury",
            // „Zmień" tylko poza edycją i tylko dla kogoś, kto kartę zapisuje
            // (`deal.manage`) — reszcie API i tak odmówi.
            action = if (form == null && state.canManage) "Zmień" else null,
            onAction = if (form == null && state.canManage) viewModel::openBillingForm else null,
        )
        SectionGap()

        if (form == null) {
            if (deal.billingSameAsInstall) {
                Text(
                    text = "Faktura na dane klienta (adres instalacji).",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                InfoRow("Nabywca", client?.displayName)
                InfoRow("Adres", client?.address)
            } else {
                InfoRow("Odbiorca", deal.billingName)
                InfoRow("Firma", deal.billingCompany)
                InfoRow("NIP", deal.billingNip)
                InfoRow("Adres", deal.billingAddress)
            }
            // NIP przesądza o tym, czy faktury tego deala rozpoznamy PEWNIE.
            // Mówimy więc osobno o jego braku i osobno o wpisie, który NIP-em
            // nie jest: przy tym drugim człowiek widzi na karcie cyfry i bez
            // ostrzeżenia nie ma jak zgadnąć, że dopasowanie je pomija.
            if (!deal.billingSameAsInstall) {
                val cyfry = deal.billingNip.orEmpty().filter { it.isDigit() }.length
                val ostrzezenie = when {
                    deal.billingNip.isNullOrBlank() ->
                        "Brak NIP-u — faktury tego deala rozpoznajemy wtedy tylko po nazwie nabywcy."

                    nipDoFaktur(deal.billingNip) == null ->
                        "Ten NIP ma $cyfry ${cyfrySlowo(cyfry)}, a poprawny ma dziesięć — " +
                            "faktury szukamy wtedy po nazwie nabywcy, tak jakby NIP-u nie było."

                    else -> null
                }
                ostrzezenie?.let { tekst ->
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = tekst,
                        style = MaterialTheme.typography.bodySmall,
                        color = Orange600,
                    )
                }
            }
            return@SectionCard
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Adres jak instalacji",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Switch(
                checked = form.jakInstalacji,
                onCheckedChange = { value ->
                    viewModel.editBillingForm { it.copy(jakInstalacji = value) }
                },
                enabled = !state.isSaving,
            )
        }

        if (!form.jakInstalacji) {
            Spacer(Modifier.height(8.dp))
            BillingField("Odbiorca", form.odbiorca, state.isSaving) { value ->
                viewModel.editBillingForm { it.copy(odbiorca = value) }
            }
            Spacer(Modifier.height(8.dp))
            BillingField("Firma", form.firma, state.isSaving) { value ->
                viewModel.editBillingForm { it.copy(firma = value) }
            }
            Spacer(Modifier.height(8.dp))
            BillingField("NIP", form.nip, state.isSaving) { value ->
                viewModel.editBillingForm { it.copy(nip = value) }
            }
            Spacer(Modifier.height(8.dp))
            BillingField("Adres do faktury", form.adres, state.isSaving) { value ->
                viewModel.editBillingForm { it.copy(adres = value) }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = viewModel::saveBilling, enabled = !state.isSaving) {
                Text("Zapisz")
            }
            TextButton(onClick = viewModel::closeBillingForm, enabled = !state.isSaving) {
                Text("Anuluj")
            }
        }
    }
}

@Composable
private fun BillingField(
    label: String,
    value: String,
    saving: Boolean,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        enabled = !saving,
        modifier = Modifier.fillMaxWidth(),
    )
}

// ── Faktury z KSeF ───────────────────────────────────────────────────────────

/**
 * Faktury sprzedażowe wystawione klientowi tego deala.
 *
 * Dopasowanie robi board360, a karta je POKAZUJE: faktura trafiona po NIP-ie
 * jest pewna, trafiona po nazwie — prawdopodobna. Ta różnica musi być widoczna,
 * bo po nazwie da się trafić w imiennika, a rozmowa z klientem o cudzej
 * fakturze kosztuje więcej niż jedno słowo na ekranie.
 */
@Composable
private fun InvoicesCard(state: DealDetailViewModel.UiState) {
    val dane = state.invoices.data

    SectionCard {
        SectionTitle(
            text = "Faktury",
            accent = dane?.faktury?.size?.takeIf { it > 0 }?.toString(),
        )
        SectionGap()

        // Brak prawa i brak faktur to dwie różne odpowiedzi — mylenie ich każe
        // handlowcowi szukać dokumentu, którego po prostu nie widzi.
        if (!state.canViewInvoices || dane?.brakDostepu == true) {
            Text(
                text = "Wystawione faktury widzi księgowość (uprawnienie „Faktury KSeF”). " +
                    "Kwoty do zafakturowania masz wyżej — one nie wymagają tego prawa.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }

        // Odmowa serwera nie kasuje tego, co już mamy: gdy w telefonie leży
        // starsze pobranie, pokazujemy błąd NAD listą, a nie zamiast niej.
        dane?.bladFaktur?.let { blad ->
            Text(
                text = blad,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            if (dane.faktury.isEmpty()) return@SectionCard
            Spacer(Modifier.height(10.dp))
        }

        if (dane == null || dane.faktury.isEmpty()) {
            Text(
                text = buildString {
                    append("Brak faktur wystawionych na ")
                    append(dane?.nabywca ?: "tego klienta")
                    if (dane?.nabywcaNip != null) append(" (NIP ${dane.nabywcaNip})")
                    append(". Faktury wystawia się w KSeF — tu pojawią się po synchronizacji.")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }

        dane.faktury.forEachIndexed { index, faktura ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 10.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
            InvoiceRow(faktura)
        }

        if (dane.niepewne > 0) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "${dane.niepewne} z tych faktur trafiło tu po samej NAZWIE nabywcy, " +
                    "nie po NIP-ie — po nazwie da się trafić w imiennika. Sprawdź numer, " +
                    "zanim powołasz się na taką fakturę przy kliencie.",
                style = MaterialTheme.typography.bodySmall,
                color = Orange600,
            )
        }
    }
}

@Composable
private fun InvoiceRow(faktura: KsefInvoice) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = faktura.numer ?: faktura.numerKsef,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = listOfNotNull(
                        formatDate(faktura.dataWystawienia),
                        faktura.nabywca,
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = kwotaText(faktura.brutto, faktura.waluta),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }

        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            MatchBadge(faktura.dopasowanie)
            Spacer(Modifier.width(8.dp))
            Text(
                text = listOfNotNull(
                    faktura.netto?.let { "netto ${kwotaText(it, faktura.waluta)}" },
                    faktura.vat?.let { "VAT ${kwotaText(it, faktura.waluta)}" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(4.dp))
        Text(
            text = "KSeF: ${faktura.numerKsef}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MatchBadge(match: InvoiceMatch) {
    val (label, color) = when (match) {
        InvoiceMatch.NIP -> "po NIP" to OkGreen
        InvoiceMatch.NAZWA -> "po nazwie" to Orange600
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

// ── Montaże (część 1:1 z panelem) ────────────────────────────────────────────

/**
 * Montaże deala — dokładnie ta lista, którą panel rysuje na tej zakładce.
 * Podgląd: planowanie terminów ma własny moduł, a karta deala tylko pokazuje,
 * co i kiedy się dzieje.
 */
@Composable
private fun MontazeCard(montaze: List<DealMontaz>) {
    SectionCard {
        SectionTitle("Montaże", accent = montaze.size.takeIf { it > 0 }?.toString())
        SectionGap()

        if (montaze.isEmpty()) {
            Text(
                text = "Brak montaży dla tego deala. Termin planuje się w module „Montaże”.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }

        montaze.forEachIndexed { index, montaz ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = formatDateTime(montaz.termin) ?: "Termin nieustalony",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = listOfNotNull(
                        montaz.status.label,
                        DealDifficulty.fromWire(montaz.trudnosc)?.label,
                        montaz.notatka?.takeIf { it.isNotBlank() }?.let { "„$it”" },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── Zakres (część 1:1 z panelem) ─────────────────────────────────────────────

/**
 * Drzewo etapu „montaz" — to samo, które panel pokazuje na tej zakładce.
 * Zwinięte, bo na tej karcie jest kontekstem, a nie treścią; i przycięte do
 * samego wyboru klienta, żeby nie rysować marek, których nie wziął.
 */
@Composable
private fun ScopeCard(
    state: DealDetailViewModel.UiState,
    viewModel: DealDetailViewModel,
) {
    val invoices = state.invoices
    val count = invoices.scope.size

    CollapsibleSectionCard(
        title = "Zakres montażu",
        summary = when {
            invoices.scopeTree.isEmpty() && count == 0 ->
                "Deal nie ma jeszcze zakresu na etapie „Montaż”"
            count == 1 -> "1 instalacja"
            count in 2..4 -> "$count instalacje"
            else -> "$count instalacji"
        },
    ) {
        if (invoices.scopeTree.isEmpty()) {
            Text(
                text = "Zakres ustala się na karcie „Oferta”; na tę zakładkę schodzi " +
                    "migawką etapu „Montaż” i jest tu wyłącznie podglądem.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@CollapsibleSectionCard
        }

        InstallationTree(
            nodes = invoices.scopeTree,
            selected = invoices.scope,
            expanded = invoices.expanded,
            editable = false,
            onToggleSelection = {},
            onToggleBranch = viewModel::toggleInvoiceScopeBranch,
        )
    }
}

// ── Drobiazgi ────────────────────────────────────────────────────────────────

/**
 * Kwota z faktury. API oddaje ją TEKSTEM przepisanym z XML-a FA(3) („42000.00"),
 * więc formatujemy tylko to, co da się bezpiecznie przeczytać jako liczbę —
 * reszta idzie na ekran tak, jak stoi w dokumencie. Podstawianie zera za
 * nieznany zapis pokazywałoby klientowi kwotę, której nikt nie wystawił.
 */
private fun kwotaText(raw: String?, waluta: String): String {
    val value = raw?.trim().orEmpty()
    if (value.isEmpty()) return "—"
    val number = value.replace(',', '.').toDoubleOrNull() ?: return "$value $waluta"
    return if (waluta == "PLN") formatZl(number) else "$value $waluta"
}

/** Wąski pasek stanu nad treścią zakładki (kopia z telefonu, ostrzeżenia). */
@Composable
private fun StatusBanner(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}
