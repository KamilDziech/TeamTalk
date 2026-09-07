package com.ekotak.teamtalk.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.ekotak.teamtalk.data.local.entity.DealSettlementEntity
import com.ekotak.teamtalk.data.local.entity.SettlementMutationEntity

@Dao
interface SettlementDao {

    // ── Zatwierdzone migawki ──────────────────────────────────────────────────

    @Query("SELECT * FROM deal_settlements WHERE dealId = :dealId")
    suspend fun getForDeal(dealId: String): List<DealSettlementEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(settlement: DealSettlementEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(settlements: List<DealSettlementEntity>)

    @Query("DELETE FROM deal_settlements WHERE dealId = :dealId AND categoryId = :categoryId")
    suspend fun delete(dealId: String, categoryId: String)

    @Query("DELETE FROM deal_settlements WHERE dealId = :dealId")
    suspend fun deleteForDeal(dealId: String)

    /**
     * Podmiana migawek deala odpowiedzią serwera. Cache trzyma to, co powiedział
     * serwer — niewysłane decyzje nakładamy dopiero przy odczycie, z kolejki.
     */
    @Transaction
    suspend fun replaceForDeal(dealId: String, settlements: List<DealSettlementEntity>) {
        deleteForDeal(dealId)
        upsertAll(settlements)
    }

    // ── Kolejka ───────────────────────────────────────────────────────────────

    /** Cała kolejka, od najstarszej decyzji — tak ją opróżnia worker. */
    @Query("SELECT * FROM settlement_mutations ORDER BY createdAt ASC")
    suspend fun getMutations(): List<SettlementMutationEntity>

    @Query("SELECT * FROM settlement_mutations WHERE dealId = :dealId")
    suspend fun getMutationsForDeal(dealId: String): List<SettlementMutationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMutation(mutation: SettlementMutationEntity)

    @Query("DELETE FROM settlement_mutations WHERE dealId = :dealId AND categoryId = :categoryId")
    suspend fun deleteMutation(dealId: String, categoryId: String)
}
