package com.ekotak.teamtalk.presentation.meetings

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.data.meeting.MeetingRecorder
import com.ekotak.teamtalk.data.remote.dto.MeetingAgendaInput
import com.ekotak.teamtalk.data.remote.dto.MeetingApproveAgenda
import com.ekotak.teamtalk.data.remote.dto.MeetingApproveProposal
import com.ekotak.teamtalk.data.remote.dto.MeetingApproveRequest
import com.ekotak.teamtalk.data.remote.dto.MeetingConflictDto
import com.ekotak.teamtalk.data.remote.dto.MeetingDto
import com.ekotak.teamtalk.data.remote.dto.MeetingListItemDto
import com.ekotak.teamtalk.data.remote.dto.MeetingMetaDto
import com.ekotak.teamtalk.data.remote.dto.MeetingUpsertRequest
import com.ekotak.teamtalk.domain.repository.MeetingRepository
import com.ekotak.teamtalk.presentation.crm.crmErrorMessage
import com.ekotak.teamtalk.presentation.crm.parseIsoMillis
import com.ekotak.teamtalk.service.MeetingRecordingService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.Calendar
import java.util.UUID
import javax.inject.Inject

// ── Lista ────────────────────────────────────────────────────────────────────

@HiltViewModel
class MeetingsListViewModel @Inject constructor(
    private val repository: MeetingRepository,
    recorder: MeetingRecorder,
) : ViewModel() {
    data class State(
        val items: List<MeetingListItemDto> = emptyList(),
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    val recorderState: StateFlow<MeetingRecorder.State> = recorder.state

    init { load() }

    fun refresh() = load(refreshing = true)

    private fun load(refreshing: Boolean = false) {
        _state.update { it.copy(isRefreshing = refreshing, error = null) }
        viewModelScope.launch {
            runCatching { repository.list() }
                .onSuccess { list -> _state.update { it.copy(items = list, isLoading = false, isRefreshing = false) } }
                .onFailure { e ->
                    _state.update {
                        it.copy(isLoading = false, isRefreshing = false, error = crmErrorMessage(e, "Nie udało się pobrać spotkań"))
                    }
                }
        }
    }
}

// ── Kreator / edycja ─────────────────────────────────────────────────────────

data class AgendaDraft(val key: String = UUID.randomUUID().toString(), val id: String? = null, val text: String)

@HiltViewModel
class MeetingFormViewModel @Inject constructor(
    private val repository: MeetingRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val editId: String? = savedStateHandle.get<String>("id")

    data class State(
        val meta: MeetingMetaDto? = null,
        val isLoading: Boolean = true,
        val type: String? = null,
        val title: String = "",
        val hostId: String = "",
        val participantIds: List<String> = emptyList(),
        val startAtMs: Long = defaultStart(),
        val durationMin: Int = 60,
        val location: String = "",
        val agenda: List<AgendaDraft> = emptyList(),
        val conflicts: List<MeetingConflictDto> = emptyList(),
        val isSaving: Boolean = false,
        val error: String? = null,
        val savedId: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private var conflictJob: Job? = null

    init {
        viewModelScope.launch {
            runCatching {
                val meta = repository.meta()
                val existing = editId?.let { repository.get(it) }
                meta to existing
            }.onSuccess { (meta, m) ->
                _state.update { s ->
                    if (m == null) {
                        s.copy(meta = meta, isLoading = false, hostId = meta.me.id)
                    } else {
                        s.copy(
                            meta = meta,
                            isLoading = false,
                            type = m.type,
                            title = m.title,
                            hostId = m.host.id,
                            participantIds = m.participants.filter { it.role == "participant" }.map { it.id },
                            startAtMs = parseIsoMillis(m.startAt) ?: s.startAtMs,
                            durationMin = m.durationMin,
                            location = m.location.orEmpty(),
                            agenda = m.agenda.map { AgendaDraft(key = it.id, id = it.id, text = it.text) },
                        )
                    }
                }
                checkConflicts()
            }.onFailure { e ->
                _state.update { it.copy(isLoading = false, error = crmErrorMessage(e, "Nie udało się otworzyć kreatora")) }
            }
        }
    }

    fun pickType(key: String) {
        val meta = _state.value.meta ?: return
        val def = meta.types.firstOrNull { it.key == key } ?: return
        _state.update { s ->
            val prev = meta.types.firstOrNull { it.key == s.type }?.agendaTemplate.orEmpty()
            val untouched = s.agenda.map { it.text } == prev
            val hostOk = !def.boardOnly || meta.people.firstOrNull { it.id == s.hostId }?.isBoard == true
            s.copy(
                type = key,
                title = if (s.title.isBlank() || meta.types.any { it.label == s.title }) def.label else s.title,
                agenda = if (s.agenda.isEmpty() || untouched) def.agendaTemplate.map { AgendaDraft(text = it) } else s.agenda,
                hostId = if (hostOk) s.hostId else meta.me.id,
            )
        }
    }

    fun clearType() = _state.update { it.copy(type = null) }
    fun setTitle(v: String) = _state.update { it.copy(title = v) }
    fun setLocation(v: String) = _state.update { it.copy(location = v) }
    fun setHost(id: String) { _state.update { it.copy(hostId = id, participantIds = it.participantIds - id) }; checkConflicts() }
    fun setStart(ms: Long) { _state.update { it.copy(startAtMs = ms) }; checkConflicts() }
    fun setDuration(min: Int) { _state.update { it.copy(durationMin = min) }; checkConflicts() }
    fun toggleParticipant(id: String) {
        _state.update { s -> s.copy(participantIds = if (id in s.participantIds) s.participantIds - id else s.participantIds + id) }
        checkConflicts()
    }
    fun setAgendaText(key: String, text: String) =
        _state.update { s -> s.copy(agenda = s.agenda.map { if (it.key == key) it.copy(text = text) else it }) }
    fun addAgenda() = _state.update { it.copy(agenda = it.agenda + AgendaDraft(text = "")) }
    fun removeAgenda(key: String) = _state.update { s -> s.copy(agenda = s.agenda.filterNot { it.key == key }) }
    fun moveAgendaUp(key: String) = _state.update { s ->
        val i = s.agenda.indexOfFirst { it.key == key }
        if (i <= 0) s else s.copy(agenda = s.agenda.toMutableList().apply { add(i - 1, removeAt(i)) })
    }
    fun clearError() = _state.update { it.copy(error = null) }

    /** D4: kolizje tylko ostrzegają. */
    private fun checkConflicts() {
        conflictJob?.cancel()
        conflictJob = viewModelScope.launch {
            delay(500)
            val s = _state.value
            if (s.hostId.isBlank()) return@launch
            val list = runCatching {
                repository.conflicts(
                    listOf(s.hostId) + s.participantIds,
                    Instant.ofEpochMilli(s.startAtMs).toString(),
                    s.durationMin,
                    editId,
                )
            }.getOrDefault(emptyList())
            _state.update { it.copy(conflicts = list) }
        }
    }

    fun save() {
        val s = _state.value
        val type = s.type ?: return
        _state.update { it.copy(isSaving = true, error = null) }
        viewModelScope.launch {
            val request = MeetingUpsertRequest(
                type = type,
                title = s.title.trim(),
                hostId = s.hostId,
                participantIds = s.participantIds.filter { it != s.hostId },
                startAt = Instant.ofEpochMilli(s.startAtMs).toString(),
                durationMin = s.durationMin,
                location = s.location.trim().ifBlank { null },
                agenda = s.agenda.filter { it.text.isNotBlank() }.map { MeetingAgendaInput(it.id, it.text.trim()) },
            )
            runCatching { if (editId != null) repository.update(editId, request) else repository.create(request) }
                .onSuccess { m -> _state.update { it.copy(isSaving = false, savedId = m.id) } }
                .onFailure { e -> _state.update { it.copy(isSaving = false, error = crmErrorMessage(e, "Nie udało się zapisać spotkania")) } }
        }
    }

    private companion object {
        fun defaultStart(): Long = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 9); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
}

// ── Karta spotkania ──────────────────────────────────────────────────────────

data class ProposalDraft(
    val key: String,
    val id: String?,
    val source: String,
    val status: String,
    val title: String,
    val description: String,
    val area: String?,
    val assigneeId: String?,
    val durationDays: String,
)

@HiltViewModel
class MeetingDetailViewModel @Inject constructor(
    private val repository: MeetingRepository,
    private val recorder: MeetingRecorder,
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val id: String = checkNotNull(savedStateHandle.get<String>("id"))

    data class State(
        val meeting: MeetingDto? = null,
        val meta: MeetingMetaDto? = null,
        val fetchedAtMs: Long = 0,
        val isLoading: Boolean = true,
        val isBusy: Boolean = false,
        val error: String? = null,
        val message: String? = null,
        val deleted: Boolean = false,
        // Akceptacja
        val summary: String = "",
        val decisions: String = "",
        val agendaDone: Map<String, Boolean> = emptyMap(),
        val proposals: List<ProposalDraft> = emptyList(),
        val reviewLoadedFor: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    val recorderState: StateFlow<MeetingRecorder.State> = recorder.state

    init {
        load()
        viewModelScope.launch {
            runCatching { repository.meta() }.onSuccess { meta -> _state.update { it.copy(meta = meta) } }
        }
        // Odpytywanie w trakcie i przy przetwarzaniu — stan zmienia też panel i serwer.
        viewModelScope.launch {
            while (isActive) {
                delay(5_000)
                val status = _state.value.meeting?.status
                if (status in setOf("live", "paused", "processing")) load(silent = true)
            }
        }
    }

    fun load(silent: Boolean = false) {
        if (!silent) _state.update { it.copy(error = null) }
        viewModelScope.launch {
            runCatching { repository.get(id) }
                .onSuccess { apply(it) }
                .onFailure { e ->
                    if (!silent) _state.update { it.copy(isLoading = false, error = crmErrorMessage(e, "Nie udało się pobrać spotkania")) }
                }
        }
    }

    private fun apply(m: MeetingDto) {
        _state.update { s ->
            val review = m.status == "review" && s.reviewLoadedFor != m.id
            s.copy(
                meeting = m,
                fetchedAtMs = System.currentTimeMillis(),
                isLoading = false,
                isBusy = false,
                summary = if (review) m.summary.orEmpty() else s.summary,
                decisions = if (review) m.decisions.joinToString("\n") else s.decisions,
                agendaDone = if (review) m.agenda.associate { it.id to (it.done || it.aiDiscussed == true) } else s.agendaDone,
                proposals = if (review) {
                    m.proposals.filter { it.taskId == null }.map {
                        ProposalDraft(
                            key = it.id, id = it.id, source = it.source, status = it.status, title = it.title,
                            description = it.description.orEmpty(), area = it.area, assigneeId = it.assigneeId,
                            durationDays = it.durationDays?.toString().orEmpty(),
                        )
                    }
                } else s.proposals,
                reviewLoadedFor = if (review) m.id else s.reviewLoadedFor,
            )
        }
    }

    private fun run(fallback: String, block: suspend () -> MeetingDto) {
        _state.update { it.copy(isBusy = true, error = null) }
        viewModelScope.launch {
            runCatching { block() }
                .onSuccess { apply(it) }
                .onFailure { e -> _state.update { it.copy(isBusy = false, error = crmErrorMessage(e, fallback)) } }
        }
    }

    /** Włącza spotkanie na serwerze i odpala nagrywanie w tle. Wołać po przyznaniu mikrofonu. */
    fun startRecording() {
        val m = _state.value.meeting ?: return
        if (recorder.isActive) {
            _state.update { it.copy(error = "Na tym telefonie trwa już nagrywanie innego spotkania.") }
            return
        }
        run("Nie udało się włączyć spotkania") {
            val started = repository.start(m.id)
            MeetingRecordingService.start(context, m.id, m.title)
            started
        }
    }

    fun pause() = control(MeetingRecordingService.ACTION_PAUSE, "pause")
    fun resume() = control(MeetingRecordingService.ACTION_RESUME, "resume")
    fun finish() = control(MeetingRecordingService.ACTION_FINISH, "finish")

    /**
     * Gdy nagrywa TEN telefon, polecenie idzie przez usługę (działa też bez
     * zasięgu). W przeciwnym razie telefon tylko steruje — prosto do API.
     */
    private fun control(action: String, command: String) {
        if (recorder.state.value.meetingId == id) {
            MeetingRecordingService.send(context, action)
            _state.update { s ->
                val m = s.meeting ?: return@update s
                val status = when (command) { "pause" -> "paused"; "resume" -> "live"; else -> "processing" }
                s.copy(meeting = m.copy(status = status))
            }
            viewModelScope.launch { delay(1500); load(silent = true) }
        } else {
            run("Nie udało się wykonać polecenia") { repository.command(id, command) }
        }
    }

    fun skipRecording() = run("Nie udało się pominąć nagrania") { repository.command(id, "skip-recording") }
    fun retry() = run("Nie udało się ponowić transkrypcji") { repository.command(id, "retry") }
    fun rsvp(response: String) = run("Nie udało się zapisać odpowiedzi") { repository.rsvp(id, response) }

    fun toggleAgenda(itemId: String, done: Boolean) {
        _state.update { s ->
            val m = s.meeting ?: return@update s
            s.copy(meeting = m.copy(agenda = m.agenda.map { if (it.id == itemId) it.copy(done = done) else it }))
        }
        viewModelScope.launch {
            runCatching { repository.toggleAgenda(id, itemId, done) }
                .onSuccess { apply(it) }
                .onFailure { e -> _state.update { it.copy(error = crmErrorMessage(e, "Nie udało się odhaczyć punktu")) } }
        }
    }

    fun delete() {
        _state.update { it.copy(isBusy = true) }
        viewModelScope.launch {
            runCatching { repository.delete(id) }
                .onSuccess { _state.update { it.copy(isBusy = false, deleted = true) } }
                .onFailure { e -> _state.update { it.copy(isBusy = false, error = crmErrorMessage(e, "Nie udało się usunąć")) } }
        }
    }

    // ── Akceptacja ──

    fun setSummary(v: String) = _state.update { it.copy(summary = v) }
    fun setDecisions(v: String) = _state.update { it.copy(decisions = v) }
    fun setAgendaDone(itemId: String, v: Boolean) = _state.update { it.copy(agendaDone = it.agendaDone + (itemId to v)) }
    fun patchProposal(key: String, f: (ProposalDraft) -> ProposalDraft) =
        _state.update { s -> s.copy(proposals = s.proposals.map { if (it.key == key) f(it) else it }) }
    fun addProposal() = _state.update { s ->
        s.copy(
            proposals = s.proposals + ProposalDraft(
                key = UUID.randomUUID().toString(), id = null, source = "manual", status = "accepted",
                title = "", description = "", area = null, assigneeId = null, durationDays = "",
            ),
        )
    }

    fun approve() {
        val s = _state.value
        if (s.proposals.any { it.status == "proposed" }) {
            _state.update { it.copy(error = "Zatwierdź albo odrzuć każdą propozycję zadania.") }
            return
        }
        if (s.proposals.any { it.status == "accepted" && it.title.isBlank() }) {
            _state.update { it.copy(error = "Zatwierdzone zadanie musi mieć nazwę.") }
            return
        }
        val request = MeetingApproveRequest(
            summary = s.summary.trim(),
            decisions = s.decisions.lines().map { it.trim().removePrefix("-").removePrefix("•").trim() }.filter { it.isNotEmpty() },
            agenda = s.agendaDone.map { (k, v) -> MeetingApproveAgenda(k, v) },
            proposals = s.proposals
                .filter { it.id != null || it.status == "accepted" }
                .map {
                    MeetingApproveProposal(
                        id = it.id,
                        status = it.status,
                        title = it.title.trim().ifBlank { "—" },
                        description = it.description.trim().ifBlank { null },
                        area = it.area,
                        assigneeId = it.assigneeId,
                        durationDays = it.durationDays.toIntOrNull()?.coerceIn(0, 365),
                    )
                },
        )
        run("Nie udało się zaakceptować podsumowania") {
            repository.approve(id, request).also {
                _state.update { st -> st.copy(message = "Podsumowanie zaakceptowane — nagranie i transkrypcja usunięte.") }
            }
        }
    }

    fun clearMessage() = _state.update { it.copy(message = null) }
    fun clearError() = _state.update { it.copy(error = null) }
}
