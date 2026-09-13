package com.ekotak.teamtalk.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ekotak.teamtalk.data.local.entity.LeadOutboxEntity
import kotlinx.coroutines.flow.Flow

/** Kolejka leadów czekających na zasięg. */
@Dao
interface LeadOutboxDao {

    @Query("SELECT * FROM lead_outbox ORDER BY createdAt")
    suspend fun getAll(): List<LeadOutboxEntity>

    /** Licznik na pierwszej planszy kreatora — „2 leady czekają na wysyłkę". */
    @Query("SELECT COUNT(*) FROM lead_outbox")
    fun observeCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(lead: LeadOutboxEntity)

    @Query("DELETE FROM lead_outbox WHERE clientRef = :clientRef")
    suspend fun delete(clientRef: String)
}
