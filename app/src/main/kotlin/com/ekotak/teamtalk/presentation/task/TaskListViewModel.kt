package com.ekotak.teamtalk.presentation.task

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.data.local.preferences.SessionPreferences
import com.ekotak.teamtalk.domain.model.NO_SECTION_LABEL
import com.ekotak.teamtalk.domain.model.Task
import com.ekotak.teamtalk.domain.model.TaskMember
import com.ekotak.teamtalk.domain.model.TaskPriority
import com.ekotak.teamtalk.domain.model.TaskSection
import com.ekotak.teamtalk.domain.model.TaskSource
import com.ekotak.teamtalk.domain.model.TaskStatus
import com.ekotak.teamtalk.domain.usecase.task.GetTaskMembersUseCase
import com.ekotak.teamtalk.domain.usecase.task.ObserveTasksUseCase
import com.ekotak.teamtalk.domain.usecase.task.RefreshTasksUseCase
import com.ekotak.teamtalk.domain.usecase.task.SetTaskDoneUseCase
import com.ekotak.teamtalk.domain.usecase.task.ToggleTaskPriorityUseCase
import com.ekotak.teamtalk.presentation.crm.crmErrorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** W jakiej roli patrzę na zadania — czyj filtr osoby stosujemy. */
enum class RoleScope(val label: String) {
    /** Zadania przypisane do wybranej osoby — co ma zrobić. */
    ASSIGNEE("Do wykonania"),

    /** Zadania utworzone przez wybraną osobę — co komuś zleciła. */
    CREATOR("Zlecone"),

    /** Wykonawca LUB zlecający. */
    ANY("Wszystkie"),
}

/** Kogo dotyczy filtr osoby. */
sealed interface PersonScope {
    data object Mine : PersonScope
    data object All : PersonScope
    data object Unassigned : PersonScope
    data class Group(val group: MemberGroup) : PersonScope
    data class Person(val id: String) : PersonScope
}

enum class DueScope(val label: String) { ALL("Wszystkie"), TODAY("Dziś"), OVERDUE("Zaległe") }

enum class SourceScope(val label: String) { ALL("Wszystkie"), CLIENT("Klient"), PROJECT("Projekt") }

enum class TaskSort(val label: String) {
    DUE("Termin"),
    PRIORITY("Priorytet"),
    NEWEST("Najnowsze"),
    NAME("Nazwa A→Z"),
}

/**
 * Lista zadań zespołu — mobilny odpowiednik tablicy „Zadania" z board360.
 *
 * Zadania idą ze strumienia z cache Room (`observeTasks`), więc lista pokazuje
 * się bez zasięgu; filtrowanie, sortowanie i grupowanie robimy lokalnie na
 * pobranej liście — przełączanie chipów bez okrążenia po sieci jest wyraźnie
 * szybsze, a zadania jednej organizacji to rząd setek rekordów.
 *
 * Zapis (odhaczenie, priorytet) wymaga sieci: dopóki nie ma kolejki offline
 * (E3), lepiej pokazać awarię niż udawać, że zmiana weszła.
 */
