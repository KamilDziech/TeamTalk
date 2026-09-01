package com.ekotak.teamtalk.presentation.crm

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.domain.model.ArticleGate
import com.ekotak.teamtalk.domain.model.AssistantMessage
import com.ekotak.teamtalk.domain.model.CategoryNode
import com.ekotak.teamtalk.domain.model.Client
import com.ekotak.teamtalk.domain.model.ClientDraft
import com.ekotak.teamtalk.domain.model.DealBuildingKind
import com.ekotak.teamtalk.domain.model.DealDetail
import com.ekotak.teamtalk.domain.model.DealDraft
import com.ekotak.teamtalk.domain.model.DealStage
import com.ekotak.teamtalk.domain.model.InstallationStage
import com.ekotak.teamtalk.domain.model.KnowledgeArticle
import com.ekotak.teamtalk.domain.model.LeadIntake
import com.ekotak.teamtalk.domain.model.MeetingKind
import com.ekotak.teamtalk.domain.model.TaskMember
import com.ekotak.teamtalk.domain.model.ancestorsOfSelected
import com.ekotak.teamtalk.domain.model.buildCategoryTree
import com.ekotak.teamtalk.domain.model.hasChangesFrom
import com.ekotak.teamtalk.domain.model.nextStages
import com.ekotak.teamtalk.domain.model.toDraft
import com.ekotak.teamtalk.domain.repository.AuthRepository
import com.ekotak.teamtalk.domain.repository.TaskRepository
import com.ekotak.teamtalk.domain.usecase.calllog.MakeCallUseCase
import com.ekotak.teamtalk.domain.usecase.client.UpdateClientUseCase
import com.ekotak.teamtalk.domain.usecase.deal.AddDealCompanionUseCase
import com.ekotak.teamtalk.domain.usecase.deal.AskDealAssistantUseCase
import com.ekotak.teamtalk.domain.usecase.deal.ChangeDealStageUseCase
import com.ekotak.teamtalk.domain.usecase.deal.GetArticleGateUseCase
import com.ekotak.teamtalk.domain.usecase.deal.GetCategoriesUseCase
import com.ekotak.teamtalk.domain.usecase.deal.GetDealCompanionsUseCase
import com.ekotak.teamtalk.domain.usecase.deal.GetDealDetailUseCase
import com.ekotak.teamtalk.domain.usecase.deal.GetDealInstallationsUseCase
import com.ekotak.teamtalk.domain.usecase.deal.GetKnowledgeArticlesUseCase
import com.ekotak.teamtalk.domain.usecase.deal.SendArticleToClientUseCase
import com.ekotak.teamtalk.domain.usecase.deal.SetDealInstallationsUseCase
import com.ekotak.teamtalk.domain.usecase.deal.GetLeadIntakeUseCase
import com.ekotak.teamtalk.domain.usecase.deal.UpdateLeadNoteUseCase
import com.ekotak.teamtalk.domain.usecase.deal.RemoveDealCompanionUseCase
import com.ekotak.teamtalk.domain.usecase.deal.SetPrimaryDealContactUseCase
import com.ekotak.teamtalk.domain.usecase.deal.UpdateDealUseCase
import com.ekotak.teamtalk.domain.usecase.client.GetClientsUseCase
import com.ekotak.teamtalk.domain.usecase.client.NavigateToClientUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Uprawnienie board360 wymagane do zmiany etapu, edycji karty i kontaktów.
 * `internal`, bo ten sam warunek gate-uje ekran artykułu wiedzy — dwa wpisane
 * z ręki stringi rozjechałyby się przy pierwszej zmianie nazwy uprawnienia.
 */
internal const val PERMISSION_DEAL_MANAGE = "deal.manage"

/** Odstęp między znakiem a zapytaniem do kartoteki przy szukaniu kontaktu. */
private const val CONTACT_SEARCH_DEBOUNCE_MS = 250L

/**
 * Karta deala — odpowiednik `DealDrawer` z panelu. Uprawnienia czytamy z
 * `GET /api/me` przy wejściu: sesja w DataStore ich nie trzyma, a i tak chcemy
 * świeże (rola mogła się zmienić w panelu). Brak `deal.manage` chowa akcje;
 * autorytatywnym gate'em zostaje API.
 *
 * Edycja jest inline w zakładce, tak jak w web: „Edytuj" przełącza karty w tryb
 * formularza, „Zapisz" wysyła jedno `PATCH` deala i — gdy trzeba — drugie na
 * kartotekę klienta. Zakładka „Dane" edytuje bowiem oba rekordy naraz, bo
 * z punktu widzenia handlowca to jedna karta.
 */
