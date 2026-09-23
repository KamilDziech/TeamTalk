package com.ekotak.teamtalk.presentation.chat

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.ekotak.teamtalk.data.files.ChatAttachmentStore
import com.ekotak.teamtalk.domain.model.ChatMessage
import com.ekotak.teamtalk.domain.model.ChatPerson
import com.ekotak.teamtalk.domain.model.TaskMember
import com.ekotak.teamtalk.presentation.components.MentionComposer
import com.ekotak.teamtalk.presentation.components.rememberMentionState
import com.ekotak.teamtalk.presentation.crm.PickedFile
import com.ekotak.teamtalk.presentation.crm.rememberFilePickers
import com.ekotak.teamtalk.presentation.theme.EkotakGreen
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay
import java.io.File

/**
 * Ekran jednej rozmowy: dymki, pole pisania, załączniki i głosówka.
 *
 * Pasek u góry jest ŚWIADOMYM wyjątkiem od decyzji z 2026-09-07 (patrz
 * `ChatTopBar`) — bez niego nie widać, z kim się pisze.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatThreadScreen(
    onNavigateBack: () -> Unit,
    onOpenTask: (String) -> Unit,
    onOpenDeal: (String) -> Unit,
    viewModel: ChatThreadViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val mention = rememberMentionState()
    val attachments = rememberChatAttachmentStore()
    var menuOpen by remember { mutableStateOf(false) }
    var attachOpen by remember { mutableStateOf(false) }

    val thread = state.thread

    // Zjeżdżamy na dół przy każdej nowej wiadomości — rozmowa czyta się od końca.
    LaunchedEffect(thread?.messages?.size) {
        val count = thread?.messages?.size ?: 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissError()
        }
    }
    LaunchedEffect(state.notice) {
        state.notice?.let {
            snackbar.showSnackbar(it)
            viewModel.dismissNotice()
        }
    }
    // Licznik nagrania tyka po stronie ekranu — model nie ma własnego zegara.
    LaunchedEffect(state.recording) {
        while (state.recording) {
            delay(1000)
            viewModel.tickRecording()
        }
    }

    val pickers = rememberFilePickers { picked ->
        picked.forEach { file ->
            val cached = file.toCacheFile(context.cacheDir)
            if (cached != null) {
                viewModel.sendAttachment(cached, file.name, file.contentType)
            }
        }
    }

    // Zdjęcie z aparatu bierzemy tą samą drogą, co w „Plikach" deala —
    // bez uprawnienia CAMERA, przez intencję do cudzej aplikacji.
    val openFile = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { /* wynik podglądu nas nie interesuje */ }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            ChatTopBar(
                title = thread?.title ?: "Rozmowa",
                subtitle = when {
                    thread == null -> null
                    state.pending > 0 -> "${state.pending} w kolejce"
                    thread.observing -> "Podgląd zarządu — tu nie piszesz"
                    thread.subtitle.isNotBlank() -> thread.subtitle
                    else -> thread.topic
                },
                onNavigateBack = onNavigateBack,
                leading = {
                    if (thread != null) ChatAvatar(thread.id, thread.initials, size = 36)
                },
                actions = {
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Więcej")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            thread?.taskId?.let { taskId ->
                                DropdownMenuItem(
                                    text = { Text("Otwórz kartę zadania") },
                                    onClick = { menuOpen = false; onOpenTask(taskId) },
                                )
                            }
                            thread?.dealId?.let { dealId ->
                                DropdownMenuItem(
                                    text = { Text("Otwórz kartę klienta") },
                                    onClick = { menuOpen = false; onOpenDeal(dealId) },
                                )
                            }
                            if (thread?.pinnedMessage != null) {
                                DropdownMenuItem(
                                    text = { Text("Zdejmij przypięcie") },
                                    onClick = { menuOpen = false; viewModel.pin(null) },
                                )
                            }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            thread?.pinnedMessage?.let { pinned ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "📌 ${pinned.preview}",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    CircularProgressIndicator()
                }

                thread == null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("Nie udało się wczytać rozmowy.")
                }

                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 10.dp),
                ) {
                    itemsIndexed(thread.messages) { index, message ->
                        val previous = thread.messages.getOrNull(index - 1)
                        val newDay = previous == null ||
                            chatDifferentDay(previous.createdAt, message.createdAt)
                        if (newDay) ChatCenterChip(chatDayChip(message.createdAt))
                        ChatBubble(
                            message = message,
                            showAuthor = thread.showsAuthors,
                            firstOfSeries = newDay || previous?.authorId != message.authorId,
                            attachments = attachments,
                            canAct = !thread.readOnly && !message.pending,
                            onReply = { viewModel.reply(message) },
                            onReact = { viewModel.react(message, it) },
                            onStar = { viewModel.star(message) },
                            onPin = { viewModel.pin(message) },
                            onForward = { viewModel.forward(message, emptyList()) },
                            onReceipts = { viewModel.showReceipts(message) },
                            onVote = { viewModel.vote(message, it) },
                            onOpenAttachment = {
                                viewModel.openAttachment(message) { file ->
                                    openFile.launch(file.viewIntent(context, message))
                                }
                            },
                        )
                    }
                }
            }

            if (thread != null && thread.readOnly) {
                Text(
                    text = if (thread.observing) {
                        "Oglądasz cudzą rozmowę — pisanie jest wyłączone."
                    } else {
                        "W kanale ogłoszeń pisze tylko moderator."
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(14.dp)
                        .navigationBarsPadding(),
                )
            } else if (thread != null) {
                state.replyTo?.let { reply ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (reply.mine) "Ty" else reply.authorName,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = EkotakGreen,
                            )
                            Text(
                                text = reply.preview,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = viewModel::cancelReply) {
                            Icon(Icons.Default.Close, contentDescription = "Anuluj cytat")
                        }
                    }
                }

                if (state.recording) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .navigationBarsPadding(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "● Nagrywam… ${chatDuration(state.recordedSeconds)}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = viewModel::cancelRecording) {
                            Icon(Icons.Default.Delete, contentDescription = "Odrzuć")
                        }
                        IconButton(onClick = viewModel::stopAndSendRecording) {
                            Icon(Icons.Default.Send, contentDescription = "Wyślij", tint = EkotakGreen)
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .navigationBarsPadding(),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Box {
                            IconButton(onClick = { attachOpen = true }) {
                                Icon(Icons.Default.Add, contentDescription = "Załącz")
                            }
                            DropdownMenu(
                                expanded = attachOpen,
                                onDismissRequest = { attachOpen = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Zdjęcie z aparatu") },
                                    onClick = { attachOpen = false; pickers.takePhoto() },
                                )
                                DropdownMenuItem(
                                    text = { Text("Plik z telefonu") },
                                    onClick = { attachOpen = false; pickers.pickFiles() },
                                )
                                DropdownMenuItem(
                                    text = { Text("Ankieta") },
                                    onClick = { attachOpen = false; viewModel.openPoll() },
                                )
                            }
                        }

                        MentionComposer(
                            state = mention,
                            members = thread.members.map { it.toTaskMember() },
                            sending = state.isSending,
                            placeholder = "Napisz wiadomość… (@ wywołuje osobę)",
                            onSend = {
                                viewModel.send(mention.text, mention.tokens)
                                mention.clear()
                            },
                            modifier = Modifier.weight(1f),
                        )

                        if (mention.text.isBlank()) {
                            IconButton(onClick = viewModel::startRecording) {
                                Icon(Icons.Default.Mic, contentDescription = "Nagraj głosówkę")
                            }
                        }
                    }
                }
            }
        }
    }

    if (state.pollOpen) {
        ChatPollDialog(
            onDismiss = viewModel::closePoll,
            onSubmit = { question, options, multi -> viewModel.sendPoll(question, options, multi) },
        )
    }

    state.receipts?.let { receipts ->
        AlertDialog(
            onDismissRequest = viewModel::hideReceipts,
            confirmButton = {
                TextButton(onClick = viewModel::hideReceipts) { Text("Zamknij") }
            },
            title = { Text("Kto przeczytał") },
            text = {
                Column {
                    Text("Przeczytali (${receipts.read.size})", fontWeight = FontWeight.SemiBold)
                    receipts.read.forEach { Text("• ${it.name}") }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Doręczone, nieprzeczytane (${receipts.pending.size})",
                        fontWeight = FontWeight.SemiBold,
                    )
                    receipts.pending.forEach { Text("• ${it.name}") }
                }
            },
        )
    }
}

