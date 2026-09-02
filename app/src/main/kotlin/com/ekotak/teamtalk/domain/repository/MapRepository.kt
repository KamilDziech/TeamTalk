package com.ekotak.teamtalk.domain.repository

import com.ekotak.teamtalk.domain.model.MapSnapshot
import com.ekotak.teamtalk.domain.model.PlaceSuggestion
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

    /** Podpowiedzi miejscowości do filtra promienia (min. 3 znaki). */
    suspend fun suggestPlaces(query: String): List<PlaceSuggestion>
}
