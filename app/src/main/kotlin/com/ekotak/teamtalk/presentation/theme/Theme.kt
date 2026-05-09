package com.ekotak.teamtalk.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary             = Blue700,
    onPrimary           = Color.White,
    primaryContainer    = Blue100,
    onPrimaryContainer  = Blue900,
    secondary           = Teal600,
    onSecondary         = Color.White,
    secondaryContainer  = Color(0xFFB2DFDB),
    onSecondaryContainer = Color(0xFF00251A),
    error               = Red600,
    onError             = Color.White,
    background          = Grey100,
    onBackground        = Color(0xFF1C1B1F),
    surface             = Color.White,
    onSurface           = Color(0xFF1C1B1F),
    surfaceVariant      = Color(0xFFE8EAF6),
    onSurfaceVariant    = Color(0xFF49454F),
    outline             = Color(0xFF79747E),
)

private val DarkColorScheme = darkColorScheme(
    primary             = Blue600,
    onPrimary           = Color.White,
    primaryContainer    = Blue900,
    onPrimaryContainer  = Blue100,
    secondary           = Teal400,
    onSecondary         = Color.Black,
    error               = Red600,
    onError             = Color.White,
    background          = Color(0xFF121212),
    onBackground        = Color(0xFFE6E1E5),
    surface             = Color(0xFF1E1E1E),
    onSurface           = Color(0xFFE6E1E5),
    surfaceVariant      = Color(0xFF2A2A3A),
    onSurfaceVariant    = Color(0xFFCAC4D0),
    outline             = Color(0xFF938F99),
)

@Composable
fun TeamTalkTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography  = TeamTalkTypography,
        content     = content,
    )
}
