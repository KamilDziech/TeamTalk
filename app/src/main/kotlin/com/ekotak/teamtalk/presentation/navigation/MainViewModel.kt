package com.ekotak.teamtalk.presentation.navigation

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.ekotak.teamtalk.data.scanner.CallLogScanWorker
import com.ekotak.teamtalk.domain.usecase.auth.ObserveSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    observeSessionUseCase: ObserveSessionUseCase,
    @ApplicationContext context: Context,
) : ViewModel() {

    sealed interface SessionState {
        data object Loading : SessionState
        data object Unauthenticated : SessionState
        data object Authenticated : SessionState
    }

    val sessionState: StateFlow<SessionState> = observeSessionUseCase()
        .map { session ->
            if (session != null) SessionState.Authenticated else SessionState.Unauthenticated
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SessionState.Loading)

    init {
        val workManager = WorkManager.getInstance(context)
        sessionState.onEach { state ->
            when (state) {
                SessionState.Authenticated   -> CallLogScanWorker.schedule(workManager)
                SessionState.Unauthenticated -> CallLogScanWorker.cancel(workManager)
                SessionState.Loading         -> {}
            }
        }.launchIn(viewModelScope)
    }
}
