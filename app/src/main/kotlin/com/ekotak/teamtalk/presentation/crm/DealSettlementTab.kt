package com.ekotak.teamtalk.presentation.crm

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
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
import com.ekotak.teamtalk.domain.model.DealSettlement
import com.ekotak.teamtalk.domain.model.TaskMember
import com.ekotak.teamtalk.domain.ufh.SettlementGroup
import com.ekotak.teamtalk.domain.ufh.UfhPointsResult
import com.ekotak.teamtalk.domain.ufh.fmtPoints
import com.ekotak.teamtalk.domain.ufh.groupBySchemes
import com.ekotak.teamtalk.domain.ufh.groupLabel
import com.ekotak.teamtalk.domain.ufh.settlementBreakdown
import com.ekotak.teamtalk.domain.ufh.settlementStale
import com.ekotak.teamtalk.domain.ufh.totalPoints
import com.ekotak.teamtalk.domain.ufh.ufhMontagePoints
import com.ekotak.teamtalk.domain.ufh.unitLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Zakładka „Rozliczenie" karty deala — mobilny odpowiednik
 * `DealSettlementPanel` panelu.
 *
 * Ile punktów za pracę montażową należy się za tego deala, w rozbiciu na
 * instalacje i na zestawy punktowe („Montaż", „Biuro") z Warunków finansowych.
 * Rachunek jest ten sam, co blok „Punkty montażu" w audycie — tu tylko zebrany
 * dla wszystkich instalacji naraz.
 *
 * Zakres instalacji bierzemy z migawki etapu „Montaż", a gdy pusta —
 * z najdalszego wypełnionego wcześniejszego; zakładka pisze wprost, z którego
 * etapu, bo inaczej lista wygląda na wziętą znikąd.
 *
 * Zatwierdzenie ZAMRAŻA wynik, żeby późniejsza zmiana punktacji albo audytu nie
 * ruszała już rozliczonych deali. Przyciski widzi tylko zarząd
 * (`financial.terms.manage`); sam rachunek — każdy, kto widzi kartę.
 */
@Composable
fun DealSettlementTab(
    state: DealDetailViewModel.UiState,
    viewModel: DealDetailViewModel,
) {
    val settlement = state.settlement

    if (!settlement.loaded) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }
        return
    }

    if (settlement.error != null) {
        SectionCard {
            SectionTitle("Rozliczenie")
            SectionGap()
            Text(
                text = settlement.error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { viewModel.loadSettlement(force = true) }) {
                Text("Spróbuj ponownie")
            }
        }
        return
    }

    if (settlement.installations.isEmpty()) {
        SectionCard {
            SectionTitle("Rozliczenie")
            SectionGap()
            Text(
                text = "Deal nie ma jeszcze wybranych instalacji — rozliczenie liczymy " +
                    "z zakresu montażowego (etap Montaż, a przed nim Zamówienie/Oferta). " +
                    "Zakres zaznacza się na pasku etapu w panelu.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val rows by rememberSettlementRows(settlement)
    val ready = rows
    if (ready == null) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }
        return
    }

    SettlementSummary(ready, settlement)
    SectionGap()

    ready.forEach { row ->
        SettlementCard(
            row = row,
            expanded = row.inst.categoryId in settlement.expanded,
            canManage = state.canManageSettlements,
            busy = settlement.busy,
            members = state.members,
            viewModel = viewModel,
        )
        SectionGap()
    }
}

/** Instalacja z policzonym wynikiem — jeden wiersz zakładki. */
private class SettlementRow(
    val inst: DealDetailViewModel.SettlementInstallation,
    /** `null` = instalacja bez zapisanego audytu, nie ma z czego liczyć. */
    val result: UfhPointsResult?,
    val groups: List<SettlementGroup>,
    /** Punkty z bieżącego rachunku (przed zamrożeniem). */
    val live: Double,
    val snapshot: DealSettlement?,
) {
    /** Ile pokazujemy: zamrożoną migawkę, a gdy jej nie ma — rachunek na żywo. */
    val shown: Double get() = snapshot?.totalPoints ?: live

    /** Migawka rozjechała się z bieżącym rachunkiem (zmienił się audyt/cennik). */
    val stale: Boolean
        get() = snapshot?.let { settlementStale(it.totalPoints, live) } == true
}

