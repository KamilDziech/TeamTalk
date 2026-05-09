package com.ekotak.teamtalk.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.domain.usecase.auth.LoginUseCase
import com.ekotak.teamtalk.domain.usecase.auth.RegisterUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase,
    private val registerUseCase: RegisterUseCase,
) : ViewModel() {

    data class LoginUiState(
        val email: String = "",
        val password: String = "",
        val passwordVisible: Boolean = false,
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
    )

    data class RegisterUiState(
        val email: String = "",
        val password: String = "",
        val displayName: String = "",
        val passwordVisible: Boolean = false,
        val isLoading: Boolean = false,
        val errorMessage: String? = null,
    )

    private val _loginState = MutableStateFlow(LoginUiState())
    val loginState: StateFlow<LoginUiState> = _loginState.asStateFlow()

    private val _registerState = MutableStateFlow(RegisterUiState())
    val registerState: StateFlow<RegisterUiState> = _registerState.asStateFlow()

    fun onLoginEmailChange(value: String) =
        _loginState.update { it.copy(email = value, errorMessage = null) }

    fun onLoginPasswordChange(value: String) =
        _loginState.update { it.copy(password = value, errorMessage = null) }

    fun onLoginPasswordVisibilityToggle() =
        _loginState.update { it.copy(passwordVisible = !it.passwordVisible) }

    fun login() {
        val state = _loginState.value
        viewModelScope.launch {
            _loginState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                loginUseCase(state.email.trim(), state.password)
                // Session saved → ObserveSessionUseCase triggers NavGraph navigation automatically
            } catch (e: Exception) {
                _loginState.update {
                    it.copy(isLoading = false, errorMessage = e.message ?: "Błąd logowania")
                }
            }
        }
    }

    fun onRegisterEmailChange(value: String) =
        _registerState.update { it.copy(email = value, errorMessage = null) }

    fun onRegisterPasswordChange(value: String) =
        _registerState.update { it.copy(password = value, errorMessage = null) }

    fun onRegisterDisplayNameChange(value: String) =
        _registerState.update { it.copy(displayName = value, errorMessage = null) }

    fun onRegisterPasswordVisibilityToggle() =
        _registerState.update { it.copy(passwordVisible = !it.passwordVisible) }

    fun register() {
        val state = _registerState.value
        viewModelScope.launch {
            _registerState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                registerUseCase(state.email.trim(), state.password, state.displayName.trim())
            } catch (e: Exception) {
                _registerState.update {
                    it.copy(isLoading = false, errorMessage = e.message ?: "Błąd rejestracji")
                }
            }
        }
    }
}
