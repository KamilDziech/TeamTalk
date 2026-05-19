package com.ekotak.teamtalk.presentation.postcallnote

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.data.audio.AudioRecorder
import com.ekotak.teamtalk.domain.model.CallLogFilter
import com.ekotak.teamtalk.domain.model.CallStatus
import com.ekotak.teamtalk.domain.model.CallType
import com.ekotak.teamtalk.domain.model.Client
import com.ekotak.teamtalk.domain.repository.CallLogRepository
import com.ekotak.teamtalk.domain.repository.ClientRepository
import com.ekotak.teamtalk.domain.usecase.auth.GetCurrentUserUseCase
import com.ekotak.teamtalk.domain.usecase.client.UpdateClientUseCase
import com.ekotak.teamtalk.domain.usecase.voicereport.TranscribeAudioUseCase
import com.ekotak.teamtalk.domain.usecase.voicereport.UploadAudioUseCase
import java.io.File
import com.ekotak.teamtalk.presentation.voicereport.NoteMode
import com.ekotak.teamtalk.presentation.voicereport.RecordingState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

@HiltViewModel
class PostCallNoteViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val clientRepository: ClientRepository,
    private val callLogRepository: CallLogRepository,
    private val updateClientUseCase: UpdateClientUseCase,
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val audioRecorder: AudioRecorder,
    private val uploadAudioUseCase: UploadAudioUseCase,
    private val transcribeAudioUseCase: TranscribeAudioUseCase,
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

    init {
        if (phone.isNotBlank()) loadClient()
    }

    private fun loadClient() {
        viewModelScope.launch {
            try {
                val client = clientRepository.getClientByPhone(phone)
                val displayName = when {
                    !client?.name.isNullOrBlank() -> client!!.name
                    else -> withContext(Dispatchers.IO) { lookupContactName() }
                }
                _uiState.update { it.copy(client = client, displayName = displayName) }
            } catch (_: Exception) {}
        }
    }

    private fun lookupContactName(): String? {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phone)
        )
        return try {
            context.contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null, null, null,
            )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        } catch (_: Exception) { null }
    }

    fun setNoteMode(mode: NoteMode) {
        _noteMode.value = mode
    }

    fun onNoteTextChange(text: String) = _uiState.update { it.copy(noteText = text, error = null) }

    fun saveNote() {
        val text = _uiState.value.noteText.trim()
        if (text.isBlank()) {
            _uiState.update { it.copy(error = "Wpisz notatkę") }
            return
        }
        persistNote(text)
    }

    // ── Voice recording ────────────────────────────────────────────────────

    fun startRecording() {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { audioRecorder.start() }
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
                _uiState.update { it.copy(error = e.message ?: "Nie można uruchomić nagrywania") }
            }
        }
    }

    fun stopRecording() {
        timerJob?.cancel()
        timerJob = null
        val current = _recordingState.value as? RecordingState.Recording ?: return
        val file = audioRecorder.stop()
        if (file != null) {
            autoTranscribe(file)
        } else {
            _recordingState.value = RecordingState.Idle
        }
    }

    private fun autoTranscribe(file: File) {
        viewModelScope.launch {
            _recordingState.value = RecordingState.Processing
            try {
                val transcription = withContext(Dispatchers.IO) {
                    runCatching { uploadAudioUseCase(file) }.getOrNull()
                    transcribeAudioUseCase(file)
                }
                _uiState.update { it.copy(noteText = transcription ?: "") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Błąd transkrypcji: ${e.javaClass.simpleName}: ${e.message}") }
            } finally {
                withContext(Dispatchers.IO) { runCatching { file.delete() } }
                _recordingState.value = RecordingState.Idle
                _noteMode.value = NoteMode.TEXT
            }
        }
    }

    fun resetToVoice() {
        _uiState.update { it.copy(noteText = "", error = null) }
        _noteMode.value = NoteMode.VOICE
        _recordingState.value = RecordingState.Idle
    }

    // ── Persistence ────────────────────────────────────────────────────────

    private fun persistNote(text: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val timestamp = SimpleDateFormat("HH:mm dd.MM.yyyy", Locale.getDefault()).format(Date())
                val entry = "[$timestamp] $text"
                var clientId: String? = null
                val client = _uiState.value.client
                if (client != null) {
                    val updatedNotes = if (client.notes.isNullOrBlank()) entry
                                       else "$entry\n\n${client.notes}"
                    updateClientUseCase(id = client.id, notes = updatedNotes)
                    clientId = client.id
                } else if (phone.isNotBlank()) {
                    val created = clientRepository.createClient(
                        phone = phone,
                        name = _uiState.value.displayName,
                        address = null,
                        notes = entry,
                    )
                    clientId = created.id
                }
                createCallLogEntry(clientId)
                resolveMissedCalls()
                _uiState.update { it.copy(isLoading = false, isSaved = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Błąd zapisu") }
            }
        }
    }

    private suspend fun resolveMissedCalls() {
        if (phone.isBlank()) return
        runCatching {
            val missed = callLogRepository.getCallLogs(
                CallLogFilter(callerPhoneEq = phone, statusEq = "missed")
            ).first()
            for (entry in missed) {
                runCatching { callLogRepository.updateCallLog(id = entry.id, status = CallStatus.COMPLETED) }
            }
        }
    }

    private suspend fun createCallLogEntry(clientId: String?) {
        runCatching {
            val userId = getCurrentUserUseCase().id
            val isoFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            callLogRepository.createCallLog(
                clientId = clientId,
                employeeId = userId,
                type = CallType.COMPLETED,
                status = CallStatus.COMPLETED,
                timestamp = isoFmt.format(Date()),
                callerPhone = phone.ifBlank { null },
            )
        }
    }

    override fun onCleared() {
        timerJob?.cancel()
        audioRecorder.cancel()
        super.onCleared()
    }

    private fun normalizePhone(raw: String): String {
        val digits = raw.replace(Regex("[^\\d]"), "")
        return if (digits.length >= 9) digits.takeLast(9) else digits
    }
}
