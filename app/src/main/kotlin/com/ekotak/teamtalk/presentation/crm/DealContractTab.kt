package com.ekotak.teamtalk.presentation.crm

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ekotak.teamtalk.domain.model.ContractKind
import com.ekotak.teamtalk.domain.model.ContractPending
import com.ekotak.teamtalk.domain.model.ContractStatus
import com.ekotak.teamtalk.domain.model.DealContract
import com.ekotak.teamtalk.domain.model.TERMIN_ODESLANIA_H
import com.ekotak.teamtalk.domain.model.opisWysylki
import com.ekotak.teamtalk.domain.model.pozostalyCzas
import com.ekotak.teamtalk.presentation.theme.OkGreen
import com.ekotak.teamtalk.presentation.theme.Orange600
import com.ekotak.teamtalk.presentation.theme.Red600
import com.ekotak.teamtalk.presentation.theme.SyncBlue
import kotlinx.coroutines.delay
import java.time.Instant

/**
 * Zakładka „Umowa" karty deala — mobilny odpowiednik `DealContractPanel`.
 *
 * Robi wszystko to, co panel: wystawia umowę z Załącznikiem nr 1 policzonym
 * z audytu, wystawia ją na nowo po terminie odesłania, zgłasza zmianę
 * podpisanej umowy (nową wersją albo aneksem), przyjmuje decyzję zarządu,
 * wystawia nowy link do podpisu, unieważnia i odtwarza zamówienie z umowy.
 * Do tego podgląd dokumentu i PDF do wysłania klientowi.
 *
 * Dlaczego to wszystko jest na telefonie: umowę podpisuje się u klienta w domu,
 * zaraz po rozmowie. Odesłanie handlowca do panelu znaczyłoby „wystawię ci ją
 * jutro z biura", czyli dzień na rozmyślenie się.
 *
 * Czego telefon NIE liczy: zestawienia materiałowego. Dobór materiału to
 * kilkaset linijek rachunku po stronie panelu (ta sama decyzja, co przy
 * „Przelicz z audytu" w zakładce „Zamówienie") — migawkę bierzemy z rezerwacji
 * deala, a gdy jej nie ma, formularz mówi wprost, czym to grozi po podpisie.
 */
@Composable
fun DealContractTab(
    state: DealDetailViewModel.UiState,
    viewModel: DealDetailViewModel,
) {
    val contracts = state.contracts

    contracts.preview?.let { preview ->
        ContractPreviewDialog(
            preview = preview,
            onCopyLink = { viewModel.showMessage("Link do podpisu skopiowany do schowka") },
            signUrl = viewModel::contractSignUrl,
            onClose = viewModel::closeContractPreview,
        )
    }

    if (!contracts.loaded) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }
        return
    }

    // Licznik terminu odesłania ma iść do przodu bez odpytywania serwera; gdy
    // termin minie przy otwartej karcie, dociągamy listę raz — API domyka
    // umowę leniwie właśnie przy odczycie.
    val teraz = rememberMinuteTicker()
    // Znacznik PRZED odczytem: gdyby serwer oddał umowę dalej jako „wysłana"
    // (zegary się rozjechały, zapis nie przeszedł), lista zmieniłaby się i efekt
    // wszedłby ponownie — bez tego byłaby pętla odpytywania.
    val domkniete = remember { mutableSetOf<String>() }
    LaunchedEffect(teraz, contracts.contracts) {
        val spoznione = contracts.contracts.filter {
            it.status == ContractStatus.SENT &&
                it.wygasaLink != null &&
                pozostalyCzas(it.wygasaLink, teraz) == null &&
                it.id !in domkniete
        }
        if (spoznione.isEmpty()) return@LaunchedEffect
        spoznione.forEach { domkniete.add(it.id) }
        viewModel.loadContracts(force = true)
    }

    if (contracts.fromCache) {
        ContractNotice(
            text = "Brak zasięgu — pokazujemy ostatnią kopię. Zapisy wyślemy, gdy wróci sieć.",
            color = SyncBlue,
        )
        SectionGap()
    }

    if (contracts.pendingCount > 0) {
        ContractNotice(
            text = "${contracts.pendingCount} zapis(y) czekają w telefonie na zasięg. " +
                "Numer, PDF i link do podpisu nadaje serwer — powstaną po wysłaniu.",
            color = Orange600,
        )
        SectionGap()
    }

    SectionCard {
        SectionTitle(
            text = "Umowy deala",
            action = if (contracts.form == null) "+ Nowa umowa" else null,
            onAction = if (contracts.form == null) viewModel::openNewContract else null,
        )
        SectionGap()
        Text(
            text = "Dane Zamawiającego zaciągają się z kartoteki klienta i danych do faktury.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (contracts.error != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = contracts.error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { viewModel.loadContracts(force = true) }) {
                Text("Spróbuj ponownie")
            }
        }

        if (contracts.contracts.isEmpty() && contracts.error == null) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Brak umów dla tego deala.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    contracts.contracts.forEach { umowa ->
        SectionGap()
        ContractCard(
            umowa = umowa,
            busy = contracts.busyId == umowa.id,
            pdfBusy = contracts.pdfId == umowa.id,
            formOpen = contracts.form != null,
            teraz = teraz,
            viewModel = viewModel,
        )
    }

    contracts.form?.let { form ->
        SectionGap()
        ContractFormCard(form = form, viewModel = viewModel)
    }
}

