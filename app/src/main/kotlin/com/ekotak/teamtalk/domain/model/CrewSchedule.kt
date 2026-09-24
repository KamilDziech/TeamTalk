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
    /** Blokady dni widoczne w oknie (firma, ekipa, osoba). */
    val blocks: List<ScheduleBlock> = emptyList(),
    /** Dni ekip inne niż firmowe — pracująca sobota, blokada ekipy. */
    val crewDays: List<ScheduleCrewDay> = emptyList(),
) {
    /** Kalendarz dni roboczych tej osi (z dniami ekip). */
    val calendar: ScheduleCalendar get() = ScheduleCalendar(days, crewDays)
}

enum class BlockScope(val wire: String, val label: String) {
    COMPANY("company", "Cała firma"),
    CREW("crew", "Ekipa"),
    USER("user", "Osoba"),
    ;

    companion object {
        fun fromWire(value: String?): BlockScope = entries.firstOrNull { it.wire == value } ?: COMPANY
    }
}

enum class BlockReason(val wire: String, val label: String) {
    TRAINING("training", "Szkolenie"),
    FAIR("fair", "Targi"),
    OTHER("other", "Inne"),
    ;

    companion object {
        fun fromWire(value: String?): BlockReason = entries.firstOrNull { it.wire == value } ?: OTHER
    }
}

/**
 * Blokada dni (decyzje usera 2026-09-24): cała firma zatrzymuje nasze ekipy
 * (zewnętrznych nie), ekipa — jedną ekipę, osoba tylko ostrzega jak urlop.
 */
data class ScheduleBlock(
    val id: String,
    val scope: BlockScope,
    val crewId: String?,
    val userId: String?,
    val start: LocalDate,
    val end: LocalDate,
    val reason: BlockReason,
    val note: String?,
    val label: String,
    /** Nowa albo usunięta — ekipy jeszcze tego nie widzą. */
    val draft: Boolean,
    /** Usunięta w szkicu; ekipy widzą ją do publikacji. */
    val removed: Boolean,
)

/** Dzień ekipy inny niż firmowy. */
data class ScheduleCrewDay(
    val crewId: String,
    val date: LocalDate,
    val workday: Boolean,
    /** Zaznaczone „w ten dzień pracujemy". */
    val exception: Boolean,
    val draft: Boolean,
    val label: String?,
)

