package com.ekotak.teamtalk.presentation.goals

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.domain.model.CompanyGoals
import com.ekotak.teamtalk.domain.model.Goal
import com.ekotak.teamtalk.domain.model.GoalCatalog
import com.ekotak.teamtalk.domain.model.GoalDraft
import com.ekotak.teamtalk.domain.model.GoalScope
import com.ekotak.teamtalk.domain.model.GoalTrend
import com.ekotak.teamtalk.domain.model.PersonalGoals
import com.ekotak.teamtalk.domain.model.TeamGoals
import com.ekotak.teamtalk.domain.repository.GoalRepository
import com.ekotak.teamtalk.presentation.crm.crmErrorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZoneOffset
import java.time.ZonedDateTime
import javax.inject.Inject

/** Zakładki modułu — te same trzy, co w panelu. */
enum class GoalsTab { PERSONAL, TEAM, COMPANY }

/**
 * Moduł Cele na telefonie — pełne 1:1 z `/app/goals` panelu (decyzja
 * 2026-09-23), z pełnym offline.
 *
 * Ekran czyta migawkę z Room, więc otwiera się bez zasięgu, a sieć tylko
 * dolewa świeże liczby. Żadnej arytmetyki tu nie ma: realizację, tempo
 * i status liczy serwer — inaczej „70%" znaczyłoby na telefonie co innego
 * niż w panelu.
 */
