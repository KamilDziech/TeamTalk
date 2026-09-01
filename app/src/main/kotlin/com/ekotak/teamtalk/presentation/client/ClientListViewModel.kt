package com.ekotak.teamtalk.presentation.client

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.domain.model.CallLogFilter
import com.ekotak.teamtalk.domain.model.Client
import com.ekotak.teamtalk.domain.model.ClientCategory
import com.ekotak.teamtalk.domain.model.ClientListEntry
import com.ekotak.teamtalk.domain.model.DealStage
import com.ekotak.teamtalk.domain.model.DuplicateGroup
import com.ekotak.teamtalk.domain.model.duplicateGroups
import com.ekotak.teamtalk.domain.repository.AuthRepository
import com.ekotak.teamtalk.domain.usecase.calllog.GetCallLogsUseCase
import com.ekotak.teamtalk.domain.usecase.calllog.MakeCallUseCase
import com.ekotak.teamtalk.domain.usecase.client.ClientDirectoryData
import com.ekotak.teamtalk.domain.usecase.client.GetClientDirectoryUseCase
import com.ekotak.teamtalk.domain.usecase.client.GetClientsUseCase
import com.ekotak.teamtalk.domain.usecase.client.RefreshClientsUseCase
import com.ekotak.teamtalk.presentation.crm.crmErrorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Uprawnienie board360 wymagane do dodania i edycji wpisu kartoteki. */
private const val PERMISSION_DEAL_MANAGE = "deal.manage"

/**
 * Kartoteka klientów — mobilny odpowiednik karty „Klienci" z board360.
 * Klienci lecą ze strumienia Room (offline-first), a dane lejka (główny etap,
 * instalacje, deale wspólne) dociągamy jednym przebiegiem przy wejściu i przy
 * odświeżeniu. Wyszukiwarkę i filtry liczymy lokalnie — przełączanie zakładki
 * kategorii bez okrążenia po sieci jest wyraźnie szybsze, a kartoteka jednej
 * organizacji to rząd tysięcy rekordów, nie milionów.
 */
