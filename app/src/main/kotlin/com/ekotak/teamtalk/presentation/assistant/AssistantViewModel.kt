package com.ekotak.teamtalk.presentation.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.data.audio.SpeechToText
import com.ekotak.teamtalk.data.audio.TextToSpeechPlayer
import com.ekotak.teamtalk.domain.model.AssistantAction
import com.ekotak.teamtalk.domain.model.AssistantMessage
import com.ekotak.teamtalk.domain.repository.AssistantRepository
import com.ekotak.teamtalk.presentation.crm.crmErrorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Ekran asystenta: rozmowa, dyktowanie i zatwierdzanie propozycji akcji.
 *
 * Rozmowa żyje TYLKO tutaj (ustalenie z zamawiającym: bez historii) — wyjście
 * z ekranu ją kasuje. Do serwera przy każdym pytaniu leci cały dotychczasowy
 * wątek, bo API jest bezstanowe.
 *
 * Propozycje akcji wiszą przy wiadomości, która je zrodziła, i mają własny
 * status wykonania — dzięki temu widać, co już poszło na serwer, a co czeka
 * na kliknięcie. Wykonana propozycja nie da się kliknąć drugi raz.
 */
@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val repository: AssistantRepository,
    private val speechToText: SpeechToText,
    private val textToSpeech: TextToSpeechPlayer,
) : ViewModel() {

    /** Wiadomość rozmowy wraz z propozycjami akcji, które przyszły razem z nią. */
    data class Entry(
        val message: AssistantMessage,
        val actions: List<AssistantAction> = emptyList(),
    )

    data class UiState(
        val log: List<Entry> = emptyList(),
        val input: String = "",
        val pending: Boolean = false,
        val listening: Boolean = false,
        val speakReplies: Boolean = false,
        val micAvailable: Boolean = true,
        val notice: String? = null,
        val error: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        _uiState.update { it.copy(micAvailable = speechToText.isAvailable()) }
    }

    fun onInputChange(value: String) {
        _uiState.update { it.copy(input = value) }
    }

    fun toggleSpeakReplies() {
        val next = !_uiState.value.speakReplies
        if (!next) textToSpeech.stop()
        _uiState.update { it.copy(speakReplies = next) }
    }

    /** Wysyła pytanie i dopisuje odpowiedź do wątku. */
    fun ask(question: String) {
        val text = question.trim()
        if (text.isBlank() || _uiState.value.pending) return

        val log = _uiState.value.log +
            Entry(AssistantMessage(AssistantMessage.ROLE_USER, text))
        _uiState.update {
            it.copy(log = log, input = "", pending = true, error = null, notice = null)
        }

        viewModelScope.launch {
            try {
                val answer = repository.ask(log.map { it.message })
                _uiState.update { state ->
                    state.copy(
                        log = state.log + Entry(
                            message = AssistantMessage(AssistantMessage.ROLE_ASSISTANT, answer.text),
                            actions = answer.actions,
                        ),
                        pending = false,
                        notice = when {
                            !answer.configured ->
                                "Asystent AI nie jest jeszcze skonfigurowany (brak klucza LLM)."
                            answer.usedTools.isNotEmpty() ->
                                "Sprawdziłem w systemie: ${describeTools(answer.usedTools)}."
                            else -> null
                        },
                    )
                }
                if (_uiState.value.speakReplies) textToSpeech.speak(answer.text)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        pending = false,
                        error = crmErrorMessage(e, "Nie udało się uzyskać odpowiedzi"),
                    )
                }
            }
        }
    }

    /**
     * Wykonuje zatwierdzoną propozycję. Adresujemy ją pozycją w wątku, bo ta
     * sama akcja może pojawić się w rozmowie kilka razy (np. dwa zadania).
     */
    fun runAction(entryIndex: Int, actionIndex: Int) {
        val action = _uiState.value.log.getOrNull(entryIndex)?.actions?.getOrNull(actionIndex)
            ?: return
        if (action.status == AssistantAction.Status.RUNNING ||
            action.status == AssistantAction.Status.DONE
        ) {
            return
        }
        updateAction(entryIndex, actionIndex) {
            it.copy(status = AssistantAction.Status.RUNNING, result = null)
        }

        viewModelScope.launch {
            try {
                val summary = repository.runAction(action)
                updateAction(entryIndex, actionIndex) {
                    it.copy(status = AssistantAction.Status.DONE, result = summary)
                }
            } catch (e: Exception) {
                updateAction(entryIndex, actionIndex) {
                    it.copy(
                        status = AssistantAction.Status.ERROR,
                        result = crmErrorMessage(e, "Nie udało się wykonać akcji"),
                    )
                }
            }
        }
    }

    /**
     * Mikrofon: tryb jednej wypowiedzi. Tekst leci do pola na żywo, a po ciszy
     * pytanie wysyła się samo — jak w panelu, żeby dało się zapytać bez patrzenia
     * w ekran (kierowca, drabina, ręce w robocie).
     */
    fun toggleMic() {
        if (_uiState.value.listening) {
            val text = speechToText.stop()
            _uiState.update { it.copy(listening = false) }
            if (text.isNotBlank()) ask(text)
            return
        }
        if (!speechToText.isAvailable()) {
            _uiState.update {
                it.copy(micAvailable = false, error = "Rozpoznawanie mowy niedostępne na tym urządzeniu")
            }
            return
        }
        speechToText.onText = { text -> _uiState.update { it.copy(input = text) } }
        speechToText.onError = { message ->
            _uiState.update { it.copy(listening = false, error = message) }
        }
        speechToText.onDone = {
            val text = _uiState.value.input
            _uiState.update { it.copy(listening = false) }
            if (text.isNotBlank()) ask(text)
        }
        _uiState.update { it.copy(listening = true, error = null, input = "") }
        speechToText.start(continuous = false)
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun updateAction(
        entryIndex: Int,
        actionIndex: Int,
        transform: (AssistantAction) -> AssistantAction,
    ) {
        _uiState.update { state ->
            state.copy(
                log = state.log.mapIndexed { i, entry ->
                    if (i != entryIndex) {
                        entry
                    } else {
                        entry.copy(
                            actions = entry.actions.mapIndexed { j, action ->
                                if (j == actionIndex) transform(action) else action
                            },
                        )
                    }
                },
            )
        }
    }

    /** Nazwy narzędzi serwera → jedno zdanie dla człowieka. */
    private fun describeTools(tools: List<String>): String =
        tools.distinct().joinToString(", ") { tool ->
            when (tool) {
                "search_clients" -> "kartoteka klientów"
                "search_deals" -> "lista deali"
                "get_deal" -> "karta deala"
                else -> tool
            }
        }

    override fun onCleared() {
        speechToText.cancel()
        textToSpeech.release()
        super.onCleared()
    }
}
