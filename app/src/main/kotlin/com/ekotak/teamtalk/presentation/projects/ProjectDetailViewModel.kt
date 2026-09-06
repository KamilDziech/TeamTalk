package com.ekotak.teamtalk.presentation.projects

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.data.local.preferences.SessionPreferences
import com.ekotak.teamtalk.domain.model.ProjectDetail
import com.ekotak.teamtalk.domain.model.ProjectTask
import com.ekotak.teamtalk.domain.repository.ProjectRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Zakładki karty projektu na telefonie. */
enum class ProjectTab { MILESTONES, MY_TASKS, TEAM }

/**
 * Karta projektu. Gantta na telefonie nie ma — zamiast osi poziomej pionowa
 * lista kamieni z paskiem postępu, bo tak się to czyta kciukiem.
 */
@HiltViewModel
class ProjectDetailViewModel @Inject constructor(
    private val repository: ProjectRepository,
    private val sessionPreferences: SessionPreferences,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val tab: ProjectTab = ProjectTab.MILESTONES,
        val detail: ProjectDetail? = null,
        val myTasks: List<ProjectTask> = emptyList(),
        val pendingCount: Int = 0,
        val offline: Boolean = false,
        val message: String? = null,
        /** Zadanie, dla którego pytamy „ile zajęło?". */
        val closing: ProjectTask? = null,
    )

    private val projectId: String = savedStateHandle.get<String>("projectId").orEmpty()

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var myUserId: String? = null

    init {
        viewModelScope.launch {
            myUserId = sessionPreferences.session.first()?.userId
            _state.update { it.copy(myTasks = mineOf(it.detail)) }
        }
        viewModelScope.launch {
            repository.observeDetail(projectId).collect { detail ->
                _state.update {
                    it.copy(isLoading = false, detail = detail, myTasks = mineOf(detail))
                }
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
            val ok = repository.refreshDetail(projectId)
            _state.update { it.copy(isRefreshing = false, offline = !ok) }
        }
    }

    fun setTab(tab: ProjectTab) = _state.update { it.copy(tab = tab) }

    fun askHours(task: ProjectTask) = _state.update { it.copy(closing = task) }

    fun cancelClose() = _state.update { it.copy(closing = null) }

    /**
     * Domknięcie zadania. `minutes = null` znaczy „nie podano" — rozliczenie
     * policzy je po estymacie i oznaczy jako szacowane. Bez zasięgu zmiana idzie
     * do kolejki, a zadanie i tak od razu wygląda na zrobione.
     */
    fun closeTask(task: ProjectTask, minutes: Int?) {
        viewModelScope.launch {
            _state.update { it.copy(closing = null) }
            val ok = repository.closeTask(task.id, minutes)
            _state.update {
                it.copy(
                    message = if (ok) "Zadanie zamknięte." else "Nie udało się zamknąć zadania.",
                )
            }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }

    /** Moje = przypisane do mnie i jeszcze niezrobione, najpilniejsze na górze. */
    private fun mineOf(detail: ProjectDetail?): List<ProjectTask> {
        val uid = myUserId ?: return emptyList()
        return detail?.tasks.orEmpty()
            .filter { it.assigneeId == uid }
            .sortedWith(compareBy({ it.done }, { it.dueAt ?: "9999" }))
    }
}
