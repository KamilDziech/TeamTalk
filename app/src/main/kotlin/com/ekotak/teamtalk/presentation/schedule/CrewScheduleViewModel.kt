package com.ekotak.teamtalk.presentation.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.domain.model.Change
import com.ekotak.teamtalk.domain.model.CrewSchedule
import com.ekotak.teamtalk.domain.model.ScheduleBacklogItem
import com.ekotak.teamtalk.domain.model.ScheduleCalendar
import com.ekotak.teamtalk.domain.model.ScheduleStage
import com.ekotak.teamtalk.domain.model.ScheduleStageStatus
import com.ekotak.teamtalk.domain.model.StageAssignee
import com.ekotak.teamtalk.domain.model.StagePatch
import com.ekotak.teamtalk.domain.model.etapy
import com.ekotak.teamtalk.domain.model.mondayOf
import com.ekotak.teamtalk.domain.repository.CrewScheduleRepository
import com.ekotak.teamtalk.domain.repository.ScheduleCallResult
import com.ekotak.teamtalk.domain.repository.ScheduleSaveResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * HARMONOGRAM EKIP — kafelek „Harmonogram", 1:1 z `/app/schedule` panelu.
 *
 * Kopia robocza jak w panelu: ruch paska widać od razu, a serwer przelicza
 * ostrzeżenia i odsyła prawdę (przeładowanie po zapisie). Odmowa serwera
 * cofa zmianę i mówi dlaczego; brak zasięgu zostawia ją na osi z plakietką
 * „czeka na wysłanie".
 */
