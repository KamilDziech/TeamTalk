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
                    it.copy(isLoading = false, errorMessage = friendlyLoginError(e))
                }
            }
        }
    }

    /** Przyjazny, polski opis błędu logowania — board360 zwraca komunikaty po polsku. */
    private fun friendlyLoginError(e: Throwable): String = when (e) {
        is retrofit2.HttpException -> when (e.code()) {
            400 -> serverMessage(e) ?: "Podaj e-mail i hasło"
            401 -> serverMessage(e) ?: "Nieprawidłowy e-mail lub hasło"
            403 -> "To konto nie ma dostępu do aplikacji mobilnej"
            429 -> "Zbyt wiele prób logowania — odczekaj minutę i spróbuj ponownie"
            in 500..599 -> "Błąd serwera (${e.code()}) — spróbuj ponownie"
            else -> "Nie udało się zalogować (kod ${e.code()})"
        }
        is java.io.IOException -> "Brak połączenia z serwerem"
        else -> e.message ?: "Nie udało się zalogować"
    }

    /** Wyciąga `message` z ciała błędu board360: `{"message":"...","statusCode":401}`. */
    private fun serverMessage(e: retrofit2.HttpException): String? =
        runCatching { e.response()?.errorBody()?.string() }
            .getOrNull()
            ?.let { MESSAGE_FIELD.find(it)?.groupValues?.get(1) }
            ?.takeIf { it.isNotBlank() }

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

    private companion object {
        val MESSAGE_FIELD = Regex("\"message\"\\s*:\\s*\"([^\"]*)\"")
    }
}
