package com.ekotak.teamtalk.presentation.lead

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.data.local.preferences.SessionPreferences
import com.ekotak.teamtalk.data.repository.LeadRejectedException
import com.ekotak.teamtalk.domain.model.FloorHeatingVariant
import com.ekotak.teamtalk.domain.model.LeadChannel
import com.ekotak.teamtalk.domain.model.LeadConstruction
import com.ekotak.teamtalk.domain.model.LeadDraft
import com.ekotak.teamtalk.domain.model.LeadEvent
import com.ekotak.teamtalk.domain.model.LeadInterest
import com.ekotak.teamtalk.domain.model.LeadOccupancy
import com.ekotak.teamtalk.domain.model.LeadOrigin
import com.ekotak.teamtalk.domain.model.LeadProjectKind
import com.ekotak.teamtalk.domain.model.LeadShape
import com.ekotak.teamtalk.domain.model.LeadSubmitResult
import com.ekotak.teamtalk.domain.model.RenovationWorks
import com.ekotak.teamtalk.domain.model.TaskMember
import com.ekotak.teamtalk.domain.repository.LeadRepository
import com.ekotak.teamtalk.domain.repository.MemberRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Plansze kreatora LEAD. Kolejność z makiety `design/mockups/modul-lead.html`;
 * które plansze biorą udział, zależy od odpowiedzi — patrz [LeadWizardViewModel.UiState.steps].
 */
enum class LeadStep(val crumb: String) {
    KONTAKT("Kontakt"),
    ZAKRES("Zainteresowanie"),
    PROJEKT("Projekt"),
    BRYLA("Bryła"),
    PODLOGOWKA("Podłogówka"),
    REMONT("Zamieszkały"),
    DANE("Dane"),
    PODSUMOWANIE("Podsumowanie"),
}

