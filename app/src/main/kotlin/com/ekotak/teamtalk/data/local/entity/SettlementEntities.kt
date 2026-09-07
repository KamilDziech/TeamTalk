package com.ekotak.teamtalk.data.local.entity

import androidx.room.Entity

/**
 * Cache i kolejka zakładki „Rozliczenie". Rozliczenie domyka się na budowie,
 * po odbiorze — a tam zasięgu zwykle nie ma. Zatwierdzenie zrobione w kotłowni
 * ma przeżyć drogę do samochodu.
 */

/**
 * Zatwierdzona migawka rozliczenia instalacji. `breakdownJson` zostaje surowy:
 * to kontrakt z panelem, a rozbieranie go na kolumny tylko po to, żeby złożyć
 * z powrotem, gubiłoby pola, których telefon nie zna.
 */
@Entity(tableName = "deal_settlements", primaryKeys = ["dealId", "categoryId"])
data class DealSettlementEntity(
    val dealId: String,
    val categoryId: String,
    val totalPoints: Double,
    val breakdownJson: String,
    val approvedById: String?,
    val approvedAt: String,
    val syncedAt: Long,
)

/**
 * Decyzja o rozliczeniu czekająca na wysyłkę.
 *
 * Klucz (`dealId`, `categoryId`) sprawia, że kolejna decyzja o tej samej
 * instalacji NADPISUJE poprzednią: zatwierdzenie i cofnięcie tej samej pozycji
 * to jedno pole tabeli w bazie, więc wysyłanie obu po kolei tylko dublowałoby
 * ruch. Liczy się ostatnia decyzja człowieka.
 */
@Entity(tableName = "settlement_mutations", primaryKeys = ["dealId", "categoryId"])
data class SettlementMutationEntity(
    val dealId: String,
    val categoryId: String,
    /** [KIND_APPROVE] albo [KIND_REVOKE]. */
    val kind: String,
    /** Ciało `PUT`-a (`{"totalPoints":…,"breakdown":{…}}`); puste przy cofnięciu. */
    val payload: String,
    val createdAt: Long,
) {
    companion object {
        /** `PUT /financial-terms/settlements/{dealId}/{categoryId}`. */
        const val KIND_APPROVE = "approve"

        /** `DELETE /financial-terms/settlements/{dealId}/{categoryId}`. */
        const val KIND_REVOKE = "revoke"
    }
}
