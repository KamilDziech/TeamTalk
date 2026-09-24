package com.ekotak.teamtalk.domain.model

/**
 * Komunikator — czat zespołu na wzór WhatsAppa (decyzje usera z 2026-09-23).
 *
 * Jedna skrzynka mieści trzy rzeczy naraz: rozmowy dwóch osób, grupy i kanały
 * ogłoszeń (nowe byty w board360) ORAZ wątki komentarzy zadań, które zostają
 * tam, gdzie były. Stąd [ChatThread.id] jest zwykłym stringiem — wątek zadania
 * ma id z przedrostkiem `task:`.
 */

enum class ChatKind {
    DIRECT, GROUP, CHANNEL, TASK;

    companion object {
        fun from(raw: String): ChatKind = when (raw) {
            "direct" -> DIRECT
            "group" -> GROUP
            "channel" -> CHANNEL
            else -> TASK
        }
    }
}

/** Ptaszki: jeden = zapisana, dwa = doszła do wszystkich, dwa niebieskie =
 *  przeczytana. [PENDING] to stan WYŁĄCZNIE telefonu — wiadomość czeka
 *  w kolejce offline i serwer jeszcze o niej nie wie (zegarek zamiast ptaszka). */
enum class ChatDelivery {
    PENDING, SENT, DELIVERED, READ;

    companion object {
        fun from(raw: String?): ChatDelivery? = when (raw) {
            "sent" -> SENT
            "delivered" -> DELIVERED
            "read" -> READ
            else -> null
        }
    }
}

enum class ChatMessageKind {
    TEXT, IMAGE, FILE, VOICE, POLL, SYSTEM;

    companion object {
        fun from(raw: String): ChatMessageKind = when (raw) {
            "image" -> IMAGE
            "file" -> FILE
            "voice" -> VOICE
            "poll" -> POLL
            "system" -> SYSTEM
            else -> TEXT
        }
    }
}

data class ChatPerson(
    val id: String,
    val name: String,
    val email: String,
    val department: String,
    /** Rozbite imię i nazwisko — podpowiedzi „@" biorą ten sam kształt, co
     *  w kreatorze zadania ([TaskMember]), więc nie zgadujemy ich z `name`. */
    val firstName: String? = null,
    val lastName: String? = null,
    val role: String? = null,
    val lastReadAt: String? = null,
) {
    val isModerator: Boolean get() = role == "moderator"

    /** Inicjały do kółka awatara — imię i nazwisko, inaczej pierwsza litera. */
    val initials: String
        get() = name.split(' ', '·').filter { it.isNotBlank() }
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")
            .ifEmpty { "?" }
}

data class ChatAttachment(
    val name: String,
    val contentType: String,
    val size: Long,
    /** Ścieżka na serwerze (`/api/chat/attachments/<id>`) — telefon dokleja host. */
    val url: String,
) {
    val isImage: Boolean get() = contentType.startsWith("image/")

    val sizeLabel: String
        get() = when {
            size <= 0 -> ""
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${size / 1024} kB"
            else -> String.format(java.util.Locale("pl"), "%.1f MB", size / (1024.0 * 1024))
        }
}

data class ChatPoll(
    val question: String,
    val options: List<String>,
    val multi: Boolean,
    val counts: List<Int>,
    val myVotes: List<Int>,
    val totalVoters: Int,
) {
    fun countAt(index: Int): Int = counts.getOrElse(index) { 0 }

    /** Udział w najwyższym wyniku — po tym rysuje się długość paska. */
    fun shareAt(index: Int): Float {
        val max = counts.maxOrNull() ?: 0
        return if (max <= 0) 0f else countAt(index).toFloat() / max
    }
}

data class ChatLinkPreview(
    val url: String,
    val title: String,
    val description: String,
    val host: String,
)

data class ChatQuoted(
    val id: String,
    val authorName: String,
    val preview: String,
)

data class ChatOrigin(
    val taskId: String,
    val title: String,
)

data class ChatReaction(
    val emoji: String,
    val count: Int,
    val mine: Boolean,
)

data class ChatMessage(
    val id: String,
    val kind: ChatMessageKind,
    val body: String,
    val authorId: String,
    val authorName: String,
    val createdAt: String,
    val mine: Boolean,
    val delivery: ChatDelivery?,
    val replyTo: ChatQuoted?,
    val forwarded: Boolean,
    val attachment: ChatAttachment?,
    val durationSec: Int?,
    val waveform: List<Int>,
    val poll: ChatPoll?,
    val linkPreview: ChatLinkPreview?,
    val reactions: List<ChatReaction>,
    val starred: Boolean,
    /** Grupa klienta (od 2026-09-24): zadanie deala, pod którym padł komentarz;
     *  null = karta deala albo zwykły czat. */
    val origin: ChatOrigin? = null,
    /** Czeka w kolejce offline — dymek jest przygaszony, a zamiast ptaszka zegar. */
    val pending: Boolean = false,
) {
    /** Zajawka do listy rozmów i do cytatu: załącznik opisuje się typem. */
    val preview: String
        get() = body.ifBlank {
            when (kind) {
                ChatMessageKind.IMAGE -> "Zdjęcie"
                ChatMessageKind.VOICE -> "Wiadomość głosowa"
                ChatMessageKind.FILE -> attachment?.name ?: "Plik"
                ChatMessageKind.POLL -> poll?.question ?: "Ankieta"
                else -> "—"
            }
        }
}

data class ChatLastMessage(
    val id: String,
    val authorName: String,
    val preview: String,
    val createdAt: String,
    val mine: Boolean,
    val delivery: ChatDelivery?,
)

data class ChatThread(
    val id: String,
    val kind: ChatKind,
    val title: String,
    val subtitle: String,
    val initials: String,
    val lastMessage: ChatLastMessage?,
    val unreadCount: Int,
    val mentionedMe: Boolean,
    val pinned: Boolean,
    val muted: Boolean,
    val archived: Boolean,
    val draft: String?,
    val taskId: String?,
    val dealId: String?,
    val readOnly: Boolean,
    val memberCount: Int,
    /** Ile wiadomości z tego wątku czeka w kolejce offline. */
    val pendingCount: Int = 0,
)

data class ChatThreadDetail(
    val id: String,
    val kind: ChatKind,
    val title: String,
    val subtitle: String,
    val initials: String,
    val topic: String?,
    val taskId: String?,
    val dealId: String?,
    val readOnly: Boolean,
    val pinned: Boolean,
    val muted: Boolean,
    val archived: Boolean,
    val draft: String?,
    /** Podgląd zarządu (`chat.view_all`) — pole pisania jest wtedy schowane. */
    val observing: Boolean,
    val members: List<ChatPerson>,
    val messages: List<ChatMessage>,
    val pinnedMessage: ChatMessage?,
) {
    /** Czy podpisywać dymki nazwiskiem — w rozmowie dwóch osób to zbędne. */
    val showsAuthors: Boolean get() = kind != ChatKind.DIRECT
}

data class ChatSearchHit(
    val threadId: String,
    val threadTitle: String,
    val message: ChatMessage,
)

data class ChatReceipts(
    val read: List<ChatPerson>,
    val pending: List<ChatPerson>,
)

/** Wynik wysyłki — ten sam zestaw, co w innych modułach z kolejką. */
enum class ChatSendResult { SENT, QUEUED }
