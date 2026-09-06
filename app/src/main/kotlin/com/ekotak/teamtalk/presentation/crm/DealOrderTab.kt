package com.ekotak.teamtalk.presentation.crm

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ekotak.teamtalk.domain.model.DealOrder
import com.ekotak.teamtalk.domain.model.OrderItem
import com.ekotak.teamtalk.domain.model.OrderSource
import com.ekotak.teamtalk.domain.model.OrderStatus
import com.ekotak.teamtalk.domain.model.PurchaseLine
import com.ekotak.teamtalk.domain.model.StockReservation
import com.ekotak.teamtalk.presentation.theme.OkGreen
import com.ekotak.teamtalk.presentation.theme.Orange600
import com.ekotak.teamtalk.presentation.theme.Red600
import com.ekotak.teamtalk.presentation.theme.SyncBlue

/**
 * Zakładka „Zamówienie" karty deala — mobilny odpowiednik zakładki `zamowienia`
 * z `DealDrawer`. Te same trzy bloki co w panelu, w kolejności, w jakiej używa
 * ich magazyn:
 *
 *  1. **Zakres** — drzewo etapu „sold", czyli to, co klient KUPIŁ. W panelu
 *     stoi na górze i zajmuje pół szerokości; tu jest zwinięte do nagłówka,
 *     bo na 360 dp wypchnęłoby resztę pod dolną krawędź, a magazynier wchodzi
 *     w tę zakładkę po pozycje, nie po drzewo. Jest to czysty PODGLĄD —
 *     zakres zamówienia zmienia się wyłącznie przez ofertę, tak jak w panelu.
 *  2. **Rezerwacja materiału** — trzy liczby magazynu: ile potrzeba, ile jest
 *     nasze, ile brakuje; plus stan zakupu każdej linii i akcje „wydane" /
 *     „zwolnij".
 *  3. **Zamówienia** — karty z pozycjami i dwoma ptaszkami („zamów.", „odebr.").
 *     To one są treścią zakładki.
 *
 * Czego świadomie NIE ma na telefonie: przycisku „Przelicz z audytu". Zestawienie
 * materiałowe liczy się z całego audytu podłogówki (rozdzielacze, długości rur,
 * chemia) i to rachunek na kilkaset linijek po stronie panelu — przeniesienie go
 * na telefon oznaczałoby drugą implementację tej samej matematyki, a rozjazd
 * między nimi zamawiałby zły towar. Rezerwacja i tak powstaje sama po podpisaniu
 * umowy; telefon ją pokazuje i pozwala domknąć.
 */
@Composable
fun DealOrderTab(
    state: DealDetailViewModel.UiState,
    viewModel: DealDetailViewModel,
) {
    val orders = state.orders

    if (!orders.loaded) {
        Box(
            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }
        return
    }

    if (orders.fromCache) {
        OfflineNotice()
        SectionGap()
    }

    ScopeCard(state = state, viewModel = viewModel)
    SectionGap()
    MaterialsCard(state = state, viewModel = viewModel)
    SectionGap()
    OrdersCard(state = state, viewModel = viewModel)

    if (orders.error != null) {
        SectionGap()
        SectionCard {
            SectionTitle("Zamówienia")
            SectionGap()
            Text(
                text = orders.error,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { viewModel.loadOrders(force = true) }) {
                Text("Spróbuj ponownie")
            }
        }
    }
}

/**
 * Praca bez zasięgu. Mówimy o tym raz, na górze zakładki — trzy bloki niżej
 * pochodzą z tego samego odczytu, więc powtarzanie tego przy każdym z nich
 * tylko zabierałoby miejsce liczbom.
 */
