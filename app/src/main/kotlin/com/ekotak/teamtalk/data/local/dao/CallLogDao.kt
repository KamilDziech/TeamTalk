package com.ekotak.teamtalk.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ekotak.teamtalk.data.local.entity.CallLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CallLogDao {

    /**
     * General filtered query. All parameters are optional — passing null skips the condition.
     * Handles the most common filter combinations used by the UI.
     */
    @Query("""
        SELECT * FROM call_logs
        WHERE (:statusEq      IS NULL OR status      =  :statusEq)
          AND (:statusNeq     IS NULL OR status      != :statusNeq)
          AND (:typeNeq       IS NULL OR type        != :typeNeq)
          AND (:typeNeq2      IS NULL OR type        != :typeNeq2)
          AND (:clientIdEq    IS NULL OR clientId    =  :clientIdEq)
          AND (:callerPhoneEq IS NULL OR callerPhone =  :callerPhoneEq)
          AND (:timestampGte  IS NULL OR timestamp   >= :timestampGte)
          AND (:timestampLte  IS NULL OR timestamp   <= :timestampLte)
        ORDER BY timestamp DESC
    """)
    fun observeFiltered(
        statusEq: String?,
        statusNeq: String?,
        typeNeq: String?,
        typeNeq2: String?,
        clientIdEq: String?,
        callerPhoneEq: String?,
        timestampGte: String?,
        timestampLte: String?,
    ): Flow<List<CallLogEntity>>

    /** For status_in filters (e.g. missed + reserved). */
    @Query("SELECT * FROM call_logs WHERE status IN (:statuses) ORDER BY timestamp DESC")
    fun observeByStatusIn(statuses: List<String>): Flow<List<CallLogEntity>>

    @Query("SELECT * FROM call_logs WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): CallLogEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(callLogs: List<CallLogEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(callLog: CallLogEntity)

    @Query("DELETE FROM call_logs WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM call_logs")
    suspend fun deleteAll()
}
