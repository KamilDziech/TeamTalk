package com.ekotak.teamtalk.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ekotak.teamtalk.presentation.theme.FgLight
import com.ekotak.teamtalk.presentation.theme.MutedDark
import com.ekotak.teamtalk.presentation.theme.MutedLight
import com.ekotak.teamtalk.presentation.theme.NavyBg
import com.ekotak.teamtalk.presentation.theme.NavyPanel

// Nagłówek marki wg board360: solidny panel z wordmarkiem „ekotak" i delikatnym
// cieniem u dołu. Zależny od motywu — w ciemnym granatowy z białym tekstem, w
// jasnym jasny (biały→szary) z ciemnym tekstem, tak jak header board360
// (rgba white .7, tekst --fg). Dzięki temu pasek statusu wtapia się w nagłówek
// i nic go nie „przykrywa". Bez zalewania akcentem — zieleń zostaje na
// aktywnych elementach ekranów.
private val BarGradientDark = Brush.linearGradient(
    colors = listOf(NavyPanel, NavyBg),
    start = Offset(0f, 0f),
    end = Offset(0f, Float.POSITIVE_INFINITY),
)

private val BarGradientLight = Brush.linearGradient(
    colors = listOf(Color(0xFFFFFFFF), Color(0xFFEEF3F9)), // jasny, wg board360
    start = Offset(0f, 0f),
    end = Offset(0f, Float.POSITIVE_INFINITY),
)

@Composable
fun AppTopBar(
    title: String,
    onNavigateBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    // Czytaj faktycznie zastosowany motyw (z colorScheme), nie ustawienie
    // systemu — apka ma własny przełącznik ThemeMode, który system może nie
    // odzwierciedlać. Jasna powierzchnia → szary pasek, ciemna → granatowy.
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val barGradient = if (isDark) BarGradientDark else BarGradientLight
    val contentColor = if (isDark) Color.White else FgLight
    val separatorColor = if (isDark) MutedDark else MutedLight
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = if (isDark) 6.dp else 3.dp, shape = RectangleShape)
                .background(barGradient),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .height(48.dp)
                    .padding(end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onNavigateBack != null) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Wróć",
                        )
                    }
                } else {
                    Spacer(Modifier.width(16.dp))
                }

                // Wordmark marki + separator + tytuł ekranu.
                Text(
                    text = "ekotak",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = contentColor,
                    letterSpacing = 0.5.sp,
                )
                Text(
                    text = "  ·  ",
                    style = MaterialTheme.typography.titleMedium,
                    color = separatorColor,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                Row(
                    horizontalArrangement = Arrangement.End,
                    content = actions,
                )
            }
        }
    }
}
