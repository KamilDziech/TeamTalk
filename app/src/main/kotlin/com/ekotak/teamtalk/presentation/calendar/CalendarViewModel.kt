package com.ekotak.teamtalk.presentation.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.data.local.preferences.SessionPreferences
import com.ekotak.teamtalk.domain.model.Calendar
import com.ekotak.teamtalk.domain.model.CalendarConflictException
import com.ekotak.teamtalk.domain.model.PrivateBusy
import com.ekotak.teamtalk.domain.model.PrivateBusyConflictException
import com.ekotak.teamtalk.domain.model.CalendarDraft
import com.ekotak.teamtalk.domain.model.CalendarEvent
import com.ekotak.teamtalk.domain.model.CalendarEventDraft
import com.ekotak.teamtalk.domain.model.CalendarEventPatch
import com.ekotak.teamtalk.domain.model.CalendarOverlay
import com.ekotak.teamtalk.domain.model.CalendarPatch
import com.ekotak.teamtalk.domain.model.CalendarType
import com.ekotak.teamtalk.domain.model.Edit
import com.ekotak.teamtalk.domain.model.OverlaySource
import com.ekotak.teamtalk.domain.model.RecurFreq
import com.ekotak.teamtalk.domain.model.Recurrence
import com.ekotak.teamtalk.domain.model.RecurrenceScope
import com.ekotak.teamtalk.domain.model.RsvpStatus
import com.ekotak.teamtalk.domain.model.TaskMember
import com.ekotak.teamtalk.domain.repository.CalendarRepository
import com.ekotak.teamtalk.domain.repository.CalendarSnapshot
import com.ekotak.teamtalk.presentation.crm.crmErrorMessage
import com.ekotak.teamtalk.presentation.crm.parseIsoMillis
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import javax.inject.Inject

