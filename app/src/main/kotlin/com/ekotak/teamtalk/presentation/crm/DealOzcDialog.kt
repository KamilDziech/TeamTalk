package com.ekotak.teamtalk.presentation.crm

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.ekotak.teamtalk.domain.model.DealBuildingKind
import com.ekotak.teamtalk.domain.model.DealOzcData
import kotlin.math.roundToInt

/**
 * Okno „+ OZC" (Obliczenie Zapotrzebowania na Ciepło) — port `LeadOzcModal`
 * panelu, otwierany z zakładki „Remarketing".
 *
 * Moce przepisuje się RĘCZNIE z cieplo.app (dwa pola: budynek i CWU). Checker
 * na dole waliduje moc BUDYNKU względem zakresu kontrolnego
 * 40–50 W/m² × powierzchnia ogrzewana:
 *  - mieści się     → „Audyt zwalidowany", zapis bez dodatkowych warunków,
 *  - nie mieści się → osoba robiąca audyt musi potwierdzić, że wynik jest
 *    policzony poprawnie; bez tego zapis nie przechodzi.
 *
 * Powierzchnia ogrzewana idzie z bloku „Dane budynku" (zakł. Dane). Gdy jej
 * brak — pole jest edytowalne tutaj i zapisuje się z powrotem do budynku, żeby
 * zostało JEDNO źródło prawdy. Dokładnie ta sama reguła co w panelu.
 *
 * Podpisu „kto potwierdził" nie pokazujemy: mobilny model OZC (`DealOzcData`)
 * nie niesie `confirmedById`/`confirmedAt` — stempluje je API, a karta i tak
 * ich nie czyta.
 */
@Composable
fun OzcDialog(
    state: DealDetailViewModel.UiState,
    onDismiss: () -> Unit,
    onSave: (
        buildingKw: Double,
        dhwKw: Double?,
        sourceUrl: String?,
        confirmed: Boolean,
        areaM2: Int?,
    ) -> Unit,
) {
    val deal = state.detail?.deal ?: return
    val ozc = deal.ozcData
    val savedArea = deal.buildingData?.areaM2
    val busy = state.remarketing.isSavingOzc

    var buildingKw by remember { mutableStateOf(ozc?.buildingKw?.toPlainText().orEmpty()) }
    var dhwKw by remember { mutableStateOf(ozc?.dhwKw?.toPlainText().orEmpty()) }
    // Metraż edytujemy tylko wtedy, gdy w „Danych budynku" go jeszcze nie ma.
    var areaDraft by remember { mutableStateOf(savedArea?.toPlainText().orEmpty()) }
    var sourceUrl by remember { mutableStateOf(ozc?.sourceUrl.orEmpty()) }
    var confirmed by remember { mutableStateOf(ozc?.confirmed == true) }
    var error by remember { mutableStateOf<String?>(null) }

    val area = savedArea ?: areaDraft.toDecimalOrNull()
    val kw = buildingKw.toDecimalOrNull()
    val check = ozcCheck(area, kw)

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("OZC — zapotrzebowanie na ciepło") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = if (deal.buildingKind == DealBuildingKind.MODERNIZACJA) {
                        "Budynek modernizowany"
                    } else {
                        "Budynek nowy"
                    } + (savedArea?.let { " · pow. ogrzewana ${it.toPlainText()} m² (z zakł. Dane)" }
                        ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))

                if (savedArea == null) {
                    OzcNumberField(
                        label = "Powierzchnia ogrzewana [m²]",
                        value = areaDraft,
                        enabled = !busy,
                    ) { areaDraft = it; error = null }
                    Spacer(Modifier.height(8.dp))
                }

                OzcNumberField(
                    label = "Moc ciepła dla budynku z cieplo.app [kW]",
                    value = buildingKw,
                    enabled = !busy,
                ) { buildingKw = it; error = null }
                Spacer(Modifier.height(8.dp))

                OzcNumberField(
                    label = "Moc dla CWU z cieplo.app [kW]",
                    value = dhwKw,
                    enabled = !busy,
                ) { dhwKw = it; error = null }
                Spacer(Modifier.height(8.dp))

                OutlinedTextField(
                    value = sourceUrl,
                    onValueChange = { sourceUrl = it; error = null },
                    label = { Text("Link do obliczenia (informacyjnie)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    enabled = !busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                )
                Spacer(Modifier.height(12.dp))

                OzcCheckBox(
                    check = check,
                    confirmed = confirmed,
                    enabled = !busy,
                    onConfirmedChange = { confirmed = it; error = null },
                )

                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy,
                onClick = {
                    val problem = ozcProblem(kw, area, check, confirmed, sourceUrl)
                    if (problem != null) {
                        error = problem
                        return@TextButton
                    }
                    onSave(
                        kw!!,
                        dhwKw.toDecimalOrNull(),
                        normalizeUrl(sourceUrl),
                        check is OzcCheck.Out && confirmed,
                        // Metraż dopisujemy do budynku tylko wtedy, gdy go
                        // tam nie było — inaczej okno OZC nadpisywałoby dane
                        // wpisane w zakładce „Dane".
                        if (savedArea == null) area!!.roundToInt() else null,
                    )
                },
            ) { Text(if (busy) "Zapisuję…" else "Zapisz OZC") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Anuluj") }
        },
    )
}

