package com.ekotak.teamtalk.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Promienie kształtów wg aplikacji wzorcowej board360 (globals.css / moduły):
 * małe elementy 6–8px, przyciski 9–10px, karty/panele 12px, duże panele/modale
 * 14–20px, pigułki/badge pełne zaokrąglenie (999px).
 *
 * Material3 wyprowadza kształty komponentów z tej skali: Card → medium,
 * TextField/Chip → extraSmall/small, AlertDialog → extraLarge, FAB → large.
 * Przyciski mają w M3 kształt pełnej pigułki niezależny od Shapes — dlatego
 * podajemy im jawnie [ButtonShape] w miejscach użycia.
 */
val TeamTalkShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small      = RoundedCornerShape(8.dp),
    medium     = RoundedCornerShape(12.dp), // karty/panele
    large      = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp), // modale/duże arkusze
)

/** Przyciski board360: prostokąt r≈10px (nie pigułka). */
val ButtonShape = RoundedCornerShape(10.dp)

/** Pigułki/badge/statusy board360: pełne zaokrąglenie. */
val PillShape = RoundedCornerShape(999.dp)
