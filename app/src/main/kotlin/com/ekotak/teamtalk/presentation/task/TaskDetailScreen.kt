package com.ekotak.teamtalk.presentation.task

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ekotak.teamtalk.domain.model.Task
import com.ekotak.teamtalk.domain.model.TaskComment
import com.ekotak.teamtalk.domain.model.TaskPriority
import com.ekotak.teamtalk.domain.model.TaskSource
import com.ekotak.teamtalk.domain.model.TaskStatus
import com.ekotak.teamtalk.presentation.components.AppTopBar
import com.ekotak.teamtalk.presentation.components.MentionComposer
import com.ekotak.teamtalk.presentation.components.rememberMentionState
import com.ekotak.teamtalk.presentation.crm.formatDateTime
import com.ekotak.teamtalk.presentation.theme.EkotakGreen
import com.ekotak.teamtalk.presentation.theme.Orange600
import com.ekotak.teamtalk.presentation.theme.Red600

/**
 * Karta zadania: nagłówek z tym, kogo zadanie dotyczy, szybkie akcje
 * (odhaczenie, priorytet) i WĄTEK KOMENTARZY — ten sam, który w Komunikatorze
 * jest dyskusją. Wpisanie „@" i wybór osoby wywołuje ją: komentarz zostaje tu,
 * a wywołany dostaje dyskusję podpisaną nazwiskiem klienta.
 */
