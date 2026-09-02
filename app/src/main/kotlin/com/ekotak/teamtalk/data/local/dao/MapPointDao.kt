package com.ekotak.teamtalk.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.ekotak.teamtalk.data.local.entity.MapPointEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MapPointDao {

    /** Kolejność bez znaczenia dla mapy — sortuje i grupuje ViewModel. */
    @Query("SELECT * FROM map_points")
    fun observeAll(): Flow<List<MapPointEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(points: List<MapPointEntity>)

    @Query("DELETE FROM map_points")
    suspend fun deleteAll()

    /**
     * Podmiana całej migawki. Punkt znika z mapy także wtedy, gdy deal wypadł
     * z lejka albo zlecenie skasowano w panelu — dlatego kasujemy przed
     * zapisem, zamiast nadpisywać po identyfikatorach.
     */
    @Transaction
    suspend fun replaceAll(points: List<MapPointEntity>) {
        deleteAll()
        upsertAll(points)
    }
}
