package com.ekotak.teamtalk.presentation.chat

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ekotak.teamtalk.data.files.ChatAttachmentStore
import com.ekotak.teamtalk.domain.model.ChatMessage
import com.ekotak.teamtalk.domain.model.ChatMessageKind
import com.ekotak.teamtalk.presentation.theme.EkotakGreen

/** Szybkie reakcje — „✅" na początku, bo w robocie znaczy „przyjąłem". */
val QUICK_EMOJI = listOf("✅", "👍", "❤️", "😂", "😮", "🙏")

/**
 * Jeden dymek rozmowy.
 *
 * Dymek jest zielony po prawej (moje) i szary po lewej (cudze), a seria zdań
 * tej samej osoby zlewa się w blok — podpis i „ogonek" dostaje tylko pierwszy.
 * Długie przytrzymanie otwiera menu akcji; krótkie kliknięcie w załącznik
 * otwiera plik.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatBubble(
    message: ChatMessage,
    showAuthor: Boolean,
    firstOfSeries: Boolean,
    attachments: ChatAttachmentStore,
    canAct: Boolean,
    onReply: () -> Unit,
    onReact: (String) -> Unit,
    onStar: () -> Unit,
    onPin: () -> Unit,
    onForward: () -> Unit,
    onReceipts: () -> Unit,
    onVote: (Int) -> Unit,
    onOpenAttachment: () -> Unit,
    /** Grupa klienta: stuknięcie w plakietkę zadania otwiera jego kartę. */
    onOpenOrigin: (String) -> Unit = {},
) {
    if (message.kind == ChatMessageKind.SYSTEM) {
        ChatCenterChip(message.body)
        return
    }

    var menuOpen by remember { mutableStateOf(false) }
    var emojiOpen by remember { mutableStateOf(false) }

    val mine = message.mine
    val background = when {
        mine -> EkotakGreen.copy(alpha = if (message.pending) 0.14f else 0.24f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val shape = RoundedCornerShape(
        topStart = if (!mine && firstOfSeries) 2.dp else 12.dp,
        topEnd = if (mine && firstOfSeries) 2.dp else 12.dp,
        bottomStart = 12.dp,
        bottomEnd = 12.dp,
    )

    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        Box {
            Column(
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .background(background, shape)
                    .combinedClickable(
                        onClick = { if (message.attachment != null) onOpenAttachment() },
                        onLongClick = { if (canAct) menuOpen = true },
                    )
                    .padding(horizontal = 10.dp, vertical = 7.dp),
            ) {
                if (message.forwarded) {
                    Text(
                        text = "Przekazano dalej",
                        style = MaterialTheme.typography.labelSmall,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (showAuthor && !mine && firstOfSeries) {
                    Text(
                        text = message.authorName,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = EkotakGreen,
                    )
                }

                message.origin?.let { origin ->
                    Text(
                        text = "Zadanie: ${origin.title}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .padding(bottom = 2.dp)
                            .clickable { onOpenOrigin(origin.taskId) },
                    )
                }

                message.replyTo?.let { quote ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                                RoundedCornerShape(6.dp),
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = quote.authorName,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = EkotakGreen,
                        )
                        Text(
                            text = quote.preview,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                when (message.kind) {
                    ChatMessageKind.IMAGE -> ChatPhoto(message, attachments)
                    ChatMessageKind.FILE -> ChatFileRow(message)
                    ChatMessageKind.VOICE -> ChatVoiceRow(message)
                    ChatMessageKind.POLL -> ChatPollBlock(message, canAct, onVote)
                    else -> Unit
                }

                message.linkPreview?.let { preview ->
                    Column(
                        modifier = Modifier
                            .padding(vertical = 3.dp)
                            .background(
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                                RoundedCornerShape(6.dp),
                            )
                            .padding(8.dp),
                    ) {
                        Text(
                            text = preview.title,
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (preview.description.isNotBlank()) {
                            Text(
                                text = preview.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = preview.host,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                if (message.body.isNotBlank() && message.kind != ChatMessageKind.POLL) {
                    Text(
                        text = chatFormatted(message.body),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (message.starred) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "Oznaczona gwiazdką",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp),
                        )
                        Spacer(Modifier.width(3.dp))
                    }
                    Text(
                        text = chatTime(message.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (mine) {
                        Spacer(Modifier.width(4.dp))
                        ChatTicks(message.delivery)
                    }
                }

                if (message.reactions.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(top = 3.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        message.reactions.forEach { reaction ->
                            Box(
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.surface,
                                        RoundedCornerShape(10.dp),
                                    )
                                    .border(
                                        width = if (reaction.mine) 1.dp else 0.dp,
                                        color = if (reaction.mine) EkotakGreen else Color.Transparent,
                                        shape = RoundedCornerShape(10.dp),
                                    )
                                    .clickable(enabled = canAct) { onReact(reaction.emoji) }
                                    .padding(horizontal = 6.dp, vertical = 1.dp),
                            ) {
                                Text(
                                    text = "${reaction.emoji} ${reaction.count}",
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                        }
                    }
                }
            }

            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                if (emojiOpen) {
                    Row(modifier = Modifier.padding(horizontal = 8.dp)) {
                        QUICK_EMOJI.forEach { emoji ->
                            Text(
                                text = emoji,
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier
                                    .clickable {
                                        onReact(emoji)
                                        emojiOpen = false
                                        menuOpen = false
                                    }
                                    .padding(6.dp),
                            )
                        }
                    }
                } else {
                    DropdownMenuItem(
                        text = { Text("Reakcja") },
                        onClick = { emojiOpen = true },
                    )
                    DropdownMenuItem(
                        text = { Text("Odpowiedz") },
                        onClick = { onReply(); menuOpen = false },
                    )
                    DropdownMenuItem(
                        text = { Text(if (message.starred) "Zdejmij gwiazdkę" else "Oznacz gwiazdką") },
                        onClick = { onStar(); menuOpen = false },
                    )
                    DropdownMenuItem(
                        text = { Text("Przypnij w rozmowie") },
                        onClick = { onPin(); menuOpen = false },
                    )
                    DropdownMenuItem(
                        text = { Text("Przekaż dalej") },
                        onClick = { onForward(); menuOpen = false },
                    )
                    if (mine) {
                        DropdownMenuItem(
                            text = { Text("Kto przeczytał") },
                            onClick = { onReceipts(); menuOpen = false },
                        )
                    }
                }
            }
        }
    }
}

/** Zdjęcie w dymku — pobierane raz i trzymane w pamięci procesu. */
@Composable
private fun ChatPhoto(message: ChatMessage, attachments: ChatAttachmentStore) {
    val name = message.attachment?.name ?: return
    var bitmap by remember(message.id) { mutableStateOf(attachments.cached(message.id)) }

    LaunchedEffect(message.id) {
        if (bitmap == null && !message.pending) bitmap = attachments.image(message.id, name)
    }

    Box(
        modifier = Modifier
            .padding(bottom = 3.dp)
            .fillMaxWidth()
            .heightIn(min = 120.dp, max = 240.dp)
            .background(Color.White, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        val image: ImageBitmap? = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
            )
        } else {
            // Nic nie mruga: do czasu pobrania stoi ikona tej samej wielkości.
            Icon(
                Icons.AutoMirrored.Filled.InsertDriveFile,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(40.dp),
            )
        }
    }
}

@Composable
private fun ChatFileRow(message: ChatMessage) {
    val attachment = message.attachment ?: return
    Row(
        modifier = Modifier
            .padding(bottom = 3.dp)
            .background(
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                RoundedCornerShape(8.dp),
            )
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.AutoMirrored.Filled.InsertDriveFile,
            contentDescription = null,
            modifier = Modifier.size(28.dp),
        )
        Spacer(Modifier.width(8.dp))
        Column {
            Text(
                text = attachment.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = attachment.sizeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Głosówka — pasek zamiast fali, bo amplitud z gotowego m4a nie liczymy.
 *  Odtwarza ją systemowy odtwarzacz po kliknięciu w dymek. */
@Composable
private fun ChatVoiceRow(message: ChatMessage) {
    Row(
        modifier = Modifier.padding(bottom = 3.dp).width(220.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.PlayArrow, contentDescription = "Odtwórz", modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(8.dp))
        LinearProgressIndicator(
            progress = { 0f },
            modifier = Modifier.weight(1f).height(4.dp),
            color = EkotakGreen,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = chatDuration(message.durationSec ?: 0),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ChatPollBlock(message: ChatMessage, canAct: Boolean, onVote: (Int) -> Unit) {
    val poll = message.poll ?: return
    Column(modifier = Modifier.width(260.dp)) {
        Text(
            text = poll.question,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        poll.options.forEachIndexed { index, option ->
            val chosen = poll.myVotes.contains(index)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = canAct) { onVote(index) }
                    .padding(vertical = 3.dp),
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = (if (chosen) "◉ " else "○ ") + option,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (chosen) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = poll.countAt(index).toString(),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                LinearProgressIndicator(
                    progress = { poll.shareAt(index) },
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = EkotakGreen,
                )
            }
        }
        Text(
            text = buildString {
                append(if (poll.multi) "Można wskazać kilka" else "Jedna odpowiedź")
                append(" · ")
                append(poll.totalVoters)
                append(if (poll.totalVoters == 1) " głos" else " głosów")
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Składnia WhatsAppa w treści: *pogrubienie*, _kursywa_, ~przekreślenie~.
 * Panel renderuje to samo — telefon ma pokazywać tekst tak, jak go widać po
 * drugiej stronie, a nie z gwiazdkami.
 */
@Composable
private fun chatFormatted(body: String) = androidx.compose.ui.text.buildAnnotatedString {
    val pattern = Regex("""(\*[^*\n]+\*)|(_[^_\n]+_)|(~[^~\n]+~)""")
    var last = 0
    for (match in pattern.findAll(body)) {
        append(body.substring(last, match.range.first))
        val raw = match.value
        val inner = raw.substring(1, raw.length - 1)
        val style = when (raw.first()) {
            '*' -> androidx.compose.ui.text.SpanStyle(fontWeight = FontWeight.Bold)
            '_' -> androidx.compose.ui.text.SpanStyle(fontStyle = FontStyle.Italic)
            else -> androidx.compose.ui.text.SpanStyle(
                textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough,
            )
        }
        withStyle(style) { append(inner) }
        last = match.range.last + 1
    }
    if (last < body.length) append(body.substring(last))
}

