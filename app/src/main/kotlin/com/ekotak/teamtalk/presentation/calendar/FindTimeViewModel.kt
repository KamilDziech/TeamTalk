package com.ekotak.teamtalk.presentation.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.data.local.preferences.SessionPreferences
import com.ekotak.teamtalk.domain.model.FreeBusy
import com.ekotak.teamtalk.domain.model.TaskMember
import com.ekotak.teamtalk.domain.repository.CalendarRepository
import com.ekotak.teamtalk.presentation.crm.crmErrorMessage
import com.ekotak.teamtalk.presentation.crm.parseIsoMillis
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

/**
 * „Znajdź termin" — mobilny odpowiednik `FindTimeModal.tsx`.
 *
 * `GET /calendar/events/freebusy` oddaje same przedziały zajętości, bez treści
 * wydarzeń, więc widać, kiedy ktoś jest wolny, ale nie na czym siedzi. Sloty
 * liczymy lokalnie z tych przedziałów — dokładnie tak jak panel.
 */
@HiltViewModel
class FindTimeViewModel @Inject constructor(
    private val repository: CalendarRepository,
    private val sessionPreferences: SessionPreferences,
) : ViewModel() {

    /** Wolne okno wspólne dla wszystkich zaznaczonych osób. */
    data class Slot(val startMillis: Long, val endMillis: Long)

    data class UiState(
        val isLoading: Boolean = false,
        val error: String? = null,
        val members: List<TaskMember> = emptyList(),
        val selected: Set<String> = emptySet(),
        val day: LocalDate = LocalDate.now(),
        val durationMinutes: Int = 60,
        val busy: List<FreeBusy> = emptyList(),
        val slots: List<Slot> = emptyList(),
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val userId = sessionPreferences.session.first()?.userId
            repository.observe().collect { snapshot ->
                _uiState.update { state ->
                    state.copy(
                        members = snapshot.members,
                        // Szukamy terminu dla siebie i kogoś — zalogowany wchodzi
                        // do zestawu od razu, żeby nie trzeba go było zaznaczać.
                        selected = if (state.selected.isEmpty() && userId != null) {
                            setOf(userId)
                        } else {
                            state.selected
                        },
                    )
                }
            }
        }
        load()
    }

    fun toggleMember(id: String) {
        _uiState.update { state ->
            val selected = state.selected.toMutableSet()
            if (!selected.add(id)) selected.remove(id)
            state.copy(selected = selected)
        }
        load()
    }

    fun setDay(day: LocalDate) {
        _uiState.update { it.copy(day = day) }
        load()
    }

    fun stepDay(direction: Int) = setDay(_uiState.value.day.plusDays(direction.toLong()))

    fun setDuration(minutes: Int) {
        _uiState.update { it.copy(durationMinutes = minutes) }
        recompute()
    }

    fun load() {
        val state = _uiState.value
        if (state.selected.isEmpty()) {
            _uiState.update { it.copy(busy = emptyList(), slots = emptyList()) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val busy = repository.freeBusy(
                    userIds = state.selected.toList(),
                    fromIso = isoUtc(dateToMillis(state.day)),
                    toIso = isoUtc(dateToMillis(state.day.plusDays(1))),
                )
                _uiState.update { it.copy(isLoading = false, busy = busy) }
                recompute()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = crmErrorMessage(e, "Nie udało się pobrać zajętości"),
                    )
                }
            }
        }
    }

    /**
     * Wolne okna w godzinach pracy, krokiem co pół godziny. Szukamy tylko
     * w [WORK_START]–[WORK_END]: propozycja spotkania o 4:00 nad ranem byłaby
     * formalnie poprawna i do niczego.
     */
    private fun recompute() {
        val state = _uiState.value
        val dayStart = dateTimeToMillis(state.day.atTime(LocalTime.of(WORK_START, 0)))
        val dayEnd = dateTimeToMillis(state.day.atTime(LocalTime.of(WORK_END, 0)))
        val duration = state.durationMinutes * 60_000L
        val busy = state.busy.flatMap { person ->
            person.busy.mapNotNull { slot ->
                val from = parseIsoMillis(slot.startAt) ?: return@mapNotNull null
                val to = parseIsoMillis(slot.endAt) ?: return@mapNotNull null
                from to to
            }
        }

        val slots = mutableListOf<Slot>()
        var cursor = dayStart
        while (cursor + duration <= dayEnd && slots.size < MAX_SLOTS) {
            val end = cursor + duration
            val free = busy.none { (from, to) -> from < end && to > cursor }
            if (free) slots += Slot(cursor, end)
            cursor += STEP_MS
        }
        _uiState.update { it.copy(slots = slots) }
    }

    private companion object {
        const val WORK_START = 7
        const val WORK_END = 18
        const val STEP_MS = 30 * 60_000L
        const val MAX_SLOTS = 8
    }
}