@HiltViewModel
class CrewScheduleViewModel @Inject constructor(
    private val repository: CrewScheduleRepository,
) : ViewModel() {

    /** Potwierdzenie, o które panel pyta przez `confirm()`. */
    sealed interface Confirm {
        val message: String

        data class LockedMove(val stage: ScheduleStage, val start: LocalDate, val crewId: String?) : Confirm {
            override val message = "Termin uzgodniony z klientem. Przesunąć mimo to?"
        }

        data object EnablePublishing : Confirm {
            override val message =
                "Od teraz ekipy zobaczą montaż dopiero po „Opublikuj tydzień”. Nieopublikowane " +
                    "montaże znikną im z listy i z kalendarza. Włączyć?"
        }
    }

    data class UiState(
        val today: LocalDate = LocalDate.now(),
        val from: LocalDate = mondayOf(LocalDate.now()),
        val zoom: Int = 7,
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val schedule: CrewSchedule? = null,
        val forbidden: Boolean = false,
        val fromCache: Boolean = false,
        val error: String? = null,
        val pendingCount: Int = 0,
        val saving: Boolean = false,
        /** Rozwinięte ekipy (składy imienne) i pula „Wolni monterzy" (`__pool`). */
        val expanded: Set<String> = emptySet(),
        val selectedId: String? = null,
        /** Pozycja „Do zaplanowania" otwarta w arkuszu wyboru ekipy i dnia. */
        val planItemId: String? = null,
        val planningDealId: String? = null,
        val confirm: Confirm? = null,
    ) {
        val to: LocalDate get() = from.plusDays(zoom - 1L)
        val selected: ScheduleStage? get() = schedule?.stages?.firstOrNull { it.id == selectedId }
        val planItem: ScheduleBacklogItem? get() = schedule?.backlog?.firstOrNull { it.id == planItemId }
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _toasts = Channel<String>(Channel.BUFFERED)
    val toasts = _toasts.receiveAsFlow()

    init {
        load(initial = true)
    }

    // ── Okno osi ──────────────────────────────────────────────────────────────

    fun setZoom(zoom: Int) {
        _uiState.update { it.copy(zoom = zoom) }
        load()
    }

    fun previous() = step(-1)
    fun next() = step(1)

    private fun step(dir: Int) {
        val days = if (_uiState.value.zoom == 35) 28L else 7L
        _uiState.update { it.copy(from = it.from.plusDays(dir * days)) }
        load()
    }

    fun goToday() {
        _uiState.update { it.copy(from = mondayOf(it.today)) }
        load()
    }

    fun refresh() = load(refreshing = true)

    private fun load(initial: Boolean = false, refreshing: Boolean = false, quiet: Boolean = false) {
        val s = _uiState.value
        viewModelScope.launch {
            if (!quiet) _uiState.update { it.copy(isLoading = initial || it.schedule == null, isRefreshing = refreshing) }
            val snap = repository.load(s.from, s.to)
            _uiState.update { cur ->
                // Odpowiedź na stare okno (ktoś stuknął „›" dwa razy) nie nadpisuje nowego.
                if (cur.from != s.from || cur.zoom != s.zoom) return@update cur.copy(isRefreshing = false)
                cur.copy(
                    isLoading = false,
                    isRefreshing = false,
                    schedule = snap.schedule,
                    forbidden = snap.forbidden,
                    fromCache = snap.fromCache,
                    error = snap.error,
                    pendingCount = snap.pendingCount,
                )
            }
        }
    }

    fun toggle(id: String) = _uiState.update {
        it.copy(expanded = if (id in it.expanded) it.expanded - id else it.expanded + id)
    }

    fun select(id: String?) = _uiState.update { it.copy(selectedId = id) }
    fun openPlan(id: String?) = _uiState.update { it.copy(planItemId = id) }
    fun dismissConfirm() = _uiState.update { it.copy(confirm = null) }

    fun acceptConfirm() {
        when (val c = _uiState.value.confirm) {
            is Confirm.LockedMove -> moveStage(c.stage, c.start, c.crewId, confirmed = true)
            Confirm.EnablePublishing -> setPublishing(true)
            null -> Unit
        }
        _uiState.update { it.copy(confirm = null) }
    }

    // ── Zapis etapu ───────────────────────────────────────────────────────────

    private fun calendar(): ScheduleCalendar = ScheduleCalendar(_uiState.value.schedule?.days.orEmpty())

    private fun replaceStage(id: String, local: (ScheduleStage) -> ScheduleStage) = _uiState.update { st ->
        val sch = st.schedule ?: return@update st
        st.copy(schedule = sch.copy(stages = sch.stages.map { if (it.id == id) local(it) else it }))
    }

    /** Zapis z podmianą lokalną; odmowa cofa oś do stanu sprzed zmiany. */
    private fun save(stage: ScheduleStage, patch: StagePatch, local: (ScheduleStage) -> ScheduleStage, note: String?) {
        val before = _uiState.value.schedule
        replaceStage(stage.id) { local(it).copy(pending = true) }
        persist(stage.dealId, stage.id, patch, before, note)
    }

    private fun persist(dealId: String, id: String, patch: StagePatch, before: CrewSchedule?, note: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true) }
            val res = repository.patchStage(dealId, id, patch)
            _uiState.update { it.copy(saving = false) }
            when (res) {
                ScheduleSaveResult.Sent -> note?.let { _toasts.send(it) }
                ScheduleSaveResult.Queued ->
                    _toasts.send("Bez zasięgu — zmiana czeka w telefonie i pójdzie sama.")
                is ScheduleSaveResult.Failed -> {
                    _uiState.update { it.copy(schedule = before) }
                    _toasts.send(res.message)
                    return@launch
                }
            }
            load(quiet = true)
        }
    }

    fun moveStage(stage: ScheduleStage, start: LocalDate, crewId: String?, confirmed: Boolean = false) {
        val cal = calendar()
        val from = cal.nextWork(start)
        val crewChanged = crewId != stage.crewId
        if (from == stage.scheduledAt && !crewChanged) return
        if (stage.locked && from != stage.scheduledAt && !confirmed) {
            _uiState.update { it.copy(confirm = Confirm.LockedMove(stage, start, crewId)) }
            return
        }
        val crew = crewId?.let { id -> _uiState.value.schedule?.crews?.firstOrNull { it.id == id } }
        val assignees = if (crewChanged && crew != null) {
            if (crew.external) emptyList() else crew.memberIds.map { StageAssignee(it, null) }
        } else {
            null
        }
        val patch = StagePatch(
            scheduledAt = from,
            crew = if (crewChanged) Change(crewId) else null,
            assignees = if (crewChanged) assignees else null,
        )
        save(
            stage,
            patch,
            local = { old ->
                old.copy(
                    scheduledAt = from,
                    endDate = cal.endOf(from, old.durationDays),
                    crewId = if (crewChanged) crewId else old.crewId,
                    assignees = assignees ?: old.assignees,
                )
            },
            note = if (crewChanged) {
                "${stage.clientName}: ${crew?.name ?: "bez ekipy"}, od ${dm(from)}"
            } else {
                "${stage.clientName}: start ${dm(from)}"
            },
        )
    }

    fun resizeStage(stage: ScheduleStage, deltaDays: Int) {
        val cal = calendar()
        val newEnd = maxOf(stage.scheduledAt, stage.endDate.plusDays(deltaDays.toLong()))
        val duration = cal.workdaysBetween(stage.scheduledAt, newEnd)
        if (duration == stage.durationDays) return
        save(
            stage,
            StagePatch(durationDays = duration),
            local = { it.copy(durationDays = duration, endDate = cal.endOf(it.scheduledAt, duration)) },
            note = "${stage.clientName}: $duration dni roboczych",
        )
    }

    /** Przesunięcie startu o dzień roboczy (strzałki w arkuszu). */
    fun shiftStage(id: String, days: Int) {
        val stage = _uiState.value.schedule?.stages?.firstOrNull { it.id == id } ?: return
        val target = calendar().nextWork(stage.scheduledAt.plusDays(days.toLong()), if (days < 0) -1 else 1)
        moveStage(stage, target, stage.crewId)
    }

    /** Zmiana z arkusza etapu — długość, ekipa, skład, przerwa, blokada. */
    fun patchStage(id: String, patch: StagePatch) {
        val stage = _uiState.value.schedule?.stages?.firstOrNull { it.id == id } ?: return
        val cal = calendar()
        save(
            stage,
            patch,
            local = { old ->
                val duration = patch.durationDays ?: old.durationDays
                old.copy(
                    durationDays = duration,
                    endDate = if (patch.durationDays != null) cal.endOf(old.scheduledAt, duration) else old.endDate,
                    crewId = if (patch.crew != null) patch.crew.value else old.crewId,
                    assignees = patch.assignees ?: old.assignees,
                    minGapDays = if (patch.minGapDays != null) patch.minGapDays.value else old.minGapDays,
                    gapLabel = if (patch.gapLabel != null) patch.gapLabel.value else old.gapLabel,
                    locked = patch.locked ?: old.locked,
                )
            },
            note = null,
        )
    }

    /** Wybór ekipy wypełnia skład jej stałymi członkami (ekipa zewnętrzna — pusty). */
    fun pickCrew(id: String, crewId: String?) {
        val crew = crewId?.let { c -> _uiState.value.schedule?.crews?.firstOrNull { it.id == c } }
        patchStage(
            id,
            StagePatch(
                crew = Change(crewId),
                assignees = if (crew != null && !crew.external) crew.memberIds.map { StageAssignee(it, null) } else emptyList(),
            ),
        )
    }

    /** Wrzucenie pozycji z „Do zaplanowania" na ekipę i dzień. */
    fun dropFromBacklog(item: ScheduleBacklogItem, crewId: String?, day: LocalDate) {
        val sch = _uiState.value.schedule ?: return
        val cal = calendar()
        val crew = crewId?.let { c -> sch.crews.firstOrNull { it.id == c } }
        val start = cal.nextWork(day)
        val assignees = if (crew != null && !crew.external) crew.memberIds.map { StageAssignee(it, null) } else emptyList()
        val before = sch
        val staged = ScheduleStage(
            id = item.id,
            dealId = item.dealId,
            clientName = item.clientName,
            city = item.city,
            title = item.title,
            status = if (item.status == ScheduleStageStatus.RESERVED) ScheduleStageStatus.PLANNED else item.status,
            scheduledAt = start,
            endDate = cal.endOf(start, item.durationDays),
            durationDays = item.durationDays,
            crewId = crewId,
            assignees = assignees,
            requiredRoles = emptyList(),
            minGapDays = null,
            gapLabel = null,
            locked = false,
            stageNo = 1,
            stageCount = 1,
            draft = sch.settings.publishEnabled,
            published = null,
            warnings = emptyList(),
            pending = true,
        )
        _uiState.update {
            it.copy(
                planItemId = null,
                schedule = sch.copy(
                    backlog = sch.backlog.filterNot { b -> b.id == item.id },
                    stages = if (crewId != null || assignees.isNotEmpty()) sch.stages + staged else sch.stages,
                ),
            )
        }
        persist(
            item.dealId,
            item.id,
            StagePatch(
                scheduledAt = start,
                crew = Change(crewId),
                assignees = assignees,
                fromReservation = item.status == ScheduleStageStatus.RESERVED,
            ),
            before,
            "${item.clientName}: ${crew?.name ?: "bez ekipy"}, od ${dm(start)}",
        )
    }

    /** Zdjęcie montażu z osi: wraca na listę „Do zaplanowania" (bez ekipy i obsady). */
    fun takeOff(id: String) {
        val sch = _uiState.value.schedule ?: return
        val stage = sch.stages.firstOrNull { it.id == id } ?: return
        _uiState.update {
            it.copy(selectedId = null, schedule = sch.copy(stages = sch.stages.filterNot { s -> s.id == id }))
        }
        persist(
            stage.dealId,
            id,
            StagePatch(crew = Change(null), assignees = emptyList()),
            sch,
            "${stage.clientName} wrócił na listę „Do zaplanowania”.",
        )
    }

    // ── Decyzje wyłącznie w zasięgu ───────────────────────────────────────────

    /** „Zaplanuj": po jednym etapie na instalację z zakresu deala. */
    fun planDeal(dealId: String, clientName: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(planningDealId = dealId, saving = true) }
            val res = repository.planDeal(dealId)
            _uiState.update { it.copy(planningDealId = null, saving = false) }
            when (res) {
                is ScheduleCallResult.Ok -> {
                    _toasts.send(
                        if (res.value > 0) "$clientName: ${etapy(res.value)} do rozstawienia na osi."
                        else "$clientName ma już montaż.",
                    )
                    load(quiet = true)
                }
                ScheduleCallResult.Offline -> _toasts.send("„Zaplanuj” wymaga zasięgu.")
                is ScheduleCallResult.Failed -> _toasts.send(res.message)
            }
        }
    }

    /** Publikacja idzie tydzień po tygodniu — każdy widoczny tydzień ze szkicem. */
    fun publish() {
        val s = _uiState.value
        val sch = s.schedule ?: return
        val weeks = draftWeeks(sch)
        if (weeks.isEmpty()) return
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true) }
            var published = 0
            var notified = 0
            for (w in weeks) {
                when (val res = repository.publishWeek(w)) {
                    is ScheduleCallResult.Ok -> {
                        published += res.value.published
                        notified += res.value.notified
                    }
                    ScheduleCallResult.Offline -> {
                        _uiState.update { it.copy(saving = false) }
                        _toasts.send("Publikacja wymaga zasięgu.")
                        return@launch
                    }
                    is ScheduleCallResult.Failed -> {
                        _uiState.update { it.copy(saving = false) }
                        _toasts.send(res.message)
                        return@launch
                    }
                }
            }
            _uiState.update { it.copy(saving = false) }
            _toasts.send(
                if (published == 0) "Nie było czego publikować."
                else "Opublikowano $published ${if (published == 1) "montaż" else "montaże"}. Powiadomienia: $notified.",
            )
            load(quiet = true)
        }
    }

    fun togglePublishing() {
        val on = _uiState.value.schedule?.settings?.publishEnabled != true
        if (on) _uiState.update { it.copy(confirm = Confirm.EnablePublishing) } else setPublishing(false)
    }

    private fun setPublishing(on: Boolean) {
        viewModelScope.launch {
            _uiState.update { it.copy(saving = true) }
            val res = repository.setPublishEnabled(on)
            _uiState.update { it.copy(saving = false) }
            when (res) {
                is ScheduleCallResult.Ok -> {
                    _toasts.send(
                        if (on) "Publikacja tygodni włączona."
                        else "Publikacja wyłączona — ekipy widzą zmiany od razu.",
                    )
                    load(quiet = true)
                }
                ScheduleCallResult.Offline -> _toasts.send("Zmiana ustawienia wymaga zasięgu.")
                is ScheduleCallResult.Failed -> _toasts.send(res.message)
            }
        }
    }
}