@Composable
private fun OfflineNotice() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(SyncBlue.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.CloudUpload,
            contentDescription = null,
            tint = SyncBlue,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "Brak zasięgu — pokazujemy ostatnią kopię. Zmiany wyślemy, gdy wróci sieć.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

// ── Zakres ───────────────────────────────────────────────────────────────────

/**
 * Drzewo przycięte do samego wyboru klienta — bez pustych kategorii i bez
 * wyszarzonych alternatyw (`pruneToSelected`). Zamawiamy dokładnie to, co
 * klient kupił, więc marka, której nie wziął, byłaby tu tylko szumem.
 */
@Composable
private fun ScopeCard(
    state: DealDetailViewModel.UiState,
    viewModel: DealDetailViewModel,
) {
    val orders = state.orders
    val count = orders.scope.size

    CollapsibleSectionCard(
        title = "Zakres zamówienia",
        summary = when {
            orders.scopeTree.isEmpty() && count == 0 ->
                "Deal nie ma jeszcze zakresu na etapie „Zamówienie”"
            count == 1 -> "1 instalacja"
            count in 2..4 -> "$count instalacje"
            else -> "$count instalacji"
        },
    ) {
        if (orders.scopeTree.isEmpty()) {
            Text(
                text = "Zakres ustala się na karcie „Oferta” — zamówienie jest jego kopią " +
                    "i nie da się go tu zmienić.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@CollapsibleSectionCard
        }

        InstallationTree(
            nodes = orders.scopeTree,
            selected = orders.scope,
            expanded = orders.expanded,
            // Podgląd: dotknięcie nazwy niczego nie zaznacza. Zakres zamówienia
            // zmienia się wyłącznie przez ofertę — tak samo jak w panelu.
            editable = false,
            onToggleSelection = {},
            onToggleBranch = viewModel::toggleOrderScopeBranch,
        )
    }
}

// ── Rezerwacja materiału ─────────────────────────────────────────────────────

@Composable
private fun MaterialsCard(
    state: DealDetailViewModel.UiState,
    viewModel: DealDetailViewModel,
) {
    val orders = state.orders

    SectionCard {
        SectionTitle(
            text = "Rezerwacja materiału",
            accent = orders.clientLabel.takeIf { it.isNotBlank() },
        )
        SectionGap()

        when {
            !orders.materialsAvailable -> Text(
                text = "Brak dostępu do magazynu — rezerwację materiału zobaczysz " +
                    "po nadaniu uprawnienia „inventory.view”.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            orders.reservations.isEmpty() -> Text(
                text = "Brak rezerwacji. Powstanie sama w chwili podpisania umowy; " +
                    "przeliczyć ją z audytu można w panelu.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            else -> {
                MaterialsSummary(orders)

                val unordered = orders.unorderedReservations
                if (unordered.isNotEmpty() && state.canManageInventory) {
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = viewModel::orderMissingMaterials,
                        enabled = !orders.isSaving,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("ZAMÓW braki (${unordered.size})") }
                }

                orders.reservations.forEach { row ->
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    ReservationRow(
                        row = row,
                        purchases = orders.purchasesFor(row),
                        canManage = state.canManageInventory,
                        enabled = !orders.isSaving,
                        onStatus = { status -> viewModel.setReservationStatus(row.id, status) },
                    )
                }

                if (orders.gapReservations.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "${orders.gapReservations.size} poz. nie ma kartoteki " +
                            "w Magazynie — dopóki jej nie założysz, tego materiału nikt " +
                            "nie zarezerwuje ani nie zamówi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Orange600,
                    )
                }
            }
        }
    }
}

