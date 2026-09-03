package com.ekotak.teamtalk.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ekotak.teamtalk.data.local.entity.ClientEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ClientDao {

    @Query("SELECT * FROM clients ORDER BY lastName, firstName")
    fun observeAll(): Flow<List<ClientEntity>>

    // Szukania nie ma tu celowo: `LIKE` porównuje hasło z każdą kolumną osobno
    // i nie zna polskich ogonków, więc filtruje ClientRepositoryImpl.

    @Query("SELECT * FROM clients WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<ClientEntity?>

    @Query("SELECT * FROM clients WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ClientEntity?

    @Query("SELECT * FROM clients WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<String>): List<ClientEntity>

    @Query("SELECT * FROM clients WHERE phone = :phone OR phone2 = :phone LIMIT 1")
    suspend fun getByPhone(phone: String): ClientEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(clients: List<ClientEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(client: ClientEntity)

    @Query("DELETE FROM clients WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("DELETE FROM clients WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM clients")
    suspend fun deleteAll()
}
