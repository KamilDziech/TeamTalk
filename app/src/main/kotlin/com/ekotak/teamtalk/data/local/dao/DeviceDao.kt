package com.ekotak.teamtalk.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ekotak.teamtalk.data.local.entity.DeviceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {

    @Query("SELECT * FROM devices ORDER BY lastActiveAt DESC")
    fun observeAll(): Flow<List<DeviceEntity>>

    @Query("SELECT * FROM devices WHERE pushToken != :excludeToken ORDER BY lastActiveAt DESC")
    fun observeExcluding(excludeToken: String): Flow<List<DeviceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(devices: List<DeviceEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(device: DeviceEntity)

    @Query("DELETE FROM devices")
    suspend fun deleteAll()
}
