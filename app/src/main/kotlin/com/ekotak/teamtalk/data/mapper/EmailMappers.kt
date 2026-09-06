package com.ekotak.teamtalk.data.mapper

import com.ekotak.teamtalk.data.local.entity.EmailAccountEntity
import com.ekotak.teamtalk.data.local.entity.EmailAttachmentEntity
import com.ekotak.teamtalk.data.local.entity.EmailFolderCountEntity
import com.ekotak.teamtalk.data.local.entity.EmailLabelEntity
import com.ekotak.teamtalk.data.local.entity.EmailMessageEntity
import com.ekotak.teamtalk.data.local.entity.EmailThreadEntity
import com.ekotak.teamtalk.data.remote.dto.EmailAccountDto
import com.ekotak.teamtalk.data.remote.dto.EmailAttachmentDto
import com.ekotak.teamtalk.data.remote.dto.EmailFolderCountDto
import com.ekotak.teamtalk.data.remote.dto.EmailLabelDto
import com.ekotak.teamtalk.data.remote.dto.EmailMessageDto
import com.ekotak.teamtalk.data.remote.dto.EmailThreadDto
import com.ekotak.teamtalk.domain.model.EmailAttachment
import com.ekotak.teamtalk.domain.model.EmailFolder
import com.ekotak.teamtalk.domain.model.EmailFolderCount
import com.ekotak.teamtalk.domain.model.EmailLabel
import com.ekotak.teamtalk.domain.model.EmailMessage
import com.ekotak.teamtalk.domain.model.EmailThread
import com.ekotak.teamtalk.domain.model.Mailbox
import com.ekotak.teamtalk.domain.model.MailboxKind
import com.ekotak.teamtalk.domain.model.MailboxScope

/**
 * DTO ↔ encja ↔ model modułu Email.
 *
 * Dwie rzeczy warte uwagi:
 *  • Encja wątku niesie `scope` — ten sam wątek serwera bywa w cache dwa razy,
 *    raz dla widoku „Moje", raz dla „Wszystkie" (wycinek liczy serwer).
 *  • Etykiety zapisujemy jako tekst `id|nazwa|kolor` po średnikach. Osobna
 *    tabela łącząca dołożyłaby złączenie do listy, po której nigdy nie
 *    szukamy — kolorowe kropki tylko się rysują.
 */

// ── Skrzynki ─────────────────────────────────────────────────────────────────

fun EmailAccountDto.toEntity(position: Int, syncedAt: Long) = EmailAccountEntity(
    id = id,
    address = address,
    displayName = displayName,
    kind = kind,
    canViewAll = canViewAll,
    unread = unread,
    position = position,
    syncedAt = syncedAt,
)

fun EmailAccountEntity.toDomain() = Mailbox(
    id = id,
    address = address,
    displayName = displayName,
    kind = MailboxKind.fromWire(kind),
    canViewAll = canViewAll,
    unread = unread,
)

// ── Foldery i etykiety ───────────────────────────────────────────────────────

fun EmailFolderCountDto.toEntity(accountId: String, scope: MailboxScope, syncedAt: Long) =
    EmailFolderCountEntity(
        accountId = accountId,
        scope = scope.wire,
        folder = folder,
        total = total,
        unread = unread,
        syncedAt = syncedAt,
    )

fun EmailFolderCountEntity.toDomain() = EmailFolderCount(
    folder = EmailFolder.fromWire(folder),
    total = total,
    unread = unread,
)

fun EmailLabelDto.toEntity(syncedAt: Long) =
    EmailLabelEntity(id = id, name = name, color = color, syncedAt = syncedAt)

fun EmailLabelEntity.toDomain() = EmailLabel(id = id, name = name, color = color)

// ── Wątki ────────────────────────────────────────────────────────────────────

fun EmailThreadDto.toEntity(accountId: String, scope: MailboxScope, syncedAt: Long) =
    EmailThreadEntity(
        id = id,
        accountId = accountId,
        scope = scope.wire,
        subject = subject,
        folder = folder,
        lastAt = lastAt,
        unread = unread,
        starred = starred,
        dealId = dealId,
        // Lista wątków nie niesie etykiety deala — dopisze ją otwarcie wątku.
        dealLabel = null,
        fromName = fromName,
        fromAddr = fromAddr,
        snippet = snippet,
        messageCount = messageCount,
        hasAttachment = hasAttachment,
        labelsRaw = labels.encodeLabels(),
        syncedAt = syncedAt,
    )

fun EmailThreadEntity.toDomain(pendingSync: Boolean = false) = EmailThread(
    id = id,
    accountId = accountId,
    subject = subject,
    folder = EmailFolder.fromWire(folder),
    lastAt = lastAt,
    unread = unread,
    starred = starred,
    dealId = dealId,
    fromName = fromName,
    fromAddr = fromAddr,
    snippet = snippet,
    messageCount = messageCount,
    hasAttachment = hasAttachment,
    labels = labelsRaw.decodeLabels(),
    pendingSync = pendingSync,
)

private fun List<EmailLabelDto>.encodeLabels(): String =
    joinToString(";") { "${it.id}|${it.name}|${it.color}" }

private fun String.decodeLabels(): List<EmailLabel> =
    split(";").mapNotNull { entry ->
        val parts = entry.split("|")
        if (parts.size < 3) null else EmailLabel(parts[0], parts[1], parts[2])
    }

// ── Wiadomości i załączniki ──────────────────────────────────────────────────

fun EmailMessageDto.toEntity(syncedAt: Long) = EmailMessageEntity(
    id = id,
    threadId = threadId,
    outbound = direction == "outbound",
    fromAddr = fromAddr,
    fromName = fromName,
    toAddrs = toAddrs.joinToString(","),
    ccAddrs = ccAddrs.joinToString(","),
    subject = subject,
    bodyText = bodyText ?: bodyHtml?.stripHtml(),
    status = status,
    createdAt = createdAt,
    syncedAt = syncedAt,
)

fun EmailMessageEntity.toDomain(attachments: List<EmailAttachment>) = EmailMessage(
    id = id,
    threadId = threadId,
    outbound = outbound,
    fromAddr = fromAddr,
    fromName = fromName,
    toAddrs = toAddrs.splitAddrs(),
    ccAddrs = ccAddrs.splitAddrs(),
    subject = subject,
    bodyText = bodyText,
    status = status,
    createdAt = createdAt,
    attachments = attachments,
)

private fun String.splitAddrs(): List<String> =
    split(",").map { it.trim() }.filter { it.isNotEmpty() }

fun EmailAttachmentDto.toEntity(messageId: String) = EmailAttachmentEntity(
    id = id,
    messageId = messageId,
    filename = filename,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    localUri = null,
)

fun EmailAttachmentEntity.toDomain() = EmailAttachment(
    id = id,
    filename = filename,
    mimeType = mimeType,
    sizeBytes = sizeBytes,
    localUri = localUri,
)

/**
 * Awaryjne odchudzenie HTML-a, gdy wiadomość przyszła bez wersji tekstowej.
 * Telefon nie renderuje HTML-a poczty (to wektor na treści śledzące i pole do
 * popisu dla nadawcy), więc lepszy jest zgrubny tekst niż pusty ekran.
 */
private fun String.stripHtml(): String =
    replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("(?i)</p>"), "\n\n")
        .replace(Regex("<[^>]+>"), " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace(Regex("[ \\t]{2,}"), " ")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
