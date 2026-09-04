package com.ekotak.teamtalk.domain.repository

import com.ekotak.teamtalk.domain.model.ServiceClient
import com.ekotak.teamtalk.domain.model.ServiceJob
import com.ekotak.teamtalk.domain.model.ServiceJobDraft
import com.ekotak.teamtalk.domain.model.ServiceJobPatch
import com.ekotak.teamtalk.domain.model.ServiceJobStatus
import com.ekotak.teamtalk.domain.model.Technician
import com.ekotak.teamtalk.domain.model.WarrantyCard
import com.ekotak.teamtalk.domain.model.WarrantyCardDraft
import com.ekotak.teamtalk.domain.model.WarrantyCardPatch
import com.ekotak.teamtalk.domain.model.WarrantyInspectionUpsert
import kotlinx.coroutines.flow.Flow

/**
 * Migawka modułu Serwis — komplet, z którego ekran składa obie dziedziny.
 * [warrantyAvailable] = czy `GET /api/warranty-cards` odpowiedziało; przy braku
 * uprawnienia albo przed migracją pokazujemy komunikat zamiast pustej listy,
 * tak samo jak panel.
 */
data class ServiceSnapshot(
    val jobs: List<ServiceJob> = emptyList(),
    val cards: List<WarrantyCard> = emptyList(),
    val clients: Map<String, ServiceClient> = emptyMap(),
    val technicians: List<Technician> = emptyList(),
    val warrantyAvailable: Boolean = true,
    /** Kiedy dane przyszły z sieci — podpis „dane z …” przy pracy bez zasięgu. */
    val syncedAt: Long? = null,
)

/** Wynik opróżniania kolejki — `RETRY` mówi robotnikowi, że sieci wciąż nie ma. */
enum class ServiceSyncResult { DONE, RETRY }

/** Odczyt i zapis modułu Serwis (zlecenia + karty przeglądów gwarancyjnych). */
interface ServiceRepository {

    /** Ostatnia migawka; emituje po każdym odświeżeniu i po zapisie. */
    fun observe(): Flow<ServiceSnapshot>

    /** Pobranie kompletu z API. Rzuca, gdy nie da się wczytać zleceń. */
    suspend fun refresh()

    suspend fun createJob(draft: ServiceJobDraft): ServiceJob

    suspend fun updateJob(id: String, patch: ServiceJobPatch): ServiceJob

    /**
     * „Wykonane” jednym dotknięciem. Ze statusu `new` idzie DWOMA krokami, bo
     * maszyna statusów board360 nie zna przeskoku `new → done`.
     */
    suspend fun completeJob(id: String, from: ServiceJobStatus): ServiceJob

    suspend fun createCard(draft: WarrantyCardDraft): WarrantyCard

    suspend fun updateCard(id: String, patch: WarrantyCardPatch): WarrantyCard

    suspend fun upsertInspection(cardId: String, input: WarrantyInspectionUpsert): WarrantyCard

    /**
     * Wysyła zmiany zrobione bez zasięgu. Woła to robotnik `WorkManagera`,
     * którego system budzi po powrocie łączności.
     */
    suspend fun syncPendingMutations(): ServiceSyncResult
}