@HiltViewModel
class ClientListViewModel @Inject constructor(
    private val getClientsUseCase: GetClientsUseCase,
    private val getClientDirectoryUseCase: GetClientDirectoryUseCase,
    private val refreshClientsUseCase: RefreshClientsUseCase,
    private val getCallLogsUseCase: GetCallLogsUseCase,
    private val makeCallUseCase: MakeCallUseCase,
    private val authRepository: AuthRepository,
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val error: String? = null,
        val message: String? = null,
        val searchQuery: String = "",
        val category: ClientCategory = ClientCategory.KLIENT,
        /** `null` = wszystkie etapy / wszystkie instalacje. */
        val stageFilter: DealStage? = null,
        val installFilter: String? = null,
        val categoryCounts: Map<ClientCategory, Int> = emptyMap(),
        val entries: List<ClientListEntry> = emptyList(),
        val installOptions: List<String> = emptyList(),
        /** Liczności etapów w bieżącej kategorii — podpowiedź w arkuszu filtra. */
        val stageCounts: Map<DealStage, Int> = emptyMap(),
        val duplicateGroups: List<DuplicateGroup> = emptyList(),
        /** Liczba połączeń per klient — zostaje z poprzedniej wersji listy. */
        val callCounts: Map<String, Int> = emptyMap(),
        val canManage: Boolean = false,
    ) {
        val filtersActive: Boolean get() = stageFilter != null || installFilter != null
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var allClients: List<Client> = emptyList()
    private var directory = ClientDirectoryData()

    init {
        observeClients()
        observeCallCounts()
        loadDirectory(initial = true)
        loadPermissions()
    }

    private fun observeClients() {
        viewModelScope.launch {
            getClientsUseCase().collect { clients ->
                allClients = clients
                recompute()
            }
        }
    }

    /** Licznik połączeń na karcie — jedyna rzecz, której panel nie ma. */
    private fun observeCallCounts() {
        viewModelScope.launch {
            getCallLogsUseCase(CallLogFilter()).collect { logs ->
                _uiState.update { state ->
                    state.copy(
                        callCounts = logs
                            .mapNotNull { it.clientId }
                            .groupingBy { it }
                            .eachCount(),
                    )
                }
            }
        }
    }

    /** Brak odpowiedzi z `/api/me` nie blokuje podglądu — chowa tylko akcje. */
    private fun loadPermissions() {
        viewModelScope.launch {
            val canManage = try {
                authRepository.getCurrentUser().permissions.contains(PERMISSION_DEAL_MANAGE)
            } catch (_: Exception) {
                false
            }
            _uiState.update { it.copy(canManage = canManage) }
        }
    }

    fun refresh() = loadDirectory(initial = false)

    private fun loadDirectory(initial: Boolean) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = initial && allClients.isEmpty(),
                    isRefreshing = !initial,
                    error = null,
                )
            }
            // Kartoteka i lejek to dwa niezależne pobrania: nawet gdy lejek
            // odmówi, lista klientów ma się pokazać (bez etapów i instalacji).
            val clientsError = runCatching { refreshClientsUseCase() }.exceptionOrNull()
            val directoryError = runCatching { directory = getClientDirectoryUseCase() }.exceptionOrNull()

            _uiState.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = when {
                        clientsError != null ->
                            crmErrorMessage(clientsError, "Nie udało się wczytać kartoteki")
                        directoryError != null ->
                            crmErrorMessage(directoryError, "Nie udało się wczytać danych lejka")
                        else -> null
                    },
                )
            }
            recompute()
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        recompute()
    }

    fun onCategoryChange(category: ClientCategory) {
        _uiState.update { it.copy(category = category) }
        recompute()
    }

    fun onStageFilterChange(stage: DealStage?) {
        _uiState.update { it.copy(stageFilter = stage) }
        recompute()
    }

    fun onInstallFilterChange(install: String?) {
        _uiState.update { it.copy(installFilter = install) }
        recompute()
    }

    fun clearFilters() {
        _uiState.update { it.copy(stageFilter = null, installFilter = null) }
        recompute()
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    fun clearMessage() = _uiState.update { it.copy(message = null) }

    /** Komunikat z ekranu potomnego (dodano / scalono / zapisano). */
    fun showMessage(text: String) = _uiState.update { it.copy(message = text) }

    fun call(phone: String) = makeCallUseCase(phone)

    private fun recompute() {
        val state = _uiState.value
        val nameById = allClients.associate { it.id to it.displayName }

        val enriched = allClients.map { client ->
            ClientListEntry(
                client = client,
                deals = directory.dealsByClient[client.id].orEmpty(),
                installations = directory.installByClient[client.id].orEmpty(),
                sharedWith = directory.sharedIdsByClient[client.id]
                    .orEmpty()
                    .map { id -> nameById[id] ?: "?" },
            )
        }

        val query = state.searchQuery.trim()
        val matching = if (query.isBlank()) enriched else enriched.filter { matches(it.client, query) }

        // Liczniki zakładek liczymy na wyniku wyszukiwarki (jak w panelu), żeby
        // przełącznik pokazywał, w której kategorii szukana osoba faktycznie jest.
        val categoryCounts = ClientCategory.entries.associateWith { cat ->
            matching.count { it.client.category == cat }
        }

        val inCategory = matching.filter { it.client.category == state.category }
        val stageCounts = inCategory.mapNotNull { it.mainStage }.groupingBy { it }.eachCount()

        val shown = inCategory.filter { entry ->
            val stageOk = state.stageFilter == null || entry.mainStage == state.stageFilter
            val installOk = state.installFilter == null ||
                entry.installations.contains(state.installFilter)
            stageOk && installOk
        }

        _uiState.update {
            it.copy(
                entries = shown,
                categoryCounts = categoryCounts,
                stageCounts = stageCounts,
                installOptions = directory.installOptions,
                // Duplikaty liczymy z PEŁNEJ kartoteki, nie z wyniku filtrów.
                duplicateGroups = duplicateGroups(enriched),
            )
        }
    }

    private fun matches(client: Client, query: String): Boolean = listOfNotNull(
        client.displayName,
        client.phone,
        client.phone2,
        client.email,
        client.email2,
        client.address,
        client.place,
    ).any { it.contains(query, ignoreCase = true) }
}
