package com.ekotak.teamtalk.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.ekotak.teamtalk.data.local.entity.EmailAccountEntity
import com.ekotak.teamtalk.data.local.entity.EmailAttachmentEntity
import com.ekotak.teamtalk.data.local.entity.EmailFolderCountEntity
import com.ekotak.teamtalk.data.local.entity.EmailLabelEntity
import com.ekotak.teamtalk.data.local.entity.EmailMessageEntity
import com.ekotak.teamtalk.data.local.entity.EmailMutationEntity
import com.ekotak.teamtalk.data.local.entity.EmailThreadEntity
import kotlinx.coroutines.flow.Flow

/** Cache i kolejka modułu Email. */
@Dao
interface EmailDao {

    // ── Strumienie dla ekranu listy ───────────────────────────────────────────

    @Query("SELECT * FROM email_accounts ORDER BY position ASC")
    fun observeAccounts(): Flow<List<EmailAccountEntity>>

    @Query(
        "SELECT * FROM email_folders WHERE accountId = :accountId AND scope = :scope",
    )
    fun observeFolders(accountId: String, scope: String): Flow<List<EmailFolderCountEntity>>

    /**
     * Lista wątków w folderze. Sortowanie po `lastAt` tekstowo działa, bo daty
     * są w ISO-8601 z UTC — to jedyny format, w którym porządek leksykalny jest
     * porządkiem chronologicznym.
     */
    @Query(
        """
        SELECT * FROM email_threads
        WHERE accountId = :accountId AND scope = :scope AND folder = :folder
        ORDER BY lastAt DESC
        """,
    )
    fun observeThreads(
        accountId: String,
        scope: String,
        folder: String,
    ): Flow<List<EmailThreadEntity>>

    @Query("SELECT * FROM email_labels ORDER BY name ASC")
    fun observeLabels(): Flow<List<EmailLabelEntity>>

    /** Wątki z czymś w kolejce — po nich rysujemy znacznik „W kolejce". */
    @Query("SELECT DISTINCT targetId FROM email_mutations")
    fun observePendingIds(): Flow<List<String>>

    // ── Skrzynki ──────────────────────────────────────────────────────────────

    @Query("SELECT * FROM email_accounts ORDER BY position ASC")
    suspend fun getAccounts(): List<EmailAccountEntity>

    @Query("SELECT * FROM email_accounts WHERE id = :id")
    suspend fun getAccount(id: String): EmailAccountEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAccounts(accounts: List<EmailAccountEntity>)

    @Query("DELETE FROM email_accounts WHERE id NOT IN (:keep)")
    suspend fun deleteAccountsOtherThan(keep: List<String>)

    @Transaction
    suspend fun replaceAccounts(accounts: List<EmailAccountEntity>) {
        upsertAccounts(accounts)
        deleteAccountsOtherThan(accounts.map { it.id })
    }

    // ── Foldery ───────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFolders(folders: List<EmailFolderCountEntity>)

    // ── Wątki ─────────────────────────────────────────────────────────────────

    @Query(
        "SELECT * FROM email_threads WHERE id = :id AND accountId = :accountId AND scope = :scope",
    )
    suspend fun getThread(id: String, accountId: String, scope: String): EmailThreadEntity?

    /** Wszystkie kopie wątku — ten sam wątek bywa w „Moje" i w „Wszystkie". */
    @Query("SELECT * FROM email_threads WHERE id = :id")
    suspend fun getThreadCopies(id: String): List<EmailThreadEntity>

    @Query("SELECT * FROM email_threads WHERE id = :id")
    fun observeThreadCopies(id: String): Flow<List<EmailThreadEntity>>

    /** Otwarcie wątku zdejmuje pogrubienie także z listy, w obu widokach. */
    @Query("UPDATE email_threads SET unread = 0 WHERE id = :id")
    suspend fun markRead(id: String)

    /** Dowiązanie do karty deala razem z czytelną etykietą chipa. */
    @Query("UPDATE email_threads SET dealId = :dealId, dealLabel = :label WHERE id = :id")
    suspend fun setDealLink(id: String, dealId: String?, label: String?)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertThreads(threads: List<EmailThreadEntity>)

