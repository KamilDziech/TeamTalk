package com.ekotak.teamtalk.presentation.task

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.data.audio.SpeechToText
import com.ekotak.teamtalk.data.local.preferences.SessionPreferences
import com.ekotak.teamtalk.domain.model.Client
import com.ekotak.teamtalk.domain.model.Deal
import com.ekotak.teamtalk.domain.model.NewClient
import com.ekotak.teamtalk.domain.model.TaskLink
import com.ekotak.teamtalk.domain.model.TaskMember
import com.ekotak.teamtalk.domain.model.TaskPriority
import com.ekotak.teamtalk.domain.model.TaskProject
import com.ekotak.teamtalk.domain.model.TaskTeam
import com.ekotak.teamtalk.domain.model.membersFrom
import com.ekotak.teamtalk.domain.repository.ClientRepository
import com.ekotak.teamtalk.domain.repository.DealRepository
import com.ekotak.teamtalk.domain.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

/** Kolejne plansze kreatora. [DONE] to podsumowanie po zapisie, nie krok wejściowy. */
enum class WizardStep {
    TITLE, DESCRIPTION, SUBJECT, TEAM, PERSON, PRIORITY, DUE, DONE;

    companion object {
        /** Pełny kreator — wejście z kartoteki, karty deala albo osi czasu. */
        val WIZARD: List<WizardStep> = WizardStep.entries.filter { it != WizardStep.DONE }

        /**
         * Skrót używany po zakończonej rozmowie: tytuł, opis i „kogo dotyczy"
         * mamy już z kreatora notatki, więc pytamy tylko o cztery brakujące
         * rzeczy — zespół, osobę, priorytet i termin.
         */
        val SHORT: List<WizardStep> = listOf(TEAM, PERSON, PRIORITY, DUE)
    }
}

/** Tryb kroku „kogo dotyczy". */
enum class SubjectMode { CLIENT, PROJECT, INTERNAL }

