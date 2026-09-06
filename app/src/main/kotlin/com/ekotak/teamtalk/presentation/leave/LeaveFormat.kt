package com.ekotak.teamtalk.presentation.leave

import androidx.compose.ui.graphics.Color
import com.ekotak.teamtalk.domain.model.LeaveStatus
import com.ekotak.teamtalk.presentation.theme.EkotakGreen
import com.ekotak.teamtalk.presentation.theme.MutedDark
import com.ekotak.teamtalk.presentation.theme.Red600
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Formatowanie i kolory modułu Urlop — lustro `hr.module.css` z panelu.
 *
 * Trzy kolory niosą całą treść kalendarza i muszą znaczyć to samo co w panelu,
 * bo ludzie oglądają oba ekrany tego samego dnia:
 *  • zieleń — mój zatwierdzony urlop (i dni jeszcze wolne w pasku),
 *  • bursztyn — mój wniosek w toku (w panelu ukośne paski) i dni wykorzystane,
 *  • błękit — urlop kogoś z zespołu i dni zaplanowane.
 */

val PL_LEAVE: Locale = Locale("pl", "PL")

/** `.dayMine` / `--accent` z panelu — mój urlop, zaznaczenie, dni wolne. */
val LeaveMine: Color = EkotakGreen

/** `#ffcf8f` — wniosek czekający na decyzję i dni już wykorzystane. */
val LeavePending: Color = Color(0xFFFFCF8F)

/** `#79c0ff` — urlop zespołu i dni zaplanowane. */
val LeaveTeam: Color = Color(0xFF79C0FF)

/** Akcent kafelka „Urlop" na pulpicie — spina moduł z ikoną na ekranie startowym. */
val LeaveAccent: Color = Color(0xFFF0A742)

/** Kolor plakietki statusu — ten sam podział co `.pill*` w panelu. */
fun statusColor(status: LeaveStatus): Color = when (status) {
    LeaveStatus.ZATWIERDZONY -> LeaveMine
    LeaveStatus.OCZEKUJE -> LeavePending
    LeaveStatus.ODRZUCONY -> Red600
    LeaveStatus.ANULOWANY -> MutedDark
}

/** Skale kalendarza urlopowego — te same cztery co w panelu, w tej samej kolejności. */
enum class LeaveScale(val wire: String, val label: String) {
    WEEK("tydzien", "Tydzień"),
    MONTH("miesiac", "Miesiąc"),
    QUARTER("kwartal", "Kwartał"),
    YEAR("rok", "Rok"),
    ;

    companion object {
        fun fromWire(value: String?): LeaveScale =
            entries.firstOrNull { it.wire == value } ?: MONTH
    }
}

private val MONTHS_NOMINATIVE = listOf(
    "styczeń", "luty", "marzec", "kwiecień", "maj", "czerwiec",
    "lipiec", "sierpień", "wrzesień", "październik", "listopad", "grudzień",
)

private val MONTHS_SHORT = listOf(
    "sty", "lut", "mar", "kwi", "maj", "cze", "lip", "sie", "wrz", "paź", "lis", "gru",
)

private val ROMAN = listOf("I", "II", "III", "IV")

/** „lipiec 2026" — mianownik, jak w nagłówku panelu. */
fun monthLabel(day: LocalDate): String = "${MONTHS_NOMINATIVE[day.monthValue - 1]} ${day.year}"

fun monthShort(month: Int): String = MONTHS_SHORT[month - 1]

/** „III kwartał 2026". */
fun quarterLabel(day: LocalDate): String =
    "${ROMAN[(day.monthValue - 1) / 3]} kwartał ${day.year}"

/** „13 – 19 lipca" — zakres tygodnia bez powtarzania miesiąca, gdy ten sam. */
fun weekLabel(start: LocalDate): String {
    val end = start.plusDays(6)
    val endName = end.month.getDisplayName(TextStyle.FULL, PL_LEAVE)
    return if (start.month == end.month) {
        "${start.dayOfMonth} – ${end.dayOfMonth} $endName"
    } else {
        val startName = start.month.getDisplayName(TextStyle.FULL, PL_LEAVE)
        "${start.dayOfMonth} $startName – ${end.dayOfMonth} $endName"
    }
}

/** „13 lipca" — dzień w dopełniaczu, tak jak mówi się o urlopie. */
fun dayLabel(day: LocalDate): String =
    "${day.dayOfMonth} ${day.month.getDisplayName(TextStyle.FULL, PL_LEAVE)}"

/**
 * Zakres wniosku jednym napisem: „13 – 17 lipca", „28 lipca – 3 sierpnia"
 * albo samo „15 lipca", gdy urlop jest jednodniowy.
 */
fun rangeLabel(start: LocalDate, end: LocalDate): String = when {
    start == end -> dayLabel(start)
    start.month == end.month && start.year == end.year ->
        "${start.dayOfMonth} – ${dayLabel(end)}"
    else -> "${dayLabel(start)} – ${dayLabel(end)}"
}

/** „5 dni" / „1 dzień" — polska odmiana, bo liczba stoi tu w każdym wierszu. */
fun daysLabel(days: Int): String = if (days == 1) "1 dzień" else "$days dni"

/** Skróty dni tygodnia w nagłówku siatki, od poniedziałku. */
val WEEKDAY_SHORT = listOf("Pn", "Wt", "Śr", "Cz", "Pt", "So", "Nd")

/** Indeks dnia w tygodniu liczonym od poniedziałku (0..6). */
fun mondayIndex(day: LocalDate): Int = day.dayOfWeek.value - 1

/** Poniedziałek tygodnia, w którym leży `day`. */
fun startOfWeek(day: LocalDate): LocalDate = day.minusDays(mondayIndex(day).toLong())
