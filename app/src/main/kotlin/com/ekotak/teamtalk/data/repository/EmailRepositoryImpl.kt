package com.ekotak.teamtalk.data.repository

import android.content.Context
import android.net.Uri
import com.ekotak.teamtalk.data.local.dao.EmailDao
import com.ekotak.teamtalk.data.local.entity.EmailAttachmentEntity
import com.ekotak.teamtalk.data.local.entity.EmailMessageEntity
import com.ekotak.teamtalk.data.local.entity.EmailMutationEntity
import com.ekotak.teamtalk.data.local.entity.EmailMutationEntity.Companion.KIND_DELETE
import com.ekotak.teamtalk.data.local.entity.EmailMutationEntity.Companion.KIND_DRAFT
import com.ekotak.teamtalk.data.local.entity.EmailMutationEntity.Companion.KIND_PATCH
import com.ekotak.teamtalk.data.local.entity.EmailMutationEntity.Companion.KIND_SEND
import com.ekotak.teamtalk.data.local.entity.EmailMutationEntity.Companion.LOCAL_ID_PREFIX
import com.ekotak.teamtalk.data.local.entity.EmailThreadEntity
import com.ekotak.teamtalk.data.mapper.toDomain
import com.ekotak.teamtalk.data.mapper.toEntity
import com.ekotak.teamtalk.data.remote.api.TeamTalkApi
import com.ekotak.teamtalk.data.remote.dto.EmailQueuedAttachmentDto
import com.ekotak.teamtalk.data.remote.dto.EmailQueuedSendDto
import com.ekotak.teamtalk.data.remote.dto.EmailSendDto
import com.ekotak.teamtalk.data.remote.dto.buildEmailThreadPatch
import com.ekotak.teamtalk.data.sync.EmailSyncScheduler
import com.ekotak.teamtalk.domain.model.EmailDealOption
import com.ekotak.teamtalk.domain.model.EmailDraft
import com.ekotak.teamtalk.domain.model.EmailFolder
import com.ekotak.teamtalk.domain.model.EmailMessage
import com.ekotak.teamtalk.domain.model.EmailSnapshot
import com.ekotak.teamtalk.domain.model.EmailThread
import com.ekotak.teamtalk.domain.model.EmailThreadDetail
import com.ekotak.teamtalk.domain.model.EmailThreadPatch
import com.ekotak.teamtalk.domain.model.MailboxScope
import com.ekotak.teamtalk.domain.repository.EmailRepository
import com.ekotak.teamtalk.domain.repository.EmailSyncRejection
import com.ekotak.teamtalk.domain.repository.EmailSyncResult
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException
import java.io.IOException
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Moduł Email — mobilny odpowiednik poczty z huba Komunikacja panelu.
 *
 * Źródłem prawdy dla ekranu jest Room: skrzynka otwiera się w kotłowni klienta
 * bez zasięgu, a sieć tylko dolewa świeże dane. Zapis idzie wprost do API, a bez
 * zasięgu — do kolejki i do cache, żeby zmiana była widoczna od razu (ustalenie
 * 2026-09-06, makieta `design/mockups/modul-email.html`).
 *
 * Rzecz, na której najłatwiej się tu przejechać: WYCINEK OPIEKUNA LICZY SERWER.
 * Ta sama skrzynka firmowa daje inną listę w widoku „Moje" i „Wszystkie", a
 * telefon nie ma z czego wycinka odtworzyć — nie zna cudzych deali ani adresów
 * z kartoteki. Dlatego `scope` wchodzi do KLUCZA cache: obie listy leżą osobno,
 * a przełączenie widoku bez zasięgu pokazuje dokładnie to, co pobrano wcześniej
 * w tym widoku, zamiast mieszać cudzą korespondencję do „Moich".
 *
 * Wysyłka bez zasięgu żyje pod lokalnym identyfikatorem (`local:…`) i ma
 * w kolejce wyłącznie [KIND_SEND]: dopóki serwer o niej nie wie, nie ma czego
 * łatać zmianami, więc wysyłka nie musi układać kolejności zdarzeń.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class EmailRepositoryImpl @Inject constructor(
    private val api: TeamTalkApi,
    private val dao: EmailDao,
    private val syncScheduler: EmailSyncScheduler,
    @ApplicationContext private val context: Context,
) : EmailRepository {

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val syncedAt = MutableStateFlow<Long?>(null)

    // ── Odczyt ────────────────────────────────────────────────────────────────

    override fun observe(
        accountId: String?,
        scope: MailboxScope,
        folder: EmailFolder,
    ): Flow<EmailSnapshot> = dao.observeAccounts().flatMapLatest { accounts ->
        val mailboxes = accounts.map { it.toDomain() }
        // Pierwsze wejście: skrzynki jeszcze nie ma, więc pokazujemy same
        // zakładki — ekran i tak zaraz woła `refresh` z konkretnym id.
        val id = accountId ?: accounts.firstOrNull()?.id
        if (id == null) {
            flowOf(EmailSnapshot(mailboxes = mailboxes))
        } else {
            combine(
                dao.observeFolders(id, scope.wire),
                dao.observeThreads(id, scope.wire, folder.wire),
                dao.observeLabels(),
                dao.observePendingIds(),
                syncedAt,
            ) { folders, threads, labels, pending, at ->
                val queued = pending.toSet()
                EmailSnapshot(
                    mailboxes = mailboxes,
                    folders = folders.map { it.toDomain() },
                    threads = threads.map { it.toDomain(pendingSync = it.id in queued) },
                    labels = labels.map { it.toDomain() },
                    syncedAt = at,
                )
            }
        }
    }

    override fun observeThread(threadId: String): Flow<EmailThreadDetail?> =
        combine(
            dao.observeThreadCopies(threadId),
            dao.observeMessages(threadId),
            dao.observeThreadAttachments(threadId),
            dao.observePendingIds(),
        ) { copies, messages, attachments, pending ->
            val head = copies.firstOrNull() ?: return@combine null
            val byMessage = attachments.groupBy { it.messageId }
            val thread = head.toDomain(pendingSync = head.id in pending.toSet())
            EmailThreadDetail(
                thread = thread,
                messages = messages.map { message ->
                    message.toDomain(byMessage[message.id].orEmpty().map { it.toDomain() })
                },
                labels = thread.labels,
                dealLabel = head.dealLabel,
            )
        }

    override suspend fun refresh(accountId: String?, scope: MailboxScope, folder: EmailFolder) {
        // Najpierw kolejka: gdyby odpowiedź serwera trafiła do cache przed
        // wysłaniem tego, co czeka, wiadomość napisana w busie zniknęłaby
        // z „Wysłanych" na oczach człowieka.
        runCatching { syncPendingMutations() }

        val now = System.currentTimeMillis()
        val accounts = api.getEmailAccounts()
        dao.replaceAccounts(accounts.mapIndexed { index, dto -> dto.toEntity(index, now) })

        val id = accountId ?: accounts.firstOrNull()?.id ?: return
        val account = accounts.firstOrNull { it.id == id }
        // Skrzynka personalna nie zna widoków — jest w całości moja. Pytanie
        // o `all` byłoby dla niej bez znaczenia, a w cache zrobiłoby drugą,
        // nigdy nieodwiedzaną kopię tych samych wątków.
        val effective = if (account?.kind == "personal") MailboxScope.MINE else scope

        val folders = api.getEmailFolders(id, effective.wire)
        dao.upsertFolders(folders.map { it.toEntity(id, effective, now) })

        val threads = api.getEmailThreads(id, effective.wire, folder.wire)
        dao.replaceFolderThreads(
            accountId = id,
            scope = effective.wire,
            folder = folder.wire,
            threads = threads.map { it.toEntity(id, effective, now) },
        )

        runCatching { api.getEmailLabels() }
            .onSuccess { labels -> dao.replaceLabels(labels.map { it.toEntity(now) }) }

        syncedAt.value = now
    }

    override suspend fun refreshThread(threadId: String) {
        // Wiadomości zakolejkowanej serwer jeszcze nie zna — nie ma po co pytać.
        if (threadId.startsWith(LOCAL_ID_PREFIX)) return
        val now = System.currentTimeMillis()
        val detail = api.getEmailThread(threadId)
        dao.replaceThreadMessages(
            threadId = threadId,
            messages = detail.messages.map { it.toEntity(now) },
            attachments = detail.messages.flatMap { message ->
                message.attachments.map { it.toEntity(message.id) }
            },
        )
        // Otwarcie wątku kasuje pogrubienie także w cache — inaczej po powrocie
        // na listę wiersz dalej wyglądałby na nieprzeczytany.
        dao.markRead(threadId)
        dao.setDealLink(threadId, detail.thread.dealId, detail.dealLabel)
    }

    override suspend fun search(
        accountId: String,
        scope: MailboxScope,
        folder: EmailFolder,
        query: String,
    ): List<EmailThread> {
        val now = System.currentTimeMillis()
        return api.getEmailThreads(accountId, scope.wire, folder.wire, query)
            .map { it.toEntity(accountId, scope, now).toDomain() }
    }

    // ── Zapis ─────────────────────────────────────────────────────────────────

    override suspend fun patchThread(threadId: String, patch: EmailThreadPatch) {
        if (patch.isEmpty) return
        val body = buildEmailThreadPatch(patch)
        applyLocally(threadId, patch)

        try {
            api.patchEmailThread(threadId, body)
            dao.deleteMutation(threadId, KIND_PATCH)
        } catch (_: IOException) {
            // Bez zasięgu scalamy z tym, co już czeka: liczy się OSTATNI stan
            // każdego pola, a nie droga, którą człowiek do niego doszedł.
            val queued = dao.getMutation(threadId, KIND_PATCH)?.let {
                runCatching { json.parseToJsonElement(it.payload).jsonObject }.getOrNull()
            }
            val merged = JsonObject((queued ?: JsonObject(emptyMap())) + body)
            dao.upsertMutation(
                EmailMutationEntity(
                    targetId = threadId,
                    kind = KIND_PATCH,
                    payload = merged.toString(),
                    createdAt = System.currentTimeMillis(),
                ),
            )
            syncScheduler.scheduleSync()
        }
    }

    override suspend fun deleteThread(threadId: String) {
        // Wątek istniejący tylko lokalnie znika razem ze swoją kolejką —
        // serwer nigdy się o nim nie dowie.
        if (threadId.startsWith(LOCAL_ID_PREFIX)) {
            dao.deleteMutationsFor(threadId)
            dao.deleteThread(threadId)
            return
        }
        dao.deleteThread(threadId)
        try {
            api.deleteEmailThread(threadId)
            dao.deleteMutationsFor(threadId)
        } catch (_: IOException) {
            dao.upsertMutation(
                EmailMutationEntity(
                    targetId = threadId,
                    kind = KIND_DELETE,
                    payload = "",
                    createdAt = System.currentTimeMillis(),
                ),
            )
            syncScheduler.scheduleSync()
        }
    }

    override suspend fun send(draft: EmailDraft) = dispatch(draft, asDraft = false)

    override suspend fun saveDraft(draft: EmailDraft) = dispatch(draft, asDraft = true)

    private suspend fun dispatch(draft: EmailDraft, asDraft: Boolean) {
        val body = draft.toSendDto()
        try {
            val message = if (asDraft) api.saveEmailDraft(body) else api.sendEmail(body)
            uploadAttachments(message.id, draft.attachments.map { it.toQueued() })
            draft.threadId?.let { runCatching { refreshThread(it) } }
        } catch (e: IOException) {
            queueOutgoing(draft, body, asDraft, e)
        }
    }

    /**
     * Wiadomość napisana bez zasięgu ląduje w cache od razu — w „Wysłanych"
     * albo w „Wersjach roboczych" — i dostaje wpis w kolejce. Bez tego człowiek
     * patrzyłby na pusty folder i pisał to samo drugi raz.
     */
    private suspend fun queueOutgoing(
        draft: EmailDraft,
        body: EmailSendDto,
        asDraft: Boolean,
        cause: IOException,
    ) {
        val now = System.currentTimeMillis()
        val localMessageId = LOCAL_ID_PREFIX + UUID.randomUUID()
        val account = dao.getAccount(draft.accountId) ?: throw cause
        val folder = if (asDraft) EmailFolder.DRAFTS else EmailFolder.SENT
        val subject = draft.subject.ifBlank { "(bez tematu)" }
        val createdAt = Instant.ofEpochMilli(now).toString()

        // Odpowiedź dopisuje się do istniejącego wątku; nowa wiadomość zakłada
        // wątek lokalny, który po wysyłce zostanie podmieniony serwerowym.
        val threadId = draft.threadId ?: (LOCAL_ID_PREFIX + UUID.randomUUID())
        if (draft.threadId == null) {
            // Wiersz wchodzi do OBU widoków skrzynki: własnej poczty nie
            // gubimy przez to, w którym trybie akurat była otwarta lista.
            val rows = listOf(MailboxScope.MINE, MailboxScope.ALL).map { scope ->
                EmailThreadEntity(
                    id = threadId,
                    accountId = account.id,
                    scope = scope.wire,
                    subject = subject,
                    folder = folder.wire,
                    lastAt = createdAt,
                    unread = false,
                    starred = false,
                    dealId = draft.dealId,
                    dealLabel = null,
                    fromName = account.displayName,
                    fromAddr = draft.to.firstOrNull() ?: account.address,
                    snippet = draft.body.replace(Regex("\\s+"), " ").take(140),
                    messageCount = 1,
                    hasAttachment = draft.attachments.isNotEmpty(),
                    labelsRaw = "",
                    syncedAt = now,
                )
            }
            dao.upsertThreads(rows)
        }

        dao.upsertMessages(
            listOf(
                EmailMessageEntity(
                    id = localMessageId,
                    threadId = threadId,
                    outbound = true,
                    fromAddr = account.address,
                    fromName = account.displayName,
                    toAddrs = draft.to.joinToString(","),
                    ccAddrs = draft.cc.joinToString(","),
                    subject = subject,
                    bodyText = draft.body,
                    status = if (asDraft) EmailMessage.STATUS_DRAFT
                    else EmailMessage.STATUS_QUEUED_LOCAL,
                    createdAt = createdAt,
                    syncedAt = now,
                ),
            ),
        )
        dao.upsertAttachments(
            draft.attachments.map { file ->
                EmailAttachmentEntity(
                    id = LOCAL_ID_PREFIX + UUID.randomUUID(),
                    messageId = localMessageId,
                    filename = file.filename,
                    mimeType = file.mimeType,
                    sizeBytes = file.sizeBytes,
                    localUri = file.uri,
                )
            },
        )

        val queued = EmailQueuedSendDto(
            send = body,
            localMessageId = localMessageId,
            localThreadId = if (draft.threadId == null) threadId else null,
            attachments = draft.attachments.map { it.toQueued() },
        )
        dao.upsertMutation(
            EmailMutationEntity(
                targetId = localMessageId,
                kind = if (asDraft) KIND_DRAFT else KIND_SEND,
                payload = json.encodeToString(EmailQueuedSendDto.serializer(), queued),
                createdAt = now,
            ),
        )
        syncScheduler.scheduleSync()
    }

    override suspend fun dealOptions(query: String?): List<EmailDealOption> =
        api.getEmailDealOptions(query?.takeIf { it.isNotBlank() })
            .map { EmailDealOption(dealId = it.dealId, label = it.label) }

    // ── Kolejka ───────────────────────────────────────────────────────────────

    /**
     * Opróżnia kolejkę w kolejności zapisu.
     *
     * Brak zasięgu przerywa przebieg — reszta poczeka na kolejne obudzenie.
     * Odmowa serwera (4xx) zdejmuje wpis, bo wożenie go w kółko niczego nie
     * naprawi, ale wraca w [EmailSyncResult.rejected]: człowiek widział
     * wiadomość jako wysłaną i musi się dowiedzieć, że jednak nie poszła.
     */
    override suspend fun syncPendingMutations(): EmailSyncResult {
        var sent = 0
        val rejected = mutableListOf<EmailSyncRejection>()
        for (mutation in dao.getMutations()) {
            try {
                when (mutation.kind) {
                    KIND_PATCH -> {
                        val body = json.parseToJsonElement(mutation.payload).jsonObject
                        api.patchEmailThread(mutation.targetId, body)
                        dao.deleteMutation(mutation.targetId, KIND_PATCH)
                    }
                    KIND_DELETE -> {
                        api.deleteEmailThread(mutation.targetId)
                        dao.deleteMutation(mutation.targetId, KIND_DELETE)
                    }
                    KIND_SEND, KIND_DRAFT -> {
                        val queued = json.decodeFromString(
                            EmailQueuedSendDto.serializer(),
                            mutation.payload,
                        )
                        val message = if (mutation.kind == KIND_DRAFT) {
                            api.saveEmailDraft(queued.send)
                        } else {
                            api.sendEmail(queued.send)
                        }
                        val failedFiles = uploadAttachments(message.id, queued.attachments)
                        dao.deleteMutation(mutation.targetId, mutation.kind)
                        // Lokalne ślady znikają: świeży stan przyjdzie przy
                        // najbliższym odświeżeniu listy, już z serwera.
                        dao.deleteAttachmentsOf(queued.localMessageId)
                        dao.deleteMessage(queued.localMessageId)
                        queued.localThreadId?.let { dao.deleteThread(it) }
                        if (failedFiles.isNotEmpty()) {
                            rejected += EmailSyncRejection(
                                label = "Wiadomość ${queued.send.subject} wysłana bez załączników",
                                reason = failedFiles.joinToString(", ") +
                                    " — pliku nie ma już na telefonie.",
                            )
                        }
                    }
                    else -> dao.deleteMutation(mutation.targetId, mutation.kind)
                }
                sent++
            } catch (_: IOException) {
                // Nadal bez zasięgu — reszta kolejki poczeka na następny raz.
                return EmailSyncResult(sent = sent, rejected = rejected, incomplete = true)
            } catch (e: HttpException) {
                rejected += EmailSyncRejection(
                    label = rejectionLabel(mutation),
                    reason = e.serverMessage(),
                )
                if (mutation.kind == KIND_SEND || mutation.kind == KIND_DRAFT) {
                    // Wiadomość odrzucona przy wysyłce: kasujemy lokalny wiersz,
                    // żeby folder nie pokazywał pisma, którego serwer nie przyjął.
                    val queued = runCatching {
                        json.decodeFromString(EmailQueuedSendDto.serializer(), mutation.payload)
                    }.getOrNull()
                    queued?.let {
                        dao.deleteAttachmentsOf(it.localMessageId)
                        dao.deleteMessage(it.localMessageId)
                        it.localThreadId?.let { threadId -> dao.deleteThread(threadId) }
                    }
                }
                dao.deleteMutation(mutation.targetId, mutation.kind)
            }
        }
        return EmailSyncResult(sent = sent, rejected = rejected)
    }

    // ── Pomocnicze ────────────────────────────────────────────────────────────

    /**
     * Wgrywa załączniki wiadomości i zwraca nazwy tych, których nie udało się
     * odczytać. Plik trzymamy jako `content://`, więc skasowanie go z telefonu
     * przed powrotem zasięgu jest realnym przypadkiem — i musi być widoczne,
     * a nie cicho pominięte.
     */
    private suspend fun uploadAttachments(
        messageId: String,
        files: List<EmailQueuedAttachmentDto>,
    ): List<String> {
        val failed = mutableListOf<String>()
        for (file in files) {
            val bytes = runCatching {
                context.contentResolver.openInputStream(Uri.parse(file.uri))?.use { it.readBytes() }
            }.getOrNull()
            if (bytes == null) {
                failed += file.filename
                continue
            }
            val part = MultipartBody.Part.createFormData(
                "file",
                file.filename,
                bytes.toRequestBody(file.mimeType.toMediaTypeOrNull()),
            )
            api.uploadEmailAttachment(messageId, part)
        }
        return failed
    }

    /** Zmiana widoczna od razu, w obu kopiach wątku (widok „Moje" i „Wszystkie"). */
    private suspend fun applyLocally(threadId: String, patch: EmailThreadPatch) {
        val copies = dao.getThreadCopies(threadId)
        if (copies.isEmpty()) return
        dao.upsertThreads(
            copies.map { row ->
                row.copy(
                    starred = patch.starred ?: row.starred,
                    unread = patch.unread ?: row.unread,
                    folder = patch.folder?.wire ?: row.folder,
                    dealId = patch.dealId?.value ?: row.dealId,
                    // Odpięcie od deala kasuje też etykietę — inaczej chip
                    // pokazywałby nazwisko klienta, z którym nic już nie łączy.
                    dealLabel = if (patch.dealId != null && patch.dealId.value == null) {
                        null
                    } else {
                        row.dealLabel
                    },
                )
            },
        )
    }

    private fun EmailDraft.toSendDto() = EmailSendDto(
        accountId = accountId,
        threadId = threadId,
        to = to,
        cc = cc,
        subject = subject.ifBlank { "(bez tematu)" },
        bodyText = body,
        dealId = dealId,
    )

    private fun com.ekotak.teamtalk.domain.model.EmailDraftAttachment.toQueued() =
        EmailQueuedAttachmentDto(
            uri = uri,
            filename = filename,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
        )

    /** Opis odrzuconego zapisu — tyle, żeby człowiek poznał, o co chodziło. */
    private fun rejectionLabel(mutation: EmailMutationEntity): String = when (mutation.kind) {
        KIND_SEND -> "Wiadomość nie została wysłana"
        KIND_DRAFT -> "Wersja robocza nie została zapisana"
        KIND_DELETE -> "Nie udało się usunąć wątku"
        else -> "Nie udało się zapisać zmiany wątku"
    }

    /** Komunikat serwera z ciała błędu; bez niego zostaje sam kod odpowiedzi. */
    private fun HttpException.serverMessage(): String? {
        val raw = runCatching { response()?.errorBody()?.string() }.getOrNull() ?: return null
        return runCatching {
            json.parseToJsonElement(raw).jsonObject["message"]?.jsonPrimitive?.contentOrNull
        }.getOrNull()
    }
}
