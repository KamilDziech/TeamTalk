package com.ekotak.teamtalk.presentation.client

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.domain.model.ClientListEntry
import com.ekotak.teamtalk.domain.model.DuplicateGroup
import com.ekotak.teamtalk.domain.model.duplicateGroups
import com.ekotak.teamtalk.domain.usecase.client.GetClientDirectoryUseCase
import com.ekotak.teamtalk.domain.usecase.client.GetClientsUseCase
import com.ekotak.teamtalk.domain.usecase.client.MergeClientsUseCase
import com.ekotak.teamtalk.domain.usecase.client.RefreshClientsUseCase
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
 * Scalanie duplikatów kartoteki. Grupy liczymy tak samo jak panel (te same
 * imię i nazwisko, telefon lub e-mail), a domyślnym rekordem docelowym jest ten
 * z największą liczbą deali — po scaleniu wszystko i tak trafia do niego, więc
 * najmniej rzeczy się przenosi.
 *
 * Kto może scalać, rozstrzyga API (admin albo właściciel szansy) — tu nie
 * chowamy przycisku, tylko pokazujemy odpowiedź serwera.
 */
@HiltViewModel
class ClientMergeViewModel @Inject constructor(
    private val getClientsUseCase: GetClientsUseCase,
    private val getClientDirectoryUseCase: GetClientDirectoryUseCase,
    private val refreshClientsUseCase: RefreshClientsUseCase,
    private val mergeClientsUseCase: MergeClientsUseCase,
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val isMerging: Boolean = false,
        val groups: List<DuplicateGroup> = emptyList(),
        /** Wybrany rekord docelowy w każdej grupie (klucz = id pierwszego wpisu). */
        val targetByGroup: Map<String, String> = emptyMap(),
        val error: String? = null,
        val message: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                runCatching { refreshClientsUseCase() }
                val clients = getClientsUseCase().first()
                val directory = runCatching { getClientDirectoryUseCase() }.getOrNull()
                val entries = clients.map { client ->
                    ClientListEntry(
                        client = client,
                        deals = directory?.dealsByClient?.get(client.id).orEmpty(),
                    )
                }
                val groups = duplicateGroups(entries)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        groups = groups,
                        targetByGroup = groups.associate { group ->
                            groupKey(group) to group.clients.first().client.id
                        },
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = crmErrorMessage(e, "Nie udało się wczytać kartoteki"),
                    )
                }
            }
        }
    }

    fun selectTarget(group: DuplicateGroup, clientId: String) {
        _uiState.update { it.copy(targetByGroup = it.targetByGroup + (groupKey(group) to clientId)) }
    }

    fun merge(group: DuplicateGroup) {
        val targetId = _uiState.value.targetByGroup[groupKey(group)] ?: return
        val sourceIds = group.clients.map { it.client.id }.filter { it != targetId }
        if (sourceIds.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isMerging = true, error = null) }
            try {
                mergeClientsUseCase(targetId, sourceIds)
                _uiState.update {
                    it.copy(isMerging = false, message = "Scalono kontakty (${sourceIds.size + 1}).")
                }
                load()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isMerging = false, error = crmErrorMessage(e, "Nie udało się scalić"))
                }
            }
        }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }

    fun clearError() = _uiState.update { it.copy(error = null) }

    private fun groupKey(group: DuplicateGroup): String = group.clients.first().client.id
}
