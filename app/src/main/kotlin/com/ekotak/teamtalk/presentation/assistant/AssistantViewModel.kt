package com.ekotak.teamtalk.presentation.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.data.audio.SpeechToText
import com.ekotak.teamtalk.data.audio.TextToSpeechPlayer
import com.ekotak.teamtalk.data.image.CardPhoto
import com.ekotak.teamtalk.domain.model.AssistantAction
import com.ekotak.teamtalk.domain.model.AssistantActionType
import com.ekotak.teamtalk.domain.model.AssistantMessage
import com.ekotak.teamtalk.domain.model.CardField
import com.ekotak.teamtalk.domain.model.ContactKind
import com.ekotak.teamtalk.domain.model.ContactRole
import com.ekotak.teamtalk.domain.model.matchKind
import com.ekotak.teamtalk.domain.model.matchLeadMode
import com.ekotak.teamtalk.domain.model.matchRole
import com.ekotak.teamtalk.domain.model.toLeadPrefill
import com.ekotak.teamtalk.domain.repository.AssistantRepository
import com.ekotak.teamtalk.presentation.crm.crmErrorMessage
import com.ekotak.teamtalk.presentation.lead.LeadPrefillStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
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
 *
 * Wizytówka (2026-09-13): zdjęcie z aparatu albo galerii idzie do serwera,
 * który czyta QR i tekst. Pytania „do czego włożyć" zadaje telefon sam — bez
 * modelu, więc działają zawsze i od razu; odpowiedzieć można kliknięciem,
 * tekstem albo głosem.
 */
