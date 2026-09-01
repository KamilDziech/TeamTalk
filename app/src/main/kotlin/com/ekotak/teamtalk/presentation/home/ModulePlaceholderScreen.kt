package com.ekotak.teamtalk.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ekotak.teamtalk.presentation.components.AppTopBar

/**
 * Zaślepka modułu, który jest już na pulpicie, ale nie ma jeszcze ekranu w
 * aplikacji mobilnej. Dzięki niej kafelek zawsze prowadzi gdzieś sensownie, a
 * użytkownik wie, że moduł jest w planie (a nie że apka się zawiesiła).
 */
@Composable
fun ModulePlaceholderScreen(module: HomeModule, onNavigateBack: () -> Unit) {
    Scaffold(
        topBar = { AppTopBar(title = module.label, onNavigateBack = onNavigateBack) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            ModuleBadge(module = module, size = 64)
            Text(
                text = module.label,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = module.desc,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Text(
                text = "Moduł dostępny w panelu ekotak.app — wersja mobilna w przygotowaniu.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