/** Okno ankiety — pytanie i od dwóch do dwunastu odpowiedzi (limit WhatsAppa). */
@Composable
private fun ChatPollDialog(
    onDismiss: () -> Unit,
    onSubmit: (String, List<String>, Boolean) -> Unit,
) {
    var question by remember { mutableStateOf("") }
    var options by remember { mutableStateOf(listOf("", "")) }
    var multi by remember { mutableStateOf(false) }
    val ready = question.isNotBlank() && options.count { it.isNotBlank() } >= 2

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = ready,
                onClick = { onSubmit(question.trim(), options.map { it.trim() }.filter { it.isNotBlank() }, multi) },
            ) { Text("Wyślij") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Anuluj") } },
        title = { Text("Nowa ankieta") },
        text = {
            Column {
                OutlinedTextField(
                    value = question,
                    onValueChange = { question = it },
                    label = { Text("Pytanie") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                options.forEachIndexed { index, option ->
                    OutlinedTextField(
                        value = option,
                        onValueChange = { value ->
                            options = options.mapIndexed { i, old -> if (i == index) value else old }
                        },
                        label = { Text("Odpowiedź ${index + 1}") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    )
                }
                if (options.size < 12) {
                    TextButton(onClick = { options = options + "" }) { Text("+ Kolejna odpowiedź") }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked = multi, onCheckedChange = { multi = it })
                    Text("Można wskazać kilka odpowiedzi", style = MaterialTheme.typography.bodySmall)
                }
            }
        },
    )
}

/** Podpowiedzi „@" biorą ten sam kształt, co w kreatorze zadania. */
private fun ChatPerson.toTaskMember(): TaskMember = TaskMember(
    id = id,
    email = email,
    firstName = firstName ?: name.substringBefore(' ', name),
    lastName = lastName ?: name.substringAfter(' ', ""),
    role = null,
)

/** Treść z wybieraka zapisana do cache'u — repozytorium pracuje na plikach. */
private fun PickedFile.toCacheFile(cacheDir: File): File? = runCatching {
    val target = File(cacheDir, "chat_pick_${System.currentTimeMillis()}_${name.takeLast(40)}")
    target.writeBytes(bytes)
    target
}.getOrNull()

/** Intencja podglądu pliku systemową aplikacją. */
private fun File.viewIntent(context: android.content.Context, message: ChatMessage): Intent {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", this)
    return Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, message.attachment?.contentType ?: "*/*")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}

/** Magazyn załączników z grafu Hilta — ekran nie ma własnego wstrzykiwania. */
@EntryPoint
@InstallIn(SingletonComponent::class)
private interface ChatAttachmentEntryPoint {
    fun chatAttachmentStore(): ChatAttachmentStore
}

@Composable
private fun rememberChatAttachmentStore(): ChatAttachmentStore {
    val context = LocalContext.current
    return remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            ChatAttachmentEntryPoint::class.java,
        ).chatAttachmentStore()
    }
}
