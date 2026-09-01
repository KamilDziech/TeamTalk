package com.ekotak.teamtalk.presentation.client

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.domain.model.Client
import com.ekotak.teamtalk.domain.model.ClientCategory
import com.ekotak.teamtalk.domain.model.ClientDraft
import com.ekotak.teamtalk.domain.model.ClientType
import com.ekotak.teamtalk.domain.model.NewClient
import com.ekotak.teamtalk.domain.usecase.client.CreateClientUseCase
import com.ekotak.teamtalk.domain.usecase.client.ObserveClientUseCase
import com.ekotak.teamtalk.domain.usecase.client.UpdateClientUseCase
import com.ekotak.teamtalk.presentation.crm.crmErrorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Formularz kartoteki w dwóch trybach: nowy wpis (`POST /api/clients`) i edycja
 * istniejącego (`PATCH /api/clients/:id`). Oba prowadzą przez ten sam ekran, bo
 * różnią się w praktyce jednym polem (typ „własny/partnera" tylko dla klienta)
 * i tym, że edycja wysyła wyłącznie zmienione pola.
 *
 * Walidację powtarzamy lokalnie tylko po to, żeby nie wysyłać żądania skazanego
 * na 422 — źródłem prawdy zostaje schemat po stronie API.
 */
@HiltViewModel
class ClientFormViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observeClientUseCase: ObserveClientUseCase,
    private val createClientUseCase: CreateClientUseCase,
    private val updateClientUseCase: UpdateClientUseCase,
) : ViewModel() {

    private val clientId: String? = savedStateHandle.get<String>("clientId")?.takeIf { it.isNotBlank() }

    data class UiState(
        val isEdit: Boolean = false,
        val isLoading: Boolean = false,
        val isSaving: Boolean = false,
        val category: ClientCategory = ClientCategory.KLIENT,
        val firstName: String = "",
        val lastName: String = "",
        val phone: String = "",
        val phone2: String = "",
        val email: String = "",
        val email2: String = "",
        val address: String = "",
        val type: ClientType = ClientType.WLASNY,
        val error: String? = null,
        /** Ustawiany po udanym zapisie — ekran ma się zamknąć z tym komunikatem. */
        val savedMessage: String? = null,
    ) {
        val title: String
            get() = if (isEdit) "Edycja danych" else "Nowy ${category.oneLabel}"
    }

    private val _uiState = MutableStateFlow(
        UiState(
            isEdit = clientId != null,
            isLoading = clientId != null,
            category = ClientCategory.fromWire(savedStateHandle.get<String>("category")),
        ),
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** Oryginał do zbudowania patcha (wysyłamy tylko faktyczne zmiany). */
    private var original: Client? = null

    init {
        if (clientId != null) loadClient(clientId)
    }

    private fun loadClient(id: String) {
        viewModelScope.launch {
            val client = observeClientUseCase(id).first()
            if (client == null) {
                _uiState.update {
                    it.copy(isLoading = false, error = "Nie znaleziono klienta w kartotece.")
                }
                return@launch
            }
            original = client
            _uiState.update {
                it.copy(
                    isLoading = false,
                    category = client.category,
                    firstName = client.firstName,
                    lastName = client.lastName,
                    phone = client.phone.orEmpty(),
                    phone2 = client.phone2.orEmpty(),
                    email = client.email.orEmpty(),
                    email2 = client.email2.orEmpty(),
                    address = client.address.orEmpty(),
                    type = client.type,
                )
            }
        }
    }

    fun onFirstName(value: String) = _uiState.update { it.copy(firstName = value, error = null) }
    fun onLastName(value: String) = _uiState.update { it.copy(lastName = value, error = null) }
    fun onPhone(value: String) = _uiState.update { it.copy(phone = value, error = null) }
    fun onPhone2(value: String) = _uiState.update { it.copy(phone2 = value, error = null) }
    fun onEmail(value: String) = _uiState.update { it.copy(email = value, error = null) }
    fun onEmail2(value: String) = _uiState.update { it.copy(email2 = value, error = null) }
    fun onAddress(value: String) = _uiState.update { it.copy(address = value, error = null) }
    fun onType(value: ClientType) = _uiState.update { it.copy(type = value) }

    fun save() {
        val state = _uiState.value
        val firstName = state.firstName.trim()
        val lastName = state.lastName.trim()
        if (firstName.isBlank() || lastName.isBlank()) {
            _uiState.update { it.copy(error = "Imię i nazwisko są wymagane.") }
            return
        }
        val emails = listOf(state.email.trim(), state.email2.trim()).filter { it.isNotBlank() }
        if (emails.any { !it.contains("@") || it.startsWith("@") || it.endsWith("@") }) {
            _uiState.update { it.copy(error = "Nieprawidłowy adres e-mail.") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                val existing = original
                if (existing != null) {
                    updateClientUseCase(
                        original = existing,
                        draft = ClientDraft(
                            firstName = firstName,
                            lastName = lastName,
                            email = state.email.trim().ifBlank { null },
                            email2 = state.email2.trim().ifBlank { null },
                            phone = state.phone.trim().ifBlank { null },
                            phone2 = state.phone2.trim().ifBlank { null },
                            address = state.address.trim().ifBlank { null },
                        ),
                    )
                    _uiState.update {
                        it.copy(isSaving = false, savedMessage = "Zapisano dane: $firstName $lastName")
                    }
                } else {
                    createClientUseCase(
                        NewClient(
                            firstName = firstName,
                            lastName = lastName,
                            email = state.email.trim().ifBlank { null },
                            phone = state.phone.trim().ifBlank { null },
                            address = state.address.trim().ifBlank { null },
                            // Typ rozróżnia tylko klientów — kontrahent i afiliant
                            // dostają go z domyślnej wartości schematu API.
                            type = if (state.category == ClientCategory.KLIENT) state.type else null,
                            category = state.category,
                        ),
                    )
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            savedMessage = "Dodano: ${state.category.oneLabel} $firstName $lastName",
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        error = crmErrorMessage(
                            e,
                            if (state.isEdit) "Nie udało się zapisać zmian" else "Nie udało się dodać wpisu",
                        ),
                    )
                }
            }
        }
    }
}