@Composable
fun TaskDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: TaskDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val mention = rememberMentionState()
    val listState = rememberLazyListState()
    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    // Nowy komentarz ma być widoczny bez przewijania ręką. Nad wątkiem stoją
    // cztery pozycje listy — nagłówek, pola, opis i tytuł dyskusji — więc sam
    // licznik komentarzy celowałby w środek karty, a nie w ostatni wpis.
    LaunchedEffect(state.comments.size, state.task != null) {
        if (state.comments.isNotEmpty()) {
            val headers = if (state.task != null) 4 else 0
            listState.animateScrollToItem(headers + state.comments.size - 1)
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Zadanie",
                onNavigateBack = onNavigateBack,
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }, enabled = state.task != null) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Więcej")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Usuń zadanie") },
                                onClick = {
                                    menuOpen = false
                                    confirmDelete = true
                                },
                                leadingIcon = {
                                    Icon(Icons.Filled.Delete, contentDescription = null, tint = Red600)
                                },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                // Bez tego klawiatura zasłania i pole komentarza, i listę
                // podpowiedzi po „@" — sprawdzone na telefonie 2026-09-01.
                .imePadding(),
        ) {
            when {
                state.isLoading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }

                state.error != null -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(state.error!!, color = Red600) }

                else -> {
                    val task = state.task
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (task != null) {
                            item { TaskHeader(task, state.saving, viewModel) }
                            item {
                                TaskFieldRows(
                                    task = task,
                                    members = state.members,
                                    saving = state.saving,
                                    viewModel = viewModel,
                                )
                            }
                            item {
                                TaskDescriptionCard(
                                    task = task,
                                    saving = state.saving,
                                    viewModel = viewModel,
                                )
                            }
                            item {
                                Divider()
                                Text(
                                    text = if (state.comments.isEmpty()) {
                                        "Dyskusja"
                                    } else {
                                        "Dyskusja (${state.comments.size})"
                                    },
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                            }
                        }
                        if (state.comments.isEmpty()) {
                            item {
                                Text(
                                    "Brak komentarzy. Wpisz @ i wybierz osobę, żeby ją wywołać.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        items(state.comments, key = { it.id }) { CommentBubble(it) }
                    }

                    if (confirmDelete) {
                        // Usunięcia nie da się cofnąć ani zakolejkować, więc
                        // pytamy wprost i nazywamy zadanie po tytule.
                        AlertDialog(
                            onDismissRequest = { confirmDelete = false },
                            title = { Text("Usunąć zadanie?") },
                            text = {
                                Text(
                                    task?.title?.let { "„$it” zniknie z listy wszystkim w zespole." }
                                        ?: "Zadanie zniknie z listy wszystkim w zespole.",
                                )
                            },
                            confirmButton = {
                                TextButton(
                                    onClick = {
                                        confirmDelete = false
                                        viewModel.delete(onDeleted = onNavigateBack)
                                    },
                                ) { Text("Usuń", color = Red600) }
                            },
                            dismissButton = {
                                TextButton(onClick = { confirmDelete = false }) { Text("Anuluj") }
                            },
                        )
                    }

                    Surface(tonalElevation = 2.dp) {
                        MentionComposer(
                            state = mention,
                            members = state.members,
                            sending = state.sending,
                            modifier = Modifier.padding(12.dp),
                            onSend = {
                                viewModel.send(mention.text, mention.tokens) { mention.clear() }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskHeader(task: Task, saving: Boolean, viewModel: TaskDetailViewModel) {
    val done = task.status == TaskStatus.DONE
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { viewModel.setDone(!done) }, enabled = !saving) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = if (done) "Cofnij odhaczenie" else "Odhacz zadanie",
                    tint = if (done) EkotakGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = task.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textDecoration = if (done) TextDecoration.LineThrough else null,
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = { viewModel.setPriority(task.priority != TaskPriority.HIGH) },
                enabled = !saving,
            ) {
                val high = task.priority == TaskPriority.HIGH
                Icon(
                    if (high) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = if (high) "Zdejmij wysoki priorytet" else "Wysoki priorytet",
                    tint = if (high) Orange600 else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Kogo dotyczy — to samo, co podpisuje dyskusję w Komunikatorze.
        when (val source = task.source) {
            is TaskSource.Deal -> InfoRow(Icons.Filled.Person, source.label ?: "Klient z deala")
            is TaskSource.Project -> InfoRow(Icons.Filled.Folder, source.label ?: "Projekt")
            null -> Unit
        }

        // Opis ma własną sekcję nad dyskusją (TaskDescriptionCard) — czyta się
        // go razem z komentarzami, a nie w nagłówku obok tytułu.

        // Status i termin miały tu wcześniej martwe chipy — teraz są wierszami
        // pod spodem, z których da się je zmienić. Zostaje sam licznik SLA:
        // to nie ustawienie, tylko stan, który sam biegnie.
        dueLabel(task.dueAt)?.takeIf { isOverdue(task.dueAt) && !done }?.let { due ->
            Text(due, style = MaterialTheme.typography.labelMedium, color = Red600)
        }

        slaState(task.createdAt, task.slaHours, done)?.let { sla ->
            Text(
                sla.text,
                style = MaterialTheme.typography.labelMedium,
                color = when (sla.level) {
                    SlaLevel.OVER -> Red600
                    SlaLevel.WARN -> Orange600
                    SlaLevel.OK -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/**
 * Opis zadania — nad dyskusją, bo to jej pierwsze zdanie: czyta się go razem
 * z komentarzami, a nie w nagłówku obok tytułu.
 *
 * Sekcja jest widoczna także wtedy, gdy opisu nie ma. Kreator wypełnia to pole
 * tylko przy dyktowaniu szczegółów, więc najczęstsze użycie karty to DOPISANIE
 * opisu później — pusta ramka z zaproszeniem jest tu ważniejsza niż oszczędność
 * miejsca. W odróżnieniu od wierszy pól (jedno dotknięcie = zapis) tekst ma
 * jawne „Zapisz": w połowie zdania nikt nie chce wysyłki na serwer.
 */
@Composable
private fun TaskDescriptionCard(task: Task, saving: Boolean, viewModel: TaskDetailViewModel) {
    val current = task.description.orEmpty()
    var editing by remember(task.id) { mutableStateOf(false) }
    var draft by remember(task.id) { mutableStateOf(current) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Notes,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "Opis",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                if (!editing) {
                    IconButton(
                        onClick = {
                            draft = current
                            editing = true
                        },
                        enabled = !saving,
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = if (current.isBlank()) "Dodaj opis" else "Edytuj opis",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (editing) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    placeholder = { Text("np. co dokładnie zrobić, na co uważać") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    enabled = !saving,
                )
                Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                    TextButton(onClick = { editing = false }, enabled = !saving) { Text("Anuluj") }
                    TextButton(
                        onClick = {
                            viewModel.setDescription(draft)
                            editing = false
                        },
                        enabled = !saving && draft.trim() != current.trim(),
                    ) { Text("Zapisz") }
                }
            } else {
                // Dotknięcie tekstu (albo zaproszenia) otwiera edycję — ołówek
                // jest dla tych, którzy go szukają, ale nie jest jedyną drogą.
                Text(
                    text = current.ifBlank { "Dodaj opis — dotknij, żeby napisać." },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (current.isBlank()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !saving) {
                            draft = current
                            editing = true
                        },
                )
            }
        }
    }
}

@Composable
private fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Dymek komentarza — własne po prawej, cudze po lewej (jak w Komunikatorze). */
@Composable
private fun CommentBubble(comment: TaskComment) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (comment.mine) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            modifier = Modifier
                .background(
                    if (comment.mine) EkotakGreen.copy(alpha = 0.16f)
                    else MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(
                "${comment.authorName} · ${formatDateTime(comment.createdAt) ?: ""}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(comment.body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
