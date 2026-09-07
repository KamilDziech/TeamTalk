package com.ekotak.teamtalk.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.ekotak.teamtalk.data.local.entity.DealDocumentEntity
import com.ekotak.teamtalk.data.local.entity.DocumentMutationEntity

/** Cache i kolejka zakładki „Pliki" karty deala. */
@Dao
interface DocumentDao {

    // ── Pliki ─────────────────────────────────────────────────────────────────

    /**
     * Pliki deala od najstarszego — ta sama kolejność, co w panelu (serwer
     * zwraca je rosnąco po dacie), więc sekcje wyglądają tak samo na obu
     * ekranach. Wgrane bez zasięgu mają najświeższą datę, więc lądują na końcu
     * swojej sekcji.
     */
    @Query("SELECT * FROM deal_documents WHERE dealId = :dealId ORDER BY createdAt ASC")
    suspend fun getDocuments(dealId: String): List<DealDocumentEntity>

    @Query("SELECT * FROM deal_documents WHERE id = :id")
    suspend fun getDocument(id: String): DealDocumentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(document: DealDocumentEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(documents: List<DealDocumentEntity>)

    @Query("DELETE FROM deal_documents WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM deal_documents WHERE dealId = :dealId AND pending = 0")
    suspend fun deleteSynced(dealId: String)

    /**
     * Podmiana plików deala odpowiedzią serwera. Wiersze czekające w kolejce
     * (`pending`) ZOSTAJĄ: serwer o nich jeszcze nie wie, więc jego odpowiedź
     * nie jest w ich sprawie nowsza, a zniknięcie zdjęcia zrobionego przed
     * chwilą w kotłowni wyglądałoby, jakby aparat go nie zapisał.
     */
    @Transaction
    suspend fun replaceDocuments(dealId: String, documents: List<DealDocumentEntity>) {
        deleteSynced(dealId)
        upsertAll(documents)
    }

    // ── Kolejka ───────────────────────────────────────────────────────────────

    @Query("SELECT * FROM document_mutations ORDER BY createdAt ASC")
    suspend fun getMutations(): List<DocumentMutationEntity>

    @Query("SELECT * FROM document_mutations WHERE dealId = :dealId ORDER BY createdAt ASC")
    suspend fun getMutationsForDeal(dealId: String): List<DocumentMutationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMutation(mutation: DocumentMutationEntity)

    @Query("DELETE FROM document_mutations WHERE targetId = :targetId AND kind = :kind")
    suspend fun deleteMutation(targetId: String, kind: String)

    @Query("DELETE FROM document_mutations WHERE targetId = :targetId")
    suspend fun deleteMutationsFor(targetId: String)
}
