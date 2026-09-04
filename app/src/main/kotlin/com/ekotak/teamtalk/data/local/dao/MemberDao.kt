package com.ekotak.teamtalk.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.ekotak.teamtalk.data.local.entity.TeamMemberEntity
import kotlinx.coroutines.flow.Flow

/**
 * Książka zespołu — jedna na aplikację. Czyta z niej Kalendarz (uczestnicy
 * i filtr osoby) oraz Mapa (drzewo działów w filtrze), więc DAO nie należy do
 * żadnego z tych modułów.
 */
@Dao
interface MemberDao {

    @Query("SELECT * FROM team_members ORDER BY firstName COLLATE NOCASE, email COLLATE NOCASE")
    fun observeMembers(): Flow<List<TeamMemberEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMembers(members: List<TeamMemberEntity>)

    @Query("DELETE FROM team_members")
    suspend fun deleteMembers()

    /**
     * Podmiana całej książki. Pustej listy nie bierzemy pod uwagę: to zwykle
     * nieudane pobranie (miękkie źródło), a nie firma bez ludzi — skasowanie
     * cache zostawiłoby moduły bez nazwisk do najbliższego zasięgu.
     */
    @Transaction
    suspend fun replaceMembers(members: List<TeamMemberEntity>) {
        if (members.isEmpty()) return
        deleteMembers()
        upsertMembers(members)
    }
}
