package com.ekotak.teamtalk.presentation.meetings

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkManager
import com.ekotak.teamtalk.data.meeting.MeetingDictationRecorder
import com.ekotak.teamtalk.data.meeting.MeetingRecorder
import com.ekotak.teamtalk.data.meeting.VoiceSummaryRecorder
import com.ekotak.teamtalk.data.remote.dto.MeetingAgendaInput
import com.ekotak.teamtalk.data.remote.dto.MeetingAgendaProposalItemDto
import com.ekotak.teamtalk.data.remote.dto.MeetingAgendaProposalRequest
import com.ekotak.teamtalk.data.remote.dto.MeetingApproveAgenda
import com.ekotak.teamtalk.data.remote.dto.MeetingApproveProposal
import com.ekotak.teamtalk.data.remote.dto.MeetingApproveRequest
import com.ekotak.teamtalk.data.remote.dto.MeetingConflictDto
import com.ekotak.teamtalk.data.remote.dto.MeetingContractorCreateRequest
import com.ekotak.teamtalk.data.remote.dto.MeetingContractorDto
import com.ekotak.teamtalk.data.remote.dto.MeetingDto
import com.ekotak.teamtalk.data.remote.dto.MeetingListItemDto
import com.ekotak.teamtalk.data.remote.dto.MeetingMetaDto
import com.ekotak.teamtalk.data.remote.dto.MeetingTypeDto
import com.ekotak.teamtalk.data.remote.dto.MeetingUpsertRequest
import com.ekotak.teamtalk.domain.repository.KIND_SUMMARY
import com.ekotak.teamtalk.domain.repository.MeetingRepository
import com.ekotak.teamtalk.presentation.crm.crmErrorMessage
import com.ekotak.teamtalk.presentation.crm.parseIsoMillis
import com.ekotak.teamtalk.service.MeetingRecordingService
import com.ekotak.teamtalk.worker.MeetingRecordingUploadWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import kotlinx.coroutines.withContext
import retrofit2.HttpException
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

/** Gotowe długości (min) — wszystko poza nimi to „Własny…" (v2, D14). */
val MEETING_DURATION_PRESETS = listOf(30, 45, 60, 90, 120, 180)

/** [minutes] jako tekst pola — puste = punkt bez czasu (opcjonalny, v2). */
data class AgendaDraft(
    val key: String = UUID.randomUUID().toString(),
    val id: String? = null,
    val text: String,
    val minutes: String = "",
)

