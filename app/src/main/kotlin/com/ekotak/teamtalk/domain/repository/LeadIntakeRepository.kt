package com.ekotak.teamtalk.domain.repository

import com.ekotak.teamtalk.domain.model.LeadBuilding
import com.ekotak.teamtalk.domain.model.LeadIntake

/**
 * Zgłoszenia z publicznej leadowni (cennikinstalacji.pl) przypięte do deali.
 * Osobno od `DealRepository`, bo to osobny moduł board360 (`/api/intake`) —
 * deal może istnieć bez zgłoszenia i odwrotnie.
 */
interface LeadIntakeRepository {
    /** Zgłoszenie deala; `null` = deal nie pochodzi z leadowni. */
    suspend fun getLeadIntake(dealId: String): LeadIntake?

    /**
     * Zapis notatki z rozmowy / uwag klienta. Pusty tekst czyści wartość.
     * Zwraca notatkę rozwiązaną przez serwer (po wyczyszczeniu może wrócić
     * wyciąg z archiwalnej treści zgłoszenia), `null` gdy deal nie z leadowni.
     */
    suspend fun updateNote(dealId: String, note: String?): String?

    /**
     * Ręczna korekta danych budynku ze zgłoszenia (panel robi to ikonografiką
     * „Zmień dane budynku"). Zwraca komplet zapisany przez serwer; `null` gdy
     * deal nie pochodzi z leadowni.
     */
    suspend fun updateBuilding(dealId: String, building: LeadBuilding): LeadBuilding?
}
