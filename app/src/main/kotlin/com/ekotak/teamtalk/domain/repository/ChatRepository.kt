package com.ekotak.teamtalk.domain.repository

import com.ekotak.teamtalk.domain.model.ChatPerson
import com.ekotak.teamtalk.domain.model.ChatReceipts
import com.ekotak.teamtalk.domain.model.ChatSearchHit
import com.ekotak.teamtalk.domain.model.ChatSendResult
import com.ekotak.teamtalk.domain.model.ChatThread
import com.ekotak.teamtalk.domain.model.ChatThreadDetail
import com.ekotak.teamtalk.domain.model.CommsSyncResult
import java.io.File

/**
 * Komunikator na telefonie.
 *
 * Odczyt ZAWSZE oddaje to, co jest — najpierw próbuje sieci i podmienia cache,
 * a bez zasięgu zwraca ostatni odczyt z Rooma. Wysyłka bez zasięgu ląduje
 * w kolejce i wraca jako [ChatSendResult.QUEUED]; ekran pokazuje ją wtedy jako
 * dymek z zegarkiem.
 *
 * Kolejkujemy WYŁĄCZNIE wiadomości. Reakcje, gwiazdki, głosy w ankiecie,
 * znaczniki przeczytania i porządek listy idą „najlepszym staraniem": bez
 * zasięgu przepadają bez śladu, bo to informacje, a nie decyzje, i kolejka
 * musiałaby rozstrzygać konflikty z tym, co w międzyczasie zrobił ktoś inny.
 */
interface ChatRepository {

    suspend fun listThreads(archived: Boolean = false): List<ChatThread>

    suspend fun getThread(threadId: String): ChatThreadDetail

    suspend fun unreadCount(): Int

    /** Katalog osób do nowej rozmowy — z cache'u, gdy nie ma zasięgu. */
    suspend fun people(): List<ChatPerson>

    // ── Zakładanie rozmów ────────────────────────────────────────────────────

    /** Zwraca id rozmowy z tą osobą (zakłada ją, jeśli jeszcze nie istnieje). */
    suspend fun openDirect(userId: String): String

    suspend fun createGroup(
        channel: Boolean,
        title: String,
        memberIds: List<String>,
    ): String

    suspend fun leave(threadId: String)

    // ── Wysyłka ──────────────────────────────────────────────────────────────

    suspend fun sendText(
        threadId: String,
        body: String,
        mentions: List<String> = emptyList(),
        replyToId: String? = null,
    ): ChatSendResult

    /** Zdjęcie, plik albo głosówka. [durationSec] i [waveform] tylko dla nagrań. */
    suspend fun sendAttachment(
        threadId: String,
        file: File,
        fileName: String,
        mimeType: String,
        caption: String = "",
        replyToId: String? = null,
        durationSec: Int? = null,
        waveform: List<Int> = emptyList(),
    ): ChatSendResult

    suspend fun sendPoll(
        threadId: String,
        question: String,
        options: List<String>,
        multi: Boolean,
    ): ChatSendResult

    suspend fun forward(messageId: String, threadIds: List<String>)

    // ── Stan ─────────────────────────────────────────────────────────────────

    suspend fun markRead(threadId: String)

    suspend fun markUnread(threadId: String)

    /** Meldunek „doszło" — z niego bierze się drugi ptaszek u nadawcy. */
    suspend fun markDelivered()

    suspend fun setPinned(threadId: String, pinned: Boolean)

    suspend fun setMuted(threadId: String, muted: Boolean)

    suspend fun setArchived(threadId: String, archived: Boolean)

    suspend fun saveDraft(threadId: String, draft: String)

    suspend fun pinMessage(threadId: String, messageId: String?)

    suspend fun toggleReaction(messageId: String, emoji: String)

    suspend fun toggleStar(messageId: String)

    suspend fun vote(messageId: String, optionIndex: Int)

    suspend fun receipts(messageId: String): ChatReceipts

    // ── Szukanie i załączniki ────────────────────────────────────────────────

    suspend fun search(query: String): List<ChatSearchHit>

    suspend fun starred(): List<ChatSearchHit>

    /** Pobiera załącznik do pliku w cache'u telefonu i oddaje go do otwarcia. */
    suspend fun downloadAttachment(messageId: String, fileName: String): File

    // ── Kolejka ──────────────────────────────────────────────────────────────

    /** Opróżnia kolejkę — woła to robotnik po powrocie zasięgu. */
    suspend fun syncPendingMutations(): CommsSyncResult

    /** Ile wiadomości czeka na wysłanie (plakietka w pasku skrzynki). */
    suspend fun pendingCount(): Int
}
