package com.ekotak.teamtalk.presentation.crm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.data.local.preferences.SessionPreferences
import com.ekotak.teamtalk.domain.model.Client
import com.ekotak.teamtalk.domain.model.Deal
import com.ekotak.teamtalk.domain.model.DealListItem
import com.ekotak.teamtalk.domain.model.DealStage
import com.ekotak.teamtalk.domain.model.FunnelGroup
import com.ekotak.teamtalk.domain.model.PIPELINE_STAGES
import com.ekotak.teamtalk.domain.usecase.calllog.MakeCallUseCase
import com.ekotak.teamtalk.domain.usecase.client.GetClientsUseCase
import com.ekotak.teamtalk.domain.usecase.deal.GetDealsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Lejek sprzedaży na liście. Deale pobieramy jednorazowo przy wejściu (bez cache
 * Room — patrz `DealRepository`), a kartotekę klientów bierzemy ze strumienia,
 * który już ma cache offline: dzięki temu karta pokaże nazwisko także wtedy, gdy
 * `GET /api/deals` odpowie, a kartoteka akurat nie.
 *
 * Filtrowanie po fazie i wyszukiwanie robimy lokalnie na pobranej liście —
 * przełączanie chipów bez okrążenia po sieci jest wyraźnie szybsze, a lejek
 * jednej organizacji to rząd setek rekordów, nie dziesiątek tysięcy.
 */
@HiltViewModel
class DealListViewModel @Inject constructor(
    private val getDealsUseCase: GetDealsUseCase,
    private val getClientsUseCase: GetClientsUseCase,
    private val sessionPreferences: SessionPreferences,
    private val makeCallUseCase: MakeCallUseCase,
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val error: String? = null,
        val searchQuery: String = "",
        val group: FunnelGroup? = null,
        val onlyOverdue: Boolean = false,
        val onlyMine: Boolean = false,
        /** Karty pogrupowane etapami, w kolejności lejka. */
        val sections: List<Section> = emptyList(),
        /** Liczba dealów po filtrach fazy/„moje"/„zaległe", przed wyszukiwarką. */
        val totalInScope: Int = 0,
    )

    data class Section(val stage: DealStage, val items: List<DealListItem>)

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var deals: List<Deal> = emptyList()
    private var clientsById: Map<String, Client> = emptyMap()
    private var currentUserId: String? = null

    init {
        observeClients()
        load(initial = true)
    }

    /** Kartoteka z cache Room — źródło nazwisk i telefonów na kartach lejka. */
    private fun observeClients() {
        viewModelScope.launch {
            getClientsUseCase().collect { clients ->
                clientsById = clients.associateBy { it.id }
                recompute()
            }
        }
    }

    fun refresh() = load(initial = false)

    private fun load(initial: Boolean) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = initial, isRefreshing = !initial, error = null)
            }
            try {
                currentUserId = sessionPreferences.session.first()?.userId
                // Filtry etapu/zaległości stosujemy lokalnie, więc z API bierzemy
                // pełny lejek raz — jeden request zamiast po jednym na chip.
                deals = getDealsUseCase()
                _uiState.update { it.copy(isLoading = false, isRefreshing = false) }
                recompute()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = crmErrorMessage(e, "Nie udało się pobrać lejka"),
                    )
                }
            }
        }
    }

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        recompute()
    }

    /** Ponowne kliknięcie w aktywną fazę zdejmuje filtr (wraca cały lejek). */
    fun onGroupChange(group: FunnelGroup?) {
        _uiState.update { it.copy(group = if (it.group == group) null else group) }
        recompute()
    }

    fun onToggleOverdue() {
        _uiState.update { it.copy(onlyOverdue = !it.onlyOverdue) }
        recompute()
    }

    fun onToggleMine() {
        _uiState.update { it.copy(onlyMine = !it.onlyMine) }
        recompute()
    }

    fun clearError() = _uiState.update { it.copy(error = null) }

    fun call(phone: String) = makeCallUseCase(phone)

    private fun recompute() {
        val state = _uiState.value
        val visibleStages = state.group?.stages ?: PIPELINE_STAGES

        val inScope = deals.filter { deal ->
            deal.stage in visibleStages &&
                (!state.onlyOverdue || isOverdue(deal.nextContactAt)) &&
                (!state.onlyMine || deal.ownerId == currentUserId)
        }

        val query = state.searchQuery.trim()
        val matching = if (query.isBlank()) inScope else inScope.filter { matches(it, query) }

        val sections = visibleStages.mapNotNull { stage ->
            val items = matching
                .filter { it.stage == stage }
                // Najpierw zaległy kontakt, potem najdłużej stojące w etapie.
                .sortedWith(
                    compareByDescending<Deal> { isOverdue(it.nextContactAt) }
                        .thenBy { parseIsoMillis(it.stageEnteredAt) ?: Long.MAX_VALUE },
                )
                .map { DealListItem(deal = it, client = clientsById[it.clientId]) }
            if (items.isEmpty()) null else Section(stage, items)
        }

        _uiState.update {
            it.copy(sections = sections, totalInScope = inScope.size)
        }
    }

    private fun matches(deal: Deal, query: String): Boolean {
        val client = clientsById[deal.clientId]
        return listOfNotNull(
            client?.displayName,
            client?.primaryPhone,
            client?.city,
            client?.address,
            deal.projectName,
            deal.description,
            deal.source,
        ).any { it.contains(query, ignoreCase = true) }
    }
}
