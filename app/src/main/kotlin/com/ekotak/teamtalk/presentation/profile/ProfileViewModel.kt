package com.ekotak.teamtalk.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.domain.usecase.auth.GetCurrentUserUseCase
import com.ekotak.teamtalk.domain.usecase.auth.LogoutUseCase
import com.ekotak.teamtalk.domain.usecase.profile.GetProfilesUseCase
import com.ekotak.teamtalk.domain.usecase.profile.UpdateProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val getCurrentUserUseCase: GetCurrentUserUseCase,
    private val getProfilesUseCase: GetProfilesUseCase,
    private val updateProfileUseCase: UpdateProfileUseCase,
    private val logoutUseCase: LogoutUseCase,
) : ViewModel() {

    data class UiState(
        val userId: String = "",
        val email: String = "",
        val displayName: String = "",
        val isAdmin: Boolean = false,
        val isEditing: Boolean = false,
        val editDisplayName: String = "",
        val isSaving: Boolean = false,
        val isLoggingOut: Boolean = false,
        val errorMessage: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val user = getCurrentUserUseCase()
                _uiState.update { it.copy(email = user.email, userId = user.id) }
                getProfilesUseCase(idEq = user.id).collect { profiles ->
                    profiles.firstOrNull()?.let { profile ->
                        _uiState.update { state ->
                            state.copy(
                                displayName = profile.displayName,
                                isAdmin = profile.isAdmin,
                                editDisplayName = if (!state.isEditing) profile.displayName
                                                  else state.editDisplayName,
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message ?: "Błąd ładowania profilu") }
            }
        }
    }

    fun startEditing() {
        _uiState.update { it.copy(isEditing = true, editDisplayName = it.displayName, errorMessage = null) }
    }

    fun cancelEditing() {
        _uiState.update { it.copy(isEditing = false, errorMessage = null) }
    }

    fun onEditDisplayNameChange(value: String) {
        _uiState.update { it.copy(editDisplayName = value, errorMessage = null) }
    }

    fun saveProfile() {
        val state = _uiState.value
        if (state.userId.isBlank()) return
        val newName = state.editDisplayName.trim()
        if (newName.isBlank()) {
            _uiState.update { it.copy(errorMessage = "Imię nie może być puste") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null) }
            try {
                updateProfileUseCase(id = state.userId, displayName = newName)
                _uiState.update { it.copy(isSaving = false, isEditing = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, errorMessage = e.message ?: "Błąd zapisu") }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoggingOut = true, errorMessage = null) }
            try {
                logoutUseCase()
                // SessionPreferences.clear() → ObserveSessionUseCase emits null → NavGraph navigates to auth
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoggingOut = false, errorMessage = e.message ?: "Błąd wylogowania") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(errorMessage = null) }
    }
}
