package com.ekotak.teamtalk.presentation.service

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.domain.model.Edit
import com.ekotak.teamtalk.domain.model.WarrantyCard
import com.ekotak.teamtalk.domain.model.WarrantyCardPatch
import com.ekotak.teamtalk.domain.model.WarrantyCardStatus
import com.ekotak.teamtalk.domain.model.WarrantyInspectionUpsert
import com.ekotak.teamtalk.domain.repository.ServiceRepository
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
 * Karta przeglądów gwarancyjnych — odpowiednik modala `WarrantyCardDetail.tsx`.
 * Na telefonie to pełny ekran: pięć wierszy harmonogramu nie mieści się w arkuszu,
 * a każdy z nich ma trzy pola do edycji.
 */
@HiltViewModel
class WarrantyCardViewModel @Inject constructor(
    private val repository: ServiceRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val cardId: String = checkNotNull(savedStateHandle["cardId"])

    data class UiState(
        val isLoading: Boolean = true,
        val card: WarrantyCard? = null,
        val pending: Boolean = false,
        val message: String? = null,
        val error: String? = null,
        val now: Long = System.currentTimeMillis(),
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observe().collect { snap ->
                val card = snap.cards.firstOrNull { it.id == cardId }
                _uiState.update {
                    it.copy(
                        isLoading = card == null && snap.syncedAt == null,
                        card = card,
                        error = if (card == null && snap.syncedAt != null) {
                            "Nie znaleziono karty — mogła zostać usunięta."
                        } else {
                            null
                        },
                    )
                }
            }
        }
        viewModelScope.launch {
            if (repository.observe().first().syncedAt == null) {
                runCatching { repository.refresh() }
                    .onFailure { e ->
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                error = crmErrorMessage(e, "Nie udało się wczytać karty"),
                            )
                        }
                    }
            }
        }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }

    fun setStatus(status: WarrantyCardStatus) =
        patch(WarrantyCardPatch(status = Edit(status)), "Zmieniono status karty.")

    fun setBrand(brand: String) {
        val current = _uiState.value.card?.brand
        val next = brand.trim()
        if (next.isBlank() || next == current) return
        patch(WarrantyCardPatch(brand = Edit(next)), "Zapisano producenta.")
    }

    fun setLocation(location: String) {
        val current = _uiState.value.card?.location.orEmpty()
        val next = location.trim()
        if (next == current) return
        patch(WarrantyCardPatch(location = Edit(next.ifBlank { null })), "Zapisano lokalizację.")
    }

    fun setNote(note: String) {
        val current = _uiState.value.card?.note.orEmpty()
        val next = note.trim()
        if (next == current) return
        patch(WarrantyCardPatch(note = Edit(next.ifBlank { null })), "Zapisano notatkę.")
    }

    fun setCommissionedAt(iso: String?) =
        patch(WarrantyCardPatch(commissionedAt = Edit(iso)), "Zapisano datę uruchomienia.")

    fun setUnits(
        outdoorModel: String?,
        outdoorSerial: String?,
        indoorModel: String?,
        indoorSerial: String?,
    ) = patch(
        WarrantyCardPatch(
            outdoorModel = Edit(outdoorModel?.trim()?.ifBlank { null }),
            outdoorSerial = Edit(outdoorSerial?.trim()?.ifBlank { null }),
            indoorModel = Edit(indoorModel?.trim()?.ifBlank { null }),
            indoorSerial = Edit(indoorSerial?.trim()?.ifBlank { null }),
        ),
        "Zapisano dane jednostek.",
    )

    /** Zapis wiersza harmonogramu (rok 1..5) — upsert po numerze przeglądu. */
    fun saveInspection(ordinal: Int, plannedAt: String?, doneAt: String?, price: Int?) {
        run("Zapisano przegląd #$ordinal.") {
            repository.upsertInspection(
                cardId,
                WarrantyInspectionUpsert(
                    ordinal = ordinal,
                    plannedAt = plannedAt,
                    doneAt = doneAt,
                    price = price,
                ),
            )
        }
    }

    private fun patch(patch: WarrantyCardPatch, okMessage: String) {
        if (patch.isEmpty) return
        run(okMessage) { repository.updateCard(cardId, patch) }
    }

    private fun run(okMessage: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(pending = true) }
            runCatching { block() }
                .onSuccess { _uiState.update { s -> s.copy(message = okMessage) } }
                .onFailure { e ->
                    _uiState.update { s ->
                        s.copy(message = crmErrorMessage(e, "Nie udało się zapisać"))
                    }
                }
            _uiState.update { it.copy(pending = false, now = System.currentTimeMillis()) }
        }
    }
}
