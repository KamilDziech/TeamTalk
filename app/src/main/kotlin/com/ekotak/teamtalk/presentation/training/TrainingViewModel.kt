package com.ekotak.teamtalk.presentation.training

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.domain.model.AssignmentStatus
import com.ekotak.teamtalk.domain.model.MySkillEntry
import com.ekotak.teamtalk.domain.model.TrainingAssignment
import com.ekotak.teamtalk.domain.model.mineEntries
import com.ekotak.teamtalk.domain.repository.TrainingRepository
import com.ekotak.teamtalk.presentation.crm.crmErrorMessage
import com.ekotak.teamtalk.presentation.crm.parseIsoMillis
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/** Zakładki ekranu — odpowiednik dwóch kart w zakładce HR → Szkolenia. */
enum class TrainingTab { LESSONS, LEVELS }

/**
 * Widok pracownika: co mam do zrobienia i po co. Moduł jest **wyłącznie
 * online** (ustalenie 2026-09-06), więc zamiast Room jest tu jedno pobranie
 * na wejście i „pociągnij, by odświeżyć"; brak sieci to komunikat, nie pusty
 * ekran udający, że nic nie przypisano.
 */
@HiltViewModel
class TrainingViewModel @Inject constructor(
    private val repository: TrainingRepository,
) : ViewModel() {

    /**
     * Wiersz listy razem z tym, co panel liczy w locie: zaliczone szkolenie po
     * dacie ważności przestaje być „Zaliczone" i robi się „Do odnowienia",
     * a nieodrobione po terminie — „Po terminie".
     */
    data class Row(
        val assignment: TrainingAssignment,
        val expired: Boolean,
        val overdue: Boolean,
    ) {
        val statusLabel: String = when {
            expired -> "Do odnowienia"
            overdue -> "Po terminie"
            else -> assignment.status.label
        }
    }

    data class UiState(
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val tab: TrainingTab = TrainingTab.LESSONS,
        val rows: List<Row> = emptyList(),
        val levels: List<MySkillEntry> = emptyList(),
        /** Nie udało się pobrać — treść komunikatu dla paska nad listą. */
        val error: String? = null,
        val message: String? = null,
    ) {
        val todoCount: Int get() = rows.count { it.assignment.status != AssignmentStatus.PASSED || it.expired }
        val overdueCount: Int get() = rows.count { it.overdue || it.expired }
        val passedCount: Int get() = rows.count { it.assignment.status == AssignmentStatus.PASSED && !it.expired }
        val gapCount: Int get() = levels.count { it.missing.isNotEmpty() }
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        load(initial = true)
    }

    fun refresh() = load(initial = false)

    fun setTab(tab: TrainingTab) = _state.update { it.copy(tab = tab) }

    fun clearMessage() = _state.update { it.copy(message = null) }

    /**
     * Certyfikat: pobranie do pamięci podręcznej i oddanie systemowi. Plik
     * nazywamy po identyfikatorze lekcji, żeby drugie kliknięcie nie ruszało
     * sieci — treść certyfikatu dla zaliczonego szkolenia się nie zmienia.
     */
    fun openCertificate(lessonId: String, cacheDir: File, onReady: (File) -> Unit) {
        viewModelScope.launch {
            try {
                val dir = File(cacheDir, "certificates").apply { mkdirs() }
                val target = File(dir, "$lessonId.pdf")
                if (!target.exists() || target.length() == 0L) {
                    repository.downloadCertificate(lessonId, target)
                }
                onReady(target)
            } catch (e: Exception) {
                _state.update {
                    it.copy(message = crmErrorMessage(e, "Nie udało się pobrać certyfikatu"))
                }
            }
        }
    }

    private fun load(initial: Boolean) {
        viewModelScope.launch {
            _state.update {
                it.copy(isLoading = initial && it.rows.isEmpty(), isRefreshing = !initial, error = null)
            }
            // Poziomy są dodatkiem do listy: gdy ta trasa padnie, szkolenia i tak
            // mają się pokazać — dlatego dwa osobne `runCatching`, a nie jedno.
            val trainings = runCatching { repository.listMyTrainings() }
            val skills = runCatching { repository.getMySkills() }

            val now = System.currentTimeMillis()
            _state.update { prev ->
                prev.copy(
                    isLoading = false,
                    isRefreshing = false,
                    rows = trainings.getOrNull()?.let { sortRows(it, now) } ?: prev.rows,
                    levels = skills.getOrNull()?.let(::mineEntries) ?: prev.levels,
                    error = trainings.exceptionOrNull()?.let {
                        crmErrorMessage(it, "Nie udało się pobrać szkoleń")
                    } ?: skills.exceptionOrNull()?.let {
                        crmErrorMessage(it, "Nie udało się pobrać poziomów")
                    },
                )
            }
        }
    }

    /** Kolejność jak w panelu: do poprawy → do zrobienia → w toku → zaliczone. */
    private fun sortRows(items: List<TrainingAssignment>, now: Long): List<Row> {
        val order = mapOf(
            AssignmentStatus.FAILED to 0,
            AssignmentStatus.ASSIGNED to 1,
            AssignmentStatus.IN_PROGRESS to 2,
            AssignmentStatus.PASSED to 3,
        )
        return items
            .map { assignment ->
                val expired = assignment.status == AssignmentStatus.PASSED &&
                    (parseIsoMillis(assignment.expiresAt)?.let { it < now } ?: false)
                val overdue = assignment.status != AssignmentStatus.PASSED &&
                    (parseIsoMillis(assignment.dueDate)?.let { it < now } ?: false)
                Row(assignment = assignment, expired = expired, overdue = overdue)
            }
            // Wygasłe zaliczenie wraca na górę razem z zaległościami — to jest
            // robota do zrobienia, a nie pamiątka po zdanym teście.
            .sortedWith(
                compareBy<Row> { if (it.expired) 0 else 1 }
                    .thenByDescending { it.overdue }
                    .thenBy { order[it.assignment.status] ?: 9 }
                    .thenBy { parseIsoMillis(it.assignment.dueDate) ?: Long.MAX_VALUE },
            )
    }
}

