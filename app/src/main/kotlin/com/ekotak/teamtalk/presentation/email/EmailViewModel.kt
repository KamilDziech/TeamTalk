package com.ekotak.teamtalk.presentation.email

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.domain.model.Edit
import com.ekotak.teamtalk.domain.model.EmailDraft
import com.ekotak.teamtalk.domain.model.EmailDraftAttachment
import com.ekotak.teamtalk.domain.model.EmailFolder
import com.ekotak.teamtalk.domain.model.EmailFolderCount
import com.ekotak.teamtalk.domain.model.EmailLabel
import com.ekotak.teamtalk.domain.model.EmailThread
import com.ekotak.teamtalk.domain.model.EmailThreadPatch
import com.ekotak.teamtalk.domain.model.Mailbox
import com.ekotak.teamtalk.domain.model.MailboxKind
import com.ekotak.teamtalk.domain.model.MailboxScope
import com.ekotak.teamtalk.domain.repository.EmailRepository
import com.ekotak.teamtalk.presentation.crm.crmErrorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Moduł Email — lista wątków w układzie Gmaila.
 *
 * Ekran żyje z jednej migawki Room, więc zakładki skrzynek, liczniki folderów
 * i lista wątków zmieniają się razem. Trzy rzeczy sterują tym, co widać, i
 * wszystkie trzy są w stanie: SKRZYNKA (firmowa / personalna), WIDOK („Moje" =
 * wycinek opiekuna albo „Wszystkie") i FOLDER.
 *
 * Przełączenie skrzynki zeruje widok do „Moje". Nie jest to kosmetyka:
 * „Wszystkie" istnieje tylko dla skrzynki firmowej i tylko z uprawnieniem
 * `email.view_all`, a zapytanie o `scope=all` do skrzynki personalnej byłoby
 * pytaniem bez sensu.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class EmailViewModel @Inject constructor(
    private val repository: EmailRepository,
) : ViewModel() {

    /** Okno pisania — jedno na nową wiadomość, odpowiedź i przekazanie. */
    data class Composer(
        val threadId: String? = null,
        val to: String = "",
        val cc: String = "",
        val subject: String = "",
        val body: String = "",
        val attachments: List<EmailDraftAttachment> = emptyList(),
        val sending: Boolean = false,
        /** Nagłówek okna — „Nowa wiadomość" / „Odpowiedź" / „Przekaż dalej". */
        val title: String = "Nowa wiadomość",
    )

    data class UiState(
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val error: String? = null,
        val message: String? = null,
        val mailboxes: List<Mailbox> = emptyList(),
        val accountId: String? = null,
        val scope: MailboxScope = MailboxScope.MINE,
        val folder: EmailFolder = EmailFolder.INBOX,
        val folders: List<EmailFolderCount> = emptyList(),
        val threads: List<EmailThread> = emptyList(),
        val labels: List<EmailLabel> = emptyList(),
        /** Puste = lista z cache; niepuste = wyniki szukania (tylko po sieci). */
        val query: String = "",
        val searching: Boolean = false,
        val searchResults: List<EmailThread>? = null,
        val composer: Composer? = null,
        val syncedAt: Long? = null,
    ) {
        val mailbox: Mailbox? get() = mailboxes.firstOrNull { it.id == accountId }

        /** Wiersze do narysowania: wyniki szukania mają pierwszeństwo. */
        val visibleThreads: List<EmailThread> get() = searchResults ?: threads

        fun unreadIn(folder: EmailFolder): Int =
            folders.firstOrNull { it.folder == folder }?.unread ?: 0

        fun totalIn(folder: EmailFolder): Int =
            folders.firstOrNull { it.folder == folder }?.total ?: 0

        /** Przełącznik „Moje / Wszystkie" — tylko firmowa skrzynka z prawem. */
        val canSwitchScope: Boolean get() = mailbox?.canViewAll == true

        /**
         * Pusty wycinek u kogoś, kto ma pełny wgląd, to nie jest awaria — to
         * znak, że w tym folderze po prostu nie ma JEGO korespondencji. Wtedy
         * ekran proponuje przejście na „Wszystkie" zamiast pustej strony.
         */
        val emptyBecauseOfScope: Boolean
            get() = visibleThreads.isEmpty() && query.isBlank() &&
                scope == MailboxScope.MINE && mailbox?.kind == MailboxKind.SHARED
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** Strumień z Room dla bieżącej trójki (skrzynka, widok, folder). */
    private var observeJob: Job? = null
    private var searchJob: Job? = null

    init {
        observe()
        refresh()
    }

    private fun observe() {
        observeJob?.cancel()
        val state = _uiState.value
        observeJob = viewModelScope.launch {
            repository.observe(state.accountId, state.scope, state.folder).collect { snapshot ->
                _uiState.update { current ->
                    current.copy(
                        isLoading = false,
                        mailboxes = snapshot.mailboxes,
                        // Pierwsze wejście: skrzynka firmowa jest pierwsza na liście.
                        accountId = current.accountId ?: snapshot.mailboxes.firstOrNull()?.id,
                        folders = snapshot.folders,
                        threads = snapshot.threads,
                        labels = snapshot.labels,
                        syncedAt = snapshot.syncedAt,
                    )
                }
            }
        }
    }

    fun refresh() {
        val state = _uiState.value
        _uiState.update { it.copy(isRefreshing = true, error = null) }
        viewModelScope.launch {
            runCatching { repository.refresh(state.accountId, state.scope, state.folder) }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = crmErrorMessage(e, "Nie udało się pobrać poczty"),
                        )
                    }
                }
            _uiState.update { it.copy(isRefreshing = false, isLoading = false) }
        }
    }

    fun selectMailbox(accountId: String) {
        if (_uiState.value.accountId == accountId) return
        _uiState.update {
            it.copy(
                accountId = accountId,
                // Widok „Wszystkie" należy do skrzynki firmowej — przy zmianie
                // zakładki wracamy do wycinka, żeby nie pytać o coś, czego dla
                // nowej skrzynki nie ma.
                scope = MailboxScope.MINE,
                folder = EmailFolder.INBOX,
                threads = emptyList(),
                query = "",
                searchResults = null,
                isLoading = true,
            )
        }
        observe()
        refresh()
    }

    fun selectScope(scope: MailboxScope) {
        if (_uiState.value.scope == scope) return
        _uiState.update {
            it.copy(
                scope = scope,
                folder = EmailFolder.INBOX,
                threads = emptyList(),
                query = "",
                searchResults = null,
                isLoading = true,
            )
        }
        observe()
        refresh()
    }

    fun selectFolder(folder: EmailFolder) {
        if (_uiState.value.folder == folder) return
        _uiState.update {
            it.copy(
                folder = folder,
                threads = emptyList(),
                query = "",
                searchResults = null,
                isLoading = true,
            )
        }
        observe()
        refresh()
    }

    // ── Szukanie ──────────────────────────────────────────────────────────────
    // Szukamy WYŁĄCZNIE po sieci i nie zapisujemy wyników do cache. Serwer
    // przeszukuje też treść wiadomości, których telefon nigdy nie pobrał, więc
    // lokalne szukanie po samym cache dawałoby wyniki wyglądające na kompletne,
    // a nie będące kompletnymi — najgorszy możliwy wariant.

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query) }
        if (query.isBlank()) clearSearch()
    }

    fun runSearch() {
        val state = _uiState.value
        val accountId = state.accountId ?: return
        val query = state.query.trim()
        if (query.isBlank()) return clearSearch()
        searchJob?.cancel()
        _uiState.update { it.copy(searching = true, error = null) }
        searchJob = viewModelScope.launch {
            runCatching { repository.search(accountId, state.scope, state.folder, query) }
                .onSuccess { results ->
                    _uiState.update { it.copy(searching = false, searchResults = results) }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(
                            searching = false,
                            error = crmErrorMessage(e, "Szukanie wymaga zasięgu"),
                        )
                    }
                }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _uiState.update { it.copy(query = "", searchResults = null, searching = false) }
    }

    // ── Zmiany wątku ──────────────────────────────────────────────────────────

    fun toggleStar(thread: EmailThread) =
        patch(thread.id, EmailThreadPatch(starred = !thread.starred))

    fun toggleRead(thread: EmailThread) =
        patch(thread.id, EmailThreadPatch(unread = !thread.unread))

    fun move(thread: EmailThread, target: EmailFolder) {
        patch(thread.id, EmailThreadPatch(folder = target))
        flash("Przeniesiono do: ${target.label}.")
    }

    fun trash(thread: EmailThread) {
        viewModelScope.launch {
            runCatching { repository.deleteThread(thread.id) }
                .onSuccess {
                    flash(
                        if (thread.folder == EmailFolder.TRASH) "Usunięto trwale."
                        else "Przeniesiono do kosza.",
                    )
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(error = crmErrorMessage(e, "Nie udało się usunąć wątku"))
                    }
                }
        }
    }

    fun linkDeal(threadId: String, dealId: String?) =
        patch(threadId, EmailThreadPatch(dealId = Edit(dealId)))

    private fun patch(threadId: String, patch: EmailThreadPatch) {
        viewModelScope.launch {
            runCatching { repository.patchThread(threadId, patch) }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(error = crmErrorMessage(e, "Nie udało się zapisać zmiany"))
                    }
                }
        }
    }

    // ── Pisanie ───────────────────────────────────────────────────────────────

    fun startCompose() {
        _uiState.update { it.copy(composer = Composer()) }
    }

    fun startReply(thread: EmailThread, to: String, subject: String) {
        _uiState.update {
            it.copy(
                composer = Composer(
                    threadId = thread.id,
                    to = to,
                    subject = subject,
                    title = "Odpowiedź",
                ),
            )
        }
    }

    fun startForward(subject: String, body: String) {
        _uiState.update {
            it.copy(composer = Composer(subject = subject, body = body, title = "Przekaż dalej"))
        }
    }

    fun updateComposer(block: (Composer) -> Composer) {
        _uiState.update { state -> state.copy(composer = state.composer?.let(block)) }
    }

    fun cancelCompose() {
        _uiState.update { it.copy(composer = null) }
    }

    fun addAttachment(attachment: EmailDraftAttachment) =
        updateComposer { it.copy(attachments = it.attachments + attachment) }

    fun removeAttachment(uri: String) =
        updateComposer { it.copy(attachments = it.attachments.filterNot { a -> a.uri == uri }) }

    fun send() = dispatchComposer(asDraft = false)

    fun saveDraft() = dispatchComposer(asDraft = true)

    private fun dispatchComposer(asDraft: Boolean) {
        val state = _uiState.value
        val composer = state.composer ?: return
        val accountId = state.accountId ?: return
        val to = composer.to.splitAddresses()
        if (!asDraft && to.isEmpty()) {
            _uiState.update { it.copy(error = "Podaj co najmniej jednego odbiorcę.") }
            return
        }
        updateComposer { it.copy(sending = true) }
        viewModelScope.launch {
            val draft = EmailDraft(
                accountId = accountId,
                threadId = composer.threadId,
                to = to,
                cc = composer.cc.splitAddresses(),
                subject = composer.subject,
                body = composer.body,
                attachments = composer.attachments,
            )
            runCatching {
                if (asDraft) repository.saveDraft(draft) else repository.send(draft)
            }
                .onSuccess {
                    _uiState.update { it.copy(composer = null) }
                    flash(
                        if (asDraft) "Zapisano wersję roboczą."
                        else "Wiadomość wysłana. Bez zasięgu poleci po powrocie łączności.",
                    )
                    refresh()
                }
                .onFailure { e ->
                    updateComposer { it.copy(sending = false) }
                    _uiState.update {
                        it.copy(error = crmErrorMessage(e, "Nie udało się wysłać wiadomości"))
                    }
                }
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null, error = null) }

    private fun flash(text: String) = _uiState.update { it.copy(message = text) }
}

/** „a@x.pl, b@y.pl; c@z.pl" → trzy adresy. Ludzie piszą i przecinkiem, i średnikiem. */
internal fun String.splitAddresses(): List<String> =
    split(',', ';').map { it.trim() }.filter { it.isNotEmpty() }
