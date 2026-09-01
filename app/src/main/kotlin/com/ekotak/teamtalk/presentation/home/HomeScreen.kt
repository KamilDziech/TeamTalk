package com.ekotak.teamtalk.presentation.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ekotak.teamtalk.presentation.components.AppTopBar

/**
 * Pulpit — ekran startowy aplikacji ekotak. Siatka kafelków modułów przeniesiona
 * z pulpitu board360 (płaska „szyba": tło panelu, cienka obwódka, kolor modułu
 * punktowo w badge'u ikony — bez gradientów i poświat).
 */
@Composable
fun HomeScreen(onOpenModule: (HomeModule) -> Unit) {
    Scaffold(
        topBar = { AppTopBar(title = "Pulpit") },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Text(
                text = "Wybierz moduł, aby przejść dalej",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
            )

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 108.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(HOME_MODULES, key = { it.key }) { module ->
                    ModuleTile(module = module, onClick = { onOpenModule(module) })
                }
            }
        }
    }
}

@Composable
private fun ModuleTile(module: HomeModule, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 104.dp)
            .clickable(onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        ) {
            ModuleBadge(module, size = 44)
            Text(
                text = module.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Badge ikony: płaski tint koloru modułu, ikona przyciągnięta do koloru tekstu. */
@Composable
internal fun ModuleBadge(module: HomeModule, size: Int = 40) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(module.color.copy(alpha = 0.14f), RoundedCornerShape((size / 4).dp))
            .border(1.dp, module.color.copy(alpha = 0.32f), RoundedCornerShape((size / 4).dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = module.icon,
            contentDescription = null,
            tint = iconTint(module.color),
            modifier = Modifier.size((size * 21 / 40).dp),
        )
    }
}

/** Odpowiednik `color-mix(in srgb, var(--tile) 88%, var(--fg))` z board360. */
@Composable
private fun iconTint(tile: Color): Color = lerp(MaterialTheme.colorScheme.onSurface, tile, 0.88f)
