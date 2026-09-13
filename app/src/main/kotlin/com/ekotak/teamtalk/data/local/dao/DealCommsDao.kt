package com.ekotak.teamtalk.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.ekotak.teamtalk.data.local.entity.DealCallSummaryEntity
import com.ekotak.teamtalk.data.local.entity.DealCommMutationEntity
import com.ekotak.teamtalk.data.local.entity.DealCommentEntity
import com.ekotak.teamtalk.data.local.entity.DealWhatsappEntity

/** Cache i kolejka zakładki „Komunikacja" karty deala. */
@Dao
interface DealCommsDao {

    // ── Komunikator wewnętrzny ────────────────────────────────────────────────

    /**
     * Wątek od najstarszej wiadomości. Sortowanie po `createdAt` tekstowo
     * działa, bo daty są w ISO-8601 z UTC — to jedyny format, w którym porządek
     * leksykalny jest porządkiem chronologicznym.
     */
    @Query("SELECT * FROM deal_comments WHERE dealId = :dealId ORDER BY createdAt ASC")
    suspend fun getComments(dealId: String): List<DealCommentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertComments(comments: List<DealCommentEntity>)

    @Query("DELETE FROM deal_comments WHERE dealId = :dealId")
    suspend fun deleteComments(dealId: String)

    /** Podmiana całego wątku po świeżym odczycie z serwera. */
    @Transaction
    suspend fun replaceComments(dealId: String, comments: List<DealCommentEntity>) {
        deleteComments(dealId)
        upsertComments(comments)
    }

    // ── WhatsApp ──────────────────────────────────────────────────────────────

    @Query("SELECT * FROM deal_whatsapp WHERE dealId = :dealId ORDER BY createdAt ASC")
    suspend fun getWhatsapp(dealId: String): List<DealWhatsappEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWhatsapp(messages: List<DealWhatsappEntity>)

    @Query("DELETE FROM deal_whatsapp WHERE dealId = :dealId")
    suspend fun deleteWhatsapp(dealId: String)

    @Transaction
    suspend fun replaceWhatsapp(dealId: String, messages: List<DealWhatsappEntity>) {
        deleteWhatsapp(dealId)
        upsertWhatsapp(messages)
    }

    // ── Telefon ───────────────────────────────────────────────────────────────

    /** Rozmowy od najnowszej — tak samo jak `PhoneCallList` w panelu. */
    @Query("SELECT * FROM deal_call_summaries WHERE dealId = :dealId ORDER BY occurredAt DESC")
    suspend fun getCallSummaries(dealId: String): List<DealCallSummaryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCallSummaries(rows: List<DealCallSummaryEntity>)

    @Query("DELETE FROM deal_call_summaries WHERE dealId = :dealId")
    suspend fun deleteCallSummaries(dealId: String)

    @Transaction
    suspend fun replaceCallSummaries(dealId: String, rows: List<DealCallSummaryEntity>) {
        deleteCallSummaries(dealId)
        upsertCallSummaries(rows)
    }

    // ── Kolejka ───────────────────────────────────────────────────────────────

    /** Cała kolejka, od najstarszego wpisu — tak ją opróżnia robotnik. */
    @Query("SELECT * FROM deal_comm_mutations ORDER BY createdAt ASC")
    suspend fun getMutations(): List<DealCommMutationEntity>

    @Query(
        """
        SELECT * FROM deal_comm_mutations
        WHERE dealId = :dealId AND kind = :kind
        ORDER BY createdAt ASC
        """,
    )
    suspend fun getMutationsForDeal(dealId: String, kind: String): List<DealCommMutationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMutation(mutation: DealCommMutationEntity)

    @Query("DELETE FROM deal_comm_mutations WHERE localId = :localId")
    suspend fun deleteMutation(localId: String)
}
