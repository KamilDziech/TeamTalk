package com.ekotak.teamtalk.presentation.chat

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.data.audio.AudioRecorder
import com.ekotak.teamtalk.domain.model.ChatMessage
import com.ekotak.teamtalk.domain.model.ChatReceipts
import com.ekotak.teamtalk.domain.model.ChatSendResult
import com.ekotak.teamtalk.domain.model.ChatThreadDetail
import com.ekotak.teamtalk.domain.repository.ChatRepository
import com.ekotak.teamtalk.presentation.crm.crmErrorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

/**
 * Jedna rozmowa: wątek, pisanie, załączniki i drobne akcje pod dymkiem.
 *
 * Wysyłka jest OPTYMISTYCZNA tylko o tyle, o ile pozwala repozytorium —
 * wiadomość wchodzi na listę dopiero po jego odpowiedzi, ale bez zasięgu
 * wraca od razu jako wpis z kolejki (zegarek zamiast ptaszka), więc ekran
 * nigdy nie „zjada" napisanego zdania.
 */
@HiltViewModel
class ChatThreadViewModel @Inject constructor(
    private val chat: ChatRepository,
    private val recorder: AudioRecorder,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val threadId: String = savedStateHandle.get<String>("threadId").orEmpty()

    data class UiState(
        val isLoading: Boolean = true,
        val isSending: Boolean = false,
        val error: String? = null,
        val notice: String? = null,
        val thread: ChatThreadDetail? = null,
        /** Cytowana wiadomość — pasek nad polem pisania. */
        val replyTo: ChatMessage? = null,
        val recording: Boolean = false,
        val recordedSeconds: Int = 0,
        /** „Kto przeczytał" — otwarte okno informacji o wiadomości. */
        val receipts: ChatReceipts? = null,
        val pollOpen: Boolean = false,
        /** Ile wiadomości z tej rozmowy czeka w kolejce. */
        val pending: Int = 0,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        load(initial = true)
    }

    fun refresh() = load(initial = false)

    private fun load(initial: Boolean) {
        viewModelScope.launch {
            if (initial) _uiState.update { it.copy(isLoading = true) }
            try {
                val thread = chat.getThread(threadId)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        thread = thread,
                        pending = thread.messages.count { m -> m.pending },
                    )
                }
                // Wejście w rozmowę zeruje licznik — tak samo jak w panelu.
                chat.markRead(threadId)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, error = crmErrorMessage(e, "Nie udało się wczytać rozmowy"))
                }
            }
        }
    }

    // ── Pisanie ──────────────────────────────────────────────────────────────

    fun send(body: String, mentions: List<String>) {
        if (body.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, error = null) }
            try {
                val result = chat.sendText(
                    threadId = threadId,
                    body = body,
                    mentions = mentions,
                    replyToId = _uiState.value.replyTo?.id,
                )
                _uiState.update {
                    it.copy(
                        isSending = false,
                        replyTo = null,
                        notice = if (result == ChatSendResult.QUEUED) QUEUED_NOTICE else null,
                    )
                }
                load(initial = false)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSending = false, error = crmErrorMessage(e, "Nie udało się wysłać"))
                }
            }
        }
    }

    fun sendAttachment(file: File, fileName: String, mimeType: String, caption: String = "") {
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, error = null) }
            try {
                val result = chat.sendAttachment(
                    threadId = threadId,
                    file = file,
                    fileName = fileName,
                    mimeType = mimeType,
                    caption = caption,
                    replyToId = _uiState.value.replyTo?.id,
                )
                _uiState.update {
                    it.copy(
                        isSending = false,
                        replyTo = null,
                        notice = if (result == ChatSendResult.QUEUED) QUEUED_NOTICE else null,
                    )
                }
                load(initial = false)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSending = false, error = crmErrorMessage(e, "Nie udało się wysłać załącznika"))
                }
            }
        }
    }

    fun sendPoll(question: String, options: List<String>, multi: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(pollOpen = false, isSending = true) }
            try {
                val result = chat.sendPoll(threadId, question, options, multi)
                _uiState.update {
                    it.copy(
                        isSending = false,
                        notice = if (result == ChatSendResult.QUEUED) QUEUED_NOTICE else null,
                    )
                }
                load(initial = false)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSending = false, error = crmErrorMessage(e, "Nie udało się wysłać ankiety"))
                }
            }
        }
    }

    // ── Głosówka ─────────────────────────────────────────────────────────────

    fun startRecording() {
        runCatching { recorder.start() }
            .onSuccess { _uiState.update { s -> s.copy(recording = true, recordedSeconds = 0) } }
            .onFailure {
                _uiState.update { s -> s.copy(error = "Nie udało się włączyć mikrofonu.") }
            }
    }

    fun tickRecording() = _uiState.update { it.copy(recordedSeconds = it.recordedSeconds + 1) }

    fun cancelRecording() {
        recorder.cancel()
        _uiState.update { it.copy(recording = false, recordedSeconds = 0) }
    }

    fun stopAndSendRecording() {
        val seconds = _uiState.value.recordedSeconds
        val file = recorder.stop()
        _uiState.update { it.copy(recording = false, recordedSeconds = 0) }
        if (file == null) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true) }
            try {
                val result = chat.sendAttachment(
                    threadId = threadId,
                    file = file,
                    fileName = file.name,
                    mimeType = "audio/mp4",
                    replyToId = _uiState.value.replyTo?.id,
                    durationSec = seconds.coerceAtLeast(1),
                    // Fali nie rysujemy z nagrania — dymek dostaje równy pasek,
                    // a liczenie amplitud z pliku m4a nie warte jest dekodera.
                    waveform = emptyList(),
                )
                _uiState.update {
                    it.copy(
                        isSending = false,
                        replyTo = null,
                        notice = if (result == ChatSendResult.QUEUED) QUEUED_NOTICE else null,
                    )
                }
                load(initial = false)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSending = false, error = crmErrorMessage(e, "Nie udało się wysłać głosówki"))
                }
            }
        }
    }

    // ── Akcje pod dymkiem ────────────────────────────────────────────────────

    fun reply(message: ChatMessage) = _uiState.update { it.copy(replyTo = message) }

    fun cancelReply() = _uiState.update { it.copy(replyTo = null) }

    fun react(message: ChatMessage, emoji: String) = act { chat.toggleReaction(message.id, emoji) }

    fun star(message: ChatMessage) = act { chat.toggleStar(message.id) }

    fun vote(message: ChatMessage, optionIndex: Int) = act { chat.vote(message.id, optionIndex) }

    fun pin(message: ChatMessage?) = act { chat.pinMessage(threadId, message?.id) }

    fun forward(message: ChatMessage, threadIds: List<String>) =
        act { chat.forward(message.id, threadIds) }

    fun showReceipts(message: ChatMessage) {
        viewModelScope.launch {
            val receipts = runCatching { chat.receipts(message.id) }.getOrNull()
            if (receipts == null) {
                _uiState.update { it.copy(error = "Nie udało się sprawdzić odczytów.") }
            } else {
                _uiState.update { it.copy(receipts = receipts) }
            }
        }
    }

    fun hideReceipts() = _uiState.update { it.copy(receipts = null) }

    fun openPoll() = _uiState.update { it.copy(pollOpen = true) }

    fun closePoll() = _uiState.update { it.copy(pollOpen = false) }

    fun saveDraft(text: String) {
        viewModelScope.launch { runCatching { chat.saveDraft(threadId, text) } }
    }

    /** Pobiera załącznik do pliku w cache'u i oddaje ścieżkę do otwarcia. */
    fun openAttachment(message: ChatMessage, onReady: (File) -> Unit) {
        val name = message.attachment?.name ?: return
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                runCatching { chat.downloadAttachment(message.id, name) }.getOrNull()
            }
            if (file == null) {
                _uiState.update { it.copy(error = "Nie udało się pobrać pliku.") }
            } else {
                onReady(file)
            }
        }
    }

    private fun act(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
                load(initial = false)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = crmErrorMessage(e, "Nie udało się zapisać")) }
            }
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }

    fun dismissNotice() = _uiState.update { it.copy(notice = null) }

    private companion object {
        const val QUEUED_NOTICE = "Brak zasięgu — wyślę, gdy wróci."
    }
}
