package com.ekotak.teamtalk.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ekotak.teamtalk.data.local.entity.DealInstallationsEntity
import com.ekotak.teamtalk.data.local.entity.DealMutationEntity

@Dao
interface DealDao {

    // ── Migawki instalacji ────────────────────────────────────────────────────

    @Query("SELECT * FROM deal_installations WHERE dealId = :dealId")
    suspend fun getInstallations(dealId: String): DealInstallationsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInstallations(installations: DealInstallationsEntity)

    // ── Kolejka ───────────────────────────────────────────────────────────────

    /** Cała kolejka, od najstarszej zmiany — tak ją opróżnia worker. */
    @Query("SELECT * FROM deal_mutations ORDER BY createdAt ASC")
    suspend fun getMutations(): List<DealMutationEntity>

    @Query("SELECT * FROM deal_mutations WHERE dealId = :dealId ORDER BY createdAt ASC")
    suspend fun getMutationsForDeal(dealId: String): List<DealMutationEntity>

    @Query("SELECT * FROM deal_mutations WHERE dealId = :dealId AND kind = :kind")
    suspend fun getMutation(dealId: String, kind: String): DealMutationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMutation(mutation: DealMutationEntity)

    @Query("DELETE FROM deal_mutations WHERE dealId = :dealId AND kind = :kind")
    suspend fun deleteMutation(dealId: String, kind: String)
}
