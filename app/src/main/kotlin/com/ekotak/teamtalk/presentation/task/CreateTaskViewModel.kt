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
import com.ekotak.teamtalk.domain.search.matchesQuery
import com.ekotak.teamtalk.domain.search.similarTo
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

/**
 * Pole kreatora, do którego akurat mówi użytkownik. Rozpoznawanie mowy obsługuje
 * jedną sesję naraz, więc w stanie trzymamy jedno pole zamiast flagi przy każdym.
 */
enum class VoiceField {
    TITLE,
    DESCRIPTION,
    CLIENT,
    PROJECT,
    PERSON,
    CONTACT_FIRST_NAME,
    CONTACT_LAST_NAME,
}

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
        /** Pole, do którego trwa dyktowanie; `null` = mikrofon wyłączony. */
        val voiceField: VoiceField? = null,
        val recordingSeconds: Int = 0,
        // ── Krok 3 ────────────────────────────────────────────────────────────
        val subjectMode: SubjectMode = SubjectMode.CLIENT,
        /**
         * Szukanie „po wszystkim" — włącza je mikrofon przy wyszukiwarce.
         * Mówiący nie wie, czy to, czego szuka, siedzi w kartotece, czy wśród
         * projektów, więc podyktowane hasło leci w oba rodzaje naraz.
         */
        val crossSearch: Boolean = false,
        val clientQuery: String = "",
        val clients: List<Client> = emptyList(),
        /** Cała kartoteka z pamięci telefonu — po niej szukamy podobnych nazwisk. */
        val allClients: List<Client> = emptyList(),
        /**
         * Hasła, przy których użytkownik odpowiedział „to ktoś nowy". Trzymamy
         * treść, a nie samo „tak/nie", żeby pytanie wróciło samo, gdy wpisze
         * coś innego.
         */
        val dismissedSuggestion: String? = null,
        val selectedClient: Client? = null,
        val clientDeals: List<Deal> = emptyList(),
        val isLoadingDeals: Boolean = false,
        val selectedDealId: String? = null,
        val newContact: NewContact = NewContact(),
        val projects: List<TaskProject> = emptyList(),
        val projectQuery: String = "",
        val isLoadingProjects: Boolean = false,
        val projectsError: String? = null,
        val selectedProjectId: String? = null,
        // ── Krok 4–7 ──────────────────────────────────────────────────────────
        val team: TaskTeam? = null,
        val members: List<TaskMember> = emptyList(),
        val personQuery: String = "",
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

        /** Czy trwa dyktowanie do wskazanego pola. */
        fun isListening(field: VoiceField): Boolean = voiceField == field

        /**
         * Nazwy, do których szukamy podobnych: hasło z wyszukiwarki i to, co
         * użytkownik zdążył wpisać w nowym kontakcie. Oba naraz, bo połowa
         * duplikatów powstaje tak, że ktoś nie znalazł osoby w kartotece
         * i zaczął ją wpisywać ręcznie tuż pod spodem.
         */
        val suggestionProbes: List<String>
            get() = listOf(clientQuery, newContact.displayName).filter { it.isNotBlank() }

        /** Klucz odpowiedzi „to ktoś nowy" — zmiana hasła przywraca pytanie. */
        val suggestionKey: String get() = suggestionProbes.joinToString("|")

        /**
         * Wpisy z kartoteki na tyle podobne do wpisanej nazwy, że trzeba
         * zapytać, czy nie chodzi o którąś z nich. Pytamy tylko wtedy, gdy
         * wyszukiwarka nic nie znalazła albo ktoś zakłada nowy kontakt —
         * przy trafieniach lista i tak stoi obok.
         */
        val similarClients: List<Client>
            get() {
                if (suggestionKey.isBlank() || dismissedSuggestion == suggestionKey) return emptyList()
                if (clients.isNotEmpty() && newContact.displayName.isBlank()) return emptyList()
                return allClients.similarTo(
                    probes = suggestionProbes,
                    exclude = clients.map { it.id }.toSet() + setOfNotNull(selectedClient?.id),
                )
            }

        /**
         * Projekty po zawężeniu wyszukiwarką. Wybrany zostaje na liście nawet gdy
         * nie pasuje do frazy — inaczej znikałby razem z powodem, dla którego
         * przycisk „Dalej" jest aktywny.
         */
        val visibleProjects: List<TaskProject>
            get() = projects.filter {
                it.id == selectedProjectId || it.name.matchesQuery(projectQuery)
            }

        /**
         * Projekty pasujące do dyktowanego hasła. Bez wyjątku dla wybranego —
         * to lista wyników szukania, a nie lista do wyboru.
         */
        val crossProjects: List<TaskProject>
            get() = if (clientQuery.isBlank()) emptyList()
            else projects.filter { it.name.matchesQuery(clientQuery) }

        /** Osoby z kafelka po zawężeniu wyszukiwarką (wybrana zawsze widoczna). */
        val visibleMembers: List<TaskMember>
            get() = teamMembers.filter {
                it.id == assigneeId ||
                    it.displayName.matchesQuery(personQuery) ||
                    it.email.matchesQuery(personQuery)
            }

        /** Numer planszy pokazywany użytkownikowi; [WizardStep.DONE] jest poza licznikiem. */
        val stepNumber: Int get() = steps.indexOf(step) + 1

        val isFirstStep: Boolean get() = step == steps.firstOrNull()
        val isLastStep: Boolean get() = step == steps.lastOrNull()
    }

    /** Komplet plansz tego przebiegu, zanim kafelek zespołu którąś z nich zdejmie. */
    private val baseSteps: List<WizardStep> = if (isShort) WizardStep.SHORT else WizardStep.WIZARD

    /**
     * Plansze do pokazania przy danym kafelku. „Moje" nie pyta o wykonawcę —
     * zadanie idzie na twórcę, więc krok z osobą znika. Zdejmujemy go dopiero
     * gdy znamy [selfId]; bez niego nie mielibyśmy kogo wpisać i pytanie o osobę
     * nadal ma sens.
     */
    private fun stepsFor(team: TaskTeam?, selfId: String?): List<WizardStep> =
        if (team == TaskTeam.MOJE && selfId != null) baseSteps - WizardStep.PERSON else baseSteps

    private val _uiState = MutableStateFlow(
        UiState(
            step = if (isShort) WizardStep.TEAM else WizardStep.TITLE,
            steps = baseSteps,
            title = defaultTitle(),
            description = presetNote.orEmpty(),
        ),
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var timerJob: Job? = null

    init {
        loadMembers()
        observeAllClients()
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
        stopVoice()
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
                    projectQuery = "",
                    subjectMode = SubjectMode.INTERNAL,
                    crossSearch = false,
                )
            }
        }
        next()
    }

    fun back() {
        stopVoice()
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

    // ── Dyktowanie ───────────────────────────────────────────────────────────

    /** Włącza dyktowanie do wskazanego pola albo je kończy, gdy już trwa. */
    fun toggleVoice(field: VoiceField) {
        if (_uiState.value.voiceField == field) stopVoice() else startVoice(field)
    }

    private fun startVoice(field: VoiceField) {
        // Rozpoznawanie ma jedną sesję — sięgając po mikrofon w innym polu,
        // kończymy poprzednie dyktowanie zamiast startować drugie naraz.
        stopVoice()
        if (!speechToText.isAvailable()) {
            _uiState.update {
                it.copy(error = "Rozpoznawanie mowy niedostępne — wpisz tekst ręcznie")
            }
            return
        }
        // Opis dopisuje się do tego, co już jest; krótkie pola nadpisujemy w całości.
        val prefix =
            if (field == VoiceField.DESCRIPTION) _uiState.value.description.trim() else ""
        // Mikrofon przy wyszukiwarce szuka od razu we wszystkich rodzajach —
        // stare hasło znika, żeby wyniki nie mieszały się z poprzednią próbą.
        if (field == VoiceField.CLIENT || field == VoiceField.PROJECT) {
            _uiState.update { it.copy(crossSearch = true, clientQuery = "", projectQuery = "") }
            if (_uiState.value.projects.isEmpty()) loadProjects()
        }
        speechToText.onText = { text -> applyVoiceText(field, prefix, text) }
        speechToText.onError = { message ->
            stopTimer()
            _uiState.update { it.copy(voiceField = null, error = message) }
        }
        speechToText.onDone = {
            stopTimer()
            _uiState.update { it.copy(voiceField = null) }
        }
        // Ciągiem dyktuje się tylko opis. Przy szukaniu jedna wypowiedź kończy
        // sesję — mikrofon nie ma prawa zostać włączony po nazwisku klienta.
        speechToText.start(continuous = field == VoiceField.DESCRIPTION)
        _uiState.update { it.copy(voiceField = field, recordingSeconds = 0, error = null) }
        if (field == VoiceField.DESCRIPTION) startTimer()
    }

    private fun startTimer() {
        timerJob = viewModelScope.launch {
            var seconds = 0
            while (true) {
                delay(1_000)
                seconds++
                _uiState.update { it.copy(recordingSeconds = seconds) }
            }
        }
    }

    /** Rozpoznany tekst trafia do pola, z którego uruchomiono mikrofon. */
    private fun applyVoiceText(field: VoiceField, prefix: String, text: String) {
        when (field) {
            VoiceField.DESCRIPTION -> {
                val merged = if (prefix.isBlank()) text else "$prefix $text"
                _uiState.update { it.copy(description = merged) }
            }
            VoiceField.TITLE -> onTitleChange(asSentence(text))
            // Obie wyszukiwarki dyktuje się tak samo — hasło idzie w oba rodzaje.
            VoiceField.CLIENT, VoiceField.PROJECT -> onClientQueryChange(asQuery(text))
            VoiceField.PERSON -> onPersonQueryChange(asQuery(text))
            VoiceField.CONTACT_FIRST_NAME ->
                onNewContactChange(_uiState.value.newContact.copy(firstName = asName(text)))
            VoiceField.CONTACT_LAST_NAME ->
                onNewContactChange(_uiState.value.newContact.copy(lastName = asName(text)))
        }
    }

    private fun stopVoice() {
        if (_uiState.value.voiceField == null) return
        stopTimer()
        speechToText.stop() // rozpoznany tekst wpadł już przez `onText`
        _uiState.update { it.copy(voiceField = null) }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    /**
     * Fraza wyszukiwania z dyktowania. Rozpoznawanie lubi dokleić kropkę albo
     * znak zapytania — w zapytaniu do kartoteki nie znalazłyby nic.
     */
    private fun asQuery(text: String): String = text.trim().trimEnd('.', ',', '!', '?').trim()

    /** Imię/nazwisko z dyktowania — każdy człon wielką literą. */
    private fun asName(text: String): String = asQuery(text)
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { part -> part.replaceFirstChar { it.uppercaseChar() } }

    /** Tytuł z dyktowania — wielka litera na początku, reszta jak powiedziana. */
    private fun asSentence(text: String): String =
        asQuery(text).replaceFirstChar { it.uppercaseChar() }

    // ── Krok 3: kogo dotyczy ─────────────────────────────────────────────────

    fun onSubjectModeChange(mode: SubjectMode) {
        // Zakładka zmienia pole, do którego szło dyktowanie — mikrofon gasimy.
        stopVoice()
        // Ręczny wybór zakładki kończy szukanie po wszystkim: użytkownik właśnie
        // powiedział, w którym rodzaju to siedzi.
        _uiState.update { it.copy(subjectMode = mode, crossSearch = false, error = null) }
        if (mode == SubjectMode.PROJECT && _uiState.value.projects.isEmpty()) loadProjects()
    }

    /**
     * Fraza wyszukiwarki. W trybie „po wszystkim" jedno pole obsługuje oba
     * rodzaje, więc trzymamy je zgodne; wyczyszczenie pola kończy ten tryb.
     */
    fun onClientQueryChange(query: String) {
        _uiState.update {
            it.copy(
                clientQuery = query,
                projectQuery = if (it.crossSearch) query else it.projectQuery,
                crossSearch = it.crossSearch && query.isNotBlank(),
            )
        }
        observeClients(query)
    }

    /**
     * Cała kartoteka, przez cały czas trwania kreatora — z niej biorą się
     * podobne nazwiska i lista przy pustej wyszukiwarce. Osobno od [searchJob],
     * bo tamten gaśnie przy każdej literze.
     */
    private fun observeAllClients() {
        viewModelScope.launch {
            clientRepository.getClients(null).collect { list ->
                _uiState.update {
                    it.copy(
                        allClients = list,
                        // Przy pustym haśle „wyniki" to po prostu kartoteka.
                        clients = if (it.clientQuery.isBlank()) list.take(MAX_CLIENT_RESULTS) else it.clients,
                    )
                }
            }
        }
    }

    private fun observeClients(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _uiState.update { it.copy(clients = it.allClients.take(MAX_CLIENT_RESULTS)) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(250) // odsapnięcie przy pisaniu
            clientRepository.getClients(query).collect { list ->
                _uiState.update { it.copy(clients = list.take(MAX_CLIENT_RESULTS)) }
            }
        }
    }

    /**
     * „Tak, to ten klient" — podpowiedź zastępuje wpisywany kontakt, żeby
     * zapis nie założył duplikatu tuż obok istniejącej karty.
     */
    fun onSuggestionAccept(client: Client) {
        _uiState.update {
            it.copy(
                crossSearch = false,
                subjectMode = SubjectMode.CLIENT,
                selectedProjectId = null,
                clientQuery = client.displayName,
                newContact = NewContact(),
                dismissedSuggestion = null,
            )
        }
        observeClients(client.displayName)
        onClientSelect(client)
    }

    /** „Nie, to ktoś nowy" — chowa pytanie do czasu zmiany wpisanej nazwy. */
    fun onSuggestionDismiss() =
        _uiState.update { it.copy(dismissedSuggestion = it.suggestionKey) }

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

    fun onProjectQueryChange(query: String) = _uiState.update { it.copy(projectQuery = query) }

    fun onProjectSelect(projectId: String?) = _uiState.update {
        it.copy(selectedProjectId = if (it.selectedProjectId == projectId) null else projectId)
    }

    // ── Wyniki szukania po wszystkich rodzajach ──────────────────────────────
    // Kliknięcie w wynik samo przestawia zakładkę: użytkownik powiedział, czego
    // szuka, a nie gdzie to leży.

    fun onCrossClientSelect(client: Client) {
        _uiState.update {
            it.copy(crossSearch = false, subjectMode = SubjectMode.CLIENT, selectedProjectId = null)
        }
        onClientSelect(client)
    }

    fun onCrossProjectSelect(projectId: String) = _uiState.update {
        it.copy(
            crossSearch = false,
            subjectMode = SubjectMode.PROJECT,
            selectedProjectId = projectId,
            selectedClient = null,
            clientDeals = emptyList(),
            selectedDealId = null,
            newContact = NewContact(),
        )
    }

    fun onCrossInternalSelect() = _uiState.update {
        it.copy(
            crossSearch = false,
            subjectMode = SubjectMode.INTERNAL,
            selectedProjectId = null,
            selectedClient = null,
            clientDeals = emptyList(),
            selectedDealId = null,
            newContact = NewContact(),
        )
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
        // Fraza wyszukiwania też idzie do kosza, bo dotyczyła poprzedniej listy.
        // Wyjątek to „Moje": wykonawcą jest twórca zadania, więc wpisujemy go od
        // razu i nie pytamy o osobę — plansza PERSON wypada z kreatora.
        it.copy(
            team = team,
            assigneeId = if (team == TaskTeam.MOJE) it.selfId else null,
            assigneeCleared = false,
            personQuery = "",
            steps = stepsFor(team, it.selfId),
        )
    }

    fun onPersonQueryChange(query: String) = _uiState.update { it.copy(personQuery = query) }

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
                    // W skrócie po rozmowie kafelki są pierwszą planszą, więc „Moje"
                    // może paść, zanim poznamy zalogowanego. Uzupełniamy wtedy
                    // wykonawcę i dopiero teraz zdejmujemy planszę z osobą.
                    val mine = it.team == TaskTeam.MOJE
                    it.copy(
                        members = members,
                        selfId = userId,
                        isLoadingMembers = false,
                        assigneeId = if (mine && it.assigneeId == null) userId else it.assigneeId,
                        // Planszy, na której użytkownik właśnie stoi, nie wolno wyjąć
                        // spod niego — zniknęłaby z listy i nawigacja nie miałaby
                        // od czego liczyć następnego kroku.
                        steps = if (it.step == WizardStep.PERSON) it.steps
                        else stepsFor(it.team, userId),
                    )
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
