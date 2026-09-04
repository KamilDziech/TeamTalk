package com.ekotak.teamtalk.domain.repository

import com.ekotak.teamtalk.domain.model.Calendar
import com.ekotak.teamtalk.domain.model.CalendarDraft
import com.ekotak.teamtalk.domain.model.CalendarEvent
import com.ekotak.teamtalk.domain.model.CalendarEventDraft
import com.ekotak.teamtalk.domain.model.CalendarEventPatch
import com.ekotak.teamtalk.domain.model.CalendarOverlay
import com.ekotak.teamtalk.domain.model.CalendarPatch
import com.ekotak.teamtalk.domain.model.FreeBusy
import com.ekotak.teamtalk.domain.model.PrivateBusy
import com.ekotak.teamtalk.domain.model.RecurrenceScope
import com.ekotak.teamtalk.domain.model.RsvpStatus
import com.ekotak.teamtalk.domain.model.TaskMember
import kotlinx.coroutines.flow.Flow

/**
 * Migawka modułu Kalendarz — wszystko, co ekran rysuje, w jednym strumieniu
 * z Room. Filtry (warstwy, osoba, zakres dat) robimy lokalnie na tej migawce,
 * żeby przełączanie zakładek nie kosztowało okrążenia po sieci.
 */
data class CalendarSnapshot(
    val calendars: List<Calendar> = emptyList(),
    val events: List<CalendarEvent> = emptyList(),
    val members: List<TaskMember> = emptyList(),
    /** Szare pola „Zajęte" z prywatnych kalendarzy zespołu (bez treści wpisów). */
    val busy: List<PrivateBusy> = emptyList(),
    /** Czy zalogowany może planować MIMO prywatnej zajętości (`calendar.override_busy`). */
    val canOverrideBusy: Boolean = false,
    val syncedAt: Long? = null,
)

enum class CalendarSyncResult { DONE, RETRY }

interface CalendarRepository {

    fun observe(): Flow<CalendarSnapshot>

    /**
     * Dociąga kalendarze, osoby i wydarzenia zakresu `[fromIso, toIso)`.
     * Wydarzenia spoza zakresu zostają w cache — obejrzany miesiąc ma się
     * otworzyć także bez zasięgu.
     */
    suspend fun refresh(fromIso: String, toIso: String)

    /** Nakładki operacyjne zakresu. Wymagają zasięgu — nie trzymamy ich w cache. */
    suspend fun overlays(fromIso: String, toIso: String): List<CalendarOverlay>

    suspend fun freeBusy(userIds: List<String>, fromIso: String, toIso: String): List<FreeBusy>

    suspend fun createEvent(
        draft: CalendarEventDraft,
        allowConflict: Boolean = false,
    ): CalendarEvent

    suspend fun updateEvent(
        id: String,
        patch: CalendarEventPatch,
        scope: RecurrenceScope = RecurrenceScope.THIS,
        allowConflict: Boolean = false,
    ): CalendarEvent

    suspend fun deleteEvent(id: String, scope: RecurrenceScope = RecurrenceScope.THIS)

    suspend fun setRsvp(eventId: String, userId: String, response: RsvpStatus): CalendarEvent

    suspend fun createCalendar(draft: CalendarDraft): Calendar

    suspend fun updateCalendar(id: String, patch: CalendarPatch): Calendar

    suspend fun setCalendarArchived(id: String, archived: Boolean)

    /** Opróżnia kolejkę zmian zapisanych bez zasięgu. */
    suspend fun syncPendingMutations(): CalendarSyncResult
}