@HiltViewModel
class CreateTaskViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val taskRepository: TaskRepository,
    private val clientRepository: ClientRepository,
    private val dealRepository: DealRepository,
    private val sessionPreferences: SessionPreferences,
    private val speechToText: SpeechToText,
) : ViewModel() {

    private val phone: String = savedStateHandle["phone"] ?: ""
    private val callerName: String? =
        (savedStateHandle["name"] as String?)?.takeIf { it.isNotBlank() }

    /** Klient ustalony wcześniej (kreator po rozmowie) — pomija krok „kogo dotyczy". */
    private val presetClientId: String? =
        (savedStateHandle["clientId"] as String?)?.takeIf { it.isNotBlank() }

    /** Streszczenie rozmowy przekazane z kreatora notatki — ląduje w opisie. */
    private val presetNote: String? =
        (savedStateHandle["note"] as String?)?.takeIf { it.isNotBlank() }

    /** `short` = wejście po rozmowie: cztery plansze zamiast siedmiu. */
    private val isShort: Boolean = savedStateHandle.get<String>("mode") == MODE_SHORT

    /** Ręcznie wpisany kontakt z kroku „kogo dotyczy". Zakłada klienta w kartotece. */
    data class NewContact(
        val firstName: String = "",
        val lastName: String = "",
        val phone: String = "",
        val email: String = "",
    ) {
        /** `POST /api/clients` wymaga imienia i nazwiska — bez nich nie ma czego zakładać. */
        val isComplete: Boolean get() = firstName.isNotBlank() && lastName.isNotBlank()
        val isEmpty: Boolean
            get() = firstName.isBlank() && lastName.isBlank() && phone.isBlank() && email.isBlank()
        val displayName: String get() = "$firstName $lastName".trim()
    }

    data class UiState(
        val step: WizardStep = WizardStep.TITLE,
        /** Plansze tego przebiegu — pełne siedem albo skrót po rozmowie. */
        val steps: List<WizardStep> = WizardStep.WIZARD,
        // ── Krok 1–2 ──────────────────────────────────────────────────────────
        val title: String = "",
        val description: String = "",
        val isRecording: Boolean = false,
        val recordingSeconds: Int = 0,
        // ── Krok 3 ────────────────────────────────────────────────────────────
        val subjectMode: SubjectMode = SubjectMode.CLIENT,
        val clientQuery: String = "",
        val clients: List<Client> = emptyList(),
        val selectedClient: Client? = null,
        val clientDeals: List<Deal> = emptyList(),
        val isLoadingDeals: Boolean = false,
        val selectedDealId: String? = null,
        val newContact: NewContact = NewContact(),
        val projects: List<TaskProject> = emptyList(),
        val isLoadingProjects: Boolean = false,
        val projectsError: String? = null,
        val selectedProjectId: String? = null,
        // ── Krok 4–7 ──────────────────────────────────────────────────────────
        val team: TaskTeam? = null,
        val members: List<TaskMember> = emptyList(),
        val isLoadingMembers: Boolean = false,
        /** Id zalogowanego użytkownika — po nim kafelek „Moje" znajduje jedyną osobę. */
        val selfId: String? = null,
        /** `null` = nikt jeszcze nie wybrany; osoby domyślnej celowo nie ma. */
        val assigneeId: String? = null,
        /** Świadomy wybór „bez przypisania" — odróżnia go od braku decyzji. */
        val assigneeCleared: Boolean = false,
        val priority: TaskPriority = TaskPriority.NORMAL,
        val dueAtMillis: Long? = null,
        // ── Zapis ─────────────────────────────────────────────────────────────
        val isSaving: Boolean = false,
        val error: String? = null,
    ) {
        /** Osoby pasujące do wybranego kafelka. */
        val teamMembers: List<TaskMember>
            get() = team?.membersFrom(members, selfId) ?: emptyList()

        /** Numer planszy pokazywany użytkownikowi; [WizardStep.DONE] jest poza licznikiem. */
        val stepNumber: Int get() = steps.indexOf(step) + 1

        val isFirstStep: Boolean get() = step == steps.firstOrNull()
        val isLastStep: Boolean get() = step == steps.lastOrNull()
    }

    private val _uiState = MutableStateFlow(
        UiState(
            step = if (isShort) WizardStep.TEAM else WizardStep.TITLE,
            steps = if (isShort) WizardStep.SHORT else WizardStep.WIZARD,
            title = defaultTitle(),
            description = presetNote.orEmpty(),
        ),
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var timerJob: Job? = null

    init {
        loadMembers()
        observeClients("")
        if (presetClientId != null) loadPresetClient(presetClientId)
    }

    /**
     * Podpina klienta ustalonego w kreatorze po rozmowie. Idzie przez
     * [onClientSelect], bo ono dociąga deale i samo wybiera ten jedyny — inaczej
     * zadanie powstałoby bez powiązania mimo znanego klienta.
     */
    private fun loadPresetClient(clientId: String) {
        viewModelScope.launch {
            try {
                onClientSelect(clientRepository.getClientById(clientId))
            } catch (_: Exception) {
                // Bez klienta kreator nadal działa — numer trafi do opisu zadania.
            }
        }
    }

    private fun defaultTitle(): String {
        val who = callerName ?: phone.ifBlank { null }
        return if (who != null) "Kontakt: $who" else ""
    }

    // ── Nawigacja ────────────────────────────────────────────────────────────

    /** Czy z bieżącego kroku wolno iść dalej. */
    fun canGoNext(state: UiState = _uiState.value): Boolean = when (state.step) {
        WizardStep.TITLE -> state.title.isNotBlank()
        WizardStep.TEAM -> state.team != null
        WizardStep.SUBJECT -> when (state.subjectMode) {
            SubjectMode.INTERNAL -> true
            SubjectMode.PROJECT -> state.selectedProjectId != null
            // Nowy kontakt idzie dalej dopiero z imieniem i nazwiskiem — inaczej
            // `POST /clients` odbiłby się o walidację dopiero przy zapisie.
            SubjectMode.CLIENT ->
                state.selectedClient != null || state.newContact.isComplete
        }
        else -> true
    }

    /** Czy krok wolno pominąć (przycisk „Pomiń"). */
    fun canSkip(state: UiState = _uiState.value): Boolean =
        state.step == WizardStep.DESCRIPTION ||
            (state.step == WizardStep.SUBJECT && state.subjectMode != SubjectMode.INTERNAL)

    fun next() {
        stopRecording()
        val state = _uiState.value
        val steps = state.steps
        val idx = steps.indexOf(state.step)
        if (idx < 0 || idx == steps.lastIndex) return
        val nextStep = steps[idx + 1]
        _uiState.update { it.copy(step = nextStep, error = null) }
        prepareStep(nextStep)
    }

    /** Pomija krok, czyszcząc to, co się na nim ustawia. */
    fun skip() {
        val state = _uiState.value
        if (state.step == WizardStep.SUBJECT) {
            _uiState.update {
                it.copy(
                    selectedClient = null,
                    selectedDealId = null,
                    clientDeals = emptyList(),
                    newContact = NewContact(),
                    selectedProjectId = null,
                    subjectMode = SubjectMode.INTERNAL,
                )
            }
        }
        next()
    }

    fun back() {
        stopRecording()
        val steps = _uiState.value.steps
        val idx = steps.indexOf(_uiState.value.step)
        if (idx <= 0) return
        _uiState.update { it.copy(step = steps[idx - 1], error = null) }
    }

    /** Doładowuje to, czego dany krok potrzebuje — dopiero gdy użytkownik na nim stanie. */
    private fun prepareStep(step: WizardStep) {
        if (step == WizardStep.SUBJECT &&
            _uiState.value.subjectMode == SubjectMode.PROJECT &&
            _uiState.value.projects.isEmpty()
        ) {
            loadProjects()
        }
    }

    // ── Krok 1–2 ─────────────────────────────────────────────────────────────

    fun onTitleChange(value: String) = _uiState.update { it.copy(title = value, error = null) }

    fun onDescriptionChange(value: String) = _uiState.update { it.copy(description = value) }

    fun toggleRecording() {
        if (_uiState.value.isRecording) stopRecording() else startRecording()
    }

    private fun startRecording() {
        if (!speechToText.isAvailable()) {
            _uiState.update {
                it.copy(error = "Rozpoznawanie mowy niedostępne — wpisz opis ręcznie")
            }
            return
        }
        // Dyktowanie dopisuje do tego, co już jest — poprzedni tekst zostaje.
        val prefix = _uiState.value.description.trim()
        speechToText.onText = { text ->
            val merged = if (prefix.isBlank()) text else "$prefix $text"
            _uiState.update { it.copy(description = merged) }
        }
        speechToText.onError = { message ->
            stopTimer()
            _uiState.update { it.copy(isRecording = false, error = message) }
        }
        speechToText.start()
        _uiState.update { it.copy(isRecording = true, recordingSeconds = 0, error = null) }
        timerJob = viewModelScope.launch {
            var seconds = 0
            while (true) {
                delay(1_000)
                seconds++
                _uiState.update { it.copy(recordingSeconds = seconds) }
            }
        }
    }

    private fun stopRecording() {
        if (!_uiState.value.isRecording) return
        stopTimer()
        speechToText.stop() // rozpoznany tekst wpadł już przez `onText`
        _uiState.update { it.copy(isRecording = false) }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    // ── Krok 3: kogo dotyczy ─────────────────────────────────────────────────

    fun onSubjectModeChange(mode: SubjectMode) {
        _uiState.update { it.copy(subjectMode = mode, error = null) }
        if (mode == SubjectMode.PROJECT && _uiState.value.projects.isEmpty()) loadProjects()
    }

    fun onClientQueryChange(query: String) {
        _uiState.update { it.copy(clientQuery = query) }
        observeClients(query)
    }

    private fun observeClients(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (query.isNotBlank()) delay(250) // odsapnięcie przy pisaniu
            clientRepository.getClients(query.ifBlank { null }).collect { list ->
                _uiState.update { it.copy(clients = list.take(MAX_CLIENT_RESULTS)) }
            }
        }
    }

    fun onClientSelect(client: Client?) {
        if (client == null || _uiState.value.selectedClient?.id == client.id) {
            _uiState.update {
                it.copy(selectedClient = null, clientDeals = emptyList(), selectedDealId = null)
            }
            return
        }
        _uiState.update {
            it.copy(
                selectedClient = client,
                clientDeals = emptyList(),
                selectedDealId = null,
                isLoadingDeals = true,
            )
        }
        viewModelScope.launch {
            try {
                val deals = dealRepository.getDeals().filter { it.clientId == client.id }
                _uiState.update {
                    it.copy(
                        clientDeals = deals,
                        isLoadingDeals = false,
                        // Jeden deal wybieramy sami; przy kilku pytamy, przy zerze
                        // zadanie powstanie bez powiązania.
                        selectedDealId = deals.singleOrNull()?.id,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoadingDeals = false, clientDeals = emptyList(), error = friendlyError(e))
                }
            }
        }
    }

    fun onDealSelect(dealId: String?) = _uiState.update { it.copy(selectedDealId = dealId) }

    fun onNewContactChange(contact: NewContact) =
        _uiState.update { it.copy(newContact = contact, error = null) }

    fun onProjectSelect(projectId: String?) = _uiState.update {
        it.copy(selectedProjectId = if (it.selectedProjectId == projectId) null else projectId)
    }

    private fun loadProjects() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingProjects = true, projectsError = null) }
            try {
                val projects = taskRepository.getProjects()
                _uiState.update { it.copy(projects = projects, isLoadingProjects = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingProjects = false,
                        projectsError = when {
                            e is retrofit2.HttpException && e.code() == 403 ->
                                "Brak uprawnień do projektów — wybierz klienta albo pomiń ten krok"
                            else -> friendlyError(e)
                        },
                    )
                }
            }
        }
    }

    // ── Krok 4–7 ─────────────────────────────────────────────────────────────

    fun onTeamChange(team: TaskTeam) = _uiState.update {
        // Zmiana kafelka unieważnia wybraną osobę — mogła być z innego działu.
        it.copy(team = team, assigneeId = null, assigneeCleared = false)
    }

    fun onAssigneeChange(id: String?) = _uiState.update {
        it.copy(assigneeId = id, assigneeCleared = id == null)
    }

    fun onPriorityChange(priority: TaskPriority) = _uiState.update { it.copy(priority = priority) }

    fun onDueAtChange(millis: Long?) = _uiState.update { it.copy(dueAtMillis = millis) }

    private fun loadMembers() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMembers = true) }
            try {
                val userId = sessionPreferences.session.first()?.userId
                val members = taskRepository.getMembers()
                _uiState.update {
                    it.copy(members = members, selfId = userId, isLoadingMembers = false)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingMembers = false,
                        error = e.message ?: "Nie udało się pobrać listy pracowników",
                    )
                }
            }
        }
    }

    // ── Zapis ────────────────────────────────────────────────────────────────

    fun createTask() {
        val state = _uiState.value
        val title = state.title.trim()
        if (title.isBlank()) {
            // W skrócie po rozmowie kroku z tytułem nie ma — zostajemy na miejscu
            // z komunikatem, zamiast skakać na planszę spoza przebiegu.
            val titleStep = state.steps.firstOrNull { it == WizardStep.TITLE } ?: state.step
            _uiState.update { it.copy(error = "Tytuł jest wymagany", step = titleStep) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                // Ręcznie wpisany kontakt zakłada klienta w kartotece; zadanie idzie
                // wtedy bez powiązania, bo świeży klient nie ma jeszcze deala.
                val createdClient = createContactIfNeeded(state)
                taskRepository.createTask(
                    title = title,
                    description = buildDescription(state, createdClient),
                    assigneeId = state.assigneeId,
                    dueAt = state.dueAtMillis?.let(::toIsoDate),
                    priority = state.priority,
                    link = linkFor(state),
                )
                _uiState.update { it.copy(isSaving = false, step = WizardStep.DONE) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = friendlyError(e)) }
            }
        }
    }

    private suspend fun createContactIfNeeded(state: UiState): Client? {
        if (state.subjectMode != SubjectMode.CLIENT) return null
        if (state.selectedClient != null) return null
        val contact = state.newContact
        if (!contact.isComplete) return null
        return clientRepository.createClient(
            NewClient(
                firstName = contact.firstName.trim(),
                lastName = contact.lastName.trim(),
                phone = contact.phone.trim().ifBlank { null },
                email = contact.email.trim().ifBlank { null },
            )
        )
    }

    private fun linkFor(state: UiState): TaskLink = when (state.subjectMode) {
        SubjectMode.PROJECT -> state.selectedProjectId?.let(TaskLink::Project) ?: TaskLink.None
        SubjectMode.CLIENT -> state.selectedDealId?.let(TaskLink::Deal) ?: TaskLink.None
        SubjectMode.INTERNAL -> TaskLink.None
    }

    /**
     * Opis wysyłany na serwer. Doklejamy to, czego zadanie nie potrafi udźwignąć
     * w polach: numer z połączenia i klienta bez deala (zadanie nie ma `clientId`).
     */
    private fun buildDescription(state: UiState, createdClient: Client?): String? {
        val lines = mutableListOf<String>()
        state.description.trim().takeIf { it.isNotBlank() }?.let(lines::add)

        val clientWithoutDeal = when {
            createdClient != null -> createdClient
            state.subjectMode == SubjectMode.CLIENT && state.selectedDealId == null ->
                state.selectedClient
            else -> null
        }
        clientWithoutDeal?.let { c ->
            val phoneSuffix = c.primaryPhone?.let { ", tel. $it" } ?: ""
            lines += "Klient: ${c.displayName}$phoneSuffix"
        }
        phone.takeIf { it.isNotBlank() }?.let { lines += "Telefon z połączenia: $it" }
        return lines.joinToString("\n\n").ifBlank { null }
    }

    /** Etykieta powiązania do podsumowania — bez tego ekran musiałby liczyć to sam. */
    fun subjectLabel(state: UiState = _uiState.value): String = when (state.subjectMode) {
        SubjectMode.INTERNAL -> "Zadanie wewnętrzne"
        SubjectMode.PROJECT ->
            state.projects.firstOrNull { it.id == state.selectedProjectId }?.name
                ?: "Bez powiązania"
        SubjectMode.CLIENT -> {
            val client = state.selectedClient?.displayName
                ?: state.newContact.displayName.ifBlank { null }
            val deal = state.clientDeals.firstOrNull { it.id == state.selectedDealId }
            when {
                client != null && deal != null -> "$client → ${dealLabel(deal)}"
                client != null -> client
                else -> "Bez powiązania"
            }
        }
    }

    /** Deal na liście: nazwa własna, a gdy jej brak — etap lejka. */
    fun dealLabel(deal: Deal): String =
        deal.projectName?.takeIf { it.isNotBlank() } ?: deal.stage.label

    private fun friendlyError(e: Throwable): String = when (e) {
        is retrofit2.HttpException -> when (e.code()) {
            401 -> "Sesja wygasła — zaloguj się ponownie"
            403 -> "Brak uprawnień do tej operacji"
            422 -> "Nieprawidłowe dane zadania"
            in 500..599 -> "Błąd serwera (${e.code()}) — spróbuj ponownie"
            else -> "Nie udało się utworzyć zadania (kod ${e.code()})"
        }
        is java.io.IOException -> "Brak połączenia z serwerem"
        else -> e.message ?: "Nie udało się utworzyć zadania"
    }

    private fun toIsoDate(millis: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date(millis))
    }

    override fun onCleared() {
        stopTimer()
        speechToText.cancel()
        super.onCleared()
    }

    companion object {
        /** Wartość argumentu `mode` włączająca skrócony kreator (po rozmowie). */
        const val MODE_SHORT = "short"

        /** Lista klientów w kroku 3 — tyle mieści się bez przewijania w nieskończoność. */
        private const val MAX_CLIENT_RESULTS = 25
    }
}