@HiltViewModel
class DealDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getDealDetailUseCase: GetDealDetailUseCase,
    private val changeDealStageUseCase: ChangeDealStageUseCase,
    private val updateDealUseCase: UpdateDealUseCase,
    private val updateClientUseCase: UpdateClientUseCase,
    private val getCompanionsUseCase: GetDealCompanionsUseCase,
    private val addCompanionUseCase: AddDealCompanionUseCase,
    private val removeCompanionUseCase: RemoveDealCompanionUseCase,
    private val setPrimaryContactUseCase: SetPrimaryDealContactUseCase,
    private val askAssistantUseCase: AskDealAssistantUseCase,
    private val getLeadIntakeUseCase: GetLeadIntakeUseCase,
    private val updateLeadNoteUseCase: UpdateLeadNoteUseCase,
    private val getDealInstallationsUseCase: GetDealInstallationsUseCase,
    private val setDealInstallationsUseCase: SetDealInstallationsUseCase,
    private val getCategoriesUseCase: GetCategoriesUseCase,
    private val getKnowledgeArticlesUseCase: GetKnowledgeArticlesUseCase,
    private val getArticleGateUseCase: GetArticleGateUseCase,
    private val sendArticleToClientUseCase: SendArticleToClientUseCase,
    private val getClientsUseCase: GetClientsUseCase,
    private val navigateToClientUseCase: NavigateToClientUseCase,
    private val authRepository: AuthRepository,
    private val taskRepository: TaskRepository,
    private val makeCallUseCase: MakeCallUseCase,
) : ViewModel() {

    private val dealId: String = savedStateHandle["dealId"] ?: ""

    /**
     * Surowy tekst pól liczbowych zakładki „Dane". Osobno od draftu, bo w
     * trakcie pisania („1", „12", „") wartość bywa niesparsowalna — gdyby pole
     * czytało liczbę z draftu, znaki znikałyby użytkownikowi spod palca.
     */
    data class NumberText(
        val people: String = "",
        val areaM2: String = "",
        val floors: String = "",
    )

    /**
     * Zakładka „LEAD": zgłoszenie z leadowni i migawka instalacji. Dociągana
     * dopiero przy wejściu w zakładkę — to cztery dodatkowe zapytania, a większość
     * wejść w kartę kończy się na „Dane".
     *
     * `intake == null` przy `loaded == true` to normalny stan: deal wpisany
     * ręcznie w panelu nie ma zgłoszenia z leadowni.
     */
    data class LeadState(
        val isLoading: Boolean = false,
        val loaded: Boolean = false,
        val intake: LeadIntake? = null,
        /** Katalog technologii jako drzewo; pusty = katalogu nie udało się wczytać. */
        val catalog: List<CategoryNode> = emptyList(),
        /** Zaznaczone węzły migawki „lead"; `null` = odczyt się nie udał. */
        val selectedInstallations: Set<String>? = null,
        /** Rozwinięte gałęzie drzewa — stan widoku, ale przeżywa obrót ekranu. */
        val expanded: Set<String> = emptySet(),
        /** Czy API pozwala zmieniać migawkę tego etapu (`editable` z odpowiedzi). */
        val installationsEditable: Boolean = false,
        val isSavingInstallations: Boolean = false,
        /** Artykuły wiedzy deala po `categoryId` — kafel pokazuje ich stan. */
        val articles: Map<String, KnowledgeArticle> = emptyMap(),
        /** Bramka generowania artykułu (etap + komplet danych budynku). */
        val articleGate: ArticleGate = ArticleGate(),
        /** Instalacja, dla której trwa potwierdzanie wysyłki artykułu. */
        val sendingArticleFor: String? = null,
        val isSendingArticle: Boolean = false,
        val noteDraft: String = "",
        val savedNote: String = "",
        val isSavingNote: Boolean = false,
        val error: String? = null,
    ) {
        val isNoteDirty: Boolean get() = noteDraft.trim() != savedNote.trim()

        /**
         * Zaznaczone instalacje w kolejności katalogu, ze ścieżką nazw. Kafle
         * artykułu wiedzy idą właśnie tą listą, więc kolejność musi być ta sama
         * co w drzewie wyżej — inaczej przy kilku instalacjach kafle skakałyby
         * względem gałęzi, z których wyrosły.
         */
        val selectedPaths: List<SelectedInstallation>
            get() {
                val selected = selectedInstallations.orEmpty()
                val out = ArrayList<SelectedInstallation>()

                fun walk(node: CategoryNode, path: List<String>) {
                    val here = path + node.name
                    if (node.id in selected) {
                        out += SelectedInstallation(node.id, here.joinToString(" › "))
                    }
                    node.children.forEach { walk(it, here) }
                }

                catalog.forEach { walk(it, emptyList()) }
                // Węzeł skasowany z katalogu po zapisaniu migawki nie ma ścieżki —
                // pokazujemy surowe id, żeby wybór nie zniknął bez śladu.
                val known = out.mapTo(HashSet()) { it.categoryId }
                return out + selected.filter { it !in known }.map { SelectedInstallation(it, it) }
            }
    }

    /** Jedna zaznaczona instalacja: id węzła i jego ścieżka w katalogu. */
    data class SelectedInstallation(val categoryId: String, val pathLabel: String)

    /** Wątek asystenta karty. Historia żyje tylko w pamięci ekranu. */
    data class AssistantState(
        val messages: List<AssistantMessage> = emptyList(),
        val isAsking: Boolean = false,
        /** `false` = serwer bez klucza LLM; odpowiedź jest informacyjna. */
        val configured: Boolean = true,
    )

    data class UiState(
        val isLoading: Boolean = true,
        val isSaving: Boolean = false,
        val detail: DealDetail? = null,
        val canManage: Boolean = false,
        val error: String? = null,
        /** Komunikat operacji (błąd / potwierdzenie) do snackbara. */
        val message: String? = null,
        val tab: DealTab = DealTab.DANE,
        val editing: Boolean = false,
        val dealDraft: DealDraft = DealDraft(),
        val clientDraft: ClientDraft? = null,
        val numbers: NumberText = NumberText(),
        val members: List<TaskMember> = emptyList(),
        val assistant: AssistantState = AssistantState(),
        val lead: LeadState = LeadState(),
    ) {
        /** Etapy, na które wolno przejść z bieżącego (maszyna stanów board360). */
        val availableStages: List<DealStage>
            get() = detail?.deal?.stage?.let(::nextStages).orEmpty()

        /**
         * Czy formularz różni się od zapisanego stanu — steruje przyciskiem
         * „Zapisz" i ostrzeżeniem przy wyjściu z edycji.
         */
        val isDirty: Boolean
            get() {
                val deal = detail?.deal ?: return false
                if (deal.hasChangesFrom(dealDraft)) return true
                val client = detail.client ?: return false
                return clientDraft != null && client.toDraft() != clientDraft
            }

        /** Etap domknięcia procesu — stopka pokazuje wtedy zieloną akcję. */
        val canComplete: Boolean
            get() = canManage && detail?.deal?.stage == DealStage.FERTIG

        /** Deal wciąż w grze — można go oznaczyć jako stracony. */
        val canMarkLost: Boolean
            get() = canManage && detail?.deal?.stage?.let {
                it != DealStage.LOST && it != DealStage.FERTIG && it != DealStage.ZAKONCZONY
            } == true
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /**
     * Wyszukiwarka kartoteki dla okna „dodaj kontakt". Kontakt towarzyszący to
     * zawsze istniejący rekord kartoteki, więc zamiast formularza dajemy szukanie
     * po tej samej liście, którą telefon i tak trzyma w cache Room.
     */
    private val contactQuery = MutableStateFlow("")

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val contactCandidates: StateFlow<List<Client>> = contactQuery
        .debounce(CONTACT_SEARCH_DEBOUNCE_MS)
        .flatMapLatest { query -> getClientsUseCase(query.takeIf { it.isNotBlank() }) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun onContactQueryChange(query: String) {
        contactQuery.value = query
    }

    init {
        load()
    }

    /**
     * @param silent odświeżenie po udanej akcji — bez spinnera, żeby karta nie
     *   migała pustą treścią tuż po tym, jak użytkownik zobaczył nowy etap.
     */
    fun load(silent: Boolean = false) {
        viewModelScope.launch {
            if (!silent) _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val detail = getDealDetailUseCase(dealId)
                _uiState.update { state ->
                    val fresh = detail.deal.toDraft()
                    state.copy(
                        isLoading = false,
                        detail = detail,
                        error = null,
                        // Odświeżenie nie może skasować niezapisanego formularza —
                        // drafty podmieniamy tylko poza trybem edycji. Zapis sam
                        // wychodzi z edycji, zanim zawoła `load`.
                        dealDraft = if (state.editing) state.dealDraft else fresh,
                        clientDraft = if (state.editing) state.clientDraft else detail.client?.toDraft(),
                        numbers = if (state.editing) state.numbers else fresh.toNumberText(),
                    )
                }
                loadCompanions()
                // Zakładka „LEAD" raz wczytana ma być tak samo świeża jak reszta
                // karty — instalacje i notatka mogły się zmienić w panelu.
                if (_uiState.value.lead.loaded) loadLead(force = true)
            } catch (e: Exception) {
                val text = crmErrorMessage(e, "Nie udało się wczytać karty deala")
                _uiState.update {
                    // Przy cichym odświeżeniu mamy już poprawne dane na ekranie —
                    // nie zamieniamy ich na pełnoekranowy błąd.
                    if (silent) it.copy(message = text) else it.copy(isLoading = false, error = text)
                }
            }
            if (!silent) {
                loadPermissions()
                loadMembers()
            }
        }
    }

    /**
     * Kontakty towarzyszące idą osobnym zapytaniem, więc ich brak nie może
     * przewrócić całej karty — pasek kontaktów pokaże wtedy sam główny.
     */
    private suspend fun loadCompanions() {
        val companions = try {
            getCompanionsUseCase(dealId)
        } catch (_: Exception) {
            return
        }
        _uiState.update { it.copy(detail = it.detail?.copy(companions = companions)) }
    }

    /** Brak odpowiedzi z `/api/me` nie blokuje podglądu — chowamy tylko akcje. */
    private suspend fun loadPermissions() {
        val canManage = try {
            authRepository.getCurrentUser().permissions.contains(PERMISSION_DEAL_MANAGE)
        } catch (_: Exception) {
            false
        }
        _uiState.update { it.copy(canManage = canManage) }
    }

    /**
     * Osoby do wyboru opiekunów. Endpoint należy do modułu zadań
     * (`tasks.view`) — bez tego uprawnienia zostaje pusta lista, a karta chowa
     * selektory opiekunów zamiast pokazywać puste pole.
     */
    private suspend fun loadMembers() {
        val members = try {
            taskRepository.getMembers()
        } catch (_: Exception) {
            emptyList()
        }
        _uiState.update { it.copy(members = members) }
    }

    // ── Zakładki i tryb edycji ───────────────────────────────────────────────

    fun selectTab(tab: DealTab) {
        val state = _uiState.value
        // Wyjście z zakładki w trakcie edycji porzuciłoby formularz po cichu,
        // więc blokujemy przełączanie, dopóki użytkownik nie zapisze lub anuluje.
        if (state.editing && tab != state.tab) {
            _uiState.update { it.copy(message = "Zapisz lub anuluj zmiany, zanim zmienisz zakładkę") }
            return
        }
        _uiState.update { it.copy(tab = tab) }
        if (tab == DealTab.LEAD) loadLead()
    }

    // ── Zakładka „LEAD" ──────────────────────────────────────────────────────

    /**
     * Zgłoszenie z leadowni plus migawka instalacji. Oba źródła są niezależne:
     * awaria katalogu technologii nie może schować zgłoszenia, a deal bez
     * zgłoszenia nadal ma instalacje — dlatego każde ma własną obsługę błędu.
     *
     * @param force ponowny odczyt po zapisie/odświeżeniu karty.
     */
    fun loadLead(force: Boolean = false) {
        val lead = _uiState.value.lead
        if (!force && (lead.loaded || lead.isLoading)) return

        viewModelScope.launch {
            _uiState.update { it.copy(lead = it.lead.copy(isLoading = true, error = null)) }

            var error: String? = null
            val intake = try {
                getLeadIntakeUseCase(dealId)
            } catch (e: Exception) {
                error = crmErrorMessage(e, "Nie udało się wczytać zgłoszenia")
                null
            }
            val installations = loadInstallations()
            val articles = loadArticles()

            _uiState.update { state ->
                val note = intake?.note.orEmpty()
                val selected = installations?.selected
                state.copy(
                    lead = state.lead.copy(
                        isLoading = false,
                        loaded = true,
                        intake = intake,
                        catalog = installations?.catalog.orEmpty(),
                        selectedInstallations = selected,
                        // Gałęzie z wyborem rozwijamy same — wybór schowany dwa
                        // poziomy w głąb wyglądałby jak brak wyboru, a to główna
                        // informacja tej sekcji. Ręczne zwinięcia użytkownika
                        // zostają, bo dokładamy tylko brakujące gałęzie.
                        expanded = state.lead.expanded + ancestorsOfSelected(
                            nodes = installations?.catalog.orEmpty(),
                            selected = selected.orEmpty(),
                        ),
                        installationsEditable = installations?.editable ?: false,
                        articles = articles?.first ?: state.lead.articles,
                        articleGate = articles?.second ?: state.lead.articleGate,
                        // Niezapisana notatka przeżywa odświeżenie — inaczej
                        // ciche przeładowanie karty skasowałoby wpisany tekst.
                        noteDraft = if (state.lead.isNoteDirty) state.lead.noteDraft else note,
                        savedNote = note,
                        error = error,
                    ),
                )
            }
        }
    }

    /** Katalog technologii plus migawka etapu „lead" — materiał na drzewo zakresu. */
    private data class InstallationsData(
        val catalog: List<CategoryNode>,
        val selected: Set<String>,
        val editable: Boolean,
    )

    /**
     * Drzewo katalogu i wybór instalacji dla etapu LEAD. `null` = odczytu nie
     * udało się wykonać (drzewa bez katalogu nie da się narysować, a sama lista
     * id niczego handlowcowi nie mówi). Katalog ciągniemy zawsze, nie tylko przy
     * niepustym wyborze: bez niego nie byłoby czego zaznaczać.
     */
    private suspend fun loadInstallations(): InstallationsData? = try {
        val snapshot = getDealInstallationsUseCase(dealId).forStage(InstallationStage.LEAD)
        InstallationsData(
            catalog = buildCategoryTree(getCategoriesUseCase()),
            selected = snapshot?.categoryIds.orEmpty().toSet(),
            editable = snapshot?.editable ?: false,
        )
    } catch (_: Exception) {
        null
    }

    /**
     * Artykuły wiedzy deala i bramka ich generowania. `null` = odczyt nie wyszedł
     * — kafel pokazuje wtedy stan sprzed odświeżenia zamiast udawać, że artykułu
     * nie ma; „brak artykułu" i „nie wiem, czy jest" prowadzą do innych decyzji.
     */
    private suspend fun loadArticles(): Pair<Map<String, KnowledgeArticle>, ArticleGate>? = try {
        val articles = getKnowledgeArticlesUseCase(dealId).associateBy { it.categoryId }
        articles to getArticleGateUseCase(dealId)
    } catch (_: Exception) {
        null
    }

    // ── Zakres instalacji ────────────────────────────────────────────────────

    /** Rozwinięcie/zwinięcie gałęzi drzewa katalogu. Nic nie zapisuje. */
    fun toggleInstallationBranch(categoryId: String) {
        _uiState.update { state ->
            val expanded = state.lead.expanded
            state.copy(
                lead = state.lead.copy(
                    expanded = if (categoryId in expanded) {
                        expanded - categoryId
                    } else {
                        expanded + categoryId
                    },
                ),
            )
        }
    }

    /**
     * Zaznaczenie/odznaczenie węzła katalogu w migawce etapu LEAD. API przyjmuje
     * pełną listę po zmianie, więc wysyłamy cały wybór; odpowiedź nadpisuje stan,
     * bo zapis etapu wcześniejszego przelicza dziedziczenie w dalszych.
     *
     * Zmiana leci od razu, bez przycisku „Zapisz": to jedno kliknięcie i jedno
     * żądanie, a zakres instalacji wchodzi dalej do oferty — lepiej, żeby był
     * zapisany w chwili, w której handlowiec go ustala przy kliencie.
     */
    fun toggleInstallation(categoryId: String) {
        val lead = _uiState.value.lead
        val current = lead.selectedInstallations ?: return
        if (lead.isSavingInstallations || !lead.installationsEditable) return

        val next = if (categoryId in current) current - categoryId else current + categoryId

        viewModelScope.launch {
            // Zaznaczenie pokazujemy natychmiast — czekanie na odpowiedź przy
            // dotknięciu checkboxa czytałoby się jak zignorowany klik.
            _uiState.update {
                it.copy(
                    lead = it.lead.copy(selectedInstallations = next, isSavingInstallations = true),
                    message = null,
                )
            }
            try {
                val saved = setDealInstallationsUseCase(dealId, InstallationStage.LEAD, next.toList())
                    .forStage(InstallationStage.LEAD)
                    ?.categoryIds
                    .orEmpty()
                    .toSet()
                _uiState.update {
                    it.copy(
                        lead = it.lead.copy(
                            selectedInstallations = saved,
                            expanded = it.lead.expanded + ancestorsOfSelected(it.lead.catalog, saved),
                            isSavingInstallations = false,
                        ),
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        // Cofamy do stanu sprzed kliknięcia — inaczej ekran
                        // pokazywałby wybór, którego serwer nie przyjął.
                        lead = it.lead.copy(
                            selectedInstallations = current,
                            isSavingInstallations = false,
                        ),
                        message = crmErrorMessage(e, "Nie udało się zapisać zakresu instalacji"),
                    )
                }
            }
        }
    }

    // ── Artykuł wiedzy ───────────────────────────────────────────────────────

    /** Otwiera/zamyka potwierdzenie wysyłki artykułu dla danej instalacji. */
    fun askSendArticle(categoryId: String?) {
        _uiState.update { it.copy(lead = it.lead.copy(sendingArticleFor = categoryId)) }
    }

    /**
     * Wysyłka artykułu klientowi wątkiem WhatsApp deala. Wiadomość idzie do
     * klienta, więc ekran pyta o potwierdzenie, zanim tu trafi. Poza oknem 24h
     * API odrzuca wysyłkę free-form — komunikat serwera pokazujemy dosłownie,
     * bo tłumaczy regułę lepiej niż nasze „nie udało się".
     */
    fun sendArticleToClient(categoryId: String) {
        val article = _uiState.value.lead.articles[categoryId] ?: return
        if (_uiState.value.lead.isSendingArticle) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(lead = it.lead.copy(isSendingArticle = true), message = null)
            }
            try {
                sendArticleToClientUseCase(dealId, article)
                _uiState.update {
                    it.copy(
                        lead = it.lead.copy(isSendingArticle = false, sendingArticleFor = null),
                        message = "Wysłano artykuł klientowi",
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        lead = it.lead.copy(isSendingArticle = false, sendingArticleFor = null),
                        message = crmErrorMessage(e, "Nie udało się wysłać artykułu"),
                    )
                }
            }
        }
    }

    // ── Szybka edycja pól deala z zakładki LEAD ──────────────────────────────

    fun setBuildingKind(kind: DealBuildingKind) =
        patchDeal("Zapisano rodzaj budynku") { it.copy(buildingKind = kind) }

    fun setMeetingKind(kind: MeetingKind) =
        patchDeal("Zapisano miejsce spotkania") { it.copy(meetingKind = kind) }

    fun setMeetingAt(millis: Long?) =
        patchDeal(if (millis == null) "Usunięto termin" else "Zapisano termin") {
            it.copy(meetingAt = millis)
        }

    /**
     * Zmiana jednego pola karty prosto z zakładki, bez wchodzenia w formularz.
     * Idzie tą samą drogą co „Edytuj" (`PATCH` z różnicy draftu), więc wysyła
     * wyłącznie to jedno pole i nie nadpisuje zmian zrobionych równolegle
     * w panelu. Po zapisie odświeżamy kartę cicho — deal wraca z serwera i to on
     * jest źródłem prawdy, a nie nasze założenie o wyniku.
     */
    private fun patchDeal(success: String, edit: (DealDraft) -> DealDraft) {
        val deal = _uiState.value.detail?.deal ?: return
        if (!_uiState.value.canManage || _uiState.value.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, message = null) }
            try {
                val updated = updateDealUseCase(deal, edit(deal.toDraft()))
                _uiState.update { state ->
                    state.copy(
                        isSaving = false,
                        message = success,
                        detail = state.detail?.copy(deal = updated),
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSaving = false, message = crmErrorMessage(e, "Nie udało się zapisać"))
                }
            }
        }
    }

    fun onLeadNoteChange(text: String) {
        _uiState.update { it.copy(lead = it.lead.copy(noteDraft = text)) }
    }

    /**
     * Zapis notatki z rozmowy / uwag klienta. Serwer zwraca treść rozwiązaną —
     * po wyczyszczeniu pola może odesłać wyciąg z archiwalnej treści zgłoszenia,
     * więc pole przestawiamy na to, co faktycznie zapisał, a nie na to, co
     * wpisał użytkownik.
     */
    fun saveLeadNote() {
        val lead = _uiState.value.lead
        if (lead.isSavingNote || !lead.isNoteDirty) return
        viewModelScope.launch {
            _uiState.update { it.copy(lead = it.lead.copy(isSavingNote = true), message = null) }
            try {
                val saved = updateLeadNoteUseCase(dealId, lead.noteDraft).orEmpty()
                _uiState.update { state ->
                    state.copy(
                        message = "Zapisano notatkę",
                        lead = state.lead.copy(
                            isSavingNote = false,
                            noteDraft = saved,
                            savedNote = saved,
                            intake = state.lead.intake?.copy(note = saved.ifBlank { null }),
                        ),
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        lead = it.lead.copy(isSavingNote = false),
                        message = crmErrorMessage(e, "Nie udało się zapisać notatki"),
                    )
                }
            }
        }
    }

    fun startEdit() {
        val detail = _uiState.value.detail ?: return
        val draft = detail.deal.toDraft()
        _uiState.update {
            it.copy(
                editing = true,
                dealDraft = draft,
                clientDraft = detail.client?.toDraft(),
                numbers = draft.toNumberText(),
            )
        }
    }

    fun cancelEdit() {
        val detail = _uiState.value.detail
        val draft = detail?.deal?.toDraft() ?: DealDraft()
        _uiState.update {
            it.copy(
                editing = false,
                dealDraft = draft,
                clientDraft = detail?.client?.toDraft(),
                numbers = draft.toNumberText(),
            )
        }
    }

    /** Każda zmiana pola deala przechodzi tędy — jedno miejsce mutacji draftu. */
    fun editDeal(transform: (DealDraft) -> DealDraft) {
        _uiState.update { it.copy(dealDraft = transform(it.dealDraft)) }
    }

    /** Zmiana pola kartoteki (imię, e-mail, telefon, adres głównego kontaktu). */
    fun editClient(transform: (ClientDraft) -> ClientDraft) {
        _uiState.update { state ->
            state.clientDraft?.let { state.copy(clientDraft = transform(it)) } ?: state
        }
    }

    fun onPeopleChange(text: String) = editNumber(text) { n, d ->
        n.copy(people = text) to d.copy(people = text.toIntOrNull())
    }

    fun onAreaChange(text: String) = editNumber(text) { n, d ->
        n.copy(areaM2 = text) to d.copy(areaM2 = text.toIntOrNull())
    }

    fun onFloorsChange(text: String) = editNumber(text) { n, d ->
        n.copy(floors = text) to d.copy(floors = text.toIntOrNull())
    }

    private fun editNumber(
        text: String,
        transform: (NumberText, DealDraft) -> Pair<NumberText, DealDraft>,
    ) {
        // Wpisany śmieć („12a") nie może wywrócić zapisu — do draftu trafia
        // tylko to, co się parsuje, a tekst i tak zostaje na ekranie.
        if (!text.isNumericInput()) return
        _uiState.update { state ->
            val (numbers, draft) = transform(state.numbers, state.dealDraft)
            state.copy(numbers = numbers, dealDraft = draft)
        }
    }

    /**
     * Zapis zakładki „Dane". Kolejność ma znaczenie: najpierw kartoteka, potem
     * deal. Zmiana adresu uruchamia po stronie serwera ponowne geokodowanie i
     * przeliczenie dojazdu, a przeładowanie karty na końcu przynosi już wynik.
     *
     * To dwa osobne żądania, więc awaria drugiego zostawia zapisaną kartotekę
     * i niezapisanego deala — bez transakcji po stronie API nie da się tego
     * uniknąć. Dlatego przy błędzie zostajemy w trybie edycji z komunikatem:
     * ponowne „Zapisz" wyśle oba patche jeszcze raz, a powtórzony zapis
     * kartoteki tymi samymi wartościami niczego nie psuje.
     */
    fun saveEdit() {
        val state = _uiState.value
        val detail = state.detail ?: return
        if (!state.isDirty) {
            _uiState.update { it.copy(editing = false, message = "Nic się nie zmieniło") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, message = null) }
            try {
                val client = detail.client
                val clientDraft = state.clientDraft
                if (client != null && clientDraft != null && client.toDraft() != clientDraft) {
                    updateClientUseCase(client, clientDraft)
                }
                updateDealUseCase(detail.deal, state.dealDraft)
                _uiState.update { it.copy(isSaving = false, editing = false, message = "Zapisano") }
                load(silent = true)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSaving = false, message = crmErrorMessage(e, "Nie udało się zapisać"))
                }
            }
        }
    }

    // ── Kontakty towarzyszące ────────────────────────────────────────────────

    fun addCompanion(clientId: String) = runContacts("Dodano kontakt") {
        addCompanionUseCase(dealId, clientId)
    }

    fun removeCompanion(clientId: String) = runContacts("Odpięto kontakt") {
        removeCompanionUseCase(dealId, clientId)
    }

    private fun runContacts(success: String, block: suspend () -> List<Client>) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, message = null) }
            try {
                val companions = block()
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        detail = it.detail?.copy(companions = companions),
                        message = success,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        message = crmErrorMessage(e, "Nie udało się zmienić kontaktów"),
                    )
                }
            }
        }
    }

    /**
     * Zamiana głównego kontaktu. Podmienia `Deal.clientId`, więc cała karta jest
     * po niej nieaktualna — przeładowujemy ją w całości zamiast łatać stan.
     */
    fun setPrimaryContact(clientId: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, message = null) }
            try {
                setPrimaryContactUseCase(dealId, clientId)
                _uiState.update { it.copy(isSaving = false, message = "Zmieniono główny kontakt") }
                load(silent = true)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        message = crmErrorMessage(e, "Nie udało się zmienić kontaktu"),
                    )
                }
            }
        }
    }

    // ── Etap i termin kontaktu ───────────────────────────────────────────────

    fun changeStage(
        stage: DealStage,
        lostReasonCategory: String? = null,
        lostReason: String? = null,
    ) {
        val detail = _uiState.value.detail ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, message = null) }
            try {
                val updated = changeDealStageUseCase(
                    id = detail.deal.id,
                    stage = stage,
                    lostReasonCategory = lostReasonCategory,
                    lostReason = lostReason,
                )
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        // Klient i historia nie wracają z endpointu etapu —
                        // podmieniamy sam deal, resztę dociągnie odświeżenie.
                        detail = it.detail?.copy(deal = updated),
                        message = "Etap zmieniony na „${stage.label}”",
                    )
                }
                // Historia zmian dopisała nowy wpis — dociągamy pełną kartę.
                load(silent = true)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        message = crmErrorMessage(e, "Nie udało się zmienić etapu"),
                    )
                }
            }
        }
    }

    /** Domknięcie procesu po montażu — odpowiednik zielonej akcji ze stopki web. */
    fun markCompleted() = changeStage(DealStage.ZAKONCZONY)

    /**
     * Skrót „oddzwonię za N dni" — najczęstsza zmiana robiona w terenie, więc
     * zostaje na karcie zamiast wymuszać wejście w formularz. Idzie tą samą
     * ścieżką co edycja: draft z jednym zmienionym polem → `PATCH`.
     */
    fun setNextContact(millis: Long) {
        val detail = _uiState.value.detail ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, message = null) }
            try {
                val deal = detail.deal
                val updated = updateDealUseCase(deal, deal.toDraft().copy(nextContactAt = millis))
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        detail = it.detail?.copy(deal = updated),
                        message = "Następny kontakt: ${formatDate(updated.nextContactAt).orEmpty()}",
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        message = crmErrorMessage(e, "Nie udało się zapisać terminu"),
                    )
                }
            }
        }
    }

    // ── Asystent karty ───────────────────────────────────────────────────────

    /**
     * Pytanie trafia do wątku od razu, jeszcze przed odpowiedzią serwera — bez
     * tego ekran przez sekundę wyglądałby, jakby dotknięcie „Wyślij" nic nie
     * zrobiło. Przy błędzie pytanie zostaje w wątku, żeby dało się je powtórzyć
     * bez przepisywania.
     */
    fun askAssistant(question: String) {
        val text = question.trim()
        if (text.isEmpty() || _uiState.value.assistant.isAsking) return

        val asked = _uiState.value.assistant.messages +
            AssistantMessage(AssistantMessage.ROLE_USER, text)
        _uiState.update {
            it.copy(assistant = it.assistant.copy(messages = asked, isAsking = true))
        }

        viewModelScope.launch {
            try {
                val reply = askAssistantUseCase(dealId, asked)
                _uiState.update {
                    it.copy(
                        assistant = it.assistant.copy(
                            messages = asked +
                                AssistantMessage(AssistantMessage.ROLE_ASSISTANT, reply.text),
                            isAsking = false,
                            configured = reply.configured,
                        ),
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        assistant = it.assistant.copy(isAsking = false),
                        message = crmErrorMessage(e, "Asystent nie odpowiedział"),
                    )
                }
            }
        }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }

    fun call(phone: String) = makeCallUseCase(phone)

    /**
     * Adres instalacji w mapach. Współrzędne z geokodowania mają pierwszeństwo
     * przed tekstem — adres bywa niejednoznaczny, a handlowiec ma dojechać pod
     * właściwy budynek, nie pod podobnie nazwaną ulicę w innej miejscowości.
     */
    fun openMap() {
        val client = _uiState.value.detail?.client ?: return
        navigateToClientUseCase(client.address, client.geoLat, client.geoLng)
    }
}

/** Wartości liczbowe draftu jako tekst startowy formularza. */
private fun DealDraft.toNumberText() = DealDetailViewModel.NumberText(
    people = people?.toString().orEmpty(),
    areaM2 = areaM2?.toString().orEmpty(),
    floors = floors?.toString().orEmpty(),
)