/** Tyka co minutę — licznik „zostało X h" ma się ruszać bez odczytu z sieci. */
@Composable
private fun rememberMinuteTicker(): Instant {
    var teraz by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            teraz = Instant.now()
        }
    }
    return teraz
}

// ── Karta umowy ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ContractCard(
    umowa: DealContract,
    busy: Boolean,
    pdfBusy: Boolean,
    formOpen: Boolean,
    teraz: Instant,
    viewModel: DealDetailViewModel,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    var confirm by remember { mutableStateOf<ContractConfirm?>(null) }
    var rejecting by remember { mutableStateOf(false) }

    confirm?.let { pytanie ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(pytanie.tytul) },
            text = { Text(pytanie.tresc) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    pytanie.onConfirm()
                }) { Text(pytanie.akcja) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Anuluj") } },
        )
    }

    if (rejecting) {
        ContractRejectDialog(
            numer = umowa.numer,
            onDismiss = { rejecting = false },
            onReject = { powod ->
                rejecting = false
                viewModel.rejectContractChange(umowa, powod)
            },
        )
    }

    SectionCard {
        // Nagłówek: numer + stan. Kolejka bije stan serwera — jest nowsza.
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = umowa.numer.ifBlank { "Umowa bez numeru" },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            ContractBadge(
                text = umowa.statusLabel,
                color = when {
                    umowa.pending.isNotEmpty() -> Orange600
                    umowa.status == ContractStatus.SIGNED -> OkGreen
                    umowa.status == ContractStatus.EXPIRED -> Red600
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            if (umowa.rodzaj == ContractKind.ANEKS) {
                ContractBadge("Aneks nr ${umowa.wersja}")
            } else if (umowa.wersja > 1) {
                ContractBadge("Wersja ${umowa.wersja} (po zmianie)")
            }
        }

        Spacer(Modifier.height(6.dp))
        ContractMeta(
            when {
                umowa.localOnly ->
                    "Wystawiona w telefonie ${formatDateTime(umowa.utworzona).orEmpty()}"

                umowa.podpisana != null -> buildString {
                    append("Podpisana ${formatDateTime(umowa.podpisana).orEmpty()}")
                    umowa.podpisanaIp?.let { append(" · IP $it") }
                    if (umowa.parafaZalacznika != null) append(" · z Załącznikiem nr 1")
                }

                umowa.wyslana != null ->
                    "Wysłana do podpisu ${formatDateTime(umowa.wyslana).orEmpty()}"

                else -> "Utworzona ${formatDateTime(umowa.utworzona).orEmpty()}"
            },
        )

        // Termin odesłania — licznik na umowie, która czeka na podpis.
        if (umowa.status == ContractStatus.SENT && umowa.wygasaLink != null) {
            ContractMeta(
                "Termin odesłania: ${formatDateTime(umowa.wygasaLink).orEmpty()} " +
                    "(${pozostalyCzas(umowa.wygasaLink, teraz) ?: "termin minął"})",
            )
        }

        opisWysylki(umowa.wysylka)?.let { wys ->
            if (wys.ostrzezenie) ContractWarning(wys.tekst) else ContractMeta(wys.tekst)
        }

        if (umowa.localOnly) {
            ContractWarning(
                "Umowa czeka w telefonie na zasięg. Numer, dokument i link do podpisu " +
                    "nadaje serwer — do czasu wysłania nie ma czego dać klientowi.",
            )
        }

        if (umowa.status == ContractStatus.EXPIRED) {
            ContractAlarm(
                "Klient nie odesłał umowy w ciągu $TERMIN_ODESLANIA_H h — link nie przyjmuje " +
                    "już podpisu. Kwoty w Załączniku nr 1 pochodzą z cennika sprzed terminu, " +
                    "więc zamiast przedłużać ten dokument wystaw nową umowę z aktualnymi cenami.",
            )
        }

        if (umowa.brakParafy) {
            ContractWarning(
                "Brak podpisu pod Załącznikiem nr 1 — umowa podpisana przed wprowadzeniem " +
                    "parafy. Wyślij ponownie do podpisu; klient uzupełni sam załącznik, " +
                    "podpis umowy zostaje bez zmian.",
            )
        }

        // ZMIANA UMOWY — skąd wzięła się ta wersja i co ją zastępuje.
        umowa.zmiana?.let { zmiana ->
            ContractMeta(
                buildString {
                    append("Zmiana zgłoszona ${formatDateTime(zmiana.zgloszona).orEmpty()}")
                    umowa.zastepuje?.let {
                        append(
                            if (umowa.rodzaj == ContractKind.ANEKS) {
                                " · aneks do umowy $it"
                            } else {
                                " · zastępuje umowę $it"
                            },
                        )
                    }
                    zmiana.powod?.takeIf { it.isNotBlank() }?.let { append(" · powód: $it") }
                    zmiana.zaakceptowana?.let {
                        append(" · zaakceptowana ${formatDateTime(it).orEmpty()}")
                    }
                    zmiana.odrzucona?.let {
                        append(" · ODRZUCONA ${formatDateTime(it).orEmpty()}")
                        zmiana.powodOdrzucenia?.let { p -> append(" — $p") }
                    }
                },
            )
        }

        if (umowa.czekaNaAkceptacje) {
            ContractWarning(
                "Zmiana czeka na akceptację zarządu. Klient nie widzi jeszcze tego " +
                    "dokumentu — link do ponownego podpisu powstaje dopiero po akceptacji.",
            )
        }

        if (umowa.zastapionaPrzez != null && umowa.status == ContractStatus.SIGNED) {
            if (umowa.nastepcaPodpisany) {
                // Podpisany aneks nie zamyka umowy — obie strony mają dwa
                // wiążące dokumenty i muszą wiedzieć, że czytają je razem.
                ContractMeta(
                    "Zmieniona podpisanym aneksem ${umowa.zastapionaPrzez} — obowiązuje " +
                        "razem z nim, w zakresie nieobjętym aneksem.",
                )
            } else {
                ContractWarning(
                    "Trwa zmiana tej umowy (${umowa.zastapionaPrzez}). Do chwili podpisania " +
                        (if (umowa.rodzajNastepcy == ContractKind.ANEKS) "aneksu" else "nowej wersji") +
                        " obowiązuje ta.",
                )
            }
        }

        if (umowa.status == ContractStatus.SUPERSEDED) {
            ContractMeta(
                "Zastąpiona podpisaną wersją${umowa.zastapionaPrzez?.let { " $it" }.orEmpty()} — " +
                    "dokument i podpis zostają jako dowód, ale link klienta już nie działa.",
            )
        }

        // Link do podpisu — to po niego handlowiec wchodzi w tę zakładkę.
        val link = umowa.sciezkaPodpisu
        if (link != null && (umowa.status != ContractStatus.SIGNED || umowa.brakParafy)) {
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = {
                    clipboard.setText(AnnotatedString(viewModel.contractSignUrl(link)))
                    viewModel.showMessage("Link do podpisu skopiowany do schowka")
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Kopiuj link do podpisu") }
        }

        Spacer(Modifier.height(4.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!umowa.localOnly) {
                OutlinedButton(
                    onClick = {
                        viewModel.openContractPreview(umowa.id, umowa.numer, umowa.sciezkaPodpisu)
                    },
                ) { Text("Podgląd umowy") }

                // Wersja czekająca na zarząd nie ma jeszcze PDF-a — powstaje
                // dopiero przy akceptacji, razem z linkiem dla klienta.
                if (!umowa.czekaNaAkceptacje) {
                    OutlinedButton(
                        enabled = !pdfBusy,
                        onClick = {
                            viewModel.downloadContractPdf(umowa, context.cacheDir) { file ->
                                context.shareContractPdf(file)
                            }
                        },
                    ) { Text(if (pdfBusy) "Pobieram…" else "PDF — udostępnij") }
                }
            }

            // Po terminie linku nie przedłużamy — API i tak odmówi
            // (`ContractExpired`), a obok stoi właściwa akcja.
            val mozeLink = umowa.status != ContractStatus.CANCELLED &&
                umowa.status != ContractStatus.SUPERSEDED &&
                umowa.status != ContractStatus.EXPIRED &&
                !umowa.czekaNaAkceptacje &&
                !umowa.localOnly &&
                (umowa.status != ContractStatus.SIGNED || umowa.brakParafy)
            if (mozeLink) {
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        confirm = ContractConfirm(
                            tytul = "Nowy link do podpisu",
                            tresc = "Wystawić klientowi nowy link do umowy ${umowa.numer}? " +
                                "Poprzedni przestanie działać.",
                            akcja = "Wystaw link",
                        ) { viewModel.resendContract(umowa) }
                    },
                ) { Text(if (busy) "Wystawiam…" else "Wyślij ponownie do podpisu") }
            }

            if (umowa.status == ContractStatus.EXPIRED) {
                Button(
                    enabled = !busy && !formOpen,
                    onClick = { viewModel.openContractAfterExpiry(umowa) },
                ) { Text("Wystaw nową z aktualnymi cenami") }
            }

            if (umowa.mozeZmienic) {
                OutlinedButton(
                    enabled = !busy && !formOpen,
                    onClick = { viewModel.openContractChange(umowa) },
                ) { Text("Wprowadź zmiany i podpisz ponownie") }
            }

            // Ratunek dla kart, na których zakładka „Zamówienie" jest pusta
            // mimo podpisu. Idempotentne — powtórka tylko potwierdza stan.
            if (umowa.status == ContractStatus.SIGNED) {
                OutlinedButton(
                    enabled = !busy,
                    onClick = { viewModel.rebuildContractOrder(umowa) },
                ) { Text(if (busy) "Odtwarzam…" else "Odtwórz zamówienie z umowy") }
            }

            if (umowa.mogeZdecydowac) {
                Button(
                    enabled = !busy,
                    onClick = {
                        confirm = ContractConfirm(
                            tytul = "Akceptacja zmiany",
                            tresc = "Dać klientowi umowę ${umowa.numer} jeszcze raz do " +
                                "podpisu? Poprzednia wersja przestanie obowiązywać z chwilą " +
                                "złożenia nowego podpisu.",
                            akcja = "Akceptuj i wyślij",
                        ) { viewModel.approveContractChange(umowa) }
                    },
                ) { Text(if (busy) "Wysyłam…" else "Akceptuj zmianę") }
                OutlinedButton(enabled = !busy, onClick = { rejecting = true }) {
                    Text("Odrzuć zmianę")
                }
            }

            // Umowa po terminie jest już martwa — nie ma czego unieważniać.
            val mozeUniewaznic = umowa.status != ContractStatus.SIGNED &&
                umowa.status != ContractStatus.CANCELLED &&
                umowa.status != ContractStatus.SUPERSEDED &&
                umowa.status != ContractStatus.EXPIRED
            if (mozeUniewaznic) {
                OutlinedButton(
                    enabled = !busy,
                    onClick = {
                        confirm = when {
                            // Umowy, której serwer nigdy nie widział, nie ma czego
                            // unieważniać — znika razem z wpisem w kolejce.
                            umowa.localOnly -> ContractConfirm(
                                tytul = "Usunięcie z kolejki",
                                tresc = "Usunąć umowę czekającą na wysyłkę? Nie poszła jeszcze " +
                                    "na serwer, więc przepadnie razem z wpisaną treścią.",
                                akcja = "Usuń",
                            ) { viewModel.cancelContract(umowa) }

                            umowa.zastepuje != null -> ContractConfirm(
                                tytul = "Wycofanie zmiany",
                                tresc = "Wycofać zmianę umowy ${umowa.zastepuje}? W mocy " +
                                    "zostaje ostatni podpisany dokument.",
                                akcja = "Wycofaj",
                            ) { viewModel.cancelContract(umowa) }

                            else -> ContractConfirm(
                                tytul = "Unieważnienie umowy",
                                tresc = "Unieważnić umowę ${umowa.numer}? Link klienta " +
                                    "przestanie działać.",
                                akcja = "Unieważnij",
                            ) { viewModel.cancelContract(umowa) }
                        }
                    },
                ) {
                    // Wersja po zmianie i aneks to wciąż zmiana TEJ SAMEJ umowy
                    // — rozpoznajemy je po `zastepuje`, bo aneks ma własny
                    // licznik i pierwszy z nich jest nr 1.
                    Text(
                        when {
                            umowa.localOnly -> "Usuń z kolejki"
                            umowa.zastepuje != null -> "Wycofaj zmianę"
                            else -> "Unieważnij link"
                        },
                    )
                }
            }
        }
    }
}