@HiltViewModel
class TaskListViewModel @Inject constructor(
    private val observeTasks: ObserveTasksUseCase,
    private val refreshTasks: RefreshTasksUseCase,
    private val getTaskMembers: GetTaskMembersUseCase,
    private val setTaskDone: SetTaskDoneUseCase,
    private val toggleTaskPriority: ToggleTaskPriorityUseCase,
    private val sessionPreferences: SessionPreferences,
) : ViewModel() {

    data class Section(val section: TaskSection?, val label: String, val items: List<Task>)

    data class UiState(
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val error: String? = null,
        /** Komunikat po akcji (snackbar) — sukces albo powód niepowodzenia. */
        val message: String? = null,
        val searchQuery: String = "",
        val role: RoleScope = RoleScope.ASSIGNEE,
        val person: PersonScope = PersonScope.Mine,
        val statuses: Set<TaskStatus> = OPEN_STATUSES,
        val priority: TaskPriority? = null,
        val due: DueScope = DueScope.ALL,
        val source: SourceScope = SourceScope.ALL,
        val sort: TaskSort = TaskSort.DUE,
        /** Grupowanie sekcjami (nagłówki) albo płaska lista — przełącznik w arkuszu. */
        val groupBySection: Boolean = true,
        val sections: List<Section> = emptyList(),
        val members: List<TaskMember> = emptyList(),
        val membersById: Map<String, TaskMember> = emptyMap(),
        /** Zadania z trwającym zapisem — wiersz pokazuje kręciołek zamiast kółka. */
        val pendingIds: Set<String> = emptySet(),
        /** Ile zadań widać po filtrach i ile jest wszystkich. */
        val visibleCount: Int = 0,
        val totalCount: Int = 0,
    ) {
        /** Liczba filtrów odbiegających od domyślnych — kropka przy ikonie filtra. */
        val activeFilterCount: Int
            get() = listOf(
                person != PersonScope.Mine,
                statuses != OPEN_STATUSES,
                priority != null,
                due != DueScope.ALL,
                source != SourceScope.ALL,
                sort != TaskSort.DUE,
            ).count { it }
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var tasks: List<Task> = emptyList()
    private var currentUserId: String? = null

    init {
        observe()
        load(initial = true)
        loadMembers()
    }

    /**
     * Cache Room jest źródłem listy — strumień żyje przez cały czas życia
     * ekranu, a pobranie z sieci zleca [load] i [refresh]. Dzięki temu kręciołek
     * gaśnie, gdy pobranie faktycznie się skończyło, a nie gdy Room odda pustą
     * listę na starcie.
     */
    private fun observe() {
        viewModelScope.launch {
            currentUserId = sessionPreferences.session.first()?.userId
            observeTasks().collect { list ->
                tasks = list
                _uiState.update { it.copy(totalCount = list.size) }
                recompute()
            }
        }
    }

    private fun load(initial: Boolean) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = initial, isRefreshing = !initial, error = null)
            }
            try {
                refreshTasks()
                _uiState.update { it.copy(isLoading = false, isRefreshing = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = crmErrorMessage(e, "Nie udało się pobrać zadań"),
                    )
                }
            }
        }
    }

    /** Zespół potrzebny jest do filtra osoby i do podpisu „kto wykonuje". */
    private fun loadMembers() {
        viewModelScope.launch {
            try {
                val members = sortMembersForTasks(getTaskMembers())
                _uiState.update {
                    it.copy(members = members, membersById = members.associateBy { m -> m.id })
                }
                recompute()
            } catch (_: Exception) {
                // Brak listy osób nie blokuje zadań — wiersz pokaże wtedy e-mail.
            }
        }
    }

    fun refresh() = load(initial = false)

    // ── Filtry ────────────────────────────────────────────────────────────────
    fun onSearchQueryChange(query: String) = update { it.copy(searchQuery = query) }

    fun onRoleChange(role: RoleScope) = update { it.copy(role = role) }

    fun onPersonChange(person: PersonScope) = update { it.copy(person = person) }

    /** Ponowne kliknięcie w zaznaczony status zdejmuje go; ostatni zostaje. */
    fun onToggleStatus(status: TaskStatus) = update { state ->
        val next = if (status in state.statuses) state.statuses - status else state.statuses + status
        state.copy(statuses = next.ifEmpty { state.statuses })
    }

    fun onStatusesChange(statuses: Set<TaskStatus>) = update { it.copy(statuses = statuses) }

    fun onPriorityChange(priority: TaskPriority?) = update {
        it.copy(priority = if (it.priority == priority) null else priority)
    }

    fun onDueChange(due: DueScope) = update { it.copy(due = due) }

    fun onSourceChange(source: SourceScope) = update { it.copy(source = source) }

    fun onSortChange(sort: TaskSort) = update { it.copy(sort = sort) }

    fun onGroupBySectionChange(grouped: Boolean) = update { it.copy(groupBySection = grouped) }

    /** „Moje / Zaległe" z paska listy — skróty do najczęstszych zawężeń. */
    fun onToggleMine() = update {
        it.copy(person = if (it.person == PersonScope.Mine) PersonScope.All else PersonScope.Mine)
    }

    fun onToggleOverdue() = update {
        it.copy(due = if (it.due == DueScope.OVERDUE) DueScope.ALL else DueScope.OVERDUE)
    }

    fun onToggleOpenOnly() = update {
        it.copy(statuses = if (it.statuses == OPEN_STATUSES) ALL_STATUSES else OPEN_STATUSES)
    }

    fun clearFilters() = update {
        it.copy(
            person = PersonScope.Mine,
            statuses = OPEN_STATUSES,
            priority = null,
            due = DueScope.ALL,
            source = SourceScope.ALL,
            sort = TaskSort.DUE,
        )
    }

    // ── Akcje na wierszu ──────────────────────────────────────────────────────
    fun onToggleDone(task: Task) = runAction(task.id) {
        val done = task.status != TaskStatus.DONE
        setTaskDone(task.id, done)
        if (done) "Zadanie wykonane." else "Przywrócono zadanie."
    }

    fun onTogglePriority(task: Task) = runAction(task.id) {
        val high = task.priority != TaskPriority.HIGH
        toggleTaskPriority(task.id, high)
        if (high) "Oznaczono jako wysoki priorytet." else "Zdjęto priorytet."
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    fun clearError() = _uiState.update { it.copy(error = null) }

    /**
     * Zapis pojedynczego zadania. Wiersz dostaje znacznik „w trakcie", a wynik
     * wraca strumieniem z Rooma — nie ma więc ręcznego podmieniania listy.
     */
    private fun runAction(taskId: String, block: suspend () -> String) {
        viewModelScope.launch {
            _uiState.update { it.copy(pendingIds = it.pendingIds + taskId) }
            try {
                val message = block()
                _uiState.update { it.copy(message = message) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(message = crmErrorMessage(e, "Nie udało się zapisać zmiany"))
                }
            } finally {
                _uiState.update { it.copy(pendingIds = it.pendingIds - taskId) }
            }
        }
    }

    private fun update(block: (UiState) -> UiState) {
        _uiState.update(block)
        recompute()
    }

    // ── Filtrowanie, sortowanie, grupowanie ───────────────────────────────────
    private fun recompute() {
        val state = _uiState.value
        val now = System.currentTimeMillis()

        val filtered = tasks
            .filter { matchesPerson(it, state) }
            .filter { it.status in state.statuses }
            .filter { state.priority == null || it.priority == state.priority }
            .filter { matchesDue(it, state.due, now) }
            .filter { matchesSource(it, state.source) }
            .filter { matchesQuery(it, state.searchQuery, state.membersById) }

        val sorted = when (state.sort) {
            TaskSort.DUE -> filtered.sortedWith(
                compareBy({ it.dueAt == null }, { it.dueAt ?: "" }, { it.createdAt }),
            )
            TaskSort.PRIORITY -> filtered.sortedWith(
                compareByDescending<Task> { it.priority.ordinal }
                    .thenBy { it.dueAt == null }
                    .thenBy { it.dueAt ?: "" },
            )
            TaskSort.NEWEST -> filtered.sortedByDescending { it.createdAt }
            TaskSort.NAME -> filtered.sortedBy { it.title.lowercase() }
        }

        _uiState.update {
            it.copy(sections = groupTasks(sorted, it.groupBySection), visibleCount = sorted.size)
        }
    }

    private fun matchesPerson(task: Task, state: UiState): Boolean {
        val assignee = task.assigneeId
        val creator = task.createdBy

        fun matches(id: String?): Boolean = when (state.role) {
            RoleScope.ASSIGNEE -> assignee == id
            RoleScope.CREATOR -> creator == id
            RoleScope.ANY -> assignee == id || creator == id
        }

        return when (val person = state.person) {
            PersonScope.All -> true
            // „Nieprzypisane" zawsze dotyczy wykonawcy — zlecający jest zawsze znany.
            PersonScope.Unassigned -> assignee == null
            PersonScope.Mine -> currentUserId?.let(::matches) ?: true
            is PersonScope.Person -> matches(person.id)
            is PersonScope.Group -> state.members
                .filter { memberGroupOf(it) == person.group }
                .any { matches(it.id) }
        }
    }

    private fun matchesDue(task: Task, scope: DueScope, now: Long): Boolean = when (scope) {
        DueScope.ALL -> true
        DueScope.TODAY -> isDueToday(task.dueAt, now)
        DueScope.OVERDUE -> task.status != TaskStatus.DONE && isOverdue(task.dueAt, now)
    }

    private fun matchesSource(task: Task, scope: SourceScope): Boolean = when (scope) {
        SourceScope.ALL -> true
        SourceScope.CLIENT -> task.source is TaskSource.Deal
        SourceScope.PROJECT -> task.source is TaskSource.Project
    }

    private fun matchesQuery(
        task: Task,
        query: String,
        membersById: Map<String, TaskMember>,
    ): Boolean {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return true
        val assignee = task.assigneeId?.let { membersById[it]?.displayName } ?: task.assigneeEmail
        return listOfNotNull(task.title, task.description, assignee, task.source?.label)
            .any { it.lowercase().contains(q) }
    }

    /**
     * Grupy w kolejności sekcji z board360, „Bez sekcji" na końcu. Puste sekcje
     * pomijamy — na telefonie nie ma do czego przeciągać, więc pusty nagłówek
     * byłby samym hałasem.
     */
    private fun groupTasks(items: List<Task>, grouped: Boolean): List<Section> {
        if (!grouped) {
            return if (items.isEmpty()) emptyList() else listOf(Section(null, "", items))
        }
        val buckets = items.groupBy { it.section }
        val sections = TaskSection.entries.mapNotNull { section ->
            buckets[section]?.takeIf { it.isNotEmpty() }
                ?.let { Section(section, section.label, it) }
        }
        val none = buckets[null].orEmpty()
        return if (none.isEmpty()) sections else sections + Section(null, NO_SECTION_LABEL, none)
    }

    companion object {
        /** Domyślny widok: to, co jeszcze do zrobienia. */
        val OPEN_STATUSES = setOf(TaskStatus.OPEN, TaskStatus.IN_PROGRESS)
        val ALL_STATUSES = TaskStatus.entries.toSet()
    }
}
