package com.ekotak.teamtalk.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ekotak.teamtalk.data.local.entity.VoiceReportEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VoiceReportDao {

    @Query("SELECT * FROM voice_reports WHERE callLogId = :callLogId ORDER BY createdAt DESC")
    fun observeByCallLogId(callLogId: String): Flow<List<VoiceReportEntity>>

    @Query("SELECT * FROM voice_reports WHERE callLogId IN (:callLogIds) ORDER BY createdAt DESC")
    fun observeByCallLogIds(callLogIds: List<String>): Flow<List<VoiceReportEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(reports: List<VoiceReportEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(report: VoiceReportEntity)

    @Query("DELETE FROM voice_reports WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM voice_reports")
    suspend fun deleteAll()
}