/** Pytanie przed akcją, która idzie do klienta albo kasuje jego link. */
private data class ContractConfirm(
    val tytul: String,
    val tresc: String,
    val akcja: String,
    val onConfirm: () -> Unit,
)

@Composable
private fun ContractRejectDialog(
    numer: String,
    onDismiss: () -> Unit,
    onReject: (String?) -> Unit,
) {
    var powod by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Odrzucenie zmiany") },
        text = {
            Column {
                Text("Odrzucić zmianę umowy $numer? Podpisana umowa zostaje bez zmian.")
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = powod,
                    onValueChange = { powod = it },
                    label = { Text("Powód (opcjonalnie)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onReject(powod.ifBlank { null }) }) { Text("Odrzuć") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
    )
}

// ── Drobiazgi wspólne dla karty i formularza ────────────────────────────────

@Composable
internal fun ContractBadge(
    text: String,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

@Composable
internal fun ContractMeta(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
internal fun ContractWarning(text: String) {
    Text(
        text = "⚠ $text",
        style = MaterialTheme.typography.bodySmall,
        color = Orange600,
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
internal fun ContractAlarm(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = Red600,
        modifier = Modifier
            .padding(top = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Red600.copy(alpha = 0.10f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    )
}

/** Pasek nad listą: praca z kopii albo kolejka czekająca na zasięg. */
@Composable
private fun ContractNotice(text: String, color: androidx.compose.ui.graphics.Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Linia oddzielająca bloki formularza — ten sam odstęp, co w innych zakładkach. */
@Composable
internal fun ContractDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 10.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}
