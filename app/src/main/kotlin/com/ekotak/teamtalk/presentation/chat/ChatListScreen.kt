package com.ekotak.teamtalk.presentation.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ekotak.teamtalk.domain.model.ChatKind
import com.ekotak.teamtalk.domain.model.ChatThread
import com.ekotak.teamtalk.presentation.theme.EkotakGreen

/**
 * Skrzynka Komunikatora — jedna lista na czaty, grupy, kanały i wątki
 * komentarzy zadań.
 *
 * Wiersz otwiera EKRAN ROZMOWY, a nie kartę zadania: od 2026-09-23 telefon ma
 * własny czat, więc wątek zadania też czyta się tu, w dymkach. Do karty zadania
 * prowadzi osobna akcja z paska rozmowy.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatListScreen(
    onOpenThread: (String) -> Unit,
    onNavigateBack: () -> Unit,
    viewModel: ChatListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var searching by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            ChatTopBar(
                title = when {
                    state.archived -> "Zarchiwizowane"
                    state.unreadTotal > 0 -> "Komunikator (${state.unreadTotal})"
                    else -> "Komunikator"
                },
                subtitle = if (state.pendingCount > 0) {
                    "${state.pendingCount} w kolejce — wyślę, gdy wróci zasięg"
                } else {
                    null
                },
                onNavigateBack = onNavigateBack,
                actions = {
                    IconButton(onClick = { searching = !searching; if (!searching) viewModel.clearQuery() }) {
                        Icon(
                            if (searching) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = if (searching) "Zamknij szukanie" else "Szukaj",
                        )
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Więcej")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(if (state.archived) "Wróć do rozmów" else "Zarchiwizowane") },
                                onClick = { viewModel.toggleArchived(); menuOpen = false },
                            )
                            DropdownMenuItem(
                                text = { Text("Oznaczone gwiazdką") },
                                onClick = {
                                    viewModel.showStarred()
                                    searching = true
                                    menuOpen = false
                                },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (!state.archived) {
                FloatingActionButton(
                    onClick = viewModel::openNewChat,
                    containerColor = EkotakGreen,
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Nowa rozmowa")
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (searching) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Search, contentDescription = null)
                    Spacer(Modifier.width(10.dp))
                    BasicTextField(
                        value = state.query,
                        onValueChange = viewModel::onQueryChange,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                        ),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (state.query.isEmpty()) {
                                Text(
                                    "Szukaj w rozmowach i wiadomościach",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            inner()
                        },
                    )
                }
                HorizontalDivider()
            } else if (!state.archived) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    ChatFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = state.filter == filter,
                            onClick = { viewModel.setFilter(filter) },
                            label = { Text(filter.label, style = MaterialTheme.typography.labelMedium) },
                        )
                    }
                }
            }

            val hits = state.hits
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }

                hits != null -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(hits, key = { it.message.id }) { hit ->
                        SearchRow(hit.threadTitle, hit.message.authorName, hit.message.preview) {
                            onOpenThread(hit.threadId)
                        }
                        HorizontalDivider()
                    }
                    if (hits.isEmpty()) {
                        item { EmptyNote("Nic takiego nie ma.") }
                    }
                }

                else -> PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(state.visible, key = { it.id }) { thread ->
                            ChatThreadRow(
                                thread = thread,
                                onOpen = { onOpenThread(thread.id) },
                                onPin = { viewModel.togglePinned(thread) },
                                onMute = { viewModel.toggleMuted(thread) },
                                onArchive = { viewModel.toggleArchived(thread) },
                                onUnread = { viewModel.markUnread(thread) },
                                onLeave = { viewModel.leave(thread) },
                            )
                            HorizontalDivider()
                        }
                        if (state.visible.isEmpty()) {
                            item {
                                EmptyNote(
                                    if (state.archived) {
                                        "Archiwum jest puste."
                                    } else {
                                        "Brak rozmów. Zacznij nową plusem w rogu albo wywołaj kogoś " +
                                            "przez @ w komentarzu zadania."
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (state.newChatOpen) {
        NewChatSheet(
            people = state.people,
            onDismiss = viewModel::closeNewChat,
            onPickPerson = { viewModel.startDirect(it, onOpenThread) },
            onCreateGroup = { channel, title, ids ->
                viewModel.startGroup(channel, title, ids, onOpenThread)
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun ChatThreadRow(
    thread: ChatThread,
    onOpen: () -> Unit,
    onPin: () -> Unit,
    onMute: () -> Unit,
    onArchive: () -> Unit,
    onUnread: () -> Unit,
    onLeave: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onOpen,
                    // Wątku zadania nie przypina się ani nie archiwizuje — to
                    // komentarze karty, a nie rozmowa z własnym porządkiem.
                    onLongClick = { if (thread.kind != ChatKind.TASK) menuOpen = true },
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChatAvatar(seed = thread.id, initials = thread.initials)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = (if (thread.mentionedMe) "@ " else "") + thread.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    thread.lastMessage?.let {
                        Text(
                            text = chatListTime(it.createdAt),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (thread.unreadCount > 0) {
                                EkotakGreen
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (thread.lastMessage?.mine == true) {
                        ChatTicks(thread.lastMessage.delivery)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = when {
                            thread.draft != null -> "Szkic: ${thread.draft}"
                            thread.lastMessage == null -> "—"
                            thread.kind == ChatKind.DIRECT || thread.lastMessage.mine ->
                                thread.lastMessage.preview
                            else -> "${thread.lastMessage.authorName}: ${thread.lastMessage.preview}"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (thread.draft != null) {
                            EkotakGreen
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (thread.kind == ChatKind.TASK) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (thread.dealId != null) "klient" else "zadanie",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (thread.muted) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = "Wyciszona",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(14.dp),
                        )
                    }
                    if (thread.unreadCount > 0) {
                        Spacer(Modifier.width(6.dp))
                        ChatUnreadBadge(thread.unreadCount)
                    }
                    if (thread.pendingCount > 0) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "🕓${thread.pendingCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(if (thread.pinned) "Odepnij" else "Przypnij na górze") },
                onClick = { onPin(); menuOpen = false },
            )
            DropdownMenuItem(
                text = { Text(if (thread.muted) "Włącz powiadomienia" else "Wycisz na dobę") },
                onClick = { onMute(); menuOpen = false },
            )
            DropdownMenuItem(
                text = { Text(if (thread.archived) "Przywróć z archiwum" else "Archiwizuj") },
                onClick = { onArchive(); menuOpen = false },
            )
            DropdownMenuItem(
                text = { Text("Oznacz jako nieprzeczytane") },
                onClick = { onUnread(); menuOpen = false },
            )
            if (thread.kind == ChatKind.GROUP || thread.kind == ChatKind.CHANNEL) {
                DropdownMenuItem(
                    text = { Text("Opuść grupę") },
                    onClick = { onLeave(); menuOpen = false },
                )
            }
        }
    }
}

@Composable
private fun SearchRow(title: String, author: String, preview: String, onOpen: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.width(20.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "$author: $preview",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EmptyNote(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
