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
import com.ekotak.teamtalk.domain.model.DealDraft
import com.ekotak.teamtalk.domain.model.DealOffer
import com.ekotak.teamtalk.domain.model.DealOrder
import com.ekotak.teamtalk.domain.model.DealStage
import com.ekotak.teamtalk.domain.model.PurchaseLine
import com.ekotak.teamtalk.domain.model.StockReservation
import com.ekotak.teamtalk.domain.model.pruneToSelected
import com.ekotak.teamtalk.domain.model.HeatloadMode
import com.ekotak.teamtalk.domain.model.InstallationStage
import com.ekotak.teamtalk.domain.model.KnowledgeArticle
import com.ekotak.teamtalk.domain.model.LeadIntake
import com.ekotak.teamtalk.domain.model.MeetingKind
import com.ekotak.teamtalk.domain.model.OfferLock
import com.ekotak.teamtalk.domain.model.TaskMember
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
import com.ekotak.teamtalk.domain.repository.AuditSaveResult
import com.ekotak.teamtalk.domain.repository.AuthRepository
import com.ekotak.teamtalk.domain.repository.OfferPricingRepository
import com.ekotak.teamtalk.domain.repository.OrderRepository
import com.ekotak.teamtalk.domain.repository.OrderSaveResult
import com.ekotak.teamtalk.domain.repository.TaskRepository
import com.ekotak.teamtalk.domain.ufh.OfferPricing
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

/**
 * Zamówienia deala: board360 trzyma pod tym uprawnieniem także ODCZYT listy,
 * nie tylko zapis (`OrdersController`). Bez niego zakładka pokazuje sam zakres
 * i rezerwację, zamiast udawać, że deal nie ma zamówień.
 */
private const val PERMISSION_ORDER_MANAGE = "order.manage"

/** Zmiany w rezerwacji materiału i dokładanie braków na listę zakupową. */
private const val PERMISSION_INVENTORY_MANAGE = "inventory.manage"

/** Odstęp między znakiem a zapytaniem do kartoteki przy szukaniu kontaktu. */
private const val CONTACT_SEARCH_DEBOUNCE_MS = 250L

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
        val audit: AuditState = AuditState(),
        val offer: OfferState = OfferState(),
        val orders: OrdersState = OrdersState(),
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
        val permissions = try {
            authRepository.getCurrentUser().permissions.toSet()
        } catch (_: Exception) {
            emptySet()
        }
        _uiState.update {
            it.copy(
                permissions = permissions,
                canManage = PERMISSION_DEAL_MANAGE in permissions,
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
        if (tab == DealTab.AUDYT) loadAudit()
        if (tab == DealTab.OFERTA) loadOffer()
        if (tab == DealTab.ZAMOWIENIE) loadOrders()
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
