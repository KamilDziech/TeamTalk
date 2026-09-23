package com.ekotak.teamtalk.data.repository

import android.content.Context
import com.ekotak.teamtalk.data.local.dao.ChatDao
import com.ekotak.teamtalk.data.local.entity.ChatMessageEntity
import com.ekotak.teamtalk.data.local.entity.ChatMutationEntity
import com.ekotak.teamtalk.data.local.entity.ChatMutationEntity.Companion.KIND_ATTACHMENT
import com.ekotak.teamtalk.data.local.entity.ChatMutationEntity.Companion.KIND_POLL
import com.ekotak.teamtalk.data.local.entity.ChatMutationEntity.Companion.KIND_TEXT
import com.ekotak.teamtalk.data.local.entity.ChatPersonEntity
import com.ekotak.teamtalk.data.local.entity.ChatThreadEntity
import com.ekotak.teamtalk.data.mapper.toDomain
import com.ekotak.teamtalk.data.remote.api.TeamTalkApi
import com.ekotak.teamtalk.data.remote.dto.ChatForwardRequest
import com.ekotak.teamtalk.data.remote.dto.ChatMessageDto
import com.ekotak.teamtalk.data.remote.dto.ChatPersonDto
import com.ekotak.teamtalk.data.remote.dto.ChatPinMessageRequest
import com.ekotak.teamtalk.data.remote.dto.ChatPollRequest
import com.ekotak.teamtalk.data.remote.dto.ChatReactionRequest
import com.ekotak.teamtalk.data.remote.dto.ChatSettingsRequest
import com.ekotak.teamtalk.data.remote.dto.ChatThreadDetailDto
import com.ekotak.teamtalk.data.remote.dto.ChatThreadDto
import com.ekotak.teamtalk.data.remote.dto.ChatVoteRequest
import com.ekotak.teamtalk.data.remote.dto.CreateChatGroupRequest
import com.ekotak.teamtalk.data.remote.dto.OpenDirectRequest
import com.ekotak.teamtalk.data.remote.dto.SendChatMessageRequest
import com.ekotak.teamtalk.data.sync.ChatSyncScheduler
import com.ekotak.teamtalk.domain.model.ChatDelivery
import com.ekotak.teamtalk.domain.model.ChatKind
import com.ekotak.teamtalk.domain.model.ChatMessage
import com.ekotak.teamtalk.domain.model.ChatMessageKind
import com.ekotak.teamtalk.domain.model.ChatPerson
import com.ekotak.teamtalk.domain.model.ChatReceipts
import com.ekotak.teamtalk.domain.model.ChatSearchHit
import com.ekotak.teamtalk.domain.model.ChatSendResult
import com.ekotak.teamtalk.domain.model.ChatThread
import com.ekotak.teamtalk.domain.model.ChatThreadDetail
import com.ekotak.teamtalk.domain.model.CommsSyncResult
import com.ekotak.teamtalk.domain.repository.ChatRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Ciało zakolejkowanej wiadomości — to, czego potrzeba do ponowienia wysyłki. */
@kotlinx.serialization.Serializable
private data class QueuedMessage(
    val body: String = "",
    val mentions: List<String> = emptyList(),
    val replyToId: String? = null,
    val options: List<String> = emptyList(),
    val multi: Boolean = false,
    val durationSec: Int? = null,
    val waveform: List<Int> = emptyList(),
)

/**
 * Komunikator na telefonie: cache Room + kolejka offline.
 *
 * Schemat jest ten sam, co w zakładce „Komunikacja" karty deala i celowo prosty:
 *  1. próbujemy sieci i PODMIENIAMY cache odpowiedzią serwera,
 *  2. czytamy cache,
 *  3. doklejamy to, co czeka w kolejce, jako wiadomości `pending`.
 *
 * Kolejki NIE wpisujemy do cache'u jako faktu: cache trzyma to, co powiedział
 * serwer, więc jego odpowiedź nigdy nie kasuje wiadomości, o której serwer
 * jeszcze nie wie, a opróżnienie kolejki nie wymaga sprzątania duplikatów.
 *
 * Odmowa serwera (403, 404, 422) to NIE brak sieci — leci dalej do ekranu.
 * W kanale ogłoszeń zwykły członek dostanie 403 i ma to zobaczyć od razu,
 * zamiast oglądać wiadomość wiszącą w kolejce do skutku.
 */
