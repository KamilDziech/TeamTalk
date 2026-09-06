package com.ekotak.teamtalk.domain.repository

import com.ekotak.teamtalk.domain.ufh.OfferPricing

/**
 * Cennik jednostkowy dla zakładki „Oferta" — trzy źródła panelu w jednym
 * odczycie: kartoteka Magazynu, ustawienia firmowe (producent szafek, materiały
 * domyślne, narzut węzła) i zestawy Warunków finansowych.
 *
 * Sam rachunek robi telefon (`domain/ufh`), tak jak w panelu robi go
 * przeglądarka — tu idą wyłącznie dane. Bez zasięgu odpowiedzi wracają
 * z ostatniego pobrania, więc oferta z kwotami otwiera się także u klienta
 * w domu bez zasięgu.
 */
interface OfferPricingRepository {

    /**
     * Ceny jednostkowe i narzut węzła-właściciela formularza audytu.
     *
     * @param categoryId węzeł, na którym wisi formularz audytu (i narzut).
     * @param categoryName nazwa tego węzła — po niej poznajemy instalację.
     * @param categoryIdPath ścieżka id od korzenia do węzła; zestawy Warunków
     *   finansowych dziedziczą się w dół drzewa, więc bierzemy je z całej.
     * @return `null` = dla tej instalacji nie ma rozpisanej formuły ceny
     *   (dziś tylko ogrzewanie podłogowe) — oferta pokazuje wtedy zakres bez kwot.
     */
    suspend fun getPricing(
        categoryId: String,
        categoryName: String,
        categoryIdPath: List<String>,
    ): OfferPricing?
}
