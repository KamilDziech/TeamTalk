package com.ekotak.teamtalk.presentation.map

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ekotak.teamtalk.domain.model.RouteHistory
import com.ekotak.teamtalk.domain.usecase.map.LoadRouteHistoryUseCase
import com.ekotak.teamtalk.presentation.crm.crmErrorMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/** Okno historii — te same trzy skróty co w panelu plus wybrany dzień. */
enum class RouteWindow(val label: String, val hours: Int) {
    H6("6 h", 6),
    H24("24 h", 24),
    H72("3 dni", 72),
}

/**
 * Historia trasy jednego auta.
 *
 * Dane idą WYŁĄCZNIE z sieci (patrz `MapRepository.loadRouteHistory`) i tu jest
 * to widać: brak zasięgu kończy się komunikatem, a nie pustą trasą. Ekran
 * otwiera się z konkretnym pytaniem („gdzie był we wtorek"), na które zapisana
 * wczoraj migawka i tak by nie odpowiedziała.
 */
@HiltViewModel
class RouteHistoryViewModel @Inject constructor(
    private val loadRouteHistory: LoadRouteHistoryUseCase,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val assetId: String = savedStateHandle["assetId"] ?: ""
    val assetName: String = savedStateHandle.get<String>("name").orEmpty().ifBlank { "Pojazd" }
    val registration: String? = savedStateHandle.get<String>("reg")?.takeIf { it.isNotBlank() }

    data class UiState(
        val isLoading: Boolean = true,
        val error: String? = null,
        val window: RouteWindow = RouteWindow.H24,
        /** Wybrany dzień (epoch millis początku doby) — null = „ostatnie N godzin". */
        val dayMillis: Long? = null,
        val history: RouteHistory? = null,
        /** Punkt zaznaczony na osi czasu — mapa dosuwa do niego kadr. */
        val focus: Pair<Double, Double>? = null,
        /** Licznik żądań kadrowania; zmiana = mapa ma się dopasować od nowa. */
        val fitRequest: Int = 0,
    )

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun setWindow(window: RouteWindow) {
        _uiState.update { it.copy(window = window, dayMillis = null) }
        load()
    }

    /** Wybór konkretnej doby — „co robił we wtorek", a nie „ostatnie N godzin". */
    fun setDay(millis: Long) {
        _uiState.update { it.copy(dayMillis = startOfLocalDay(millis)) }
        load()
    }

    fun focusOn(lat: Double, lng: Double) {
        _uiState.update { it.copy(focus = lat to lng) }
    }

    fun requestFit() {
        _uiState.update { it.copy(focus = null, fitRequest = it.fitRequest + 1) }
    }

    fun reload() = load()

    private fun load() {
        if (assetId.isBlank()) {
            _uiState.update { it.copy(isLoading = false, error = "Brak identyfikatora pojazdu.") }
            return
        }
        val state = _uiState.value
        val (from, to) = windowMillis(state.window, state.dayMillis)
        _uiState.update { it.copy(isLoading = true, error = null) }
        viewModelScope.launch {
            val result = runCatching { loadRouteHistory(assetId, from, to) }
            _uiState.update {
                result.fold(
                    onSuccess = { history ->
                        it.copy(
                            isLoading = false,
                            error = null,
                            history = history,
                            focus = null,
                            fitRequest = it.fitRequest + 1,
                        )
                    },
                    onFailure = { error ->
                        // Trasy nie podmieniamy na pustą: jeśli coś już wisi na
                        // ekranie, lepiej zostawić to z komunikatem niż zgasić
                        // mapę po jednym wywołaniu bez zasięgu.
                        it.copy(
                            isLoading = false,
                            error = crmErrorMessage(error, "Nie udało się pobrać historii trasy"),
                        )
                    },
                )
            }
        }
    }
}

/** Doba lokalna albo ostatnie N godzin — granice okna liczone w strefie telefonu. */
private fun windowMillis(window: RouteWindow, dayMillis: Long?): Pair<Long, Long> {
    if (dayMillis != null) return dayMillis to (dayMillis + 24L * 3_600_000L)
    val now = System.currentTimeMillis()
    return (now - window.hours * 3_600_000L) to now
}

/**
 * Początek doby LOKALNEJ dla znacznika z kalendarza.
 *
 * Material DatePicker oddaje północ UTC wybranego dnia, a doba kierowcy zaczyna
 * się o północy u niego — bez tej zamiany „wtorek" w lecie znaczyłby okno od
 * 02:00 wtorku do 02:00 środy.
 */
private fun startOfLocalDay(utcMillis: Long): Long {
    val zone = ZoneId.systemDefault()
    val date = java.time.Instant.ofEpochMilli(utcMillis).atZone(ZoneId.of("UTC")).toLocalDate()
    return LocalDate.of(date.year, date.month, date.dayOfMonth)
        .atStartOfDay(zone)
        .toInstant()
        .toEpochMilli()
}
