package com.ekotak.teamtalk.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ekotak.teamtalk.data.local.entity.ProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {

    @Query("SELECT * FROM profiles ORDER BY createdAt ASC")
    fun observeAll(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<ProfileEntity?>

    @Query("SELECT * FROM profiles WHERE isAdmin = 1 ORDER BY createdAt ASC")
    fun observeAdmins(): Flow<List<ProfileEntity>>

    @Query("SELECT * FROM profiles WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(profiles: List<ProfileEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: ProfileEntity)

    @Query("DELETE FROM profiles")
    suspend fun deleteAll()
}
