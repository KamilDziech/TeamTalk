package com.ekotak.teamtalk.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.ekotak.teamtalk.data.local.entity.CalendarBusyEntity
import com.ekotak.teamtalk.data.local.entity.CalendarEntity
import com.ekotak.teamtalk.data.local.entity.CalendarEventEntity
import com.ekotak.teamtalk.data.local.entity.CalendarMutationEntity
import kotlinx.coroutines.flow.Flow

/** Cache modułu Kalendarz: kalendarze (warstwy), wydarzenia i zajętość. Osoby
 *  zespołu trzyma wspólny [MemberDao]. */
@Dao
interface CalendarDao {

    @Query("SELECT * FROM calendars ORDER BY name COLLATE NOCASE")
    fun observeCalendars(): Flow<List<CalendarEntity>>

    @Query("SELECT * FROM calendar_events")
    fun observeEvents(): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM calendar_events WHERE id = :id")
    suspend fun getEvent(id: String): CalendarEventEntity?

    @Query("SELECT * FROM calendars WHERE id = :id")
    suspend fun getCalendar(id: String): CalendarEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCalendars(calendars: List<CalendarEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCalendar(calendar: CalendarEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEvents(events: List<CalendarEventEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEvent(event: CalendarEventEntity)

    @Query("DELETE FROM calendar_events WHERE id = :id")
    suspend fun deleteEvent(id: String)

    @Query("DELETE FROM calendars")
    suspend fun deleteCalendars()

    /**
     * Podmiana wydarzeń pobranego zakresu. Kasujemy tylko to, co serwer mógł
     * przysłać: wydarzenia spoza zakresu zostają w cache (miesiąc obejrzany
     * w zasięgu ma się otworzyć także bez niego), a zapisane offline
     * (`localOnly`) przeżywają zawsze — to jedyna kopia decyzji człowieka.
     */
    @Query(
        "DELETE FROM calendar_events WHERE localOnly = 0 AND startAt >= :fromIso AND startAt < :toIso",
    )
    suspend fun deleteSyncedInRange(fromIso: String, toIso: String)

    @Transaction
    suspend fun replaceRange(fromIso: String, toIso: String, events: List<CalendarEventEntity>) {
        deleteSyncedInRange(fromIso, toIso)
        upsertEvents(events)
    }

    @Transaction
    suspend fun replaceCalendars(calendars: List<CalendarEntity>) {
        deleteCalendars()
        upsertCalendars(calendars)
    }

    // ── Prywatna zajętość (szare pola) ────────────────────────────────────

    @Query("SELECT * FROM calendar_private_busy ORDER BY startAt")
    fun observeBusy(): Flow<List<CalendarBusyEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBusy(rows: List<CalendarBusyEntity>)

    @Query("DELETE FROM calendar_private_busy WHERE startAt >= :fromIso AND startAt < :toIso")
    suspend fun deleteBusyInRange(fromIso: String, toIso: String)

    /**
     * Podmiana zajętości pobranego zakresu. Poza zakresem cache zostaje —
     * tak samo jak przy wydarzeniach, żeby obejrzany tydzień otwierał się bez
     * zasięgu z kompletem szarych pól.
     */
    @Transaction
    suspend fun replaceBusyRange(fromIso: String, toIso: String, rows: List<CalendarBusyEntity>) {
        deleteBusyInRange(fromIso, toIso)
        upsertBusy(rows)
    }
}

/** Kolejka niewysłanych zmian modułu Kalendarz. */
@Dao
interface CalendarMutationDao {

    /** Wydarzenia z niewysłaną zmianą — znacznik „czeka na wysyłkę" w wierszu. */
    @Query("SELECT DISTINCT eventId FROM calendar_mutations")
    fun observePendingEventIds(): Flow<List<String>>

    /** Wydarzenia skasowane bez zasięgu — nie pokazujemy ich, choć serwer je zna. */
    @Query("SELECT eventId FROM calendar_mutations WHERE field = '__delete'")
    fun observeDeletedEventIds(): Flow<List<String>>

    /**
     * Cała kolejka, od najstarszej zmiany. Tworzenie (`__create`) idzie pierwsze
     * w obrębie wydarzenia — inaczej `PATCH` poleciałby na id, którego serwer
     * jeszcze nie zna.
     */
    @Query("SELECT * FROM calendar_mutations ORDER BY createdAt ASC, field != '__create'")
    suspend fun getAll(): List<CalendarMutationEntity>

    @Query("SELECT * FROM calendar_mutations WHERE eventId = :eventId")
    suspend fun getForEvent(eventId: String): List<CalendarMutationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(mutations: List<CalendarMutationEntity>)

    @Query("DELETE FROM calendar_mutations WHERE eventId = :eventId AND field IN (:fields)")
    suspend fun delete(eventId: String, fields: List<String>)

    @Query("DELETE FROM calendar_mutations WHERE eventId = :eventId")
    suspend fun deleteForEvent(eventId: String)

    /** Po wysłaniu wydarzenia lokalnego reszta jego kolejki dostaje id serwera. */
    @Query("UPDATE calendar_mutations SET eventId = :newId WHERE eventId = :oldId")
    suspend fun rekeyEvent(oldId: String, newId: String)

    @Query("DELETE FROM calendar_mutations")
    suspend fun deleteAll()
}
