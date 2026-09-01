package com.ekotak.teamtalk.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.ekotak.teamtalk.data.local.entity.TaskMutationEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskMutationDao {

    /** Zadania z niewysłaną zmianą — znacznik „czeka na wysyłkę" na liście. */
    @Query("SELECT DISTINCT taskId FROM task_mutations")
    fun observePendingTaskIds(): Flow<List<String>>

    /** Cała kolejka, od najstarszej zmiany — tak ją opróżnia worker. */
    @Query("SELECT * FROM task_mutations ORDER BY createdAt ASC")
    suspend fun getAll(): List<TaskMutationEntity>

    @Query("SELECT * FROM task_mutations WHERE taskId = :taskId")
    suspend fun getForTask(taskId: String): List<TaskMutationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(mutations: List<TaskMutationEntity>)

    /**
     * Kasujemy po `field`, nie po całym zadaniu: kiedy worker wysyła kolejkę,
     * człowiek może w tym samym czasie zmienić coś jeszcze. Sprzątamy więc
     * dokładnie to, co poszło na serwer.
     */
    @Query("DELETE FROM task_mutations WHERE taskId = :taskId AND field IN (:fields)")
    suspend fun delete(taskId: String, fields: List<String>)

    @Query("DELETE FROM task_mutations WHERE taskId = :taskId")
    suspend fun deleteForTask(taskId: String)

    @Query("DELETE FROM task_mutations")
    suspend fun deleteAll()
}
