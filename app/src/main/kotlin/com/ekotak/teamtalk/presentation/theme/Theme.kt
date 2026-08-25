package com.ekotak.teamtalk.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Motyw TeamTalka odwzorowujący szatę aplikacji wzorcowej board360 (ekotak.app):
 * akcent = neonowa zieleń marki na czarnym tekście, ciemne tło granat→czerń
 * jako tożsamość, jasny wariant neutralizujący tło. Bez dynamicColor (Material
 * You), aby paleta marki była identyczna na każdym urządzeniu.
 */

// Motyw ciemny — domyślna tożsamość marki (board360 color-scheme: dark).
private val DarkColorScheme = darkColorScheme(
    primary              = EkotakGreen,      // --accent
    onPrimary            = EkotakBlack,      // --accent-contrast (czarny tekst na zieleni)
    primaryContainer     = Color(0xFF14361A),
    onPrimaryContainer   = OkGreen,
    secondary            = OkGreen,
    onSecondary          = EkotakBlack,
    secondaryContainer   = Color(0xFF14361A),
    onSecondaryContainer = OkGreen,
    tertiary             = Orange600,        // warning/pending
    onTertiary           = EkotakBlack,
    error                = Red600,
    onError              = Color.White,
    background           = NavyBg,           // --bg-2
    onBackground         = FgDark,           // --fg
    surface              = NavySurface,      // --surface-2
    onSurface            = FgDark,
    surfaceVariant       = NavyPanel,        // --panel
    onSurfaceVariant     = MutedDark,        // --muted
    outline              = BorderDark,       // --border
    outlineVariant       = Color(0xFF202934),
)

// Motyw jasny — neutralne tło, akcent zieleni marki zachowany.
private val LightColorScheme = lightColorScheme(
    primary              = EkotakGreen,
    onPrimary            = EkotakBlack,
    primaryContainer     = Color(0xFFCDEFC2),
    onPrimaryContainer   = Color(0xFF0C2A08),
    secondary            = EkotakGreenDark,
    onSecondary          = Color.White,
    secondaryContainer   = Color(0xFFCDEFC2),
    onSecondaryContainer = Color(0xFF0C2A08),
    tertiary             = Orange600,
    onTertiary           = EkotakBlack,
    error                = Red600,
    onError              = Color.White,
    background           = LightBg,          // --bg-2
    onBackground         = FgLight,          // --fg
    surface              = LightSurface,     // --surface-2
    onSurface            = FgLight,
    surfaceVariant       = LightPanel,       // --bg-1
    onSurfaceVariant     = MutedLight,       // --muted
    outline              = BorderLight,      // --border
    outlineVariant       = Color(0xFFD8DEE6),
)

@Composable
fun TeamTalkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography  = TeamTalkTypography,
        shapes      = TeamTalkShapes,
        content     = content,
    )
}