@Composable
private fun OzcNumberField(
    label: String,
    value: String,
    enabled: Boolean,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { if (it.isNumericInput()) onChange(it) },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
    )
}

/** Boks checkera pod polami — kolor i treść zależą od wyniku walidacji. */
@Composable
private fun OzcCheckBox(
    check: OzcCheck,
    confirmed: Boolean,
    enabled: Boolean,
    onConfirmedChange: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val background = when (check) {
        is OzcCheck.Ok -> colors.primaryContainer
        is OzcCheck.Out -> colors.errorContainer
        else -> colors.surfaceVariant
    }
    val onBackground = when (check) {
        is OzcCheck.Ok -> colors.onPrimaryContainer
        is OzcCheck.Out -> colors.onErrorContainer
        else -> colors.onSurfaceVariant
    }

    Surface(shape = RoundedCornerShape(12.dp), color = background) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Text(
                text = check.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = onBackground,
            )
            check.detail?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = onBackground,
                )
            }

            if (check is OzcCheck.Out) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = confirmed,
                        onCheckedChange = onConfirmedChange,
                        enabled = enabled,
                    )
                    Text(
                        text = "Potwierdzam, że wynik z cieplo.app jest policzony poprawnie",
                        style = MaterialTheme.typography.bodySmall,
                        color = onBackground,
                    )
                }
            }
        }
    }
}

// ── Checker 40–50 W/m² ───────────────────────────────────────────────────────

/** Dolna/górna granica zakresu kontrolnego mocy budynku [W/m²]. */
private const val WM2_MIN = 40.0
private const val WM2_MAX = 50.0

/** Wynik walidacji mocy budynku względem powierzchni ogrzewanej. */
sealed class OzcCheck(val title: String, val detail: String?) {
    object NoArea : OzcCheck("Podaj powierzchnię ogrzewaną, żeby zwalidować wynik", null)
    class NoPower(range: String) : OzcCheck(
        "Czeka na moc budynku",
        "Zakres kontrolny 40–50 W/m² ($range).",
    )
    class Ok(wm2: String, range: String) : OzcCheck(
        "Audyt zwalidowany",
        "Wynik $wm2 W/m² mieści się w zakresie 40–50 W/m² ($range).",
    )
    class Out(wm2: String, below: Boolean, range: String) : OzcCheck(
        "Wynik poza zakresem — wymaga potwierdzenia",
        "Wynik $wm2 W/m² jest ${if (below) "poniżej" else "powyżej"} zakresu " +
            "40–50 W/m² ($range).",
    )
}

private fun ozcCheck(area: Double?, kw: Double?): OzcCheck {
    if (area == null || area <= 0) return OzcCheck.NoArea
    val range = "${pl(WM2_MIN * area / 1000)}–${pl(WM2_MAX * area / 1000)} kW " +
        "dla ${area.toPlainText()} m²"
    if (kw == null || kw <= 0) return OzcCheck.NoPower(range)
    val wm2 = kw * 1000 / area
    return if (wm2 >= WM2_MIN && wm2 <= WM2_MAX) {
        OzcCheck.Ok(pl(wm2, 1), range)
    } else {
        OzcCheck.Out(pl(wm2, 1), wm2 < WM2_MIN, range)
    }
}

/** Co blokuje zapis; `null` = wolno zapisać. Kolejność sprawdzeń jak w panelu. */
private fun ozcProblem(
    kw: Double?,
    area: Double?,
    check: OzcCheck,
    confirmed: Boolean,
    sourceUrl: String,
): String? = when {
    kw == null || kw <= 0 -> "Podaj moc ciepła dla budynku z cieplo.app."
    area == null || area <= 0 ->
        "Podaj powierzchnię ogrzewaną — bez niej nie da się zwalidować wyniku."
    check is OzcCheck.Out && !confirmed ->
        "Wynik poza zakresem — zaznacz potwierdzenie, żeby zapisać."
    sourceUrl.isNotBlank() && normalizeUrl(sourceUrl) == null ->
        "Link do cieplo.app nie wygląda na poprawny adres."
    else -> null
}

/**
 * Link wklejany „jak leci", więc dopisujemy brakujący protokół. `null` = to nie
 * wygląda na adres http(s) — pusty tekst też, bo czyści pole.
 */
private fun normalizeUrl(raw: String): String? {
    val text = raw.trim()
    if (text.isEmpty()) return null
    val withScheme = if (text.startsWith("http://", true) || text.startsWith("https://", true)) {
        text
    } else {
        "https://$text"
    }
    val host = withScheme.removePrefix("https://").removePrefix("http://")
        .substringBefore('/')
        .substringBefore('?')
    return if (host.contains('.') && host.length > 2) withScheme else null
}

/** Liczba po polsku: przecinek dziesiętny, stała liczba miejsc. */
private fun pl(value: Double, digits: Int = 2): String =
    String.format(java.util.Locale("pl"), "%.${digits}f", value)

/** Podpis pod przyciskiem „+ OZC" — co już zapisano. */
fun ozcSummary(ozc: DealOzcData?): String? {
    if (ozc == null || ozc.isEmpty) return null
    return listOfNotNull(
        ozc.buildingKw?.let { "budynek ${it.toPlainText()} kW" },
        ozc.dhwKw?.let { "CWU ${it.toPlainText()} kW" },
        if (ozc.confirmed) "potwierdzone mimo odchyłki" else null,
    ).joinToString(" · ").ifBlank { null }
}
