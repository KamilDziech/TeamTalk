package com.ekotak.teamtalk.presentation.leave

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.domain.leave.countWorkingDays
import com.ekotak.teamtalk.domain.model.LeaveAbsence
import com.ekotak.teamtalk.domain.model.LeaveBalance
import com.ekotak.teamtalk.domain.model.LeaveDraft
import com.ekotak.teamtalk.domain.model.LeaveMode
import com.ekotak.teamtalk.domain.model.LeaveOverlapException
import com.ekotak.teamtalk.domain.model.LeaveRequest
import com.ekotak.teamtalk.domain.model.LeaveType
import com.ekotak.teamtalk.domain.model.LeaveTypeNotAllowedException
import com.ekotak.teamtalk.domain.model.TaskMember
import com.ekotak.teamtalk.domain.repository.LeaveRepository
import com.ekotak.teamtalk.domain.repository.MemberRepository
import com.ekotak.teamtalk.presentation.crm.crmErrorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * Moduł Urlop — mobilny odpowiednik zakładki „Urlop" z `HrView.tsx`.
 *
 * Ekran żyje z jednej migawki Room, więc kalendarz, liczniki i lista wniosków
 * zmieniają się razem. Zaznaczanie zakresu jest tu DWOMA DOTKNIĘCIAMI, a nie
 * przeciąganiem jak w panelu: przeciąganie po siatce gryzie się z przewijaniem
 * ekranu (ta sama decyzja co przy siatce Kalendarza).
 */