/** Treść blokady do zapisu (nowej albo zmienianej). */
data class ScheduleBlockInput(
    val scope: BlockScope,
    val crewId: String?,
    val userId: String?,
    val start: LocalDate,
    val end: LocalDate,
    val reason: BlockReason,
    val note: String?,
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
    /** Dzień roboczy NASZYCH ekip wg API — zna święta i blokady firmy. */
    val workday: Boolean,
    /** Ile montaży stoi tego dnia i ile firma przerabia (`InstallationCapacity`). */
    val load: Int,
    val limit: Int,
    /** „Święto", „Szkolenie · BHP"; zwykły weekend bez etykiety. */
    val label: String? = null,
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

/**
 * Osoba w obsadzie etapu. [days] = dni, w które jest na TYM etapie — wypożyczenie
 * do innej ekipy na część montażu (decyzja usera 2026-09-24); pusta = cały etap.
 */
data class StageAssignee(
    val userId: String,
    val role: String?,
    val days: List<LocalDate> = emptyList(),
)

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
 * W oknie o dniach wie API (święta, blokady firmy), a dni ekip inne niż firmowe
 * (pracująca sobota, blokada ekipy) przychodzą osobno. Poza oknem wolny zostaje
 * weekend — sobota od 2026-09-24 też. Koniec etapu to `durationDays` dni
 * roboczych EKIPY licząc od startu.
 */
class ScheduleCalendar(days: List<ScheduleDay>, crewDays: List<ScheduleCrewDay> = emptyList()) {
    private val workdayOf: Map<LocalDate, Boolean> = days.associate { it.date to it.workday }
    private val crewDayOf: Map<Pair<String, LocalDate>, ScheduleCrewDay> =
        crewDays.associateBy { it.crewId to it.date }

    /** Dzień ekipy inny niż firmowy (albo `null`). */
    fun crewDay(crewId: String?, day: LocalDate): ScheduleCrewDay? =
        crewId?.let { crewDayOf[it to day] }

    fun isWork(day: LocalDate, crewId: String? = null): Boolean =
        crewDay(crewId, day)?.workday
            ?: workdayOf[day]
            ?: (day.dayOfWeek != DayOfWeek.SUNDAY && day.dayOfWeek != DayOfWeek.SATURDAY)

    /** Najbliższy dzień roboczy ekipy w kierunku `dir` (±1), najwyżej dwa miesiące szukania. */
    fun nextWork(day: LocalDate, dir: Int = 1, crewId: String? = null): LocalDate {
        var d = day
        var i = 0
        while (i < 60 && !isWork(d, crewId)) {
            d = d.plusDays(dir.toLong())
            i++
        }
        return d
    }

    fun endOf(start: LocalDate, count: Int, crewId: String? = null): LocalDate {
        var d = nextWork(start, 1, crewId)
        var left = maxOf(1, count)
        while (left > 1) {
            d = d.plusDays(1)
            if (isWork(d, crewId)) left -= 1
        }
        return d
    }

    fun workdaysBetween(a: LocalDate, b: LocalDate, crewId: String? = null): Int {
        var n = 0
        var d = a
        while (!d.isAfter(b)) {
            if (isWork(d, crewId)) n += 1
            d = d.plusDays(1)
        }
        return maxOf(1, n)
    }
}

/** Blokada, która dotyczy montera: firmy, jego ekipy albo jego samego. */
data class MyDayBlock(
    val scope: BlockScope,
    val start: LocalDate,
    val end: LocalDate,
    val label: String,
    val crewName: String?,
)

/** Dzień wolny, w który ekipa montera pracuje („w tę sobotę pracujemy”). */
data class MyWorkday(val day: LocalDate, val crewName: String)

/**
 * Kalendarz montera w module „Montaże" — wersja opublikowana z Harmonogramu.
 *
 * Wolne: weekend, święta, blokada firmy albo ekipy. Pracująca sobota ekipy
 * wygrywa. Blokada OSOBY montażu nie przerywa (tylko ją pokazujemy), tak jak
 * liczy serwer.
 */
data class MyDays(val blocks: List<MyDayBlock>, val workdays: List<MyWorkday>) {
    private val working = workdays.mapTo(HashSet()) { it.day }

    /** Blokada firmy/ekipy w ten dzień (osoby — nie). */
    fun blockOf(day: LocalDate): MyDayBlock? =
        blocks.firstOrNull { it.scope != BlockScope.USER && !day.isBefore(it.start) && !day.isAfter(it.end) }

    fun isWork(day: LocalDate): Boolean {
        if (day in working) return true
        if (day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY) return false
        if (com.ekotak.teamtalk.domain.leave.isPolishHoliday(day)) return false
        return blockOf(day) == null
    }

    /** Ostatni dzień montażu: `count` dni roboczych od startu. */
    fun endOf(start: LocalDate, count: Int): LocalDate {
        var d = start
        var i = 0
        while (!isWork(d) && i++ < 60) d = d.plusDays(1)
        var left = maxOf(1, count)
        while (left > 1 && i++ < 400) {
            d = d.plusDays(1)
            if (isWork(d)) left -= 1
        }
        return d
    }

    /** Dni przerwy w trakcie montażu (weekend, święto, blokada). */
    fun offDaysBetween(start: LocalDate, end: LocalDate): List<LocalDate> {
        val out = mutableListOf<LocalDate>()
        var d = start
        while (!d.isAfter(end)) {
            if (!isWork(d)) out += d
            d = d.plusDays(1)
        }
        return out
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

/**
 * Ekipy w kolejności [order] (id od góry osi). Ekipy spoza listy — świeżo
 * założone albo nieznane w chwili układania — zostają na końcu, w porządku
 * z serwera; id ekip, których już nie ma, są pomijane.
 */
fun orderCrews(crews: List<ScheduleCrew>, order: List<String>): List<ScheduleCrew> {
    val byId = crews.associateBy { it.id }
    val placed = order.distinct().mapNotNull { byId[it] }
    val ids = placed.mapTo(HashSet()) { it.id }
    return placed + crews.filter { it.id !in ids }
}

/** Lista wyboru ekipy w arkuszach zostaje w starym porządku: własne, zewnętrzne, po nazwie. */
fun pickerCrews(crews: List<ScheduleCrew>): List<ScheduleCrew> =
    crews.sortedWith(compareBy<ScheduleCrew> { it.external }.thenBy { it.name.lowercase() })
