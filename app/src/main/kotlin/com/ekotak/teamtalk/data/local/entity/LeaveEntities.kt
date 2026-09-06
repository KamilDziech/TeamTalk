package com.ekotak.teamtalk.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cache i kolejka modułu Urlop.
 *
 * Urlop planuje się tam, gdzie akurat jest człowiek — w busie, na budowie,
 * wieczorem w domu — a nie przy biurku z zasięgiem. Stąd pełny offline
 * z kolejką (ustalenie 2026-09-06), jak w Zadaniach, Serwisie i Kalendarzu.
 *
 * Trzy tabele, bo trzy różne zastosowania: własne wnioski i skrzynka
 * zwierzchnika (pełne dane, można je zmieniać), cudze nieobecności (samo tło
 * kalendarza i oś czasu — ani ich nie edytujemy, ani nie znamy powodów)
 * oraz kolejka zapisów czekających na wysyłkę.
 */

/**
 * Wniosek urlopowy w cache — własny (`mine = true`) albo ze skrzynki
 * zwierzchnika (`mine = false`).
 *
 * Rozdzielenie flagą, a nie osobną tabelą: to ten sam rekord serwera i te same
 * pola, a zbiory są rozłączne — skrzynka niesie wyłącznie wnioski PODWŁADNYCH,
 * nigdy własne.
 */
@Entity(tableName = "leave_requests")
data class LeaveRequestEntity(
    /** Id serwerowe albo lokalne (`local:…`) do czasu wysłania wniosku. */
    @PrimaryKey val id: String,
    val userId: String,
    val mine: Boolean,
    val employeeName: String?,
    val employeeEmail: String?,
    /** Rola pracownika — podpis „montaż / biuro / serwis" w skrzynce. */
    val employeeRole: String?,
    val type: String,
    /** `yyyy-MM-dd` — dzień kalendarzowy, bez strefy i bez godziny. */
    val startDate: String,
    val endDate: String,
    val workingDays: Int,
    val status: String,
    val reason: String?,
    val decisionNote: String?,
    val decidedAt: String?,
    /**
     * Czy zalogowany może rozpatrzyć ten wniosek. Liczy to SERWER — tylko on
     * wie, czy zwierzchnik jest dziś na urlopie i kto go zastępuje.
     */
    val canDecide: Boolean = false,
    /** Kto rozstrzyga, gdy nie my — do napisu „Czeka na: …". */
    val awaitingName: String? = null,
    val awaitingIsBackup: Boolean = false,
    val syncedAt: Long,
)

/**
 * Cudza nieobecność — tło kalendarza urlopowego i wiersz osi czasu zespołu.
 *
 * Świadomie uboższa od [LeaveRequestEntity]: rodzaj urlopu i powód to nie nasza
 * sprawa, a kalendarz i tak rysuje wszystkie cudze urlopy jednym kolorem.
 * Własnych wniosków tu NIE zapisujemy — te ma [LeaveRequestEntity].
 */
@Entity(tableName = "leave_absences")
data class LeaveAbsenceEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val employeeName: String?,
    val employeeRole: String?,
    val startDate: String,
    val endDate: String,
    /** `oczekuje` rysujemy paskami, `zatwierdzony` pełnym kolorem. */
    val status: String,
    val syncedAt: Long,
)

/**
 * Liczniki roku, jeden wiersz na rok. `mode` decyduje o całym wyglądzie ekranu:
 * `wymiar` pokazuje pulę i pasek zużycia, `bezplatny` samą liczbę dni.
 *
 * Trzymamy przy nich `managerId` i `backupDecisionId` z kartoteki — nie po to,
 * by pokazywać kadry (tych na telefonie nie ma), tylko żeby przed wysłaniem
 * wniosku napisać, kto go rozpatrzy. Imię dokłada się z listy członków zespołu.
 */
@Entity(tableName = "leave_balance")
data class LeaveBalanceEntity(
    @PrimaryKey val year: Int,
    /** `wymiar` albo `bezplatny` — patrz koncepcja urlopów z 2026-09-04. */
    val mode: String,
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
    /** Rodzaj umowy — do zdania „Twoja umowa (…) nie daje wymiaru urlopu". */
    val employmentType: String?,
    val managerId: String?,
    val backupDecisionId: String?,
    val syncedAt: Long,
)

/**
 * Zapis czekający na wysyłkę.
 *
 * Klucz (`targetId`, `kind`) sprawia, że kolejna zmiana tego samego wniosku
 * nadpisuje poprzednią — liczy się ostatnia decyzja człowieka, a nie droga,
 * którą do niej doszedł. Jedna kolejka na wszystkie rodzaje, bo kolejność
 * między nimi ma znaczenie: anulowanie wniosku założonego offline musi pójść
 * PO jego utworzeniu.
 */
@Entity(tableName = "leave_mutations", primaryKeys = ["targetId", "kind"])
data class LeaveMutationEntity(
    /** Id wniosku — serwerowe albo lokalne (`local:…`) dla nowego. */
    val targetId: String,
    val kind: String,
    /** Gotowe ciało żądania. */
    val payload: String,
    val createdAt: Long,
) {
    companion object {
        /** `POST /api/hr/leave` — nowy wniosek. */
        const val KIND_CREATE = "leave_create"

        /** `PATCH /api/hr/leave/{id}` — zmiana dat albo rodzaju. */
        const val KIND_UPDATE = "leave_update"

        /** `POST /api/hr/leave/{id}/cancel`. */
        const val KIND_CANCEL = "leave_cancel"

        /** `POST /api/hr/leave/{id}/decision` — zatwierdzenie albo odrzucenie. */
        const val KIND_DECISION = "leave_decision"

        /** Prefiks identyfikatora nadawanego lokalnie do czasu wysłania. */
        const val LOCAL_ID_PREFIX = "local:"
    }
}
