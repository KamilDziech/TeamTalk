package com.ekotak.teamtalk.presentation.inventory

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.ekotak.teamtalk.domain.model.StockLevel
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Wspólny język wizualny magazynu — te same znaczenia co w panelu
 * (`web/src/app/app/inventory/stockLevel.ts`), żeby czerwień na telefonie
 * znaczyła dokładnie to samo co czerwień na monitorze.
 *
 * Kolory mamy w dwóch kompletach: panel jest pisany pod ciemne tło, a aplikacja
 * chodzi w obu motywach. Na jasnym te same odcienie robią się nieczytelne, więc
 * bierzemy przyciemnione odpowiedniki — decyduje faktycznie zastosowany motyw
 * (jak w `AppTopBar`), nie ustawienie systemu.
 */
@Composable
@ReadOnlyComposable
fun levelColor(level: StockLevel): Color {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return when (level) {
        StockLevel.LOW -> if (dark) Color(0xFFF85149) else Color(0xFFC5343A)
        StockLevel.OK -> if (dark) Color(0xFF7EE787) else Color(0xFF2FA84F)
        StockLevel.OVER -> if (dark) Color(0xFF58A6FF) else Color(0xFF2563EB)
        StockLevel.NOGOAL -> if (dark) Color(0xFF6E7681) else Color(0xFF5A6B7C)
    }
}

/** Akcent modułu — żółty kafelka „Magazyn" z pulpitu (`HomeModules.kt`). */
@Composable
@ReadOnlyComposable
fun inventoryAccent(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color(0xFFEAB308) else Color(0xFFB98900)

/** Kolor rezerwacji — fiolet, jak znacznik 🔒 w panelu. */
@Composable
@ReadOnlyComposable
fun reservedColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color(0xFFC084FC) else Color(0xFF7C3AED)

/** Kolor „w drodze" — błękit pozycji zamówionych u dystrybutora. */
@Composable
@ReadOnlyComposable
fun transitColor(): Color =
    if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) Color(0xFF4AA3FF) else Color(0xFF2563EB)

val StockLevel.label: String
    get() = when (this) {
        StockLevel.LOW -> "Zbyt mało"
        StockLevel.OK -> "Stan bezpieczny"
        StockLevel.OVER -> "Ponad maks."
        StockLevel.NOGOAL -> "Bez celu"
    }

/**
 * Ilości bez zbędnego ogona: magazyn liczy i sztuki, i metry, więc „15" ma
 * zostać „15", a 2,5 rolki ma zostać „2,5".
 */
fun formatQty(value: Double): String {
    if (value.isNaN() || value.isInfinite()) return "—"
    val rounded = (value * 100).roundToLong() / 100.0
    return if (abs(rounded % 1.0) < 0.005) {
        rounded.toLong().toString()
    } else {
        String.format(Locale("pl", "PL"), "%.2f", rounded).trimEnd('0').trimEnd(',')
    }
}

/** Cena netto zakupu; null = ceny jeszcze nie znamy (punkt zakupu bywa bez niej). */
fun formatPrice(value: Double?): String =
    value?.let { String.format(Locale("pl", "PL"), "%.2f zł", it) } ?: "—"

/** Podpis „dane sprzed …" — przy pracy bez zasięgu to jedyna informacja o wieku danych. */
fun syncedLabel(syncedAt: Long?): String? {
    if (syncedAt == null) return null
    val minutes = (System.currentTimeMillis() - syncedAt) / 60_000
    return when {
        minutes < 1 -> "dane sprzed chwili"
        minutes < 60 -> "dane sprzed $minutes min"
        minutes < 1440 -> "dane sprzed ${minutes / 60} godz."
        else -> "dane sprzed ${minutes / 1440} dni"
    }
}

/** Wypełnienie paska stanu: udział w celu, a bez celu — stała, krótka kreska. */
fun barFraction(stock: Double, target: Double, level: StockLevel): Float = when {
    level == StockLevel.NOGOAL -> 0.25f
    target > 0 -> (stock / target).coerceIn(0.0, 1.0).toFloat()
    stock > 0 -> 1f
    else -> 0f
}
