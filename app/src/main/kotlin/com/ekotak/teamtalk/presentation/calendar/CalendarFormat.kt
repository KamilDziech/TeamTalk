package com.ekotak.teamtalk.presentation.calendar

import androidx.compose.ui.graphics.Color
import com.ekotak.teamtalk.domain.model.CalendarEvent
import com.ekotak.teamtalk.domain.model.PrivateBusy
import com.ekotak.teamtalk.presentation.crm.parseIsoMillis
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Formatowanie i kolory modułu Kalendarz — lustro pomocników z `CalendarView.tsx`.
 *
 * Daty jadą do API jako ISO 8601 w UTC; na ekranie pokazujemy je w strefie
 * telefonu, tak samo jak panel pokazuje je w strefie przeglądarki.
 */

val PL: Locale = Locale("pl", "PL")

private val isoUtcFormat = ThreadLocal.withInitial {
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
}

private val hourFormat = ThreadLocal.withInitial {
    SimpleDateFormat("HH:mm", PL).apply { timeZone = TimeZone.getDefault() }
}

/** Millis (czas lokalny) → ISO 8601 w UTC — tak, jak zapisuje panel. */
fun isoUtc(millis: Long): String = isoUtcFormat.get()!!.format(Date(millis))

/** Godzina lokalna „08:30" — puste pole daje pusty łańcuch. */
fun formatHour(iso: String?): String = parseIsoMillis(iso)?.let { formatHour(it) }.orEmpty()

fun formatHour(millis: Long): String = hourFormat.get()!!.format(Date(millis))

/** Widoki kalendarza — te same cztery co w panelu, w tej samej kolejności. */
enum class CalendarViewKind(val wire: String, val label: String) {
    MONTH("month", "Miesiąc"),
    WEEK("week", "Tydzień"),
    DAY("day", "Dzień"),
    AGENDA("agenda", "Agenda"),
    ;

    /** Czy widok rysuje siatkę godzinową — tylko w niej da się ciągnąć kropki. */
    val hasTimeGrid: Boolean get() = this == WEEK || this == DAY

    companion object {
        fun fromWire(value: String?): CalendarViewKind =
            entries.firstOrNull { it.wire == value } ?: MONTH
    }
}

/** Ile dni pokazuje agenda — jak `AGENDA_DAYS` w panelu. */
const val AGENDA_DAYS = 30

val zone: ZoneId get() = ZoneId.systemDefault()

fun millisToDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

fun millisToDateTime(millis: Long): LocalDateTime =
    Instant.ofEpochMilli(millis).atZone(zone).toLocalDateTime()

fun dateToMillis(date: LocalDate): Long =
    date.atStartOfDay(zone).toInstant().toEpochMilli()

fun dateTimeToMillis(dateTime: LocalDateTime): Long =
    dateTime.atZone(zone).toInstant().toEpochMilli()

/** Początek wydarzenia w millis; wydarzenia bez czytelnej daty odpadają wyżej. */
fun CalendarEvent.startMillis(): Long? = parseIsoMillis(startAt)

/**
 * Koniec wydarzenia. Brak `endAt` znaczy godzinę od początku — dokładnie tak
 * liczy panel przy rozkładaniu kolumn.
 */
fun CalendarEvent.endMillis(): Long? {
    val start = startMillis() ?: return null
    return parseIsoMillis(endAt)?.takeIf { it > start } ?: (start + 60 * 60 * 1000L)
}

/** Dni, na których wydarzenie się kładzie — wielodniowe widać na całym zakresie. */
fun CalendarEvent.coveredDays(): List<LocalDate> {
    val start = startMillis() ?: return emptyList()
    val first = millisToDate(start)
    val last = millisToDate(parseIsoMillis(endAt) ?: start)
    if (last <= first) return listOf(first)
    val days = mutableListOf<LocalDate>()
    var day = first
    var guard = 0
    while (day <= last && guard < 400) {
        days += day
        day = day.plusDays(1)
        guard++
    }
    return days
}

// ── Kolory ───────────────────────────────────────────────────────────────────

/** `CHIP_COLORS` z panelu — kolor zastępczy liczony z identyfikatora kalendarza. */
private val CHIP_COLORS = listOf(
    Color(0xFF2A78D6),
    Color(0xFF1BAF7A),
    Color(0xFFEB6834),
    Color(0xFF8A6DF0),
    Color(0xFFE0A500),
    Color(0xFFD55181),
    Color(0xFF0CA30C),
)

