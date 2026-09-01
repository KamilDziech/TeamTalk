package com.ekotak.teamtalk.presentation.task

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.domain.model.Edit
import com.ekotak.teamtalk.domain.model.Task
import com.ekotak.teamtalk.domain.model.TaskAttachment
import com.ekotak.teamtalk.domain.model.TaskComment
import com.ekotak.teamtalk.domain.model.TaskMember
import com.ekotak.teamtalk.domain.model.TaskPatch
import com.ekotak.teamtalk.domain.model.TaskPriority
import com.ekotak.teamtalk.domain.model.TaskSection
import com.ekotak.teamtalk.domain.model.slaLabel
import com.ekotak.teamtalk.domain.model.TaskStatus
import com.ekotak.teamtalk.domain.usecase.task.AddTaskCommentUseCase
import com.ekotak.teamtalk.domain.usecase.task.DeleteTaskAttachmentUseCase
import com.ekotak.teamtalk.domain.usecase.task.DeleteTaskUseCase
import com.ekotak.teamtalk.domain.usecase.task.DownloadTaskAttachmentUseCase
import com.ekotak.teamtalk.domain.usecase.task.GetTaskAttachmentsUseCase
import com.ekotak.teamtalk.domain.usecase.task.UploadTaskAttachmentUseCase
import com.ekotak.teamtalk.domain.usecase.task.GetTaskCommentsUseCase
import com.ekotak.teamtalk.domain.usecase.task.GetTaskMembersUseCase
import com.ekotak.teamtalk.domain.usecase.task.GetTaskUseCase
import com.ekotak.teamtalk.domain.usecase.task.MarkDiscussionReadUseCase
import com.ekotak.teamtalk.domain.usecase.task.UpdateTaskUseCase
import com.ekotak.teamtalk.presentation.crm.crmErrorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * Karta zadania (E2) wraz z wątkiem komentarzy (E5 w części „komentarze").
 *
 * Wątek komentarzy jest jednocześnie DYSKUSJĄ w Komunikatorze: wywołanie kogoś
 * przez @ wciąga to zadanie do jego skrzynki, a odpowiedź napisana w skrzynce
 * wraca tutaj. Dlatego wejście w kartę zeruje licznik nieprzeczytanych — to ten
 * sam wątek, a nie dwie równoległe rozmowy.
 *
 * Zapis wymaga sieci (kolejka offline to E3), więc błąd idzie na wierzch
 * zamiast udawać, że zmiana weszła.
 */
@HiltViewModel
class TaskDetailViewModel @Inject constructor(
    private val getTask: GetTaskUseCase,
    private val getComments: GetTaskCommentsUseCase,
    private val addComment: AddTaskCommentUseCase,
    private val updateTask: UpdateTaskUseCase,
    private val deleteTask: DeleteTaskUseCase,
    private val getAttachments: GetTaskAttachmentsUseCase,
    private val uploadAttachment: UploadTaskAttachmentUseCase,
    private val downloadAttachment: DownloadTaskAttachmentUseCase,
    private val deleteAttachment: DeleteTaskAttachmentUseCase,
    private val getTaskMembers: GetTaskMembersUseCase,
    private val markDiscussionRead: MarkDiscussionReadUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val taskId: String = checkNotNull(savedStateHandle["taskId"])

    data class UiState(
        val isLoading: Boolean = true,
        val error: String? = null,
        val message: String? = null,
        val task: Task? = null,
        val comments: List<TaskComment> = emptyList(),
        val members: List<TaskMember> = emptyList(),
        val membersById: Map<String, TaskMember> = emptyMap(),
        /** Trwa zapis pola karty — przyciski stanu na czas zapisu gasną. */
        val saving: Boolean = false,
        val sending: Boolean = false,
        val uploading: Boolean = false,
        val attachments: List<TaskAttachment> = emptyList(),
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        load()
        loadMembers()
        loadAttachments()
    }

    private fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val task = getTask(taskId)
                val comments = getComments(taskId)
                _uiState.update {
                    it.copy(isLoading = false, task = task, comments = comments)
                }
                // Otwarta karta = przeczytana dyskusja. Świadomie po cichu:
                // nieudany znacznik odczytu nie jest powodem, by straszyć błędem.
                runCatching { markDiscussionRead(taskId) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = crmErrorMessage(e, "Nie udało się wczytać zadania"),
                    )
                }
            }
        }
    }

    private fun loadMembers() {
        viewModelScope.launch {
            runCatching { sortMembersForTasks(getTaskMembers()) }
                .onSuccess { members ->
                    _uiState.update {
                        it.copy(members = members, membersById = members.associateBy { m -> m.id })
                    }
                }
        }
    }

    fun refresh() = load()

    /** Odhaczenie zadania wprost z karty — najczęstsza akcja. */
    fun setDone(done: Boolean) = patch(
        TaskPatch(status = Edit(if (done) TaskStatus.DONE else TaskStatus.OPEN)),
        if (done) "Zadanie odhaczone." else "Zadanie znów otwarte.",
    )

    fun setPriority(high: Boolean) = patch(
        TaskPatch(priority = Edit(if (high) TaskPriority.HIGH else TaskPriority.NORMAL)),
        if (high) "Priorytet wysoki." else "Priorytet normalny.",
    )

    fun setAssignee(memberId: String?) = patch(
        TaskPatch(assigneeId = Edit(memberId)),
        if (memberId == null) "Zdjęto wykonawcę." else "Zmieniono wykonawcę.",
    )

    fun setStatus(status: TaskStatus) = patch(
        TaskPatch(status = Edit(status)),
        "Zmieniono status: ${status.label}.",
    )

    /**
     * Termin z wybieraka dat przychodzi jako północ UTC wybranego dnia — tak
     * samo jak w kreatorze zadania, więc obie ścieżki wysyłają board360 to samo.
     */
    fun setDueAt(millis: Long?) = patch(
        TaskPatch(dueAt = Edit(millis?.let(::isoFromMillis))),
        if (millis == null) "Zdjęto termin." else "Zmieniono termin.",
    )

    fun setSection(section: TaskSection?) = patch(
        TaskPatch(section = Edit(section)),
        if (section == null) "Zdjęto sekcję." else "Sekcja: ${section.label}.",
    )

    fun setSla(hours: Int?) = patch(
        TaskPatch(slaHours = Edit(hours)),
        if (hours == null) "Zdjęto SLA." else "SLA: ${slaLabel(hours)}.",
    )

    fun setEstimate(minutes: Int?) = patch(
        TaskPatch(estimatedMinutes = Edit(minutes)),
        if (minutes == null) "Zdjęto szacowany czas." else "Potrzebny czas: ${minutes} min.",
    )

    /**
     * Opis zadania. Kreator wypełnia go tylko wtedy, gdy ktoś podyktował albo
     * wpisał szczegóły, więc dopisanie opisu później jest zwykłą ścieżką, a nie
     * wyjątkiem. Pusty tekst czyści pole (`null`), zamiast zapisywać pusty ciąg.
     */
    fun setDescription(text: String) {
        val value = text.trim().ifBlank { null }
        if (value == _uiState.value.task?.description?.trim()?.ifBlank { null }) return
        patch(
            TaskPatch(description = Edit(value)),
            if (value == null) "Usunięto opis." else "Zapisano opis.",
        )
    }

    // ── Załączniki ────────────────────────────────────────────────────────────

    private fun loadAttachments() {
        viewModelScope.launch {
            runCatching { getAttachments(taskId) }
                .onSuccess { list -> _uiState.update { it.copy(attachments = list) } }
        }
    }

    /**
     * Wgranie pliku. Zawartość czyta ekran (to on ma `ContentResolver` i wie,
     * co użytkownik wybrał w systemowym wyborze) — tu przychodzą gotowe bajty.
     */
    fun addAttachment(name: String, contentType: String, bytes: ByteArray) {
        viewModelScope.launch {
            _uiState.update { it.copy(uploading = true, message = null) }
            try {
                val added = uploadAttachment(taskId, name, contentType, bytes)
                _uiState.update {
                    it.copy(
                        uploading = false,
                        attachments = it.attachments + added,
                        message = "Dodano załącznik.",
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(uploading = false, message = crmErrorMessage(e, "Nie udało się wgrać pliku"))
                }
            }
        }
    }

    fun removeAttachment(id: String) {
        viewModelScope.launch {
            try {
                deleteAttachment(id)
                _uiState.update {
                    it.copy(
                        attachments = it.attachments.filterNot { a -> a.id == id },
                        message = "Usunięto załącznik.",
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(message = crmErrorMessage(e, "Nie udało się usunąć pliku")) }
            }
        }
    }

    /**
     * Pobiera plik do cache i oddaje go ekranowi, żeby ten otworzył go
     * systemowym podglądem. Nazwa pliku na dysku bierze się z id — dwa
     * załączniki o tej samej nazwie nie mają się nadpisywać.
     */
    fun openAttachment(attachment: TaskAttachment, cacheDir: File, onReady: (File) -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(message = null) }
            try {
                val dir = File(cacheDir, "attachments").apply { mkdirs() }
                val target = File(dir, "${attachment.id}-${attachment.name}")
                if (!target.exists()) downloadAttachment(attachment.id, target)
                onReady(target)
            } catch (e: Exception) {
                _uiState.update { it.copy(message = crmErrorMessage(e, "Nie udało się otworzyć pliku")) }
            }
        }
    }

    /**
     * Usunięcie zadania. [onDeleted] zamyka kartę — robimy to dopiero po
     * potwierdzeniu z serwera, żeby nie zamykać ekranu przy nieudanym zapisie.
     */
    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, message = null) }
            try {
                deleteTask(taskId)
                onDeleted()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(saving = false, message = crmErrorMessage(e, "Nie udało się usunąć zadania"))
                }
            }
        }
    }

    private fun patch(patch: TaskPatch, okMessage: String) {
        if (patch.isEmpty) return
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true, message = null) }
            try {
                val task = updateTask(taskId, patch)
                _uiState.update { it.copy(saving = false, task = task, message = okMessage) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(saving = false, message = crmErrorMessage(e, "Nie udało się zapisać"))
                }
            }
        }
    }

    /**
     * Komentarz z wywołaniami. [mentions] to tokeny („user:<id>", „role:<rola>",
     * „watchers", „all") — backend rozwija je do osób i to on zakłada im
     * dyskusję w Komunikatorze; telefon niczego nie rozsyła sam.
     */
    fun send(body: String, mentions: List<String>, onSent: () -> Unit) {
        val text = body.trim()
        if (text.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(sending = true, message = null) }
            try {
                val comment = addComment(taskId, text, mentions)
                _uiState.update {
                    it.copy(
                        sending = false,
                        comments = it.comments + comment,
                        // Licznik na karcie ma się zgadzać bez ponownego pobrania.
                        task = it.task?.copy(commentCount = it.comments.size + 1),
                        message = if (mentions.isEmpty()) null else "Wywołanie wysłane.",
                    )
                }
                onSent()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        sending = false,
                        message = crmErrorMessage(e, "Nie udało się wysłać komentarza"),
                    )
                }
            }
        }
    }

    fun consumeMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
