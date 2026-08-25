package com.ekotak.teamtalk.presentation.calllog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.domain.model.CallDirection
import com.ekotak.teamtalk.domain.model.CallLog
import com.ekotak.teamtalk.domain.model.CallLogFilter
import com.ekotak.teamtalk.domain.usecase.calllog.GetCallLogsUseCase
import com.ekotak.teamtalk.domain.usecase.calllog.MakeCallUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

@HiltViewModel
class CallLogViewModel @Inject constructor(
    private val getCallLogsUseCase: GetCallLogsUseCase,
    private val makeCallUseCase: MakeCallUseCase,
) : ViewModel() {

    // Zakładka „Nieodebrane" — tylko połączenia nieodebrane.
    val callLogs: StateFlow<List<CallLog>> =
        getCallLogsUseCase(CallLogFilter(direction = CallDirection.MISSED.value))
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /** Pojedyncze połączenie po ID — dla ekranu szczegółów. */
    fun observeCallLog(id: String): Flow<CallLog?> =
        getCallLogsUseCase(CallLogFilter()).map { list -> list.find { it.id == id } }

    fun makeCall(phoneNumber: String) = makeCallUseCase(phoneNumber)
}
