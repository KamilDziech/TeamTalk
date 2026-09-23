package com.ekotak.teamtalk.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.ekotak.teamtalk.data.local.entity.ChatMessageEntity
import com.ekotak.teamtalk.data.local.entity.ChatMutationEntity
import com.ekotak.teamtalk.data.local.entity.ChatPersonEntity
import com.ekotak.teamtalk.data.local.entity.ChatThreadEntity

/** Cache i kolejka Komunikatora. */
@Dao
interface ChatDao {

    // ── Skrzynka ──────────────────────────────────────────────────────────────

    /** Przypięte na górze, potem po dacie ostatniej wiadomości — ta sama
     *  kolejność, co w panelu, żeby lista nie „przeskakiwała" po zalogowaniu. */
    @Query(
        """
        SELECT * FROM chat_threads
        WHERE archived = :archived
        ORDER BY pinned DESC, lastAt DESC
        """,
    )
    suspend fun getThreads(archived: Boolean): List<ChatThreadEntity>

    @Query("SELECT * FROM chat_threads WHERE id = :id")
    suspend fun getThread(id: String): ChatThreadEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertThreads(threads: List<ChatThreadEntity>)

    @Query("DELETE FROM chat_threads WHERE archived = :archived")
    suspend fun deleteThreads(archived: Boolean)

    /** Podmiana widoku po świeżym odczycie — osobno dla listy i dla archiwum,
     *  żeby wejście w archiwum nie kasowało zwykłej skrzynki. */
    @Transaction
    suspend fun replaceThreads(archived: Boolean, threads: List<ChatThreadEntity>) {
        deleteThreads(archived)
        upsertThreads(threads)
    }

    // ── Wątek ─────────────────────────────────────────────────────────────────

    @Query("SELECT * FROM chat_thread_messages WHERE threadId = :threadId ORDER BY createdAt ASC")
    suspend fun getMessages(threadId: String): List<ChatMessageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessages(messages: List<ChatMessageEntity>)

    @Query("DELETE FROM chat_thread_messages WHERE threadId = :threadId")
    suspend fun deleteMessages(threadId: String)

    @Transaction
    suspend fun replaceMessages(threadId: String, messages: List<ChatMessageEntity>) {
        deleteMessages(threadId)
        upsertMessages(messages)
    }

    // ── Ludzie ────────────────────────────────────────────────────────────────

    @Query("SELECT * FROM chat_people ORDER BY name ASC")
    suspend fun getPeople(): List<ChatPersonEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPeople(people: List<ChatPersonEntity>)

    @Query("DELETE FROM chat_people")
    suspend fun deletePeople()

    @Transaction
    suspend fun replacePeople(people: List<ChatPersonEntity>) {
        deletePeople()
        upsertPeople(people)
    }

    // ── Kolejka ───────────────────────────────────────────────────────────────

    /** Cała kolejka, od najstarszego wpisu — tak ją opróżnia robotnik. */
    @Query("SELECT * FROM chat_mutations ORDER BY createdAt ASC")
    suspend fun getMutations(): List<ChatMutationEntity>

    @Query("SELECT * FROM chat_mutations WHERE threadId = :threadId ORDER BY createdAt ASC")
    suspend fun getMutationsForThread(threadId: String): List<ChatMutationEntity>

    @Query("SELECT COUNT(*) FROM chat_mutations")
    suspend fun countMutations(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMutation(mutation: ChatMutationEntity)

    @Query("DELETE FROM chat_mutations WHERE localId = :localId")
    suspend fun deleteMutation(localId: String)
}