@HiltViewModel
class MeetingFormViewModel @Inject constructor(
    private val repository: MeetingRepository,
    private val dictationRecorder: MeetingDictationRecorder,
    private val meetingRecorder: MeetingRecorder,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val editId: String? = savedStateHandle.get<String>("id")

    enum class DictationPhase { IDLE, RECORDING, UPLOADING }

    data class State(
        val meta: MeetingMetaDto? = null,
        val isLoading: Boolean = true,
        val type: String? = null,
        val title: String = "",
        val hostId: String = "",
        val participantIds: List<String> = emptyList(),
        val startAtMs: Long = defaultStart(),
        /** Minuty JEDNEGO dnia (D14). */
        val durationMin: Int = 60,
        /** „Własny…" — dni × godziny dnia; poza nim dayCount zawsze 1. */
        val customDuration: Boolean = false,
        val dayCount: Int = 1,
        val location: String = "",
        val agenda: List<AgendaDraft> = emptyList(),
        val conflicts: List<MeetingConflictDto> = emptyList(),
        /** Spotkanie z kontrahentem: wybrany wpis kartoteki (Kontrahenci / Inne). */
        val contractor: MeetingContractorDto? = null,
        val contractorQuery: String = "",
        val contractorResults: List<MeetingContractorDto> = emptyList(),
        val isAddingContractor: Boolean = false,
        val contractorError: String? = null,
        val isSaving: Boolean = false,
        val error: String? = null,
        val savedId: String? = null,
        // D15: „Co chcesz omówić?" → dyktowanie → propozycja agendy.
        val brief: String = "",
        val dictation: DictationPhase = DictationPhase.IDLE,
        val dictationSec: Int = 0,
        val dictationError: String? = null,
        val isProposing: Boolean = false,
        val proposalError: String? = null,
        /** Propozycja czekająca na „Zastąpić obecną agendę?". */
        val pendingProposal: List<MeetingAgendaProposalItemDto>? = null,
    ) {
        /** Y z licznika „Rozplanowano X / Y min". */
        val plannedMin: Int get() = dayCount * durationMin
        /** X — suma minut wpisanych przy punktach. */
        val scheduledMin: Int get() = agenda.filter { it.text.isNotBlank() }.sumOf { it.minutes.toIntOrNull() ?: 0 }
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private var conflictJob: Job? = null
    private var dictationTimer: Job? = null

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
                        val days = m.dayCount.coerceIn(1, 14)
                        s.copy(
                            meta = meta,
                            isLoading = false,
                            type = m.type,
                            title = m.title,
                            hostId = m.host.id,
                            participantIds = m.participants.filter { it.role == "participant" }.map { it.id },
                            startAtMs = parseIsoMillis(m.startAt) ?: s.startAtMs,
                            durationMin = m.durationMin,
                            customDuration = days > 1 || m.durationMin !in MEETING_DURATION_PRESETS,
                            dayCount = days,
                            location = m.location.orEmpty(),
                            agenda = m.agenda.map {
                                AgendaDraft(key = it.id, id = it.id, text = it.text, minutes = it.durationMin?.toString().orEmpty())
                            },
                            contractor = m.client,
                        )
                    }
                }
                checkConflicts()
                if (needsClient() && _state.value.contractor == null) searchContractors()
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
            // Spotkanie zarządu: prowadzący i uczestnicy wyłącznie z rolą „zarząd" (isBoard z API).
            val board = meta.people.filter { it.isBoard }
            val hostOk = !def.boardOnly || board.any { it.id == s.hostId }
            val boardHost = board.firstOrNull { it.id == meta.me.id }?.id ?: board.firstOrNull()?.id ?: meta.me.id
            s.copy(
                type = key,
                title = if (s.title.isBlank() || meta.types.any { it.label == s.title }) def.label else s.title,
                agenda = if (s.agenda.isEmpty() || untouched) def.agendaTemplate.map { AgendaDraft(text = it) } else s.agenda,
                hostId = if (hostOk) s.hostId else boardHost,
                participantIds = if (def.boardOnly) s.participantIds.filter { id -> board.any { it.id == id } } else s.participantIds,
                // Kontrahent nie jest kandydatem (i odwrotnie) — po zmianie rodzaju wybór od nowa.
                contractor = s.contractor?.takeIf { (it.category == "kandydat") == def.needsCandidate },
                contractorResults = emptyList(),
                isAddingContractor = false,
            )
        }
        if (needsClient() && _state.value.contractor == null) searchContractors()
    }

    private fun typeDef(): MeetingTypeDto? = _state.value.let { s -> s.meta?.types?.firstOrNull { it.key == s.type } }

    /** Osoba spoza firmy z kartoteki: kontrahent albo (rekrutacja) kandydat. */
    private fun needsClient(): Boolean = typeDef()?.let { it.needsContractor || it.needsCandidate } == true

    private fun clientKind(): String = if (typeDef()?.needsCandidate == true) "candidate" else "contractor"

    private var contractorJob: Job? = null

    fun setContractorQuery(q: String) {
        _state.update { it.copy(contractorQuery = q) }
        searchContractors()
    }

    private fun searchContractors() {
        contractorJob?.cancel()
        contractorJob = viewModelScope.launch {
            delay(300)
            val list = runCatching { repository.searchContractors(_state.value.contractorQuery.trim(), clientKind()) }
                .getOrDefault(emptyList())
            _state.update { it.copy(contractorResults = list) }
        }
    }

    fun pickContractor(c: MeetingContractorDto?) {
        _state.update { it.copy(contractor = c, isAddingContractor = false, contractorError = null) }
        if (c == null) searchContractors()
    }

    fun setAddingContractor(on: Boolean) = _state.update { it.copy(isAddingContractor = on, contractorError = null) }

    /** Szybkie dodanie — nowy wpis kartoteki w grupie „Inne" (albo „Kandydaci" przy rekrutacji). */
    fun createContractor(request: MeetingContractorCreateRequest) {
        val kind = clientKind()
        viewModelScope.launch {
            runCatching { repository.createContractor(request.copy(kind = kind)) }
                .onSuccess { c -> pickContractor(c) }
                .onFailure { e ->
                    val what = if (kind == "candidate") "kandydata" else "kontrahenta"
                    _state.update { it.copy(contractorError = crmErrorMessage(e, "Nie udało się dodać $what")) }
                }
        }
    }

    fun clearType() = _state.update { it.copy(type = null) }
    fun setTitle(v: String) = _state.update { it.copy(title = v) }
    fun setLocation(v: String) = _state.update { it.copy(location = v) }
    fun setHost(id: String) { _state.update { it.copy(hostId = id, participantIds = it.participantIds - id) }; checkConflicts() }
    /** Zmiana początku zachowuje długość dnia — koniec liczy się z początku i durationMin. */
    fun setStart(ms: Long) { _state.update { it.copy(startAtMs = ms) }; checkConflicts() }

    /** Gotowy preset: zawsze jeden dzień. */
    fun setDuration(min: Int) {
        _state.update { it.copy(durationMin = min, customDuration = false, dayCount = 1) }
        checkConflicts()
    }

    fun setCustomDuration() = _state.update { it.copy(customDuration = true) }

    fun setDayCount(n: Int) {
        _state.update { it.copy(dayCount = n.coerceIn(1, 14)) }
        checkConflicts()
    }

    /** „Do" w trybie własnym: durationMin = do − od (ta sama godzina każdego dnia). */
    fun setEndClock(hour: Int, minute: Int) {
        val from = minuteOfDay(_state.value.startAtMs)
        val len = hour * 60 + minute - from
        if (len < 5) {
            _state.update { it.copy(error = "Koniec dnia musi być co najmniej 5 min po początku (${formatClock(from)}).") }
            return
        }
        _state.update { it.copy(durationMin = len, error = null) }
        checkConflicts()
    }

    fun toggleParticipant(id: String) {
        _state.update { s -> s.copy(participantIds = if (id in s.participantIds) s.participantIds - id else s.participantIds + id) }
        checkConflicts()
    }
    fun setAgendaText(key: String, text: String) =
        _state.update { s -> s.copy(agenda = s.agenda.map { if (it.key == key) it.copy(text = text) else it }) }
    fun setAgendaMinutes(key: String, v: String) {
        val digits = v.filter(Char::isDigit).take(4)
        _state.update { s -> s.copy(agenda = s.agenda.map { if (it.key == key) it.copy(minutes = digits) else it }) }
    }
    fun addAgenda() = _state.update { it.copy(agenda = it.agenda + AgendaDraft(text = "")) }
    fun removeAgenda(key: String) = _state.update { s -> s.copy(agenda = s.agenda.filterNot { it.key == key }) }
    fun moveAgendaUp(key: String) = _state.update { s ->
        val i = s.agenda.indexOfFirst { it.key == key }
        if (i <= 0) s else s.copy(agenda = s.agenda.toMutableList().apply { add(i - 1, removeAt(i)) })
    }
    fun clearError() = _state.update { it.copy(error = null) }

    // ── D15: dyktowanie i propozycja agendy ──

    fun setBrief(v: String) = _state.update { it.copy(brief = v, proposalError = null) }

    /** Wołać po przyznaniu RECORD_AUDIO. */
    fun startDictation() {
        if (_state.value.dictation != DictationPhase.IDLE) return
        if (meetingRecorder.isActive) {
            _state.update { it.copy(dictationError = "Telefon nagrywa właśnie spotkanie — mikrofon jest zajęty.") }
            return
        }
        try {
            dictationRecorder.start()
        } catch (e: Exception) {
            _state.update { it.copy(dictationError = "Nie udało się włączyć mikrofonu.") }
            return
        }
        _state.update { it.copy(dictation = DictationPhase.RECORDING, dictationSec = 0, dictationError = null) }
        dictationTimer?.cancel()
        dictationTimer = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                val sec = _state.value.dictationSec + 1
                _state.update { it.copy(dictationSec = sec) }
                // Krótka wypowiedź, nie spotkanie — po 5 min wysyłamy to, co jest.
                if (sec >= DICTATION_MAX_SEC) {
                    stopDictation()
                    break
                }
            }
        }
    }

    fun stopDictation() {
        if (_state.value.dictation != DictationPhase.RECORDING) return
        dictationTimer?.cancel()
        val file = dictationRecorder.stop()
        if (file == null) {
            _state.update { it.copy(dictation = DictationPhase.IDLE, dictationError = "Nic się nie nagrało — spróbuj jeszcze raz.") }
            return
        }
        _state.update { it.copy(dictation = DictationPhase.UPLOADING) }
        viewModelScope.launch {
            runCatching { repository.dictation(file) }
                .onSuccess { text ->
                    _state.update { s ->
                        val t = text.trim()
                        s.copy(
                            dictation = DictationPhase.IDLE,
                            // Dopisujemy, nie nadpisujemy — można dyktować na raty.
                            brief = when {
                                t.isEmpty() -> s.brief
                                s.brief.isBlank() -> t
                                else -> s.brief.trimEnd() + "\n" + t
                            },
                            dictationError = if (t.isEmpty()) "Nie rozpoznano mowy — wpisz ręcznie." else null,
                        )
                    }
                    // Po dyktowaniu od razu agenda z czasami, bez osobnego dotknięcia;
                    // tekst zostaje w polu do poprawki i ponownej propozycji.
                    if (text.isNotBlank()) proposeAgenda()
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(
                            dictation = DictationPhase.IDLE,
                            dictationError = if ((e as? HttpException)?.code() == 503) {
                                "Transkrypcja chwilowo niedostępna — wpisz ręcznie"
                            } else {
                                crmErrorMessage(e, "Nie udało się spisać nagrania")
                            },
                        )
                    }
                }
            file.delete()
        }
    }

    fun proposeAgenda() {
        val s = _state.value
        val type = s.type ?: return
        if (s.brief.isBlank() || s.isProposing) return
        _state.update { it.copy(isProposing = true, proposalError = null) }
        viewModelScope.launch {
            runCatching {
                repository.proposeAgenda(
                    MeetingAgendaProposalRequest(
                        type = type,
                        title = s.title.trim().ifBlank { null },
                        text = s.brief.trim().take(8000),
                        totalMin = s.plannedMin.coerceIn(5, 20160),
                        dayCount = s.dayCount,
                    ),
                )
            }.onSuccess { items ->
                val clean = items.filter { it.text.isNotBlank() }
                when {
                    clean.isEmpty() ->
                        _state.update { it.copy(isProposing = false, proposalError = "AI nie zaproponowało żadnego punktu.") }
                    _state.value.agenda.any { it.text.isNotBlank() } ->
                        _state.update { it.copy(isProposing = false, pendingProposal = clean) }
                    else -> {
                        _state.update { it.copy(isProposing = false) }
                        applyProposal(clean)
                    }
                }
            }.onFailure { e ->
                _state.update {
                    it.copy(
                        isProposing = false,
                        proposalError = if ((e as? HttpException)?.code() == 503) {
                            "Propozycje AI są wyłączone — rozpisz agendę ręcznie."
                        } else {
                            crmErrorMessage(e, "Nie udało się zaproponować agendy")
                        },
                    )
                }
            }
        }
    }

    fun confirmProposal() {
        _state.value.pendingProposal?.let { applyProposal(it) }
    }

    fun dismissProposal() = _state.update { it.copy(pendingProposal = null) }

    private fun applyProposal(items: List<MeetingAgendaProposalItemDto>) = _state.update { s ->
        s.copy(
            agenda = items.map {
                AgendaDraft(text = it.text.trim(), minutes = it.durationMin.takeIf { m -> m > 0 }?.toString().orEmpty())
            },
            pendingProposal = null,
        )
    }

    override fun onCleared() {
        dictationTimer?.cancel()
        dictationRecorder.cancel()
        super.onCleared()
    }

    /** D4: kolizje tylko ostrzegają. v2: serwer sprawdza każdy z dni. */
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
                    s.dayCount,
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
                durationMin = s.durationMin.coerceIn(5, 1440),
                dayCount = s.dayCount.coerceIn(1, 14),
                location = s.location.trim().ifBlank { null },
                clientId = if (needsClient()) s.contractor?.id else null,
                agenda = s.agenda.filter { it.text.isNotBlank() }.map {
                    MeetingAgendaInput(
                        it.id,
                        it.text.trim(),
                        it.minutes.toIntOrNull()?.takeIf { m -> m > 0 }?.coerceAtMost(1440),
                    )
                },
            )
            runCatching { if (editId != null) repository.update(editId, request) else repository.create(request) }
                .onSuccess { m -> _state.update { it.copy(isSaving = false, savedId = m.id) } }
                .onFailure { e -> _state.update { it.copy(isSaving = false, error = crmErrorMessage(e, "Nie udało się zapisać spotkania")) } }
        }
    }

    private companion object {
        const val DICTATION_MAX_SEC = 300

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
        // D9: podsumowanie nagrane głosem
        /** Okno otwarte z przycisku, zanim stało się wymagane. */
        val voiceOpen: Boolean = false,
        /** Rekrutacja: prowadzący zaznaczył zgodę kandydata na nagranie. */
        val consent: Boolean = false,
        val voice: VoiceDraft = VoiceDraft(),
        /** Podsumowanie czeka w kolejce na sieć — okno się nie pokazuje. */
        val voiceQueued: Boolean = false,
        /** Ostatni punkt agendy odhaczony w trakcie nagrania — ekran pyta o „Zakończ". */
        val askFinish: Boolean = false,
    )

    data class VoiceDraft(
        val recording: Boolean = false,
        val file: File? = null,
        val durationSec: Int = 0,
        val sending: Boolean = false,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    val recorderState: StateFlow<MeetingRecorder.State> = recorder.state
    private val voiceRecorder = VoiceSummaryRecorder(context)

    init {
        load()
        refreshVoiceQueued()
        viewModelScope.launch {
            runCatching { repository.meta() }.onSuccess { meta -> _state.update { it.copy(meta = meta) } }
        }
        // Odpytywanie w trakcie i przy przetwarzaniu — stan zmienia też panel i serwer.
        viewModelScope.launch {
            while (isActive) {
                delay(5_000)
                val status = _state.value.meeting?.status
                if (status in setOf("live", "paused", "processing")) load(silent = true)
                // Podsumowanie w kolejce na sieć — po wysyłce okno znów zależy od serwera.
                if (_state.value.voiceQueued) { refreshVoiceQueued(); load(silent = true) }
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
            val started = repository.start(m.id, consent = _state.value.consent)
            MeetingRecordingService.start(context, m.id, m.title, m.currentDay)
            started
        }
    }

    fun setConsent(on: Boolean) = _state.update { it.copy(consent = on) }

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

    // ── D9: podsumowanie nagrane głosem ──

    fun openVoice() = _state.update { it.copy(voiceOpen = true) }

    fun closeVoice() {
        voiceRecorder.cancel()
        _state.update { it.copy(voiceOpen = false, voice = VoiceDraft()) }
    }

    fun voiceElapsedMs(): Long = voiceRecorder.elapsedMs()

    /** Wołać po przyznaniu mikrofonu. Poprzednie, niewysłane nagranie idzie do kosza. */
    fun startVoice() {
        if (recorder.isActive) {
            _state.update { it.copy(voice = VoiceDraft(error = "Na tym telefonie trwa nagrywanie spotkania — najpierw je zakończ.")) }
            return
        }
        _state.value.voice.file?.delete()
        try {
            voiceRecorder.start(id)
            _state.update { it.copy(voice = VoiceDraft(recording = true)) }
        } catch (e: Exception) {
            _state.update { it.copy(voice = VoiceDraft(error = "Nie udało się włączyć mikrofonu: ${e.message ?: "błąd"}")) }
        }
    }

    fun stopVoice() {
        val done = voiceRecorder.stop()
        _state.update {
            it.copy(
                voice = if (done == null) VoiceDraft(error = "Nic się nie nagrało — spróbuj jeszcze raz.")
                else VoiceDraft(file = done.first, durationSec = done.second),
            )
        }
    }

    /**
     * Wysyłka od razu, żeby prowadzący zobaczył wynik. Bez sieci plik idzie do
     * kolejki workera (jak nagranie spotkania) i okno znika do czasu wysyłki.
     */
    fun sendVoice() {
        val v = _state.value.voice
        val file = v.file ?: return
        val m = _state.value.meeting ?: return
        _state.update { it.copy(voice = v.copy(sending = true, error = null)) }
        viewModelScope.launch {
            try {
                val updated = repository.uploadRecording(id, file, v.durationSec, KIND_SUMMARY)
                file.delete()
                _state.update { it.copy(voiceOpen = false, voice = VoiceDraft(), message = "Podsumowanie wysłane — za chwilę pojawią się propozycje zadań.") }
                apply(updated)
            } catch (e: HttpException) {
                _state.update { it.copy(voice = v.copy(sending = false, error = crmErrorMessage(e, "Serwer odrzucił podsumowanie"))) }
            } catch (e: Exception) {
                VoiceSummaryRecorder.deleteOthers(context, id, keep = file)
                MeetingRecordingUploadWorker.enqueue(context, id, file, v.durationSec, m.title, KIND_SUMMARY)
                _state.update {
                    it.copy(
                        voiceOpen = false,
                        voice = VoiceDraft(),
                        voiceQueued = true,
                        message = "Brak sieci — podsumowanie wyśle się samo, gdy wróci zasięg.",
                    )
                }
            }
        }
    }

    private fun refreshVoiceQueued() {
        viewModelScope.launch {
            val queued = withContext(Dispatchers.IO) {
                runCatching {
                    WorkManager.getInstance(context)
                        .getWorkInfosForUniqueWork(MeetingRecordingUploadWorker.summaryWorkName(id))
                        .get()
                        .any { !it.state.isFinished }
                }.getOrDefault(false)
            }
            _state.update { it.copy(voiceQueued = queued) }
        }
    }

    override fun onCleared() {
        voiceRecorder.cancel()
        super.onCleared()
    }

    fun retry() = run("Nie udało się ponowić transkrypcji") { repository.command(id, "retry") }
    fun rsvp(response: String) = run("Nie udało się zapisać odpowiedzi") { repository.rsvp(id, response) }

    fun toggleAgenda(itemId: String, done: Boolean) {
        _state.update { s ->
            val m = s.meeting ?: return@update s
            s.copy(meeting = m.copy(agenda = m.agenda.map { if (it.id == itemId) it.copy(done = done) else it }))
        }
        viewModelScope.launch {
            runCatching { repository.toggleAgenda(id, itemId, done) }
                .onSuccess { m ->
                    apply(m)
                    val allDone = m.agenda.isNotEmpty() && m.agenda.all { it.done }
                    if (done && allDone && m.canControl && (m.status == "live" || m.status == "paused")) {
                        _state.update { it.copy(askFinish = true) }
                    }
                }
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
    fun clearAskFinish() = _state.update { it.copy(askFinish = false) }
    fun clearError() = _state.update { it.copy(error = null) }
}
