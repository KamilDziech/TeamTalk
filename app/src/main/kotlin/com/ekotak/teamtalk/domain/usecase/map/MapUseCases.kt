package com.ekotak.teamtalk.domain.usecase.map

import com.ekotak.teamtalk.domain.model.MapSnapshot
import com.ekotak.teamtalk.domain.model.PlaceSuggestion
import com.ekotak.teamtalk.domain.model.RouteHistory
import com.ekotak.teamtalk.domain.model.TrackerHealth
import com.ekotak.teamtalk.domain.repository.MapRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Punkty mapy z cache — strumień, więc odświeżenie samo przerysuje ekran. */
class ObserveMapPointsUseCase @Inject constructor(
    private val repository: MapRepository,
) {
    operator fun invoke(): Flow<MapSnapshot> = repository.observeSnapshot()
}

/** Pobranie migawki z serwera (wejście na ekran, przeciągnięcie w dół). */
class RefreshMapUseCase @Inject constructor(
    private val repository: MapRepository,
) {
    suspend operator fun invoke() = repository.refresh()
}

/** Same pozycje floty — przycisk „Odśwież pozycje" na zakładce Flota. */
class RefreshFleetUseCase @Inject constructor(
    private val repository: MapRepository,
) {
    suspend operator fun invoke() = repository.refreshFleet()
}

/** Podpowiedzi miejscowości dla filtra promienia. */
class SuggestPlacesUseCase @Inject constructor(
    private val repository: MapRepository,
) {
    suspend operator fun invoke(query: String): List<PlaceSuggestion> =
        repository.suggestPlaces(query)
}

/**
 * Historia trasy auta (ekran „Historia trasy" wywoływany z pinu Floty).
 * Wyłącznie z sieci — patrz [MapRepository.loadRouteHistory].
 */
class LoadRouteHistoryUseCase @Inject constructor(
    private val repository: MapRepository,
) {
    suspend operator fun invoke(assetId: String, fromMillis: Long, toMillis: Long): RouteHistory =
        repository.loadRouteHistory(assetId, fromMillis, toMillis)
}

/** Kondycja lokalizatorów — arkusz „Lokalizatory" na zakładce Flota. */
class LoadTrackerHealthUseCase @Inject constructor(
    private val repository: MapRepository,
) {
    suspend operator fun invoke(): List<TrackerHealth> = repository.loadTrackerHealth()
}
