package com.ekotak.teamtalk.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.domain.usecase.auth.ObserveSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    observeSessionUseCase: ObserveSessionUseCase,
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
}
