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
    /** Skrzynka zwierzchnika — wnioski podwładnych do decyzji i do wglądu. */
    val inbox: List<LeaveRequest> = emptyList(),
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
     * Decyzja zwierzchnika o wniosku podwładnego. Bez zasięgu ląduje w kolejce,
     * a wniosek od razu zmienia stan na ekranie — monter czekający pod bramą
     * ma usłyszeć „zatwierdzone" wtedy, kiedy zwierzchnik to klika, a nie
     * wtedy, kiedy telefon złapie sieć.
     */
    suspend fun decide(id: String, approve: Boolean, note: String? = null): LeaveRequest

    /**
     * Opróżnia kolejkę. Woła ją robotnik po powrocie sieci i każde odświeżenie
     * ekranu — dwa wejścia do tej samej ścieżki, bo człowiek zwykle wchodzi
     * w moduł szybciej, niż system zdąży obudzić `WorkManagera`.
     */
    suspend fun syncPendingMutations(): LeaveSyncResult
}

/**
 * Wynik opróżniania kolejki.
 *
 * [rejected] to zapisy, których serwer NIE przyjął — najczęściej dlatego, że
 * w międzyczasie ktoś inny zajął ten termin albo zwierzchnik zdążył wniosek
 * rozpatrzyć. Takiej odmowy nie wolno przemilczeć: człowiek widział na ekranie
 * wniosek jako złożony, więc musi się dowiedzieć, że go nie ma.
 */
data class LeaveSyncResult(
    val sent: Int = 0,
    val rejected: List<LeaveSyncRejection> = emptyList(),
    /** Kolejka nie opróżniła się do końca — nadal brak zasięgu. */
    val incomplete: Boolean = false,
)

data class LeaveSyncRejection(
    /** Krótki opis wniosku, np. „Wypoczynkowy 13 – 17 lipca". */
    val label: String,
    /** Powód z serwera, jeśli go podał. */
    val reason: String?,
)
