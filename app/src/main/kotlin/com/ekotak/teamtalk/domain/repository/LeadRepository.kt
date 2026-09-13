package com.ekotak.teamtalk.domain.repository

import com.ekotak.teamtalk.domain.model.LeadDraft
import com.ekotak.teamtalk.domain.model.LeadEvent
import com.ekotak.teamtalk.domain.model.LeadSubmitResult
import kotlinx.coroutines.flow.Flow

/** Kreator LEAD: zapis zgłoszenia z kolejką offline i lista targów. */
interface LeadRepository {

    /**
     * Wysyła lead. Bez zasięgu odkłada go do kolejki i zwraca [LeadSubmitResult.Queued];
     * odmowę serwera (np. brak uprawnienia, błędne dane) rzuca dalej z jego komunikatem.
     */
    suspend fun submit(draft: LeadDraft): LeadSubmitResult

    /** Liczba leadów czekających na zasięg. */
    fun observePendingCount(): Flow<Int>

    /** Wydarzenia do wyboru przy kanale „Targi" — przy braku sieci pusta lista. */
    suspend fun getEvents(): List<LeadEvent>

    /** Opróżnia kolejkę; wołane przez [com.ekotak.teamtalk.worker.LeadSyncWorker]. */
    suspend fun syncPending(): LeadSyncResult
}

data class LeadSyncResult(
    val sent: Int,
    /** Imię i nazwisko klienta → powód odmowy serwera. */
    val rejected: List<Pair<String, String>>,
    /** Sieć znowu zawiodła w trakcie — reszta czeka. */
    val incomplete: Boolean = false,
)
