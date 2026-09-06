package com.ekotak.teamtalk.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Moduł Urlop — zakładka „Urlop" modułu HR panelu (board360
 * `api/src/modules/hr`). Kształt 1:1 z odpowiedziami kontrolera:
 * `GET /api/hr/me` niesie profil, liczniki i własne wnioski jednym strzałem.
 *
 * Daty przychodzą jako ISO z północą UTC (`@db.Date` w Prismie) — trzymamy je
 * tekstem i porównujemy po pierwszych dziesięciu znakach, tak samo jak panel.
 * Doba lokalna nie ma tu nic do rzeczy: urlop liczy się w dniach kalendarzowych.
 */

/** Kartoteka kadrowa. Na telefonie czytamy z niej tylko to, co dotyczy urlopu. */
@Serializable
data class HrProfileDto(
    val userId: String,
    val email: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val role: String? = null,
    /** Zwierzchnik (`User.managerId`); `null` = pracownik podlega Zarządowi. */
    val managerId: String? = null,
    /** Zastępca zwierzchnika w decyzjach, gdy ten sam jest na urlopie. */
    val backupDecisionId: String? = null,
    /**
     * Rodzaj umowy — tekst, nie enum (kartoteki mają własne warianty, np.
     * „Umowa o pracę na czas nieokreślony"). O trybie urlopu decyduje serwer
     * i przysyła go w [LeaveBalanceDto.mode]; tutaj potrzebny tylko do napisu
     * „Twoja umowa (…) nie daje wymiaru urlopu".
     */
    val employmentType: String? = null,
    val position: String? = null,
)

/**
 * Liczniki roku. Kształt zależy od `mode`:
 *  • `wymiar` — umowa o pracę: pula dni, z której schodzą `used` i `planned`,
 *  • `bezplatny` — każda inna umowa: bez limitu, liczy się sama liczba dni
 *    (`unpaidDays`), a `entitled`/`remaining`/`onDemand*` są zerami.
 */
@Serializable
data class LeaveBalanceDto(
    val year: Int,
    /** `wymiar` albo `bezplatny`. */
    val mode: String = MODE_QUOTA,
    val entitled: Int = 0,
    val used: Int = 0,
    val planned: Int = 0,
    val pending: Int = 0,
    val remaining: Int = 0,
    val onDemandUsed: Int = 0,
    val onDemandTotal: Int = 0,
    val specialDays: Int = 0,
    val unpaidDays: Int = 0,
    val unpaidTotal: Int = 0,
) {
    companion object {
        const val MODE_QUOTA = "wymiar"
        const val MODE_UNPAID = "bezplatny"
    }
}

/** Wniosek urlopowy. `employeeName`/`employeeEmail` dokłada serwer przy odczycie. */
@Serializable
data class LeaveRequestDto(
    val id: String,
    val userId: String,
    val employeeEmail: String? = null,
    val employeeName: String? = null,
    /** wypoczynkowy | na_zadanie | okolicznosciowy | bezplatny. */
    val type: String,
    val startDate: String,
    val endDate: String,
    /** Dni robocze policzone przez serwer (bez weekendów i polskich świąt). */
    val workingDays: Int = 0,
    /** oczekuje | zatwierdzony | odrzucony | anulowany. */
    val status: String,
    val reason: String? = null,
    val decidedById: String? = null,
    val decidedAt: String? = null,
    val decisionNote: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

/** `GET /api/hr/me` — cały ekran „Moje" jednym wywołaniem. */
@Serializable
data class HrDashboardDto(
    val profile: HrProfileDto? = null,
    val balance: LeaveBalanceDto,
    val requests: List<LeaveRequestDto> = emptyList(),
)

/** `POST /api/hr/leave` i `PATCH /api/hr/leave/{id}` — to samo ciało. */
@Serializable
data class LeaveCreateDto(
    val type: String,
    /** `yyyy-MM-dd`; serwer normalizuje do północy UTC. */
    val startDate: String,
    val endDate: String,
    val reason: String? = null,
)

/** `POST /api/hr/leave/{id}/decision`. */
@Serializable
data class LeaveDecisionDto(
    /** zatwierdzony | odrzucony. */
    val status: String,
    val decisionNote: String? = null,
)

/**
 * Pozycja skrzynki zwierzchnika (`GET /api/hr/leave/inbox`).
 *
 * Reguła „kto może zdecydować" zostaje po stronie serwera — on jeden wie, czy
 * zwierzchnik jest dziś na urlopie i kto jest jego backupem. Telefon dostaje
 * gotowe [canDecide] i [awaitingName]; lustrzenie tego w kliencie skończyłoby
 * się przyciskiem, który po naciśnięciu oddaje 403.
 */
@Serializable
data class LeaveInboxItemDto(
    val request: LeaveRequestDto,
    /** Czy pytający może rozpatrzyć ten wniosek. */
    val canDecide: Boolean = false,
    /** Kto rozstrzyga, gdy nie my — imię do napisu „Czeka na: …". */
    val awaitingName: String? = null,
    /** Czy ten ktoś jest backupem nieobecnego zwierzchnika. */
    val awaitingIsBackup: Boolean = false,
    /** Rola pracownika — podpis „montaż / biuro / serwis" w wierszu. */
    val employeeRole: String? = null,
)

/** `GET /api/hr/leave/inbox` — wnioski podwładnych pytającego. */
@Serializable
data class LeaveInboxDto(
    val requests: List<LeaveInboxItemDto> = emptyList(),
)

/**
 * Cudza nieobecność (`GET /api/hr/absences`) — tło kalendarza urlopowego i oś
 * czasu zespołu.
 *
 * Świadomie uboższa od [LeaveRequestDto]: sam fakt, że kogoś nie ma, bez
 * rodzaju urlopu i bez powodu. To nie kartoteka, więc chodzi pod `hr.view` —
 * inaczej monter i koordynator (bez `hr.manage`) mieliby puste tło kalendarza.
 */
@Serializable
data class LeaveAbsenceDto(
    val id: String,
    val userId: String,
    val employeeName: String? = null,
    val employeeRole: String? = null,
    val startDate: String,
    val endDate: String,
    val workingDays: Int = 0,
    /** `oczekuje` albo `zatwierdzony` — rozpatrzonych i anulowanych tu nie ma. */
    val status: String,
)

/** Pozycja `GET /api/hr/overview` — kadry widzą liczniki całego zespołu. */
@Serializable
data class HrEmployeeBalanceDto(
    val profile: HrProfileDto,
    val balance: LeaveBalanceDto,
)

/** `GET /api/hr/overview` — wymaga `hr.manage`; oś czasu nieobecności dla kadr. */
@Serializable
data class HrOverviewDto(
    val employees: List<HrEmployeeBalanceDto> = emptyList(),
    val requests: List<LeaveRequestDto> = emptyList(),
)
