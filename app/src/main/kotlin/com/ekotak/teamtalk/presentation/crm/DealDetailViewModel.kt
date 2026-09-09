package com.ekotak.teamtalk.presentation.crm

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.domain.model.ArticleGate
import com.ekotak.teamtalk.domain.model.AssistantMessage
import com.ekotak.teamtalk.domain.model.Audit
import com.ekotak.teamtalk.domain.model.AuditAddressKind
import com.ekotak.teamtalk.domain.model.BuildingStandard
import com.ekotak.teamtalk.domain.model.Category
import com.ekotak.teamtalk.domain.model.CategoryNode
import com.ekotak.teamtalk.domain.model.Client
import com.ekotak.teamtalk.domain.model.ClientDraft
import com.ekotak.teamtalk.domain.model.Deal
import com.ekotak.teamtalk.domain.model.DealBuildingKind
import com.ekotak.teamtalk.domain.model.DealDetail
import com.ekotak.teamtalk.domain.model.DealDocument
import com.ekotak.teamtalk.domain.model.DealDraft
import com.ekotak.teamtalk.domain.model.DealInvoices
import com.ekotak.teamtalk.domain.model.InvoiceRachunek
import com.ekotak.teamtalk.domain.model.DealSettlement
import com.ekotak.teamtalk.domain.model.ContractFilling
import com.ekotak.teamtalk.domain.model.ContractItem
import com.ekotak.teamtalk.domain.model.ContractKind
import com.ekotak.teamtalk.domain.model.ContractMaterial
import com.ekotak.teamtalk.domain.model.ContractPreview
import com.ekotak.teamtalk.domain.model.ContractStage
import com.ekotak.teamtalk.domain.model.ContractStatus
import com.ekotak.teamtalk.domain.model.DealContract
import com.ekotak.teamtalk.domain.model.DealOffer
import com.ekotak.teamtalk.domain.model.DealOrder
import com.ekotak.teamtalk.domain.model.DealStage
import com.ekotak.teamtalk.domain.model.DocumentCategory
import com.ekotak.teamtalk.domain.model.isImageOrPdfUpload
import com.ekotak.teamtalk.domain.model.slotLabel
import com.ekotak.teamtalk.domain.model.slotLimit
import com.ekotak.teamtalk.domain.model.withSlot
import com.ekotak.teamtalk.domain.model.PurchaseLine
import com.ekotak.teamtalk.domain.model.StockReservation
import com.ekotak.teamtalk.domain.model.pruneToSelected
import com.ekotak.teamtalk.domain.model.HeatloadMode
import com.ekotak.teamtalk.domain.model.InstallationStage
import com.ekotak.teamtalk.domain.model.KnowledgeArticle
import com.ekotak.teamtalk.domain.model.LeadIntake
import com.ekotak.teamtalk.domain.model.MeetingKind
import com.ekotak.teamtalk.domain.model.OfferLock
import com.ekotak.teamtalk.domain.model.Project
import com.ekotak.teamtalk.domain.model.NO_SECTION_LABEL
import com.ekotak.teamtalk.domain.model.Edit
import com.ekotak.teamtalk.domain.model.Task
import com.ekotak.teamtalk.domain.model.TaskMember
import com.ekotak.teamtalk.domain.model.TaskPatch
import com.ekotak.teamtalk.domain.model.TaskPriority
import com.ekotak.teamtalk.domain.model.TaskSection
import com.ekotak.teamtalk.domain.model.TaskStatus
import com.ekotak.teamtalk.domain.model.sectionFromStage
import com.ekotak.teamtalk.domain.model.UfhState
import com.ekotak.teamtalk.domain.model.ancestorsOfSelected
import com.ekotak.teamtalk.domain.model.applyBuildingToUfh
import com.ekotak.teamtalk.domain.model.buildCategoryTree
import com.ekotak.teamtalk.domain.model.categoryIdPath
import com.ekotak.teamtalk.domain.model.categoryPath
import com.ekotak.teamtalk.domain.model.previewHeatloadKw
import com.ekotak.teamtalk.domain.model.resolveAuditForm
import com.ekotak.teamtalk.domain.model.toM2
import com.ekotak.teamtalk.domain.model.ufhMissingAnswers
import com.ekotak.teamtalk.domain.model.hasChangesFrom
import com.ekotak.teamtalk.domain.model.nextStages
import com.ekotak.teamtalk.domain.model.toDraft
import com.ekotak.teamtalk.domain.repository.AuditInstallations
import com.ekotak.teamtalk.domain.repository.AuditRepository
import com.ekotak.teamtalk.domain.repository.DealDocumentRepository
import com.ekotak.teamtalk.domain.repository.AuditSaveResult
import com.ekotak.teamtalk.domain.repository.AuthRepository
import com.ekotak.teamtalk.domain.repository.ContractOrderRebuild
import com.ekotak.teamtalk.domain.repository.ContractRepository
import com.ekotak.teamtalk.domain.repository.InvoiceRepository
import com.ekotak.teamtalk.domain.repository.ContractSaveResult
import com.ekotak.teamtalk.domain.repository.OfferPricingRepository
import com.ekotak.teamtalk.domain.repository.DealProjectSaveResult
import com.ekotak.teamtalk.domain.repository.OrderRepository
import com.ekotak.teamtalk.domain.repository.ProjectRepository
import com.ekotak.teamtalk.domain.repository.OrderSaveResult
import com.ekotak.teamtalk.domain.repository.SettlementRepository
import com.ekotak.teamtalk.domain.repository.SettlementSaveResult
import com.ekotak.teamtalk.domain.repository.TaskRepository
import com.ekotak.teamtalk.domain.ufh.OfferPricing
import com.ekotak.teamtalk.BuildConfig
import com.ekotak.teamtalk.domain.model.policzPodglad
import com.ekotak.teamtalk.domain.ufh.ContractScopeInstallation
import com.ekotak.teamtalk.domain.ufh.contractScopeItems
import com.ekotak.teamtalk.domain.ufh.przedmiotZAutomatu
import com.ekotak.teamtalk.domain.ufh.scalPozycje
import com.ekotak.teamtalk.domain.ufh.PointRate
import com.ekotak.teamtalk.domain.ufh.ratesForPath
import com.ekotak.teamtalk.domain.ufh.PlanPrep
import com.ekotak.teamtalk.domain.ufh.planPrepToJson
import com.ekotak.teamtalk.domain.ufh.prepEmpty
import com.ekotak.teamtalk.data.files.DocumentFileStore
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
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
import kotlinx.serialization.json.JsonObject
import javax.inject.Inject
import java.io.File
import java.time.LocalDate

/**
 * Uprawnienie board360 wymagane do zmiany etapu, edycji karty i kontaktów.
 * `internal`, bo ten sam warunek gate-uje ekran artykułu wiedzy — dwa wpisane
 * z ręki stringi rozjechałyby się przy pierwszej zmianie nazwy uprawnienia.
 */
internal const val PERMISSION_DEAL_MANAGE = "deal.manage"

/**
 * Zamówienia deala: board360 trzyma pod tym uprawnieniem także ODCZYT listy,
 * nie tylko zapis (`OrdersController`). Bez niego zakładka pokazuje sam zakres
 * i rezerwację, zamiast udawać, że deal nie ma zamówień.
 */
private const val PERMISSION_ORDER_MANAGE = "order.manage"

/** Zmiany w rezerwacji materiału i dokładanie braków na listę zakupową. */
private const val PERMISSION_INVENTORY_MANAGE = "inventory.manage"

/**
 * Zatwierdzanie i cofanie rozliczeń punktowych (zakł. „Rozliczenie"). Sam
 * RACHUNEK widzi każdy, kto widzi kartę — tak samo jak w panelu, gdzie punkty
 * z audytu pokazują się bez tego uprawnienia. Zamraża je zarząd.
 */
private const val PERMISSION_FINANCIAL_MANAGE = "financial.terms.manage"

/**
 * Podgląd faktur pobranych z KSeF (zakł. „Faktura"). To księgowość, więc
 * board360 daje je adminowi, zarządowi i biuru. Handlowiec BEZ tego prawa
 * widzi na zakładce rachunek z umowy i montaże — sama lista wystawionych
 * dokumentów jest dla niego zamknięta, i zakładka mówi to wprost.
 */
private const val PERMISSION_KSEF_VIEW = "ksef.view"

/**
 * Założenie projektu pod dealem (zakł. „Harmonogram"). Board360 puszcza na
 * `projects.view` wyłącznie ZGŁOSZENIE POMYSŁU do Poczekalni — projekt wprost,
 * a taki jest projekt deala, wymaga zarządzania (`ProjectsController.create`).
 */
private const val PERMISSION_PROJECTS_MANAGE = "projects.manage"

/**
 * Zakładanie i zmiana zadań (zakł. „Zadania"). Odczyt listy chodzi na
 * `tasks.view` — to samo rozróżnienie, co w `TasksController` board360.
 */
private const val PERMISSION_TASKS_MANAGE = "tasks.manage"

/** Odstęp między znakiem a zapytaniem do kartoteki przy szukaniu kontaktu. */
private const val CONTACT_SEARCH_DEBOUNCE_MS = 250L

/**
 * Limit pliku deala po stronie board360 (`FileInterceptor` kontrolera
 * dokumentów). Sprawdzamy go PRZED wrzuceniem do kolejki: plik odrzucony
 * dopiero przy wysyłce zniknąłby z karty godzinę po tym, jak ktoś go dodał.
 */
private const val MAX_DOCUMENT_BYTES = 25 * 1024 * 1024

/**
 * Pompa ciepła w ścieżce katalogu („Pompa ciepła", „Powietrzne pompy ciepła").
 * Rozpoznajemy po NAZWIE, a nie po id węzła — katalog jest budowany osobno
 * w każdej organizacji, więc id niczego nie gwarantuje. Ten sam wzorzec ma
 * panel (`dealHasHeatPump`).
 */
