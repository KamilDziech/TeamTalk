package com.ekotak.teamtalk.presentation.voicereport

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.data.audio.SpeechToText
import com.ekotak.teamtalk.domain.model.VoiceReport
import com.ekotak.teamtalk.domain.usecase.voicereport.CreateVoiceReportUseCase
import com.ekotak.teamtalk.domain.usecase.voicereport.GetVoiceReportsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

sealed interface RecordingState {
    data object Idle : RecordingState
    data class Recording(val durationSeconds: Int = 0) : RecordingState
    data class Recorded(val file: File, val durationSeconds: Int) : RecordingState
    data object Processing : RecordingState
}

enum class NoteMode { VOICE, TEXT }

@HiltViewModel
class VoiceReportViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getVoiceReportsUseCase: GetVoiceReportsUseCase,
    private val createVoiceReportUseCase: CreateVoiceReportUseCase,
    private val speechToText: SpeechToText,
) : ViewModel() {

    val callLogId: String = savedStateHandle.get<String>("callLogId") ?: ""

    val reports: StateFlow<List<VoiceReport>> =
        getVoiceReportsUseCase(callLogId = callLogId.ifBlank { null })
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _recordingState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val recordingState: StateFlow<RecordingState> = _recordingState.asStateFlow()

    private val _noteMode = MutableStateFlow(NoteMode.VOICE)
    val noteMode: StateFlow<NoteMode> = _noteMode.asStateFlow()

    private val _textInput = MutableStateFlow("")
    val textInput: StateFlow<String> = _textInput.asStateFlow()

    private val _isSavingText = MutableStateFlow(false)
    val isSavingText: StateFlow<Boolean> = _isSavingText.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    private var timerJob: Job? = null
    private var recordedDurationSec: Int? = null

    fun clearActionError() { _actionError.value = null }
    fun setNoteMode(mode: NoteMode) { _noteMode.value = mode }
    fun onTextInputChanged(text: String) { _textInput.value = text }

    fun saveTextNote() {
        val text = _textInput.value.trim()
        if (text.isBlank()) return
        viewModelScope.launch {
            _isSavingText.value = true
            try {
                createVoiceReportUseCase(
                    callLogId = callLogId.ifBlank { null },
                    text = text,
                    durationSec = recordedDurationSec,
                )
                _textInput.value = ""
                recordedDurationSec = null
            } catch (e: Exception) {
                _actionError.value = e.message ?: "Błąd zapisywania notatki"
            } finally {
                _isSavingText.value = false
            }
        }
    }

    // ── Dyktowanie (mowa → tekst) ─────────────────────────────────────────────

    fun startRecording() {
        if (!speechToText.isAvailable()) {
            _noteMode.value = NoteMode.TEXT
            _recordingState.value = RecordingState.Idle
            _actionError.value = "Rozpoznawanie mowy niedostępne — wpisz notatkę ręcznie"
            return
        }
        speechToText.onText = { text -> _textInput.value = text }
        speechToText.onError = { message ->
            timerJob?.cancel()
            timerJob = null
            _recordingState.value = RecordingState.Idle
            _actionError.value = message
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
        if (text.isNotBlank()) _textInput.value = text
        _noteMode.value = NoteMode.TEXT
        _recordingState.value = RecordingState.Idle
    }

    override fun onCleared() {
        timerJob?.cancel()
        speechToText.cancel()
        super.onCleared()
    }
}
