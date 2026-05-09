package com.ekotak.teamtalk.presentation.calllog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.domain.model.CallLog
import com.ekotak.teamtalk.domain.model.CallLogFilter
import com.ekotak.teamtalk.domain.model.CallStatus
import com.ekotak.teamtalk.domain.usecase.auth.GetCurrentUserUseCase
import com.ekotak.teamtalk.domain.usecase.calllog.AppendRecipientUseCase
import com.ekotak.teamtalk.domain.usecase.calllog.GetCallLogsUseCase
import com.ekotak.teamtalk.domain.usecase.calllog.MakeCallUseCase
import com.ekotak.teamtalk.domain.usecase.calllog.ScanMissedCallsUseCase
import com.ekotak.teamtalk.domain.usecase.calllog.UpdateCallLogUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CallLogViewModel @Inject constructor(
    private val getCallLogsUseCase: GetCallLogsUseCase,
    private val updateCallLogUseCase: UpdateCallLogUseCase,
    private val appendRecipientUseCase: AppendRecipientUseCase,
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val scanMissedCallsUseCase: ScanMissedCallsUseCase,
    private val makeCallUseCase: MakeCallUseCase,
) : ViewModel() {

    enum class Tab(val label: String) {
        ACTIVE("Aktywne"),
        COMPLETED("Zakończone"),
        ALL("Wszystkie"),
    }

    data class ListUiState(
        val callLogs: List<CallLog> = emptyList(),
        val selectedTab: Tab = Tab.ACTIVE,
    )

    private val _selectedTab = MutableStateFlow(Tab.ACTIVE)
    val selectedTab: StateFlow<Tab> = _selectedTab.asStateFlow()

    private val _refreshKey = MutableStateFlow(0)

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val listState: StateFlow<ListUiState> = combine(_selectedTab, _refreshKey) { tab, _ -> tab }
        .flatMapLatest { tab ->
            val filter = when (tab) {
                Tab.ACTIVE    -> CallLogFilter(statusIn = listOf("missed", "reserved"), embedClients = true)
                Tab.COMPLETED -> CallLogFilter(statusEq = "completed", embedClients = true)
                Tab.ALL       -> CallLogFilter(embedClients = true)
            }
            getCallLogsUseCase(filter).map { logs -> ListUiState(callLogs = logs, selectedTab = tab) }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ListUiState())

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    fun selectTab(tab: Tab) { _selectedTab.value = tab }

    fun clearActionError() { _actionError.value = null }

    /** Observe a single call log by ID. Used by the detail screen. */
    fun observeCallLog(id: String): Flow<CallLog?> =
        getCallLogsUseCase(CallLogFilter(embedClients = true))
            .map { list -> list.find { it.id == id } }

    fun reserveCallLog(id: String) {
        viewModelScope.launch {
            try {
                val user = getCurrentUserUseCase()
                updateCallLogUseCase(
                    id = id,
                    status = CallStatus.RESERVED,
                    reservationBy = user.id,
                )
            } catch (e: Exception) {
                _actionError.value = e.message ?: "Błąd rezerwacji"
            }
        }
    }

    fun completeCallLog(id: String) {
        viewModelScope.launch {
            try {
                updateCallLogUseCase(id = id, status = CallStatus.COMPLETED)
            } catch (e: Exception) {
                _actionError.value = e.message ?: "Błąd aktualizacji"
            }
        }
    }

    fun reopenCallLog(id: String) {
        viewModelScope.launch {
            try {
                updateCallLogUseCase(id = id, status = CallStatus.MISSED)
            } catch (e: Exception) {
                _actionError.value = e.message ?: "Błąd aktualizacji"
            }
        }
    }

    fun scanNow() {
        if (_isScanning.value) return
        viewModelScope.launch {
            _isScanning.update { true }
            try {
                scanMissedCallsUseCase()
            } catch (e: Exception) {
                _actionError.value = e.message ?: "Błąd skanowania"
            } finally {
                _isScanning.update { false }
            }
        }
    }

    fun addCurrentUserAsRecipient(callLogId: String) {
        viewModelScope.launch {
            try {
                val user = getCurrentUserUseCase()
                appendRecipientUseCase(callLogId, user.id)
            } catch (e: Exception) {
                _actionError.value = e.message ?: "Błąd dodawania odbiorcy"
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.update { true }
            try {
                _refreshKey.update { it + 1 }
            } finally {
                _isRefreshing.update { false }
            }
        }
    }

    fun makeCall(phoneNumber: String) {
        makeCallUseCase(phoneNumber)
    }
}