private val HEAT_PUMP_NAME = Regex("pomp\\w*\\s+ciep", RegexOption.IGNORE_CASE)

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
    private val auditRepository: AuditRepository,
    private val orderRepository: OrderRepository,
    private val offerPricingRepository: OfferPricingRepository,
    private val settlementRepository: SettlementRepository,
    private val contractRepository: ContractRepository,
    private val invoiceRepository: InvoiceRepository,
    private val dealDocumentRepository: DealDocumentRepository,
    private val projectRepository: ProjectRepository,
    private val documentFiles: DocumentFileStore,
    private val makeCallUseCase: MakeCallUseCase,
) : ViewModel() {

    private val dealId: String = savedStateHandle["dealId"] ?: ""

    /** Surowe zadania deala z cache — filtrujemy i grupujemy je lokalnie. */
    private var dealTasks: List<Task> = emptyList()

    /** Strumień zadań; zakładany raz, przy pierwszym wejściu w zakładkę. */
    private var tasksJob: Job? = null

    /**
     * Ręczna kolejność zadań (preferencja `tasks.order`, wspólna z panelem).
     * Trzymamy ją poza `UiState`, bo obejmuje CAŁY zespół, a nie ten deal —
     * do widoku trafia już jako ułożona lista sekcji.
     */
    private var manualOrder: List<String> = emptyList()

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

    /**
     * Formularz nowego audytu Heizlast. Pola liczbowe trzymamy jako tekst
     * z tego samego powodu co `NumberText` wyżej — w trakcie pisania bywają
     * niesparsowalne.
     */
    data class HeatloadDraft(
        val mode: HeatloadMode? = null,
        val areaM2: String = "",
        val heightM: String = "",
        val standard: BuildingStandard? = null,
        val kw: String = "",
        val note: String = "",
    ) {
        /** Podgląd wyniku szybkiego szacunku; `null` = za mało danych. */
        val preview: Double?
            get() = if (mode == HeatloadMode.SZYBKI) {
                previewHeatloadKw(areaM2.toM2(), standard, heightM.toM2())
            } else {
                null
            }

        /** Czy da się z tego zbudować zapis (walidacja jak w panelu). */
        val isSubmittable: Boolean
            get() = when (mode) {
                HeatloadMode.SZYBKI -> (areaM2.toM2() ?: 0.0) > 0 && standard != null
                HeatloadMode.DIN -> (kw.toM2() ?: 0.0) > 0
                null -> note.isNotBlank()
            }
    }

    /**
     * Zakładka „Audyt". Trzy niezależne bloki, każdy z własnym błędem — jak
     * w panelu: awaria odczytu umów nie może schować formularza, a brak
     * katalogu nie może schować listy Heizlast.
     */
    data class AuditState(
        val isLoading: Boolean = false,
        val loaded: Boolean = false,
        /** Wszystkie audyty deala — po przełączeniu instalacji szukamy w nich rekordu. */
        val records: List<Audit> = emptyList(),
        /** Płaski katalog — z niego dziedziczy się formularz po przodkach węzła. */
        val categories: List<Category> = emptyList(),
        /** Instalacje migawki etapu „audit", ze ścieżką nazw. */
        val installations: List<SelectedInstallation> = emptyList(),
        val selectedInstallationId: String? = null,
        /** Węzeł-właściciel szablonu; `null` = ta gałąź formularza nie ma. */
        val formOwnerId: String? = null,
        /** Id rekordu formularza deala; `null` = jeszcze nie zapisano żadnego. */
        val formAuditId: String? = null,
        /** Stan formularza w edycji; `null` = nie ma czego pokazać. */
        val form: UfhState? = null,
        /** Stan zapisany — po nim poznajemy niezapisane zmiany. */
        val savedForm: UfhState? = null,
        /** Czy deal ma gdziekolwiek pompę ciepła — odsłania pytanie o chłodzenie. */
        val hasHeatPump: Boolean = false,
        /** Podpisana umowa zamykająca ofertę; `null` = audyt otwarty. */
        val lock: OfferLock? = null,
        val isSavingForm: Boolean = false,
        val draft: HeatloadDraft = HeatloadDraft(),
        val isSavingHeatload: Boolean = false,
        val error: String? = null,
    ) {
        /**
         * Lista Heizlast — bez rekordów formularza instalacji. Jeden endpoint
         * zwraca oba rodzaje, a wymieszane na jednej liście nic by nie mówiły.
         */
        val heatloads: List<Audit> get() = records.filter { it.installationForm == null }

        /** Pytania bez odpowiedzi — sterują kolorem „Zapisz" i wypisem braków. */
        val missing: List<String> get() = form?.let(::ufhMissingAnswers).orEmpty()

        val isFormDirty: Boolean get() = form != null && form != savedForm

        /** Formularz zapisany w telefonie, ale jeszcze niewysłany do panelu. */
        val isFormPending: Boolean
            get() = formAuditId != null &&
                records.firstOrNull { it.id == formAuditId }?.pendingSince != null

        /** Formularz zamknięty podpisem: pola nieaktywne, zmiana idzie z panelu. */
        val isFormLocked: Boolean get() = lock != null
    }

    /**
     * Zakładka „Zamówienie" — trzy bloki panelu w jednym stanie: zakres (drzewo
     * etapu „sold", sam podgląd), rezerwacja materiału i zamówienia deala.
     * Każdy blok ma własne prawo do porażki, bo każdy stoi na innym uprawnieniu
     * board360 (`crm.view` / `inventory.view` / `order.manage`).
     */
    data class OrdersState(
        val isLoading: Boolean = false,
        val loaded: Boolean = false,
        val orders: List<DealOrder> = emptyList(),
        val offers: List<DealOffer> = emptyList(),
        val reservations: List<StockReservation> = emptyList(),
        val purchases: List<PurchaseLine> = emptyList(),
        /** `false` = odczytu zamówień odmówiono (brak `order.manage`). */
        val ordersAvailable: Boolean = true,
        /** `false` = odczytu magazynu odmówiono (brak `inventory.view`). */
        val materialsAvailable: Boolean = true,
        /** Dane sprzed utraty zasięgu — zakładka mówi o tym wprost. */
        val fromCache: Boolean = false,
        /** Katalog przycięty do zakresu kupionego przez klienta (etap „sold"). */
        val scopeTree: List<CategoryNode> = emptyList(),
        val scope: Set<String> = emptySet(),
        val expanded: Set<String> = emptySet(),
        /** Oferta wskazana w selektorze „Wygrana oferta…". */
        val selectedOfferId: String? = null,
        val isSaving: Boolean = false,
        val error: String? = null,
    ) {
        /** Tylko z wygranej oferty da się założyć zamówienie (reguła board360). */
        val wonOffers: List<DealOffer> get() = offers.filter { it.isWon }

        /** Linie, które realnie trzymają towar — wydane i zwolnione już nie. */
        val activeReservations: List<StockReservation>
            get() = reservations.filter { it.isActive }

        /** Braki z kartoteką: tylko takie da się dołożyć na listę zakupową. */
        val missingReservations: List<StockReservation>
            get() = activeReservations.filter { it.missing > 0 && it.productId != null }

        /** Linie bez kartoteki magazynu — nikt ich nie zarezerwuje ani nie kupi. */
        val gapReservations: List<StockReservation>
            get() = activeReservations.filter { it.productId == null }

        val coveredCount: Int
            get() = activeReservations.size - missingReservations.size - gapReservations.size

        /**
         * Braki, których nie objął jeszcze żaden zakup — tylko te wolno
         * „ZAMÓWIĆ". Reszta ma propozycję albo zamówienie w drodze i drugie
         * kliknięcie kupiłoby towar podwójnie.
         */
        val unorderedReservations: List<StockReservation>
            get() = missingReservations.filter { purchasesFor(it).isEmpty() }

        /**
         * Zakupy pod jedną linię rezerwacji. Propozycja scalona z kilku linii
         * gubi `reservationId` — wtedy paruje się po kartotece, żeby wiersz nie
         * udawał, że nikt nic nie zamówił.
         */
        fun purchasesFor(row: StockReservation): List<PurchaseLine> = purchases.filter {
            it.status != "cancelled" &&
                (it.reservationId == row.id ||
                    (it.reservationId == null && row.productId != null && it.productId == row.productId))
        }

        /** Klient plus miejscowość — podpis „dla kogo", ten sam co w magazynie. */
        val clientLabel: String
            get() = reservations.firstOrNull()?.clientLabel.orEmpty()
    }

    /** Ekran zakładki „Oferta" — trzy widoki tej samej instalacji, jak w panelu. */
    enum class OfferMode(val label: String) {
        CLIENT("Oferta dla klienta"),
        SUMMARY("Podsumowanie"),
        TECH("Widok techniczny"),
    }

    /**
     * Instalacja gotowa do wyceny: audyt (CO robimy) + węzeł-właściciel
     * formularza (spod niego idzie cennik). [form] jest ZAPISANYM audytem deala,
     * nigdy szablonem katalogu — oferty nie składa się z pytań bez odpowiedzi.
     */
    data class OfferInstallation(
        val categoryId: String,
        val name: String,
        val path: List<String>,
        val formOwnerId: String?,
        /** Nazwa węzła-właściciela — po niej poznajemy instalację (formuła ceny). */
        val formOwnerName: String = "",
        /** Ścieżka id od korzenia do właściciela — zestawy cennika dziedziczą w dół. */
        val formOwnerPath: List<String> = emptyList(),
        val form: UfhState?,
    )

    /**
     * Zakładka „Oferta". Zakres i argumenty dla klienta plus podsumowanie
     * z kwotami — 1:1 z `DealOfferPanel` panelu.
     *
     * Cennik ([pricing]) dociągamy PER WĘZEŁ-właściciel formularza, jeden odczyt
     * na instalację; `null` w mapie = węzeł bez formuły ceny (inna instalacja),
     * brak klucza = jeszcze nie pytaliśmy.
     */
    data class OfferState(
        val isLoading: Boolean = false,
        val loaded: Boolean = false,
        val installations: List<OfferInstallation> = emptyList(),
        val selectedIndex: Int = 0,
        val mode: OfferMode = OfferMode.CLIENT,
        /** Podpisana umowa zamykająca ofertę; `null` = oferta otwarta. */
        val lock: OfferLock? = null,
        val pricing: Map<String, OfferPricing?> = emptyMap(),
        val pricingLoading: Boolean = false,
        val error: String? = null,
    ) {
        val active: OfferInstallation?
            get() = if (installations.isEmpty()) {
                null
            } else {
                installations.getOrNull(selectedIndex.coerceIn(0, installations.size - 1))
            }

        /** Cennik aktywnej instalacji; `null` = brak formuły albo jeszcze nie wczytany. */
        val activePricing: OfferPricing?
            get() = active?.formOwnerId?.let { pricing[it] }

        /** Czy o cennik aktywnej instalacji już pytaliśmy (choćby z wynikiem `null`). */
        val activePricingAsked: Boolean
            get() = active?.formOwnerId?.let { pricing.containsKey(it) } ?: false
    }

    /**
     * Zakładka „Pliki". Dociągana przy wejściu w zakładkę, jak pozostałe —
     * lista plików to osobne zapytanie, a większość wejść w kartę kończy się
     * na „Dane".
     *
     * `offline` znaczy „to, co widzisz, jest z telefonu": lista przyszła
     * z cache'u, bo serwer był nieosiągalny. Nie jest to błąd — wgrywanie i tak
     * działa, zapisy czekają w kolejce — ale ma być widoczne, żeby nikt nie
     * uznał braku cudzego pliku za jego brak w dealu.
     */
    data class FilesState(
        val isLoading: Boolean = false,
        val loaded: Boolean = false,
        val documents: List<DealDocument> = emptyList(),
        /** Piwnica i garaż ze zgłoszenia — fallback slotów, gdy „Dane" milczą. */
        val leadBasement: Boolean? = null,
        val leadGarage: Boolean? = null,
        val offline: Boolean = false,
        /** Trwa wgrywanie/kasowanie — blokuje przyciski, żeby nie dublować akcji. */
        val busy: Boolean = false,
        val error: String? = null,
    ) {
        /** Ile plików czeka w kolejce na wysyłkę — pasek nad sekcjami. */
        val pendingCount: Int get() = documents.count { it.pending }
    }

    /**
     * Zakładka „Harmonogram" (klucz `projekt` w panelu) — projekty rozwojowe
     * przypięte do tego deala. 1:1 z `DealProjectPanel`: lista z postępem
     * i pole zakładania nowego, a planowanie zadań zostaje w module Projekty.
     */
    data class ScheduleState(
        val isLoading: Boolean = false,
        val loaded: Boolean = false,
        val projects: List<Project> = emptyList(),
        /** Nazwa wpisywana w polu „Nazwa nowego projektu…". */
        val newName: String = "",
        /** Lista pochodzi z cache — serwer był nieosiągalny. */
        val offline: Boolean = false,
        /** Trwa zakładanie projektu — blokuje przycisk, żeby nie dublować. */
        val busy: Boolean = false,
        val error: String? = null,
    ) {
        /** Ile projektów czeka w kolejce na wysyłkę — pasek nad listą. */
        val pendingCount: Int get() = projects.count { it.localOnly }
    }

    /**
     * Instalacja do rozliczenia: zapisany audyt (skąd ILOŚCI) i stawki punktowe
     * ze ścieżki węzła w katalogu (skąd PUNKTY). Rachunku tu nie ma — robi go
     * zakładka poza wątkiem głównym, tak samo jak przy ofercie.
     */
    data class SettlementInstallation(
        val categoryId: String,
        val name: String,
        val path: List<String>,
        val formOwnerId: String?,
        /** Zapisany audyt tej instalacji; `null` = nie ma z czego liczyć. */
        val form: UfhState?,
        /** Pozycje zestawów punktowych („Montaż", „Biuro") wzdłuż ścieżki węzła. */
        val rates: List<PointRate>,
    )

    /**
     * Zakładka „Rozliczenie" — ile punktów za pracę należy się za tego deala,
     * w rozbiciu na instalacje i zestawy punktowe. 1:1 z `DealSettlementPanel`.
     *
     * [stage] mówi, z którego etapu wzięliśmy zakres instalacji (montaż, a gdy
     * pusty — najdalszy wypełniony wcześniejszy); zakładka pisze to wprost, bo
     * inaczej lista instalacji wygląda na wziętą znikąd.
     */
    data class SettlementState(
        val isLoading: Boolean = false,
        val loaded: Boolean = false,
        val stage: InstallationStage? = null,
        val installations: List<SettlementInstallation> = emptyList(),
        /** Zatwierdzone migawki; `pending` = decyzja czeka w kolejce. */
        val snapshots: List<DealSettlement> = emptyList(),
        /** Rozwinięte instalacje (id węzła) — rozbicie na pozycje. */
        val expanded: Set<String> = emptySet(),
        /** Trwa zatwierdzanie/cofanie — blokuje przyciski, żeby nie dublować. */
        val busy: Boolean = false,
        val error: String? = null,
    ) {
        fun snapshotFor(categoryId: String): DealSettlement? =
            snapshots.firstOrNull { it.categoryId == categoryId }
    }

    /**
     * Zakładka „Remarketing" (etap instalacyjny `edukacja`): własna migawka
     * zakresu instalacji, dziedziczona z LEAD-a i edytowalna niezależnie od
     * niego. Trzymamy ją osobno od `LeadState`, bo to DWIE różne migawki tego
     * samego katalogu — wspólny stan pokazywałby na obu zakładkach ten sam
     * wybór i kasował sens Remarketingu.
     *
     * Dociągana przy wejściu w zakładkę: to dwa zapytania, a większość wejść
     * w kartę kończy się na „Dane".
     */
    data class RemarketingState(
        val isLoading: Boolean = false,
        val loaded: Boolean = false,
        /** Katalog technologii jako drzewo; pusty = katalogu nie udało się wczytać. */
        val catalog: List<CategoryNode> = emptyList(),
        /** Zaznaczone węzły migawki `edukacja`; `null` = odczyt się nie udał. */
        val selected: Set<String>? = null,
        /** Rozwinięte gałęzie drzewa — stan widoku, ale przeżywa obrót ekranu. */
        val expanded: Set<String> = emptySet(),
        /** Czy API pozwala zmieniać tę migawkę (`editable` z odpowiedzi). */
        val editable: Boolean = false,
        /** Migawka zapisana bez zasięgu — czeka w kolejce na wysyłkę. */
        val pendingSync: Boolean = false,
        val isSaving: Boolean = false,
        /** Zapis OZC w toku — blokuje przycisk okna, żeby nie poszedł dwa razy. */
        val isSavingOzc: Boolean = false,
        val error: String? = null,
    )

    /** Umowa, którą właśnie zmieniamy — formularz pracuje wtedy w trybie zmiany. */
    data class ContractChangeTarget(
        val id: String,
        val numer: String,
        /** Czy TA sesja akceptuje zmiany umów — od tego zależy napis na przycisku. */
        val zarzad: Boolean,
    )

    /** Umowa po terminie odesłania, z której przepisaliśmy treść do formularza. */
    data class ContractExpiredSource(val numer: String, val wystawiona: String)

    /**
     * Formularz umowy — nowej albo zmiany. Jeden kształt na oba przypadki,
     * tak jak w panelu: zmiana poprawia treść, którą klient podpisał, więc
     * pracuje na tych samych polach, co wystawienie.
     */
    data class ContractForm(
        val zmianaDla: ContractChangeTarget? = null,
        val poTerminie: ContractExpiredSource? = null,
        /** Co dostaje klient do podpisu: nowa wersja całej umowy czy aneks. */
        val rodzajZmiany: ContractKind = ContractKind.UMOWA,
        val powodZmiany: String = "",
        val filling: ContractFilling = ContractFilling(),
        /**
         * Czego automat nie policzył z audytu (instalacja bez formuły ceny, luki
         * w cenniku). Pokazujemy WPROST — zaniżona kwota na dokumencie, który
         * klient podpisuje, jest droższa niż komunikat.
         */
        val braki: List<String> = emptyList(),
        /** Trwa przeliczanie Załącznika nr 1 z audytu. */
        val liczenie: Boolean = false,
        val zapis: Boolean = false,
        val blad: String? = null,
        /**
         * Czy do dokumentu dołączamy migawkę materiału. `false` = deal nie ma
         * jeszcze rezerwacji z panelu, więc po podpisie magazyn nie ruszy sam.
         * Formularz mówi to wprost, zamiast po cichu wystawić umowę bez niej.
         */
        val materialZnany: Boolean = false,
    ) {
        /** Warunki wystawienia — te same, co w panelu (`gotowe`). */
        val gotowe: Boolean
            get() = filling.przedmiot.isNotBlank() &&
                filling.podstawaZalacznika.isNotBlank() &&
                filling.pozycje.any { it.opis.isNotBlank() && it.cenaNetto > 0 } &&
                policzPodglad(
                    filling.pozycje,
                    filling.etapy,
                    filling.vatStawka,
                    filling.zaliczkaProc,
                ).sieroty.isEmpty() &&
                // Zmiana bez uzasadnienia nie przejdzie w API — powód drukuje się
                // na dokumencie, bo klient musi wiedzieć, co podpisuje drugi raz.
                (zmianaDla == null || powodZmiany.isNotBlank())
    }

    /** Otwarty podgląd dokumentu — HTML ten sam, z którego powstaje PDF. */
    data class ContractPreviewState(
        val contractId: String,
        val numer: String = "",
        val isLoading: Boolean = true,
        val preview: ContractPreview? = null,
        val error: String? = null,
        /** Link do podpisu tej umowy — pod ręką, gdy handlowiec ogląda dokument. */
        val sciezkaPodpisu: String? = null,
    )

    /**
     * Zakładka „Umowa" — 1:1 z `DealContractPanel`. Lista umów z cache Room,
     * formularz wystawienia i zmiany, podgląd dokumentu.
     *
     * [busyId] blokuje akcje JEDNEJ karty (dwuklik w „Wyślij ponownie" wystawia
     * dwa linki), a nie całej zakładki — zarząd bywa w niej po to, żeby domknąć
     * kilka wniosków pod rząd.
     */
    data class ContractsState(
        val isLoading: Boolean = false,
        val loaded: Boolean = false,
        val contracts: List<DealContract> = emptyList(),
        /** Lista pochodzi z cache — serwer był nieosiągalny. */
        val fromCache: Boolean = false,
        val error: String? = null,
        val busyId: String? = null,
        /** Id umowy, której PDF właśnie pobieramy. */
        val pdfId: String? = null,
        /** `null` = formularz zwinięty. */
        val form: ContractForm? = null,
        val preview: ContractPreviewState? = null,
    ) {
        /** Ile zapisów czeka w kolejce na wysyłkę — pasek nad listą. */
        val pendingCount: Int get() = contracts.count { it.pending.isNotEmpty() }
    }

    /**
     * Zakładka „Zadania" — zadania tego jednego deala, pogrupowane sekcjami
     * (etapami lejka). 1:1 z `TasksBoard` w trybie `sections`: lewa kolumna
     * panelu to u nas nagłówki sekcji, a lista jest ta sama co w module Zadania.
     *
     * Pasek filtrów jest krótszy niż w panelu (ustalenie 2026-09-08): pod jednym
     * dealem zadań są jednostki, więc zostaje „Moje", „Wykonane" i szukajka —
     * pełny arkusz filtrów zostaje w module.
     */
    data class TasksState(
        val isLoading: Boolean = false,
        val loaded: Boolean = false,
        /** Lista pochodzi z cache — serwer był nieosiągalny. */
        val offline: Boolean = false,
        val error: String? = null,
        val query: String = "",
        /**
         * Domyślnie WSZYSTKIE zadania deala, nie tylko własne (ustalenie
         * 2026-09-08): w karcie klienta chodzi o to, co się na nim dzieje,
         * także u innych osób. Panel startuje od „Moje", telefon nie.
         */
        val mineOnly: Boolean = false,
        /** Wykonane są domyślnie schowane; włączone lądują na dole sekcji. */
        val showDone: Boolean = false,
        val sections: List<TaskGroup> = emptyList(),
        /** Zadania z trwającym zapisem — wiersz pokazuje kręciołek zamiast kółka. */
        val busyIds: Set<String> = emptySet(),
        /** Zadania ze zmianą czekającą w kolejce offline. */
        val queuedIds: Set<String> = emptySet(),
        val visibleCount: Int = 0,
        val totalCount: Int = 0,
    )

    /** Sekcja zadań w zakładce; `section == null` to kubełek „Bez sekcji". */
    data class TaskGroup(val section: TaskSection?, val label: String, val items: List<Task>)

    /**
     * Dane do faktury w trybie edycji. Pola są te same, co w formularzu karty
     * deala — zakładka nie zakłada własnych: faktura idzie na dane deala, a nie
     * na kopię trzymaną obok.
     */
    data class BillingForm(
        val jakInstalacji: Boolean = true,
        val odbiorca: String = "",
        val firma: String = "",
        val nip: String = "",
        val adres: String = "",
    )

    /**
     * Zakładka „Faktura" karty deala.
     *
     * Panel ma tu dziś atrapę (drzewo etapu „montaz" + lista montaży) i tyle
     * telefon powtarza 1:1 — [scopeTree] i montaże w [data]. Reszta zakładki
     * odpowiada na pytania, dla których handlowiec w nią wchodzi u klienta:
     * ile jest do zafakturowania ([rachunek], z podpisanej umowy), na kogo
     * idzie faktura ([form], zapisywane wspólną kolejką karty) i czy dokument
     * już wyszedł ([data] — faktury z KSeF).
     */
    data class InvoicesState(
        val isLoading: Boolean = false,
        val loaded: Boolean = false,
        val data: DealInvoices? = null,
        /** Rachunek z aktualnej umowy; `null` = deal jeszcze jej nie ma. */
        val rachunek: InvoiceRachunek? = null,
        /** Zakres z etapu „montaz" — to samo drzewo, co pokazuje panel. */
        val scopeTree: List<CategoryNode> = emptyList(),
        val scope: Set<String> = emptySet(),
        val expanded: Set<String> = emptySet(),
        /** `null` = dane do faktury tylko do odczytu. */
        val form: BillingForm? = null,
        val error: String? = null,
    )

    data class UiState(
        val isLoading: Boolean = true,
        val isSaving: Boolean = false,
        val detail: DealDetail? = null,
        val canManage: Boolean = false,
        /** Zalogowany — filtr „Moje" w zakładce „Zadania" i podpisy wierszy. */
        val currentUserId: String? = null,
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
        val remarketing: RemarketingState = RemarketingState(),
        val audit: AuditState = AuditState(),
        val offer: OfferState = OfferState(),
        val orders: OrdersState = OrdersState(),
        val files: FilesState = FilesState(),
        val schedule: ScheduleState = ScheduleState(),
        val settlement: SettlementState = SettlementState(),
        val contracts: ContractsState = ContractsState(),
        val invoices: InvoicesState = InvoicesState(),
        val tasks: TasksState = TasksState(),
        /**
         * Uprawnienia z `GET /api/me`. Trzymamy CAŁY zestaw, a nie same
         * `deal.manage`: zakładka „Zamówienie" pyta jeszcze o `order.manage`
         * i `inventory.manage`, a dokładanie kolejnych `Boolean`-ów przy każdej
         * nowej zakładce rozjeżdżałoby się z odpowiedzią serwera.
         */
        val permissions: Set<String> = emptySet(),
    ) {
        /** Zakładanie zamówień i odhaczanie pozycji (board360: `order.manage`). */
        val canManageOrders: Boolean get() = PERMISSION_ORDER_MANAGE in permissions

        /** Zmiany w rezerwacji materiału i lista zakupowa (`inventory.manage`). */
        val canManageInventory: Boolean get() = PERMISSION_INVENTORY_MANAGE in permissions

        /** Zatwierdzanie i cofanie rozliczeń (`financial.terms.manage`). */
        val canManageSettlements: Boolean get() = PERMISSION_FINANCIAL_MANAGE in permissions

        /** Podgląd faktur pobranych z KSeF (`ksef.view`) — księgowość. */
        val canViewInvoices: Boolean get() = PERMISSION_KSEF_VIEW in permissions

        /**
         * Zakładanie projektu pod dealem (`projects.manage`). Sam podgląd listy
         * chodzi na `projects.view`, więc zakładka jest dla każdego, kto widzi
         * moduł Projekty — tylko pole „+ Projekt" się bez tego nie pokaże.
         */
        val canManageProjects: Boolean get() = PERMISSION_PROJECTS_MANAGE in permissions

        /**
         * Zakładanie i zmiana zadań (`tasks.manage`). Sam podgląd chodzi na
         * `tasks.view`, więc zakładkę widzi każdy, kto widzi moduł Zadania —
         * bez tego uprawnienia znikają tylko „+" przy sekcjach i odhaczanie.
         */
        val canManageTasks: Boolean get() = PERMISSION_TASKS_MANAGE in permissions

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
                if (_uiState.value.remarketing.loaded) loadRemarketing(force = true)
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
        // Stąd bierze się też id zalogowanego — filtr „Moje" w zakładce
        // „Zadania" musi wiedzieć, czyje zadania zostawić na liście.
        val me = try {
            authRepository.getCurrentUser()
        } catch (_: Exception) {
            null
        }
        val permissions = me?.permissions?.toSet().orEmpty()
        _uiState.update {
            it.copy(
                permissions = permissions,
                canManage = PERMISSION_DEAL_MANAGE in permissions,
                currentUserId = me?.id ?: it.currentUserId,
            )
        }
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
        if (tab == DealTab.EDUKACJA) loadRemarketing()
        if (tab == DealTab.AUDYT) loadAudit()
        if (tab == DealTab.OFERTA) loadOffer()
        if (tab == DealTab.ZAMOWIENIE) loadOrders()
        if (tab == DealTab.PLIKI) loadFiles()
        if (tab == DealTab.HARMONOGRAM) loadSchedule()
        if (tab == DealTab.ZADANIA) loadTasks()
        if (tab == DealTab.ROZLICZENIE) loadSettlement()
        if (tab == DealTab.UMOWA) loadContracts()
        if (tab == DealTab.FAKTURA) loadInvoices()
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

    // ── Zakładka „Remarketing" ───────────────────────────────────────────────

    /**
     * Migawka instalacji etapu `edukacja` plus katalog technologii. Bez katalogu
     * nie ma czego rysować (sama lista id niczego handlowcowi nie mówi), więc
     * jego brak traktujemy jak brak odczytu.
     *
     * Repozytorium odpowiada z cache, gdy sieci nie ma, i dokłada zmiany
     * czekające w kolejce — zakładka nie musi o tym wiedzieć poza znacznikiem
     * `pendingSync`, którym mówi o tym człowiekowi.
     *
     * @param force ponowny odczyt po zapisie/odświeżeniu karty.
     */
    fun loadRemarketing(force: Boolean = false) {
        val state = _uiState.value.remarketing
        if (!force && (state.loaded || state.isLoading)) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(remarketing = it.remarketing.copy(isLoading = true, error = null))
            }

            val loaded = try {
                val snapshot = getDealInstallationsUseCase(dealId)
                    .forStage(InstallationStage.EDUKACJA)
                buildCategoryTree(getCategoriesUseCase()) to snapshot
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        remarketing = it.remarketing.copy(
                            isLoading = false,
                            loaded = true,
                            error = crmErrorMessage(e, "Nie udało się wczytać zakresu instalacji"),
                        ),
                    )
                }
                return@launch
            }

            val (catalog, snapshot) = loaded
            val selected = snapshot?.categoryIds.orEmpty().toSet()
            _uiState.update { s ->
                s.copy(
                    remarketing = s.remarketing.copy(
                        isLoading = false,
                        loaded = true,
                        catalog = catalog,
                        selected = selected,
                        // Gałęzie z wyborem rozwijamy same — wybór schowany dwa
                        // poziomy w głąb wyglądałby jak brak wyboru.
                        expanded = s.remarketing.expanded +
                            ancestorsOfSelected(catalog, selected),
                        editable = snapshot?.editable ?: false,
                        pendingSync = snapshot?.pending ?: false,
                        error = null,
                    ),
                )
            }
        }
    }

    /** Rozwinięcie/zwinięcie gałęzi drzewa Remarketingu. Nic nie zapisuje. */
    fun toggleRemarketingBranch(categoryId: String) {
        _uiState.update { state ->
            val expanded = state.remarketing.expanded
            state.copy(
                remarketing = state.remarketing.copy(
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
     * Zaznaczenie/odznaczenie węzła w migawce etapu `edukacja`. Jak w LEAD:
     * API przyjmuje pełną listę po zmianie, a zapis leci od razu, bez przycisku
     * — to jedno kliknięcie ustalane przy kliencie, a zakres wchodzi dalej do
     * audytu i oferty.
     *
     * Bez zasięgu repozytorium odkłada zapis do kolejki i oddaje migawkę
     * z naniesioną zmianą, więc ekran zachowuje się tak samo jak z siecią —
     * różnicę widać wyłącznie po znaczniku „czeka na wysyłkę".
     */
    fun toggleRemarketingInstallation(categoryId: String) {
        val state = _uiState.value.remarketing
        val current = state.selected ?: return
        if (state.isSaving || !state.editable) return

        val next = if (categoryId in current) current - categoryId else current + categoryId

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    remarketing = it.remarketing.copy(selected = next, isSaving = true),
                    message = null,
                )
            }
            try {
                val saved = setDealInstallationsUseCase(
                    dealId,
                    InstallationStage.EDUKACJA,
                    next.toList(),
                ).forStage(InstallationStage.EDUKACJA)
                val ids = saved?.categoryIds.orEmpty().toSet()
                _uiState.update {
                    it.copy(
                        remarketing = it.remarketing.copy(
                            selected = ids,
                            expanded = it.remarketing.expanded +
                                ancestorsOfSelected(it.remarketing.catalog, ids),
                            pendingSync = saved?.pending ?: false,
                            isSaving = false,
                        ),
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        // Cofamy do stanu sprzed kliknięcia — inaczej ekran
                        // pokazywałby wybór, którego serwer nie przyjął.
                        remarketing = it.remarketing.copy(selected = current, isSaving = false),
                        message = crmErrorMessage(e, "Nie udało się zapisać zakresu instalacji"),
                    )
                }
            }
        }
    }

    /**
     * Zapis OZC z okna „+ OZC" — port `LeadOzcModal` panelu. Moce przepisuje się
     * ręcznie z cieplo.app; walidację 40–50 W/m² robi ekran, tutaj zostaje sam
     * zapis.
     *
     * `areaM2` przychodzi TYLKO wtedy, gdy w „Danych budynku" go brakowało:
     * wpisany w oknie ma dopisać się do bloku budynku, żeby powierzchnia miała
     * jedno źródło prawdy — dokładnie tak jak w panelu.
     */
    fun saveOzc(
        buildingKw: Double,
        dhwKw: Double?,
        sourceUrl: String?,
        confirmed: Boolean,
        areaM2: Int?,
        onSaved: () -> Unit,
    ) {
        val deal = _uiState.value.detail?.deal ?: return
        if (!_uiState.value.canManage || _uiState.value.remarketing.isSavingOzc) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(remarketing = it.remarketing.copy(isSavingOzc = true), message = null)
            }
            try {
                val draft = deal.toDraft().copy(
                    ozcBuildingKw = buildingKw,
                    ozcDhwKw = dhwKw,
                    ozcSourceUrl = sourceUrl,
                    ozcConfirmed = confirmed,
                    areaM2 = areaM2 ?: deal.buildingData?.areaM2?.toInt(),
                )
                val updated = updateDealUseCase(deal, draft)
                _uiState.update { state ->
                    state.copy(
                        remarketing = state.remarketing.copy(isSavingOzc = false),
                        message = "Zapisano OZC.",
                        detail = state.detail?.copy(deal = updated),
                        // Formularz „pozostałych pól" czyta z draftu karty —
                        // po zapisie z okna musi widzieć nowe moce, inaczej
                        // pierwsze wejście w edycję cofnęłoby je.
                        dealDraft = if (state.editing) state.dealDraft else updated.toDraft(),
                        numbers = if (state.editing) state.numbers else updated.toDraft().toNumberText(),
                    )
                }
                onSaved()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        remarketing = it.remarketing.copy(isSavingOzc = false),
                        message = crmErrorMessage(e, "Nie udało się zapisać OZC"),
                    )
                }
            }
        }
    }

    // ── Zakładka „Audyt" ─────────────────────────────────────────────────────

    /**
     * Materiał zakładki: audyty deala, katalog (dla dziedziczenia formularza),
     * migawka instalacji etapu „audit" i stan blokady ofertowej. Każdy odczyt
     * osobno — awaria jednego nie może wygasić pozostałych bloków.
     *
     * @param force ponowny odczyt po zapisie.
     */
    fun loadAudit(force: Boolean = false) {
        val audit = _uiState.value.audit
        if (!force && (audit.loaded || audit.isLoading)) return

        viewModelScope.launch {
            _uiState.update { it.copy(audit = it.audit.copy(isLoading = true, error = null)) }

            var error: String? = null
            val audits = try {
                auditRepository.getAudits(dealId)
            } catch (e: Exception) {
                error = crmErrorMessage(e, "Nie udało się wczytać audytów")
                null
            }
            // Katalog i migawka idą przez repozytorium audytu, a nie przez
            // use case'y karty: tamte są czysto sieciowe, a tu bez zasięgu
            // muszą wrócić z cache — inaczej audytor w domu w budowie zobaczy
            // pustą zakładkę zamiast formularza do wypełnienia.
            val categories = try {
                auditRepository.getCategories()
            } catch (_: Exception) {
                emptyList()
            }
            val snapshot = try {
                auditRepository.getAuditInstallations(dealId)
            } catch (_: Exception) {
                AuditInstallations()
            }
            val lock = auditRepository.getOfferLock(dealId)

            val byId = categories.associateBy { it.id }
            val installations = snapshot.auditStage.map { id ->
                SelectedInstallation(
                    categoryId = id,
                    pathLabel = categoryPath(id, byId).joinToString(" › ").ifBlank { id },
                )
            }

            _uiState.update { state ->
                // Pompa ciepła gdziekolwiek w dealu odsłania pytanie o chłodzenie —
                // po nazwie w ścieżce, tak jak w panelu (id katalogu bywa inne
                // w każdej organizacji).
                val heatPump = snapshot.allStages.any { id ->
                    categoryPath(id, byId).any { HEAT_PUMP_NAME.containsMatchIn(it) }
                }
                val fresh = state.audit.copy(
                    isLoading = false,
                    loaded = true,
                    records = audits ?: state.audit.records,
                    categories = categories,
                    installations = installations,
                    hasHeatPump = heatPump,
                    lock = lock,
                    error = error,
                )
                // Wybór instalacji zostaje, dopóki nadal jest w migawce —
                // odświeżenie po zapisie nie ma przerzucać audytora na inny węzeł.
                val keep = fresh.selectedInstallationId
                    ?.takeIf { id -> installations.any { it.categoryId == id } }
                state.copy(
                    audit = fresh.withInstallation(
                        categoryId = keep ?: installations.firstOrNull()?.categoryId,
                        deal = state.detail?.deal,
                    ),
                )
            }
        }
    }

    /** Przełączenie instalacji, której audyt oglądamy. */
    fun selectAuditInstallation(categoryId: String) {
        _uiState.update { state ->
            if (state.audit.selectedInstallationId == categoryId) return@update state
            // Niezapisane zmiany przepadłyby po cichu — mówimy o tym wprost
            // zamiast blokować przełączenie: audytor bywa w połowie dwóch
            // formularzy naraz i sam wie, który chce dokończyć.
            val warn = if (state.audit.isFormDirty) {
                "Zmiany w poprzednim formularzu nie zostały zapisane"
            } else {
                state.message
            }
            state.copy(
                message = warn,
                audit = state.audit.withInstallation(
                    categoryId = categoryId,
                    deal = state.detail?.deal,
                ),
            )
        }
    }

    /** Zmiana pola formularza audytu instalacji. Nic nie wysyła. */
    fun editAuditForm(edit: (UfhState) -> UfhState) {
        _uiState.update { state ->
            val form = state.audit.form ?: return@update state
            if (state.audit.isFormLocked) return@update state
            state.copy(audit = state.audit.copy(form = edit(form)))
        }
    }

    /**
     * Zapis formularza audytu instalacji. Niekompletny audyt też zapisujemy —
     * audyt bywa uzupełniany na raty, a lista braków jest sygnałem, nie blokadą.
     */
    fun saveAuditForm() {
        val state = _uiState.value
        val audit = state.audit
        val form = audit.form ?: return
        val owner = audit.formOwnerId ?: return
        if (audit.isSavingForm || audit.isFormLocked || !state.canManage) return

        viewModelScope.launch {
            _uiState.update { it.copy(audit = it.audit.copy(isSavingForm = true), message = null) }
            try {
                val result = auditRepository.saveInstallationAudit(
                    dealId = dealId,
                    auditId = audit.formAuditId,
                    categoryId = owner,
                    state = form,
                    includeCooling = audit.hasHeatPump,
                )
                _uiState.update {
                    it.copy(
                        message = savedMessage(result, "Zapisano audyt instalacji"),
                        audit = it.audit.copy(isSavingForm = false),
                        // Oferta liczy się z TEGO audytu, więc po zapisie musi
                        // przeliczyć się od nowa — inaczej zakładka pokazywałaby
                        // klientowi zakres sprzed poprawki. Cennik zostaje:
                        // zmienił się audyt, a nie stawki węzła.
                        offer = it.offer.copy(loaded = false),
                        // Rozliczenie liczy ILOŚCI z tego samego audytu — po
                        // poprawce musi przeliczyć punkty od nowa, inaczej
                        // zarząd zatwierdziłby wynik sprzed zmiany zakresu.
                        settlement = it.settlement.copy(loaded = false),
                    )
                }
                // Rekord wraca z serwera z własnym id — bez tego drugi zapis
                // założyłby DRUGI formularz tego samego węzła.
                loadAudit(force = true)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        audit = it.audit.copy(isSavingForm = false),
                        message = crmErrorMessage(e, "Nie udało się zapisać audytu"),
                    )
                }
            }
        }
    }

    fun editHeatloadDraft(edit: (HeatloadDraft) -> HeatloadDraft) {
        _uiState.update { it.copy(audit = it.audit.copy(draft = edit(it.audit.draft))) }
    }

    /** Nowy wpis Heizlast. Wejścia szybkiego szacunku przelicza serwer. */
    fun saveHeatload() {
        val audit = _uiState.value.audit
        val draft = audit.draft
        if (audit.isSavingHeatload || !_uiState.value.canManage) return
        if (!draft.isSubmittable) {
            _uiState.update {
                it.copy(
                    message = when (draft.mode) {
                        HeatloadMode.SZYBKI -> "Podaj powierzchnię i standard budynku"
                        HeatloadMode.DIN -> "Podaj dodatni wynik Heizlast (kW)"
                        null -> "Wybierz tryb Heizlast albo wpisz notatkę"
                    },
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(audit = it.audit.copy(isSavingHeatload = true), message = null)
            }
            try {
                val result = auditRepository.createHeatload(
                    dealId = dealId,
                    mode = draft.mode,
                    areaM2 = draft.areaM2.toM2(),
                    standard = draft.standard,
                    heightM = draft.heightM.toM2(),
                    kw = draft.kw.toM2(),
                    note = draft.note,
                )
                _uiState.update {
                    it.copy(
                        message = savedMessage(result, "Zapisano audyt"),
                        audit = it.audit.copy(
                            isSavingHeatload = false,
                            draft = HeatloadDraft(),
                        ),
                    )
                }
                loadAudit(force = true)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        audit = it.audit.copy(isSavingHeatload = false),
                        message = crmErrorMessage(e, "Nie udało się zapisać audytu"),
                    )
                }
            }
        }
    }

    /**
     * Potwierdzenie zapisu. Przy braku zasięgu mówimy WPROST, że praca siedzi
     * w telefonie i pójdzie sama — audytor u klienta musi wiedzieć, czy może
     * wyjść z budynku, a samo „zapisano" znaczyłoby dla niego „panel już to ma".
     */
    private fun savedMessage(result: AuditSaveResult, sent: String): String = when (result) {
        AuditSaveResult.SENT -> sent
        AuditSaveResult.QUEUED -> "$sent w telefonie — wyślemy, gdy wróci zasięg"
    }

    // ── Zakładka „Oferta" ────────────────────────────────────────────────────

    /**
     * Etapy od oferty w dół — zakres, który wyceniamy. Zaczynamy od migawki
     * etapu „Oferta", a gdy pusta, bierzemy pierwszy wypełniony wcześniejszy
     * (audyt zwykle wyprzedza pasek oferty). Kolejność 1:1 z panelem.
     */
    private val stagesFromOffer = listOf(
        InstallationStage.ANGEBOT,
        InstallationStage.AUDIT,
        InstallationStage.SOLD,
        InstallationStage.MONTAZ,
        InstallationStage.EDUKACJA,
        InstallationStage.LEAD,
    )

    private fun offerInstallationIds(snapshot: AuditInstallations): List<String> {
        for (stage in stagesFromOffer) {
            val ids = snapshot.byStage[stage.wire].orEmpty()
            if (ids.isNotEmpty()) return ids
        }
        // Migawka sprzed dopisania `byStage` (cache starszego wydania) — zostają
        // etapy, które trzymamy w osobnych polach.
        return snapshot.auditStage.ifEmpty { snapshot.soldStage }
    }

    /**
     * Materiał zakładki: audyty deala, katalog (dziedziczenie formularza),
     * migawka instalacji i blokada ofertowa — te same odczyty, co „Audyt",
     * więc drugie wejście idzie już z cache repozytorium.
     *
     * @param force ponowny odczyt (np. po zapisie audytu w sąsiedniej zakładce).
     */
    fun loadOffer(force: Boolean = false) {
        val offer = _uiState.value.offer
        if (!force && (offer.loaded || offer.isLoading)) return

        viewModelScope.launch {
            _uiState.update { it.copy(offer = it.offer.copy(isLoading = true, error = null)) }

            var error: String? = null
            val audits = try {
                auditRepository.getAudits(dealId)
            } catch (e: Exception) {
                error = crmErrorMessage(e, "Nie udało się wczytać danych wyceny")
                emptyList()
            }
            val categories = try {
                auditRepository.getCategories()
            } catch (_: Exception) {
                emptyList()
            }
            val snapshot = try {
                auditRepository.getAuditInstallations(dealId)
            } catch (_: Exception) {
                AuditInstallations()
            }
            // Cisza przy błędzie: pasek blokady jest informacją, a nie warunkiem
            // pokazania oferty — zapisu i tak pilnuje API przy audycie.
            val lock = auditRepository.getOfferLock(dealId)

            val byId = categories.associateBy { it.id }
            val forms = audits.filter { it.installationForm != null }
            val installations = offerInstallationIds(snapshot).map { id ->
                val owner = resolveAuditForm(id, byId)
                // 1) rekord dokładnie dla tego węzła; 2) zapis sprzed wprowadzenia
                //    `categoryId` (jeden formularz na cały deal).
                val record = owner?.let { o ->
                    forms.firstOrNull { it.categoryId == o.id }
                        ?: forms.firstOrNull { it.categoryId == null }
                }
                OfferInstallation(
                    categoryId = id,
                    name = byId[id]?.name ?: "(nieznana instalacja)",
                    path = categoryPath(id, byId),
                    formOwnerId = owner?.id,
                    formOwnerName = owner?.name.orEmpty(),
                    formOwnerPath = owner?.let { categoryIdPath(it.id, byId) }.orEmpty(),
                    // Oferta idzie WYŁĄCZNIE z zapisanego audytu — szablonu
                    // katalogu tu nie podstawiamy, bo to pytania bez odpowiedzi.
                    form = record?.installationForm,
                )
            }

            // Pasek etapu zwykle niesie kilka instalacji, a audyt OP jest
            // wypełniony tylko na jednej — otwieranie panelu na pierwszej
            // z brzegu pokazywało „brak audytu" i wyglądało jak pusta zakładka.
            val withAudit = installations.indexOfFirst { it.form != null }

            _uiState.update { state ->
                state.copy(
                    offer = state.offer.copy(
                        isLoading = false,
                        loaded = true,
                        installations = installations,
                        selectedIndex = if (withAudit >= 0) withAudit else 0,
                        lock = lock,
                        error = error,
                    ),
                )
            }
            loadOfferPricing()
        }
    }

    /** Przełączenie instalacji, której ofertę oglądamy. */
    fun selectOfferInstallation(index: Int) {
        _uiState.update { state ->
            if (index == state.offer.selectedIndex) return@update state
            state.copy(offer = state.offer.copy(selectedIndex = index))
        }
        loadOfferPricing()
    }

    fun setOfferMode(mode: DealDetailViewModel.OfferMode) {
        _uiState.update { it.copy(offer = it.offer.copy(mode = mode)) }
        // Kwoty widać dopiero w „Podsumowaniu", ale cennik dociągamy i tak przy
        // wejściu w zakładkę: przełączenie widoku ma być natychmiastowe.
        loadOfferPricing()
    }

    /**
     * Cennik jednostkowy per WĘZEŁ-właściciel formularza audytu — to on trzyma
     * stawki, koszty i narzut. Jeden odczyt na instalację, wynik zostaje
     * w pamięci karty (`null` = węzeł bez formuły ceny, np. inna instalacja).
     */
    private fun loadOfferPricing() {
        val state = _uiState.value
        val active = state.offer.active ?: return
        val owner = active.formOwnerId ?: return
        if (state.offer.pricing.containsKey(owner) || state.offer.pricingLoading) return

        viewModelScope.launch {
            _uiState.update { it.copy(offer = it.offer.copy(pricingLoading = true)) }
            val pricing = try {
                offerPricingRepository.getPricing(
                    categoryId = owner,
                    categoryName = active.formOwnerName,
                    categoryIdPath = active.formOwnerPath,
                )
            } catch (_: Exception) {
                null
            }
            _uiState.update {
                it.copy(
                    offer = it.offer.copy(
                        pricing = it.offer.pricing + (owner to pricing),
                        pricingLoading = false,
                    ),
                )
            }
        }
    }

    // ── Zakładka „Rozliczenie" ───────────────────────────────────────────────

    /**
     * Etapy od montażu w dół — rozliczamy zakres MONTAŻOWY. Gdy migawka etapu
     * „Montaż" jest pusta, schodzimy niżej: deal przed montażem rozliczy się
     * z tego, co ustalono na sprzedaży/ofercie. Kolejność 1:1 z panelem
     * (`STAGES_FROM_MONTAGE` w `deal-settlement.ts`).
     */
    private val stagesFromMontage = listOf(
        InstallationStage.MONTAZ,
        InstallationStage.SOLD,
        InstallationStage.ANGEBOT,
        InstallationStage.AUDIT,
        InstallationStage.EDUKACJA,
        InstallationStage.LEAD,
    )

    /** Zakres do rozliczenia + etap, z którego go wzięliśmy (do pokazania). */
    private fun settlementScope(
        snapshot: AuditInstallations,
    ): Pair<InstallationStage?, List<String>> {
        for (stage in stagesFromMontage) {
            val ids = snapshot.byStage[stage.wire].orEmpty()
            if (ids.isNotEmpty()) return stage to ids
        }
        // Migawka sprzed dopisania `byStage` (cache starszego wydania) — zostają
        // etapy, które trzymamy w osobnych polach.
        val legacy = snapshot.soldStage.ifEmpty { snapshot.auditStage }
        return null to legacy
    }

    /**
     * Materiał zakładki: audyty deala, katalog, migawka instalacji, zestawy
     * punktowe i zatwierdzone rozliczenia. Pierwsze trzy odczyty są te same, co
     * w „Audycie" i „Ofercie", więc drugie wejście idzie już z cache.
     *
     * Rachunku tu NIE ma — geometria rzutu potrafi zająć chwilę, więc liczy go
     * zakładka poza wątkiem głównym, tak samo jak przy ofercie.
     *
     * @param force ponowny odczyt (po zapisie audytu w sąsiedniej zakładce).
     */
    fun loadSettlement(force: Boolean = false) {
        val settlement = _uiState.value.settlement
        if (!force && (settlement.loaded || settlement.isLoading)) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(settlement = it.settlement.copy(isLoading = true, error = null))
            }

            var error: String? = null
            val audits = try {
                auditRepository.getAudits(dealId)
            } catch (e: Exception) {
                error = crmErrorMessage(e, "Nie udało się wczytać danych rozliczenia")
                emptyList()
            }
            val categories = try {
                auditRepository.getCategories()
            } catch (_: Exception) {
                emptyList()
            }
            val snapshot = try {
                auditRepository.getAuditInstallations(dealId)
            } catch (_: Exception) {
                AuditInstallations()
            }
            // Cisza przy błędzie cennika: pusty cennik znaczy rachunek bez
            // stawek z wypisanymi brakami, a nie zakładkę bez treści.
            val schemes = try {
                settlementRepository.getSchemes()
            } catch (_: Exception) {
                emptyList()
            }
            val snapshots = try {
                settlementRepository.getSettlements(dealId)
            } catch (_: Exception) {
                emptyList()
            }

            val byId = categories.associateBy { it.id }
            val forms = audits.filter { it.installationForm != null }
            val (stage, categoryIds) = settlementScope(snapshot)
            val installations = categoryIds.map { id ->
                val owner = resolveAuditForm(id, byId)
                val record = owner?.let { o ->
                    forms.firstOrNull { it.categoryId == o.id }
                        ?: forms.firstOrNull { it.categoryId == null }
                }
                SettlementInstallation(
                    categoryId = id,
                    name = byId[id]?.name ?: "(nieznana instalacja)",
                    path = categoryPath(id, byId),
                    formOwnerId = owner?.id,
                    form = record?.installationForm,
                    // Zestawy dziedziczą się w dół drzewa — „Montaż" wisi zwykle
                    // pod korzeniem technologii, a audyt niżej.
                    rates = ratesForPath(schemes, categoryIdPath(id, byId)),
                )
            }

            _uiState.update { state ->
                state.copy(
                    settlement = state.settlement.copy(
                        isLoading = false,
                        loaded = true,
                        stage = stage,
                        installations = installations,
                        snapshots = snapshots,
                        error = error,
                    ),
                )
            }
        }
    }

    /** Rozwinięcie/zwinięcie rozbicia instalacji na pozycje. */
    fun toggleSettlementDetails(categoryId: String) {
        _uiState.update { state ->
            val open = state.settlement.expanded
            state.copy(
                settlement = state.settlement.copy(
                    expanded = if (categoryId in open) open - categoryId else open + categoryId,
                ),
            )
        }
    }

    /**
     * Zatwierdzenie zamraża wynik policzony przez telefon — dlatego suma
     * i rozbicie przychodzą z zakładki, a nie liczą się tu drugi raz. Inaczej
     * człowiek zatwierdzałby liczbę, której nie widział na ekranie.
     */
    fun approveSettlement(
        categoryId: String,
        totalPoints: Double,
        breakdown: JsonObject,
    ) {
        val name = _uiState.value.settlement.installations
            .firstOrNull { it.categoryId == categoryId }?.name.orEmpty()
        runSettlement(
            action = {
                settlementRepository.approve(dealId, categoryId, totalPoints, breakdown)
            },
            sent = "Zatwierdzono rozliczenie: $name.",
            failure = "Nie udało się zatwierdzić rozliczenia",
        )
    }

    fun revokeSettlement(categoryId: String) {
        runSettlement(
            action = { settlementRepository.revoke(dealId, categoryId) },
            sent = "Cofnięto zatwierdzenie — rozliczenie znów liczy się na żywo.",
            failure = "Nie udało się cofnąć zatwierdzenia",
        )
    }

    /**
     * Wspólna obsługa obu decyzji: blokada przycisków, komunikat i odświeżenie
     * migawek. Kolejka mówi WPROST, że decyzja siedzi w telefonie — samo
     * „zatwierdzono" znaczyłoby dla zarządu „panel już to ma".
     */
    private fun runSettlement(
        action: suspend () -> SettlementSaveResult,
        sent: String,
        failure: String,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(settlement = it.settlement.copy(busy = true)) }
            try {
                val result = action()
                val snapshots = try {
                    settlementRepository.getSettlements(dealId)
                } catch (_: Exception) {
                    _uiState.value.settlement.snapshots
                }
                _uiState.update {
                    it.copy(
                        settlement = it.settlement.copy(busy = false, snapshots = snapshots),
                        message = when (result) {
                            SettlementSaveResult.SENT -> sent
                            SettlementSaveResult.QUEUED ->
                                "$sent Zapisane w telefonie — wyślemy, gdy wróci zasięg."
                        },
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        settlement = it.settlement.copy(busy = false),
                        message = crmErrorMessage(e, failure),
                    )
                }
            }
        }
    }

    // ── Zakładka „Zamówienie" ────────────────────────────────────────────────

    /**
     * Materiał zakładki: zamówienia deala z ofertami i rezerwacją materiału
     * (jedno wywołanie repozytorium — ono rozdziela cztery odczyty i kolejkę)
     * plus zakres etapu „sold" na drzewo.
     *
     * Zakres idzie przez repozytorium audytu, a nie przez use case'y karty:
     * tamte są czysto sieciowe, a tu drzewo ma się narysować także bez zasięgu —
     * magazynier pakuje towar w hali, nie przy biurku.
     *
     * @param force ponowny odczyt po zapisie.
     */
    fun loadOrders(force: Boolean = false) {
        val orders = _uiState.value.orders
        if (!force && (orders.loaded || orders.isLoading)) return

        viewModelScope.launch {
            _uiState.update { it.copy(orders = it.orders.copy(isLoading = true, error = null)) }

            var error: String? = null
            val snapshot = try {
                orderRepository.getDealOrders(dealId)
            } catch (e: Exception) {
                error = crmErrorMessage(e, "Nie udało się wczytać zamówień")
                null
            }
            val categories = try {
                auditRepository.getCategories()
            } catch (_: Exception) {
                emptyList()
            }
            val scope = try {
                auditRepository.getAuditInstallations(dealId).soldStage.toSet()
            } catch (_: Exception) {
                emptySet()
            }
            val tree = pruneToSelected(buildCategoryTree(categories), scope)

            _uiState.update { state ->
                val fresh = state.orders.copy(
                    isLoading = false,
                    loaded = true,
                    orders = snapshot?.orders ?: state.orders.orders,
                    offers = snapshot?.offers ?: state.orders.offers,
                    reservations = snapshot?.reservations ?: state.orders.reservations,
                    purchases = snapshot?.purchases ?: state.orders.purchases,
                    ordersAvailable = snapshot?.ordersAvailable ?: state.orders.ordersAvailable,
                    materialsAvailable = snapshot?.materialsAvailable
                        ?: state.orders.materialsAvailable,
                    fromCache = snapshot?.fromCache ?: state.orders.fromCache,
                    scopeTree = tree,
                    scope = scope,
                    // Zakres jest tu PODGLĄDEM, nie wyborem — rozwijamy więc
                    // wszystkie gałęzie z zaznaczeniem, żeby magazynier zobaczył
                    // kupione instalacje bez ani jednego dotknięcia. Ręczne
                    // zwinięcia zostają, bo dokładamy tylko brakujące gałęzie.
                    expanded = state.orders.expanded + ancestorsOfSelected(tree, scope),
                    error = error,
                )
                // Oferta wskazana w selektorze zostaje, dopóki nadal jest wygrana;
                // inaczej po odświeżeniu przycisk „Utwórz zamówienie" celowałby
                // w ofertę, której na liście już nie ma.
                val keep = fresh.selectedOfferId
                    ?.takeIf { id -> fresh.wonOffers.any { it.id == id } }
                state.copy(
                    orders = fresh.copy(
                        selectedOfferId = keep ?: fresh.wonOffers.singleOrNull()?.id,
                    ),
                )
            }
        }
    }

    /** Rozwinięcie gałęzi drzewa zakresu. Wyboru NIE zmieniamy — to podgląd. */
    fun toggleOrderScopeBranch(categoryId: String) {
        _uiState.update { state ->
            val expanded = state.orders.expanded
            state.copy(
                orders = state.orders.copy(
                    expanded = if (categoryId in expanded) expanded - categoryId
                    else expanded + categoryId,
                ),
            )
        }
    }

    fun selectOffer(offerId: String) {
        _uiState.update { it.copy(orders = it.orders.copy(selectedOfferId = offerId)) }
    }

    /**
     * Zamówienie z wygranej oferty. Zamówienia powstają zwykle SAME z podpisanej
     * umowy — to jest droga ręczna, dla deali sprzedanych z samej oferty.
     */
    fun createOrder() {
        val state = _uiState.value
        val offerId = state.orders.selectedOfferId ?: return
        if (state.orders.isSaving) return
        if (!state.canManageOrders) {
            _uiState.update { it.copy(message = "Brak uprawnień do zamówień (order.manage)") }
            return
        }

        runOrderAction(
            action = { orderRepository.createOrder(dealId, offerId) },
            sent = "Utworzono zamówienie",
            failed = "Nie udało się utworzyć zamówienia",
        )
    }

    /**
     * Ptaszek przy pozycji zamówienia. Wysyłamy dokładnie jedno pole — API
     * zostawia drugie nietknięte, więc odhaczenie „odebrane" nie cofa
     * „zamówione" postawionego wcześniej w panelu.
     */
    fun setOrderItem(orderId: String, itemId: String, ordered: Boolean? = null, received: Boolean? = null) {
        val state = _uiState.value
        if (state.orders.isSaving) return
        if (!state.canManageOrders) {
            _uiState.update { it.copy(message = "Brak uprawnień do zamówień (order.manage)") }
            return
        }

        runOrderAction(
            action = {
                orderRepository.setOrderItem(
                    dealId = dealId,
                    orderId = orderId,
                    itemId = itemId,
                    ordered = ordered,
                    received = received,
                )
            },
            sent = "Zapisano pozycję",
            failed = "Nie udało się zapisać pozycji",
        )
    }

    /** „Wydane" / „Zwolnij" / „Przywróć" przy linii rezerwacji materiału. */
    fun setReservationStatus(reservationId: String, status: String) {
        val state = _uiState.value
        if (state.orders.isSaving) return
        if (!state.canManageInventory) {
            _uiState.update { it.copy(message = "Brak uprawnień do magazynu (inventory.manage)") }
            return
        }

        runOrderAction(
            action = { orderRepository.setReservationStatus(dealId, reservationId, status) },
            sent = when (status) {
                "done" -> "Materiał oznaczony jako wydany"
                "cancelled" -> "Rezerwacja zwolniona"
                else -> "Rezerwacja przywrócona"
            },
            failed = "Nie udało się zmienić rezerwacji",
        )
    }

    /**
     * Braki na listę zakupową magazynu — tylko te, których nie objął jeszcze
     * żaden zakup. Reszta ma propozycję albo zamówienie w drodze, więc drugie
     * kliknięcie kupiłoby ten sam towar podwójnie.
     */
    fun orderMissingMaterials() {
        val state = _uiState.value
        val rows = state.orders.unorderedReservations
        if (state.orders.isSaving || rows.isEmpty()) return
        if (!state.canManageInventory) {
            _uiState.update { it.copy(message = "Brak uprawnień do magazynu (inventory.manage)") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(orders = it.orders.copy(isSaving = true), message = null) }
            var queued = 0
            var failed = 0
            for (row in rows) {
                val productId = row.productId ?: continue
                try {
                    val result = orderRepository.orderMissing(
                        dealId = dealId,
                        reservationId = row.id,
                        productId = productId,
                        quantity = row.missing,
                        clientLabel = row.clientLabel,
                    )
                    if (result == OrderSaveResult.QUEUED) queued++
                } catch (_: Exception) {
                    failed++
                }
            }
            _uiState.update {
                it.copy(
                    orders = it.orders.copy(isSaving = false),
                    message = when {
                        failed == rows.size -> "Nie udało się dopisać braków do listy zakupowej"
                        failed > 0 -> "$failed z ${rows.size} pozycji nie weszło na listę zakupową"
                        queued > 0 -> "Dodano ${rows.size} poz. w telefonie — wyślemy, gdy wróci zasięg"
                        else -> "Dodano ${rows.size} poz. na listę zakupową magazynu"
                    },
                )
            }
            loadOrders(force = true)
        }
    }

    /**
     * Wspólna obsługa zapisów zakładki: blokada podwójnego dotknięcia, komunikat
     * rozróżniający wysłane od zakolejkowanego i odświeżenie. Cztery akcje
     * różnią się wyłącznie treścią, więc trzymanie czterech kopii tej ramy
     * kończyłoby się rozjazdem komunikatów.
     */
    private fun runOrderAction(
        action: suspend () -> OrderSaveResult,
        sent: String,
        failed: String,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(orders = it.orders.copy(isSaving = true), message = null) }
            try {
                val result = action()
                _uiState.update {
                    it.copy(
                        orders = it.orders.copy(isSaving = false),
                        message = when (result) {
                            OrderSaveResult.SENT -> sent
                            OrderSaveResult.QUEUED ->
                                "$sent w telefonie — wyślemy, gdy wróci zasięg"
                        },
                    )
                }
                loadOrders(force = true)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        orders = it.orders.copy(isSaving = false),
                        message = crmErrorMessage(e, failed),
                    )
                }
            }
        }
    }

    /**
     * Wybór instalacji + dobranie formularza: szablon dziedziczymy z najbliższego
     * przodka z `auditForm`, a dane deala z rekordu przypiętego do tego przodka.
     * Bez rekordu startujemy z szablonu uzupełnionego danymi budynku — dokładnie
     * jak panel, żeby pierwszy audyt wyglądał tak samo z telefonu i z biurka.
     */
    private fun AuditState.withInstallation(
        categoryId: String?,
        deal: Deal?,
    ): AuditState {
        if (categoryId == null) {
            return copy(
                selectedInstallationId = null,
                formOwnerId = null,
                formAuditId = null,
                form = null,
                savedForm = null,
            )
        }

        val owner = resolveAuditForm(categoryId, categories.associateBy { it.id })
        val template = owner?.auditForm
        if (owner == null || template == null) {
            return copy(
                selectedInstallationId = categoryId,
                formOwnerId = null,
                formAuditId = null,
                form = null,
                savedForm = null,
            )
        }

        val forms = records.filter { it.installationForm != null }
        // 1) rekord dokładnie dla tego węzła; 2) zapis sprzed wprowadzenia
        //    `categoryId` (jeden formularz na cały deal).
        val record = forms.firstOrNull { it.categoryId == owner.id }
            ?: forms.firstOrNull { it.categoryId == null }

        // Brak rekordu deala → start z szablonu katalogu, uzupełnionego danymi
        // budynku (ilość i nazwy kondygnacji) — czyli dziedziczenie.
        val form = record?.installationForm ?: applyBuildingToUfh(
            template,
            deal?.buildingData?.floors,
            deal?.buildingData?.heatedBasement == true,
        )
        return copy(
            selectedInstallationId = categoryId,
            formOwnerId = owner.id,
            formAuditId = record?.id,
            form = form,
            savedForm = form,
        )
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
     * Miejsce i termin AUDYTU — osobne pola deala niż spotkanie wstępne
     * (`meetingKind`/`meetingAt`), bo to dwa różne wyjazdy do klienta.
     */
    fun setAuditAddressKind(kind: AuditAddressKind) =
        patchDeal("Zapisano miejsce audytu") { it.copy(auditAddressKind = kind) }

    fun setAuditMeetingAt(millis: Long?) =
        patchDeal(if (millis == null) "Usunięto termin audytu" else "Zapisano termin audytu") {
            it.copy(auditMeetingAt = millis)
        }

    /**
     * Zmiana jednego pola karty prosto z zakładki, bez wchodzenia w formularz.
     * Idzie tą samą drogą co „Edytuj" (`PATCH` z różnicy draftu), więc wysyła
     * wyłącznie to jedno pole i nie nadpisuje zmian zrobionych równolegle
     * w panelu. Po zapisie odświeżamy kartę cicho — deal wraca z serwera i to on
     * jest źródłem prawdy, a nie nasze założenie o wyniku.
     */
    private fun patchDeal(
        success: String,
        onSaved: () -> Unit = {},
        edit: (DealDraft) -> DealDraft,
    ) {
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
                onSaved()
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

    // ── Zakładka „Pliki" ─────────────────────────────────────────────────────

    /**
     * Pliki deala plus piwnica/garaż ze zgłoszenia. Ten drugi odczyt jest
     * fallbackiem slotów „Projekt domu": gdy w „Danych" nikt nie zaznaczył
     * ogrzewanej piwnicy, wiadomo o niej ze zgłoszenia z leadowni i slot ma się
     * pojawić tak samo jak w panelu.
     *
     * @param force ponowny odczyt po zapisie albo po odświeżeniu karty.
     */
    fun loadFiles(force: Boolean = false) {
        val files = _uiState.value.files
        if (!force && (files.loaded || files.isLoading)) return

        viewModelScope.launch {
            _uiState.update { it.copy(files = it.files.copy(isLoading = true, error = null)) }

            val snapshot = try {
                dealDocumentRepository.getDocuments(dealId)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        files = it.files.copy(
                            isLoading = false,
                            loaded = true,
                            error = crmErrorMessage(e, "Nie udało się wczytać plików"),
                        ),
                    )
                }
                return@launch
            }

            // Zgłoszenie ciągniemy tylko raz: to dodatkowe zapytanie, a piwnica
            // i garaż nie zmieniają się w trakcie oglądania plików.
            val lead = if (files.loaded) null else runCatching { getLeadIntakeUseCase(dealId) }
                .getOrNull()

            _uiState.update { state ->
                state.copy(
                    files = state.files.copy(
                        isLoading = false,
                        loaded = true,
                        documents = snapshot.documents,
                        leadBasement = lead?.building?.heatedBasement ?: state.files.leadBasement,
                        leadGarage = lead?.building?.heatedGarage ?: state.files.leadGarage,
                        offline = snapshot.offline,
                        error = snapshot.error,
                    ),
                )
            }
        }
    }

    /**
     * Wgranie pliku do wskazanej sekcji. `category = null` znaczy „wykryj sekcję
     * automatycznie" — rozstrzyga to serwer, więc do wysyłki plik leży
     * w „Pozostałe".
     *
     * Odrzucamy wszystko poza zdjęciami i PDF-ami, dokładnie jak panel. Systemowy
     * wybór pliku da się obejść (udostępnianie z innej aplikacji), a plik, którego
     * API i tak nie przyjmie, nie ma po co jechać przez kolejkę.
     */
    fun uploadFile(
        name: String,
        contentType: String,
        bytes: ByteArray,
        category: DocumentCategory?,
        slot: String? = null,
    ) {
        if (!isImageOrPdfUpload(name, contentType)) {
            _uiState.update { it.copy(message = "Dozwolone są tylko zdjęcia i pliki PDF.") }
            return
        }
        if (bytes.size > MAX_DOCUMENT_BYTES) {
            _uiState.update { it.copy(message = "Plik jest większy niż 25 MB — board360 go nie przyjmie.") }
            return
        }
        // Slot rzutu jest cechą NAZWY pliku, nie osobnym polem — ta sama
        // konwencja co w panelu, więc audyt zobaczy rzut wgrany z telefonu.
        val finalName = slot?.let { withSlot(it, name) } ?: name
        val finalCategory = if (slot != null) DocumentCategory.PROJEKT else category

        viewModelScope.launch {
            _uiState.update { it.copy(files = it.files.copy(busy = true)) }
            val added = runCatching {
                dealDocumentRepository.upload(dealId, finalName, contentType, finalCategory, bytes)
            }.getOrNull()
            _uiState.update {
                it.copy(
                    files = it.files.copy(busy = false),
                    message = if (added == null) "Nie udało się zapisać pliku na telefonie." else null,
                )
            }
            if (added != null) refreshFiles()
        }
    }

    /**
     * „Odbicie" pliku na slot rzutu — kopia dokumentu (albo pojedynczej strony
     * PDF-a) wgrana jeszcze raz, z prefiksem slotu w nazwie. Oryginał zostaje:
     * ten sam PDF bywa źródłem kilku rzutów.
     *
     * Panel pobiera stronę z `/preview/<n>`; telefon renderuje ją u siebie, więc
     * odbicie działa też bez zasięgu — a wtedy i tak wszystko idzie kolejką.
     */
    fun mirrorToSlot(slotKey: String, document: DealDocument, page: Int? = null) {
        val inSlot = _uiState.value.files.documents.count { it.slot == slotKey }
        if (inSlot >= slotLimit(slotKey)) {
            _uiState.update {
                it.copy(message = "Slot „${slotLabel(slotKey)}” jest pełny (max ${slotLimit(slotKey)}).")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(files = it.files.copy(busy = true)) }
            val base = document.displayName.removeSuffix(".pdf").removeSuffix(".PDF")
            val payload: Pair<ByteArray, Pair<String, String>>? = if (page != null) {
                documentFiles.pageJpeg(document, page)
                    ?.let { it to ("$base — str. $page.jpg" to "image/jpeg") }
            } else {
                documentFiles.content(document)?.readBytes()
                    ?.let { it to (document.displayName to document.contentType) }
            }

            if (payload == null) {
                _uiState.update {
                    it.copy(
                        files = it.files.copy(busy = false),
                        message = "Nie udało się pobrać treści pliku — spróbuj z zasięgiem.",
                    )
                }
                return@launch
            }

            val (bytes, meta) = payload
            val added = runCatching {
                dealDocumentRepository.upload(
                    dealId = dealId,
                    name = withSlot(slotKey, meta.first),
                    contentType = meta.second,
                    category = DocumentCategory.PROJEKT,
                    bytes = bytes,
                )
            }.getOrNull()
            _uiState.update {
                it.copy(
                    files = it.files.copy(busy = false),
                    message = if (added == null) "Nie udało się przypisać miniatury." else null,
                )
            }
            if (added != null) refreshFiles()
        }
    }

    fun moveFile(document: DealDocument, category: DocumentCategory) {
        viewModelScope.launch {
            runCatching { dealDocumentRepository.setCategory(document, category) }
            _uiState.update { it.copy(message = "Przeniesiono do „${category.label}”.") }
            refreshFiles()
        }
    }

    fun deleteFile(document: DealDocument) {
        viewModelScope.launch {
            runCatching { dealDocumentRepository.delete(document) }
            refreshFiles()
        }
    }

    /**
     * Zapis przygotowania rzutu. Pusty rzut (bez skali i bez obrysów) KASUJE
     * przygotowanie — tak samo jak `prepEmpty` w panelu.
     */
    fun savePlanPrep(document: DealDocument, prep: PlanPrep) {
        viewModelScope.launch {
            _uiState.update { it.copy(files = it.files.copy(busy = true)) }
            runCatching {
                dealDocumentRepository.setPlanData(
                    document = document,
                    planData = if (prepEmpty(prep)) null else planPrepToJson(prep),
                )
            }
            _uiState.update { it.copy(files = it.files.copy(busy = false)) }
            refreshFiles()
        }
    }

    /** Odczyt po zapisie — bez spinnera, karta ma tylko pokazać nowy stan. */
    private suspend fun refreshFiles() {
        val snapshot = runCatching { dealDocumentRepository.getDocuments(dealId) }.getOrNull()
            ?: return
        _uiState.update {
            it.copy(
                files = it.files.copy(
                    documents = snapshot.documents,
                    offline = snapshot.offline,
                    error = snapshot.error,
                ),
            )
        }
    }

    // ── Zakładka „Harmonogram" ───────────────────────────────────────────────

    /**
     * Projekty przypięte do deala. Odczyt idzie przez repozytorium modułu
     * Projekty, więc lista otwiera się z cache także bez zasięgu — a projekt
     * założony w terenie widać na niej od razu, z podpisem o kolejce.
     *
     * @param force ponowny odczyt po założeniu projektu albo po „Spróbuj ponownie".
     */
    fun loadSchedule(force: Boolean = false) {
        val schedule = _uiState.value.schedule
        if (!force && (schedule.loaded || schedule.isLoading)) return

        viewModelScope.launch {
            _uiState.update { it.copy(schedule = it.schedule.copy(isLoading = true, error = null)) }
            val snapshot = projectRepository.getDealProjects(dealId)
            _uiState.update {
                it.copy(
                    schedule = it.schedule.copy(
                        isLoading = false,
                        loaded = true,
                        projects = snapshot.projects,
                        offline = snapshot.offline,
                        error = snapshot.error,
                    ),
                )
            }
        }
    }

    fun onNewProjectNameChange(name: String) {
        _uiState.update { it.copy(schedule = it.schedule.copy(newName = name)) }
    }

    /**
     * Nowy projekt pod dealem — odpowiednik „+ Projekt" z panelu. Bez zasięgu
     * zapis ląduje w kolejce i mówimy o tym wprost, zamiast udawać wysyłkę.
     */
    fun createDealProject() {
        val name = _uiState.value.schedule.newName.trim()
        if (name.isEmpty() || _uiState.value.schedule.busy) return

        viewModelScope.launch {
            _uiState.update { it.copy(schedule = it.schedule.copy(busy = true)) }
            val result = runCatching { projectRepository.createDealProject(dealId, name) }
            _uiState.update { state ->
                state.copy(
                    schedule = state.schedule.copy(
                        busy = false,
                        // Nazwę czyścimy tylko po udanym zapisie — po odmowie
                        // serwera człowiek ma poprawić wpis, a nie pisać od nowa.
                        newName = if (result.isSuccess) "" else state.schedule.newName,
                    ),
                    message = when (result.getOrNull()) {
                        DealProjectSaveResult.SENT -> "Utworzono projekt dla deala."
                        DealProjectSaveResult.QUEUED ->
                            "Brak zasięgu — projekt poleci, gdy wróci sieć."
                        null -> crmErrorMessage(
                            result.exceptionOrNull() ?: Exception(),
                            "Nie udało się utworzyć projektu",
                        )
                    },
                )
            }
            if (result.isSuccess) loadSchedule(force = true)
        }
    }

    // ── Zakładka „Zadania" ───────────────────────────────────────────────────

    /**
     * Zadania tego deala. Lista idzie ze strumienia z cache Room, więc otwiera
     * się bez zasięgu (także z zadaniami spisanymi w terenie), a odświeżenie
     * z board360 dochodzi po chwili. Strumień zakładamy raz — kolejne wejścia
     * w zakładkę tylko odświeżają dane.
     *
     * @param force ponowne pobranie po zapisie albo po „Spróbuj ponownie".
     */
    fun loadTasks(force: Boolean = false) {
        val tasks = _uiState.value.tasks
        if (!force && (tasks.loaded || tasks.isLoading)) return
        if (tasksJob == null) observeTasks()

        viewModelScope.launch {
            _uiState.update { it.copy(tasks = it.tasks.copy(isLoading = true, error = null)) }
            val fresh = runCatching { taskRepository.refreshDealTasks(dealId) }
            _uiState.update {
                it.copy(
                    tasks = it.tasks.copy(
                        isLoading = false,
                        loaded = true,
                        // `false` = brak sieci (mamy ostatnią kopię), wyjątek =
                        // odmowa serwera i wtedy trzeba powiedzieć wprost.
                        offline = fresh.getOrNull() == false,
                        error = fresh.exceptionOrNull()
                            ?.let { e -> crmErrorMessage(e, "Nie udało się pobrać zadań") },
                    ),
                )
            }
        }
        // Ręczna kolejność mieszka w preferencjach użytkownika, wspólnych
        // z panelem — bez zasięgu wraca pusta i zostaje kolejność domyślna.
        viewModelScope.launch {
            manualOrder = runCatching { taskRepository.getTasksOrder() }.getOrDefault(emptyList())
            recomputeTasks()
        }
    }

    private fun observeTasks() {
        tasksJob = viewModelScope.launch {
            taskRepository.observeDealTasks(dealId).collect { list ->
                dealTasks = list
                _uiState.update { it.copy(tasks = it.tasks.copy(totalCount = list.size)) }
                recomputeTasks()
            }
        }
        viewModelScope.launch {
            taskRepository.observePendingTaskIds().collect { ids ->
                _uiState.update { it.copy(tasks = it.tasks.copy(queuedIds = ids)) }
            }
        }
    }

    fun onTaskQueryChange(query: String) = updateTasks { it.copy(query = query) }

    fun onTaskMineOnlyToggle() = updateTasks { it.copy(mineOnly = !it.mineOnly) }

    fun onTaskShowDoneToggle() = updateTasks { it.copy(showDone = !it.showDone) }

    /** Odhaczenie z wiersza — bez zasięgu ląduje w kolejce zadań. */
    fun onTaskToggleDone(task: Task) = runTaskAction(task.id) {
        val done = task.status != TaskStatus.DONE
        taskRepository.updateTask(
            task.id,
            TaskPatch(status = Edit(if (done) TaskStatus.DONE else TaskStatus.OPEN)),
        )
        if (done) "Zadanie wykonane." else "Przywrócono zadanie."
    }

    fun onTaskTogglePriority(task: Task) = runTaskAction(task.id) {
        val high = task.priority != TaskPriority.HIGH
        taskRepository.updateTask(
            task.id,
            TaskPatch(priority = Edit(if (high) TaskPriority.HIGH else TaskPriority.NORMAL)),
        )
        if (high) "Oznaczono jako wysoki priorytet." else "Zdjęto priorytet."
    }

    /**
     * Przeniesienie zadania w obrębie jego sekcji (przeciąganie po długim
     * przytrzymaniu). Ruch pokazujemy od razu, a zapis do preferencji idzie
     * dopiero po puszczeniu palca — [commitTaskOrder] — bo w trakcie
     * przeciągania kolejność zmienia się co kilka pikseli.
     *
     * Przenosimy tylko wewnątrz sekcji: sekcja to etap lejka, więc wyrzucenie
     * zadania do sąsiedniej znaczyłoby zmianę etapu, a nie kolejności. Sekcję
     * zmienia się w karcie zadania, tak samo jak w panelu.
     */
    fun onTaskMove(section: TaskSection?, from: Int, to: Int) {
        val group = _uiState.value.tasks.sections.firstOrNull { it.section == section } ?: return
        if (from !in group.items.indices || to !in group.items.indices || from == to) return
        val ids = group.items.map { it.id }.toMutableList()
        ids.add(to, ids.removeAt(from))
        manualOrder = mergeManualOrder(manualOrder, ids)
        recomputeTasks()
    }

    /**
     * Zapis ułożonej kolejności. Kolejka offline jej NIE wozi — to preferencja
     * widoku, nie praca do wykonania — więc bez zasięgu ułożenie zostaje na
     * ekranie do wyjścia z karty i mówimy o tym wprost.
     */
    fun commitTaskOrder() {
        val order = manualOrder
        if (order.isEmpty()) return
        viewModelScope.launch {
            runCatching { taskRepository.saveTasksOrder(order) }
                .onFailure {
                    _uiState.update {
                        it.copy(message = "Brak zasięgu — kolejność zostaje tylko na tym ekranie.")
                    }
                }
        }
    }

    /**
     * Wplata nową kolejność jednej sekcji w zapisaną kolejność WSZYSTKICH zadań.
     * Klucz `tasks.order` jest wspólny z panelem i obejmuje cały zespół, więc
     * telefon nie może go nadpisać samym wycinkiem jednego deala — pozycje
     * pozostałych zadań zostają nietknięte, podmieniamy tylko te przestawiane.
     * Zadania bez zapisanej pozycji (świeże) idą na początek, jak w panelu.
     */
    private fun mergeManualOrder(saved: List<String>, moved: List<String>): List<String> {
        val movedSet = moved.toSet()
        val base = moved.filter { it !in saved } + saved
        var next = 0
        return base.map { id -> if (id in movedSet) moved[next++] else id }
    }

    /**
     * Zapis pojedynczego zadania. Wiersz dostaje znacznik „w trakcie", a wynik
     * wraca strumieniem z Rooma — nie ma więc ręcznego podmieniania listy.
     */
    private fun runTaskAction(taskId: String, block: suspend () -> String) {
        viewModelScope.launch {
            _uiState.update { it.copy(tasks = it.tasks.copy(busyIds = it.tasks.busyIds + taskId)) }
            try {
                val message = block()
                _uiState.update { it.copy(message = message) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(message = crmErrorMessage(e, "Nie udało się zapisać zmiany"))
                }
            } finally {
                _uiState.update {
                    it.copy(tasks = it.tasks.copy(busyIds = it.tasks.busyIds - taskId))
                }
            }
        }
    }

    private fun updateTasks(block: (TasksState) -> TasksState) {
        _uiState.update { it.copy(tasks = block(it.tasks)) }
        recomputeTasks()
    }

    /**
     * Filtrowanie i grupowanie listy. WSZYSTKIE sekcje zostają widoczne, także
     * puste (ustalenie 2026-09-08) — na karcie deala nagłówki niosą cały proces
     * i mają gdzie przyjąć nowe zadanie. „Bez sekcji" dokładamy tylko wtedy, gdy
     * coś w nim jest, bo pustego kubełka nie da się w panelu nawet wybrać.
     *
     * W obrębie sekcji: aktywne najpierw (ręczna kolejność z panelu, a przy jej
     * braku najnowsze u góry), wykonane na dole — dokładnie jak w `TasksBoard`.
     */
    private fun recomputeTasks() {
        val state = _uiState.value
        val filters = state.tasks
        val me = state.currentUserId
        val query = filters.query.trim().lowercase()

        val visible = dealTasks.filter { task ->
            val mine = !filters.mineOnly || me == null ||
                task.assigneeId == me || task.createdBy == me
            val status = filters.showDone || task.status != TaskStatus.DONE
            val matches = query.isEmpty() || listOfNotNull(
                task.title,
                task.description,
                task.assigneeId?.let { id -> state.members.firstOrNull { m -> m.id == id }?.displayName }
                    ?: task.assigneeEmail,
            ).any { it.lowercase().contains(query) }
            mine && status && matches
        }

        val rank = manualOrder.withIndex().associate { (index, id) -> id to index }
        val buckets = visible.groupBy { it.section }
        fun order(items: List<Task>): List<Task> {
            val active = items.filter { it.status != TaskStatus.DONE }
                .sortedWith(
                    // Zadanie bez zapisanej pozycji (świeże) idzie na samą górę,
                    // a między takimi decyduje data — najnowsze najwyżej.
                    compareBy<Task> { rank[it.id] ?: -1 }
                        .thenByDescending { it.createdAt },
                )
            val done = items.filter { it.status == TaskStatus.DONE }
                .sortedByDescending { it.updatedAt ?: it.createdAt }
            return active + done
        }

        val groups = TaskSection.entries.map { section ->
            TaskGroup(section, section.label, order(buckets[section].orEmpty()))
        }
        val none = buckets[null].orEmpty()
        val all = if (none.isEmpty()) groups else groups + TaskGroup(null, NO_SECTION_LABEL, order(none))

        _uiState.update {
            it.copy(tasks = it.tasks.copy(sections = all, visibleCount = visible.size))
        }
    }

    /** Sekcja podpowiadana nowemu zadaniu — z etapu deala, jak w panelu. */
    fun defaultTaskSection(): TaskSection? = sectionFromStage(_uiState.value.detail?.deal?.stage)


    // ── Zakładka „Umowa" ─────────────────────────────────────────────────────

    /**
     * Umowy deala. Odczyt idzie przez repozytorium, więc bez zasięgu wraca
     * ostatnia kopia z nałożoną kolejką — handlowiec u klienta widzi wtedy
     * także to, co sam przed chwilą wystawił.
     *
     * @param force ponowny odczyt po zapisie (wystawienie, zmiana, decyzja).
     */
    fun loadContracts(force: Boolean = false) {
        val contracts = _uiState.value.contracts
        if (!force && (contracts.loaded || contracts.isLoading)) return

        viewModelScope.launch {
            _uiState.update { it.copy(contracts = it.contracts.copy(isLoading = true)) }
            val snapshot = contractRepository.getContracts(dealId)
            _uiState.update { state ->
                state.copy(
                    contracts = state.contracts.copy(
                        isLoading = false,
                        loaded = true,
                        contracts = snapshot.contracts,
                        fromCache = snapshot.fromCache,
                        error = snapshot.error,
                    ),
                )
            }
        }
    }

    /**
     * Pełny link do podpisu. Panel skleja go z adresu przeglądarki; telefon
     * z adresu board360, bo strona podpisu (`/umowa/<token>`) stoi na tym samym
     * hoście co API.
     */
    fun contractSignUrl(sciezka: String): String =
        BuildConfig.API_BASE_URL.trimEnd('/') + sciezka

    /**
     * „+ Nowa umowa". Rozpis i przedmiot § 1 wchodzą policzone z audytu — to ta
     * sama wycena, którą handlowiec przed chwilą pokazał klientowi w zakładce
     * „Oferta".
     */
    fun openNewContract() {
        _uiState.update {
            it.copy(
                contracts = it.contracts.copy(
                    form = ContractForm(filling = ContractFilling(termin = domyslnyTerminUmowy())),
                ),
            )
        }
        przeliczZalacznik(emptyList(), "")
        wczytajMaterialDoFormularza(poprzednie = emptyList())
    }

    /**
     * Wejście w zmianę podpisanej umowy: zaciągamy treść, którą klient podpisał,
     * i otwieramy na niej ten sam formularz. Poprawia się to, co jest — pisanie
     * umowy od zera gubiłoby wszystko, czego nikt nie ruszał.
     */
    fun openContractChange(contract: DealContract) {
        viewModelScope.launch {
            _uiState.update { it.copy(contracts = it.contracts.copy(busyId = contract.id)) }
            val tresc = contractRepository.getFilling(dealId, contract.id)
            if (tresc == null) {
                _uiState.update {
                    it.copy(
                        contracts = it.contracts.copy(busyId = null),
                        message = "Nie udało się wczytać treści umowy ${contract.numer}.",
                    )
                }
                return@launch
            }
            _uiState.update {
                it.copy(
                    contracts = it.contracts.copy(
                        busyId = null,
                        form = ContractForm(
                            zmianaDla = ContractChangeTarget(
                                id = contract.id,
                                numer = contract.numer,
                                zarzad = contract.zarzad,
                            ),
                            filling = tresc,
                        ),
                    ),
                )
            }
            // Zmiana umowy bierze się ze zmiany oferty, więc rozpis MUSI iść
            // z audytu — inaczej klient podpisałby nowy dokument ze starymi
            // liczbami. Etapy pozycji i linie dopisane ręcznie zostają.
            przeliczZalacznik(tresc.pozycje, tresc.przedmiot)
            wczytajMaterialDoFormularza(poprzednie = tresc.materialy)
        }
    }

    /**
     * Umowa po terminie odesłania → NOWA umowa z aktualnymi cenami.
     *
     * Treść przepisujemy z wygasłego dokumentu (zakres i ilości nie
     * zdezaktualizowały się przez dwa dni), ale powstaje osobna umowa z nowym
     * numerem i nowym terminem — nie wersja po zmianie, bo nie ma czego
     * zmieniać: klient tamtej nigdy nie podpisał.
     */
    fun openContractAfterExpiry(contract: DealContract) {
        viewModelScope.launch {
            _uiState.update { it.copy(contracts = it.contracts.copy(busyId = contract.id)) }
            val tresc = contractRepository.getFilling(dealId, contract.id)
            if (tresc == null) {
                _uiState.update {
                    it.copy(
                        contracts = it.contracts.copy(busyId = null),
                        message = "Nie udało się wczytać treści umowy ${contract.numer}.",
                    )
                }
                return@launch
            }
            _uiState.update {
                it.copy(
                    contracts = it.contracts.copy(
                        busyId = null,
                        form = ContractForm(
                            poTerminie = ContractExpiredSource(
                                numer = contract.numer,
                                wystawiona = contract.wyslana ?: contract.utworzona,
                            ),
                            // Nowy termin odesłania liczy się od nowa — po to
                            // właśnie tamta umowa wygasła.
                            filling = tresc.copy(termin = domyslnyTerminUmowy()),
                        ),
                    ),
                )
            }
            wczytajMaterialDoFormularza(poprzednie = tresc.materialy)
        }
    }

    fun closeContractForm() {
        _uiState.update { it.copy(contracts = it.contracts.copy(form = null)) }
    }

    // Pola formularza. Osobne settery zamiast jednego „zmień cokolwiek": pola
    // umowy są przeliczane (rozpis, sumy), a jedna funkcja z lambdą chowałaby,
    // co dokładnie zmienia ekran.

    fun setContractSubject(text: String) = updateContractFilling { it.copy(przedmiot = text) }

    fun setContractDeadline(date: String) = updateContractFilling { it.copy(termin = date) }

    fun setContractBasis(text: String) =
        updateContractFilling { it.copy(podstawaZalacznika = text) }

    fun setContractVat(value: Int) =
        updateContractFilling { it.copy(vatStawka = value.coerceIn(0, 23)) }

    fun setContractAdvance(value: Int) =
        updateContractFilling { it.copy(zaliczkaProc = value.coerceIn(0, 100)) }

    fun setContractFinalDays(value: Int) =
        updateContractFilling { it.copy(terminKoncowyDni = value.coerceAtLeast(1)) }

    fun setContractChangeReason(text: String) = updateContractForm { it.copy(powodZmiany = text) }

    fun setContractChangeKind(kind: ContractKind) =
        updateContractForm { it.copy(rodzajZmiany = kind) }

    fun addContractStage() = updateContractFilling { filling ->
        val nr = filling.etapy.size + 1
        filling.copy(etapy = filling.etapy + ContractStage(nr, "Etap $nr"))
    }

    fun renameContractStage(index: Int, nazwa: String) = updateContractFilling { filling ->
        filling.copy(
            etapy = filling.etapy.mapIndexed { i, e -> if (i == index) e.copy(nazwa = nazwa) else e },
        )
    }

    /** Skasowany etap przenumerowuje resztę — § 7 nie może mieć dziur. */
    fun removeContractStage(index: Int) = updateContractFilling { filling ->
        if (filling.etapy.size <= 1) return@updateContractFilling filling
        filling.copy(
            etapy = filling.etapy.filterIndexed { i, _ -> i != index }
                .mapIndexed { i, e -> e.copy(nr = i + 1) },
        )
    }

    fun addContractItem() = updateContractFilling { filling ->
        filling.copy(
            pozycje = filling.pozycje + ContractItem(
                lp = filling.pozycje.size + 1,
                opis = "",
                ilosc = 1.0,
                jm = "szt.",
                cenaNetto = 0.0,
                etap = 1,
            ),
        )
    }

    fun updateContractItem(index: Int, item: ContractItem) = updateContractFilling { filling ->
        filling.copy(pozycje = filling.pozycje.mapIndexed { i, p -> if (i == index) item else p })
    }

    fun removeContractItem(index: Int) = updateContractFilling { filling ->
        filling.copy(
            pozycje = filling.pozycje.filterIndexed { i, _ -> i != index }
                .mapIndexed { i, p -> p.copy(lp = i + 1) },
        )
    }

    /** „↻ Przelicz z audytu" — ręczne wywołanie tego, co robi wejście w formularz. */
    fun recalcContractItems() {
        val form = _uiState.value.contracts.form ?: return
        przeliczZalacznik(form.filling.pozycje, form.filling.przedmiot)
    }

    /**
     * PRZELICZENIE ZAŁĄCZNIKA NR 1 Z AUDYTU.
     *
     * Ilości i ceny stoją już w zakładce „Oferta" (audyt × formuła ceny węzła) —
     * przepisywanie ich ręcznie do umowy było jedynym miejscem, w którym oferta
     * mogła rozjechać się z dokumentem. Rachunek leci poza wątkiem głównym:
     * geometria rzutu potrafi zająć chwilę.
     */
    private fun przeliczZalacznik(biezace: List<ContractItem>, biezacyPrzedmiot: String) {
        viewModelScope.launch {
            updateContractForm { it.copy(liczenie = true) }
            val instalacje = contractScopeInstallations()
            val wynik = withContext(Dispatchers.Default) { contractScopeItems(instalacje) }
            updateContractForm { form ->
                if (!wynik.policzone) {
                    // Brak audytu albo brak formuły ceny — zostawiamy to, co jest,
                    // i mówimy wprost dlaczego. Cicha pusta tabela byłaby gorsza.
                    return@updateContractForm form.copy(liczenie = false, braki = wynik.braki)
                }
                val scalone = scalPozycje(biezace, wynik.pozycje)
                form.copy(
                    liczenie = false,
                    braki = wynik.braki,
                    filling = form.filling.copy(
                        pozycje = scalone,
                        // Przedmiot § 1 opisuje zakres, więc idzie za audytem —
                        // ale wyłącznie wtedy, gdy nikt go nie przepisał po
                        // swojemu. Własne zdanie handlowca zostaje.
                        przedmiot = if (
                            wynik.przedmiot.isNotBlank() && przedmiotZAutomatu(biezacyPrzedmiot)
                        ) {
                            wynik.przedmiot
                        } else {
                            form.filling.przedmiot
                        },
                    ),
                )
            }
        }
    }

    /**
     * Instalacje deala z audytem i cennikiem — wejście przeliczenia. Te same
     * odczyty, co zakładka „Oferta" (repozytoria mają cache, więc drugie wejście
     * nie dobija do sieci), ale cennik ciągniemy dla WSZYSTKICH instalacji:
     * Załącznik nr 1 obejmuje cały zakres deala, a nie tylko tę instalację,
     * którą ktoś ogląda.
     */
    private suspend fun contractScopeInstallations(): List<ContractScopeInstallation> {
        val audits = runCatching { auditRepository.getAudits(dealId) }.getOrDefault(emptyList())
        val categories = runCatching { auditRepository.getCategories() }.getOrDefault(emptyList())
        val snapshot = runCatching { auditRepository.getAuditInstallations(dealId) }
            .getOrDefault(AuditInstallations())

        val byId = categories.associateBy { it.id }
        val forms = audits.filter { it.installationForm != null }

        return offerInstallationIds(snapshot).map { id ->
            val owner = resolveAuditForm(id, byId)
            val record = owner?.let { o ->
                forms.firstOrNull { it.categoryId == o.id }
                    ?: forms.firstOrNull { it.categoryId == null }
            }
            val pricing = owner?.let {
                runCatching {
                    offerPricingRepository.getPricing(
                        categoryId = it.id,
                        categoryName = it.name,
                        categoryIdPath = categoryIdPath(it.id, byId),
                    )
                }.getOrNull()
            }
            ContractScopeInstallation(
                ownerId = owner?.id,
                name = byId[id]?.name ?: "instalacja",
                form = record?.installationForm,
                pricing = pricing,
            )
        }
    }

    /**
     * Migawka materiału do dokumentu. Telefon jej NIE LICZY (dobór materiału to
     * kilkaset linijek rachunku po stronie panelu) — bierzemy zestawienie
     * z rezerwacji deala, którą policzył panel z audytu. Gdy rezerwacji nie ma,
     * zostaje to, co niosła poprzednia wersja umowy; a gdy i tego nie ma,
     * formularz mówi wprost, że po podpisie magazyn trzeba ruszyć z panelu.
     */
    private fun wczytajMaterialDoFormularza(poprzednie: List<ContractMaterial>) {
        viewModelScope.launch {
            val swiezy = contractRepository.materialsFromReservation(dealId).map { line ->
                ContractMaterial(
                    productId = line.productId,
                    kod = line.kod,
                    nazwa = line.nazwa,
                    ilosc = line.ilosc,
                    jm = line.jm,
                    klucz = line.klucz,
                    uwaga = line.uwaga,
                    // Klucz linii ma postać `instalacja:pozycja` — nazwę
                    // instalacji niesie już uwaga, więc jej nie zgadujemy.
                    instalacjaId = line.klucz?.takeIf { it.contains(':') }?.substringBefore(':'),
                    instalacja = null,
                )
            }
            val materialy = swiezy.ifEmpty { poprzednie }
            updateContractForm { form ->
                form.copy(
                    materialZnany = materialy.isNotEmpty(),
                    filling = form.filling.copy(materialy = materialy),
                )
            }
        }
    }

    /** Wystawienie umowy: PDF, numer i link nadaje serwer — my mamy treść. */
    fun generateContract() {
        val form = _uiState.value.contracts.form ?: return
        if (!form.gotowe || form.zapis) return

        viewModelScope.launch {
            updateContractForm { it.copy(zapis = true, blad = null) }
            val result = runCatching { contractRepository.generate(dealId, form.filling) }
            val saved = result.getOrNull()
            if (saved == null) {
                updateContractForm {
                    it.copy(
                        zapis = false,
                        blad = crmErrorMessage(
                            result.exceptionOrNull() ?: Exception(),
                            "Nie udało się wygenerować umowy",
                        ),
                    )
                }
                return@launch
            }
            _uiState.update {
                it.copy(
                    contracts = it.contracts.copy(form = null),
                    message = if (saved.queued) {
                        "Brak zasięgu — umowa czeka w telefonie. Numer, PDF i link do " +
                            "podpisu powstaną, gdy wróci sieć."
                    } else {
                        "Umowa ${saved.numer.orEmpty()} wygenerowana — link do podpisu gotowy."
                    },
                )
            }
            loadContracts(force = true)
            // Handlowiec ma od razu zobaczyć, co wygenerował — zanim wyśle link
            // klientowi. Umowa z kolejki dokumentu jeszcze nie ma.
            if (!saved.queued && saved.id != null) {
                openContractPreview(saved.id, saved.numer.orEmpty(), saved.sciezkaPodpisu)
            }
        }
    }

    /**
     * Zapis zmiany. Zarząd wysyła klientowi od razu, opiekun zostawia wniosek do
     * akceptacji — pytanie „czy na pewno" zadaje zakładka, bo to ponowny podpis
     * umowy, którą klient ma już za sobą.
     */
    fun saveContractChange() {
        val form = _uiState.value.contracts.form ?: return
        val target = form.zmianaDla ?: return
        if (!form.gotowe || form.zapis) return

        viewModelScope.launch {
            updateContractForm { it.copy(zapis = true, blad = null) }
            val result = runCatching {
                contractRepository.requestChange(
                    dealId = dealId,
                    contractId = target.id,
                    filling = form.filling,
                    powod = form.powodZmiany,
                    rodzaj = form.rodzajZmiany,
                )
            }
            val saved = result.getOrNull()
            if (saved == null) {
                updateContractForm {
                    it.copy(
                        zapis = false,
                        blad = crmErrorMessage(
                            result.exceptionOrNull() ?: Exception(),
                            "Nie udało się zapisać zmiany umowy",
                        ),
                    )
                }
                return@launch
            }
            _uiState.update {
                it.copy(
                    contracts = it.contracts.copy(form = null),
                    message = when {
                        saved.queued -> "Brak zasięgu — zmiana umowy ${target.numer} czeka " +
                            "w telefonie i poleci, gdy wróci sieć."

                        saved.stan == "czeka-na-akceptacje" ->
                            "Zmiana umowy ${target.numer} czeka na akceptację zarządu."

                        else -> "Zmiana umowy ${target.numer} wystawiona — klient ma link " +
                            "do ponownego podpisu."
                    },
                )
            }
            loadContracts(force = true)
            if (!saved.queued && saved.id != null && saved.stan == "do-podpisu") {
                openContractPreview(saved.id, saved.numer.orEmpty(), saved.sciezkaPodpisu)
            }
        }
    }

    /** Akceptacja wniosku o zmianę — to ona wysyła umowę do klienta po raz drugi. */
    fun approveContractChange(contract: DealContract) = runContractAction(
        contract = contract,
        action = { contractRepository.approveChange(dealId, contract.id) },
        sent = "Zmiana zaakceptowana — klient ma link do ponownego podpisu.",
        queued = "Brak zasięgu — akceptacja poleci, gdy wróci sieć.",
        failure = "Nie udało się zaakceptować zmiany",
    )

    fun rejectContractChange(contract: DealContract, powod: String?) = runContractAction(
        contract = contract,
        action = { contractRepository.rejectChange(dealId, contract.id, powod) },
        sent = "Zmiana odrzucona — umowa ${contract.numer} zostaje bez zmian.",
        queued = "Brak zasięgu — odrzucenie poleci, gdy wróci sieć.",
        failure = "Nie udało się odrzucić zmiany",
    )

    /**
     * Nowy link do podpisu dla istniejącej umowy. Poprzedni przestaje działać —
     * o tym mówi zakładka przed kliknięciem.
     */
    fun resendContract(contract: DealContract) = runContractAction(
        contract = contract,
        action = { contractRepository.resend(dealId, contract.id) },
        sent = "Nowy link do podpisu gotowy — skopiuj go klientowi.",
        queued = "Brak zasięgu — nowy link powstanie, gdy wróci sieć.",
        failure = "Nie udało się wystawić linku",
    )

    fun cancelContract(contract: DealContract) = runContractAction(
        contract = contract,
        action = { contractRepository.cancel(dealId, contract.id) },
        sent = if (contract.zastepuje != null) {
            "Zmiana wycofana — obowiązuje ostatni podpisany dokument."
        } else {
            "Umowa unieważniona — link klienta przestał działać."
        },
        queued = "Brak zasięgu — unieważnienie poleci, gdy wróci sieć.",
        failure = "Nie udało się unieważnić umowy",
    )

    /**
     * Odtworzenie zamówienia z podpisanej umowy — dla kart, na których automat
     * po podpisie się nie wykonał. Idempotentne: powtórne kliknięcie tylko
     * potwierdza stan.
     */
    fun rebuildContractOrder(contract: DealContract) {
        viewModelScope.launch {
            _uiState.update { it.copy(contracts = it.contracts.copy(busyId = contract.id)) }
            val result = runCatching { contractRepository.rebuildOrder(dealId, contract.id) }
            val done = result.getOrNull()
            _uiState.update {
                it.copy(
                    contracts = it.contracts.copy(busyId = null),
                    message = when {
                        done == null -> crmErrorMessage(
                            result.exceptionOrNull() ?: Exception(),
                            "Nie udało się odtworzyć zamówienia",
                        )

                        done.queued -> "Brak zasięgu — odtworzenie zamówienia poleci, " +
                            "gdy wróci sieć."

                        else -> rebuildOrderMessage(contract.numer, done)
                    },
                )
            }
            if (done?.queued == false) loadOrders(force = true)
            loadContracts(force = true)
        }
    }

    /** Komunikat 1:1 z panelu — powstało, zmieniło się czy już było. */
    private fun rebuildOrderMessage(numer: String, done: ContractOrderRebuild): String {
        val ile = if (done.zamowienia > 1) " w ${done.zamowienia} zamówieniach" else ""
        return when (done.status) {
            "istnialo" -> "Zamówienie z umowy $numer już stało na karcie — nic nie zdublowaliśmy."
            "zmienione" -> "Zamówienie z umowy $numer zaktualizowane (${done.pozycje} poz.$ile) " +
                "— zakładka „Zamówienie”."

            else -> "Zamówienie z umowy $numer odtworzone (${done.pozycje} poz.$ile) " +
                "— zakładka „Zamówienie”."
        }
    }

    /**
     * Wspólna obsługa akcji na karcie umowy: blokada dwukliku, komunikat
     * i odświeżenie listy. Kolejka mówi WPROST, że decyzja siedzi w telefonie —
     * samo „gotowe" znaczyłoby dla handlowca „klient już to dostał".
     */
    private fun runContractAction(
        contract: DealContract,
        action: suspend () -> ContractSaveResult,
        sent: String,
        queued: String,
        failure: String,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(contracts = it.contracts.copy(busyId = contract.id)) }
            val result = runCatching { action() }
            val saved = result.getOrNull()
            _uiState.update {
                it.copy(
                    contracts = it.contracts.copy(busyId = null),
                    message = when {
                        saved == null -> crmErrorMessage(
                            result.exceptionOrNull() ?: Exception(),
                            failure,
                        )

                        saved.queued -> queued
                        else -> sent
                    },
                )
            }
            loadContracts(force = true)
        }
    }

    /** Podgląd dokumentu — HTML ten sam, z którego powstaje PDF. */
    fun openContractPreview(contractId: String, numer: String, sciezkaPodpisu: String?) {
        _uiState.update {
            it.copy(
                contracts = it.contracts.copy(
                    preview = ContractPreviewState(
                        contractId = contractId,
                        numer = numer,
                        sciezkaPodpisu = sciezkaPodpisu,
                    ),
                ),
            )
        }
        viewModelScope.launch {
            val preview = contractRepository.getPreview(dealId, contractId)
            _uiState.update { state ->
                val open = state.contracts.preview ?: return@update state
                if (open.contractId != contractId) return@update state
                state.copy(
                    contracts = state.contracts.copy(
                        preview = open.copy(
                            isLoading = false,
                            preview = preview,
                            error = if (preview == null) {
                                "Nie udało się wczytać dokumentu. Bez zasięgu widać tylko " +
                                    "umowy, które telefon zdążył już pokazać."
                            } else {
                                null
                            },
                        ),
                    ),
                )
            }
        }
    }

    fun closeContractPreview() {
        _uiState.update { it.copy(contracts = it.contracts.copy(preview = null)) }
    }

    /**
     * PDF do udostępnienia klientowi. Składa go serwer, więc bez zasięgu tej
     * akcji nie ma — podgląd HTML zostaje.
     */
    fun downloadContractPdf(contract: DealContract, cacheDir: File, onReady: (File) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(contracts = it.contracts.copy(pdfId = contract.id)) }
            val nazwa = "umowa-${contract.numer.ifBlank { contract.id }}"
                .replace(Regex("[^A-Za-z0-9_.-]"), "-") + ".pdf"
            val result = runCatching {
                val dir = File(cacheDir, "umowy").apply { mkdirs() }
                val target = File(dir, nazwa)
                contractRepository.downloadPdf(dealId, contract.id, target)
                target
            }
            _uiState.update {
                it.copy(
                    contracts = it.contracts.copy(pdfId = null),
                    message = result.exceptionOrNull()?.let { e ->
                        crmErrorMessage(e, "Nie udało się pobrać PDF-a umowy")
                    },
                )
            }
            result.getOrNull()?.let(onReady)
        }
    }

    private fun updateContractForm(transform: (ContractForm) -> ContractForm) {
        _uiState.update { state ->
            val form = state.contracts.form ?: return@update state
            state.copy(contracts = state.contracts.copy(form = transform(form)))
        }
    }

    private fun updateContractFilling(transform: (ContractFilling) -> ContractFilling) {
        updateContractForm { it.copy(filling = transform(it.filling)) }
    }

    // ── Zakładka „Faktura" ───────────────────────────────────────────────────

    /**
     * Trzy niezależne odczyty: faktury z KSeF razem z montażami (jedno
     * repozytorium), zakres etapu „montaz" na drzewo i umowy na rachunek.
     * Każdy ma własną obsługę błędu, bo każdy bywa niedostępny osobno —
     * handlowiec bez `ksef.view` ma zobaczyć rachunek i montaże, a nie pustą
     * zakładkę z jednym komunikatem o odmowie.
     */
    fun loadInvoices(force: Boolean = false) {
        val invoices = _uiState.value.invoices
        if (!force && (invoices.loaded || invoices.isLoading)) return

        viewModelScope.launch {
            _uiState.update { it.copy(invoices = it.invoices.copy(isLoading = true, error = null)) }

            var error: String? = null
            val dane = try {
                invoiceRepository.getInvoices(dealId)
            } catch (e: Exception) {
                error = crmErrorMessage(e, "Nie udało się wczytać faktur")
                null
            }

            val categories = try {
                auditRepository.getCategories()
            } catch (_: Exception) {
                emptyList()
            }
            val scope = try {
                auditRepository.getAuditInstallations(dealId)
                    .byStage[InstallationStage.MONTAZ.wire]
                    .orEmpty()
                    .toSet()
            } catch (_: Exception) {
                emptySet()
            }
            val tree = pruneToSelected(buildCategoryTree(categories), scope)

            _uiState.update { state ->
                state.copy(
                    invoices = state.invoices.copy(
                        isLoading = false,
                        loaded = true,
                        data = dane,
                        scopeTree = tree,
                        scope = scope,
                        // Zakres jest tu PODGLĄDEM, tak jak w panelu — rozwijamy
                        // gałęzie z zaznaczeniem, żeby był czytelny bez dotknięcia.
                        expanded = state.invoices.expanded + ancestorsOfSelected(tree, scope),
                        error = error,
                    ),
                )
            }

            loadRachunek()
        }
    }

    /**
     * Rachunek z AKTUALNEJ umowy: kwoty liczy ten sam kod, co podgląd umowy
     * (`policzPodglad`), więc na telefonie nie może wyjść inna kwota niż na
     * dokumencie, który klient trzyma w ręku.
     *
     * Aktualna = najnowsza umowa, której nic nie zastąpiło i której nie
     * unieważniono. Podpisana ma pierwszeństwo przed szkicem: fakturuje się to,
     * co klient podpisał, a nie to, co ktoś właśnie pisze.
     */
    private suspend fun loadRachunek() {
        val snapshot = runCatching { contractRepository.getContracts(dealId) }.getOrNull() ?: return
        val umowa = snapshot.contracts
            .filter { it.status != ContractStatus.CANCELLED && it.status != ContractStatus.SUPERSEDED }
            .minByOrNull { if (it.status == ContractStatus.SIGNED) 0 else 1 }
            ?: return

        val filling = runCatching { contractRepository.getFilling(dealId, umowa.id) }.getOrNull() ?: return
        val kwoty = policzPodglad(
            pozycje = filling.pozycje,
            etapy = filling.etapy,
            vatStawka = filling.vatStawka,
            zaliczkaProc = filling.zaliczkaProc,
        )
        _uiState.update { state ->
            state.copy(
                invoices = state.invoices.copy(
                    rachunek = InvoiceRachunek(
                        numerUmowy = umowa.numer,
                        podpisana = umowa.status == ContractStatus.SIGNED,
                        kwoty = kwoty,
                        vatStawka = filling.vatStawka,
                        zaliczkaProc = filling.zaliczkaProc,
                        terminKoncowyDni = filling.terminKoncowyDni,
                    ),
                ),
            )
        }
    }

    /** Rozwinięcie gałęzi drzewa zakresu. Wyboru NIE zmieniamy — to podgląd. */
    fun toggleInvoiceScopeBranch(categoryId: String) {
        _uiState.update { state ->
            val open = state.invoices.expanded
            state.copy(
                invoices = state.invoices.copy(
                    expanded = if (categoryId in open) open - categoryId else open + categoryId,
                ),
            )
        }
    }

    /** Wejście w edycję danych do faktury — formularz startuje z danych karty. */
    fun openBillingForm() {
        val deal = _uiState.value.detail?.deal ?: return
        _uiState.update {
            it.copy(
                invoices = it.invoices.copy(
                    form = BillingForm(
                        jakInstalacji = deal.billingSameAsInstall,
                        odbiorca = deal.billingName.orEmpty(),
                        firma = deal.billingCompany.orEmpty(),
                        nip = deal.billingNip.orEmpty(),
                        adres = deal.billingAddress.orEmpty(),
                    ),
                ),
            )
        }
    }

    fun closeBillingForm() {
        _uiState.update { it.copy(invoices = it.invoices.copy(form = null)) }
    }

    fun editBillingForm(transform: (BillingForm) -> BillingForm) {
        _uiState.update { state ->
            val form = state.invoices.form ?: return@update state
            state.copy(invoices = state.invoices.copy(form = transform(form)))
        }
    }

    /**
     * Zapis danych do faktury. Idzie tą samą drogą, co każda inna zmiana karty
     * (`PATCH` z różnicy draftu, bez zasięgu do kolejki `deal_mutations`) —
     * osobna kolejka na te same pola rozjeżdżałaby się z formularzem karty.
     *
     * „Adres jak instalacji" CZYŚCI pozostałe pola, tak samo jak board360
     * (`UpdateDeal`): zostawienie ich zapisanych „na później" kończy się tym,
     * że po ponownym odznaczeniu wracają dane sprzed roku.
     */
    fun saveBilling() {
        val form = _uiState.value.invoices.form ?: return
        val puste = form.jakInstalacji
        patchDeal(
            success = "Zapisano dane do faktury",
            // Nabywcę na fakturach szukamy po NIP-ie i nazwie z tych właśnie
            // pól, więc lista musi zostać zadana od nowa — ale dopiero PO
            // zapisie. Bez zasięgu zapis idzie do kolejki i odczyt też się
            // wykona: pokaże wtedy kopię z telefonu, a nie pustkę.
            onSaved = { loadInvoices(force = true) },
        ) { draft ->
            draft.copy(
                billingSameAsInstall = form.jakInstalacji,
                billingName = if (puste) null else form.odbiorca.trim().ifBlank { null },
                billingCompany = if (puste) null else form.firma.trim().ifBlank { null },
                billingNip = if (puste) null else form.nip.trim().ifBlank { null },
                billingAddress = if (puste) null else form.adres.trim().ifBlank { null },
            )
        }
        closeBillingForm()
    }

    /** Komunikat do snackbara wywołany z zakładki (bez własnej operacji). */
    fun showMessage(text: String) = _uiState.update { it.copy(message = text) }

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

/**
 * Domyślny termin wykonania (§ 2): koniec przyszłego miesiąca. I tak zwykle do
 * zmiany, ale nigdy w przeszłości — 1:1 z `domyslnyTermin` panelu.
 */
private fun domyslnyTerminUmowy(): String =
    LocalDate.now().plusMonths(2).withDayOfMonth(1).minusDays(1).toString()
