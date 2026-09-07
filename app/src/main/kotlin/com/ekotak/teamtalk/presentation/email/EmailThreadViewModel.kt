package com.ekotak.teamtalk.presentation.email

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.domain.model.Edit
import com.ekotak.teamtalk.domain.model.EmailDealOption
import com.ekotak.teamtalk.domain.model.EmailDraft
import com.ekotak.teamtalk.domain.model.EmailDraftAttachment
import com.ekotak.teamtalk.domain.model.EmailFolder
import com.ekotak.teamtalk.domain.model.EmailThreadDetail
import com.ekotak.teamtalk.domain.model.EmailThreadPatch
import com.ekotak.teamtalk.domain.repository.EmailRepository
import com.ekotak.teamtalk.presentation.crm.crmErrorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Otwarty wątek poczty — czytanie i wszystko, co się z nim robi z telefonu.
 *
 * Wątek czyta się z cache, więc korespondencja otwarta rano przy kliencie jest
 * dostępna w piwnicy bez zasięgu. Sieć tylko dolewa świeże wiadomości i — przy
 * okazji — oznacza wątek na serwerze jako przeczytany.
 */
@HiltViewModel
class EmailThreadViewModel @Inject constructor(
    private val repository: EmailRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val threadId: String = savedStateHandle["threadId"] ?: ""

    /** Picker „Powiąż z dealem" — lista z serwera, więc wymaga zasięgu. */
    data class DealPicker(
        val query: String = "",
        val options: List<EmailDealOption> = emptyList(),
        val loading: Boolean = false,
    )

    data class UiState(
        val isLoading: Boolean = true,
        val error: String? = null,
        val message: String? = null,
        val detail: EmailThreadDetail? = null,
        val composer: EmailViewModel.Composer? = null,
        val dealPicker: DealPicker? = null,
        /** Wątek zniknął (przeniesiony do kosza) — ekran ma się zamknąć. */
        val closed: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var pickerJob: Job? = null

    init {
        viewModelScope.launch {
            repository.observeThread(threadId).collect { detail ->
                _uiState.update { it.copy(isLoading = false, detail = detail) }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            runCatching { repository.refreshThread(threadId) }
                .onFailure { e ->
                    // Bez zasięgu zostaje cache — to nie jest powód do czerwonego
                    // paska, o ile jest co pokazać.
                    if (_uiState.value.detail == null) {
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = crmErrorMessage(e, "Nie udało się pobrać wątku"),
                            )
                        }
                    }
                }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun toggleStar() {
        val thread = _uiState.value.detail?.thread ?: return
        patch(EmailThreadPatch(starred = !thread.starred))
    }

    fun markUnread() {
        patch(EmailThreadPatch(unread = true))
        flash("Oznaczono jako nieprzeczytane.")
    }

    fun move(target: EmailFolder) {
        patch(EmailThreadPatch(folder = target))
        flash("Przeniesiono do: ${target.label}.")
        _uiState.update { it.copy(closed = true) }
    }

    fun trash() {
        val thread = _uiState.value.detail?.thread ?: return
        viewModelScope.launch {
            runCatching { repository.deleteThread(thread.id) }
                .onSuccess { _uiState.update { it.copy(closed = true) } }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(error = crmErrorMessage(e, "Nie udało się usunąć wątku"))
                    }
                }
        }
    }

    // ── Powiązanie z dealem ───────────────────────────────────────────────────

    fun openDealPicker() {
        _uiState.update { it.copy(dealPicker = DealPicker(loading = true)) }
        searchDeals("")
    }

    fun closeDealPicker() = _uiState.update { it.copy(dealPicker = null) }

    fun searchDeals(query: String) {
        pickerJob?.cancel()
        _uiState.update {
            it.copy(dealPicker = (it.dealPicker ?: DealPicker()).copy(query = query, loading = true))
        }
        pickerJob = viewModelScope.launch {
            runCatching { repository.dealOptions(query) }
                .onSuccess { options ->
                    _uiState.update { state ->
                        state.copy(
                            dealPicker = state.dealPicker?.copy(options = options, loading = false),
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update { state ->
                        state.copy(
                            dealPicker = state.dealPicker?.copy(loading = false),
                            error = crmErrorMessage(e, "Lista deali wymaga zasięgu"),
                        )
                    }
                }
        }
    }

    fun linkDeal(dealId: String?) {
        patch(EmailThreadPatch(dealId = Edit(dealId)))
        _uiState.update { it.copy(dealPicker = null) }
        flash(if (dealId == null) "Odłączono od deala." else "Powiązano z dealem.")
    }

    private fun patch(patch: EmailThreadPatch) {
        val thread = _uiState.value.detail?.thread ?: return
        viewModelScope.launch {
            runCatching { repository.patchThread(thread.id, patch) }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(error = crmErrorMessage(e, "Nie udało się zapisać zmiany"))
                    }
                }
        }
    }

    // ── Odpowiedź i przekazanie ───────────────────────────────────────────────

    fun startReply(replyAll: Boolean) {
        val detail = _uiState.value.detail ?: return
        val last = detail.messages.lastOrNull() ?: return
        // Odpowiadamy nadawcy, a przy własnej wiadomości — jej adresatowi.
        val to = if (last.outbound) last.toAddrs else listOf(last.fromAddr)
        val cc = if (replyAll) (last.toAddrs + last.ccAddrs).filterNot { it in to } else emptyList()
        _uiState.update {
            it.copy(
                composer = EmailViewModel.Composer(
                    threadId = detail.thread.id,
                    to = to.joinToString(", "),
                    cc = cc.distinct().joinToString(", "),
                    subject = replySubject(detail.thread.subject),
                    title = if (replyAll) "Odpowiedz wszystkim" else "Odpowiedź",
                ),
            )
        }
    }

    fun startForward() {
        val detail = _uiState.value.detail ?: return
        val quoted = detail.messages.joinToString("\n\n") { message ->
            "--- ${message.fromName ?: message.fromAddr} · " +
                "${formatMessageDate(message.createdAt)} ---\n${message.bodyText.orEmpty()}"
        }
        _uiState.update {
            it.copy(
                composer = EmailViewModel.Composer(
                    subject = forwardSubject(detail.thread.subject),
                    body = "\n\n---------- Wiadomość przekazana ----------\n$quoted",
                    title = "Przekaż dalej",
                ),
            )
        }
    }

    fun updateComposer(block: (EmailViewModel.Composer) -> EmailViewModel.Composer) {
        _uiState.update { state -> state.copy(composer = state.composer?.let(block)) }
    }

    fun cancelCompose() = _uiState.update { it.copy(composer = null) }

    fun addAttachment(attachment: EmailDraftAttachment) =
        updateComposer { it.copy(attachments = it.attachments + attachment) }

    fun removeAttachment(uri: String) =
        updateComposer { it.copy(attachments = it.attachments.filterNot { a -> a.uri == uri }) }

    fun send() = dispatch(asDraft = false)

    fun saveDraft() = dispatch(asDraft = true)

    private fun dispatch(asDraft: Boolean) {
        val state = _uiState.value
        val composer = state.composer ?: return
        val accountId = state.detail?.thread?.accountId ?: return
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
            runCatching { if (asDraft) repository.saveDraft(draft) else repository.send(draft) }
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
