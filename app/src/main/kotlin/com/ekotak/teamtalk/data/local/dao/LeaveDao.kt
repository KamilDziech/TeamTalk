package com.ekotak.teamtalk.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.ekotak.teamtalk.data.local.entity.LeaveAbsenceEntity
import com.ekotak.teamtalk.data.local.entity.LeaveBalanceEntity
import com.ekotak.teamtalk.data.local.entity.LeaveMutationEntity
import com.ekotak.teamtalk.data.local.entity.LeaveRequestEntity
import kotlinx.coroutines.flow.Flow

/** Cache i kolejka modułu Urlop. */
@Dao
interface LeaveDao {

    // ── Strumienie dla ekranu ─────────────────────────────────────────────────
    // Liczniki, kalendarz i lista wniosków czytają jedną migawkę, więc zapis
    // jednego wniosku odświeża je razem — bez trzech osobnych zapytań.

    @Query("SELECT * FROM leave_requests WHERE mine = 1 ORDER BY startDate DESC")
    fun observeMyRequests(): Flow<List<LeaveRequestEntity>>

    @Query("SELECT * FROM leave_absences ORDER BY startDate ASC")
    fun observeAbsences(): Flow<List<LeaveAbsenceEntity>>

    @Query("SELECT * FROM leave_balance WHERE year = :year")
    fun observeBalance(year: Int): Flow<LeaveBalanceEntity?>

    /** Identyfikatory wniosków z czymś w kolejce — po nich rysujemy „W kolejce". */
    @Query("SELECT DISTINCT targetId FROM leave_mutations")
    fun observePendingIds(): Flow<List<String>>

    // ── Wnioski (moje i ze skrzynki zwierzchnika) ─────────────────────────────

    /** Moje wnioski — od najbliższego, tak jak lista na ekranie. */
    @Query("SELECT * FROM leave_requests WHERE mine = 1 ORDER BY startDate DESC")
    suspend fun getMyRequests(): List<LeaveRequestEntity>

    @Query("SELECT * FROM leave_requests WHERE mine = 0 ORDER BY startDate ASC")
    suspend fun getInboxRequests(): List<LeaveRequestEntity>

    @Query("SELECT * FROM leave_requests WHERE id = :id")
    suspend fun getRequest(id: String): LeaveRequestEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRequest(request: LeaveRequestEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRequests(requests: List<LeaveRequestEntity>)

    @Query("DELETE FROM leave_requests WHERE id = :id")
    suspend fun deleteRequest(id: String)

    @Query("DELETE FROM leave_requests WHERE mine = :mine AND id NOT LIKE 'local:%'")
    suspend fun deleteSyncedRequests(mine: Boolean)

    /**
     * Podmiana jednego zbioru (moje albo skrzynka) odpowiedzią serwera.
     * Wnioski złożone offline (`local:…`) ZOSTAJĄ — serwer o nich jeszcze nie
     * wie, więc jego odpowiedź nie jest w ich sprawie nowsza, a ich zniknięcie
     * wyglądałoby, jakby wniosek nie powstał, i człowiek złożyłby go drugi raz.
     */
    @Transaction
    suspend fun replaceRequests(mine: Boolean, requests: List<LeaveRequestEntity>) {
        deleteSyncedRequests(mine)
        upsertRequests(requests)
    }

    // ── Cudze nieobecności (tło kalendarza, oś czasu) ─────────────────────────

    @Query("SELECT * FROM leave_absences ORDER BY startDate ASC")
    suspend fun getAbsences(): List<LeaveAbsenceEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAbsences(absences: List<LeaveAbsenceEntity>)

    @Query("DELETE FROM leave_absences")
    suspend fun deleteAbsences()

    /** Nieobecności żyją na serwerze — cache podmieniamy w całości, bez scalania. */
    @Transaction
    suspend fun replaceAbsences(absences: List<LeaveAbsenceEntity>) {
        deleteAbsences()
        upsertAbsences(absences)
    }

    // ── Liczniki ──────────────────────────────────────────────────────────────

    @Query("SELECT * FROM leave_balance WHERE year = :year")
    suspend fun getBalance(year: Int): LeaveBalanceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBalance(balance: LeaveBalanceEntity)

    // ── Kolejka ───────────────────────────────────────────────────────────────

    /** Cała kolejka, od najstarszego zapisu — tak ją opróżnia worker. */
    @Query("SELECT * FROM leave_mutations ORDER BY createdAt ASC")
    suspend fun getMutations(): List<LeaveMutationEntity>

    /** Co czeka na wysyłkę dla tego wniosku — po tym rysujemy znacznik „w kolejce". */
    @Query("SELECT * FROM leave_mutations WHERE targetId = :targetId")
    suspend fun getMutationsFor(targetId: String): List<LeaveMutationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMutation(mutation: LeaveMutationEntity)

    /**
     * Kasujemy dokładnie ten wiersz (para `targetId` + `kind`), nie całą kolejkę
     * wniosku: gdy worker wysyła zmianę dat, człowiek może w tej samej chwili
     * anulować wniosek — sprzątamy więc tylko to, co naprawdę poszło na serwer.
     */
    @Query("DELETE FROM leave_mutations WHERE targetId = :targetId AND kind = :kind")
    suspend fun deleteMutation(targetId: String, kind: String)

    /** Po wysłaniu nowego wniosku znika cała kolejka jego lokalnego id. */
    @Query("DELETE FROM leave_mutations WHERE targetId = :targetId")
    suspend fun deleteMutationsFor(targetId: String)
}
