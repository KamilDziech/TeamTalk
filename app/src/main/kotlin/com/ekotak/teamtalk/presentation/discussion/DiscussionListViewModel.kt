package com.ekotak.teamtalk.presentation.discussion

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.domain.model.Discussion
import com.ekotak.teamtalk.domain.usecase.task.ListDiscussionsUseCase
import com.ekotak.teamtalk.presentation.crm.crmErrorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Skrzynka Komunikatora — dyskusje, w których bierzemy udział (ktoś nas wywołał
 * przez @ albo sami pisaliśmy). Bez cache: skrzynka ma sens tylko na świeżych
 * danych, a wejście w wątek i tak wchodzi w kartę zadania, która cache ma.
 */
@HiltViewModel
class DiscussionListViewModel @Inject constructor(
    private val listDiscussions: ListDiscussionsUseCase,
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val error: String? = null,
        val discussions: List<Discussion> = emptyList(),
    ) {
        val unreadTotal: Int get() = discussions.sumOf { it.unreadCount }
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        load(initial = true)
    }

    fun refresh() = load(initial = false)

    private fun load(initial: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = initial, isRefreshing = !initial, error = null) }
            try {
                // Wywołania na wierzch, reszta po dacie ostatniego komentarza
                // (kolejność z serwera). Nieprzeczytane przed przeczytanymi.
                val list = listDiscussions().sortedWith(
                    compareByDescending<Discussion> { it.mentionedMe && it.unreadCount > 0 }
                        .thenByDescending { it.unreadCount > 0 },
                )
                _uiState.update {
                    it.copy(isLoading = false, isRefreshing = false, discussions = list)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = crmErrorMessage(e, "Nie udało się pobrać dyskusji"),
                    )
                }
            }
        }
    }
}
