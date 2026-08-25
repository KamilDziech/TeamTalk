package com.ekotak.teamtalk.presentation.postcallnote

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.data.audio.SpeechToText
import com.ekotak.teamtalk.domain.model.Client
import com.ekotak.teamtalk.domain.repository.ClientRepository
import com.ekotak.teamtalk.domain.repository.VoiceReportRepository
import com.ekotak.teamtalk.domain.usecase.calllog.GetCallLogsUseCase
import com.ekotak.teamtalk.presentation.voicereport.NoteMode
import com.ekotak.teamtalk.presentation.voicereport.RecordingState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class PostCallNoteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val clientRepository: ClientRepository,
    private val voiceReportRepository: VoiceReportRepository,
    private val getCallLogsUseCase: GetCallLogsUseCase,
    private val speechToText: SpeechToText,
) : ViewModel() {

    val phone: String = normalizePhone(savedStateHandle["phone"] ?: "")

    data class UiState(
        val client: Client? = null,
        val displayName: String? = null,
        val noteText: String = "",
        val isLoading: Boolean = false,
        val isSaved: Boolean = false,
        val error: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _noteMode = MutableStateFlow(NoteMode.VOICE)
    val noteMode: StateFlow<NoteMode> = _noteMode.asStateFlow()

    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private var timerJob: Job? = null
    private var recordedDurationSec: Int? = null

    // Powiązanie notatki z połączeniem/klientem, dobrane po numerze telefonu.
    // Bez callLogId notatka nie pojawia się na żadnym ekranie (filtry po callLogId/clientId).
    private var resolvedCallLogId: String? = null
    private var resolvedClientId: String? = null

    init {
        if (phone.isNotBlank()) {
            loadClient()
            loadCallLink()
        }
    }

    /** Znajduje ostatnie połączenie dla tego numeru, by powiązać z nim notatkę. */
    private fun loadCallLink() {
        viewModelScope.launch {
            try {
                getCallLogsUseCase().collect { logs ->
                    val match = logs
                        .filter { it.phoneNumber.endsWith(phone) || phone.endsWith(it.phoneNumber) }
                        .maxByOrNull { it.startedAt }
                    if (match != null) {
                        resolvedCallLogId = match.id
                        if (resolvedClientId == null) resolvedClientId = match.clientId
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun loadClient() {
        viewModelScope.launch {
            try {
                val client = clientRepository.getClientByPhone(phone)
                val displayName = when {
                    !client?.displayName.isNullOrBlank() -> client!!.displayName
                    else -> withContext(Dispatchers.IO) { lookupContactName() }
                }
                _uiState.update { it.copy(client = client, displayName = displayName) }
            } catch (_: Exception) {}
        }
    }

    private fun lookupContactName(): String? {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phone),
        )
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null,
            )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        } catch (_: Exception) { null }
    }

    fun setNoteMode(mode: NoteMode) { _noteMode.value = mode }

    fun onNoteTextChange(text: String) = _uiState.update { it.copy(noteText = text, error = null) }

    fun saveNote() {
        val text = _uiState.value.noteText.trim()
        if (text.isBlank()) {
            _uiState.update { it.copy(error = "Wpisz notatkę lub nagraj wiadomość") }
            return
        }
        persistNote(text)
    }

    // ── Dyktowanie (mowa → tekst) ─────────────────────────────────────────────

    fun startRecording() {
        if (!speechToText.isAvailable()) {
            _noteMode.value = NoteMode.TEXT
            _recordingState.value = RecordingState.Idle
            _uiState.update {
                it.copy(error = "Rozpoznawanie mowy niedostępne — wpisz notatkę ręcznie")
            }
            return
        }
        speechToText.onText = { text ->
            _uiState.update { it.copy(noteText = text, error = null) }
        }
        speechToText.onError = { message ->
            timerJob?.cancel()
            timerJob = null
            _recordingState.value = RecordingState.Idle
            _uiState.update { it.copy(error = message) }
        }
        speechToText.start()
        _recordingState.value = RecordingState.Recording(0)
        timerJob = viewModelScope.launch {
            var seconds = 0
            while (true) {
                delay(1_000)
                seconds++
                _recordingState.value = RecordingState.Recording(seconds)
            }
        }
    }

    fun stopRecording() {
        val current = _recordingState.value as? RecordingState.Recording
        timerJob?.cancel()
        timerJob = null
        recordedDurationSec = current?.durationSeconds
        val text = speechToText.stop()
        // Zebrany tekst pokazujemy w trybie tekstowym do korekty i zapisu.
        _uiState.update { it.copy(noteText = text.ifBlank { it.noteText }) }
        _noteMode.value = NoteMode.TEXT
        _recordingState.value = RecordingState.Idle
    }

    fun resetToVoice() {
        speechToText.cancel()
        recordedDurationSec = null
        _uiState.update { it.copy(noteText = "", error = null) }
        _noteMode.value = NoteMode.VOICE
        _recordingState.value = RecordingState.Idle
    }

    // ── Zapis ───────────────────────────────────────────────────────────────────

    private fun persistNote(text: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                voiceReportRepository.createVoiceReport(
                    callLogId = resolvedCallLogId,
                    clientId = _uiState.value.client?.id ?: resolvedClientId,
                    text = text.ifBlank { null },
                    durationSec = recordedDurationSec,
                )
                _uiState.update { it.copy(isLoading = false, isSaved = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Błąd zapisu") }
            }
        }
    }

    override fun onCleared() {
        timerJob?.cancel()
        speechToText.cancel()
        super.onCleared()
    }

    private fun normalizePhone(raw: String): String {
        val digits = raw.replace(Regex("[^\\d]"), "")
        return if (digits.length >= 9) digits.takeLast(9) else digits
    }
}
