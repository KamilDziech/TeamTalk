package com.ekotak.teamtalk.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ekotak.teamtalk.data.local.entity.ClientGroupEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ClientGroupDao {

    @Query("SELECT * FROM client_groups ORDER BY isDefault DESC, name ASC")
    fun observeAll(): Flow<List<ClientGroupEntity>>

    @Query("SELECT * FROM client_groups WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefault(): ClientGroupEntity?

    @Query("SELECT * FROM client_groups WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ClientGroupEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(group: ClientGroupEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(groups: List<ClientGroupEntity>)

    @Query("DELETE FROM client_groups WHERE id = :id")
    suspend fun deleteById(id: String)
}
