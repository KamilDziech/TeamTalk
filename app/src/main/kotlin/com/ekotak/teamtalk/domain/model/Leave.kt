package com.ekotak.teamtalk.domain.model

import java.time.LocalDate

/**
 * Moduł Urlop — zakładka „Urlop" modułu HR panelu (`web/src/app/app/hr`).
 *
 * Daty są tu `LocalDate`, nie chwilami: urlop liczy się w dniach
 * kalendarzowych, więc strefa i godzina nie mają nic do rzeczy. Na styku
 * z API zamieniamy je na `yyyy-MM-dd` (mapper), tak jak robi to panel.
 */

/** Rodzaje urlopu. Pulę dni obciążają tylko [WYPOCZYNKOWY] i [NA_ZADANIE]. */
enum class LeaveType(val wire: String, val label: String) {
    WYPOCZYNKOWY("wypoczynkowy", "Wypoczynkowy"),
    NA_ZADANIE("na_zadanie", "Na żądanie"),
    OKOLICZNOSCIOWY("okolicznosciowy", "Okolicznościowy"),
    BEZPLATNY("bezplatny", "Bezpłatny"),
    ;

    companion object {
        fun fromWire(value: String?): LeaveType =
            entries.firstOrNull { it.wire == value } ?: WYPOCZYNKOWY

        /** Rodzaje do wyboru przy danym trybie — poza wymiarem zostaje sam bezpłatny. */
        fun availableIn(mode: LeaveMode): List<LeaveType> =
            if (mode == LeaveMode.UNPAID) listOf(BEZPLATNY) else entries
    }
}

/** Statusy wniosku. */
enum class LeaveStatus(val wire: String, val label: String) {
    OCZEKUJE("oczekuje", "Oczekuje"),
    ZATWIERDZONY("zatwierdzony", "Zatwierdzony"),
    ODRZUCONY("odrzucony", "Odrzucony"),
    ANULOWANY("anulowany", "Anulowany"),
    ;

    /** Czy wniosek da się jeszcze zmienić albo anulować (reguła API). */
    val isOpen: Boolean get() = this == OCZEKUJE || this == ZATWIERDZONY

    companion object {
        fun fromWire(value: String?): LeaveStatus =
            entries.firstOrNull { it.wire == value } ?: OCZEKUJE
    }
}

/**
 * Tryb rozliczania urlopu — bierze się z rodzaju umowy w kartotece i decyduje
 * o całym wyglądzie ekranu (koncepcja urlopów z 2026-09-04).
 */
enum class LeaveMode(val wire: String) {
    /** Umowa o pracę: pula dni, pasek zużycia, wszystkie rodzaje urlopu. */
    QUOTA("wymiar"),

    /** Wspólnik, B2B, zlecenie, dzieło, staż: bez limitu, sam urlop bezpłatny. */
    UNPAID("bezplatny"),
    ;

    companion object {
        fun fromWire(value: String?): LeaveMode =
            entries.firstOrNull { it.wire == value } ?: QUOTA
    }
}

/**
 * Wniosek urlopowy — własny albo ze skrzynki zwierzchnika ([mine]).
 *
 * [canDecide] i [awaitingName] liczy SERWER: tylko on wie, czy zwierzchnik jest
 * dziś na urlopie i kto go zastępuje. Klient ich nie odtwarza, żeby nie
 * pokazywać przycisku, który oddaje 403.
 */
data class LeaveRequest(
    val id: String,
    val userId: String,
    val mine: Boolean,
    val employeeName: String?,
    val employeeRole: String?,
    val type: LeaveType,
    val start: LocalDate,
    val end: LocalDate,
    val workingDays: Int,
    val status: LeaveStatus,
    val reason: String?,
    val decisionNote: String?,
    val decidedAt: String?,
    val canDecide: Boolean = false,
    val awaitingName: String? = null,
    val awaitingIsBackup: Boolean = false,
    /** Wniosek złożony bez zasięgu — czeka w kolejce na wysyłkę. */
    val pendingSync: Boolean = false,
) {
    /** Czy wniosek obejmuje ten dzień (bez względu na dni robocze w środku). */
    fun covers(day: LocalDate): Boolean = !day.isBefore(start) && !day.isAfter(end)

    /** Wnioski aktywne rysujemy w kalendarzu; odrzucone i anulowane nie istnieją. */
    val isActive: Boolean get() = status.isOpen
}

/**
 * Liczniki roku. W trybie [LeaveMode.UNPAID] limitu nie ma: `entitled`
 * i `remaining` są zerami, a znaczenie ma [unpaidDays] — liczba dni urlopu.
 *
 * [managerId] i [backupDecisionId] nie służą do pokazywania kadr (tych na
 * telefonie nie ma), tylko do zdania „Decyzję podejmie …" przed wysłaniem.
 */
data class LeaveBalance(
    val year: Int,
    val mode: LeaveMode,
    val entitled: Int,
    val used: Int,
    val planned: Int,
    val pending: Int,
    val remaining: Int,
    val onDemandUsed: Int,
    val onDemandTotal: Int,
    val specialDays: Int,
    val unpaidDays: Int,
    val unpaidTotal: Int,
    val employmentType: String? = null,
    val managerId: String? = null,
    val backupDecisionId: String? = null,
)

/**
 * Cudza nieobecność — tło kalendarza i wiersz osi czasu. Sam fakt, że kogoś
 * nie ma: bez rodzaju urlopu i bez powodu (`GET /api/hr/absences`).
 */
data class LeaveAbsence(
    val id: String,
    val userId: String,
    val employeeName: String?,
    val employeeRole: String?,
    val start: LocalDate,
    val end: LocalDate,
    val status: LeaveStatus,
) {
    fun covers(day: LocalDate): Boolean = !day.isBefore(start) && !day.isAfter(end)
}

/** Wniosek do złożenia albo zmiany. */
data class LeaveDraft(
    val type: LeaveType,
    val start: LocalDate,
    val end: LocalDate,
    val reason: String? = null,
)

/**
 * Okres nachodzi na inny wniosek tej samej osoby (409). Niesie identyfikatory
 * kolidujących wniosków, żeby ekran mógł od razu otworzyć edycję istniejącego
 * zamiast zakładać drugi.
 */
class LeaveOverlapException(
    message: String,
    val conflictIds: List<String>,
) : RuntimeException(message)

/** Rodzaj urlopu niedostępny przy tej umowie (422) — zostaje sam bezpłatny. */
class LeaveTypeNotAllowedException(message: String) : RuntimeException(message)
