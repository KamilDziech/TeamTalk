package com.ekotak.teamtalk.presentation.crm

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Zakładki karty deala — kolejność i etykiety 1:1 z `DealDrawer` panelu, żeby
 * handlowiec znający web szukał tego samego w tym samym miejscu. Nazwa zakładki
 * bywa inna niż jej klucz (`projekt` to „Harmonogram", `whatsapp` to
 * „Komunikacja") — zostawiamy nazwy z panelu, klucze służą tylko nam.
 *
 * `ready` odróżnia zakładki już zbudowane od tych, które czekają na kolejne
 * etapy prac. Świadomie ich NIE ukrywamy: telefon ma pokazywać ten sam kształt
 * procesu co panel, a zniknięcie połowy zakładek sugerowałoby, że deal ich nie
 * ma. Wyszarzona zakładka po dotknięciu mówi, gdzie te dane są dostępne.
 */
enum class DealTab(val key: String, val label: String, val ready: Boolean) {
    DANE("dane", "Dane", true),
    LEAD("lead", "LEAD", true),
    EDUKACJA("edukacja", "Remarketing", false),
    AUDYT("audyt", "Audyt", false),
    OFERTA("oferta", "Oferta", false),
    ZAMOWIENIE("zamowienia", "Zamówienie", false),
    MONTAZ("montaz", "Montaż", false),
    FAKTURA("faktura", "Faktura", false),
    UMOWA("umowa", "Umowa", false),
    KOMUNIKACJA("whatsapp", "Komunikacja", false),
    ZADANIA("zadania", "Zadania", false),
    HARMONOGRAM("projekt", "Harmonogram", false),
    PLIKI("pliki", "Pliki", false),
    HISTORIA("historia", "Historia", true),
    ROZLICZENIE("rozliczenie", "Rozliczenie", false),
    PODSUMOWANIE("podsumowanie", "Podsumowanie", true),
}

/**
 * Poziomo przewijany pasek zakładek. Na 360 dp nie mieści się nawet czwarta
 * część z szesnastu, więc lista sama przewija się do wybranej — inaczej po
 * wejściu w „Podsumowanie" pasek zostawałby na początku i wyglądał, jakby nic
 * nie było zaznaczone.
 */
@Composable
fun DealTabRow(
    selected: DealTab,
    onSelect: (DealTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val tabs = DealTab.entries

    LaunchedEffect(selected) {
        listState.animateScrollToItem(tabs.indexOf(selected).coerceAtLeast(0))
    }

    LazyRow(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(tabs, key = { it.key }) { tab ->
            DealTabChip(
                tab = tab,
                isSelected = tab == selected,
                onClick = { onSelect(tab) },
            )
        }
    }
}

@Composable
private fun DealTabChip(tab: DealTab, isSelected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val background = if (isSelected) colors.primary else Color.Transparent
    val content = when {
        isSelected -> colors.onPrimary
        // Zakładka jeszcze niezbudowana ma być czytelnie słabsza od gotowej,
        // ale nadal klikalna — po dotknięciu tłumaczy, czego brakuje.
        !tab.ready -> colors.onSurfaceVariant.copy(alpha = 0.5f)
        else -> colors.onSurfaceVariant
    }

    Text(
        text = tab.label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
        color = content,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .then(
                if (isSelected) Modifier
                else Modifier.border(1.dp, colors.outlineVariant, RoundedCornerShape(8.dp)),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}
