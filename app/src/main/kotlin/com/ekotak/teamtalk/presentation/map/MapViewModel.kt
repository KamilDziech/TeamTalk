package com.ekotak.teamtalk.presentation.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.data.location.LocationProvider
import com.ekotak.teamtalk.domain.model.MapKind
import com.ekotak.teamtalk.domain.model.MapPoint
import com.ekotak.teamtalk.domain.model.PlaceSuggestion
import com.ekotak.teamtalk.domain.model.TaskMember
import com.ekotak.teamtalk.domain.model.departmentOf
import com.ekotak.teamtalk.domain.model.haversineKm
import com.ekotak.teamtalk.domain.model.sortMembersByDepartment
import com.ekotak.teamtalk.domain.repository.MemberRepository
import com.ekotak.teamtalk.domain.usecase.map.ObserveMapPointsUseCase
import com.ekotak.teamtalk.domain.usecase.map.RefreshMapUseCase
import com.ekotak.teamtalk.domain.usecase.map.SuggestPlacesUseCase
import com.ekotak.teamtalk.presentation.components.PersonScope
import com.ekotak.teamtalk.presentation.crm.crmErrorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Widok mapy = zakładka górnego przełącznika (Flota nie ma punktów). */
enum class MapViewTab(val label: String, val kind: MapKind?) {
    FLEET("Flota", null),
    CURRENT("Klienci bieżący", MapKind.CURRENT),
    FINISHED("Klienci zakończeni", MapKind.FINISHED),
    SERVICE("Serwisy", MapKind.SERVICE),
    INSPECTION("Przeglądy", MapKind.INSPECTION);

    /** Widoki klientów mają filtr opiekuna (główny/etapowy). */
    val isClientView: Boolean get() = this == CURRENT || this == FINISHED

    /** Widoki serwisowe filtrują po serwisancie. */
    val isServiceView: Boolean get() = this == SERVICE || this == INSPECTION
}

/** Tryb rysowania punktów — piny albo mapa cieplna (jak w panelu). */
enum class MapMode(val label: String) { PINS("Piny"), HEAT("Heatmapa") }

/** Filtr opiekuna w widokach klientów. */
enum class OwnerMode(val label: String) { MAIN("Opiekun główny"), STAGE("Opiekun etapowy") }

/** Środek filtra promienia — z geokodera albo z GPS telefonu. */
data class MapCenter(val lat: Double, val lng: Double, val label: String)

/** Pozycja legendy: chip statusu z liczebnością. */
data class MapChip(
    val key: String,
    val label: String,
    val colorArgb: Long,
    val order: Int,
    val count: Int,
)

/**
 * Mapa zleceń — mobilny odpowiednik `web/src/app/app/map/MapView.tsx`.
 *
 * Punkty idą ze strumienia z cache Room, więc mapa otwiera się bez zasięgu;
 * filtrowanie (widok, słowo, osoba, instalacja, promień, chip statusu) robimy
 * lokalnie na pobranej migawce — przełączanie widoku bez okrążenia po sieci
 * jest wyraźnie szybsze, a punktów jednej organizacji są setki, nie miliony.
 *
 * Różnice wobec panelu wynikają z ekranu, nie z zakresu: pasek szukania panelu
 * (słowo · lokalizacja · promień w jednej linii) rozkłada się na pole u góry
 * i arkusz filtrów, a doklejone są trzy rzeczy terenowe — „moja lokalizacja",
 * nawigacja i telefon do klienta.
 */
