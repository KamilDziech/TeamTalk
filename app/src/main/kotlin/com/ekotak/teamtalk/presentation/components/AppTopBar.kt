package com.ekotak.teamtalk.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Nagłówek zdjęty na życzenie (ustalenie 2026-09-07): nie ma już paska z
// wordmarkiem „ekotak", tytułem ekranu ani strzałką „Wróć" — treść każdego
// ekranu zaczyna się tuż pod paskiem statusu. Cofanie obsługuje systemowy gest
// / przycisk wstecz (każde `onNavigateBack` w grafie nawigacji to i tak
// `popBackStack()`, a jedyny ekran z własną obsługą — DealEditScreen — pilnuje
// brudnego formularza przez BackHandler, więc nic nie ginie).
//
// Zostają WYŁĄCZNIE akcje ekranu (szukaj, filtry, archiwizuj, zapisz…), bo ich
// usunięcie skasowałoby funkcje, a nie ozdobę. Idą jako gołe ikony przy prawej
// krawędzi, bez tła, cienia i tytułu. Ekran bez akcji nie zajmuje ani piksela
// ponad sam inset paska statusu.
//
// `title` i `onNavigateBack` zostają w sygnaturze celowo: 36 ekranów nadal je
// przekazuje, a przywrócenie paska to wtedy zmiana w jednym pliku.
@Composable
@Suppress("UNUSED_PARAMETER")
fun AppTopBar(
    title: String,
    onNavigateBack: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
) {
    if (actions == null) {
        // Sam odstęp na pasek statusu — bez niego treść wjeżdża pod zegarek.
        Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars))
        return
    }
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .height(40.dp)
                .padding(end = 4.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}
