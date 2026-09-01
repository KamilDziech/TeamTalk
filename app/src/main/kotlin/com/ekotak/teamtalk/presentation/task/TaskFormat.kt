package com.ekotak.teamtalk.presentation.task

import com.ekotak.teamtalk.domain.model.TaskMember
import com.ekotak.teamtalk.presentation.crm.parseIsoMillis
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Etykiety terminu i SLA na wierszu listy zadań. Daty z board360 przychodzą
 * jako ISO 8601 w UTC — parsujemy je tym samym pomocnikiem co karty lejka
 * (`CrmFormat.parseIsoMillis`), żeby cała aplikacja czytała je jednakowo.
 */

private fun localDateOf(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

/** Czy zadanie ma termin w przeszłości (zamknięte nigdy nie jest zaległe). */
fun isOverdue(dueAt: String?, now: Long = System.currentTimeMillis()): Boolean {
    val due = parseIsoMillis(dueAt) ?: return false
    return due < now
}

/** Czy termin wypada dzisiaj (w czasie lokalnym telefonu). */
fun isDueToday(dueAt: String?, now: Long = System.currentTimeMillis()): Boolean {
    val due = parseIsoMillis(dueAt) ?: return false
    return localDateOf(due) == localDateOf(now)
}

/** Odstęp w minutach → „45 min" / „22 h" / „6 dni" (jak znacznik SLA w panelu). */
private fun formatSpan(minutes: Long): String = when {
    minutes < 60 -> "$minutes min"
    minutes < 48 * 60 -> "${Math.round(minutes / 60.0)} h"
    else -> "${Math.round(minutes / (24.0 * 60))} dni"
}

/** Termin na wierszu: „dziś, 14:00", „jutro", „2 dni po terminie". */
fun dueLabel(dueAt: String?, now: Long = System.currentTimeMillis()): String? {
    val due = parseIsoMillis(dueAt) ?: return null
    val today = localDateOf(now)
    val day = localDateOf(due)
    val days = day.toEpochDay() - today.toEpochDay()
    val time = Instant.ofEpochMilli(due).atZone(ZoneId.systemDefault()).toLocalTime()
    val hhmm = "%02d:%02d".format(time.hour, time.minute)
    return when {
        days == 0L -> "dziś, $hhmm"
        days == 1L -> "jutro, $hhmm"
        days == -1L -> "wczoraj"
        days < 0 -> "${-days} dni po terminie"
        days < 7 -> "za $days dni"
        else -> "za ${Math.round(days / 7.0)} tyg."
    }
}

/**
 * Millisekundy z wybieraka dat → ISO 8601 w UTC, w formacie, który board360
 * przyjmuje jako `dueAt` (`z.coerce.date`). Ten sam zapis co w kreatorze
 * zadania, żeby obie ścieżki wysyłały serwerowi identyczną datę.
 */
fun isoFromMillis(millis: Long): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    fmt.timeZone = TimeZone.getTimeZone("UTC")
    return fmt.format(Date(millis))
}

/** „45 min" / „1 h 30 min" — szacowany nakład na karcie zadania. */
fun estimateLabel(minutes: Int?): String? {
    if (minutes == null || minutes <= 0) return null
    if (minutes < 60) return "$minutes min"
    val hours = minutes / 60
    val rest = minutes % 60
    return if (rest == 0) "$hours h" else "$hours h $rest min"
}

enum class SlaLevel { OK, WARN, OVER }

/** Stan SLA: ile zostało do terminu liczonego od utworzenia zadania. */
data class SlaState(val level: SlaLevel, val text: String)

/**
 * Termin SLA = `createdAt` + `slaHours`. Ostatnie 25 % okna to ostrzeżenie,
 * po terminie — alarm. Zadanie zamknięte nie ma już czego liczyć.
 */
fun slaState(
    createdAt: String,
    slaHours: Int?,
    done: Boolean,
    now: Long = System.currentTimeMillis(),
): SlaState? {
    val hours = slaHours ?: return null
    val start = parseIsoMillis(createdAt) ?: return null
    if (done) return SlaState(SlaLevel.OK, "SLA zamknięte")
    val deadline = start + hours * 3_600_000L
    val minutesLeft = (deadline - now) / 60_000
    return when {
        minutesLeft <= 0 -> SlaState(SlaLevel.OVER, "SLA −${formatSpan(-minutesLeft)}")
        minutesLeft <= hours * 60 * 0.25 -> SlaState(SlaLevel.WARN, "SLA ${formatSpan(minutesLeft)}")
        else -> SlaState(SlaLevel.OK, "SLA ${formatSpan(minutesLeft)}")
    }
}

/**
 * Grupy filtra „Przypisany" — lustro `web/src/app/app/tasks/members.ts`.
 * Osoba z rolą biuro i montaż naraz trafia do Biura (wyższy priorytet), rola
 * dodatkowa liczy się na równi z główną.
 */
enum class MemberGroup(val label: String) {
    BIURO("Biuro"),
    MONTAZ("Montażyści"),
    POZOSTALI("Pozostali"),
}

fun memberGroupOf(member: TaskMember): MemberGroup {
    val roles = (listOf(member.role) + member.additionalRoles).filterNotNull()
    return when {
        "biuro" in roles -> MemberGroup.BIURO
        "montaz" in roles -> MemberGroup.MONTAZ
        else -> MemberGroup.POZOSTALI
    }
}

/** Osoby posortowane jak w panelu: Biuro → Montaż → Pozostali, w grupie alfabetycznie. */
fun sortMembersForTasks(members: List<TaskMember>): List<TaskMember> =
    members.sortedWith(compareBy({ memberGroupOf(it).ordinal }, { it.displayName.lowercase() }))