@Singleton
class ChatRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: TeamTalkApi,
    private val dao: ChatDao,
    private val syncScheduler: ChatSyncScheduler,
    private val json: Json,
) : ChatRepository {

    // ── Skrzynka ─────────────────────────────────────────────────────────────

    override suspend fun listThreads(archived: Boolean): List<ChatThread> {
        try {
            val fresh = api.getChatThreads(if (archived) "1" else null)
            dao.replaceThreads(archived, fresh.map { it.toEntity(archived) })
        } catch (_: IOException) {
            // Bez zasięgu zostaje ostatni odczyt — pusta lista byłaby kłamstwem.
        } catch (_: Exception) {
            // 403/404 — skrzynka pokaże to, co zdążyliśmy pobrać wcześniej.
        }

        val queuedPerThread = dao.getMutations().groupingBy { it.threadId }.eachCount()
        return dao.getThreads(archived).mapNotNull { row ->
            row.payload.decode<ChatThreadDto>()?.toDomain(queuedPerThread[row.id] ?: 0)
        }
    }

    override suspend fun getThread(threadId: String): ChatThreadDetail {
        var fresh: ChatThreadDetailDto? = null
        try {
            fresh = api.getChatThread(threadId)
            dao.replaceMessages(threadId, fresh.messages.map { it.toEntity(threadId) })
        } catch (_: IOException) {
            // Wątek czytamy z cache'u — po to go trzymamy.
        }

        val queued = dao.getMutationsForThread(threadId).map { it.toPendingMessage() }
        if (fresh != null) return fresh.toDomain(queued)

        // Bez sieci składamy nagłówek z pozycji skrzynki, a treść z cache'u wątku.
        val summary = dao.getThread(threadId)?.payload?.decode<ChatThreadDto>()
        val cached = dao.getMessages(threadId).mapNotNull { it.payload.decode<ChatMessageDto>() }
        return ChatThreadDetail(
            id = threadId,
            kind = summary?.let { ChatKind.from(it.kind) } ?: ChatKind.DIRECT,
            title = summary?.title ?: "Rozmowa",
            subtitle = summary?.subtitle.orEmpty(),
            initials = summary?.initials ?: "?",
            topic = null,
            taskId = summary?.taskId,
            dealId = summary?.dealId,
            readOnly = summary?.readOnly ?: false,
            pinned = summary?.pinned ?: false,
            muted = summary?.muted ?: false,
            archived = summary?.archived ?: false,
            draft = summary?.draft?.takeIf { it.isNotBlank() },
            observing = false,
            members = emptyList(),
            messages = cached.map { it.toDomain() } + queued,
            pinnedMessage = null,
        )
    }

    override suspend fun unreadCount(): Int = api.getChatUnreadCount().count

    override suspend fun people(): List<ChatPerson> {
        try {
            val fresh = api.getChatPeople()
            dao.replacePeople(fresh.map { it.toEntity() })
        } catch (_: IOException) {
            // Katalog osób zmienia się rzadko — cache w zupełności wystarczy.
        }
        return dao.getPeople().mapNotNull { it.payload.decode<ChatPersonDto>()?.toDomain() }
    }

    // ── Zakładanie rozmów ────────────────────────────────────────────────────

    // Zakładanie rozmowy WYMAGA sieci i tak ma być: bez id nadanego przez serwer
    // nie ma dokąd kolejkować wiadomości, a dwie lokalnie założone rozmowy tych
    // samych osób zrobiłyby z listy bałagan nie do posprzątania.

    override suspend fun openDirect(userId: String): String =
        api.openChatDirect(OpenDirectRequest(userId)).id

    override suspend fun createGroup(
        channel: Boolean,
        title: String,
        memberIds: List<String>,
    ): String = api.createChatGroup(
        CreateChatGroupRequest(
            kind = if (channel) "channel" else "group",
            title = title,
            memberIds = memberIds,
        ),
    ).id

    override suspend fun leave(threadId: String) = api.leaveChatThread(threadId)

    // ── Wysyłka ──────────────────────────────────────────────────────────────

    override suspend fun sendText(
        threadId: String,
        body: String,
        mentions: List<String>,
        replyToId: String?,
    ): ChatSendResult = try {
        val saved = api.sendChatMessage(
            threadId,
            SendChatMessageRequest(body = body, mentions = mentions, replyToId = replyToId),
        )
        dao.upsertMessages(listOf(saved.toEntity(threadId)))
        ChatSendResult.SENT
    } catch (_: IOException) {
        enqueue(
            threadId = threadId,
            kind = KIND_TEXT,
            payload = QueuedMessage(body = body, mentions = mentions, replyToId = replyToId),
        )
        ChatSendResult.QUEUED
    }

    override suspend fun sendAttachment(
        threadId: String,
        file: File,
        fileName: String,
        mimeType: String,
        caption: String,
        replyToId: String?,
        durationSec: Int?,
        waveform: List<Int>,
    ): ChatSendResult = try {
        val saved = api.sendChatAttachment(
            id = threadId,
            file = file.toPart(fileName, mimeType),
            body = caption.takeIf { it.isNotBlank() }?.toPlainPart(),
            replyToId = replyToId?.toPlainPart(),
            durationSec = durationSec?.toString()?.toPlainPart(),
            waveform = waveform.takeIf { it.isNotEmpty() }?.joinToString(",")?.toPlainPart(),
        )
        dao.upsertMessages(listOf(saved.toEntity(threadId)))
        file.deleteIfCached()
        ChatSendResult.SENT
    } catch (_: IOException) {
        // Plik MUSI przeżyć w cache'u do czasu wysyłki — kopiujemy go pod własną
        // nazwę, bo oryginał (kadr z aparatu, plik z galerii) bywa sprzątany.
        val kept = file.keepForQueue(fileName)
        enqueue(
            threadId = threadId,
            kind = KIND_ATTACHMENT,
            payload = QueuedMessage(
                body = caption,
                replyToId = replyToId,
                durationSec = durationSec,
                waveform = waveform,
            ),
            filePath = kept.absolutePath,
            fileName = fileName,
            mimeType = mimeType,
        )
        ChatSendResult.QUEUED
    }

    override suspend fun sendPoll(
        threadId: String,
        question: String,
        options: List<String>,
        multi: Boolean,
    ): ChatSendResult = try {
        val saved = api.sendChatPoll(threadId, ChatPollRequest(question, options, multi))
        dao.upsertMessages(listOf(saved.toEntity(threadId)))
        ChatSendResult.SENT
    } catch (_: IOException) {
        enqueue(
            threadId = threadId,
            kind = KIND_POLL,
            payload = QueuedMessage(body = question, options = options, multi = multi),
        )
        ChatSendResult.QUEUED
    }

    override suspend fun forward(messageId: String, threadIds: List<String>) {
        api.forwardChatMessage(messageId, ChatForwardRequest(threadIds))
    }

    // ── Stan ─────────────────────────────────────────────────────────────────
    //
    // Wszystko niżej idzie „najlepszym staraniem": to informacje, nie decyzje.
    // Bez zasięgu przepadają bez śladu i nic złego się nie dzieje — kolejka
    // stanów musiałaby rozstrzygać konflikty z tym, co zrobił w tym czasie ktoś
    // inny, a nikt na to nie czeka.

    override suspend fun markRead(threadId: String) {
        runCatching { api.markChatRead(threadId) }
    }

    override suspend fun markUnread(threadId: String) {
        runCatching { api.markChatUnread(threadId) }
    }

    override suspend fun markDelivered() {
        runCatching { api.markChatDelivered() }
    }

    override suspend fun setPinned(threadId: String, pinned: Boolean) {
        api.patchChatSettings(threadId, ChatSettingsRequest(pinned = pinned))
    }

    override suspend fun setMuted(threadId: String, muted: Boolean) {
        // Wyciszamy na dobę — tyle, ile trwa dyżur albo dzień montażu.
        val until = if (muted) Instant.now().plusSeconds(24 * 3600).toString() else null
        api.patchChatSettings(threadId, ChatSettingsRequest(mutedUntil = until))
    }

    override suspend fun setArchived(threadId: String, archived: Boolean) {
        api.patchChatSettings(threadId, ChatSettingsRequest(archived = archived))
    }

    override suspend fun saveDraft(threadId: String, draft: String) {
        runCatching {
            api.patchChatSettings(threadId, ChatSettingsRequest(draft = draft.ifBlank { null }))
        }
    }

    override suspend fun pinMessage(threadId: String, messageId: String?) {
        api.pinChatMessage(threadId, ChatPinMessageRequest(messageId))
    }

    override suspend fun toggleReaction(messageId: String, emoji: String) {
        api.toggleChatReaction(messageId, ChatReactionRequest(emoji))
    }

    override suspend fun toggleStar(messageId: String) = api.toggleChatStar(messageId)

    override suspend fun vote(messageId: String, optionIndex: Int) {
        api.voteChatPoll(messageId, ChatVoteRequest(optionIndex))
    }

    override suspend fun receipts(messageId: String): ChatReceipts =
        api.getChatReceipts(messageId).toDomain()

    // ── Szukanie i załączniki ────────────────────────────────────────────────

    override suspend fun search(query: String): List<ChatSearchHit> =
        api.searchChat(query).map { it.toDomain() }

    override suspend fun starred(): List<ChatSearchHit> =
        api.getStarredChatMessages().map { it.toDomain() }

    /** Strumień prosto do pliku — zdjęcie z montażu nie musi przechodzić przez RAM. */
    override suspend fun downloadAttachment(messageId: String, fileName: String): File {
        val target = File(context.cacheDir, "chat_${messageId}_${fileName.sanitized()}")
        if (target.exists() && target.length() > 0) return target
        api.downloadChatAttachment(messageId).byteStream().use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        return target
    }

    // ── Kolejka ──────────────────────────────────────────────────────────────

    private suspend fun enqueue(
        threadId: String,
        kind: String,
        payload: QueuedMessage,
        filePath: String? = null,
        fileName: String? = null,
        mimeType: String? = null,
    ) {
        dao.upsertMutation(
            ChatMutationEntity(
                localId = ChatMutationEntity.newLocalId(),
                threadId = threadId,
                kind = kind,
                payload = json.encodeToString(QueuedMessage.serializer(), payload),
                filePath = filePath,
                fileName = fileName,
                mimeType = mimeType,
                createdAt = System.currentTimeMillis(),
            ),
        )
        syncScheduler.scheduleSync()
    }

    /**
     * Opróżnianie kolejki, od najstarszego wpisu — kolejność pisania jest
     * kolejnością wysyłki. Brak sieci przerywa przebieg (reszta poczeka na
     * kolejne obudzenie), ale ODMOWA serwera kasuje wpis: wiadomość odrzucona
     * przez API nie ma jak przejść za setnym razem, a wisząc w kolejce
     * blokowałaby wszystko, co za nią stoi.
     */
    override suspend fun syncPendingMutations(): CommsSyncResult {
        val pending = dao.getMutations()
        if (pending.isEmpty()) return CommsSyncResult.DONE

        for (row in pending) {
            val payload = row.payload.decode<QueuedMessage>() ?: QueuedMessage()
            try {
                when (row.kind) {
                    KIND_TEXT -> api.sendChatMessage(
                        row.threadId,
                        SendChatMessageRequest(
                            body = payload.body,
                            mentions = payload.mentions,
                            replyToId = payload.replyToId,
                        ),
                    )

                    KIND_POLL -> api.sendChatPoll(
                        row.threadId,
                        ChatPollRequest(payload.body, payload.options, payload.multi),
                    )

                    KIND_ATTACHMENT -> {
                        val file = row.filePath?.let(::File)
                        if (file == null || !file.exists()) {
                            // Plik zniknął z cache'u (czyszczenie pamięci) — nie ma
                            // czego wysłać, wpis tylko blokowałby kolejkę.
                            dao.deleteMutation(row.localId)
                            continue
                        }
                        api.sendChatAttachment(
                            id = row.threadId,
                            file = file.toPart(row.fileName ?: file.name, row.mimeType.orEmpty()),
                            body = payload.body.takeIf { it.isNotBlank() }?.toPlainPart(),
                            replyToId = payload.replyToId?.toPlainPart(),
                            durationSec = payload.durationSec?.toString()?.toPlainPart(),
                            waveform = payload.waveform.takeIf { it.isNotEmpty() }
                                ?.joinToString(",")?.toPlainPart(),
                        )
                        file.deleteIfCached()
                    }
                }
                dao.deleteMutation(row.localId)
            } catch (_: IOException) {
                return CommsSyncResult.RETRY
            } catch (_: Exception) {
                dao.deleteMutation(row.localId)
            }
        }
        return CommsSyncResult.DONE
    }

    override suspend fun pendingCount(): Int = dao.countMutations()

    // ── Pomocnicze ───────────────────────────────────────────────────────────

    private inline fun <reified T> String.decode(): T? =
        runCatching { json.decodeFromString<T>(this) }.getOrNull()

    private fun ChatThreadDto.toEntity(archived: Boolean) = ChatThreadEntity(
        id = id,
        payload = json.encodeToString(ChatThreadDto.serializer(), this),
        lastAt = lastMessage?.createdAt ?: "",
        pinned = pinned,
        archived = archived,
        syncedAt = System.currentTimeMillis(),
    )

    private fun ChatMessageDto.toEntity(threadId: String) = ChatMessageEntity(
        id = id,
        threadId = threadId,
        payload = json.encodeToString(ChatMessageDto.serializer(), this),
        createdAt = createdAt,
        syncedAt = System.currentTimeMillis(),
    )

    private fun ChatPersonDto.toEntity() = ChatPersonEntity(
        id = id,
        payload = json.encodeToString(ChatPersonDto.serializer(), this),
        name = name,
        department = department,
        syncedAt = System.currentTimeMillis(),
    )

    /** Wpis z kolejki jako dymek. Podpis wstawia serwer przy zapisie — zgadywanie
     *  nazwiska tutaj wpisałoby w dymek coś, czego w bazie nigdy nie będzie. */
    private fun ChatMutationEntity.toPendingMessage(): ChatMessage {
        val payload = payload.decode<QueuedMessage>() ?: QueuedMessage()
        return ChatMessage(
            id = localId,
            kind = when (kind) {
                KIND_POLL -> ChatMessageKind.POLL
                KIND_ATTACHMENT -> when {
                    payload.durationSec != null -> ChatMessageKind.VOICE
                    mimeType?.startsWith("image/") == true -> ChatMessageKind.IMAGE
                    else -> ChatMessageKind.FILE
                }
                else -> ChatMessageKind.TEXT
            },
            body = payload.body,
            authorId = "",
            authorName = "Ja",
            createdAt = Instant.ofEpochMilli(createdAt).toString(),
            mine = true,
            delivery = ChatDelivery.PENDING,
            replyTo = null,
            forwarded = false,
            attachment = null,
            durationSec = payload.durationSec,
            waveform = payload.waveform,
            poll = null,
            linkPreview = null,
            reactions = emptyList(),
            starred = false,
            pending = true,
        )
    }

    private fun File.toPart(name: String, mimeType: String): MultipartBody.Part =
        MultipartBody.Part.createFormData(
            "file",
            name,
            asRequestBody(mimeType.ifBlank { "application/octet-stream" }.toMediaTypeOrNull()),
        )

    private fun String.toPlainPart() = toRequestBody("text/plain".toMediaTypeOrNull())

    /** Kopia pliku pod własną nazwą w cache'u — oryginał bywa sprzątany. */
    private fun File.keepForQueue(fileName: String): File {
        val target = File(context.cacheDir, "chat_queue_${System.currentTimeMillis()}_${fileName.sanitized()}")
        return runCatching {
            copyTo(target, overwrite = true)
            target
        }.getOrDefault(this)
    }

    /** Sprząta tylko to, co sami odłożyliśmy — zdjęcia z galerii zostają. */
    private fun File.deleteIfCached() {
        if (name.startsWith("chat_queue_") || name.startsWith("voice_")) runCatching { delete() }
    }

    private fun String.sanitized(): String = replace(Regex("[^A-Za-z0-9._-]"), "_").takeLast(60)
}
