package com.ekotak.teamtalk.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.domain.model.CallLogFilter
import com.ekotak.teamtalk.domain.usecase.calllog.GetCallLogsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ClientHistoryEntry(
    val clientId: String?,
    val phone: String?,
    val displayName: String,
    val callCount: Int,
    val lastCallTimestamp: String,
    val notePreview: String?,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val getCallLogsUseCase: GetCallLogsUseCase,
) : ViewModel() {

    data class UiState(
        val entries: List<ClientHistoryEntry> = emptyList(),
        val isLoading: Boolean = true,
    )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val uiState: StateFlow<UiState> = combine(
        getCallLogsUseCase(CallLogFilter(statusEq = "completed", embedClients = true)),
        _searchQuery,
    ) { logs, query ->
        val grouped = logs
            .groupBy { it.clientId?.ifBlank { null } ?: it.callerPhone ?: "unknown_${it.id}" }
            .values
            .map { calls ->
                val mostRecent = calls.maxByOrNull { it.timestamp }!!
                val client = mostRecent.client ?: calls.firstOrNull { it.client != null }?.client
                val phone = mostRecent.callerPhone
                    ?: calls.firstOrNull { it.callerPhone != null }?.callerPhone
                val clientId = client?.id
                    ?: calls.firstOrNull { !it.clientId.isNullOrBlank() }?.clientId
                val notePreview = client?.notes?.lines()
                    ?.firstOrNull { it.isNotBlank() }
                    ?.let { line ->
                        if (line.startsWith("[")) {
                            val end = line.indexOf(']')
                            if (end > 0) line.substring(end + 1).trim() else line
                        } else line
                    }
                    ?.takeIf { it.isNotBlank() }
                ClientHistoryEntry(
                    clientId = clientId,
                    phone = phone,
                    displayName = client?.name ?: phone ?: "Nieznany",
                    callCount = calls.size,
                    lastCallTimestamp = mostRecent.timestamp,
                    notePreview = notePreview,
                )
            }
            .filter { entry ->
                if (query.isBlank()) true
                else entry.displayName.contains(query, ignoreCase = true) ||
                     entry.phone?.contains(query, ignoreCase = true) == true ||
                     entry.notePreview?.contains(query, ignoreCase = true) == true
            }
            .sortedByDescending { it.lastCallTimestamp }
        UiState(entries = grouped, isLoading = false)
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun onSearchQueryChange(query: String) { _searchQuery.value = query }
}
