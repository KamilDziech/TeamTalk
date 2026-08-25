package com.ekotak.teamtalk.presentation.client

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.domain.model.CallLogFilter
import com.ekotak.teamtalk.domain.model.Client
import com.ekotak.teamtalk.domain.usecase.calllog.GetCallLogsUseCase
import com.ekotak.teamtalk.domain.usecase.client.GetClientsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Klienci — tryb read-only (board360). Serwisant przegląda kartotekę i widzi
 * liczbę połączeń; tworzenie/edycja odbywa się w panelu web board360.
 */
@HiltViewModel
class ClientViewModel @Inject constructor(
    private val getClientsUseCase: GetClientsUseCase,
    private val getCallLogsUseCase: GetCallLogsUseCase,
) : ViewModel() {

    data class ListUiState(
        val clients: List<Client> = emptyList(),
        val searchQuery: String = "",
        val callCountMap: Map<String, Int> = emptyMap(),
    )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    private val allClientsFlow: Flow<List<Client>> = getClientsUseCase()

    val listState: StateFlow<ListUiState> = combine(
        allClientsFlow,
        _searchQuery,
        getCallLogsUseCase(CallLogFilter()),
    ) { clients, query, callLogs ->
        val callCountMap = callLogs
            .filter { it.clientId != null }
            .groupBy { it.clientId!! }
            .mapValues { it.value.size }
        val filtered = if (query.isBlank()) clients
        else clients.filter {
            it.displayName.contains(query, ignoreCase = true) ||
            it.primaryPhone?.contains(query, ignoreCase = true) == true ||
            it.address?.contains(query, ignoreCase = true) == true
        }
        ListUiState(clients = filtered, searchQuery = query, callCountMap = callCountMap)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListUiState())

    fun onSearchQueryChange(query: String) { _searchQuery.value = query }
    fun clearActionError() { _actionError.value = null }

    fun observeClient(id: String): Flow<Client?> =
        allClientsFlow.map { list -> list.find { it.id == id } }
}
