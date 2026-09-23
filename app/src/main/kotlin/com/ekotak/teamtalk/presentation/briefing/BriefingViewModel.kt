package com.ekotak.teamtalk.presentation.briefing

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.domain.model.BriefingItem
import com.ekotak.teamtalk.domain.repository.BriefingRepository
import com.ekotak.teamtalk.presentation.crm.crmErrorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Skrzynka odprawy. Moduł jest wyłącznie online (ustalenie 2026-09-23), więc
 * jedno pobranie na wejście i „pociągnij, by odświeżyć"; brak sieci to
 * komunikat, a nie pusty ekran udający, że nic nie przyszło.
 *
 * Odhaczenie idzie od razu na serwer i dopiero potem znika z listy oczekujących
 * — inaczej po powrocie na ekran komunikat wróciłby jako nieprzeczytany.
 */
@HiltViewModel
class BriefingViewModel @Inject constructor(
    private val repository: BriefingRepository,
) : ViewModel() {

    data class State(
        val items: List<BriefingItem> = emptyList(),
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val acking: String? = null,
        val error: String? = null,
        val message: String? = null,
    ) {
        /** Ile komunikatów czeka na moje odhaczenie. */
        val pending: Int get() = items.count { it.pending }
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        load(refresh = false)
    }

    fun refresh() = load(refresh = true)

    private fun load(refresh: Boolean) {
        _state.update { it.copy(isLoading = !refresh && it.items.isEmpty(), isRefreshing = refresh, error = null) }
        viewModelScope.launch {
            runCatching { repository.inbox() }
                .onSuccess { items ->
                    _state.update { it.copy(items = items, isLoading = false, isRefreshing = false) }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(isLoading = false, isRefreshing = false, error = crmErrorMessage(e, "Nie udało się wczytać odprawy"))
                    }
                }
        }
    }

    fun ack(id: String) {
        if (_state.value.acking != null) return
        _state.update { it.copy(acking = id) }
        viewModelScope.launch {
            runCatching { repository.ack(id) }
                .onSuccess {
                    _state.update { s ->
                        s.copy(
                            acking = null,
                            message = "Potwierdzono odbiór.",
                            items = s.items.map { if (it.id == id) it.copy(ackAt = nowIso()) else it },
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(acking = null, message = crmErrorMessage(e, "Nie udało się potwierdzić odbioru")) }
                }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }
}

/** Znacznik odhaczenia do podmiany na liście — serwer i tak odda swój przy odświeżeniu. */
private fun nowIso(): String = java.time.Instant.now().toString()
