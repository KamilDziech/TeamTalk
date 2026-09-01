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

/** Plansze kreatora po zakończonej rozmowie. */
enum class PostCallStep {
    /** 1 — „Czy rozmawiałeś z…?" albo, przy nieznanym numerze, „Dodać kontakt?". */
    CONTACT,

    /** 2 — „Streść rozmowę" (dyktowanie albo wpisanie ręczne). */
    SUMMARY,

    /** 3 — „Czy chcesz utworzyć zadanie?". */
    TASK,
}

/**
 * Kreator uruchamiany po zakończonej rozmowie: ustalenie rozmówcy → streszczenie
 * → decyzja o zadaniu. Streszczenie zapisuje się jako notatka głosowa powiązana
 * z klientem i połączeniem — to ona zasila kanał telefoniczny w Komunikacji.
 *
 * Ekran wchodzący z karty połączenia (`skipContact`) startuje od razu od planszy
 * drugiej, bo tam rozmówca jest już znany.
 */
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

    /** Wejście z karty połączenia — plansza z pytaniem o rozmówcę jest zbędna. */
    private val skipContactStep: Boolean = savedStateHandle.get<String>("skipContact") == "1"

    data class UiState(
        val step: PostCallStep = PostCallStep.CONTACT,
        val client: Client? = null,
        /** Nazwa z kartoteki, a gdy jej brak — z książki telefonu. */
        val displayName: String? = null,
        /** `true` dopóki nie wiadomo, czy numer jest w module Klienci. */
        val isResolvingClient: Boolean = true,
        val noteText: String = "",
        val isLoading: Boolean = false,
        val error: String? = null,
        /** Pytanie „Na pewno chcesz pominąć dodanie notatki?". */
        val showSkipConfirm: Boolean = false,
        /** Kreator ma się zamknąć — pominięto notatkę albo odmówiono zadania. */
        val isFinished: Boolean = false,
        /** Zapisane streszczenie — idzie w opis zadania na planszy trzeciej. */
        val savedNote: String = "",
        /** `false` przy wejściu z karty połączenia — planszy z rozmówcą tam nie ma. */
        val canBackToContact: Boolean = true,
    ) {
        /** Czy numer jest już w kartotece — decyduje o treści planszy pierwszej. */
        val isKnownClient: Boolean get() = client != null

        /** Nazwa podpowiadana przy zakładaniu kontaktu (z książki telefonu). */
        val suggestedName: String? get() = if (client == null) displayName else null
    }

    private val _uiState = MutableStateFlow(
        UiState(
            step = if (skipContactStep) PostCallStep.SUMMARY else PostCallStep.CONTACT,
            canBackToContact = !skipContactStep,
        ),
    )
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
        } else {
            _uiState.update { it.copy(isResolvingClient = false) }
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
                _uiState.update {
                    it.copy(client = client, displayName = displayName, isResolvingClient = false)
                }
            } catch (_: Exception) {
                _uiState.update { it.copy(isResolvingClient = false) }
            }
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

    // ── Plansza 1: rozmówca ──────────────────────────────────────────────────

    /** „Tak, rozmawiałem z tą osobą" — idziemy do streszczenia. */
    fun confirmContact() = _uiState.update { it.copy(step = PostCallStep.SUMMARY, error = null) }

    /**
     * Powrót z formularza kartoteki. Świeży klient zastępuje ten dobrany po
     * numerze i od razu przechodzimy do streszczenia — o kontakt już pytaliśmy.
     */
    fun onContactCreated(clientId: String) {
        resolvedClientId = clientId
        _uiState.update { it.copy(step = PostCallStep.SUMMARY, error = null) }
        viewModelScope.launch {
            try {
                val client = clientRepository.getClientById(clientId)
                _uiState.update { it.copy(client = client, displayName = client.displayName) }
            } catch (_: Exception) {
                // Notatka i tak zapisze się po id — nazwa dociągnie się przy odświeżeniu.
            }
        }
    }

    fun askSkip() = _uiState.update { it.copy(showSkipConfirm = true) }

    fun dismissSkip() = _uiState.update { it.copy(showSkipConfirm = false) }

    /** Potwierdzone pominięcie — zamykamy cały kreator, nic nie zapisując. */
    fun confirmSkip() {
        speechToText.cancel()
        _uiState.update { it.copy(showSkipConfirm = false, isFinished = true) }
    }

    // ── Plansza 2: streszczenie ──────────────────────────────────────────────

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

    /** Cofnięcie ze streszczenia na planszę z pytaniem o rozmówcę. */
    fun backToContact() {
        if (skipContactStep) return
        speechToText.cancel()
        timerJob?.cancel()
        timerJob = null
        _recordingState.value = RecordingState.Idle
        _uiState.update { it.copy(step = PostCallStep.CONTACT, error = null) }
    }

    // ── Dyktowanie (mowa → tekst) ────────────────────────────────────────────

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

    // ── Zapis notatki ────────────────────────────────────────────────────────

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
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        savedNote = text,
                        step = PostCallStep.TASK,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Błąd zapisu") }
            }
        }
    }

    // ── Plansza 3: zadanie ───────────────────────────────────────────────────

    /** „Nie" na planszy z zadaniem — notatka jest zapisana, kreator się kończy. */
    fun declineTask() = _uiState.update { it.copy(isFinished = true) }

    /** Dane dla skróconego kreatora zadania (zespół → osoba → priorytet → termin). */
    fun taskHandoff(): TaskHandoff {
        val state = _uiState.value
        return TaskHandoff(
            phone = phone,
            name = state.displayName ?: state.client?.displayName,
            clientId = state.client?.id ?: resolvedClientId,
            note = state.savedNote,
        )
    }

    data class TaskHandoff(
        val phone: String,
        val name: String?,
        val clientId: String?,
        val note: String,
    )

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
