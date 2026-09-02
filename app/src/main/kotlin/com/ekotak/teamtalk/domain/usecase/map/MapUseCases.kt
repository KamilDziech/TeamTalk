package com.ekotak.teamtalk.domain.usecase.map

import com.ekotak.teamtalk.domain.model.MapSnapshot
import com.ekotak.teamtalk.domain.model.PlaceSuggestion
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

/** Podpowiedzi miejscowości dla filtra promienia. */
class SuggestPlacesUseCase @Inject constructor(
    private val repository: MapRepository,
) {
    suspend operator fun invoke(query: String): List<PlaceSuggestion> =
        repository.suggestPlaces(query)
}
