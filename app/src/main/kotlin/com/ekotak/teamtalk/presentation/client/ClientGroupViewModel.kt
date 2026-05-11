package com.ekotak.teamtalk.presentation.client

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.domain.model.ClientGroup
import com.ekotak.teamtalk.domain.usecase.client.GetClientsUseCase
import com.ekotak.teamtalk.domain.usecase.clientgroup.CreateClientGroupUseCase
import com.ekotak.teamtalk.domain.usecase.clientgroup.EnsureDefaultGroupUseCase
import com.ekotak.teamtalk.domain.usecase.clientgroup.GetClientGroupsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ClientGroupViewModel @Inject constructor(
    private val getClientGroupsUseCase: GetClientGroupsUseCase,
    private val createClientGroupUseCase: CreateClientGroupUseCase,
    private val ensureDefaultGroupUseCase: EnsureDefaultGroupUseCase,
    private val getClientsUseCase: GetClientsUseCase,
) : ViewModel() {

    data class UiState(
        val groups: List<ClientGroup> = emptyList(),
        val clientCountMap: Map<String, Int> = emptyMap(),
    )

    data class DialogState(
        val isVisible: Boolean = false,
        val name: String = "",
        val isLoading: Boolean = false,
        val error: String? = null,
    )

    val uiState: StateFlow<UiState> = combine(
        getClientGroupsUseCase(),
        getClientsUseCase(),
    ) { groups, clients ->
        val countMap = clients
            .filter { it.groupId != null }
            .groupBy { it.groupId!! }
            .mapValues { it.value.size }
        UiState(groups = groups, clientCountMap = countMap)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    private val _dialogState = MutableStateFlow(DialogState())
    val dialogState: StateFlow<DialogState> = _dialogState.asStateFlow()

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError.asStateFlow()

    init {
        viewModelScope.launch {
            try { ensureDefaultGroupUseCase() } catch (_: Exception) {}
        }
    }

    fun showCreateDialog() { _dialogState.update { DialogState(isVisible = true) } }
    fun hideCreateDialog() { _dialogState.update { DialogState() } }
    fun onGroupNameChange(name: String) { _dialogState.update { it.copy(name = name, error = null) } }
    fun clearActionError() { _actionError.value = null }

    fun createGroup() {
        val name = _dialogState.value.name.trim()
        if (name.isBlank()) {
            _dialogState.update { it.copy(error = "Podaj nazwę grupy") }
            return
        }
        viewModelScope.launch {
            _dialogState.update { it.copy(isLoading = true, error = null) }
            try {
                createClientGroupUseCase(name = name)
                _dialogState.value = DialogState()
            } catch (e: Exception) {
                _dialogState.update { it.copy(isLoading = false, error = e.message ?: "Błąd tworzenia grupy") }
            }
        }
    }
}
