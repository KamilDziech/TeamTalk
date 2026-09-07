package com.ekotak.teamtalk.domain.repository

import com.ekotak.teamtalk.domain.model.DealSettlement
import com.ekotak.teamtalk.domain.ufh.PointScheme
import kotlinx.serialization.json.JsonObject

/**
 * Zakładka „Rozliczenie" karty deala — zatwierdzone migawki i cennik punktowy,
 * z cache Room i kolejką offline.
 *
 * Rachunek robi telefon (`domain/ufh`), tak jak w panelu robi go przeglądarka —
 * tędy idą wyłącznie dane. Cache jest tu z tego samego powodu, co przy audycie:
 * rozliczenie ogląda się i domyka NA BUDOWIE, po odbiorze, gdzie zasięgu zwykle
 * nie ma. Zatwierdzenie zrobione w kotłowni musi przeżyć drogę do samochodu.
 */
interface SettlementRepository {

    /**
     * Zatwierdzone rozliczenia deala. Bez sieci — kopia z ostatniego pobrania
     * z nałożoną kolejką ([DealSettlement.pending]).
     */
    suspend fun getSettlements(dealId: String): List<DealSettlement>

    /**
     * Zestawy punktowe Warunków finansowych (wszystkie węzły naraz — ważą kilka
     * kilobajtów, a instalacji deala bywa kilka). Bez sieci z ostatniego
     * pobrania; odmowa `financial.terms.view` daje pustą listę, czyli rachunek
     * bez stawek, a nie brak zakładki.
     */
    suspend fun getSchemes(): List<PointScheme>

    /**
     * Zatwierdzenie (zamrożenie) rozliczenia jednej instalacji. Wymaga
     * `financial.terms.manage` — odmowa serwera leci dalej jako wyjątek,
     * bo ponawianie jej w kolejce niczego nie zmieni.
     */
    suspend fun approve(
        dealId: String,
        categoryId: String,
        totalPoints: Double,
        breakdown: JsonObject,
    ): SettlementSaveResult

    /** Cofnięcie zatwierdzenia — rozliczenie wraca do liczenia na żywo. */
    suspend fun revoke(dealId: String, categoryId: String): SettlementSaveResult

    /** Opróżnienie kolejki — woła `SettlementSyncWorker`, gdy wróci sieć. */
    suspend fun syncPendingMutations(): SettlementSyncResult
}

/** Czy decyzja poszła na serwer, czy czeka w telefonie na zasięg. */
enum class SettlementSaveResult { SENT, QUEUED }

/** Wynik przebiegu kolejki: `RETRY` = sieć znowu zawiodła, wpisy zostają. */
enum class SettlementSyncResult { DONE, RETRY }
