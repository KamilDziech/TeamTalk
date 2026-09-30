package com.ekotak.teamtalk.domain.repository

import com.ekotak.teamtalk.domain.model.InsurancePolicy
import com.ekotak.teamtalk.domain.model.MapSnapshot
import com.ekotak.teamtalk.domain.model.PlaceSuggestion
import com.ekotak.teamtalk.domain.model.RouteHistory
import com.ekotak.teamtalk.domain.model.TrackerHealth
import com.ekotak.teamtalk.domain.model.VehicleFile
import com.ekotak.teamtalk.domain.model.VehicleHistoryItem
import com.ekotak.teamtalk.domain.model.VehicleRule
import com.ekotak.teamtalk.domain.model.VehicleTasks
import kotlinx.coroutines.flow.Flow

/**
 * Mapa zleceń — punkty pięciu widoków panelu złożone w jedną migawkę.
 * Odczyt jest offline-first: ekran czyta z cache Room (mapa otwiera się
 * w kotłowni bez zasięgu), a odświeżenie idzie po sieci i podmienia całość.
 */
interface MapRepository {
    /** Strumień punktów z cache — pusty, dopóki pierwsze pobranie nie wróci. */
    fun observeSnapshot(): Flow<MapSnapshot>

    /**
     * Pobiera komplet źródeł (deale, klienci, zlecenia, karty gwarancyjne,
     * ludzie, instalacje), składa punkty i zapisuje je jako nową migawkę.
     * Rzuca, gdy nie udało się pobrać deali albo klientów — bez nich nie ma
     * z czego zbudować mapy; brak pozostałych źródeł oznacza tylko pusty widok.
     */
    suspend fun refresh()

    /**
     * Odświeża SAME pozycje floty (dwa zapytania zamiast jedenastu). Pozycja
     * auta starzeje się w minutach, więc zakładka „Flota" ma własny przycisk —
     * ciągnięcie przy nim całej mapy byłoby marnotrawstwem, a z telefonu
     * szczególnie. Rzuca przy niepowodzeniu: cache zostaje przy ostatnich
     * znanych pozycjach, zamiast zgasić mapę po jednym wywołaniu bez zasięgu.
     */
    suspend fun refreshFleet()

    /**
     * Historia trasy jednego auta w podanym oknie: ślad, kursy, postoje,
     * przekroczenia i gwałtowna jazda — jednym wywołaniem.
     *
     * WYŁĄCZNIE Z SIECI, bez cache i to jest świadome: dzień jazdy to tysiące
     * punktów na auto, a ekran otwiera się z konkretnym pytaniem („gdzie był we
     * wtorek"), na które zapisana wczoraj migawka i tak by nie odpowiedziała.
     * Bez zasięgu ekran mówi to wprost, zamiast pokazywać nieaktualną trasę.
     */
    suspend fun loadRouteHistory(assetId: String, fromMillis: Long, toMillis: Long): RouteHistory

    /** Polisy auta — z sieci, bez cache (jak historia trasy). */
    suspend fun loadInsurance(assetId: String): List<InsurancePolicy>

    /** Kondycja lokalizatorów całej floty — „czy temu na mapie wolno ufać". */
    suspend fun loadTrackerHealth(): List<TrackerHealth>

    // ── Karta auta (flota-karta-auta.md, E5) — z sieci, bez cache ──
    suspend fun loadVehicleTasks(assetId: String): VehicleTasks
    suspend fun loadVehicleFiles(assetId: String): List<VehicleFile>
    suspend fun uploadVehicleFile(assetId: String, category: String, name: String, contentType: String, bytes: ByteArray)
    suspend fun downloadVehicleFile(downloadPath: String): ByteArray
    suspend fun loadVehicleHistory(assetId: String): List<VehicleHistoryItem>
    suspend fun loadVehicleRules(assetId: String): List<VehicleRule>

    /** Podpowiedzi miejscowości do filtra promienia (min. 3 znaki). */
    suspend fun suggestPlaces(query: String): List<PlaceSuggestion>
}
