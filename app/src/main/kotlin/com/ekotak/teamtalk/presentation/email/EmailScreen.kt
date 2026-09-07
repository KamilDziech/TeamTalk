package com.ekotak.teamtalk.presentation.email

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ekotak.teamtalk.domain.model.EmailFolder
import com.ekotak.teamtalk.domain.model.EmailThread
import com.ekotak.teamtalk.domain.model.MailboxKind
import com.ekotak.teamtalk.domain.model.MailboxScope
import com.ekotak.teamtalk.presentation.components.AppTopBar
import com.ekotak.teamtalk.presentation.service.WarningBar
import com.ekotak.teamtalk.presentation.theme.SyncBlue
import kotlinx.coroutines.launch

/**
 * Moduł Email — lista wątków, układ Gmaila przełożony na 360 dp.
 *
 * Trzy warstwy wyboru, których panel nie musi ścieśniać, a telefon musi:
 *  • SKRZYNKI (firmowa / personalna) — pasek zakładek pod nagłówkiem; w panelu
 *    to dwie zakładki huba Komunikacja,
 *  • FOLDERY — szuflada spod hamburgera, bo sidebar panelu zająłby pół ekranu,
 *  • WIDOK „Moje / Wszystkie" — dwa chipy nad listą, wyłącznie w skrzynce
 *    firmowej i wyłącznie z uprawnieniem `email.view_all`.
 *
 * Pusta lista w widoku „Moje" dostaje osobny komunikat i przycisk przejścia na
 * „Wszystkie": u kogoś, kto nie prowadzi deali (np. w biurze), wycinek bywa
 * pusty przy pełnej skrzynce firmowej i bez tego zdania wyglądałoby to na awarię.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmailScreen(
    onNavigateBack: () -> Unit,
    onOpenThread: (String) -> Unit,
    viewModel: EmailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var searchOpen by remember { mutableStateOf(false) }

    LaunchedEffect(state.message, state.error) {
        val text = state.error ?: state.message
        if (text != null) {
            snackbarHostState.showSnackbar(text)
            viewModel.consumeMessage()
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                FolderDrawer(
                    state = state,
                    onSelectFolder = { folder ->
                        viewModel.selectFolder(folder)
                        scope.launch { drawerState.close() }
                    },
                )
            }
        },
    ) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                AppTopBar(
                    title = state.folder.label,
                    onNavigateBack = onNavigateBack,
                    actions = {
                        IconButton(onClick = { searchOpen = !searchOpen }) {
                            Icon(
                                if (searchOpen) Icons.Filled.Close else Icons.Filled.Search,
                                contentDescription = "Szukaj w poczcie",
                            )
                        }
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Foldery")
                        }
                    },
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = viewModel::startCompose) {
                    Icon(Icons.Filled.Edit, contentDescription = "Nowa wiadomość")
                }
            },
        ) { padding ->
            Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                MailboxTabs(
                    state = state,
                    onSelect = viewModel::selectMailbox,
                )

                if (searchOpen) {
                    SearchField(
                        query = state.query,
                        searching = state.searching,
                        onChange = viewModel::onQueryChange,
                        onSearch = viewModel::runSearch,
                        onClear = {
                            viewModel.clearSearch()
                            searchOpen = false
                        },
                    )
                }

                if (state.canSwitchScope) {
                    ScopeChips(scope = state.scope, onSelect = viewModel::selectScope)
                }

                if (state.searchResults != null) {
                    Text(
                        text = "Wyniki szukania w folderze ${state.folder.label.lowercase()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }

                PullToRefreshBox(
                    isRefreshing = state.isRefreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    when {
                        state.isLoading && state.visibleThreads.isEmpty() -> LoadingBox()
                        state.visibleThreads.isEmpty() -> EmptyBox(
                            state = state,
                            onShowAll = { viewModel.selectScope(MailboxScope.ALL) },
                        )
                        else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(state.visibleThreads, key = { it.id }) { thread ->
                                ThreadRow(
                                    thread = thread,
                                    onOpen = { onOpenThread(thread.id) },
                                    onStar = { viewModel.toggleStar(thread) },
                                    onArchive = { viewModel.move(thread, EmailFolder.ARCHIVE) },
                                    onTrash = { viewModel.trash(thread) },
                                )
                                HorizontalDivider(
                                    color = MaterialTheme.colorScheme.outlineVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    val composer = state.composer
    if (composer != null) {
        EmailComposeSheet(
            title = composer.title,
            fromAddress = state.mailbox?.address.orEmpty(),
            to = composer.to,
            cc = composer.cc,
            subject = composer.subject,
            body = composer.body,
            attachments = composer.attachments,
            sending = composer.sending,
            onToChange = { value -> viewModel.updateComposer { it.copy(to = value) } },
            onCcChange = { value -> viewModel.updateComposer { it.copy(cc = value) } },
            onSubjectChange = { value -> viewModel.updateComposer { it.copy(subject = value) } },
            onBodyChange = { value -> viewModel.updateComposer { it.copy(body = value) } },
            onAddAttachment = viewModel::addAttachment,
            onRemoveAttachment = viewModel::removeAttachment,
            onSend = viewModel::send,
            onSaveDraft = viewModel::saveDraft,
            onDismiss = viewModel::cancelCompose,
        )
    }
}

/**
 * Zakładki skrzynek. Etykieta to część adresu przed „@" — na 360 dp pełny
 * `kontakt@ekotak.pl` obok drugiego adresu nie mieści się w jednej linii,
 * a właśnie ta pierwsza część odróżnia skrzynkę firmową od personalnej.
 */