fun colorForId(id: String?): Color {
    if (id.isNullOrEmpty()) return Color(0xFF5F5E5A)
    var h = 0
    for (ch in id) h = (h * 31 + ch.code)
    return CHIP_COLORS[(h.toUInt() % CHIP_COLORS.size.toUInt()).toInt()]
}

/** `#rrggbb` albo `#aarrggbb` → kolor; cokolwiek innego → `null`. */
fun parseHexColor(hex: String?): Color? {
    val raw = hex?.trim()?.removePrefix("#") ?: return null
    if (raw.length != 6 && raw.length != 8) return null
    val value = raw.toLongOrNull(16) ?: return null
    return if (raw.length == 6) Color(value or 0xFF000000L) else Color(value)
}

/** Kolor wydarzenia: własny → kolor kalendarza → pochodny od identyfikatora. */
fun eventColor(event: CalendarEvent): Color =
    parseHexColor(event.color)
        ?: parseHexColor(event.calendarColor)
        ?: colorForId(event.calendarId)

/** Czarny albo biały napis na kolorowym tle — zależnie od jasności tła. */
fun onColor(background: Color): Color {
    val luminance = 0.299 * background.red + 0.587 * background.green + 0.114 * background.blue
    return if (luminance > 0.6) Color(0xFF08110F) else Color.White
}

// ── Etykiety zakresów ────────────────────────────────────────────────────────

fun monthLabel(month: YearMonth): String =
    "${month.month.getDisplayName(TextStyle.FULL_STANDALONE, PL).replaceFirstChar { it.titlecase(PL) }} " +
        "${month.year}"

fun dayLabel(day: LocalDate): String =
    "${day.dayOfWeek.getDisplayName(TextStyle.FULL, PL)}, ${day.dayOfMonth} " +
        day.month.getDisplayName(TextStyle.FULL, PL)

fun shortDay(day: LocalDate): String =
    "${day.dayOfMonth} ${day.month.getDisplayName(TextStyle.SHORT, PL)}"

/** Poniedziałek tygodnia, w którym leży dzień — kalendarz jest europejski. */
fun startOfWeek(day: LocalDate): LocalDate = day.minusDays(((day.dayOfWeek.value + 6) % 7).toLong())

val WEEKDAY_LABELS = listOf("pon", "wt", "śr", "czw", "pt", "sob", "niedz")

// ── Prywatna zajętość ────────────────────────────────────────────────────────

/** Kawałek zajętości mieszczący się w jednym dniu, w minutach od północy. */
data class BusySpan(val startMin: Int, val endMin: Int)

/**
 * Tnie bloki zajętości na kawałki dzienne i scala te, które się stykają.
 * Blok 22:00–01:00 daje kawałek w dwóch dniach, a dwa nachodzące na siebie
 * wpisy z prywatnego kalendarza mają dać jedno szare pole, nie dwa nałożone.
 */
fun busySlotsFor(busy: List<PrivateBusy>, day: LocalDate): List<BusySpan> {
    val dayStart = dateToMillis(day)
    val dayEnd = dateToMillis(day.plusDays(1))
    val raw = busy.mapNotNull { block ->
        val start = parseIsoMillis(block.startAt) ?: return@mapNotNull null
        val end = parseIsoMillis(block.endAt) ?: return@mapNotNull null
        if (start >= dayEnd || end <= dayStart) return@mapNotNull null
        val from = ((maxOf(start, dayStart) - dayStart) / 60_000L).toInt()
        val to = ((minOf(end, dayEnd) - dayStart) / 60_000L).toInt()
        if (to <= from) null else BusySpan(from, to)
    }.sortedBy { it.startMin }

    val merged = mutableListOf<BusySpan>()
    for (span in raw) {
        val last = merged.lastOrNull()
        if (last != null && span.startMin <= last.endMin) {
            merged[merged.lastIndex] = last.copy(endMin = maxOf(last.endMin, span.endMin))
        } else {
            merged += span
        }
    }
    return merged
}

/** Etykieta szarego pola w widokach listowych: „12:00 – 13:00". */
fun busyLabel(span: BusySpan, day: LocalDate): String {
    val dayStart = dateToMillis(day)
    return "${formatHour(dayStart + span.startMin * 60_000L)} – " +
        formatHour(dayStart + span.endMin * 60_000L)
}
