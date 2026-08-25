package com.ekotak.teamtalk.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ekotak.teamtalk.data.local.entity.CallLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CallLogDao {

    @Query("""
        SELECT * FROM call_logs
        WHERE (:clientId  IS NULL OR clientId  = :clientId)
          AND (:direction IS NULL OR direction = :direction)
          AND (:since     IS NULL OR startedAt >= :since)
        ORDER BY startedAt DESC
    """)
    fun observeFiltered(
        clientId: String?,
        direction: String?,
        since: String?,
    ): Flow<List<CallLogEntity>>

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
