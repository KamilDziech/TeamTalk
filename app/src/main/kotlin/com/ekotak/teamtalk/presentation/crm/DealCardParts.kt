package com.ekotak.teamtalk.presentation.crm

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Wspólne części kart deala — używane przez wszystkie zakładki, więc siedzą
 * osobno zamiast być prywatne w ekranie szczegółów.
 *
 * Kształt bloków idzie za szatą board360: ciemna płyta z ledwie widoczną ramką,
 * nagłówek wersalikami z rozstrzeloną spacją, a zieleń marki wyłącznie tam,
 * gdzie coś jest wybrane albo klikalne. Dzięki temu na całej karcie zieleń
 * znaczy zawsze to samo.
 */

/** Ramka pojedynczego bloku treści (odpowiednik `Field`/sekcji z panelu). */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), content = content)
    }
}

/**
 * Nagłówek bloku. Po prawej stoi albo akcja („Zmień", „wyślij klientowi"),
 * albo `accent` — krótkie podsumowanie stanu bloku (np. „3 wybrane"). Oba są
 * w nagłówku, a nie pod treścią, bo w panelu też stoją przy tytule sekcji.
 */
@Composable
fun SectionTitle(
    text: String,
    action: String? = null,
    onAction: (() -> Unit)? = null,
    accent: String? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f, fill = false),
        )
        Spacer(Modifier.width(8.dp))
        when {
            action != null && onAction != null -> Text(
                text = action,
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.6.sp),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )

            accent != null -> Text(
                text = accent.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

/**
 * Wiersz „etykieta — wartość". Pusta wartość znika z karty zamiast pokazywać
 * myślnik: na wąskim ekranie kilkanaście pustych wierszy zasłoniłoby te, które
 * naprawdę coś niosą (w panelu jest inaczej, bo tam wszystko widać naraz).
 */
@Composable
fun InfoRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/** Odstęp między blokami zakładki — jedna wartość dla całej karty. */
@Composable
fun SectionGap() = Spacer(Modifier.height(12.dp))

/**
 * Pojedynczy chip wyboru. Zaznaczony rysuje się zielenią marki na przygaszonym
 * zielonym tle — ten sam sygnał, co „wybrane" w drzewie instalacji.
 */
@Composable
fun ChoicePill(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val border = if (selected) colors.primary else colors.outline
    val content = when {
        selected -> colors.primary
        enabled -> colors.onSurface
        else -> colors.onSurfaceVariant
    }
    val shape = RoundedCornerShape(999.dp)

    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = content.copy(alpha = if (enabled) 1f else 0.6f),
        modifier = Modifier
            .clip(shape)
            .background(if (selected) colors.primary.copy(alpha = 0.12f) else Color.Transparent)
            .border(1.dp, border.copy(alpha = if (enabled) 1f else 0.5f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/**
 * Rząd chipów jednokrotnego wyboru. Bez opcji „brak": tam, gdzie wartość wolno
 * wyczyścić, robi to osobna akcja — chip „brak" obok dwóch realnych wariantów
 * czytałby się jak trzeci wariant.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> PillChoiceRow(
    options: List<T>,
    selected: T?,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit,
    enabled: Boolean = true,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            ChoicePill(
                label = optionLabel(option),
                selected = option == selected,
                enabled = enabled,
                onClick = { onSelect(option) },
            )
        }
    }
}

/**
 * Blok domyślnie zwinięty do samego nagłówka. Zakładka LEAD trzyma tak dane
 * archiwalne zgłoszenia: mają być pod ręką, ale nie mogą spychać w dół tego,
 * na czym handlowiec pracuje przed spotkaniem.
 */
@Composable
fun CollapsibleSectionCard(
    title: String,
    summary: String? = null,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by remember { mutableStateOf(initiallyExpanded) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")

    SectionCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title.uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp),
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (summary != null && !expanded) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Zwiń: $title" else "Rozwiń: $title",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp).rotate(rotation),
            )
        }

        AnimatedVisibility(visible = expanded) {
            Column(Modifier.fillMaxWidth().padding(top = 12.dp), content = content)
        }
    }
}

/**
 * Zastępnik zakładki, której ekran jeszcze nie ma. Mówi wprost, gdzie te dane
 * są dziś dostępne — „w przygotowaniu" bez wskazówki zostawiłoby handlowca
 * w terenie bez odpowiedzi.
 */
@Composable
fun TabPlaceholder(tab: DealTab) {
    SectionCard {
        SectionTitle(tab.label)
        SectionGap()
        Text(
            text = "Ta zakładka nie jest jeszcze dostępna na telefonie. " +
                "Otwórz kartę deala w panelu board360, żeby zobaczyć „${tab.label}”.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
