package com.ekotak.teamtalk.domain.repository

import com.ekotak.teamtalk.domain.model.LeaveAbsence
import com.ekotak.teamtalk.domain.model.LeaveBalance
import com.ekotak.teamtalk.domain.model.LeaveDraft
import com.ekotak.teamtalk.domain.model.LeaveRequest
import kotlinx.coroutines.flow.Flow

/**
 * Migawka modułu Urlop — wszystko, co rysuje ekran, jednym strumieniem z Room.
 * Kalendarz, liczniki i lista wniosków czytają to samo źródło, więc zmiana
 * jednego wniosku odświeża je razem, bez dodatkowego zapytania.
 */
data class LeaveSnapshot(
    val balance: LeaveBalance? = null,
    /** Własne wnioski, od najbliższego. */
    val myRequests: List<LeaveRequest> = emptyList(),
    /** Cudze nieobecności — tło kalendarza i oś czasu. */
    val absences: List<LeaveAbsence> = emptyList(),
    val syncedAt: Long? = null,
)

interface LeaveRepository {

    fun observe(): Flow<LeaveSnapshot>

    /**
     * Dociąga pulpit (`GET /hr/me`) i nieobecności zespołu. Najpierw opróżnia
     * kolejkę zapisów zrobionych bez zasięgu — inaczej odpowiedź serwera
     * cofnęłaby na ekranie wniosek, o którym on jeszcze nie wie.
     */
    suspend fun refresh()

    /** Złożenie wniosku. Bez zasięgu ląduje w kolejce i od razu w cache. */
    suspend fun submit(draft: LeaveDraft): LeaveRequest

    /** Zmiana własnego wniosku — cofa go do akceptacji. */
    suspend fun update(id: String, draft: LeaveDraft): LeaveRequest

    suspend fun cancel(id: String): LeaveRequest

    /**
     * Opróżnia kolejkę. Zwraca liczbę wysłanych zapisów; wołane przy każdym
     * odświeżeniu ekranu, dopóki nie przejmie tego robotnik (U4).
     */
    suspend fun syncPendingMutations(): Int
}