@HiltViewModel
class GoalsViewModel @Inject constructor(
    private val repository: GoalRepository,
) : ViewModel() {

    data class UiState(
        val tab: GoalsTab = GoalsTab.PERSONAL,
        val period: String = currentQuarter(),
        val team: String = "biuro",
        /** Czyje cele osobiste oglądamy; `null` = własne. */
        val userId: String? = null,
        val personal: PersonalGoals? = null,
        val teamGoals: TeamGoals? = null,
        val company: CompanyGoals? = null,
        val trend: GoalTrend? = null,
        val catalog: GoalCatalog? = null,
        val isRefreshing: Boolean = false,
        /** Kiedy ostatnio udało się pobrać tę zakładkę (`null` = nigdy). */
        val syncedAt: Long? = null,
        val pendingCount: Int = 0,
        val error: String? = null,
        val message: String? = null,
        val editorOpen: Boolean = false,
    ) {
        /** Cele bieżącej zakładki — jedno miejsce dla listy na ekranie. */
        val items: List<Goal>
            get() = when (tab) {
                GoalsTab.PERSONAL -> personal?.items.orEmpty()
                GoalsTab.TEAM -> teamGoals?.items.orEmpty()
                GoalsTab.COMPANY -> company?.items.orEmpty()
            }

        /** Czy w tej zakładce wolno zakładać i zmieniać cele. */
        val canManage: Boolean
            get() = when (tab) {
                GoalsTab.PERSONAL -> personal?.canManage == true
                GoalsTab.TEAM -> teamGoals?.canManage == true
                GoalsTab.COMPANY -> company?.canManage == true
            }

        val scope: GoalScope
            get() = when (tab) {
                GoalsTab.PERSONAL -> GoalScope.PERSONAL
                GoalsTab.TEAM -> GoalScope.TEAM
                GoalsTab.COMPANY -> GoalScope.COMPANY
            }
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    /** Subskrypcje migawki bieżącej zakładki — przy zmianie zakładki wymieniane. */
    private var viewJob: Job? = null
    private var trendJob: Job? = null

    init {
        observeCatalog()
        observePending()
        bind()
        refresh()
    }

    // ── Sterowanie widokiem ───────────────────────────────────────────────────

    fun setTab(tab: GoalsTab) {
        if (_state.value.tab == tab) return
        _state.update { it.copy(tab = tab, trend = null) }
        bind()
        refresh()
    }

    fun setPeriod(period: String) {
        if (_state.value.period == period) return
        _state.update { it.copy(period = period, trend = null) }
        bind()
        refresh()
    }

    fun setTeam(team: String) {
        if (_state.value.team == team) return
        _state.update { it.copy(team = team, trend = null) }
        bind()
        refresh()
    }

    /** Zwierzchnik i zarząd przełączają się na cele podwładnego. */
    fun setPerson(userId: String?) {
        if (_state.value.userId == userId) return
        _state.update { it.copy(userId = userId, trend = null) }
        bind()
        refresh()
    }

    fun openEditor() = _state.update { it.copy(editorOpen = true) }

    fun closeEditor() = _state.update { it.copy(editorOpen = false) }

    fun clearMessage() = _state.update { it.copy(message = null) }

    fun clearError() = _state.update { it.copy(error = null) }

    // ── Strumienie z Room ─────────────────────────────────────────────────────

    private fun bind() {
        val s = _state.value
        viewJob?.cancel()
        viewJob = viewModelScope.launch {
            when (s.tab) {
                GoalsTab.PERSONAL ->
                    repository.observePersonal(s.period, s.userId).collect { view ->
                        _state.update { it.copy(personal = view) }
                        bindTrend(view?.items?.firstOrNull())
                    }
                GoalsTab.TEAM ->
                    repository.observeTeam(s.period, s.team).collect { view ->
                        _state.update { it.copy(teamGoals = view) }
                        bindTrend(view?.items?.firstOrNull())
                    }
                GoalsTab.COMPANY ->
                    repository.observeCompany(s.period).collect { view ->
                        _state.update { it.copy(company = view) }
                        bindTrend(view?.items?.firstOrNull())
                    }
            }
        }
        viewModelScope.launch {
            repository.observeSyncedAt(currentKey()).collect { at ->
                _state.update { it.copy(syncedAt = at) }
            }
        }
    }

    /**
     * Wykres rysujemy dla celu WIODĄCEGO (pierwszego) zakładki — tak samo jak
     * panel. Osiem punktów na jeden wykres wystarcza na trend, a nie zamawia
     * sześciu zapytań przy każdym wejściu w moduł.
     */
    private fun bindTrend(lead: Goal?) {
        trendJob?.cancel()
        if (lead == null || lead.isLocal) {
            _state.update { it.copy(trend = null) }
            return
        }
        trendJob = viewModelScope.launch {
            repository.observeTrend(lead.id).collect { trend ->
                _state.update { it.copy(trend = trend) }
            }
        }
        viewModelScope.launch { runCatching { repository.refreshTrend(lead.id) } }
    }

    private fun observeCatalog() = viewModelScope.launch {
        repository.observeCatalog().collect { catalog ->
            _state.update { it.copy(catalog = catalog) }
        }
    }

    private fun observePending() = viewModelScope.launch {
        repository.observePendingIds().collect { ids ->
            _state.update { it.copy(pendingCount = ids.size) }
        }
    }

    private fun currentKey(): String {
        val s = _state.value
        return when (s.tab) {
            GoalsTab.PERSONAL -> "personal:${s.period}:${s.userId ?: "me"}"
            GoalsTab.TEAM -> "team:${s.period}:${s.team}"
            GoalsTab.COMPANY -> "company:${s.period}"
        }
    }

    // ── Sieć ──────────────────────────────────────────────────────────────────

    fun refresh() {
        val s = _state.value
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true, error = null) }
            val result = runCatching {
                when (s.tab) {
                    GoalsTab.PERSONAL -> repository.refreshPersonal(s.period, s.userId)
                    GoalsTab.TEAM -> repository.refreshTeam(s.period, s.team)
                    GoalsTab.COMPANY -> repository.refreshCompany(s.period)
                }
                if (s.catalog == null) runCatching { repository.refreshCatalog() }
            }
            _state.update { st ->
                st.copy(
                    isRefreshing = false,
                    // Brak sieci nie jest awarią: ekran pokazuje migawkę z cache
                    // i podpis, z kiedy jest. Komunikat tylko wtedy, gdy nie ma
                    // czego pokazać.
                    error = result.exceptionOrNull()
                        ?.takeIf { st.items.isEmpty() && st.syncedAt == null }
                        ?.let { crmErrorMessage(it, "Nie udało się pobrać celów") },
                )
            }
        }
    }

    // ── Zapis ─────────────────────────────────────────────────────────────────

    fun saveGoal(draft: GoalDraft) = viewModelScope.launch {
        val result = runCatching { repository.create(draft) }
        _state.update {
            if (result.isSuccess) {
                it.copy(
                    editorOpen = false,
                    message = if (result.getOrNull()?.pending == true) {
                        "Cel zapisany — poleci po powrocie zasięgu."
                    } else {
                        "Cel zapisany."
                    },
                )
            } else {
                it.copy(error = goalError(result.exceptionOrNull(), "Nie udało się zapisać celu"))
            }
        }
        refresh()
    }

    fun deleteGoal(goalId: String) = viewModelScope.launch {
        val result = runCatching { repository.delete(goalId) }
        _state.update {
            if (result.isSuccess) it.copy(message = "Cel usunięty.")
            else it.copy(error = goalError(result.exceptionOrNull(), "Nie udało się usunąć celu"))
        }
        refresh()
    }

    fun closeGoal(goalId: String) = viewModelScope.launch {
        val result = runCatching { repository.close(goalId) }
        _state.update {
            if (result.isSuccess) it.copy(message = "Okres zamknięty — wynik zapisany w historii.")
            else it.copy(error = goalError(result.exceptionOrNull(), "Nie udało się zamknąć okresu"))
        }
        refresh()
    }

    /** Wpis ręczny do celu `manual` — stan na dziś, nie przyrost. */
    fun checkin(goalId: String, value: Double, note: String) = viewModelScope.launch {
        val result = runCatching { repository.checkin(goalId, value, note) }
        _state.update {
            if (result.isSuccess) it.copy(message = "Wpis zapisany.")
            else it.copy(error = goalError(result.exceptionOrNull(), "Nie udało się zapisać wpisu"))
        }
        refresh()
    }

    /**
     * Komunikat błędu zapisu. `null` znaczy „udało się", więc zwracamy `null`
     * zamiast zmyślać treść — a `runCatching` z natury oddaje wyjątek nullowalny.
     */
    private fun goalError(e: Throwable?, fallback: String): String? =
        e?.let { crmErrorMessage(it, fallback) }

    companion object {
        /**
         * Bieżący kwartał — domyślny widok modułu, jak w panelu. Liczymy w UTC,
         * bo okresy celów mają w bazie granice UTC; różnica stref przy zmianie
         * kwartału o północy nie ma tu żadnego znaczenia praktycznego.
         */
        fun currentQuarter(): String {
            val now = ZonedDateTime.now(ZoneOffset.UTC)
            return "${now.year}-Q${(now.monthValue - 1) / 3 + 1}"
        }

        /** Okresy w przełączniku: bieżący kwartał i miesiąc, rok, trzy poprzednie kwartały. */
        fun periodOptions(): List<String> {
            val now = ZonedDateTime.now(ZoneOffset.UTC)
            val quarter = (now.monthValue - 1) / 3 + 1
            val out = mutableListOf(
                "${now.year}-Q$quarter",
                "${now.year}-${now.monthValue.toString().padStart(2, '0')}",
                "${now.year}",
            )
            for (back in 1..3) {
                val q = quarter - back
                out += if (q > 0) "${now.year}-Q$q" else "${now.year - 1}-Q${q + 4}"
            }
            return out
        }
    }
}
