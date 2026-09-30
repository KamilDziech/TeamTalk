package com.ekotak.teamtalk.domain.usecase.map

import com.ekotak.teamtalk.domain.model.InsurancePolicy
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

/** Polisy auta — karta „Ubezpieczenie" przy historii trasy. */
class LoadInsuranceUseCase @Inject constructor(
    private val repository: MapRepository,
) {
    suspend operator fun invoke(assetId: String): List<InsurancePolicy> = repository.loadInsurance(assetId)
}

/** Kondycja lokalizatorów — arkusz „Lokalizatory" na zakładce Flota. */
class LoadTrackerHealthUseCase @Inject constructor(
    private val repository: MapRepository,
) {
    suspend operator fun invoke(): List<TrackerHealth> = repository.loadTrackerHealth()
}

/**
 * Karta auta w telefonie (flota-karta-auta.md, E5): zakładki Zadania, Pliki,
 * Historia i Reguły. Jedna klasa zamiast sześciu — każda metoda to przelotka
 * do repozytorium, bez własnej logiki.
 */
class VehicleCardUseCases @Inject constructor(
    private val repository: MapRepository,
) {
    suspend fun tasks(assetId: String) = repository.loadVehicleTasks(assetId)
    suspend fun files(assetId: String) = repository.loadVehicleFiles(assetId)
    suspend fun upload(assetId: String, category: String, name: String, contentType: String, bytes: ByteArray) =
        repository.uploadVehicleFile(assetId, category, name, contentType, bytes)
    suspend fun download(downloadPath: String) = repository.downloadVehicleFile(downloadPath)
    suspend fun history(assetId: String) = repository.loadVehicleHistory(assetId)
    suspend fun rules(assetId: String) = repository.loadVehicleRules(assetId)
}
