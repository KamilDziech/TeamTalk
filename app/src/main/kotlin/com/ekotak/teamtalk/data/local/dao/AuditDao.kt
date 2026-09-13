package com.ekotak.teamtalk.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.ekotak.teamtalk.data.local.entity.AuditEntity
import com.ekotak.teamtalk.data.local.entity.AuditInstallationsEntity
import com.ekotak.teamtalk.data.local.entity.AuditMutationEntity
import com.ekotak.teamtalk.data.local.entity.CatalogCategoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditDao {

    // ── Audyty ────────────────────────────────────────────────────────────────

    @Query("SELECT * FROM audits WHERE dealId = :dealId ORDER BY createdAt DESC")
    suspend fun getForDeal(dealId: String): List<AuditEntity>

    @Query("SELECT * FROM audits WHERE id = :id")
    suspend fun getById(id: String): AuditEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(audit: AuditEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(audits: List<AuditEntity>)

    @Query("DELETE FROM audits WHERE id = :id")
    suspend fun deleteById(id: String)

    /**
     * Podmiana audytów deala odpowiedzią serwera. Rekordy z niewysłaną zmianą
     * ZOSTAJĄ: serwer o nich jeszcze nie wie, więc jego odpowiedź nie jest
     * w ich sprawie nowsza — skasowanie ich pokazałoby audytorowi, że jego
     * praca zniknęła.
     */
    @Transaction
    suspend fun replaceForDeal(dealId: String, audits: List<AuditEntity>) {
        deleteSyncedForDeal(dealId)
        val pending = getForDeal(dealId).mapTo(HashSet()) { it.id }
        upsertAll(audits.filterNot { it.id in pending })
    }

    @Query("DELETE FROM audits WHERE dealId = :dealId AND pendingSince IS NULL")
    suspend fun deleteSyncedForDeal(dealId: String)

    // ── Katalog technologii ───────────────────────────────────────────────────

    @Query("SELECT * FROM catalog_categories")
    suspend fun getCategories(): List<CatalogCategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategories(categories: List<CatalogCategoryEntity>)

    @Query("DELETE FROM catalog_categories")
    suspend fun deleteCategories()

    /** Katalog żyje w panelu — cache podmieniamy w całości, bez scalania. */
    @Transaction
    suspend fun replaceCategories(categories: List<CatalogCategoryEntity>) {
        deleteCategories()
        upsertCategories(categories)
    }

    // ── Migawka instalacji etapu „Audyt" ──────────────────────────────────────

    @Query("SELECT * FROM audit_installations WHERE dealId = :dealId")
    suspend fun getInstallations(dealId: String): AuditInstallationsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInstallations(installations: AuditInstallationsEntity)

    // ── Kolejka ───────────────────────────────────────────────────────────────

    /** Cała kolejka, od najstarszego zapisu — tak ją opróżnia worker. */
    @Query("SELECT * FROM audit_mutations ORDER BY createdAt ASC")
    suspend fun getMutations(): List<AuditMutationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMutation(mutation: AuditMutationEntity)

    @Query("DELETE FROM audit_mutations WHERE auditId = :auditId")
    suspend fun deleteMutations(auditId: String)

    /**
     * Kasujemy po `field`, nie po całym audycie: kiedy worker wysyła kolejkę,
     * audytor może w tym samym czasie zapisać formularz jeszcze raz. Sprzątamy
     * więc dokładnie to, co poszło na serwer.
     */
    @Query("DELETE FROM audit_mutations WHERE auditId = :auditId AND field = :field")
    suspend fun deleteMutation(auditId: String, field: String)

    /**
     * Rekord dostał id od serwera — przepisujemy pod nie kolejkę i zamieniamy
     * `POST` na `PATCH` ([formField]). Bez tego zapis zrobiony w trakcie
     * wysyłki poszedłby jako kolejny `POST` i deal miałby DWA formularze tego
     * samego węzła.
     */
    @Query(
        "UPDATE audit_mutations SET auditId = :newId, field = :formField " +
            "WHERE auditId = :localId",
    )
    suspend fun rekeyMutations(localId: String, newId: String, formField: String)

    // ── Konflikty zapisu (409 AUDIT_STALE) ────────────────────────────────────

    @Query("SELECT * FROM audit_mutations WHERE auditId = :auditId ORDER BY createdAt ASC")
    suspend fun getMutationsFor(auditId: String): List<AuditMutationEntity>

    /**
     * Wiersze w konflikcie dla deala — ekran karty pokazuje z nich okno
     * „nadpisz / porzuć moje". Flow, bo konflikt wykrywa zwykle worker w tle,
     * kiedy karta już stoi na ekranie.
     */
    @Query(
        "SELECT * FROM audit_mutations WHERE dealId = :dealId AND conflictJson IS NOT NULL " +
            "ORDER BY conflictAt ASC",
    )
    fun observeConflicts(dealId: String): Flow<List<AuditMutationEntity>>

    /**
     * Sprzątanie po udanej wysyłce, ale TYLKO gdy wiersz jest tym, który
     * poszedł (`createdAt` się zgadza). Zapis zrobiony w trakcie wysyłki ma ten
     * sam klucz i nowy `createdAt` — ten zostaje i dostaje nową bazę.
     *
     * @return liczba skasowanych wierszy; 0 = w międzyczasie przyszedł nowszy zapis.
     */
    @Query(
        "DELETE FROM audit_mutations WHERE auditId = :auditId AND field = :field " +
            "AND createdAt = :createdAt",
    )
    suspend fun deleteMutationIfUnchanged(auditId: String, field: String, createdAt: Long): Int

    /** Nowa baza wiersza — po udanej wysyłce albo po „nadpisz". Zdejmuje też konflikt. */
    @Query(
        "UPDATE audit_mutations SET baseUpdatedAt = :base, conflictJson = NULL, " +
            "conflictAt = NULL WHERE auditId = :auditId AND field = :field",
    )
    suspend fun rebaseMutation(auditId: String, field: String, base: String?)

    @Query(
        "UPDATE audit_mutations SET conflictJson = :conflictJson, conflictAt = :at " +
            "WHERE auditId = :auditId AND field = :field",
    )
    suspend fun markConflict(auditId: String, field: String, conflictJson: String, at: Long)
}
