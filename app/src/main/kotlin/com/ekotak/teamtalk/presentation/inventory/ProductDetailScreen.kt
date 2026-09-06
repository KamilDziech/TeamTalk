package com.ekotak.teamtalk.presentation.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ekotak.teamtalk.domain.model.Product
import com.ekotak.teamtalk.domain.model.StockLevel
import com.ekotak.teamtalk.domain.model.StockReservation
import com.ekotak.teamtalk.presentation.components.AppTopBar
import com.ekotak.teamtalk.presentation.util.toRelativeTime

/**
 * Karta pozycji magazynu (etap E1 — sam odczyt).
 *
 * Sedno karty to cztery liczby z panelu w jednym rzędzie: <b>stan −
 * zarezerwowane = wolne</b>, obok ilość w drodze. Zaraz pod nimi lokalizacja —
 * przy regale to ona jest pytaniem, więc stoi wyżej niż cena.
 *
 * Przycisków „Wydaj" i „Przyjmij" tu jeszcze nie ma: to etap E2, a przed nim
 * czeka decyzja, kto w ogóle może księgować ruch z telefonu (dziś serwisant
 * i montaż mają samo `inventory.view`).
 */
@Composable
fun ProductDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: ProductDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val product = state.product

    androidx.compose.material3.Scaffold(
        topBar = { AppTopBar(title = "Pozycja", onNavigateBack = onNavigateBack) },
    ) { padding ->
        when {
            state.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            product == null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding).padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Nie ma takiej pozycji w pobranej kartotece. " +
                        "Odśwież listę magazynu — mogła zostać skasowana w panelu.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { Header(product) }
                item { NumbersRow(product) }
                item { StockBarWide(product) }
                item {
                    InfoField(
                        label = "Lokalizacja",
                        value = product.locationLabel ?: "nie wskazano",
                        emphasis = true,
                        warn = product.locationLabel == null,
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.weight(1f)) {
                            InfoField("Cena netto", formatPrice(product.price))
                        }
                        Box(Modifier.weight(1f)) {
                            InfoField("Punkt zakupu", product.distributor ?: "—")
                        }
                    }
                }
                if (product.distributorPrices.size > 1) {
                    item { SectionLabel("Pozostałe punkty zakupu") }
                    items(product.distributorPrices.drop(1), key = { it.name }) { point ->
                        InfoField(point.name, formatPrice(point.price))
                    }
                }
                product.packaging?.let { item { InfoField("Opakowanie", it) } }
                product.producer?.let { item { InfoField("Producent", it) } }
                product.leadTimeDays?.let {
                    item { InfoField("Czas dostawy", "$it dni roboczych") }
                }
                product.notes?.let {
                    item { InfoField("Uwagi magazynu", it, warn = true) }
                }

                item { SectionLabel("Odłożone pod klientów") }
                when {
                    !state.reservationsAvailable -> item {
                        Text(
                            text = "Nie udało się pobrać rezerwacji — pokazany stan może " +
                                "obejmować towar odłożony pod czyjąś umowę.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    state.reservations.isEmpty() -> item {
                        Text(
                            text = "Nic nie jest odłożone — cały stan jest wolny.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> items(state.reservations, key = { it.id }) { ReservationRow(it) }
                }

                syncedLabel(state.syncedAt)?.let {
                    item {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(product: Product) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val color = levelColor(product.level)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(52.dp)
                .background(color.copy(alpha = 0.18f), RoundedCornerShape(11.dp)),
        ) {
            Text(
                text = product.name.trim().take(1).uppercase(),
                style = MaterialTheme.typography.titleLarge,
                color = color,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(text = product.name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = listOfNotNull(product.code, product.installationLabel)
                    .joinToString(" · ")
                    .ifEmpty { "bez kodu" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            product.stockPolicy?.let {
                Text(
                    text = it.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = inventoryAccent(),
                )
            }
        }
    }
}

/** Cztery liczby, którymi żyje moduł. „Wolne" na minusie = obiecaliśmy za dużo. */
@Composable
private fun NumbersRow(product: Product) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberTile("stan", formatQty(product.stock), levelColor(product.level), Modifier.weight(1f))
        NumberTile("rezerw.", formatQty(product.reserved), reservedColor(), Modifier.weight(1f))
        NumberTile(
            label = "wolne",
            value = formatQty(product.free),
            color = if (product.free < 0) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f),
        )
        NumberTile("w drodze", formatQty(product.inTransit), transitColor(), Modifier.weight(1f))
    }
}

@Composable
private fun NumberTile(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = color,
            )
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StockBarWide(product: Product) {
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(barFraction(product.stock, product.target, product.level))
                    .background(levelColor(product.level), RoundedCornerShape(3.dp)),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (product.isNoGoal) {
                "${product.level.label} — pozycja pod klienta, bez progów"
            } else {
                "${product.level.label} · min ${formatQty(product.min)} · " +
                    "maks ${formatQty(product.target)}"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun InfoField(
    label: String,
    value: String,
    emphasis: Boolean = false,
    warn: Boolean = false,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(11.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = if (emphasis) {
                    MaterialTheme.typography.titleSmall
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                color = if (warn) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontSize = 10.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/**
 * Linia kolejki „pod kogo". Kolejność jest tu informacją: kto montuje wcześniej,
 * ten bierze towar z półki — dlatego czerwony brak przy drugiej pozycji znaczy
 * „ten klient zostanie bez materiału", a nie „pomyłka w danych".
 */
@Composable
private fun ReservationRow(line: StockReservation) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = line.clientLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOfNotNull(
                        line.neededBy?.let { "montaż ${it.toRelativeTime()}" },
                        if (line.fromContract) "z umowy" else "ręcznie",
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "${formatQty(line.quantity)} ${line.unit}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (line.missing > 0) {
                    Text(
                        text = "brak ${formatQty(line.missing)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    }
}
