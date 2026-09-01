package com.ekotak.teamtalk.presentation.crm

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.domain.model.Deal
import com.ekotak.teamtalk.domain.model.DealDraft
import com.ekotak.teamtalk.domain.model.TaskMember
import com.ekotak.teamtalk.domain.model.hasChangesFrom
import com.ekotak.teamtalk.domain.model.toDraft
import com.ekotak.teamtalk.domain.repository.TaskRepository
import com.ekotak.teamtalk.domain.usecase.deal.GetDealDetailUseCase
import com.ekotak.teamtalk.domain.usecase.deal.UpdateDealUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Edycja karty deala. Formularz trzyma komplet edytowalnych pól (`DealDraft`),
 * a do API idzie wyłącznie różnica względem wczytanego deala — jeden `PATCH`
 * na zapis, bez nadpisywania pól, których nikt nie ruszał.
 */
@HiltViewModel
class DealEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getDealDetailUseCase: GetDealDetailUseCase,
    private val updateDealUseCase: UpdateDealUseCase,
    private val taskRepository: TaskRepository,
) : ViewModel() {

    private val dealId: String = savedStateHandle["dealId"] ?: ""

    /**
     * Surowy tekst pól liczbowych. Trzymany osobno od draftu, bo w trakcie
     * pisania („1", „12", „") wartość bywa niesparsowalna — gdyby ekran czytał
     * liczbę z draftu, znaki znikałyby użytkownikowi spod palca.
     */
    data class NumberText(
        val people: String = "",
        val areaM2: String = "",
        val floors: String = "",
        val ozcBuildingKw: String = "",
        val ozcDhwKw: String = "",
        val meetingDurationMin: String = "",
    )

    data class UiState(
        val isLoading: Boolean = true,
        val isSaving: Boolean = false,
        val error: String? = null,
        val message: String? = null,
        /** Zapis się powiódł — ekran może się zamknąć. */
        val saved: Boolean = false,
        val original: Deal? = null,
        val draft: DealDraft = DealDraft(),
        val numbers: NumberText = NumberText(),
        val members: List<TaskMember> = emptyList(),
        /** Klient karty — tylko do nagłówka, edycja kartoteki jest w panelu. */
        val clientName: String? = null,
    ) {
        val isDirty: Boolean get() = original?.hasChangesFrom(draft) == true
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val detail = getDealDetailUseCase(dealId)
                val draft = detail.deal.toDraft()
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        original = detail.deal,
                        draft = draft,
                        numbers = draft.toNumberText(),
                        clientName = detail.client?.displayName,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = crmErrorMessage(e, "Nie udało się wczytać karty deala"),
                    )
                }
            }
            loadMembers()
        }
    }

    /**
     * Lista osób do wyboru opiekunów. Endpoint należy do modułu zadań
     * (`tasks.view`) — bez tego uprawnienia zostawiamy pustą listę, a ekran
     * chowa selektory opiekunów zamiast pokazywać puste pole.
     */
    private suspend fun loadMembers() {
        val members = try {
            taskRepository.getMembers()
        } catch (_: Exception) {
            emptyList()
        }
        _uiState.update { it.copy(members = members) }
    }

    /** Każda zmiana pola przechodzi tędy — jedno miejsce mutacji draftu. */
    fun edit(transform: (DealDraft) -> DealDraft) {
        _uiState.update { it.copy(draft = transform(it.draft)) }
    }

    // ── Pola liczbowe: aktualizują i tekst, i wartość w drafcie ───────────────

    fun onPeopleChange(text: String) = editNumber(text) { s, d ->
        s.copy(people = text) to d.copy(people = text.toIntOrNull())
    }

    fun onAreaChange(text: String) = editNumber(text) { s, d ->
        s.copy(areaM2 = text) to d.copy(areaM2 = text.toIntOrNull())
    }

    fun onFloorsChange(text: String) = editNumber(text) { s, d ->
        s.copy(floors = text) to d.copy(floors = text.toIntOrNull())
    }

    fun onOzcBuildingKwChange(text: String) = editNumber(text) { s, d ->
        s.copy(ozcBuildingKw = text) to d.copy(ozcBuildingKw = text.toDecimalOrNull())
    }

    fun onOzcDhwKwChange(text: String) = editNumber(text) { s, d ->
        s.copy(ozcDhwKw = text) to d.copy(ozcDhwKw = text.toDecimalOrNull())
    }

    fun onMeetingDurationChange(text: String) = editNumber(text) { s, d ->
        s.copy(meetingDurationMin = text) to d.copy(meetingDurationMin = text.toIntOrNull())
    }

    private fun editNumber(
        text: String,
        transform: (NumberText, DealDraft) -> Pair<NumberText, DealDraft>,
    ) {
        // Wpisany śmieć („12a") nie może wywrócić zapisu — do draftu trafia
        // tylko to, co się parsuje, a tekst i tak zostaje na ekranie.
        if (!text.isNumericInput()) return
        _uiState.update { state ->
            val (numbers, draft) = transform(state.numbers, state.draft)
            state.copy(numbers = numbers, draft = draft)
        }
    }

    fun save() {
        val state = _uiState.value
        val original = state.original ?: return
        if (!state.isDirty) {
            _uiState.update { it.copy(message = "Nic się nie zmieniło") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, message = null) }
            try {
                val updated = updateDealUseCase(original, state.draft)
                val draft = updated.toDraft()
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        original = updated,
                        draft = draft,
                        numbers = draft.toNumberText(),
                        saved = true,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSaving = false, message = crmErrorMessage(e, "Nie udało się zapisać"))
                }
            }
        }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }
}

/** Wartości liczbowe draftu jako tekst startowy formularza. */
private fun DealDraft.toNumberText() = DealEditViewModel.NumberText(
    people = people?.toString().orEmpty(),
    areaM2 = areaM2?.toString().orEmpty(),
    floors = floors?.toString().orEmpty(),
    ozcBuildingKw = ozcBuildingKw?.toPlainText().orEmpty(),
    ozcDhwKw = ozcDhwKw?.toPlainText().orEmpty(),
    meetingDurationMin = meetingDurationMin?.toString().orEmpty(),
)
