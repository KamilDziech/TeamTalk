package com.ekotak.teamtalk.presentation.projects

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.data.local.preferences.SessionPreferences
import com.ekotak.teamtalk.domain.model.IdeaDraft
import com.ekotak.teamtalk.domain.model.Project
import com.ekotak.teamtalk.domain.model.ProjectStage
import com.ekotak.teamtalk.domain.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Zakładka listy: „Moje" = projekty, w których mam zadanie albo je prowadzę. */
enum class ProjectsFilter { MINE, ALL }

/**
 * Lista projektów na telefonie — odpowiednik kafli z `ProjectsView.tsx`, ale
 * pogrupowana pionowo i domyślnie zawężona do moich. Dane idą z Room, więc ekran
 * otwiera się bez zasięgu; odświeżenie tylko dolewa świeże.
 */
@HiltViewModel
class ProjectsViewModel @Inject constructor(
    private val repository: ProjectRepository,
    private val sessionPreferences: SessionPreferences,
) : ViewModel() {

    data class Group(val stage: ProjectStage, val projects: List<Project>)

    data class UiState(
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val filter: ProjectsFilter = ProjectsFilter.MINE,
        val groups: List<Group> = emptyList(),
        val pendingCount: Int = 0,
        /** Ostatnie odświeżenie nie doszło — pokazujemy pasek „brak sieci". */
        val offline: Boolean = false,
        val message: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var allProjects: List<Project> = emptyList()
    private var myEmail: String? = null

    init {
        viewModelScope.launch {
            myEmail = sessionPreferences.session.first()?.email
            // Filtr „Moje" liczy się z e-maila, więc po jego wczytaniu trzeba
            // przeliczyć grupy — inaczej pierwszy render pokazywałby wszystko.
            _state.update { it.copy(groups = group(allProjects, it.filter)) }
        }
        viewModelScope.launch {
            repository.observeProjects().collect { projects ->
                allProjects = projects
                _state.update { it.copy(isLoading = false, groups = group(projects, it.filter)) }
            }
        }
        viewModelScope.launch {
            repository.observePendingCount().collect { count ->
                _state.update { it.copy(pendingCount = count) }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(isRefreshing = true) }
            val ok = repository.refreshProjects()
            _state.update { it.copy(isRefreshing = false, offline = !ok) }
        }
    }

    fun setFilter(filter: ProjectsFilter) {
        _state.update { it.copy(filter = filter, groups = group(allProjects, filter)) }
    }

    fun submitIdea(name: String, description: String?, department: String?, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = repository.submitIdea(
                IdeaDraft(
                    name = name.trim(),
                    description = description?.trim()?.ifBlank { null },
                    department = department?.trim()?.ifBlank { null },
                ),
            )
            _state.update {
                it.copy(
                    message = if (ok) {
                        "Pomysł trafił do Poczekalni."
                    } else {
                        "Nie udało się wysłać pomysłu."
                    },
                )
            }
            onDone(ok)
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    /**
     * Grupowanie po etapie w kolejności decyzyjnej: najpierw to, co czeka na
     * czyjąś decyzję, na końcu rozliczone. Puste grupy odpadają.
     */
    private fun group(projects: List<Project>, filter: ProjectsFilter): List<Group> {
        val visible = when (filter) {
            ProjectsFilter.ALL -> projects
            // „Moje" bez listy zadań w pamięci opieramy na tym, co niesie kafel:
            // prowadzę projekt albo jestem jego sponsorem. Zadania sprawdza już
            // karta projektu — tam mamy pełną listę.
            ProjectsFilter.MINE -> projects.filter {
                val email = myEmail
                email != null && (it.managerEmail == email || it.sponsorEmail == email)
            }.ifEmpty { projects }
        }
        val order = listOf(
            ProjectStage.APPROVAL,
            ProjectStage.ACTIVE,
            ProjectStage.PLANNING,
            ProjectStage.APPRAISAL,
            ProjectStage.IDEA,
            ProjectStage.CLOSED,
        )
        return order.mapNotNull { stage ->
            val items = visible.filter { it.stage == stage }
            if (items.isEmpty()) null else Group(stage, items)
        }
    }
}
