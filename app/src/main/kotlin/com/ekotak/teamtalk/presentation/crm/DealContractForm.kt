package com.ekotak.teamtalk.presentation.crm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.ekotak.teamtalk.domain.model.ContractItem
import com.ekotak.teamtalk.domain.model.ContractKind
import com.ekotak.teamtalk.domain.model.TERMIN_ODESLANIA_H
import com.ekotak.teamtalk.domain.model.policzPodglad
import com.ekotak.teamtalk.domain.model.zl
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Formularz umowy — wystawienie nowej, wystawienie po terminie odesłania
 * i zmiana podpisanej. Jeden kształt na wszystkie trzy przypadki, tak jak
 * w panelu: zmiana poprawia treść, którą klient podpisał, więc pracuje na tych
 * samych polach.
 *
 * Układ jest inny niż w panelu — tabela z siedmioma kolumnami nie mieści się
 * na 360 dp — ale treść jest ta sama: § 1, § 2, warunki, etapy § 7 i Załącznik
 * nr 1 policzony z audytu. Pozycja rozbija się na kartę: opis w jednej linii,
 * liczby w drugiej.
 */
@Composable
fun ContractFormCard(
    form: DealDetailViewModel.ContractForm,
    viewModel: DealDetailViewModel,
) {
    val filling = form.filling
    val podglad = policzPodglad(
        filling.pozycje,
        filling.etapy,
        filling.vatStawka,
        filling.zaliczkaProc,
    )

    SectionCard {
        SectionTitle(
            text = form.zmianaDla?.let { "Zmiana umowy ${it.numer}" } ?: "Nowa umowa",
            action = "Anuluj",
            onAction = viewModel::closeContractForm,
        )
        SectionGap()

        form.zmianaDla?.let { zmiana ->
            Text(
                text = if (form.rodzajZmiany == ContractKind.ANEKS) {
                    "Poprawiasz treść, którą klient już podpisał. Powstanie aneks z wykazem " +
                        "zmian — umowa pierwotna obowiązuje dalej w nietkniętym zakresie."
                } else {
                    "Poprawiasz treść, którą klient już podpisał. Powstanie nowa wersja " +
                        "dokumentu do ponownego podpisu — poprzednia obowiązuje do chwili " +
                        "jego złożenia."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Co dostaje klient do podpisu",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(6.dp))
            // Aneks zmienia umowę punktowo, nowa wersja ją zastępuje — w obu
            // przypadkach dokument mówi wprost, że zmieniane ustalenia tracą moc.
            PillChoiceRow(
                options = listOf(ContractKind.ANEKS, ContractKind.UMOWA),
                selected = form.rodzajZmiany,
                optionLabel = { kind ->
                    if (kind == ContractKind.ANEKS) {
                        "Aneks do umowy ${zmiana.numer}"
                    } else {
                        "Nowa wersja całej umowy"
                    }
                },
                onSelect = viewModel::setContractChangeKind,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = form.powodZmiany,
                onValueChange = viewModel::setContractChangeReason,
                label = {
                    Text(
                        "Powód zmiany (drukuje się na " +
                            (if (form.rodzajZmiany == ContractKind.ANEKS) "aneksie)" else "umowie)"),
                    )
                },
                placeholder = { Text("Korekta zakresu po ustaleniach z klientem") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            ContractDivider()
        }

        form.poTerminie?.let {
            ContractAlarm(
                "SPRAWDŹ CENY w Załączniku nr 1. Kwoty poniżej pochodzą z umowy ${it.numer} " +
                    "wystawionej ${formatDateTime(it.wystawiona).orEmpty()} — po to ta umowa " +
                    "wygasła, żeby nie wysłać klientowi po raz drugi cennika sprzed terminu. " +
                    "Zakres, etapy i ilości zwykle zostają bez zmian. Powstanie osobna umowa " +
                    "z nowym numerem i nowym terminem $TERMIN_ODESLANIA_H h.",
            )
            Spacer(Modifier.height(10.dp))
        }

        OutlinedTextField(
            value = filling.przedmiot,
            onValueChange = viewModel::setContractSubject,
            label = { Text("Przedmiot umowy (§ 1)") },
            placeholder = { Text("Wykonanie instalacji ogrzewania podłogowego — 75,60 m²…") },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))

        ContractDateField(
            label = "Termin wykonania (§ 2)",
            value = filling.termin,
            onChange = viewModel::setContractDeadline,
        )
        Spacer(Modifier.height(10.dp))

        OutlinedTextField(
            value = filling.podstawaZalacznika,
            onValueChange = viewModel::setContractBasis,
            label = { Text("Podstawa Załącznika nr 1") },
            placeholder = { Text("Audyt ogrzewania podłogowego z dn. 17.08.2026") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ContractNumberField(
                label = "VAT %",
                value = filling.vatStawka.toDouble(),
                decimal = false,
                modifier = Modifier.weight(1f),
                onChange = { viewModel.setContractVat(it.toInt()) },
            )
            ContractNumberField(
                label = "Zaliczka %",
                value = filling.zaliczkaProc.toDouble(),
                decimal = false,
                modifier = Modifier.weight(1f),
                onChange = { viewModel.setContractAdvance(it.toInt()) },
            )
            ContractNumberField(
                label = "Płatność (dni)",
                value = filling.terminKoncowyDni.toDouble(),
                decimal = false,
                modifier = Modifier.weight(1f),
                onChange = { viewModel.setContractFinalDays(it.toInt()) },
            )
        }

        ContractDivider()

        // ── Etapy (§ 7) ──────────────────────────────────────────────────────
        SectionTitle(text = "Etapy (§ 7)", action = "+ Etap", onAction = viewModel::addContractStage)
        Spacer(Modifier.height(8.dp))
        filling.etapy.forEachIndexed { index, etap ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = etap.nazwa,
                    onValueChange = { viewModel.renameContractStage(index, it) },
                    label = { Text("Etap ${etap.nr}") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                if (filling.etapy.size > 1) {
                    Spacer(Modifier.width(6.dp))
                    TextButton(onClick = { viewModel.removeContractStage(index) }) { Text("Usuń") }
                }
            }
        }

        ContractDivider()

        // ── Załącznik nr 1 ───────────────────────────────────────────────────
        SectionTitle(
            text = "Załącznik nr 1 — rozpis oferty",
            action = if (form.liczenie) "Liczę…" else "↻ Przelicz z audytu",
            // Akcja zostaje w nagłówku także w trakcie liczenia (znika tylko jej
            // skutek) — inaczej napis „Liczę…" nie miałby gdzie się pokazać.
            onAction = { if (!form.liczenie) viewModel.recalcContractItems() },
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Policzony z audytu (ilości) i formuły ceny węzła (stawki) — ten sam " +
                "rachunek, co w zakładce „Oferta”. Wartość etapu = suma jego pozycji.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (form.braki.isNotEmpty()) {
            ContractWarning(
                "Automat nie policzył wszystkiego — sprawdź te pozycje przed wysłaniem:\n" +
                    form.braki.joinToString("\n") { "• $it" },
            )
        }

        Spacer(Modifier.height(10.dp))
        filling.pozycje.forEachIndexed { index, pozycja ->
            ContractItemRow(
                pozycja = pozycja,
                canRemove = filling.pozycje.size > 1,
                onChange = { viewModel.updateContractItem(index, it) },
                onRemove = { viewModel.removeContractItem(index) },
            )
        }
        OutlinedButton(
            onClick = viewModel::addContractItem,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("+ Pozycja") }

        ContractDivider()

        // ── Sumy ─────────────────────────────────────────────────────────────
        ContractSumRow("Razem netto", "${zl(podglad.netto)} zł")
        ContractSumRow("VAT ${filling.vatStawka}%", "${zl(podglad.vat)} zł")
        ContractSumRow("Razem brutto", "${zl(podglad.brutto)} zł", strong = true)
        Text(
            text = "Zaliczka ${filling.zaliczkaProc}%: ${zl(podglad.zaliczka)} zł · " +
                "płatność końcowa: ${zl(podglad.reszta)} zł",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (podglad.sieroty.isNotEmpty()) {
            ContractWarning(
                "Pozycje wskazują etapy spoza listy: ${podglad.sieroty.joinToString(", ")}. " +
                    "Suma umowy nie zgadzałaby się z rozpisem — popraw numer etapu albo dodaj " +
                    "brakujący etap.",
            )
        }

        // Migawka materiału decyduje o tym, czy podpis klienta ruszy magazyn.
        if (!form.materialZnany) {
            ContractWarning(
                "Bez zestawienia materiałowego: deal nie ma jeszcze rezerwacji z panelu, " +
                    "a telefon materiału nie liczy. Po podpisie magazyn nie zarezerwuje towaru " +
                    "sam — zrób to w panelu („Przelicz z audytu” w zakładce „Zamówienie”).",
            )
        }

        form.blad?.let {
            Spacer(Modifier.height(8.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        Spacer(Modifier.height(12.dp))
        if (form.zmianaDla != null) {
            var confirm by remember { mutableStateOf(false) }
            if (confirm) {
                ContractChangeConfirmDialog(
                    numer = form.zmianaDla.numer,
                    zarzad = form.zmianaDla.zarzad,
                    aneks = form.rodzajZmiany == ContractKind.ANEKS,
                    onDismiss = { confirm = false },
                    onConfirm = {
                        confirm = false
                        viewModel.saveContractChange()
                    },
                )
            }
            Button(
                onClick = { confirm = true },
                enabled = form.gotowe && !form.zapis,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    when {
                        form.zapis -> "Zapisuję zmianę…"
                        !form.zmianaDla.zarzad -> "Wyślij zmianę do akceptacji zarządu"
                        form.rodzajZmiany == ContractKind.ANEKS ->
                            "Wystaw aneks i wyślij do podpisu"

                        else -> "Zapisz zmianę i wyślij do ponownego podpisu"
                    },
                )
            }
        } else {
            Button(
                onClick = viewModel::generateContract,
                enabled = form.gotowe && !form.zapis,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (form.zapis) "Generuję umowę…" else "Wygeneruj umowę i link do podpisu") }
        }
    }
}

/**
 * Pytanie przed wystawieniem zmiany. Panel pyta o to samo w `window.confirm` —
 * to ponowny podpis umowy, którą klient ma już za sobą, więc pytamy wprost.
 */
@Composable
private fun ContractChangeConfirmDialog(
    numer: String,
    zarzad: Boolean,
    aneks: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (aneks) "Aneks do umowy $numer" else "Nowa wersja umowy $numer") },
        text = {
            Text(
                when {
                    !zarzad -> "Zmiana trafi do akceptacji zarządu. Dopiero po akceptacji " +
                        "klient dostanie dokument do podpisu."

                    aneks -> "Powstanie aneks i klient dostanie link do podpisu. Umowa " +
                        "pierwotna obowiązuje dalej — aneks zmienia ją dopiero z chwilą podpisu."

                    else -> "Powstanie nowa wersja umowy i klient dostanie link do ponownego " +
                        "podpisu. Dotychczasowa wersja obowiązuje do chwili złożenia nowego."
                },
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Wystaw") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

/** Pozycja Załącznika nr 1: opis w jednej linii, liczby w drugiej. */
@Composable
private fun ContractItemRow(
    pozycja: ContractItem,
    canRemove: Boolean,
    onChange: (ContractItem) -> Unit,
    onRemove: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
        OutlinedTextField(
            value = pozycja.opis,
            onValueChange = { onChange(pozycja.copy(opis = it)) },
            label = { Text("${pozycja.lp}. Opis") },
            placeholder = { Text("Rura KAN-therm 16×2, zwój 600 m") },
            minLines = 1,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ContractNumberField(
                label = "Ilość",
                value = pozycja.ilosc,
                decimal = true,
                modifier = Modifier.weight(1.1f),
                onChange = { onChange(pozycja.copy(ilosc = it)) },
            )
            OutlinedTextField(
                value = pozycja.jm,
                onValueChange = { onChange(pozycja.copy(jm = it)) },
                label = { Text("J.m.") },
                singleLine = true,
                modifier = Modifier.weight(0.9f),
            )
            ContractNumberField(
                label = "Cena netto",
                value = pozycja.cenaNetto,
                decimal = true,
                modifier = Modifier.weight(1.3f),
                onChange = { onChange(pozycja.copy(cenaNetto = it)) },
            )
            ContractNumberField(
                label = "Etap",
                value = pozycja.etap.toDouble(),
                decimal = false,
                modifier = Modifier.weight(0.8f),
                onChange = { onChange(pozycja.copy(etap = it.toInt().coerceAtLeast(1))) },
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                text = "${zl(pozycja.ilosc * pozycja.cenaNetto)} zł netto",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            if (canRemove) {
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onRemove) { Text("Usuń") }
            }
        }
    }
}

/**
 * Pole liczbowe trzymające WŁASNY tekst: w trakcie pisania („1,", „") wartość
 * bywa niesparsowalna, a gdyby pole czytało liczbę ze stanu, znaki znikałyby
 * spod palca. Przeliczenie z audytu podmienia wartość z zewnątrz — wtedy tekst
 * nadpisujemy (`LaunchedEffect`), bo to już nie jest to, co ktoś wpisywał.
 */
@Composable
private fun ContractNumberField(
    label: String,
    value: Double,
    decimal: Boolean,
    modifier: Modifier = Modifier,
    onChange: (Double) -> Unit,
) {
    fun format(v: Double): String =
        if (decimal) v.toString().removeSuffix(".0") else v.toInt().toString()

    var text by remember { mutableStateOf(format(value)) }
    LaunchedEffect(value) {
        if ((text.replace(',', '.').toDoubleOrNull() ?: Double.NaN) != value) text = format(value)
    }

    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            text = raw
            raw.replace(',', '.').toDoubleOrNull()?.let(onChange)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Number,
        ),
        modifier = modifier,
    )
}

/** Data w formacie ISO (`yyyy-MM-dd`) — takiej oczekuje API umów. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContractDateField(label: String, value: String, onChange: (String) -> Unit) {
    var picking by remember { mutableStateOf(false) }

    if (picking) {
        val startowa = runCatching { LocalDate.parse(value) }.getOrNull() ?: LocalDate.now()
        val state = rememberDatePickerState(
            // DatePicker pracuje w UTC — dzień podajemy jako północ UTC, żeby
            // użytkownik wybrał datę, którą widzi, a nie sąsiednią.
            initialSelectedDateMillis = startowa.atStartOfDay(ZoneOffset.UTC).toInstant()
                .toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onChange(
                            Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toString(),
                        )
                    }
                    picking = false
                }) { Text("Ustaw") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Anuluj") } },
        ) { DatePicker(state = state) }
    }

    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
            Text(formatIsoDay(value) ?: "Ustaw termin")
        }
    }
}

@Composable
private fun ContractSumRow(label: String, value: String, strong: Boolean = false) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
        )
    }
}

/**
 * `2026-10-31` → „31.10.2026". Osobno od `formatDate` z `CrmFormat`: tamten
 * parser oczekuje pełnego ISO z czasem, a termin umowy jest samą datą.
 */
private fun formatIsoDay(iso: String): String? {
    val d = runCatching { LocalDate.parse(iso) }.getOrNull() ?: return null
    return "%02d.%02d.%d".format(d.dayOfMonth, d.monthValue, d.year)
}
