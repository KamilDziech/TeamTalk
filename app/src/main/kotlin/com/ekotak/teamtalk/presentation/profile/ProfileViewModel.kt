package com.ekotak.teamtalk.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.domain.usecase.auth.GetCurrentUserUseCase
import com.ekotak.teamtalk.domain.usecase.auth.LogoutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Profil = bieżący użytkownik z sesji board360 (read-only). board360 nie udostępnia
 * edycji profilu serwisanta z aplikacji — zmiany danych robi panel web.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val logoutUseCase: LogoutUseCase,
) : ViewModel() {

    data class UiState(
        val userId: String = "",
        val email: String = "",
        val displayName: String = "",
        val role: String = "",
        val isAdmin: Boolean = false,
        val isLoggingOut: Boolean = false,
        val errorMessage: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val user = getCurrentUserUseCase()
                _uiState.update {
                    it.copy(
                        userId = user.id,
                        email = user.email,
                        displayName = user.displayName,
                        role = user.role,
                        isAdmin = user.isAdmin,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Błąd ładowania profilu") }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoggingOut = true, errorMessage = null) }
            try {
                logoutUseCase()
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoggingOut = false, errorMessage = e.message ?: "Błąd wylogowania") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
