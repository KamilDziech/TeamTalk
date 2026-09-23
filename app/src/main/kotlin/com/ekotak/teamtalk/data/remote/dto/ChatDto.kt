package com.ekotak.teamtalk.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Komunikator (`/api/chat/…`) — czat zespołu na wzór WhatsAppa.
 *
 * Lustro `api/src/modules/chat/domain/chat-view.ts`. Rozmowa dwóch osób, grupa
 * i kanał ogłoszeń to nowe byty w bazie; wątki komentarzy zadań zostają na
 * `task_comments` i wpadają do tej samej listy pod id `task:<taskId>` — dlatego
 * `id` wątku jest zwykłym stringiem, a nie uuid-em.
 */

@Serializable
data class ChatPersonDto(
    val id: String,
    val name: String,
    val email: String = "",
    val firstName: String? = null,
    val lastName: String? = null,
    /** `biuro` | `serwis` | `montaz` | `pozostali` — po tym grupuje się picker. */
    val department: String = "pozostali",
    /** Tylko w składzie rozmowy: `member` albo `moderator`. */
    val role: String? = null,
    val lastReadAt: String? = null,
)

@Serializable
data class ChatAttachmentDto(
    val name: String,
    val contentType: String,
    val size: Long = 0,
    /** Ścieżka pod Caddym (`/api/chat/attachments/<id>`), nie pełny adres. */
    val url: String,
)

@Serializable
data class ChatPollDto(
    val question: String,
    val options: List<String> = emptyList(),
    val multi: Boolean = false,
    val counts: List<Int> = emptyList(),
    val myVotes: List<Int> = emptyList(),
    val totalVoters: Int = 0,
)

@Serializable
data class ChatLinkPreviewDto(
    val url: String,
    val title: String = "",
    val description: String = "",
    val host: String = "",
)

@Serializable
data class ChatQuotedDto(
    val id: String,
    val authorName: String,
    val preview: String,
)

@Serializable
data class ChatReactionDto(
    val emoji: String,
    val count: Int = 0,
    val mine: Boolean = false,
)

@Serializable
data class ChatMessageDto(
    val id: String,
    /** `text` | `image` | `file` | `voice` | `poll` | `system`. */
    val kind: String = "text",
    val body: String = "",
    val authorId: String = "",
    val authorName: String = "",
    val createdAt: String,
    val mine: Boolean = false,
    /** `sent` | `delivered` | `read`; null przy cudzych wiadomościach. */
    val delivery: String? = null,
    val replyTo: ChatQuotedDto? = null,
    val forwarded: Boolean = false,
    val attachment: ChatAttachmentDto? = null,
    val durationSec: Int? = null,
    val waveform: List<Int> = emptyList(),
    val poll: ChatPollDto? = null,
    val linkPreview: ChatLinkPreviewDto? = null,
    val mentionedIds: List<String> = emptyList(),
    val reactions: List<ChatReactionDto> = emptyList(),
    val starred: Boolean = false,
)

@Serializable
data class ChatLastMessageDto(
    val id: String,
    val authorName: String = "",
    val preview: String = "",
    val createdAt: String,
    val mine: Boolean = false,
    val delivery: String? = null,
)

@Serializable
data class ChatThreadDto(
    val id: String,
    /** `direct` | `group` | `channel` | `task`. */
    val kind: String,
    val title: String,
    val subtitle: String = "",
    val initials: String = "?",
    val lastMessage: ChatLastMessageDto? = null,
    val unreadCount: Int = 0,
    val mentionedMe: Boolean = false,
    val pinned: Boolean = false,
    val muted: Boolean = false,
    val archived: Boolean = false,
    val draft: String? = null,
    val taskId: String? = null,
    val dealId: String? = null,
    /** Kanał ogłoszeń, w którym czytający nie ma prawa pisać. */
    val readOnly: Boolean = false,
    val memberCount: Int = 0,
)

@Serializable
data class ChatThreadDetailDto(
    val id: String,
    val kind: String,
    val title: String,
    val subtitle: String = "",
    val initials: String = "?",
    val topic: String? = null,
    val taskId: String? = null,
    val dealId: String? = null,
    val readOnly: Boolean = false,
    val memberCount: Int = 0,
    val pinned: Boolean = false,
    val muted: Boolean = false,
    val archived: Boolean = false,
    val draft: String? = null,
    /** Podgląd zarządu — pole pisania jest wtedy schowane. */
    val observing: Boolean = false,
    val members: List<ChatPersonDto> = emptyList(),
    val messages: List<ChatMessageDto> = emptyList(),
    val pinnedMessage: ChatMessageDto? = null,
)

@Serializable
data class ChatSearchHitDto(
    val threadId: String,
    val threadTitle: String,
    val message: ChatMessageDto,
)

@Serializable
data class ChatReceiptsDto(
    val read: List<ChatPersonDto> = emptyList(),
    val pending: List<ChatPersonDto> = emptyList(),
)

// ── Żądania ─────────────────────────────────────────────────────────────────

@Serializable
data class SendChatMessageRequest(
    val body: String,
    val mentions: List<String> = emptyList(),
    val replyToId: String? = null,
)

@Serializable
data class OpenDirectRequest(val userId: String)

@Serializable
data class CreateChatGroupRequest(
    /** `group` albo `channel`. */
    val kind: String,
    val title: String,
    val topic: String? = null,
    val memberIds: List<String> = emptyList(),
)

@Serializable
data class ChatPollRequest(
    val question: String,
    val options: List<String>,
    val multi: Boolean = false,
)

@Serializable
data class ChatVoteRequest(val optionIndex: Int)

@Serializable
data class ChatReactionRequest(val emoji: String)

@Serializable
data class ChatForwardRequest(val conversationIds: List<String>)

@Serializable
data class ChatPinMessageRequest(val messageId: String?)

/** Porządek listy — wysyłamy tylko to, co się zmienia (reszta zostaje null). */
@Serializable
data class ChatSettingsRequest(
    val pinned: Boolean? = null,
    val mutedUntil: String? = null,
    val archived: Boolean? = null,
    val draft: String? = null,
)

@Serializable
data class ChatIdResponse(val id: String)