    @Query("DELETE FROM email_threads WHERE id = :id")
    suspend fun deleteThread(id: String)

    /**
     * Podmiana zawartości folderu. Wiersze lokalne (`local:…`) zostają: to
     * wiadomości zakolejkowane na telefonie, o których serwer jeszcze nie wie,
     * a ich zniknięcie z „Wysłanych" wyglądałoby jak utrata pisma.
     */
    @Query(
        """
        DELETE FROM email_threads
        WHERE accountId = :accountId AND scope = :scope AND folder = :folder
          AND id NOT LIKE 'local:%'
        """,
    )
    suspend fun clearFolder(accountId: String, scope: String, folder: String)

    @Transaction
    suspend fun replaceFolderThreads(
        accountId: String,
        scope: String,
        folder: String,
        threads: List<EmailThreadEntity>,
    ) {
        clearFolder(accountId, scope, folder)
        upsertThreads(threads)
    }

    // ── Wiadomości i załączniki ───────────────────────────────────────────────

    @Query("SELECT * FROM email_messages WHERE threadId = :threadId ORDER BY createdAt ASC")
    fun observeMessages(threadId: String): Flow<List<EmailMessageEntity>>

    @Query("SELECT * FROM email_messages WHERE threadId = :threadId ORDER BY createdAt ASC")
    suspend fun getMessages(threadId: String): List<EmailMessageEntity>

    /** Załączniki całego wątku jednym strumieniem — bez pytania per wiadomość. */
    @Query(
        """
        SELECT a.* FROM email_attachments a
        INNER JOIN email_messages m ON m.id = a.messageId
        WHERE m.threadId = :threadId
        """,
    )
    fun observeThreadAttachments(threadId: String): Flow<List<EmailAttachmentEntity>>

    @Query("SELECT * FROM email_attachments WHERE messageId = :messageId")
    suspend fun getAttachments(messageId: String): List<EmailAttachmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessages(messages: List<EmailMessageEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAttachments(attachments: List<EmailAttachmentEntity>)

    @Query("DELETE FROM email_messages WHERE threadId = :threadId AND id NOT LIKE 'local:%'")
    suspend fun clearServerMessages(threadId: String)

    @Query("DELETE FROM email_messages WHERE id = :id")
    suspend fun deleteMessage(id: String)

    @Query("DELETE FROM email_attachments WHERE messageId = :messageId")
    suspend fun deleteAttachmentsOf(messageId: String)

    @Transaction
    suspend fun replaceThreadMessages(
        threadId: String,
        messages: List<EmailMessageEntity>,
        attachments: List<EmailAttachmentEntity>,
    ) {
        clearServerMessages(threadId)
        upsertMessages(messages)
        upsertAttachments(attachments)
    }

    // ── Etykiety ──────────────────────────────────────────────────────────────

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLabels(labels: List<EmailLabelEntity>)

    @Query("DELETE FROM email_labels WHERE id NOT IN (:keep)")
    suspend fun deleteLabelsOtherThan(keep: List<String>)

    @Transaction
    suspend fun replaceLabels(labels: List<EmailLabelEntity>) {
        upsertLabels(labels)
        deleteLabelsOtherThan(labels.map { it.id })
    }

    // ── Kolejka ───────────────────────────────────────────────────────────────

    /** Kolejność zapisu ma znaczenie — patrz komentarz przy encji mutacji. */
    @Query("SELECT * FROM email_mutations ORDER BY createdAt ASC")
    suspend fun getMutations(): List<EmailMutationEntity>

    @Query("SELECT * FROM email_mutations WHERE targetId = :targetId AND kind = :kind")
    suspend fun getMutation(targetId: String, kind: String): EmailMutationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMutation(mutation: EmailMutationEntity)

    @Query("DELETE FROM email_mutations WHERE targetId = :targetId AND kind = :kind")
    suspend fun deleteMutation(targetId: String, kind: String)

    @Query("DELETE FROM email_mutations WHERE targetId = :targetId")
    suspend fun deleteMutationsFor(targetId: String)
}
