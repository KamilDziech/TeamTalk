package com.ekotak.teamtalk.presentation.schedule

import com.ekotak.teamtalk.domain.model.CrewSchedule
import com.ekotak.teamtalk.domain.model.DatePrecision
import com.ekotak.teamtalk.domain.model.ScheduleBacklogItem
import com.ekotak.teamtalk.domain.model.ScheduleCrew
import com.ekotak.teamtalk.domain.model.SchedulePerson
import java.time.LocalDate

// Rachunki osi przeniesione z `ScheduleView.tsx` panelu — te same etykiety,
// te same progi, żeby koordynator widział w telefonie to samo co przy biurku.

internal val DOW = listOf("pn", "wt", "śr", "cz", "pt", "sb", "nd")
private val MONTHS = listOf(
    "stycznia", "lutego", "marca", "kwietnia", "maja", "czerwca",
    "lipca", "sierpnia", "września", "października", "listopada", "grudnia",
)
private val MONTHS_NOM = listOf(
    "styczeń", "luty", "marzec", "kwiecień", "maj", "czerwiec",
    "lipiec", "sierpień", "wrzesień", "październik", "listopad", "grudzień",
)

/** `23.09`. */
internal fun dm(d: LocalDate): String = "%02d.%02d".format(d.dayOfMonth, d.monthValue)

/** `23.09–26.09` albo sam dzień. */
internal fun range(a: LocalDate, b: LocalDate): String = if (a == b) dm(a) else "${dm(a)}–${dm(b)}"

/** Skróty skali jak w panelu. */
internal val ZOOMS = listOf(7 to "Tydzień", 14 to "2 tygodnie", 35 to "Miesiąc")

/** Termin rezerwacji tak, jak mówi o nim handlowiec. */
internal fun windowLabel(b: ScheduleBacklogItem): String {
    val key = b.windowKey.orEmpty()
    return when (b.datePrecision) {
        DatePrecision.WEEK -> key.substringAfter("-W", "").toIntOrNull()
            ?.let { "tydzień $it" } ?: "od ${dm(b.scheduledAt)}"
        DatePrecision.HALF_MONTH -> {
            val parts = key.split("-")
            val m = parts.getOrNull(1)?.toIntOrNull()?.minus(1)
            if (m != null && m in MONTHS.indices) {
                "${if (parts.getOrNull(2) == "H1") "1." else "2."} poł. ${MONTHS[m]} ${parts[0]}"
            } else {
                dm(b.scheduledAt)
            }
        }
        DatePrecision.MONTH -> {
            val parts = key.split("-")
            val m = parts.getOrNull(1)?.toIntOrNull()?.minus(1)
            if (m != null && m in MONTHS_NOM.indices) "${MONTHS_NOM[m]} ${parts[0]}" else dm(b.scheduledAt)
        }
        else -> dm(b.scheduledAt)
    }
}

/** Wykorzystanie ekipy w oknie: dni robocze z montażem / wszystkie dni robocze. */
internal fun crewUtil(sch: CrewSchedule, crew: ScheduleCrew): Int {
    val work = sch.days.filter { it.workday }.map { it.date }
    if (work.isEmpty()) return 0
    val busy = mutableSetOf<LocalDate>()
    sch.stages.filter { it.crewId == crew.id }.forEach { s ->
        work.forEach { d -> if (!d.isBefore(s.scheduledAt) && !d.isAfter(s.endDate)) busy += d }
    }
    return Math.round(100f * busy.size / work.size)
}

/**
 * Pula „Wolni monterzy" — monterzy bez ekipy, poznawani po rolach montażowych
 * z drzewa umiejętności (rolę konta „montaż"/„stażysta" nosi też część biura).
 */
internal fun pool(sch: CrewSchedule): List<SchedulePerson> =
    sch.people.filter { it.crewIds.isEmpty() && it.montage }

/**
 * Tygodnie okna, w których jest szkic — także taki, który ODJECHAŁ z tygodnia
 * (wersja opublikowana wciąż w nim stoi i ekipy muszą dostać zmianę).
 */
internal fun draftWeeks(sch: CrewSchedule): List<LocalDate> {
    val out = mutableListOf<LocalDate>()
    var w = sch.from
    while (!w.isAfter(sch.to)) {
        val end = w.plusDays(6)
        val hit = sch.stages.any { s ->
            s.draft && (
                (!s.scheduledAt.isAfter(end) && !s.endDate.isBefore(w)) ||
                    (s.published != null && !s.published.scheduledAt.isAfter(end) && !s.published.endDate.isBefore(w))
                )
        }
        if (hit) out += w
        w = w.plusDays(7)
    }
    return out
}