/**
 * Rachunek wszystkich instalacji. Idzie w tle i przelicza się dopiero, gdy
 * zmieni się materiał zakładki: pod spodem siedzi prawdziwa geometria rzutu
 * (podział pomieszczeń na pętle), więc liczenie go przy każdym przewinięciu
 * listy zjadałoby telefon.
 */
@Composable
private fun rememberSettlementRows(
    settlement: DealDetailViewModel.SettlementState,
): State<List<SettlementRow>?> =
    produceState<List<SettlementRow>?>(
        initialValue = null,
        settlement.installations,
        settlement.snapshots,
    ) {
        value = withContext(Dispatchers.Default) {
            settlement.installations.map { inst ->
                val result = inst.form?.let { ufhMontagePoints(it, inst.rates) }
                SettlementRow(
                    inst = inst,
                    result = result,
                    groups = result?.let { groupBySchemes(it) }.orEmpty(),
                    live = result?.total ?: 0.0,
                    snapshot = settlement.snapshotFor(inst.categoryId),
                )
            }
        }
    }

// ── Podsumowanie ─────────────────────────────────────────────────────────────

@Composable
private fun SettlementSummary(
    rows: List<SettlementRow>,
    settlement: DealDetailViewModel.SettlementState,
) {
    val sum = totalPoints(rows.map { it.shown })
    val priced = rows.count { it.live > 0 }
    val approved = rows.count { it.snapshot != null }
    val pending = rows.count { it.snapshot?.pending == true }

    SectionCard {
        SectionTitle("Rozliczenie", accent = "${fmtPoints(sum)} pkt")
        SectionGap()
        InfoRow("Instalacje wycenione", "$priced z ${rows.size}")
        InfoRow("Zatwierdzone", "$approved z ${rows.size}")
        if (pending > 0) {
            InfoRow("Czeka na wysyłkę", "$pending")
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Zakres z etapu „${settlement.stage?.label ?: "—"}”. Ilości liczone " +
                "z audytu instalacji, punktacja z Warunków finansowych — zmiana jednego " +
                "albo drugiego przelicza tę listę, dopóki rozliczenie nie zostanie " +
                "zatwierdzone.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

// ── Instalacja ───────────────────────────────────────────────────────────────

@Composable
private fun SettlementCard(
    row: SettlementRow,
    expanded: Boolean,
    canManage: Boolean,
    busy: Boolean,
    members: List<TaskMember>,
    viewModel: DealDetailViewModel,
) {
    SectionCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = row.result != null) {
                    viewModel.toggleSettlementDetails(row.inst.categoryId)
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = row.inst.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (row.inst.path.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = row.inst.path.joinToString(" › "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (row.result != null) "${fmtPoints(row.shown)} pkt" else "brak wyceny",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (row.result != null) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.End,
            )
        }

        row.snapshot?.let { Badge(if (it.pending) "czeka na wysyłkę" else "zatwierdzone") }

        if (row.result == null) {
            SectionGap()
            Text(
                text = if (row.inst.formOwnerId != null) {
                    "Instalacja ma formularz audytu, ale audyt tego deala nie został " +
                        "jeszcze zapisany — wypełnij go w zakładce „Audyt”."
                } else {
                    "Ta technologia nie ma jeszcze wzoru punktowego (formularza audytu " +
                        "ani zestawu punktowego) — rozpisuje się go w Warunkach finansowych."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@SectionCard
        }

        if (!expanded) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Dotknij, żeby zobaczyć rozbicie na pozycje.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (expanded) {
            row.groups.forEach { group ->
                SectionGap()
                GroupBlock(group)
            }

            if (row.result.skipped.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "Nie naliczono (warunek z audytu niespełniony): " +
                        row.result.skipped.joinToString(" · ") { it.label },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            row.result.warnings.forEach { WarningLine(it) }
        }

        SettlementFooter(row, canManage, busy, members, viewModel)
    }
}

@Composable
private fun GroupBlock(group: SettlementGroup) {
    val missing = group.scheme == null
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = groupLabel(group.scheme).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (missing) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "${fmtPoints(group.points)} pkt",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
        )
    }

    group.lines.forEachIndexed { index, line ->
        if (index > 0) {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
        } else {
            Spacer(Modifier.height(8.dp))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = line.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (line.rate == null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = buildString {
                        append(fmtPoints(line.qty))
                        append(" ")
                        append(unitLabel(line.unit))
                        if (line.rate == null) {
                            append(" · brak pozycji w cenniku")
                        } else {
                            append(" × ")
                            append(fmtPoints(line.rate))
                            append(" pkt")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                // „Skąd ta ilość" — bez tego rachunek jest liczbą bez dowodu,
                // a spór o punkty zaczyna się właśnie od tego pytania.
                Text(
                    text = line.source,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                text = "${fmtPoints(line.points)} pkt",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.End,
            )
        }
    }
}

// ── Zatwierdzanie ────────────────────────────────────────────────────────────

@Composable
private fun SettlementFooter(
    row: SettlementRow,
    canManage: Boolean,
    busy: Boolean,
    members: List<TaskMember>,
    viewModel: DealDetailViewModel,
) {
    val snapshot = row.snapshot
    SectionGap()
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    Spacer(Modifier.height(10.dp))

    Text(
        text = when {
            snapshot?.pending == true ->
                "Decyzja zapisana w telefonie — wyślemy ją, gdy wróci zasięg."

            snapshot != null -> buildString {
                append("Zatwierdzone ")
                append(formatDate(snapshot.approvedAt) ?: "—")
                approvedBy(snapshot.approvedById, members)?.let {
                    append(" · ")
                    append(it)
                }
            }

            else -> "Rozliczenie liczone na żywo — zatwierdzenie zamrozi tę kwotę punktów."
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (row.stale) {
        WarningLine(
            "Bieżący rachunek daje ${fmtPoints(row.live)} pkt — zatwierdź ponownie, " +
                "jeśli ma obowiązywać nowa wartość.",
        )
    }

    if (!canManage) return

    Spacer(Modifier.height(10.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (snapshot != null) {
            OutlinedButton(
                onClick = { viewModel.revokeSettlement(row.inst.categoryId) },
                enabled = !busy,
            ) { Text("Cofnij") }
        }
        Button(
            onClick = {
                viewModel.approveSettlement(
                    categoryId = row.inst.categoryId,
                    totalPoints = row.live,
                    breakdown = settlementBreakdown(
                        installation = row.inst.name,
                        path = row.inst.path,
                        groups = row.groups,
                    ),
                )
            },
            // Zero punktów nie ma czego zamrażać — przycisk aktywny mówiłby, że
            // rozliczenie jest gotowe, a ono jeszcze nie istnieje.
            enabled = !busy && row.live > 0,
        ) {
            Text(if (snapshot != null) "Zatwierdź ponownie" else "Zatwierdź rozliczenie")
        }
    }
}

private fun approvedBy(
    userId: String?,
    members: List<TaskMember>,
): String? {
    if (userId.isNullOrBlank()) return null
    return members.firstOrNull { it.id == userId }?.displayName
}

// ── Drobiazgi ────────────────────────────────────────────────────────────────

/** Plakietka stanu przy instalacji („zatwierdzone", „czeka na wysyłkę"). */
@Composable
private fun Badge(text: String) {
    Spacer(Modifier.height(6.dp))
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Ostrzeżenie rachunku — to samo zdanie, które panel pokazuje przy pozycji. */
@Composable
private fun WarningLine(text: String) {
    Spacer(Modifier.height(6.dp))
    Text(
        text = "⚠ $text",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
}