/**
 * Moduł Kalendarz — mobilny odpowiednik `CalendarView.tsx`.
 *
 * Cztery widoki panelu, warstwy kalendarzy, nakładki operacyjne i filtr osoby.
 * Dane przychodzą jedną migawką z repozytorium (Room), a filtrowanie robimy
 * lokalnie — przełączanie warstw i zakładek nie kosztuje okrążenia po sieci.
 * Zmiana zakresu (miesiąc w przód, inny widok) dociąga brakujące wydarzenia,
 * ale rysujemy od razu to, co jest w cache.
 */
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val repository: CalendarRepository,
    private val sessionPreferences: SessionPreferences,
) : ViewModel() {

    /** Formularz wydarzenia — jeden na podgląd, edycję i tworzenie, jak w panelu. */
    data class EventForm(
        val id: String? = null,
        val calendarId: String = "",
        val title: String = "",
        val date: LocalDate = LocalDate.now(),
        val endDate: LocalDate = LocalDate.now(),
        val allDay: Boolean = false,
        val startTime: LocalTime = LocalTime.of(9, 0),
        val endTime: LocalTime = LocalTime.of(10, 0),
        val assigneeId: String? = null,
        val attendeeIds: List<String> = emptyList(),
        val location: String = "",
        val color: String? = null,
        val description: String = "",
        val recurFreq: RecurFreq? = null,
        val recurInterval: Int = 1,
        val recurCount: Int? = null,
        val recurUntil: LocalDate? = null,
        /** Niepuste = edytujemy wystąpienie serii; wtedy widać wybór zakresu. */
        val recurGroupId: String? = null,
        val scope: RecurrenceScope = RecurrenceScope.THIS,
        val attendeeResponses: Map<String, RsvpStatus> = emptyMap(),
        /** Ustawione po odmowie 409: następny zapis wymusza mimo kolizji. */
        val conflictForce: Boolean = false,
        val saving: Boolean = false,
        val readOnly: Boolean = false,
        val pendingSync: Boolean = false,
    ) {
        val isNew: Boolean get() = id == null
    }

    data class UiState(
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val error: String? = null,
        val message: String? = null,
        val view: CalendarViewKind = CalendarViewKind.MONTH,
        /** Dzień, wokół którego liczymy zakres widoku. */
        val anchor: LocalDate = LocalDate.now(),
        /** Dzień rozwinięty pod siatką miesiąca; `null` = nic nie wybrano. */
        val selectedDay: LocalDate? = LocalDate.now(),
        val calendars: List<Calendar> = emptyList(),
        val hiddenLayers: Set<String> = emptySet(),
        val overlaysOn: Set<OverlaySource> = emptySet(),
        val overlays: List<CalendarOverlay> = emptyList(),
        val members: List<TaskMember> = emptyList(),
        val assigneeFilter: String? = null,
        val events: List<CalendarEvent> = emptyList(),
        val currentUserId: String? = null,
        /** Szare pola „Zajęte" z prywatnych kalendarzy zespołu (bez treści wpisów). */
        val busy: List<PrivateBusy> = emptyList(),
        /** Przełącznik warstwy szarych pól — jak nakładki operacyjne. */
        val busyOn: Boolean = true,
        /** Czy wolno mi zaplanować mimo cudzej prywatnej zajętości. */
        val canOverrideBusy: Boolean = false,
        val form: EventForm? = null,
        /**
         * Wydarzenie z uzbrojonymi kropkami w siatce — tylko ono ma uchwyty
         * do rozciągania. `null` = nikt niczego nie ciągnie.
         */
        val resizeEventId: String? = null,
        val syncedAt: Long? = null,
        val now: Long = System.currentTimeMillis(),
    ) {
        /** Kalendarze widoczne na liście warstw — archiwalnych panel też nie pokazuje. */
        val activeCalendars: List<Calendar> get() = calendars.filter { !it.isArchived }

        val writableCalendars: List<Calendar> get() = activeCalendars.filter { it.canWrite }

        /** Kalendarz domyślny nowego wydarzenia: osobisty, a jak nie — pierwszy z zapisem. */
        val defaultCalendarId: String?
            get() = (writableCalendars.firstOrNull { it.type == CalendarType.PERSONAL }
                ?: writableCalendars.firstOrNull())?.id

        fun calendarOf(id: String): Calendar? = calendars.firstOrNull { it.id == id }

        val visibleLayerCount: Int get() = activeCalendars.count { it.id !in hiddenLayers }

        /** Wydarzenia po filtrach warstw i osoby — to, co rysuje widok. */
        val visibleEvents: List<CalendarEvent>
            get() = events.filter { event ->
                event.calendarId !in hiddenLayers &&
                    (assigneeFilter == null || event.assigneeId == assigneeFilter)
            }

        /**
         * Zajętość rysowana na ekranie. Bez filtra osoby pokazujemy WŁASNĄ —
         * inaczej dzień zamieniłby się w szary mur zajętości całej firmy.
         * Kto planuje komuś, wybiera go filtrem i widzi jego szare pola.
         */
        val visibleBusy: List<PrivateBusy>
            get() = if (!busyOn) emptyList() else {
                val who = assigneeFilter ?: currentUserId
                busy.filter { it.userId == who }
            }

        val visibleOverlays: List<CalendarOverlay>
            get() = overlays.filter { it.source in overlaysOn }
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** Zakres, dla którego ostatnio pytaliśmy serwer — nie pytamy o to samo dwa razy. */
    private var loadedRange: Pair<Long, Long>? = null

    init {
        observe()
        restorePreferences()
    }

    private fun observe() {
        viewModelScope.launch {
            _uiState.update { it.copy(currentUserId = sessionPreferences.session.first()?.userId) }
        }
        viewModelScope.launch {
            repository.observe().collect { snapshot -> apply(snapshot) }
        }
    }

    private fun apply(snapshot: CalendarSnapshot) {
        _uiState.update { state ->
            val form = state.form?.let { form ->
                // Arkusz otwarty na wydarzeniu, które właśnie przyszło z serwera —
                // odświeżamy tylko znacznik kolejki, żeby nie kasować wpisywanego tekstu.
                val fresh = snapshot.events.firstOrNull { it.id == form.id }
                if (fresh == null) form else form.copy(pendingSync = fresh.pendingSync)
            }
            state.copy(
                calendars = snapshot.calendars,
                events = snapshot.events,
                members = snapshot.members,
                busy = snapshot.busy,
                canOverrideBusy = snapshot.canOverrideBusy,
                syncedAt = snapshot.syncedAt,
                form = form,
                // Kropki gasną razem z wydarzeniem — po usunięciu nie ma czego ciągnąć.
                resizeEventId = state.resizeEventId?.takeIf { id ->
                    snapshot.events.any { it.id == id }
                },
            )
        }
    }

    private fun restorePreferences() {
        viewModelScope.launch {
            val view = CalendarViewKind.fromWire(sessionPreferences.calendarView.first())
            val hidden = sessionPreferences.calendarHiddenLayers.first()
            val overlays = sessionPreferences.calendarOverlaysOn.first()
                .mapNotNull { OverlaySource.fromWire(it) }
                .toSet()
            _uiState.update {
                it.copy(view = view, hiddenLayers = hidden, overlaysOn = overlays)
            }
            load(initial = true)
            if (overlays.isNotEmpty()) loadOverlays()
        }
    }

    // ── Zakres i pobieranie ──────────────────────────────────────────────────

    /**
     * Zakres pobierania zależny od widoku — te same reguły co w panelu.
     * Miesiąc bierze pełną siatkę 6×7, więc początek i koniec zahaczają
     * o sąsiednie miesiące.
     */
    private fun range(state: UiState): Pair<Long, Long> {
        val anchor = state.anchor
        return when (state.view) {
            CalendarViewKind.MONTH -> {
                val gridStart = startOfWeek(YearMonth.from(anchor).atDay(1))
                dateToMillis(gridStart) to dateToMillis(gridStart.plusDays(42))
            }
            CalendarViewKind.WEEK -> {
                val weekStart = startOfWeek(anchor)
                dateToMillis(weekStart) to dateToMillis(weekStart.plusDays(7))
            }
            CalendarViewKind.DAY -> dateToMillis(anchor) to dateToMillis(anchor.plusDays(1))
            CalendarViewKind.AGENDA ->
                dateToMillis(anchor) to dateToMillis(anchor.plusDays(AGENDA_DAYS.toLong()))
        }
    }

    private fun load(initial: Boolean = false) {
        val state = _uiState.value
        val (from, to) = range(state)
        loadedRange = from to to
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = initial, isRefreshing = !initial, error = null) }
            try {
                repository.refresh(isoUtc(from), isoUtc(to))
                _uiState.update { it.copy(isLoading = false, isRefreshing = false) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = crmErrorMessage(e, "Nie udało się pobrać kalendarza"),
                    )
                }
            }
        }
    }

    fun refresh() = load()

    private fun loadOverlays() {
        val (from, to) = range(_uiState.value)
        viewModelScope.launch {
            val fetched = runCatching { repository.overlays(isoUtc(from), isoUtc(to)) }
                .getOrDefault(emptyList())
            _uiState.update { it.copy(overlays = fetched) }
        }
    }

    /** Po zmianie widoku albo kotwicy: dociągamy zakres, jeśli jeszcze go nie mamy. */
    private fun onRangeChanged() {
        val (from, to) = range(_uiState.value)
        if (loadedRange != from to to) load()
        if (_uiState.value.overlaysOn.isNotEmpty()) loadOverlays()
    }

    // ── Nawigacja po zakresie ────────────────────────────────────────────────

    fun setView(view: CalendarViewKind) {
        if (view == _uiState.value.view) return
        _uiState.update { it.copy(view = view, resizeEventId = null) }
        viewModelScope.launch { sessionPreferences.saveCalendarView(view.wire) }
        onRangeChanged()
    }

    fun step(direction: Int) {
        _uiState.update { state ->
            val anchor = when (state.view) {
                CalendarViewKind.MONTH -> state.anchor.plusMonths(direction.toLong())
                CalendarViewKind.WEEK -> state.anchor.plusWeeks(direction.toLong())
                CalendarViewKind.DAY -> state.anchor.plusDays(direction.toLong())
                CalendarViewKind.AGENDA -> state.anchor.plusDays((AGENDA_DAYS * direction).toLong())
            }
            state.copy(anchor = anchor, selectedDay = null, resizeEventId = null)
        }
        onRangeChanged()
    }

    fun goToday() {
        val today = LocalDate.now()
        _uiState.update { it.copy(anchor = today, selectedDay = today, resizeEventId = null) }
        onRangeChanged()
    }

    fun selectDay(day: LocalDate) {
        _uiState.update {
            it.copy(selectedDay = if (it.selectedDay == day) null else day, anchor = day)
        }
    }

    /** Dotknięcie dnia w widoku miesiąca z zamiarem obejrzenia go w całości. */
    fun openDay(day: LocalDate) {
        _uiState.update {
            it.copy(anchor = day, selectedDay = day, view = CalendarViewKind.DAY, resizeEventId = null)
        }
        viewModelScope.launch { sessionPreferences.saveCalendarView(CalendarViewKind.DAY.wire) }
        onRangeChanged()
    }

    fun tick() {
        _uiState.update { it.copy(now = System.currentTimeMillis()) }
    }

    // ── Warstwy i filtry ─────────────────────────────────────────────────────

    fun toggleLayer(calendarId: String) {
        val hidden = _uiState.value.hiddenLayers.toMutableSet()
        if (!hidden.add(calendarId)) hidden.remove(calendarId)
        _uiState.update { it.copy(hiddenLayers = hidden) }
        viewModelScope.launch { sessionPreferences.saveCalendarHiddenLayers(hidden) }
    }

    fun toggleOverlay(source: OverlaySource) {
        val on = _uiState.value.overlaysOn.toMutableSet()
        if (!on.add(source)) on.remove(source)
        _uiState.update { it.copy(overlaysOn = on, overlays = if (on.isEmpty()) emptyList() else it.overlays) }
        viewModelScope.launch { sessionPreferences.saveCalendarOverlaysOn(on.map { s -> s.wire }.toSet()) }
        if (on.isNotEmpty()) loadOverlays()
    }

    /** Warstwa szarych pól „Zajęte". Dane siedzą w cache, więc to czysty widok. */
    fun toggleBusyLayer() {
        _uiState.update { it.copy(busyOn = !it.busyOn) }
    }

    fun setAssigneeFilter(userId: String?) {
        _uiState.update { it.copy(assigneeFilter = userId) }
    }

    // ── Arkusz wydarzenia ────────────────────────────────────────────────────

    fun openNew(day: LocalDate = _uiState.value.selectedDay ?: _uiState.value.anchor, hour: Int? = null) {
        val state = _uiState.value
        val calendarId = state.defaultCalendarId
        if (calendarId == null) {
            _uiState.update { it.copy(message = "Nie masz kalendarza z prawem zapisu.") }
            return
        }
        val start = LocalTime.of(hour ?: 9, 0)
        _uiState.update {
            it.copy(
                resizeEventId = null,
                form = EventForm(
                    calendarId = calendarId,
                    date = day,
                    endDate = day,
                    startTime = start,
                    endTime = start.plusHours(1),
                    assigneeId = state.currentUserId,
                ),
            )
        }
    }

    fun openEvent(eventId: String) {
        val state = _uiState.value
        val event = state.events.firstOrNull { it.id == eventId } ?: return
        openEvent(event)
    }

    fun openEvent(event: CalendarEvent) {
        val state = _uiState.value
        val start = parseIsoMillis(event.startAt)?.let { millisToDateTime(it) }
        val end = parseIsoMillis(event.endAt)?.let { millisToDateTime(it) }
        val writable = state.calendarOf(event.calendarId)?.canWrite ?: false
        _uiState.update {
            it.copy(
                resizeEventId = null,
                form = EventForm(
                    id = event.id,
                    calendarId = event.calendarId,
                    title = event.title,
                    date = start?.toLocalDate() ?: it.anchor,
                    endDate = end?.toLocalDate() ?: start?.toLocalDate() ?: it.anchor,
                    allDay = event.allDay,
                    startTime = start?.toLocalTime() ?: LocalTime.of(9, 0),
                    endTime = end?.toLocalTime() ?: LocalTime.of(10, 0),
                    assigneeId = event.assigneeId,
                    attendeeIds = event.attendees.map { a -> a.id },
                    location = event.location.orEmpty(),
                    color = event.color,
                    description = event.description.orEmpty(),
                    recurGroupId = event.recurrenceGroupId,
                    attendeeResponses = event.attendees.associate { a -> a.id to a.response },
                    readOnly = !writable,
                    pendingSync = event.pendingSync,
                ),
            )
        }
    }

    fun closeForm() {
        _uiState.update { it.copy(form = null) }
    }

    fun editForm(block: (EventForm) -> EventForm) {
        _uiState.update { state -> state.copy(form = state.form?.let(block)) }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null) }
    }

    /**
     * Zapis wydarzenia. Kolizja zasobu wraca jako [CalendarConflictException] —
     * wtedy nie zamykamy arkusza, tylko przestawiamy go w tryb „zapisz mimo
     * kolizji", dokładnie jak panel.
     */
    fun save() {
        val state = _uiState.value
        val form = state.form ?: return
        if (form.title.isBlank()) {
            _uiState.update { it.copy(message = "Tytuł jest wymagany.") }
            return
        }
        val startAt = isoUtc(
            dateTimeToMillis(
                if (form.allDay) form.date.atStartOfDay() else form.date.atTime(form.startTime),
            ),
        )
        val endDate = if (form.endDate < form.date) form.date else form.endDate
        val endAt = isoUtc(
            dateTimeToMillis(
                if (form.allDay) {
                    endDate.atTime(LocalTime.of(23, 59))
                } else {
                    endDate.atTime(form.endTime)
                },
            ),
        )

        editForm { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                if (form.isNew) {
                    val created = repository.createEvent(
                        CalendarEventDraft(
                            calendarId = form.calendarId,
                            title = form.title.trim(),
                            description = form.description.trim().ifBlank { null },
                            location = form.location.trim().ifBlank { null },
                            color = form.color,
                            startAt = startAt,
                            endAt = endAt,
                            allDay = form.allDay,
                            assigneeId = form.assigneeId,
                            attendeeIds = form.attendeeIds,
                            recurrence = form.recurFreq?.let { freq ->
                                Recurrence(
                                    freq = freq,
                                    interval = form.recurInterval.coerceAtLeast(1),
                                    until = form.recurUntil?.let { d -> isoUtc(dateToMillis(d)) },
                                    count = form.recurCount,
                                )
                            },
                        ),
                        allowConflict = form.conflictForce,
                    )
                    // Świeży termin od razu dostaje kropki, jeśli widać siatkę —
                    // godzinę najczęściej dociąga się zaraz po dodaniu.
                    _uiState.update { state ->
                        val onGrid = state.view.hasTimeGrid && !created.allDay
                        state.copy(
                            form = null,
                            resizeEventId = created.id.takeIf { onGrid },
                            message = if (onGrid) {
                                "Dodano wydarzenie. Przeciągnij kropki, aby zmienić godziny."
                            } else {
                                "Dodano wydarzenie."
                            },
                        )
                    }
                } else {
                    repository.updateEvent(
                        id = form.id!!,
                        patch = CalendarEventPatch(
                            title = Edit(form.title.trim()),
                            description = Edit(form.description.trim().ifBlank { null }),
                            location = Edit(form.location.trim().ifBlank { null }),
                            color = Edit(form.color),
                            startAt = Edit(startAt),
                            endAt = Edit(endAt),
                            allDay = Edit(form.allDay),
                            assigneeId = Edit(form.assigneeId),
                            attendeeIds = Edit(form.attendeeIds),
                        ),
                        scope = form.scope,
                        allowConflict = form.conflictForce,
                    )
                    _uiState.update { it.copy(form = null, message = "Zapisano wydarzenie.") }
                }
            } catch (e: PrivateBusyConflictException) {
                // Twarda blokada od prywatnego kalendarza wykonawcy. Tryb
                // „zapisz mimo to" uzbrajamy TYLKO planiście — bez uprawnienia
                // drugi zapis i tak wróciłby z 409, a przycisk kłamałby.
                editForm { it.copy(saving = false, conflictForce = e.canOverride) }
                _uiState.update {
                    it.copy(
                        message = if (e.canOverride) {
                            "${e.message} Zapisz ponownie, aby zaplanować mimo to."
                        } else {
                            "${e.message} Wybierz inny termin albo poproś koordynatora."
                        },
                    )
                }
            } catch (e: CalendarConflictException) {
                editForm { it.copy(saving = false, conflictForce = true) }
                _uiState.update { it.copy(message = e.message) }
            } catch (e: Exception) {
                editForm { it.copy(saving = false) }
                _uiState.update {
                    it.copy(message = crmErrorMessage(e, "Nie udało się zapisać wydarzenia"))
                }
            }
        }
    }

    fun deleteEvent() {
        val form = _uiState.value.form ?: return
        val id = form.id ?: return
        editForm { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                repository.deleteEvent(id, form.scope)
                _uiState.update { it.copy(form = null, message = "Usunięto wydarzenie.") }
            } catch (e: Exception) {
                editForm { it.copy(saving = false) }
                _uiState.update {
                    it.copy(message = crmErrorMessage(e, "Nie udało się usunąć wydarzenia"))
                }
            }
        }
    }

    fun setRsvp(response: RsvpStatus) {
        val state = _uiState.value
        val form = state.form ?: return
        val id = form.id ?: return
        val userId = state.currentUserId ?: return
        editForm {
            it.copy(attendeeResponses = it.attendeeResponses + (userId to response))
        }
        viewModelScope.launch {
            try {
                repository.setRsvp(id, userId, response)
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(message = crmErrorMessage(e, "Nie udało się zapisać odpowiedzi"))
                }
            }
        }
    }

    // ── Rozciąganie wydarzenia w siatce ──────────────────────────────────────

    /**
     * Uzbraja kropki na wydarzeniu (przytrzymanie kafelka). Uchwyty ma tylko
     * jedno wydarzenie naraz, więc przeciąganie nie kłóci się z przewijaniem
     * siatki: palec łapie kropkę, a nie kafelek.
     */
    fun armResize(event: CalendarEvent) {
        val state = _uiState.value
        if (event.allDay) {
            _uiState.update { it.copy(message = "Wydarzenie całodniowe zmienisz w karcie.") }
            return
        }
        if (state.calendarOf(event.calendarId)?.canWrite != true) {
            _uiState.update { it.copy(message = "Ten kalendarz masz tylko do odczytu.") }
            return
        }
        _uiState.update {
            it.copy(resizeEventId = event.id, message = "Przeciągnij kropki, aby zmienić godziny.")
        }
    }

    fun cancelResize() {
        if (_uiState.value.resizeEventId == null) return
        _uiState.update { it.copy(resizeEventId = null) }
    }

    /**
     * Zapis godzin po puszczeniu kropki. Serii to nie rusza — przeciąga się
     * konkretne wystąpienie, więc zakres zostaje na [RecurrenceScope.THIS].
     * Kolizji zasobu stąd nie forsujemy: siatka nie ma gdzie zapytać, więc
     * zmiana wraca do stanu sprzed przeciągnięcia i człowiek decyduje w karcie.
     */
    fun resizeEvent(eventId: String, startMillis: Long, endMillis: Long) {
        val state = _uiState.value
        val event = state.events.firstOrNull { it.id == eventId } ?: return
        if (event.startMillis() == startMillis && event.endMillis() == endMillis) return
        if (state.calendarOf(event.calendarId)?.canWrite != true) {
            _uiState.update {
                it.copy(resizeEventId = null, message = "Ten kalendarz masz tylko do odczytu.")
            }
            return
        }
        viewModelScope.launch {
            try {
                repository.updateEvent(
                    id = eventId,
                    patch = CalendarEventPatch(
                        startAt = Edit(isoUtc(startMillis)),
                        endAt = Edit(isoUtc(endMillis)),
                    ),
                    scope = RecurrenceScope.THIS,
                )
                _uiState.update {
                    it.copy(
                        message = "Godziny: ${formatHour(startMillis)} – ${formatHour(endMillis)}.",
                    )
                }
            } catch (e: PrivateBusyConflictException) {
                // Przeciągnięcie w siatce nie ma jak zapytać „mimo to?" —
                // odsyłamy do karty wydarzenia, gdzie decyzja jest świadoma.
                _uiState.update {
                    it.copy(
                        resizeEventId = null,
                        message = "${e.message} Godzin nie zmieniono — ustaw je w karcie wydarzenia.",
                    )
                }
            } catch (e: CalendarConflictException) {
                _uiState.update {
                    it.copy(
                        resizeEventId = null,
                        message = "Zasób jest w tym czasie zajęty — godzin nie zmieniono. " +
                            "Wpisz je w karcie wydarzenia.",
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        resizeEventId = null,
                        message = crmErrorMessage(e, "Nie udało się zmienić godzin"),
                    )
                }
            }
        }
    }

    // ── Kalendarze (warstwy) ─────────────────────────────────────────────────

    fun createCalendar(name: String, type: CalendarType, color: String) {
        viewModelScope.launch {
            try {
                repository.createCalendar(CalendarDraft(name = name.trim(), type = type, color = color))
                _uiState.update { it.copy(message = "Dodano kalendarz.") }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(message = crmErrorMessage(e, "Nie udało się dodać kalendarza"))
                }
            }
        }
    }

    fun renameCalendar(id: String, name: String, color: String) {
        viewModelScope.launch {
            try {
                repository.updateCalendar(id, CalendarPatch(name = Edit(name.trim()), color = Edit(color)))
                _uiState.update { it.copy(message = "Zapisano kalendarz.") }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(message = crmErrorMessage(e, "Nie udało się zapisać kalendarza"))
                }
            }
        }
    }

    fun archiveCalendar(id: String, archived: Boolean) {
        viewModelScope.launch {
            try {
                repository.setCalendarArchived(id, archived)
                _uiState.update {
                    it.copy(message = if (archived) "Zarchiwizowano kalendarz." else "Przywrócono kalendarz.")
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(message = crmErrorMessage(e, "Nie udało się zmienić kalendarza"))
                }
            }
        }
    }

    /** Wejście z ekranu „Znajdź termin": gotowy slot otwiera arkusz nowego wydarzenia. */
    fun openFromSlot(startMillis: Long, endMillis: Long, attendeeIds: List<String>) {
        val state = _uiState.value
        val calendarId = state.defaultCalendarId ?: return
        val start = millisToDateTime(startMillis)
        val end = millisToDateTime(endMillis)
        _uiState.update {
            it.copy(
                anchor = start.toLocalDate(),
                selectedDay = start.toLocalDate(),
                resizeEventId = null,
                form = EventForm(
                    calendarId = calendarId,
                    date = start.toLocalDate(),
                    endDate = end.toLocalDate(),
                    startTime = start.toLocalTime(),
                    endTime = end.toLocalTime(),
                    assigneeId = state.currentUserId,
                    attendeeIds = attendeeIds,
                ),
            )
        }
        onRangeChanged()
    }
}
