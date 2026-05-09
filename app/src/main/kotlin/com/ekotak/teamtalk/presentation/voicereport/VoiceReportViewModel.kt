package com.ekotak.teamtalk.presentation.voicereport

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.data.audio.AudioRecorder
import com.ekotak.teamtalk.domain.model.VoiceReport
import com.ekotak.teamtalk.domain.usecase.voicereport.CreateVoiceReportUseCase
import com.ekotak.teamtalk.domain.usecase.voicereport.GetVoiceReportsUseCase
import com.ekotak.teamtalk.domain.usecase.voicereport.TranscribeAudioUseCase
import com.ekotak.teamtalk.domain.usecase.voicereport.UploadAudioUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
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
    private val uploadAudioUseCase: UploadAudioUseCase,
    private val transcribeAudioUseCase: TranscribeAudioUseCase,
    private val audioRecorder: AudioRecorder,
) : ViewModel() {

    // Nav argument key must match the route defined in NavGraph (Module 13)
    val callLogId: String = savedStateHandle.get<String>("callLogId") ?: ""

    val reports: StateFlow<List<VoiceReport>> =
        getVoiceReportsUseCase(callLogIdEq = callLogId.ifBlank { null })
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

    fun clearActionError() { _actionError.value = null }

    fun setNoteMode(mode: NoteMode) {
        _noteMode.value = mode
    }

    fun onTextInputChanged(text: String) {
        _textInput.value = text
    }

    fun saveTextNote() {
        val text = _textInput.value.trim()
        if (text.isBlank()) return
        viewModelScope.launch {
            _isSavingText.value = true
            try {
                createVoiceReportUseCase(
                    callLogId = callLogId,
                    transcription = text,
                )
                _textInput.value = ""
            } catch (e: Exception) {
                _actionError.value = e.message ?: "Błąd zapisywania notatki"
            } finally {
                _isSavingText.value = false
            }
        }
    }

    fun startRecording() {
        viewModelScope.launch {
            try {
                val file = withContext(Dispatchers.IO) { audioRecorder.start() }
                _recordingState.value = RecordingState.Recording(0)
                timerJob = launch {
                    var seconds = 0
                    while (true) {
                        delay(1_000)
                        seconds++
                        _recordingState.value = RecordingState.Recording(seconds)
                    }
                }
            } catch (e: Exception) {
                _actionError.value = e.message ?: "Nie można uruchomić nagrywania"
            }
        }
    }

    fun stopRecording() {
        timerJob?.cancel()
        timerJob = null
        val current = _recordingState.value as? RecordingState.Recording ?: return
        val file = audioRecorder.stop()
        _recordingState.value = if (file != null) RecordingState.Recorded(file, current.durationSeconds)
                                 else RecordingState.Idle
    }

    fun discardRecording() {
        val recorded = _recordingState.value as? RecordingState.Recorded ?: return
        viewModelScope.launch(Dispatchers.IO) { recorded.file.delete() }
        _recordingState.value = RecordingState.Idle
    }

    fun uploadAndTranscribe() {
        val recorded = _recordingState.value as? RecordingState.Recorded ?: return
        viewModelScope.launch {
            _recordingState.value = RecordingState.Processing
            try {
                val audioUrl = withContext(Dispatchers.IO) { uploadAudioUseCase(recorded.file) }
                val transcription = withContext(Dispatchers.IO) { transcribeAudioUseCase(recorded.file) }
                createVoiceReportUseCase(
                    callLogId = callLogId,
                    audioUrl = audioUrl,
                    transcription = transcription,
                    callCount = recorded.durationSeconds,
                )
                withContext(Dispatchers.IO) { recorded.file.delete() }
                _recordingState.value = RecordingState.Idle
            } catch (e: Exception) {
                _recordingState.value = RecordingState.Recorded(recorded.file, recorded.durationSeconds)
                _actionError.value = e.message ?: "Błąd przesyłania nagrania"
            }
        }
    }

    override fun onCleared() {
        timerJob?.cancel()
        audioRecorder.cancel()
        super.onCleared()
    }
}