@Composable
private fun MailboxTabs(
    state: EmailViewModel.UiState,
    onSelect: (String) -> Unit,
) {
    if (state.mailboxes.size < 2) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.mailboxes.forEach { mailbox ->
            val selected = mailbox.id == state.accountId
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onSelect(mailbox.id) },
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                ) {
                    Text(
                        text = if (mailbox.kind == MailboxKind.PERSONAL) "👤" else "🏢",
                        fontSize = 12.sp,
                    )
                    Text(
                        text = " ${mailbox.shortLabel}",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.onPrimary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (mailbox.unread > 0) {
                        Text(
                            text = " ${mailbox.unread}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary
                            else MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}

/** „Moje" = wycinek opiekuna, „Wszystkie" = cała skrzynka firmowa. */
@Composable
private fun ScopeChips(scope: MailboxScope, onSelect: (MailboxScope) -> Unit) {
    Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = scope == MailboxScope.MINE,
            onClick = { onSelect(MailboxScope.MINE) },
            label = { Text("Moje") },
        )
        FilterChip(
            selected = scope == MailboxScope.ALL,
            onClick = { onSelect(MailboxScope.ALL) },
            label = { Text("Wszystkie") },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchField(
    query: String,
    searching: Boolean,
    onChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
) {
    TextField(
        value = query,
        onValueChange = onChange,
        placeholder = { Text("Szukaj w poczcie…") },
        singleLine = true,
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (searching) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                IconButton(onClick = onClear) {
                    Icon(Icons.Filled.Close, contentDescription = "Wyczyść")
                }
            }
        },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        colors = TextFieldDefaults.colors(),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

/** Szuflada folderów — odpowiednik sidebara panelu. */
@Composable
private fun FolderDrawer(
    state: EmailViewModel.UiState,
    onSelectFolder: (EmailFolder) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 12.dp)) {
        Text(
            text = state.mailbox?.address ?: "Poczta",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        EmailFolder.entries.forEach { folder ->
            val unread = state.unreadIn(folder)
            val total = state.totalIn(folder)
            NavigationDrawerItem(
                selected = folder == state.folder,
                onClick = { onSelectFolder(folder) },
                icon = { Text(folderIcon(folder)) },
                label = { Text(folder.label) },
                badge = {
                    val badge = if (unread > 0) "$unread" else if (total > 0) "$total" else ""
                    if (badge.isNotEmpty()) {
                        Text(
                            text = badge,
                            fontWeight = if (unread > 0) FontWeight.Bold else FontWeight.Normal,
                        )
                    }
                },
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }

        if (state.labels.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text(
                text = "Etykiety",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
            state.labels.forEach { label ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(labelColor(label.color)),
                    )
                    Text(
                        text = " ${label.name}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

/**
 * Wiersz listy: awatar z inicjałami, nadawca, temat, fragment i data —
 * dokładnie ta hierarchia, po której ludzie skanują skrzynkę w Gmailu.
 * Gwiazdka jest osobnym celem dotknięcia, a archiwum i kosz siedzą pod
 * przytrzymaniem, bo gest przesunięcia gryzie się z przewijaniem listy.
 */
@Composable
private fun ThreadRow(
    thread: EmailThread,
    onOpen: () -> Unit,
    onStar: () -> Unit,
    onArchive: () -> Unit,
    onTrash: () -> Unit,
) {
    var actionsOpen by remember { mutableStateOf(false) }
    val weight = if (thread.unread) FontWeight.Bold else FontWeight.Normal

    Column(modifier = Modifier.fillMaxWidth().clickable { onOpen() }) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(avatarColor(thread.fromAddr)),
            ) {
                Text(
                    text = emailInitials(thread.fromName, thread.fromAddr),
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = thread.who,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = weight,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (thread.messageCount > 1) {
                        Text(
                            text = " ${thread.messageCount} ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = formatThreadDate(thread.lastAt),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = weight,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = thread.subject,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = weight,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (thread.hasAttachment) Text("📎 ", fontSize = 12.sp)
                    Text(
                        text = thread.snippet,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    thread.labels.forEach { label ->
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = labelColor(label.color).copy(alpha = 0.16f),
                        ) {
                            Text(
                                text = label.name,
                                style = MaterialTheme.typography.labelSmall,
                                color = labelColor(label.color),
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.dp),
                            )
                        }
                    }
                    if (thread.dealId != null) {
                        Text(
                            text = "🔗 deal",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (thread.pendingSync) {
                        Text(
                            text = "W kolejce",
                            style = MaterialTheme.typography.labelSmall,
                            color = SyncBlue,
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onStar, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = if (thread.starred) Icons.Filled.Star
                        else Icons.Filled.StarBorder,
                        contentDescription = if (thread.starred) "Odznacz" else "Oznacz gwiazdką",
                        tint = if (thread.starred) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(
                    onClick = { actionsOpen = !actionsOpen },
                    modifier = Modifier.size(32.dp),
                ) {
                    Text("⋯", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (actionsOpen) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(start = 60.dp, bottom = 6.dp),
            ) {
                if (thread.folder != EmailFolder.ARCHIVE) {
                    TextButton(onClick = { actionsOpen = false; onArchive() }) {
                        Icon(
                            Icons.Filled.Archive,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(" Archiwizuj")
                    }
                }
                TextButton(onClick = { actionsOpen = false; onTrash() }) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(if (thread.folder == EmailFolder.TRASH) " Usuń trwale" else " Do kosza")
                }
            }
        }
    }
}

@Composable
private fun LoadingBox() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyBox(state: EmailViewModel.UiState, onShowAll: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        when {
            state.query.isNotBlank() -> Text(
                text = "Brak wyników.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.emptyBecauseOfScope && state.canSwitchScope -> {
                Text(
                    text = "Pusto — w tym folderze nie ma wątków przypisanych do Ciebie.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = onShowAll) { Text("Pokaż całą skrzynkę") }
            }
            state.emptyBecauseOfScope -> Text(
                text = "Pusto — w tym folderze nie ma wątków Twoich klientów.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            else -> Text(
                text = "Pusto w folderze ${state.folder.label.lowercase()}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (state.syncedAt == null) {
            Spacer(modifier = Modifier.size(12.dp))
            WarningBar("Pokazujemy zapamiętaną pocztę — świeżej nie udało się pobrać.")
        }
    }
}
