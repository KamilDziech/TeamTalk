package com.ekotak.teamtalk.presentation.inventory

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.domain.model.Product
import com.ekotak.teamtalk.domain.model.StockReservation
import com.ekotak.teamtalk.domain.repository.InventoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Karta pozycji magazynu. Czyta z tej samej migawki co lista, więc wejście
 * w kartę nie kosztuje ani jednego zapytania — a bez zasięgu działa tak samo
 * jak z zasięgiem.
 *
 * Kolejka „pod kogo" jest ułożona wg DATY MONTAŻU (`neededBy` rosnąco, brak daty
 * na koniec) — tak samo przydziela towar serwer. Dzięki temu magazynier widzi
 * na telefonie tę samą kolejność, w której panel liczył pokrycie.
 */
@HiltViewModel
class ProductDetailViewModel @Inject constructor(
    private val repository: InventoryRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val product: Product? = null,
        val reservations: List<StockReservation> = emptyList(),
        val reservationsAvailable: Boolean = true,
        val syncedAt: Long? = null,
    )

    private val productId: String = savedStateHandle.get<String>("productId").orEmpty()

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observe().collect { snap ->
                val product = snap.products.firstOrNull { it.id == productId }
                val lines = snap.reservations
                    .filter { it.productId == productId && it.isActive }
                    .sortedWith(
                        compareBy(nullsLast()) { it.neededBy },
                    )
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        product = product,
                        reservations = lines,
                        reservationsAvailable = snap.reservationsAvailable,
                        syncedAt = snap.syncedAt,
                    )
                }
            }
        }
    }
}