@HiltViewModel
class MapViewModel @Inject constructor(
    private val observeMapPoints: ObserveMapPointsUseCase,
    private val refreshMap: RefreshMapUseCase,
    private val suggestPlaces: SuggestPlacesUseCase,
    private val locationProvider: LocationProvider,
    private val memberRepository: MemberRepository,
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = true,
        val isRefreshing: Boolean = false,
        val error: String? = null,
        val message: String? = null,
        /** Moment pobrania migawki — pasek „dane z…" przy pracy bez zasięgu. */
        val syncedAt: Long? = null,
        val view: MapViewTab = MapViewTab.CURRENT,
        val mode: MapMode = MapMode.PINS,
        val ownerMode: OwnerMode = OwnerMode.MAIN,
        val keyword: String = "",
        /** `null` = wszystkie statusy (chip „Wszystkie"). */
        val chip: String? = null,
        /** Filtr osoby: Wszyscy / cały dział / konkretna osoba. */
        val person: PersonScope = PersonScope.All,
        val installFilter: String? = null,
        val locationQuery: String = "",
        val suggestions: List<PlaceSuggestion> = emptyList(),
        val center: MapCenter? = null,
        val radiusKm: Int = 0,
        /** Pozycja telefonu, gdy użytkownik jej użył — do odległości w dymku. */
        val myLocation: Pair<Double, Double>? = null,
        val isLocating: Boolean = false,
        // ── Wyliczone ────────────────────────────────────────────────────────
        val counts: Map<MapKind, Int> = emptyMap(),
        /** Punkty widoku po wszystkich filtrach poza chipem — podstawa legendy. */
        val baseCount: Int = 0,
        val shown: List<MapPoint> = emptyList(),
        val noGeo: List<MapPoint> = emptyList(),
        val chips: List<MapChip> = emptyList(),
        /**
         * Osoby obecne w bieżącym widoku — do drzewa działów w filtrze.
         * Kto jest w książce zespołu, ma rolę i funkcje (a więc dział); kto
         * został tylko na punkcie (konto usunięte, ekipa zewnętrzna), dostaje
         * wpis z samą etykietą i ląduje w „Pozostali".
         */
        val people: List<TaskMember> = emptyList(),
        /** Liczba punktów na osobę — licznik przy nazwisku i sumy działów. */
        val peopleCounts: Map<String, Int> = emptyMap(),
        val installs: List<Pair<String, Int>> = emptyList(),
        /** Punkt otwarty w dymku (arkusz karty punktu). */
        val selected: MapPoint? = null,
        /** Licznik żądań dopasowania kadru — zmiana = mapa ma się przekadrować. */
        val fitRequest: Int = 0,
    ) {
        val isFleet: Boolean get() = view == MapViewTab.FLEET

        /** Liczba filtrów odbiegających od domyślnych — plakietka przy ikonie. */
        val activeFilterCount: Int
            get() = listOf(
                person != PersonScope.All,
                installFilter != null,
                center != null && radiusKm > 0,
                ownerMode != OwnerMode.MAIN,
            ).count { it }
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    /** Surowa migawka z cache — filtrowana lokalnie przy każdej zmianie stanu. */
    private var allPoints: List<MapPoint> = emptyList()

    /**
     * Książka zespołu z cache — po niej filtr osoby wie, kto siedzi w jakim
     * dziale. Punkt niesie tylko id i etykietę osoby, więc bez tego drzewo
     * miałoby same „Pozostali".
     */
    private var directory: Map<String, TaskMember> = emptyMap()
    private var suggestJob: Job? = null

    init {
        viewModelScope.launch {
            observeMapPoints().collect { snapshot ->
                allPoints = snapshot.points
                _uiState.update {
                    recompute(
                        it.copy(
                            isLoading = false,
                            syncedAt = snapshot.syncedAt,
                        ),
                    )
                }
            }
        }
        viewModelScope.launch {
            memberRepository.observe().collect { members ->
                directory = members.associateBy { it.id }
                _uiState.update { recompute(it) }
            }
        }
        // Odświeżenie książki jest miękkie (patrz MemberRepository) — mapa
        // otwiera się i bez niego, po prostu z osobami sprzed ostatniej zmiany.
        viewModelScope.launch { memberRepository.refresh() }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true, error = null) }
            runCatching { refreshMap() }
                .onFailure { err ->
                    val text = crmErrorMessage(err, "Nie udało się wczytać mapy")
                    _uiState.update {
                        it.copy(
                            isRefreshing = false,
                            isLoading = false,
                            // Bez zasięgu zostaje ostatnia migawka — mówimy o tym
                            // w pasku, zamiast czyścić mapę.
                            error = if (allPoints.isEmpty()) text else null,
                            message = if (allPoints.isNotEmpty()) text else null,
                        )
                    }
                }
                .onSuccess { _uiState.update { it.copy(isRefreshing = false, error = null) } }
        }
    }

    /** Zmiana widoku zeruje filtry i przekadrowuje — tak jak w panelu. */
    fun selectView(view: MapViewTab) {
        _uiState.update {
            recompute(
                it.copy(
                    view = view,
                    chip = null,
                    person = PersonScope.All,
                    installFilter = null,
                    selected = null,
                    fitRequest = it.fitRequest + 1,
                ),
            )
        }
    }

    fun setMode(mode: MapMode) = _uiState.update { it.copy(mode = mode) }

    fun setChip(key: String?) = _uiState.update { recompute(it.copy(chip = key)) }

    fun setOwnerMode(mode: OwnerMode) =
        _uiState.update { recompute(it.copy(ownerMode = mode, person = PersonScope.All)) }

    fun setPerson(scope: PersonScope) = _uiState.update { recompute(it.copy(person = scope)) }

    fun setInstall(name: String?) = _uiState.update { recompute(it.copy(installFilter = name)) }

    fun setKeyword(text: String) = _uiState.update { recompute(it.copy(keyword = text)) }

    fun setRadius(km: Int) =
        _uiState.update { recompute(it.copy(radiusKm = km, fitRequest = it.fitRequest + 1)) }

    fun clearFilters() {
        _uiState.update {
            recompute(
                it.copy(
                    chip = null,
                    person = PersonScope.All,
                    installFilter = null,
                    ownerMode = OwnerMode.MAIN,
                    center = null,
                    radiusKm = 0,
                    locationQuery = "",
                    suggestions = emptyList(),
                    fitRequest = it.fitRequest + 1,
                ),
            )
        }
    }

    /** Dopasowanie kadru do widocznych punktów (przycisk na mapie i „Szukaj"). */
    fun requestFit() = _uiState.update { it.copy(fitRequest = it.fitRequest + 1) }

    fun selectPoint(point: MapPoint?) = _uiState.update { it.copy(selected = point) }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    // ── Lokalizacja ──────────────────────────────────────────────────────────

    /** Pole „lokalizacja": podpowiedzi po odczekaniu, min. 3 znaki (jak panel). */
    fun onLocationQueryChange(text: String) {
        _uiState.update { it.copy(locationQuery = text) }
        suggestJob?.cancel()
        if (text.trim().length < 3) {
            _uiState.update { it.copy(suggestions = emptyList()) }
            return
        }
        suggestJob = viewModelScope.launch {
            delay(400)
            val hits = suggestPlaces(text)
            _uiState.update { it.copy(suggestions = hits) }
        }
    }

    fun selectPlace(place: PlaceSuggestion) {
        _uiState.update {
            recompute(
                it.copy(
                    center = MapCenter(place.lat, place.lng, place.label),
                    locationQuery = place.label,
                    suggestions = emptyList(),
                    // Wybór miejscowości bez promienia nic by nie filtrował —
                    // domyślnie bierzemy 30 km, tak jak najczęstszy wybór w panelu.
                    radiusKm = if (it.radiusKm == 0) 30 else it.radiusKm,
                    fitRequest = it.fitRequest + 1,
                ),
            )
        }
    }

    fun clearLocation() {
        _uiState.update {
            recompute(
                it.copy(
                    center = null,
                    locationQuery = "",
                    suggestions = emptyList(),
                    radiusKm = 0,
                    fitRequest = it.fitRequest + 1,
                ),
            )
        }
    }

    /** „Jestem tutaj" — środek promienia z GPS. Wymaga zgody na lokalizację. */
    fun useMyLocation() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLocating = true) }
            val fix = locationProvider.currentLocation()
            if (fix == null) {
                _uiState.update {
                    it.copy(
                        isLocating = false,
                        message = if (locationProvider.hasPermission()) {
                            "Nie udało się ustalić pozycji. Wpisz miejscowość."
                        } else {
                            "Brak zgody na lokalizację. Wpisz miejscowość."
                        },
                    )
                }
                return@launch
            }
            _uiState.update {
                recompute(
                    it.copy(
                        isLocating = false,
                        myLocation = fix,
                        center = MapCenter(fix.first, fix.second, "Moja lokalizacja"),
                        locationQuery = "Moja lokalizacja",
                        suggestions = emptyList(),
                        radiusKm = if (it.radiusKm == 0) 30 else it.radiusKm,
                        fitRequest = it.fitRequest + 1,
                    ),
                )
            }
        }
    }

    /** Czy w ogóle warto pokazywać przycisk lokalizacji bez pytania o zgodę. */
    fun hasLocationPermission(): Boolean = locationProvider.hasPermission()

    // ── Wyliczenia (odpowiednik useMemo z panelu) ────────────────────────────

    private fun recompute(state: UiState): UiState {
        val counts = allPoints.groupingBy { it.kind }.eachCount()
        val kind = state.view.kind
        val viewPoints = if (kind == null) emptyList() else allPoints.filter { it.kind == kind }

        // Osoby widoku liczymy PRZED filtrami: lista w arkuszu ma być stała,
        // a nie kurczyć się do jednego nazwiska po każdym wyborze.
        val personCounts = viewPoints.mapNotNull { state.personIdOf(it) }
            .groupingBy { it }
            .eachCount()
        val labels = buildMap {
            for (p in viewPoints) {
                p.ownerId?.let { id -> p.ownerLabel?.let { put(id, it) } }
                p.stageOwnerId?.let { id -> p.stageOwnerLabel?.let { put(id, it) } }
                p.technicianId?.let { id -> p.technicianLabel?.let { put(id, it) } }
            }
        }
        // Osoba z książki zespołu ma rolę i funkcje, czyli dział. Kto został
        // tylko na punkcie (konto skasowane, ekipa z zewnątrz) dostaje wpis
        // z samą etykietą i ląduje w „Pozostali" — zniknąć z filtra nie może,
        // bo jego punkty wciąż są na mapie.
        val people = sortMembersByDepartment(
            personCounts.keys.map { id ->
                directory[id] ?: TaskMember(
                    id = id,
                    email = labels[id] ?: id,
                    firstName = null,
                    lastName = null,
                    role = null,
                )
            },
        )

        // Id osób objętych filtrem; null = bez zawężenia. Dział rozwijamy do
        // zbioru osób, żeby dalej filtrować jednym porównaniem.
        val personIds: Set<String>? = when (val scope = state.person) {
            // Mapa nie ma „Moje" ani „Nieprzypisane" — punkt bez osoby i tak
            // przechodzi, a te zakresy nie trafiają tu z UI.
            PersonScope.All, PersonScope.Mine, PersonScope.Unassigned -> null
            is PersonScope.Person -> setOf(scope.id)
            is PersonScope.Dept -> people
                .filter { departmentOf(it) == scope.department }
                .map { it.id }
                .toSet()
        }

        val keyword = state.keyword.trim().lowercase()
        val base = viewPoints.filter { p ->
            if (keyword.isNotEmpty()) {
                val hay = buildString {
                    append(p.name).append(' ')
                    append(p.city.orEmpty()).append(' ')
                    append(p.installs.joinToString(" "))
                }.lowercase()
                if (!hay.contains(keyword)) return@filter false
            }
            val personId = state.personIdOf(p)
            if (personIds != null && personId !in personIds) return@filter false
            if (state.installFilter != null && !p.installs.contains(state.installFilter)) {
                return@filter false
            }
            val center = state.center
            if (center != null && state.radiusKm > 0) {
                val lat = p.lat
                val lng = p.lng
                // Punkt bez współrzędnych nie może przejść filtra promienia —
                // trafia na listę „bez lokalizacji", nie do wyniku.
                if (lat == null || lng == null) return@filter false
                if (haversineKm(center.lat, center.lng, lat, lng) > state.radiusKm) return@filter false
            }
            true
        }

        val withGeo = base.filter { it.hasGeo }
        val chips = withGeo.groupBy { it.badge.key }
            .map { (key, group) ->
                val badge = group.first().badge
                MapChip(key, badge.label, badge.colorArgb, badge.order, group.size)
            }
            .sortedWith(compareBy({ it.order }, { it.label }))

        // Chip wybrany w poprzednim widoku może już nie istnieć — wtedy „wszystkie".
        val chip = state.chip?.takeIf { key -> chips.any { it.key == key } }
        val shown = withGeo.filter { chip == null || it.badge.key == chip }

        val installs = viewPoints.flatMap { it.installs }
            .groupingBy { it }
            .eachCount()
            .toList()
            .sortedBy { it.first.lowercase() }

        return state.copy(
            counts = counts,
            baseCount = withGeo.size,
            shown = shown,
            noGeo = base.filterNot { it.hasGeo },
            chips = chips,
            chip = chip,
            people = people,
            peopleCounts = personCounts,
            installs = installs,
        )
    }

    /** Osoba punktu w bieżącym widoku: serwisant albo opiekun (główny/etapowy). */
    private fun UiState.personIdOf(point: MapPoint): String? = when {
        view.isServiceView -> point.technicianId
        ownerMode == OwnerMode.STAGE -> point.stageOwnerId
        else -> point.ownerId
    }
}
