package com.ekotak.teamtalk.presentation.task

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.data.local.preferences.SessionPreferences
import com.ekotak.teamtalk.domain.model.TaskMember
import com.ekotak.teamtalk.domain.model.TaskPriority
import com.ekotak.teamtalk.domain.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject

@HiltViewModel
class CreateTaskViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val taskRepository: TaskRepository,
    private val sessionPreferences: SessionPreferences,
) : ViewModel() {

    private val phone: String = savedStateHandle["phone"] ?: ""
    private val callerName: String? =
        (savedStateHandle["name"] as String?)?.takeIf { it.isNotBlank() }

    data class UiState(
        val title: String = "",
        val description: String = "",
        val assigneeId: String? = null,
        val priority: TaskPriority = TaskPriority.NORMAL,
        val dueAtMillis: Long? = null,
        val members: List<TaskMember> = emptyList(),
        val isLoadingMembers: Boolean = false,
        val isSaving: Boolean = false,
        val isSaved: Boolean = false,
        val error: String? = null,
    )

    private val _uiState = MutableStateFlow(
        UiState(title = defaultTitle())
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        loadMembers()
    }

    private fun defaultTitle(): String {
        val who = callerName ?: phone.ifBlank { null }
        return if (who != null) "Kontakt: $who" else "Zadanie po połączeniu"
    }

    private fun loadMembers() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMembers = true) }
            try {
                val selfId = sessionPreferences.session.first()?.userId
                val members = taskRepository.getMembers()
                val defaultAssignee = when {
                    selfId != null && members.any { it.id == selfId } -> selfId
                    else -> null
                }
                _uiState.update {
                    it.copy(
                        members = members,
                        assigneeId = it.assigneeId ?: defaultAssignee,
                        isLoadingMembers = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoadingMembers = false,
                        error = e.message ?: "Nie udało się pobrać listy pracowników",
                    )
                }
            }
        }
    }

    fun onTitleChange(value: String) = _uiState.update { it.copy(title = value, error = null) }
    fun onDescriptionChange(value: String) = _uiState.update { it.copy(description = value) }
    fun onAssigneeChange(id: String?) = _uiState.update { it.copy(assigneeId = id) }
    fun onPriorityChange(priority: TaskPriority) = _uiState.update { it.copy(priority = priority) }
    fun onDueAtChange(millis: Long?) = _uiState.update { it.copy(dueAtMillis = millis) }

    fun createTask() {
        val state = _uiState.value
        val title = state.title.trim()
        if (title.isBlank()) {
            _uiState.update { it.copy(error = "Tytuł jest wymagany") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                val description = buildDescription(state.description.trim())
                taskRepository.createTask(
                    title = title,
                    description = description,
                    assigneeId = state.assigneeId,
                    dueAt = state.dueAtMillis?.let(::toIsoDate),
                    priority = state.priority,
                )
                _uiState.update { it.copy(isSaving = false, isSaved = true) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isSaving = false, error = friendlyError(e))
                }
            }
        }
    }

    /** Przyjazny, polski opis błędu tworzenia zadania. */
    private fun friendlyError(e: Throwable): String = when (e) {
        is retrofit2.HttpException -> when (e.code()) {
            401 -> "Sesja wygasła — zaloguj się ponownie"
            403 -> "Brak uprawnień do tworzenia zadań"
            422 -> "Nieprawidłowe dane zadania"
            in 500..599 -> "Błąd serwera (${e.code()}) — spróbuj ponownie"
            else -> "Nie udało się utworzyć zadania (kod ${e.code()})"
        }
        is java.io.IOException -> "Brak połączenia z serwerem"
        else -> e.message ?: "Nie udało się utworzyć zadania"
    }

    /** Dokleja numer telefonu do opisu (powiązania z klientem brak w API zadań). */
    private fun buildDescription(userText: String): String? {
        val phoneLine = phone.takeIf { it.isNotBlank() }?.let { "Telefon: $it" }
        return listOfNotNull(userText.ifBlank { null }, phoneLine)
            .joinToString("\n\n")
            .ifBlank { null }
    }

    private fun toIsoDate(millis: Long): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date(millis))
    }
}
