package com.ekotak.teamtalk.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.ekotak.teamtalk.data.local.entity.TaskEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    /**
     * Kolejność wyjściowa: najpierw z terminem (od najbliższego), potem bez —
     * lista i tak przesortuje się w ViewModelu, ale dzięki temu pierwsze klatki
     * po starcie aplikacji nie pokazują przypadkowej kolejności.
     */
    @Query("SELECT * FROM tasks ORDER BY dueAt IS NULL, dueAt ASC, createdAt DESC")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    fun observeById(id: String): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tasks: List<TaskEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM tasks")
    suspend fun deleteAll()

    /**
     * Podmiana całej listy po odświeżeniu. Kartoteka klientów robi sam upsert,
     * tu potrzebna jest podmiana: zadanie usunięte albo zamknięte w panelu ma
     * zniknąć z telefonu, a nie zostać w cache na zawsze.
     */
    @Transaction
    suspend fun replaceAll(tasks: List<TaskEntity>) {
        deleteAll()
        upsertAll(tasks)
    }
}