@HiltViewModel
class LeadWizardViewModel @Inject constructor(
    private val leads: LeadRepository,
    private val members: MemberRepository,
    private val sessionPreferences: SessionPreferences,
) : ViewModel() {

    data class UiState(
        val step: LeadStep = LeadStep.KONTAKT,
        // ── 1. Kontakt ────────────────────────────────────────────────────────
        val channel: LeadChannel? = null,
        val events: List<LeadEvent> = emptyList(),
        val eventsLoaded: Boolean = false,
        val eventId: String? = null,
        val people: List<TaskMember> = emptyList(),
        val selfId: String? = null,
        /** Zalogowany — gdy lista osób jeszcze nie przyszła (pierwsze uruchomienie bez sieci). */
        val selfName: String? = null,
        val takenById: String? = null,
        val showAllPeople: Boolean = false,
        val peopleQuery: String = "",
        // ── 2. Zainteresowanie ────────────────────────────────────────────────
        val interest: LeadInterest? = null,
        val occupancy: LeadOccupancy? = null,
        // ── Nowy dom ──────────────────────────────────────────────────────────
        val projectKind: LeadProjectKind? = null,
        val projectName: String = "",
        val construction: LeadConstruction? = null,
        val basement: Boolean = false,
        val garage: Boolean = false,
        val shape: LeadShape? = null,
        val area: String = "",
        val lightSlab: Boolean = false,
        val variant: FloorHeatingVariant? = null,
        val variantNote: String = "",
        val milling: Boolean = false,
        // ── Dom zamieszkały ───────────────────────────────────────────────────
        val works: RenovationWorks? = null,
        val worksArea: String = "",
        val heatSource: String? = null,
        val heatPlanned: Boolean = false,
        // ── Dane klienta ──────────────────────────────────────────────────────
        val postalCode: String = "",
        val city: String = "",
        val fullName: String = "",
        val phone: String = "",
        val email: String = "",
        val origin: LeadOrigin? = null,
        val referralFrom: String = "",
        // ── Zapis ─────────────────────────────────────────────────────────────
        val pendingCount: Int = 0,
        val isSaving: Boolean = false,
        val result: LeadSubmitResult? = null,
        val error: String? = null,
    ) {
        val isDone: Boolean get() = result != null

        /** Plansze tego przebiegu. Bez wyboru stanu domu nie wiadomo jeszcze, ile ich będzie. */
        val steps: List<LeadStep>
            get() = buildList {
                add(LeadStep.KONTAKT)
                add(LeadStep.ZAKRES)
                when (occupancy) {
                    LeadOccupancy.W_BUDOWIE -> {
                        add(LeadStep.PROJEKT)
                        if (needsShape) add(LeadStep.BRYLA)
                        add(LeadStep.PODLOGOWKA)
                    }
                    LeadOccupancy.ZAMIESZKALY -> add(LeadStep.REMONT)
                    null -> Unit
                }
                add(LeadStep.DANE)
                add(LeadStep.PODSUMOWANIE)
            }

        val stepIndex: Int get() = steps.indexOf(step).coerceAtLeast(0)

        /** Licznik „3 / 7" — przed rozwidleniem „2 / 5–7". */
        val stepCountLabel: String
            get() = if (occupancy == null) "${stepIndex + 1} / 5–7" else "${stepIndex + 1} / ${steps.size}"

        /** Bryła i metraż tylko bez nazwy projektu — z nazwą ściągniemy rzut. */
        val needsShape: Boolean
            get() = projectKind == LeadProjectKind.WLASNY || projectKind == LeadProjectKind.NIE_PAMIETA

        /** Lekka konstrukcja albo lekki strop → pytamy o system suchy. */
        val lightFloor: Boolean get() = construction?.light == true || lightSlab

        /** Warianty podłogówki dla tej konstrukcji, w kolejności na ekranie. */
        val variantOptions: List<FloorHeatingVariant>
            get() = if (lightFloor) {
                listOf(FloorHeatingVariant.SUCHY, FloorHeatingVariant.STANDARD, FloorHeatingVariant.OPIS)
            } else {
                listOf(FloorHeatingVariant.STANDARD, FloorHeatingVariant.OPIS)
            }

        /** Osoba zalogowana na górze, reszta alfabetycznie. */
        val peopleSorted: List<TaskMember>
            get() = people.sortedWith(compareBy<TaskMember> { it.id != selfId }.thenBy { it.displayName.lowercase() })

        val areaM2: Int? get() = area.filter(Char::isDigit).toIntOrNull()?.takeIf { it > 0 }
        val worksAreaM2: Int? get() = worksArea.filter(Char::isDigit).toIntOrNull()?.takeIf { it > 0 }

        val takerName: String?
            get() = people.firstOrNull { it.id == takenById }?.displayName
                ?: selfName.takeIf { takenById == selfId }

        fun canLeave(s: LeadStep): Boolean = when (s) {
            LeadStep.KONTAKT -> channel != null && takenById != null &&
                // Targi bez listy wydarzeń (brak sieci) przepuszczamy — źródło zostanie „targi".
                (channel != LeadChannel.TARGI || eventId != null || (eventsLoaded && events.isEmpty()))
            LeadStep.ZAKRES -> interest != null && occupancy != null
            LeadStep.PROJEKT -> projectKind != null && construction != null &&
                (projectKind != LeadProjectKind.KATALOG || projectName.isNotBlank())
            LeadStep.BRYLA -> shape != null && areaM2 != null
            LeadStep.PODLOGOWKA -> variant != null && (variant != FloorHeatingVariant.OPIS || variantNote.isNotBlank())
            LeadStep.REMONT -> works != null && worksAreaM2 != null && heatSource != null
            LeadStep.DANE -> fullName.isNotBlank() &&
                (phone.isNotBlank() || email.isNotBlank()) &&
                (city.isNotBlank() || postalCode.isNotBlank()) &&
                (email.isBlank() || EMAIL.matches(email.trim()))
            LeadStep.PODSUMOWANIE -> true
        }
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val session = sessionPreferences.session.first()
            _uiState.update {
                it.copy(
                    selfId = session?.userId,
                    selfName = session?.displayName,
                    // Kontakt zwykle przyjmuje ten, kto trzyma telefon — zaznaczamy go z góry.
                    takenById = it.takenById ?: session?.userId,
                )
            }
        }
        viewModelScope.launch { members.observe().collect { list -> _uiState.update { it.copy(people = list) } } }
        viewModelScope.launch { runCatching { members.refresh() } }
        viewModelScope.launch { leads.observePendingCount().collect { n -> _uiState.update { it.copy(pendingCount = n) } } }
    }

    // ── Nawigacja ────────────────────────────────────────────────────────────

    fun next() = _uiState.update { s ->
        if (!s.canLeave(s.step)) return@update s
        val list = s.steps
        s.copy(step = list.getOrElse(s.stepIndex + 1) { s.step }, error = null)
    }

    /** `false` = jesteśmy na pierwszej planszy i „wstecz" zamyka kreator. */
    fun back(): Boolean {
        val s = _uiState.value
        if (s.isDone || s.stepIndex == 0) return false
        _uiState.update { it.copy(step = it.steps[it.stepIndex - 1], error = null) }
        return true
    }

    fun goTo(step: LeadStep) = _uiState.update { if (step in it.steps) it.copy(step = step) else it }

    fun startOver() = _uiState.update { s ->
        UiState(
            selfId = s.selfId,
            selfName = s.selfName,
            takenById = s.selfId,
            people = s.people,
            events = s.events,
            eventsLoaded = s.eventsLoaded,
            pendingCount = s.pendingCount,
        )
    }

    // ── 1. Kontakt ───────────────────────────────────────────────────────────

    fun onChannel(channel: LeadChannel) {
        _uiState.update {
            it.copy(
                channel = channel,
                eventId = if (channel == LeadChannel.TARGI) it.eventId else null,
                // „Polecenie" z góry zaznacza rekomendację w danych klienta.
                origin = if (channel == LeadChannel.POLECENIE && it.origin == null) LeadOrigin.REKOMENDACJA else it.origin,
            )
        }
        if (channel == LeadChannel.TARGI && !_uiState.value.eventsLoaded) loadEvents()
    }

    private fun loadEvents() {
        viewModelScope.launch {
            val events = leads.getEvents()
            _uiState.update { it.copy(events = events, eventsLoaded = true) }
        }
    }

    fun onEvent(id: String) = _uiState.update { it.copy(eventId = id) }

    fun onTaker(id: String) = _uiState.update { it.copy(takenById = id, showAllPeople = false, peopleQuery = "") }

    fun onShowAllPeople() = _uiState.update { it.copy(showAllPeople = true) }

    fun onPeopleQuery(q: String) = _uiState.update { it.copy(peopleQuery = q) }

    // ── 2. Zainteresowanie ───────────────────────────────────────────────────

    fun onInterest(interest: LeadInterest) = _uiState.update { it.copy(interest = interest) }

    fun onOccupancy(occupancy: LeadOccupancy) = _uiState.update { it.copy(occupancy = occupancy) }

    // ── Nowy dom ─────────────────────────────────────────────────────────────

    fun onProjectKind(kind: LeadProjectKind) = _uiState.update { it.copy(projectKind = kind) }

    fun onProjectName(name: String) = _uiState.update { it.copy(projectName = name) }

    fun onConstruction(c: LeadConstruction) = _uiState.update {
        // Inna konstrukcja to inne pytanie o podłogówkę — stara odpowiedź nie pasuje.
        if (it.construction == c) it else it.copy(construction = c, lightSlab = false, variant = null)
    }

    fun onBasement() = _uiState.update { it.copy(basement = !it.basement) }

    fun onGarage() = _uiState.update { it.copy(garage = !it.garage) }

    fun onNoBasementGarage() = _uiState.update { it.copy(basement = false, garage = false) }

    fun onShape(shape: LeadShape) = _uiState.update { it.copy(shape = shape) }

    fun onArea(value: String) = _uiState.update { it.copy(area = value.filter(Char::isDigit).take(5)) }

    fun onLightSlab() = _uiState.update { it.copy(lightSlab = !it.lightSlab, variant = null) }

    fun onVariant(v: FloorHeatingVariant) = _uiState.update { it.copy(variant = v) }

    fun onVariantNote(text: String) = _uiState.update { it.copy(variantNote = text) }

    fun onMilling() = _uiState.update { it.copy(milling = !it.milling) }

    // ── Dom zamieszkały ──────────────────────────────────────────────────────

    fun onWorks(w: RenovationWorks) = _uiState.update { it.copy(works = w) }

    fun onWorksArea(value: String) = _uiState.update { it.copy(worksArea = value.filter(Char::isDigit).take(5)) }

    fun onHeatPlanned(planned: Boolean) = _uiState.update { it.copy(heatPlanned = planned) }

    fun onHeatSource(source: String) = _uiState.update { it.copy(heatSource = source) }

    // ── Dane klienta ─────────────────────────────────────────────────────────

    fun onPostalCode(v: String) = _uiState.update { it.copy(postalCode = formatPostal(v)) }

    fun onCity(v: String) = _uiState.update { it.copy(city = v) }

    fun onFullName(v: String) = _uiState.update { it.copy(fullName = v) }

    fun onPhone(v: String) = _uiState.update { it.copy(phone = v) }

    fun onEmail(v: String) = _uiState.update { it.copy(email = v) }

    fun onOrigin(o: LeadOrigin) = _uiState.update { it.copy(origin = if (it.origin == o) null else o) }

    fun onReferral(v: String) = _uiState.update { it.copy(referralFrom = v) }

    // ── Zapis ────────────────────────────────────────────────────────────────

    fun save() {
        val s = _uiState.value
        if (s.isSaving || s.isDone) return
        val channel = s.channel ?: return
        val occupancy = s.occupancy ?: return
        val interest = s.interest ?: return
        val draft = LeadDraft(
            channel = channel,
            eventId = s.eventId,
            takenById = s.takenById,
            interests = listOf(interest),
            occupancy = occupancy,
            projectKind = s.projectKind,
            projectName = s.projectName,
            construction = s.construction,
            basement = s.basement,
            garage = s.garage,
            shape = s.shape.takeIf { s.needsShape },
            areaM2 = s.areaM2.takeIf { s.needsShape },
            floorHeatingVariant = s.variant,
            floorHeatingNote = s.variantNote,
            lightSlab = s.lightFloor,
            milling = s.milling,
            works = s.works,
            worksAreaM2 = s.worksAreaM2,
            heatSource = s.heatSource,
            heatSourcePlanned = s.heatPlanned,
            fullName = s.fullName,
            phone = s.phone,
            email = s.email,
            postalCode = s.postalCode,
            city = s.city,
            origin = s.origin,
            referralFrom = s.referralFrom,
        )
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                val result = leads.submit(draft)
                _uiState.update { it.copy(isSaving = false, result = result) }
            } catch (e: LeadRejectedException) {
                _uiState.update { it.copy(isSaving = false, error = e.message) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, error = e.message ?: "Nie udało się zapisać leada.") }
            }
        }
    }

    private companion object {
        val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

        /** „43300" → „43-300": kod wpisuje się kciukiem z klawiatury numerycznej. */
        fun formatPostal(raw: String): String {
            val digits = raw.filter(Char::isDigit).take(5)
            return if (digits.length > 2) digits.substring(0, 2) + "-" + digits.substring(2) else digits
        }
    }
}
