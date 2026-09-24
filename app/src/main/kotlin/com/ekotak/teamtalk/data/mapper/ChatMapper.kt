package com.ekotak.teamtalk.data.mapper

import com.ekotak.teamtalk.data.remote.dto.ChatAttachmentDto
import com.ekotak.teamtalk.data.remote.dto.ChatLinkPreviewDto
import com.ekotak.teamtalk.data.remote.dto.ChatMessageDto
import com.ekotak.teamtalk.data.remote.dto.ChatPersonDto
import com.ekotak.teamtalk.data.remote.dto.ChatPollDto
import com.ekotak.teamtalk.data.remote.dto.ChatQuotedDto
import com.ekotak.teamtalk.data.remote.dto.ChatReactionDto
import com.ekotak.teamtalk.data.remote.dto.ChatReceiptsDto
import com.ekotak.teamtalk.data.remote.dto.ChatSearchHitDto
import com.ekotak.teamtalk.data.remote.dto.ChatThreadDetailDto
import com.ekotak.teamtalk.data.remote.dto.ChatThreadDto
import com.ekotak.teamtalk.domain.model.ChatAttachment
import com.ekotak.teamtalk.domain.model.ChatDelivery
import com.ekotak.teamtalk.domain.model.ChatKind
import com.ekotak.teamtalk.domain.model.ChatLastMessage
import com.ekotak.teamtalk.domain.model.ChatLinkPreview
import com.ekotak.teamtalk.domain.model.ChatMessage
import com.ekotak.teamtalk.domain.model.ChatMessageKind
import com.ekotak.teamtalk.domain.model.ChatOrigin
import com.ekotak.teamtalk.domain.model.ChatPerson
import com.ekotak.teamtalk.domain.model.ChatPoll
import com.ekotak.teamtalk.domain.model.ChatQuoted
import com.ekotak.teamtalk.domain.model.ChatReaction
import com.ekotak.teamtalk.domain.model.ChatReceipts
import com.ekotak.teamtalk.domain.model.ChatSearchHit
import com.ekotak.teamtalk.domain.model.ChatThread
import com.ekotak.teamtalk.domain.model.ChatThreadDetail

/** Kontrakt Komunikatora (`/api/chat/…`) → modele domeny telefonu. */

fun ChatPersonDto.toDomain(): ChatPerson = ChatPerson(
    id = id,
    name = name,
    email = email,
    department = department,
    firstName = firstName,
    lastName = lastName,
    role = role,
    lastReadAt = lastReadAt,
)

fun ChatAttachmentDto.toDomain(): ChatAttachment = ChatAttachment(
    name = name,
    contentType = contentType,
    size = size,
    url = url,
)

fun ChatPollDto.toDomain(): ChatPoll = ChatPoll(
    question = question,
    options = options,
    multi = multi,
    counts = counts,
    myVotes = myVotes,
    totalVoters = totalVoters,
)

fun ChatLinkPreviewDto.toDomain(): ChatLinkPreview = ChatLinkPreview(
    url = url,
    title = title,
    description = description,
    host = host,
)

fun ChatQuotedDto.toDomain(): ChatQuoted = ChatQuoted(
    id = id,
    authorName = authorName,
    preview = preview,
)

fun ChatReactionDto.toDomain(): ChatReaction = ChatReaction(
    emoji = emoji,
    count = count,
    mine = mine,
)

fun ChatMessageDto.toDomain(): ChatMessage = ChatMessage(
    id = id,
    kind = ChatMessageKind.from(kind),
    body = body,
    authorId = authorId,
    authorName = authorName,
    createdAt = createdAt,
    mine = mine,
    delivery = ChatDelivery.from(delivery),
    replyTo = replyTo?.toDomain(),
    forwarded = forwarded,
    attachment = attachment?.toDomain(),
    durationSec = durationSec,
    waveform = waveform,
    poll = poll?.toDomain(),
    linkPreview = linkPreview?.toDomain(),
    reactions = reactions.map { it.toDomain() },
    starred = starred,
    origin = origin?.let { ChatOrigin(taskId = it.taskId, title = it.title.ifBlank { "Zadanie" }) },
)

fun ChatThreadDto.toDomain(pendingCount: Int = 0): ChatThread = ChatThread(
    id = id,
    kind = ChatKind.from(kind),
    title = title,
    subtitle = subtitle,
    initials = initials,
    lastMessage = lastMessage?.let {
        ChatLastMessage(
            id = it.id,
            authorName = it.authorName,
            preview = it.preview,
            createdAt = it.createdAt,
            mine = it.mine,
            delivery = ChatDelivery.from(it.delivery),
        )
    },
    unreadCount = unreadCount,
    mentionedMe = mentionedMe,
    pinned = pinned,
    muted = muted,
    archived = archived,
    draft = draft?.takeIf { it.isNotBlank() },
    taskId = taskId,
    dealId = dealId,
    readOnly = readOnly,
    memberCount = memberCount,
    pendingCount = pendingCount,
)

fun ChatThreadDetailDto.toDomain(queued: List<ChatMessage> = emptyList()): ChatThreadDetail =
    ChatThreadDetail(
        id = id,
        kind = ChatKind.from(kind),
        title = title,
        subtitle = subtitle,
        initials = initials,
        topic = topic,
        taskId = taskId,
        dealId = dealId,
        readOnly = readOnly,
        pinned = pinned,
        muted = muted,
        archived = archived,
        draft = draft?.takeIf { it.isNotBlank() },
        observing = observing,
        members = members.map { it.toDomain() },
        // Kolejka doklejana na koniec: cache trzyma to, co powiedział serwer,
        // więc jego odpowiedź nigdy nie kasuje wiadomości, o której nie wie.
        messages = messages.map { it.toDomain() } + queued,
        pinnedMessage = pinnedMessage?.toDomain(),
    )

fun ChatSearchHitDto.toDomain(): ChatSearchHit = ChatSearchHit(
    threadId = threadId,
    threadTitle = threadTitle,
    message = message.toDomain(),
)

fun ChatReceiptsDto.toDomain(): ChatReceipts = ChatReceipts(
    read = read.map { it.toDomain() },
    pending = pending.map { it.toDomain() },
)