@HiltViewModel
class LeaveViewModel @Inject constructor(
    private val repository: LeaveRepository,
    private val memberRepository: MemberRepository,
) : ViewModel() {

    /** Zaznaczenie na kalendarzu: pierwszy dotknięty dzień i — po drugim — koniec. */
    data class Selection(val start: LocalDate, val end: LocalDate?) {
        val from: LocalDate get() = if (end == null || !end.isBefore(start)) start else end
        val to: LocalDate get() = if (end == null) start else if (end.isBefore(start)) start else end
        val isComplete: Boolean get() = end != null
        fun covers(day: LocalDate): Boolean = !day.isBefore(from) && !day.isAfter(to)
    }

    /**
     * Arkusz wniosku — jeden na składanie i zmianę, jak modal w panelu.
     * [conflictWith] niepuste znaczy, że arkusz otworzył się po odmowie 409:
     * zmieniamy wniosek, z którym okres się nałożył, zamiast zakładać drugi.
     */
    data class LeaveForm(
        val id: String? = null,
        val type: LeaveType = LeaveType.WYPOCZYNKOWY,
        val start: LocalDate = LocalDate.now(),
        val end: LocalDate = LocalDate.now(),
        val reason: String = "",
        val saving: Boolean = false,
        val conflictWith: LeaveRequest? = null,
    ) {
        val isNew: Boolean get() = id == null
        val workingDays: Int get() = countWorkingDays(start, end)
    }

    data class UiState(
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val error: String? = null,
        val message: String? = null,
        val scale: LeaveScale = LeaveScale.MONTH,
        /** Dzień, wokół którego liczymy widoczny zakres. */
        val anchor: LocalDate = LocalDate.now(),
        val balance: LeaveBalance? = null,
        val myRequests: List<LeaveRequest> = emptyList(),
        val absences: List<LeaveAbsence> = emptyList(),
        val members: List<TaskMember> = emptyList(),
        val selection: Selection? = null,
        val form: LeaveForm? = null,
        val syncedAt: Long? = null,
    ) {
        /** Tryb z kartoteki; przed pierwszym pobraniem zakładamy wymiar, jak API. */
        val mode: LeaveMode get() = balance?.mode ?: LeaveMode.QUOTA

        /** Rodzaje do wyboru — poza umową o pracę zostaje sam bezpłatny. */
        val availableTypes: List<LeaveType> get() = LeaveType.availableIn(mode)

        /** Aktywne wnioski (bez odrzuconych i anulowanych) — to rysuje kalendarz. */
        val activeRequests: List<LeaveRequest> get() = myRequests.filter { it.isActive }

        /** Imię zwierzchnika do zdania „Decyzję podejmie …" przed wysłaniem. */
        val approverName: String?
            get() {
                val id = balance?.managerId ?: return "Zarząd"
                return members.firstOrNull { it.id == id }?.displayName
            }

        /** Ile dni wymiaru zostanie po zatwierdzeniu tego, co w arkuszu. */
        fun remainingAfter(form: LeaveForm): Int? {
            val b = balance ?: return null
            if (b.mode != LeaveMode.QUOTA) return null
            if (form.type != LeaveType.WYPOCZYNKOWY && form.type != LeaveType.NA_ZADANIE) return null
            // Przy zmianie istniejącego wniosku jego dotychczasowe dni wracają do puli.
            val previous = form.id?.let { id -> myRequests.firstOrNull { it.id == id } }
                ?.takeIf { it.isActive && (it.type == LeaveType.WYPOCZYNKOWY || it.type == LeaveType.NA_ZADANIE) }
                ?.workingDays ?: 0
            return b.remaining + previous - form.workingDays
        }
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.observe().collect { snapshot ->
                _uiState.update {
                    it.copy(
                        balance = snapshot.balance,
                        myRequests = snapshot.myRequests,
                        absences = snapshot.absences,
                        syncedAt = snapshot.syncedAt,
                        isLoading = false,
                    )
                }
            }
        }
        viewModelScope.launch {
            memberRepository.observe().collect { members ->
                _uiState.update { it.copy(members = members) }
            }
        }
        refresh(initial = true)
        viewModelScope.launch { runCatching { memberRepository.refresh() } }
    }

    fun refresh(initial: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = !initial, error = null) }
            runCatching { repository.refresh() }
                .onFailure { e ->
                    // Cache zostaje na ekranie — pasek mówi tylko, że dane mogą być starsze.
                    _uiState.update {
                        it.copy(error = crmErrorMessage(e, "Nie udało się pobrać danych urlopowych"))
                    }
                }
            _uiState.update { it.copy(isRefreshing = false, isLoading = false) }
        }
    }

    // ── Kalendarz ─────────────────────────────────────────────────────────────

    fun setScale(scale: LeaveScale) = _uiState.update { it.copy(scale = scale) }

    /** Krok o jeden zakres w tył (`-1`) albo w przód (`1`) — zależnie od skali. */
    fun step(direction: Int) = _uiState.update {
        val anchor = when (it.scale) {
            LeaveScale.WEEK -> it.anchor.plusWeeks(direction.toLong())
            LeaveScale.MONTH -> it.anchor.plusMonths(direction.toLong())
            LeaveScale.QUARTER -> it.anchor.plusMonths(3L * direction)
            LeaveScale.YEAR -> it.anchor.plusYears(direction.toLong())
        }
        it.copy(anchor = anchor)
    }

    fun goToday() = _uiState.update { it.copy(anchor = LocalDate.now()) }

    /** Wejście z widoku roku: stuknięty miesiąc otwiera się w skali miesiąca. */
    fun goToMonth(day: LocalDate) = _uiState.update { it.copy(anchor = day) }

    /**
     * Dotknięcie dnia. Pierwsze ustawia początek, drugie koniec, trzecie
     * zaczyna zaznaczanie od nowa — bez przeciągania palcem po siatce.
     */
    fun selectDay(day: LocalDate) = _uiState.update {
        val current = it.selection
        val next = when {
            current == null || current.isComplete -> Selection(day, null)
            else -> Selection(current.start, day)
        }
        it.copy(selection = next)
    }

    fun clearSelection() = _uiState.update { it.copy(selection = null) }

    // ── Arkusz wniosku ────────────────────────────────────────────────────────

    /** Nowy wniosek — daty z zaznaczenia, a bez niego od dzisiaj. */
    fun openNew() = _uiState.update {
        val from = it.selection?.from ?: LocalDate.now()
        val to = it.selection?.to ?: from
        it.copy(
            form = LeaveForm(
                type = it.availableTypes.first(),
                start = from,
                end = to,
            ),
        )
    }

    fun openEdit(id: String) = _uiState.update { state ->
        val request = state.myRequests.firstOrNull { it.id == id } ?: return@update state
        state.copy(
            form = LeaveForm(
                id = request.id,
                type = request.type,
                start = request.start,
                end = request.end,
                reason = request.reason.orEmpty(),
            ),
        )
    }

    fun editForm(change: (LeaveForm) -> LeaveForm) = _uiState.update { state ->
        state.form?.let { state.copy(form = change(it)) } ?: state
    }

    fun closeForm() = _uiState.update { it.copy(form = null) }

    fun save() {
        val state = _uiState.value
        val form = state.form ?: return
        if (form.workingDays == 0) {
            _uiState.update { it.copy(message = "Wybrany zakres nie obejmuje dni roboczych.") }
            return
        }
        val draft = LeaveDraft(
            type = form.type,
            start = form.start,
            end = form.end,
            reason = form.reason.takeIf { it.isNotBlank() },
        )
        viewModelScope.launch {
            _uiState.update { it.copy(form = form.copy(saving = true)) }
            val result = runCatching {
                if (form.isNew) repository.submit(draft) else repository.update(form.id!!, draft)
            }
            result
                .onSuccess { saved ->
                    _uiState.update {
                        it.copy(
                            form = null,
                            selection = null,
                            message = when {
                                saved.pendingSync ->
                                    "Brak zasięgu — wniosek poleci po powrocie sieci."
                                form.isNew -> "Złożono wniosek urlopowy."
                                else -> "Zmieniono wniosek — wraca do akceptacji."
                            },
                        )
                    }
                }
                .onFailure { e -> handleSaveFailure(form, e) }
        }
    }

    /**
     * Odmowa serwera. `409` nie jest tu awarią, tylko informacją, że ten okres
     * już zajęliśmy — arkusz przestawia się wtedy na ZMIANĘ tamtego wniosku,
     * dokładnie jak modal w panelu.
     */
    private fun handleSaveFailure(form: LeaveForm, e: Throwable) {
        when (e) {
            is LeaveOverlapException -> {
                val clash = _uiState.value.myRequests.firstOrNull { it.id in e.conflictIds }
                    ?: _uiState.value.activeRequests.firstOrNull { existing ->
                        existing.id != form.id &&
                            !existing.start.isAfter(form.end) &&
                            !existing.end.isBefore(form.start)
                    }
                if (clash != null) {
                    _uiState.update {
                        it.copy(
                            form = LeaveForm(
                                id = clash.id,
                                type = clash.type,
                                start = form.start,
                                end = form.end,
                                reason = clash.reason.orEmpty(),
                                conflictWith = clash,
                            ),
                        )
                    }
                } else {
                    _uiState.update { it.copy(form = form.copy(saving = false), message = e.message) }
                }
            }
            is LeaveTypeNotAllowedException -> _uiState.update {
                it.copy(
                    form = form.copy(saving = false, type = LeaveType.BEZPLATNY),
                    message = e.message,
                )
            }
            else -> _uiState.update {
                it.copy(
                    form = form.copy(saving = false),
                    message = crmErrorMessage(e, "Nie udało się zapisać wniosku"),
                )
            }
        }
    }

    fun cancelRequest(id: String) {
        viewModelScope.launch {
            runCatching { repository.cancel(id) }
                .onSuccess { cancelled ->
                    _uiState.update {
                        it.copy(
                            form = null,
                            message = if (cancelled.pendingSync) {
                                "Brak zasięgu — anulowanie poleci po powrocie sieci."
                            } else {
                                "Anulowano wniosek."
                            },
                        )
                    }
                }
                .onFailure { e ->
                    _uiState.update {
                        it.copy(message = crmErrorMessage(e, "Nie udało się anulować wniosku"))
                    }
                }
        }
    }

    fun clearMessage() = _uiState.update { it.copy(message = null) }
}
