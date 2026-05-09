package com.ekotak.teamtalk.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.domain.model.CallLog
import com.ekotak.teamtalk.domain.model.CallLogFilter
import com.ekotak.teamtalk.domain.model.VoiceReport
import com.ekotak.teamtalk.domain.usecase.calllog.GetCallLogsUseCase
import com.ekotak.teamtalk.domain.usecase.voicereport.GetVoiceReportsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class HistoryEntry(
    val callLog: CallLog,
    val noteCount: Int,
    val notePreview: String?,
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val getCallLogsUseCase: GetCallLogsUseCase,
    private val getVoiceReportsUseCase: GetVoiceReportsUseCase,
) : ViewModel() {

    data class UiState(
        val entries: List<HistoryEntry> = emptyList(),
        val searchQuery: String = "",
        val isLoading: Boolean = true,
    )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<UiState> = combine(
        getCallLogsUseCase(CallLogFilter(statusEq = "completed", embedClients = true)),
        _searchQuery,
    ) { logs, query -> logs to query }
        .flatMapLatest { (logs, query) ->
            val ids = logs.map { it.id }
            if (ids.isEmpty()) {
                flow { emit(UiState(entries = emptyList(), searchQuery = query, isLoading = false)) }
            } else {
                getVoiceReportsUseCase(callLogIdIn = ids).map { reports ->
                    val reportsByLog = reports.groupBy { it.callLogId }
                    val filtered = filterLogs(logs, reports, query)
                    UiState(
                        entries = filtered.sortedByDescending { it.timestamp }.map { log ->
                            val logReports = reportsByLog[log.id].orEmpty()
                            HistoryEntry(
                                callLog = log,
                                noteCount = logReports.size,
                                notePreview = logReports.firstOrNull { !it.transcription.isNullOrBlank() }?.transcription,
                            )
                        },
                        searchQuery = query,
                        isLoading = false,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun onSearchQueryChange(query: String) { _searchQuery.value = query }

    private fun filterLogs(logs: List<CallLog>, reports: List<VoiceReport>, query: String): List<CallLog> {
        if (query.isBlank()) return logs
        return logs.filter { log ->
            log.client?.name?.contains(query, ignoreCase = true) == true ||
            log.callerPhone?.contains(query, ignoreCase = true) == true ||
            reports.any { r ->
                r.callLogId == log.id &&
                r.transcription?.contains(query, ignoreCase = true) == true
            }
        }
    }
}