@HiltViewModel
class AssistantViewModel @Inject constructor(
    private val repository: AssistantRepository,
    private val speechToText: SpeechToText,
    private val textToSpeech: TextToSpeechPlayer,
    private val leadPrefill: LeadPrefillStore,
) : ViewModel() {

    /** Wiadomość rozmowy wraz z propozycjami akcji, które przyszły razem z nią. */
    data class Entry(
        val message: AssistantMessage,
        val actions: List<AssistantAction> = emptyList(),
        /** Zeskanowana wizytówka z pytaniami „do czego włożyć". */
        val scan: CardScanState? = null,
    )

    data class UiState(
        val log: List<Entry> = emptyList(),
        val input: String = "",
        val pending: Boolean = false,
        val scanning: Boolean = false,
        val listening: Boolean = false,
        val speakReplies: Boolean = false,
        val micAvailable: Boolean = true,
        val notice: String? = null,
        val error: String? = null,
    )

    /** Przejścia do innych ekranów po decyzji w rozmowie. */
    sealed interface Navigation {
        data object OpenLeadWizard : Navigation
        data class OpenClient(val clientId: String) : Navigation
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _navigation = Channel<Navigation>(Channel.BUFFERED)
    val navigation = _navigation.receiveAsFlow()

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
        if (answerScan(text)) return

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
                val outcome = repository.runAction(action)
                updateAction(entryIndex, actionIndex) {
                    it.copy(status = AssistantAction.Status.DONE, result = outcome.summary)
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

    // ── Wizytówka ────────────────────────────────────────────────────────────

    /** Zdjęcie z aparatu albo galerii → zmniejszenie → odczyt na serwerze. */
    fun onCardPhoto(original: ByteArray) {
        if (_uiState.value.scanning) return
        _uiState.update {
            it.copy(
                scanning = true,
                error = null,
                notice = null,
                log = it.log + Entry(AssistantMessage(AssistantMessage.ROLE_USER, "📇 Wizytówka do zeskanowania")),
            )
        }
        viewModelScope.launch {
            try {
                val jpeg = withContext(Dispatchers.Default) { CardPhoto.prepare(original) }
                    ?: throw IllegalStateException("Nie mogę otworzyć tego zdjęcia — spróbuj zrobić nowe.")
                val result = repository.scanCard(jpeg)
                val scan = CardScanState(result)
                _uiState.update {
                    it.copy(
                        scanning = false,
                        log = it.log + Entry(
                            message = AssistantMessage(AssistantMessage.ROLE_ASSISTANT, result.card.asThreadText()),
                            scan = scan,
                        ),
                    )
                }
                speak(if (result.canSave) scan.question else "Odczytałem wizytówkę.")
            } catch (e: Exception) {
                val message = crmErrorMessage(e, "Nie udało się odczytać wizytówki")
                _uiState.update {
                    it.copy(
                        scanning = false,
                        log = it.log + Entry(AssistantMessage(AssistantMessage.ROLE_ASSISTANT, message)),
                    )
                }
            }
        }
    }

    fun chooseKind(entryIndex: Int, kind: ContactKind) = updateScan(entryIndex) { it.chooseKind(kind) }

    fun chooseLead(entryIndex: Int, lead: Boolean) = updateScan(entryIndex) { it.chooseLead(lead) }

    fun chooseRole(entryIndex: Int, role: ContactRole) = updateScan(entryIndex) { it.withRole(role.wire) }

    /** „Inna rola" — pole tekstowe; pusta rola nie pozwala zapisać. */
    fun chooseOtherRole(entryIndex: Int) = updateScan(entryIndex) {
        it.copy(kind = ContactKind.INNY, role = "", otherRole = true, step = CardScanState.Step.CONFIRM)
    }

    fun onOtherRoleText(entryIndex: Int, text: String) = updateScan(entryIndex) { it.copy(role = text) }

    fun toggleCardEdit(entryIndex: Int) = updateScan(entryIndex) { it.copy(editing = !it.editing) }

    fun onCardField(entryIndex: Int, field: CardField, value: String) =
        updateScan(entryIndex) { it.copy(card = it.card.with(field, value)) }

    /** Zapis karty kontaktu — ta sama akcja `create_contact`, którą serwer umie też zaproponować w czacie. */
    fun saveScan(entryIndex: Int) {
        val scan = _uiState.value.log.getOrNull(entryIndex)?.scan ?: return
        val kind = scan.kind ?: return
        if (scan.status == CardScanState.Status.RUNNING || scan.status == CardScanState.Status.DONE) return
        updateScan(entryIndex) { it.copy(status = CardScanState.Status.RUNNING, message = null, editing = false) }

        val card = scan.card
        val args = buildMap {
            CardField.entries.forEach { field -> put(field.wireKey, JsonPrimitive(card.get(field).trim())) }
            put("kind", JsonPrimitive(kind.wire))
            if (kind == ContactKind.INNY) put("role", JsonPrimitive(scan.roleValue.lowercase()))
        }
        val action = AssistantAction(
            type = AssistantActionType.CREATE_CONTACT,
            label = "Dodaj do zakładki ${scan.tab}",
            args = JsonObject(args),
        )
        viewModelScope.launch {
            try {
                val outcome = repository.runAction(action)
                updateScan(entryIndex) {
                    it.copy(status = CardScanState.Status.DONE, message = outcome.summary, clientId = outcome.clientId)
                }
                speak(outcome.summary)
            } catch (e: Exception) {
                updateScan(entryIndex) {
                    it.copy(
                        status = CardScanState.Status.ERROR,
                        message = crmErrorMessage(e, "Nie udało się zapisać kontaktu"),
                    )
                }
            }
        }
    }

    fun openLead(entryIndex: Int) {
        val scan = _uiState.value.log.getOrNull(entryIndex)?.scan ?: return
        val kind = scan.kind?.takeIf { it.isClient } ?: return
        leadPrefill.put(scan.card.toLeadPrefill(kind))
        _navigation.trySend(Navigation.OpenLeadWizard)
    }

    fun openClient(entryIndex: Int) {
        val id = _uiState.value.log.getOrNull(entryIndex)?.scan?.clientId ?: return
        _navigation.trySend(Navigation.OpenClient(id))
    }

    /**
     * Odpowiedź na pytanie o wizytówkę wpisana albo powiedziana („to hurtownia",
     * „tylko karta"). `false` = tekst nie pasuje — idzie do zwykłego czatu.
     */
    private fun answerScan(text: String): Boolean {
        val log = _uiState.value.log
        val index = log.indexOfLast { it.scan != null }
        val scan = log.getOrNull(index)?.scan ?: return false
        if (!scan.awaitsAnswer) return false
        val next = when (scan.step) {
            CardScanState.Step.KIND -> matchKind(text)?.let { (kind, role) -> scan.chooseKind(kind, role) }
            CardScanState.Step.MODE -> matchLeadMode(text)?.let { scan.chooseLead(it) }
            CardScanState.Step.ROLE -> matchRole(text)?.let { scan.withRole(it) }
            else -> null
        } ?: return false
        _uiState.update { state ->
            state.copy(
                input = "",
                error = null,
                log = state.log.mapIndexed { i, e -> if (i == index) e.copy(scan = next) else e } +
                    Entry(AssistantMessage(AssistantMessage.ROLE_USER, text)),
            )
        }
        speak(next.question)
        return true
    }

    private fun updateScan(entryIndex: Int, transform: (CardScanState) -> CardScanState) {
        var askNext: String? = null
        _uiState.update { state ->
            state.copy(
                log = state.log.mapIndexed { i, entry ->
                    val scan = entry.scan
                    if (i != entryIndex || scan == null) {
                        entry
                    } else {
                        val next = transform(scan)
                        if (next.question != scan.question) askNext = next.question
                        entry.copy(scan = next)
                    }
                },
            )
        }
        speak(askNext)
    }

    private fun speak(text: String?) {
        if (!text.isNullOrBlank() && _uiState.value.speakReplies) textToSpeech.speak(text)
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

/** Klucz pola w argumentach `create_contact` — nazwy jak w API. */
private val CardField.wireKey: String
    get() = when (this) {
        CardField.FIRST_NAME -> "firstName"
        CardField.LAST_NAME -> "lastName"
        CardField.COMPANY -> "companyName"
        CardField.JOB_TITLE -> "jobTitle"
        CardField.NIP -> "nip"
        CardField.PHONE -> "phone"
        CardField.PHONE2 -> "phone2"
        CardField.EMAIL -> "email"
        CardField.EMAIL2 -> "email2"
        CardField.WEBSITE -> "website"
        CardField.STREET -> "street"
        CardField.POSTAL_CODE -> "postalCode"
        CardField.CITY -> "city"
    }