/** Trzy liczby, które magazyn czyta pierwsze: ile linii, ile pokrytych, ile do kupienia. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MaterialsSummary(orders: DealDetailViewModel.OrdersState) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StatusPill("${orders.activeReservations.size} poz.", MaterialTheme.colorScheme.onSurfaceVariant)
        StatusPill("${orders.coveredCount} z magazynu", OkGreen)
        if (orders.missingReservations.isNotEmpty()) {
            StatusPill("${orders.missingReservations.size} do kupienia", Red600)
        }
        val inTransit = orders.purchases.count { it.status == "ordered" }
        if (inTransit > 0) StatusPill("$inTransit w drodze", Orange600)
    }
}

@Composable
private fun ReservationRow(
    row: StockReservation,
    purchases: List<PurchaseLine>,
    canManage: Boolean,
    enabled: Boolean,
    onStatus: (String) -> Unit,
) {
    val closed = !row.isActive
    val colors = MaterialTheme.colorScheme

    Column(Modifier.fillMaxWidth()) {
        Text(
            text = row.productName ?: row.itemName,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (closed) colors.onSurfaceVariant else colors.onSurface,
        )
        row.note?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(6.dp))
        // Trzy liczby w jednym wierszu zamiast tabeli z panelu: na 360 dp
        // kolumny „Potrzeba / Z magazynu / Brakuje" zwężają nazwę pozycji do
        // jednej litery, a to nazwa mówi magazynierowi, czego szukać na półce.
        Text(
            text = buildString {
                append("potrzeba ${formatQty(row.quantity)} ${row.unit}")
                if (!closed) {
                    append(" · z magazynu ${formatQty(row.covered)}")
                    if (row.missing > 0) append(" · brakuje ${formatQty(row.missing)}")
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
        )

        Spacer(Modifier.height(6.dp))
        FlowRowOfStatuses(row = row, purchases = purchases)

        if (canManage) {
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (closed) {
                    TextButton(onClick = { onStatus("active") }, enabled = enabled) {
                        Text("Przywróć")
                    }
                } else {
                    TextButton(onClick = { onStatus("done") }, enabled = enabled) {
                        Text("Wydane")
                    }
                    TextButton(onClick = { onStatus("cancelled") }, enabled = enabled) {
                        Text("Zwolnij")
                    }
                }
            }
        }
    }
}

/**
 * Stan linii i stan jej zakupu obok siebie. Zakup pokazujemy NAJDALSZYM etapem,
 * do którego doszedł — magazynier pyta „czy to już jedzie", a nie „ile jest
 * wierszy zakupowych".
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowOfStatuses(row: StockReservation, purchases: List<PurchaseLine>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        when {
            row.status == "done" -> StatusPill("wydane", MaterialTheme.colorScheme.onSurfaceVariant)
            row.status == "cancelled" ->
                StatusPill("zwolnione", MaterialTheme.colorScheme.onSurfaceVariant)
            row.productId == null -> StatusPill("⚠ brak kartoteki", Orange600)
            row.missing > 0 ->
                StatusPill("brak ${formatQty(row.missing)} ${row.unit}", Red600)
            else -> StatusPill("● pokryte", OkGreen)
        }

        if (row.isActive) purchaseLabel(purchases)?.let { (text, color) ->
            StatusPill(text, color)
        }

        if (row.pending) {
            StatusPill("czeka na wysyłkę", SyncBlue)
        }
    }
}

/** Etykieta stanu zakupu; `null` = nikt jeszcze nic pod tę linię nie kupuje. */
@Composable
private fun purchaseLabel(purchases: List<PurchaseLine>): Pair<String, Color>? {
    if (purchases.isEmpty()) return null
    if (purchases.all { it.status == "received" }) return "● przyjęte" to OkGreen

    purchases.firstOrNull { it.status == "ordered" }?.let { line ->
        val eta = formatDayMonth(line.expectedAt)?.let { " · ~$it" }.orEmpty()
        return "zamówione$eta" to Orange600
    }
    if (purchases.any { it.status == "to_order" }) {
        return "na liście zakupowej" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    if (purchases.any { it.status == "proposed" }) {
        return "propozycja magazynu" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    return null
}

// ── Zamówienia ───────────────────────────────────────────────────────────────

@Composable
private fun OrdersCard(
    state: DealDetailViewModel.UiState,
    viewModel: DealDetailViewModel,
) {
    val orders = state.orders

    SectionCard {
        SectionTitle(
            text = "Zamówienia",
            accent = orders.orders.size.takeIf { it > 0 }?.let { "$it" },
        )
        SectionGap()

        when {
            !orders.ordersAvailable -> Text(
                text = "Brak dostępu do zamówień — zobaczysz je po nadaniu " +
                    "uprawnienia „order.manage”.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            else -> {
                if (orders.orders.isEmpty()) {
                    Text(
                        text = "Brak zamówień. Powstają same z podpisanej umowy — " +
                            "po jednym na instalację.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                orders.orders.forEach { order ->
                    Spacer(Modifier.height(10.dp))
                    OrderCard(
                        order = order,
                        canManage = state.canManageOrders,
                        enabled = !orders.isSaving,
                        onOrdered = { item, value ->
                            viewModel.setOrderItem(order.id, item.id, ordered = value)
                        },
                        onReceived = { item, value ->
                            viewModel.setOrderItem(order.id, item.id, received = value)
                        },
                    )
                }

                if (state.canManageOrders) {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(12.dp))
                    NewOrderRow(state = state, viewModel = viewModel)
                }
            }
        }
    }
}

/**
 * Ręczne założenie zamówienia z wygranej oferty. Droga awaryjna: zamówienia
 * powstają zwykle SAME po podpisaniu umowy, więc selektor stoi pod listą,
 * a nie nad nią.
 */
@Composable
private fun NewOrderRow(
    state: DealDetailViewModel.UiState,
    viewModel: DealDetailViewModel,
) {
    val orders = state.orders
    val won = orders.wonOffers
    var menuOpen by remember { mutableStateOf(false) }

    if (won.isEmpty()) {
        Text(
            text = "Brak wygranych ofert — zamówienie z ręki powstaje z oferty " +
                "oznaczonej jako wygrana.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    val selected = won.firstOrNull { it.id == orders.selectedOfferId }

    Column(Modifier.fillMaxWidth()) {
        Box {
            OutlinedButton(
                onClick = { menuOpen = true },
                enabled = !orders.isSaving,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = selected
                        ?.let { "${it.number} · ${formatZl(it.netTotal)}" }
                        ?: "Wygrana oferta…",
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Wybierz ofertę",
                    modifier = Modifier.size(18.dp),
                )
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                won.forEach { offer ->
                    DropdownMenuItem(
                        onClick = {
                            menuOpen = false
                            viewModel.selectOffer(offer.id)
                        },
                        text = { Text("${offer.number} · ${formatZl(offer.netTotal)}") },
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = viewModel::createOrder,
            enabled = !orders.isSaving && selected != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Utwórz zamówienie") }
    }
}

@Composable
private fun OrderCard(
    order: DealOrder,
    canManage: Boolean,
    enabled: Boolean,
    onOrdered: (OrderItem, Boolean) -> Unit,
    onReceived: (OrderItem, Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val status = order.derivedStatus

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.surfaceVariant.copy(alpha = 0.35f))
            .padding(12.dp),
    ) {
        Text(
            text = order.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface,
        )

        Spacer(Modifier.height(6.dp))
        OrderMetaRow(order = order, status = status)

        if (order.items.isEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (order.pendingSince != null) {
                    "Pozycje dopisze panel po wysłaniu — kopiuje je z oferty."
                } else {
                    "Zamówienie bez pozycji."
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
            )
            return@Column
        }

        order.items.forEach { item ->
            Spacer(Modifier.height(8.dp))
            OrderItemRow(
                item = item,
                enabled = enabled && canManage,
                onOrdered = { onOrdered(item, it) },
                onReceived = { onReceived(item, it) },
            )
        }
    }
}

/** Podpisy nagłówka zamówienia: skąd powstało, na czym stoi, ile odhaczono. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OrderMetaRow(order: DealOrder, status: OrderStatus) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StatusPill(
            text = status.label,
            color = when (status) {
                OrderStatus.RECEIVED -> OkGreen
                OrderStatus.ORDERED -> Orange600
                OrderStatus.OPEN -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
        // Zamówienie z umowy powstaje samo po podpisie — warto widzieć, że nikt
        // go nie wpisywał ręcznie z oferty.
        if (order.source == OrderSource.CONTRACT) {
            StatusPill("z umowy", MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (order.items.isNotEmpty()) {
            StatusPill(
                text = "${order.receivedCount}/${order.items.size} odebrane",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (order.pendingSince != null) {
            StatusPill("czeka na wysyłkę", SyncBlue)
        }
    }
}

/**
 * Pozycja z dwoma ptaszkami. Ptaszki stoją POD nazwą, nie obok niej: nazwy
 * pozycji magazynowych bywają długie („Rura PERT/AL/PERT 16×2 — zwój 200 mb"),
 * a ściśnięte do jednej linii z dwoma polami wyboru urywały się w połowie.
 */
@Composable
private fun OrderItemRow(
    item: OrderItem,
    enabled: Boolean,
    onOrdered: (Boolean) -> Unit,
    onReceived: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme

    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurface,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "× ${formatQty(item.quantity)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = colors.onSurfaceVariant,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            CheckLabel(
                label = "zamów.",
                checked = item.ordered,
                enabled = enabled,
                pending = item.pending,
                onCheckedChange = onOrdered,
            )
            Spacer(Modifier.width(12.dp))
            CheckLabel(
                label = "odebr.",
                checked = item.received,
                enabled = enabled,
                pending = item.pending,
                onCheckedChange = onReceived,
            )
            if (item.pending) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = "Czeka na wysyłkę",
                    tint = SyncBlue,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

/**
 * Ptaszek z podpisem jako jeden obszar dotyku. Sam `Checkbox` ma 24 dp — na
 * telefonie trzymanym w rękawicach roboczych to za mało, więc klikalny jest
 * cały napis.
 */
@Composable
private fun CheckLabel(
    label: String,
    checked: Boolean,
    enabled: Boolean,
    pending: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { if (enabled) onCheckedChange(it) },
            enabled = enabled,
            colors = if (pending) {
                CheckboxDefaults.colors(checkedColor = SyncBlue)
            } else {
                CheckboxDefaults.colors()
            },
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = if (enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

/** Krótka etykieta stanu — odpowiednik `badge` z panelu. */
@Composable
private fun StatusPill(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
