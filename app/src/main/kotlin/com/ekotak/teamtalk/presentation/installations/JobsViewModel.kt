package com.ekotak.teamtalk.presentation.installations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.domain.model.MontazJobRow
import com.ekotak.teamtalk.domain.model.MontazStatus
import com.ekotak.teamtalk.domain.repository.MontazJobRepository
import com.ekotak.teamtalk.presentation.crm.parseIsoMillis
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

/**
 * MOJE MONTAŻE — lista wyjazdów montażysty.
 *
 * Lista jest ułożona po DNIACH, a nie płasko: montażysta pyta „co mam dziś",
 * nie „co jest w systemie". Zakończone wyjazdy siedzą w osobnej zakładce, bo
 * po podpisie protokołu nie ma już po co do nich wracać — poza sprawdzeniem
 * PDF-u.
 */
@HiltViewModel
class JobsViewModel @Inject constructor(
    private val repository: MontazJobRepository,
) : ViewModel() {

    /** Co pokazujemy: moje wyjazdy, wyjazdy ekipy, archiwum. */
    enum class Scope(val label: String) { MINE("Moje"), CREW("Ekipa"), DONE("Zakończone") }

    /** Sekcja listy — jeden dzień albo „Bez terminu". */
    data class DaySection(val label: String, val jobs: List<MontazJobRow>)

    data class UiState(
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val scope: Scope = Scope.MINE,
        val sections: List<DaySection> = emptyList(),
        val todayCount: Int = 0,
        val weekCount: Int = 0,
        val fromCache: Boolean = false,
        val error: String? = null,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var all: List<MontazJobRow> = emptyList()

    init {
        load(initial = true)
    }

    fun setScope(scope: Scope) {
        _uiState.update { it.copy(scope = scope) }
        // „Ekipa" to inne pytanie do serwera, „Zakończone" — ten sam zbiór
        // przefiltrowany lokalnie. Drugie nie kosztuje okrążenia po sieci.
        if (scope == Scope.CREW || _uiState.value.sections.isEmpty()) load() else render()
    }

    fun refresh() = load(refreshing = true)

    private fun load(initial: Boolean = false, refreshing: Boolean = false) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = initial, isRefreshing = refreshing) }
            val snapshot = runCatching {
                repository.getJobs(crew = _uiState.value.scope == Scope.CREW)
            }.getOrNull()
            all = snapshot?.jobs ?: all
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    fromCache = snapshot?.fromCache == true,
                    error = snapshot?.error,
                )
            }
            render()
        }
    }

    /** Podział na sekcje i liczniki — wszystko lokalnie, bez ruchu po sieci. */
    private fun render() {
        val scope = _uiState.value.scope
        val visible = all.filter { row ->
            if (scope == Scope.DONE) row.status == MontazStatus.DONE else row.status != MontazStatus.DONE
        }
        val sections = visible
            .groupBy { dayLabel(it.scheduledAt) }
            .map { (label, jobs) -> DaySection(label, jobs) }
        _uiState.update {
            it.copy(
                sections = sections,
                todayCount = all.count { row -> isToday(row.scheduledAt) && row.status != MontazStatus.DONE },
                weekCount = all.count { row -> withinDays(row.scheduledAt, 7) && row.status != MontazStatus.DONE },
            )
        }
    }
}

// ── Kalendarz w kieszeni ─────────────────────────────────────────────────────
// Świadomie bez `java.time`: reszta modułów liczy dni na `Calendar`
// (`parseIsoMillis` + strefa telefonu), a mieszanie dwóch sposobów kończy się
// montażem „jutro", który pokazuje się dziś o 23:30.

internal fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

internal fun isToday(iso: String?): Boolean {
    val millis = parseIsoMillis(iso) ?: return false
    return startOfDay(millis) == startOfDay(System.currentTimeMillis())
}

internal fun withinDays(iso: String?, days: Int): Boolean {
    val millis = parseIsoMillis(iso) ?: return false
    val from = startOfDay(System.currentTimeMillis())
    return millis >= from && millis < from + days * 24L * 60 * 60 * 1000
}

private val DOW = listOf(
    "Niedziela", "Poniedziałek", "Wtorek", "Środa", "Czwartek", "Piątek", "Sobota",
)
private val MONTHS = listOf(
    "stycznia", "lutego", "marca", "kwietnia", "maja", "czerwca",
    "lipca", "sierpnia", "września", "października", "listopada", "grudnia",
)

/** Nagłówek sekcji: „Dziś · wtorek 23 września" albo „Bez terminu". */
internal fun dayLabel(iso: String?): String {
    val millis = parseIsoMillis(iso) ?: return "Bez terminu"
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    val day = "${DOW[cal.get(Calendar.DAY_OF_WEEK) - 1]} ${cal.get(Calendar.DAY_OF_MONTH)} " +
        MONTHS[cal.get(Calendar.MONTH)]
    val today = startOfDay(System.currentTimeMillis())
    return when (startOfDay(millis)) {
        today -> "Dziś · $day"
        today + 24L * 60 * 60 * 1000 -> "Jutro · $day"
        else -> day
    }
}

/** Godzina wyjazdu do lewej kolumny wiersza („7:30"); `null` = bez terminu. */
internal fun hourLabel(iso: String?): String? {
    val millis = parseIsoMillis(iso) ?: return null
    val cal = Calendar.getInstance().apply { timeInMillis = millis }
    return "%d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
}
