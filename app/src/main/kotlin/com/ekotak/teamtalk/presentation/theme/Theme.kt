package com.ekotak.teamtalk.presentation.theme

import androidx.compose.material3.MaterialTheme
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

@Composable
fun TeamTalkTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColorScheme,
        typography  = TeamTalkTypography,
        content     = content,
    )
}
