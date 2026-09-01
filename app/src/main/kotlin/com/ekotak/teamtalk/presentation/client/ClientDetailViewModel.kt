package com.ekotak.teamtalk.presentation.client

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.domain.model.AssistantMessage
import com.ekotak.teamtalk.domain.model.Client
import com.ekotak.teamtalk.domain.model.ClientDeal
import com.ekotak.teamtalk.domain.repository.AuthRepository
import com.ekotak.teamtalk.domain.usecase.calllog.MakeCallUseCase
import com.ekotak.teamtalk.domain.usecase.client.AskClientAssistantUseCase
import com.ekotak.teamtalk.domain.usecase.client.EraseClientUseCase
import com.ekotak.teamtalk.domain.usecase.client.GetClientDirectoryUseCase
import com.ekotak.teamtalk.domain.usecase.client.GetClientsUseCase
import com.ekotak.teamtalk.domain.usecase.client.NavigateToClientUseCase
import com.ekotak.teamtalk.domain.usecase.client.ObserveClientUseCase
import com.ekotak.teamtalk.domain.usecase.client.SendSmsUseCase
import com.ekotak.teamtalk.presentation.crm.crmErrorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Uprawnienia board360: edycja kartoteki i anonimizacja danych (RODO). */
private const val PERMISSION_DEAL_MANAGE = "deal.manage"
private const val PERMISSION_SETTINGS_COMPANY = "settings.company"

/**
 * Karta klienta — mobilny odpowiednik szuflady z kartoteki board360. Dane
 * klienta idą ze strumienia Room (są od razu, także offline), a szanse,
 * instalacje i deale wspólne dociągamy z lejka przy wejściu.
 *
 * Uprawnienia czytamy z `GET /api/me` przy każdym wejściu — sesja w DataStore
 * ich nie trzyma, a rola mogła się zmienić w panelu. Brak uprawnienia chowa
 * akcję; autorytatywnym gate'em zostaje API.
 */
@HiltViewModel
class ClientDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observeClientUseCase: ObserveClientUseCase,
    private val getClientsUseCase: GetClientsUseCase,
    private val getClientDirectoryUseCase: GetClientDirectoryUseCase,
    private val askClientAssistantUseCase: AskClientAssistantUseCase,
    private val eraseClientUseCase: EraseClientUseCase,
    private val makeCallUseCase: MakeCallUseCase,
    private val sendSmsUseCase: SendSmsUseCase,
    private val navigateToClientUseCase: NavigateToClientUseCase,
    private val authRepository: AuthRepository,
) : ViewModel() {

    val clientId: String = savedStateHandle["clientId"] ?: ""

    data class UiState(
        val isLoading: Boolean = true,
        val client: Client? = null,
        val deals: List<ClientDeal> = emptyList(),
        val installations: List<String> = emptyList(),
        val sharedWith: List<String> = emptyList(),
        val canManage: Boolean = false,
        val canErase: Boolean = false,
        val message: String? = null,
        /** Rozmowa z asystentem karty klienta (multi-turn). */
        val assistantLog: List<AssistantMessage> = emptyList(),
        val assistantPending: Boolean = false,
        val assistantNotice: String? = null,
        val assistantError: String? = null,
        val isErasing: Boolean = false,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        observeClient()
        loadDirectory()
        loadPermissions()
    }

    private fun observeClient() {
        viewModelScope.launch {
            observeClientUseCase(clientId).collect { client ->
                _uiState.update { it.copy(client = client, isLoading = false) }
            }
        }
    }

    /** Szanse i instalacje klienta; awarię lejka pokazujemy jako komunikat. */
    private fun loadDirectory() {
        viewModelScope.launch {
            try {
                val directory = getClientDirectoryUseCase()
                // Nazwy współkontaktów — jedna migawka kartoteki wystarczy,
                // karta nie potrzebuje ich na żywo.
                val nameById = getClientsUseCase().first().associate { it.id to it.displayName }
                _uiState.update {
                    it.copy(
                        deals = directory.dealsByClient[clientId].orEmpty()
                            .sortedByDescending { deal -> (deal.updatedAt ?: deal.createdAt).orEmpty() },
                        installations = directory.installByClient[clientId].orEmpty(),
                        sharedWith = directory.sharedIdsByClient[clientId]
                            .orEmpty()
                            .map { id -> nameById[id] ?: "?" },
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(message = crmErrorMessage(e, "Nie udało się wczytać szans klienta"))
                }
            }
        }
    }

    private fun loadPermissions() {
        viewModelScope.launch {
            val permissions = try {
                authRepository.getCurrentUser().permissions
            } catch (_: Exception) {
                emptyList()
            }
            _uiState.update {
                it.copy(
                    canManage = permissions.contains(PERMISSION_DEAL_MANAGE),
                    canErase = permissions.contains(PERMISSION_SETTINGS_COMPANY),
                )
            }
        }
    }

    fun call() {
        _uiState.value.client?.primaryPhone?.let(makeCallUseCase::invoke)
    }

    fun sms() {
        _uiState.value.client?.primaryPhone?.let(sendSmsUseCase::invoke)
    }

    fun navigate() {
        val client = _uiState.value.client ?: return
        navigateToClientUseCase(client.address, client.geoLat, client.geoLng)
    }

    /** Anonimizacja RODO. Po sukcesie karta zostaje — dane znikają z rekordu. */
    fun erase(onDone: () -> Unit) {
        val client = _uiState.value.client ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isErasing = true) }
            try {
                eraseClientUseCase(client.id)
                _uiState.update {
                    it.copy(isErasing = false, message = "Zanonimizowano dane klienta (RODO).")
                }
                onDone()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isErasing = false,
                        message = crmErrorMessage(e, "Nie udało się zanonimizować danych"),
                    )
                }
            }
        }
    }

    fun ask(question: String) {
        val text = question.trim()
        if (text.isBlank() || _uiState.value.assistantPending) return
        val log = _uiState.value.assistantLog + AssistantMessage(AssistantMessage.ROLE_USER, text)
        _uiState.update {
            it.copy(
                assistantLog = log,
                assistantPending = true,
                assistantError = null,
                assistantNotice = null,
            )
        }
        viewModelScope.launch {
            try {
                val reply = askClientAssistantUseCase(clientId, log)
                _uiState.update {
                    it.copy(
                        assistantLog = it.assistantLog +
                            AssistantMessage(AssistantMessage.ROLE_ASSISTANT, reply.text),
                        assistantPending = false,
                        assistantNotice = if (reply.configured) {
                            "Na podstawie ${reply.commsCount} wpisów z ${reply.dealCount} deali klienta."
                        } else {
                            "Asystent AI nie jest jeszcze skonfigurowany (brak klucza LLM)."
                        },
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        assistantPending = false,
                        assistantError = crmErrorMessage(e, "Nie udało się uzyskać odpowiedzi"),
                    )
                }
            }
        }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }
}
