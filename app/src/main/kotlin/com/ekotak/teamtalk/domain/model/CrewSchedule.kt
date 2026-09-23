package com.ekotak.teamtalk.domain.model

import java.time.DayOfWeek
import java.time.LocalDate

/**
 * HARMONOGRAM EKIP — oś montaży koordynatora, 1:1 z modułem panelu
 * `/app/schedule` (board360 `web/src/app/app/schedule`).
 *
 * Wiersz osi to ekipa, kolumna to dzień. Etap = jeden montaż (`Installation`)
 * deala; deal rozpisany na kilka etapów (podłogówka → wylewka → pompa) ma je
 * numerowane `stageNo/stageCount`. Ostrzeżenia liczy serwer — telefon niczego
 * tu nie sprawdza sam, a po zmianie bez zasięgu pokazuje stare, dopóki kolejka
 * nie dojdzie do biura (etap ma wtedy `pending`).
 *
 * Daty to [LocalDate]: oś liczy gołe dni, bez godzin i bez strefy.
 */
data class CrewSchedule(
    val from: LocalDate,
    val to: LocalDate,
    val settings: ScheduleSettings,
    val crews: List<ScheduleCrew>,
    val people: List<SchedulePerson>,
    val leaves: List<ScheduleLeave>,
    val days: List<ScheduleDay>,
    val stages: List<ScheduleStage>,
    val backlog: List<ScheduleBacklogItem>,
    val unplanned: List<ScheduleUnplannedDeal>,
)

data class ScheduleSettings(
    /** Domyślna przerwa technologiczna (np. wylewka) w dniach roboczych. */
    val defaultGapDays: Int,
    /** Ekipy widzą dopiero wersję po „Opublikuj tydzień". */
    val publishEnabled: Boolean,
)

data class ScheduleCrew(
    val id: String,
    val name: String,
    /** `#rrggbb` z modułu Zespół; `null` = kolor wyciszony. */
    val color: String?,
    /** Ekipa zewnętrzna jest JEDNYM zasobem — bez członków, jedzie własnym składem. */
    val external: Boolean,
    val leaderId: String?,
    val memberIds: List<String>,
)

data class SchedulePerson(
    val id: String,
    val name: String,
    val skills: List<String>,
    val crewIds: List<String>,
    /** Ma w drzewie umiejętności rolę montażową — tylko tacy trafiają do puli. */
    val montage: Boolean,
)

data class ScheduleLeave(
    val userId: String,
    val start: LocalDate,
    val end: LocalDate,
    val type: String,
) {
    /** Etykieta rodzaju tak, jak mówi o nim panel. */
    val label: String
        get() = when (type) {
            "wypoczynkowy" -> "urlop"
            "na_zadanie" -> "na żądanie"
            "okolicznosciowy" -> "okolicznościowy"
            "bezplatny" -> "bezpłatny"
            else -> type
        }
}

data class ScheduleDay(
    val date: LocalDate,
    /** Dzień roboczy wg API — zna święta, czego telefon sam nie wie. */
    val workday: Boolean,
    /** Ile montaży stoi tego dnia i ile firma przerabia (`InstallationCapacity`). */
    val load: Int,
    val limit: Int,
)

enum class ScheduleStageStatus(val wire: String, val label: String) {
    RESERVED("reserved", "Rezerwacja"),
    PLANNED("planned", "Zaplanowany"),
    IN_PROGRESS("in_progress", "W trakcie"),
    DONE("done", "Zakończony"),
    ;

    companion object {
        fun fromWire(value: String?): ScheduleStageStatus =
            entries.firstOrNull { it.wire == value } ?: PLANNED
    }
}

data class StageAssignee(val userId: String, val role: String?)

data class ScheduleWarning(
    /** `leave`, `double_booking`, `crew_busy`, `missing_role`, `gap_too_short`, `stage_overlap`, `capacity`. */
    val code: String,
    val message: String,
    val userId: String?,
    val otherInstallationId: String?,
    val day: LocalDate?,
)

/** Wersja, którą widzą ekipy (ostatnia publikacja). */
data class PublishedVersion(
    val scheduledAt: LocalDate,
    val endDate: LocalDate,
    val crewId: String?,
    val assigneeIds: List<String>,
)

