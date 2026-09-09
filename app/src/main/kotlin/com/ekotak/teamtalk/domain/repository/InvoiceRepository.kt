package com.ekotak.teamtalk.domain.repository

import com.ekotak.teamtalk.domain.model.DealInvoices

/**
 * Zakładka „Faktura" karty deala — faktury z KSeF i montaże deala, z cache Room.
 *
 * Kolejki tu nie ma i nie będzie: telefon nie wystawia faktur ani nie zmienia
 * montaży. Jedyny zapis tej zakładki to DANE DO FAKTURY na karcie deala, a te
 * jadą wspólną kolejką karty (`deal_mutations`), tą samą co rodzaj budynku czy
 * termin spotkania — druga kolejka na te same pola rozjeżdżałaby się z pierwszą.
 *
 * Cache jest z tego samego powodu, co przy umowie: rozmowa o pieniądzach
 * odbywa się u klienta, gdzie zasięgu zwykle nie ma, a „nie wiem, czy faktura
 * poszła" to najgorsza z możliwych odpowiedzi.
 */
interface InvoiceRepository {

    /**
     * Faktury i montaże deala. Bez sieci oddaje ostatnie pobranie
     * ([DealInvoices.fromCache]); odmowa `ksef.view` ustawia
     * [DealInvoices.brakDostepu], a montaże i tak przychodzą — one chodzą pod
     * innym prawem.
     */
    suspend fun getInvoices(dealId: String): DealInvoices
}
