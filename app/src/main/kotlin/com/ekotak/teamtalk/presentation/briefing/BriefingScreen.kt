package com.ekotak.teamtalk.presentation.briefing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ekotak.teamtalk.domain.model.BriefingItem
import com.ekotak.teamtalk.presentation.components.AppTopBar
import com.ekotak.teamtalk.presentation.crm.formatDateTime
import com.ekotak.teamtalk.presentation.theme.EkotakGreen
import com.ekotak.teamtalk.presentation.theme.Orange600
import com.ekotak.teamtalk.presentation.theme.Red600
import com.ekotak.teamtalk.presentation.theme.SyncBlue

/**
 * ODPRAWA — komunikaty firmowe u odbiorcy, mobilny odpowiednik dzwonka
 * z panelu. Stąd monter dowiaduje się o opublikowanym tygodniu w Harmonogramie
 * („Harmonogram na tydzień…"), bo tą samą rurą idą komunikaty systemowe.
 *
 * Czekające na odhaczenie idą na górę i mają belkę po lewej: na 318 px
 * pigułka przy prawej krawędzi ginie pod długim tytułem, a to ona niesie
 * „przeczytaj i potwierdź".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BriefingScreen(
    onNavigateBack: () -> Unit,
    viewModel: BriefingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = { AppTopBar(title = "Odprawa", onNavigateBack = onNavigateBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (state.isLoading) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
            } else {
                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = viewModel::refresh,
                ) {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        state.error?.let { error -> item { ErrorStrip(error) } }

                        if (state.pending > 0) {
                            item {
                                Text(
                                    "Czeka na potwierdzenie: ${state.pending}",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Orange600,
                                )
                            }
                        }

                        if (state.items.isEmpty() && state.error == null) {
                            item {
                                Text(
                                    "Brak komunikatów. Pojawią się tutaj, gdy biuro coś ogłosi " +
                                        "albo koordynator opublikuje tydzień w harmonogramie.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        items(sorted(state.items), key = { it.id }) { item ->
                            BriefingCard(
                                item = item,
                                acking = state.acking == item.id,
                                onAck = { viewModel.ack(item.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Najpierw to, co czeka na odhaczenie, potem świeższe. */
private fun sorted(items: List<BriefingItem>): List<BriefingItem> =
    items.sortedWith(compareByDescending<BriefingItem> { it.pending }.thenByDescending { it.publishedAt })

@Composable
private fun BriefingCard(item: BriefingItem, acking: Boolean, onAck: () -> Unit) {
    val stripe = when {
        item.pending && item.urgent -> Red600
        item.pending -> Orange600
        item.system -> SyncBlue
        else -> EkotakGreen
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().background(stripe))
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(item.body, style = MaterialTheme.typography.bodyMedium)
                Text(
                    listOfNotNull(
                        if (item.system) "komunikat systemowy" else item.authorName.ifBlank { null },
                        formatDateTime(item.publishedAt),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                when {
                    item.pending -> Button(onClick = onAck, enabled = !acking) {
                        Text(if (acking) "Potwierdzam…" else "Odhacz odbiór")
                    }
                    item.requiresAck -> Text(
                        "Potwierdzone ${formatDateTime(item.ackAt) ?: ""}".trim(),
                        style = MaterialTheme.typography.labelSmall,
                        color = EkotakGreen,
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorStrip(text: String) {
    Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.errorContainer) {
        Text(
            text,
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
    }
}