data class ScheduleStage(
    val id: String,
    val dealId: String,
    val clientName: String,
    val city: String?,
    val title: String,
    val status: ScheduleStageStatus,
    val scheduledAt: LocalDate,
    val endDate: LocalDate,
    /** Długość w dniach ROBOCZYCH. */
    val durationDays: Int,
    val crewId: String?,
    val assignees: List<StageAssignee>,
    val requiredRoles: List<String>,
    val minGapDays: Int?,
    val gapLabel: String?,
    /** Termin uzgodniony z klientem — przesunięcie wymaga potwierdzenia. */
    val locked: Boolean,
    val stageNo: Int,
    val stageCount: Int,
    /** Różni się od wersji opublikowanej. */
    val draft: Boolean,
    val published: PublishedVersion?,
    val warnings: List<ScheduleWarning>,
    /** Zmiana czeka w kolejce telefonu — biuro jej jeszcze nie widzi. */
    val pending: Boolean = false,
)

enum class DatePrecision { DAY, WEEK, HALF_MONTH, MONTH }

/** Pozycja listy „Do zaplanowania": rezerwacja z oferty albo montaż bez ekipy i obsady. */
data class ScheduleBacklogItem(
    val id: String,
    val dealId: String,
    val clientName: String,
    val city: String?,
    val title: String,
    val status: ScheduleStageStatus,
    val scheduledAt: LocalDate,
    val datePrecision: DatePrecision?,
    val windowKey: String?,
    val durationDays: Int,
    val reservationConfirmed: Boolean,
    val reservationNote: String?,
    val pending: Boolean = false,
)

/** Deal w etapie „Montaż" lejka, który montażu jeszcze nie ma — czeka na „Zaplanuj". */
data class ScheduleUnplannedDeal(
    val dealId: String,
    val clientName: String,
    val city: String?,
    val installationNames: List<String>,
    val since: LocalDate,
)

/**
 * Zmiana etapu — te same pola, które panel wysyła `PATCH /installations/:id`.
 *
 * Pola z [Change] rozróżniają „nie ruszaj" (`null`) od „ustaw pusto"
 * (`Change(null)`): odpięcie ekipy to `crewId: null` w ciele, a nie brak pola.
 */
data class StagePatch(
    val scheduledAt: LocalDate? = null,
    val durationDays: Int? = null,
    val crew: Change<String?>? = null,
    val assignees: List<StageAssignee>? = null,
    val minGapDays: Change<Int?>? = null,
    val gapLabel: Change<String?>? = null,
    val locked: Boolean? = null,
    /**
     * Rezerwacja z oferty wrzucona na oś staje się montażem na KONKRETNY dzień
     * (`status: planned`, `datePrecision: day`, `windowKey: null`) — przestaje
     * rozsmarowywać pojemność po całym oknie rezerwacji.
     */
    val fromReservation: Boolean = false,
)

data class Change<T>(val value: T)

/**
 * Kalendarz dni roboczych osi — ta sama arytmetyka co w panelu.
 *
 * W oknie wie o dniach API (święta!), poza oknem wolna zostaje sama niedziela.
 * Koniec etapu to `durationDays` dni roboczych licząc od startu.
 */
class ScheduleCalendar(days: List<ScheduleDay>) {
    private val workdayOf: Map<LocalDate, Boolean> = days.associate { it.date to it.workday }

    fun isWork(day: LocalDate): Boolean = workdayOf[day] ?: (day.dayOfWeek != DayOfWeek.SUNDAY)

    /** Najbliższy dzień roboczy w kierunku `dir` (±1), najwyżej dwa tygodnie szukania. */
    fun nextWork(day: LocalDate, dir: Int = 1): LocalDate {
        var d = day
        var i = 0
        while (i < 14 && !isWork(d)) {
            d = d.plusDays(dir.toLong())
            i++
        }
        return d
    }

    fun endOf(start: LocalDate, count: Int): LocalDate {
        var d = nextWork(start)
        var left = maxOf(1, count)
        while (left > 1) {
            d = d.plusDays(1)
            if (isWork(d)) left -= 1
        }
        return d
    }

    fun workdaysBetween(a: LocalDate, b: LocalDate): Int {
        var n = 0
        var d = a
        while (!d.isAfter(b)) {
            if (isWork(d)) n += 1
            d = d.plusDays(1)
        }
        return maxOf(1, n)
    }
}

/** Poniedziałek tygodnia zawierającego dzień. */
fun mondayOf(day: LocalDate): LocalDate = day.minusDays((day.dayOfWeek.value - 1).toLong())

/** „1 etap" / „2 etapy" / „5 etapów" — polska odmiana w komunikatach. */
fun etapy(n: Int): String {
    val last = n % 10
    val teen = n % 100 in 12..14
    if (n == 1) return "1 etap"
    return "$n ${if (!teen && last in 2..4) "etapy" else "etapów"}"
}
