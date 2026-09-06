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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.ReplyAll
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material.icons.filled.MarkEmailUnread
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ekotak.teamtalk.domain.model.EmailFolder
import com.ekotak.teamtalk.domain.model.EmailMessage
import com.ekotak.teamtalk.presentation.components.AppTopBar
import com.ekotak.teamtalk.presentation.theme.Orange600
import com.ekotak.teamtalk.presentation.theme.SyncBlue

/**
 * Otwarty wątek poczty.
 *
 * Wiadomości idą jedna pod drugą w całości, bez zwijania — na telefonie
 * rozwijanie każdej z osobna to dwa dotknięcia więcej za każdym razem, a wątki
 * w tej firmie mają po dwie–trzy wiadomości, nie po trzydzieści.
 *
 * Treść pokazujemy jako TEKST, nigdy jako HTML: renderowanie HTML-a z poczty
 * przychodzącej wpuszczałoby na ekran piksele śledzące i cudzy układ. Gdy
 * wiadomość przyszła sama w HTML-u, zamieniamy ją na tekst przy zapisie.
 */
@Composable
fun EmailThreadScreen(
    onNavigateBack: () -> Unit,
    onOpenDeal: (String) -> Unit,
    viewModel: EmailThreadViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message, state.error) {
        val text = state.error ?: state.message
        if (text != null) {
            snackbarHostState.showSnackbar(text)
            viewModel.consumeMessage()
        }
    }

    // Przeniesienie do kosza albo do archiwum zamyka ekran: wątku nie ma już
    // w folderze, z którego się tu weszło.
    LaunchedEffect(state.closed) {
        if (state.closed) onNavigateBack()
    }

    val detail = state.detail

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            AppTopBar(
                title = detail?.thread?.subject ?: "Wiadomość",
                onNavigateBack = onNavigateBack,
                actions = {
                    IconButton(onClick = viewModel::toggleStar) {
                        Icon(
                            imageVector = if (detail?.thread?.starred == true) Icons.Filled.Star
                            else Icons.Filled.StarBorder,
                            contentDescription = "Gwiazdka",
                        )
                    }
                    IconButton(onClick = viewModel::markUnread) {
                        Icon(
                            Icons.Filled.MarkEmailUnread,
                            contentDescription = "Oznacz jako nieprzeczytane",
                        )
                    }
                    if (detail?.thread?.folder != EmailFolder.ARCHIVE) {
                        IconButton(onClick = { viewModel.move(EmailFolder.ARCHIVE) }) {
                            Icon(Icons.Filled.Archive, contentDescription = "Archiwizuj")
                        }
                    }
                    IconButton(onClick = viewModel::trash) {
                        Icon(Icons.Filled.Delete, contentDescription = "Do kosza")
                    }
                },
            )
        },
    ) { padding ->
        when {
            state.isLoading && detail == null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            detail == null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Nie ma takiego wątku albo jest poza Twoją skrzynką.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                DealChip(
                    label = detail.dealLabel,
                    linked = detail.thread.dealId != null,
                    onPick = viewModel::openDealPicker,
                    onUnlink = { viewModel.linkDeal(null) },
                    onOpenDeal = { detail.thread.dealId?.let(onOpenDeal) },
                )

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(detail.messages, key = { it.id }) { message ->
                        MessageCard(message)
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TextButton(onClick = { viewModel.startReply(replyAll = false) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.Reply,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(" Odpowiedz")
                    }
                    TextButton(onClick = { viewModel.startReply(replyAll = true) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ReplyAll,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(" Wszystkim")
                    }
                    TextButton(onClick = viewModel::startForward) {
                        Icon(
                            Icons.Filled.Forward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(" Przekaż")
                    }
                }
            }
        }
    }

    val picker = state.dealPicker
    if (picker != null) {
        AlertDialog(
            onDismissRequest = viewModel::closeDealPicker,
            title = { Text("Powiąż z dealem") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = picker.query,
                        onValueChange = viewModel::searchDeals,
                        label = { Text("Nazwisko klienta") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (picker.loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    if (!picker.loading && picker.options.isEmpty()) {
                        Text(
                            text = "Brak deali do pokazania. Lista deali wymaga zasięgu.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Column(modifier = Modifier.heightIn(max = 260.dp)) {
                        picker.options.forEach { option ->
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { viewModel.linkDeal(option.dealId) }
                                    .padding(vertical = 10.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::closeDealPicker) { Text("Zamknij") }
            },
        )
    }

    val composer = state.composer
    if (composer != null) {
        EmailComposeSheet(
            title = composer.title,
            fromAddress = detail?.messages?.lastOrNull()?.let { message ->
                if (message.outbound) message.fromAddr else message.toAddrs.firstOrNull()
            }.orEmpty(),
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
 * Chip dowiązania do karty deala — jedyna rzecz, którą ta poczta ma ponad
 * Gmailem. Dotknięcie otwiera kartę, „✕" odpina wątek.
 */
@Composable
private fun DealChip(
    label: String?,
    linked: Boolean,
    onPick: () -> Unit,
    onUnlink: () -> Unit,
    onOpenDeal: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier
                .weight(1f)
                .clickable { if (linked) onOpenDeal() else onPick() },
        ) {
            Text(
                text = when {
                    label != null -> "🔗 $label"
                    linked -> "🔗 Powiązany deal"
                    else -> "🔗 Powiąż z dealem"
                },
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            )
        }
        if (linked) {
            TextButton(onClick = onUnlink) { Text("Odłącz") }
        }
    }
}

/** Pojedyncza wiadomość: nagłówek z awatarem, treść, załączniki. */
@Composable
private fun MessageCard(message: EmailMessage) {
    Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(avatarColor(message.fromAddr)),
            ) {
                Text(
                    text = emailInitials(message.fromName, message.fromAddr),
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = message.fromName ?: message.fromAddr,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "do: ${message.toAddrs.joinToString(", ").ifBlank { "—" }}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = formatMessageDate(message.createdAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Trzy stany wysyłki, które człowiek MUSI odróżnić: czeka na kredencje
        // skrzynki w board360, czeka na zasięg w telefonie, albo jest szkicem.
        when {
            message.status == EmailMessage.STATUS_QUEUED_LOCAL -> StatusLine(
                text = "W kolejce — poleci po powrocie zasięgu",
                color = SyncBlue,
            )
            message.awaitingSmtp -> StatusLine(
                text = "Oczekuje na wysyłkę (skrzynka bez kredencji SMTP)",
                color = Orange600,
            )
            message.isDraft -> StatusLine(text = "Wersja robocza", color = Orange600)
        }

        Text(
            text = message.bodyText.orEmpty().ifBlank { "(pusta treść)" },
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
        )

        message.attachments.forEach { attachment ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            ) {
                Text(
                    text = "📎 ${attachment.filename} · ${formatFileSize(attachment.sizeBytes)}" +
                        if (attachment.localUri != null) " · czeka na wysyłkę" else "",
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                )
            }
        }
    }
}

@Composable
private fun StatusLine(text: String, color: Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontSize = 11.sp,
        modifier = Modifier.padding(top = 4.dp),
    )
}
