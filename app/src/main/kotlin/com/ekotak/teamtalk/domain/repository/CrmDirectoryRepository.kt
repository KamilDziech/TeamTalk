package com.ekotak.teamtalk.domain.repository

import com.ekotak.teamtalk.domain.model.Category

/**
 * Dane pochodne lejka, których kartoteka potrzebuje do kolumn karty klienta:
 * instalacje, wartości szans i kontakty towarzyszące. Bez cache Room — to
 * migawka stanu panelu, a nieświeża myliłaby bardziej, niż pomaga.
 */
interface CrmDirectoryRepository {
    /** dealId → id kategorii głównych bieżących instalacji deala. */
    suspend fun getInstallationsByDeal(): Map<String, List<String>>

    /** id kategorii głównej → nazwa („Fotowoltaika", „Ogrzewanie"…). */
    suspend fun getRootCategoryNames(): Map<String, String>

    /**
     * Cały katalog technologii. Instalacje karty deala wskazują węzły dowolnej
     * głębokości, więc do opisania wyboru nie wystarczą kategorie główne —
     * potrzebna jest ścieżka („Ogrzewanie › Pompa ciepła").
     */
    suspend fun getCategories(): List<Category>

    /** dealId → wartość brutto szansy. */
    suspend fun getDealValues(): Map<String, Double>

    /** dealId → id klientów dopiętych jako kontakty towarzyszące. */
    suspend fun getDealContacts(): Map<String, List<String>>
}
