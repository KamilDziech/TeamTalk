package com.ekotak.teamtalk.presentation.crm

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.ekotak.teamtalk.presentation.components.AppTopBar

/**
 * Artykuł wiedzy dla jednej instalacji deala — pełna treść, generowanie
 * i wysyłka klientowi. Wchodzi się tu z kafla zakładki LEAD.
 *
 * Markdown pokazujemy jako zwykły tekst: artykuły z API są prozą z nagłówkami
 * i listami, a doklejanie do apki renderera markdown tylko po to, żeby pogrubić
 * nagłówki, kosztowałoby więcej, niż daje. Zdejmujemy jedynie znaczniki, które
 * w surowej postaci czyta się najgorzej.
 */
@Composable
fun KnowledgeArticleScreen(
    pathLabel: String,
    onNavigateBack: () -> Unit,
    viewModel: KnowledgeArticleViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = { AppTopBar(title = "Artykuł wiedzy", onNavigateBack = onNavigateBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                state.error != null -> ArticleError(
                    message = state.error.orEmpty(),
                    onRetry = viewModel::load,
                )

                else -> ArticleContent(
                    pathLabel = pathLabel,
                    state = state,
                    onGenerate = viewModel::generate,
                    onAskSend = { viewModel.askSend(true) },
                )
            }
        }
    }

    if (state.confirmingSend) {
        SendConfirmDialog(
            title = state.article?.title.orEmpty(),
            busy = state.isSending,
            onConfirm = viewModel::send,
            onDismiss = { viewModel.askSend(false) },
        )
    }
}

@Composable
private fun ArticleError(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = onRetry) { Text("Spróbuj ponownie") }
    }
}

@Composable
private fun ArticleContent(
    pathLabel: String,
    state: KnowledgeArticleViewModel.UiState,
    onGenerate: () -> Unit,
    onAskSend: () -> Unit,
) {
    val article = state.article

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        SectionCard {
            SectionTitle(pathLabel)
            SectionGap()
            Text(
                text = article?.title?.takeIf { it.isNotBlank() } ?: "Artykuł jeszcze nie istnieje",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (article != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = listOfNotNull(
                        formatDateTime(article.generatedAt)?.let { "wygenerowany $it" },
                        "wersja ${article.version}",
                        // Bez klucza LLM serwer składa tekst z szablonu. Trzeba to
                        // wiedzieć PRZED wysłaniem klientowi, a nie po.
                        if (article.llmGenerated) null else "tekst szablonowy",
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        SectionGap()

        if (article == null && !state.gate.ready && state.gate.reasons.isNotEmpty()) {
            SectionCard {
                SectionTitle("Czego brakuje")
                SectionGap()
                state.gate.reasons.forEach { reason ->
                    Text(
                        text = "• $reason",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
            }
            SectionGap()
        }

        if (article != null) {
            SectionCard {
                Text(
                    text = plainTextFromMarkdown(article.bodyMarkdown),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            SectionGap()
        }

        if (state.canManage) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = onAskSend,
                    enabled = article != null && !state.isSending && !state.isGenerating,
                    modifier = Modifier.weight(1f),
                ) { Text("Wyślij klientowi") }

                OutlinedButton(
                    onClick = onGenerate,
                    enabled = !state.isGenerating && (state.gate.ready || article != null),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        when {
                            state.isGenerating -> "Generuję…"
                            article == null -> "Wygeneruj"
                            else -> "Odśwież"
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SendConfirmDialog(
    title: String,
    busy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Wysłać artykuł klientowi?") },
        text = {
            Column {
                Text(
                    text = title.ifBlank { "Artykuł wiedzy" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Treść pójdzie wiadomością WhatsApp na wątek tego deala. " +
                        "Poza oknem 24 h od ostatniej wiadomości klienta WhatsApp " +
                        "odrzuci wysyłkę — wtedy odezwij się do klienta inaczej.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = !busy) {
                Text(if (busy) "Wysyłam…" else "Wyślij")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Anuluj") }
        },
    )
}

/**
 * Zdejmuje z markdown-a znaczniki, które w surowym tekście przeszkadzają
 * najbardziej: kratki nagłówków, gwiazdki pogrubień i myślniki punktorów
 * zamienione na kropkę. Reszta (tabele, linki) zostaje jak jest — na telefonie
 * czyta się to i tak lepiej niż ich okrojona namiastka.
 */
private fun plainTextFromMarkdown(markdown: String): String = markdown
    .lineSequence()
    .map { line ->
        line
            .replace(Regex("^#{1,6}\\s*"), "")
            .replace(Regex("^\\s*[-*]\\s+"), "• ")
            .replace("**", "")
            .replace("__", "")
    }
    .joinToString("\n")
    .trim()
