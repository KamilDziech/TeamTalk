package com.ekotak.teamtalk.presentation.client

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.domain.model.CallLog
import com.ekotak.teamtalk.domain.model.CallLogFilter
import com.ekotak.teamtalk.domain.model.Client
import com.ekotak.teamtalk.domain.model.VoiceReport
import com.ekotak.teamtalk.domain.usecase.calllog.GetCallLogsUseCase
import com.ekotak.teamtalk.domain.usecase.calllog.MakeCallUseCase
import com.ekotak.teamtalk.domain.usecase.client.GetClientsUseCase
import com.ekotak.teamtalk.domain.usecase.voicereport.GetVoiceReportsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class TimelineEntry(
    val callLog: CallLog,
    val reports: List<VoiceReport>,
)

@HiltViewModel
class ClientTimelineViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getClientsUseCase: GetClientsUseCase,
    private val getCallLogsUseCase: GetCallLogsUseCase,
    private val getVoiceReportsUseCase: GetVoiceReportsUseCase,
    private val makeCallUseCase: MakeCallUseCase,
) : ViewModel() {

    data class UiState(
        val client: Client? = null,
        val entries: List<TimelineEntry> = emptyList(),
        val isLoading: Boolean = true,
    )

    val clientId: String = savedStateHandle["clientId"] ?: ""
    val phone: String = savedStateHandle["phone"] ?: ""

    private fun matchesPhone(c: Client): Boolean {
        if (phone.isBlank()) return false
        val candidates = listOfNotNull(c.phone, c.phone2)
        return candidates.any { it == phone || it.endsWith(phone) || phone.endsWith(it) }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<UiState> = combine(
        getClientsUseCase().map { list ->
            when {
                clientId.isNotBlank() -> list.find { it.id == clientId }
                phone.isNotBlank() -> list.find { matchesPhone(it) }
                else -> null
            }
        },
        when {
            clientId.isNotBlank() -> getCallLogsUseCase(CallLogFilter(clientId = clientId))
            else -> getCallLogsUseCase(CallLogFilter())
        },
    ) { client, callLogs ->
        val relevant = when {
            clientId.isNotBlank() -> callLogs.filter { it.clientId == clientId }
            phone.isNotBlank() -> callLogs.filter { it.phoneNumber.endsWith(phone) || phone.endsWith(it.phoneNumber) }
            else -> callLogs
        }
        client to relevant
    }.flatMapLatest { (client, callLogs) ->
        getVoiceReportsUseCase(clientId = client?.id).map { reports ->
            val entries = callLogs
                .sortedByDescending { it.startedAt }
                .map { callLog ->
                    TimelineEntry(
                        callLog = callLog,
                        reports = reports
                            .filter { it.callLogId == callLog.id }
                            .sortedBy { it.createdAt },
                    )
                }
            UiState(client = client, entries = entries, isLoading = false)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    fun makeCall(phone: String) { makeCallUseCase(phone) }
}
