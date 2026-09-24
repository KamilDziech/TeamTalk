package com.ekotak.teamtalk.domain.model

import java.time.LocalDate

/**
 * PRZENIESIENIE PRACOWNIKA do innej ekipy — decyzje usera 2026-09-24, 1:1
 * z panelem (`web/src/app/app/schedule/move.ts`) i z API (`planMove`
 * w `installations/domain/schedule.ts`):
 *  1. cały montaż ALBO wybrane dni etapu,
 *  2. o etapy, na których osoba jest w tych dniach, telefon PYTA za każdym razem,
 *  3. to szkic — ekipy zobaczą zmianę po „Opublikuj tydzień",
 *  4. przytrzymanie osoby i upuszczenie na pasek albo „Przenieś do…" w arkuszu.
 *
 * Stałego składu ekipy to nie rusza: to wypożyczenie, nie zmiana ekipy.
 */
data class PersonMove(
    val userId: String,
    val toInstallationId: String,
    /** `null` = cały etap docelowy. */
    val days: List<LocalDate>?,
    /** Etapy, z których osoba schodzi w tych dniach („Tak"); pusta = „Nie". */
    val removeFrom: List<String>,
)

/** Dni robocze etapu, od startu do końca. */
fun ScheduleStage.workdays(cal: ScheduleCalendar): List<LocalDate> {
    val out = mutableListOf<LocalDate>()
    var d = scheduledAt
    while (!d.isAfter(endDate)) {
        if (cal.isWork(d)) out += d
        d = d.plusDays(1)
    }
    return out
}

/**
 * Dni obecności osoby na etapie. Brak dni — albo same dni spoza etapu (termin
 * ruszony w panelu) — to cały etap: lepiej ostrzec o dublu, niż zgubić człowieka.
 */
fun presence(stageDays: List<LocalDate>, a: StageAssignee): List<LocalDate> {
    if (a.days.isEmpty()) return stageDays
    val hit = stageDays.filter { it in a.days }
    return hit.ifEmpty { stageDays }
}

/** Dni do zapisu: tylko z etapu, po kolei; komplet etapu = pusta lista. */
fun normalizeDays(stageDays: List<LocalDate>, days: Collection<LocalDate>): List<LocalDate> {
    val hit = stageDays.filter { it in days }
    return if (hit.size == stageDays.size) emptyList() else hit
}

/** Ciągłe odcinki dni obecności — na osi jeden kawałek paska na odcinek. */
fun runs(stageDays: List<LocalDate>, present: List<LocalDate>): List<Pair<LocalDate, LocalDate>> {
    val out = mutableListOf<Pair<LocalDate, LocalDate>>()
    var open: LocalDate? = null
    var last: LocalDate? = null
    for (d in stageDays) {
        if (d in present) {
            if (open == null) open = d
            last = d
        } else if (open != null && last != null) {
            out += open to last
            open = null
        }
    }
    if (open != null && last != null) out += open to last
    return out
}

data class MoveConflict(val stage: ScheduleStage, val days: List<LocalDate>)

/** Etapy, na których osoba jest w wybrane dni — o nie telefon pyta „Zdjąć?". */
fun moveConflicts(
    sch: CrewSchedule,
    userId: String,
    targetId: String,
    days: List<LocalDate>,
): List<MoveConflict> {
    val cal = ScheduleCalendar(sch.days)
    return sch.stages.mapNotNull { s ->
        if (s.id == targetId || s.status == ScheduleStageStatus.DONE) return@mapNotNull null
        val a = s.assignees.firstOrNull { it.userId == userId } ?: return@mapNotNull null
        val hit = presence(s.workdays(cal), a).filter { it in days }
        if (hit.isEmpty()) null else MoveConflict(s, hit)
    }
}

/**
 * Nowa obsada ruszonych etapów (id etapu → skład) — ta sama rachuba, co
 * w API. Telefon liczy ją sam, żeby przeniesienie zrobione bez zasięgu było
 * od razu widać na osi; prawdę i tak odeśle serwer po wysłaniu kolejki.
 */
fun planMove(sch: CrewSchedule, move: PersonMove): Map<String, List<StageAssignee>> {
    val cal = ScheduleCalendar(sch.days)
    val target = sch.stages.firstOrNull { it.id == move.toInstallationId } ?: return emptyMap()
    val tDays = target.workdays(cal)
    val moved = move.days?.takeIf { it.isNotEmpty() }?.let { want -> tDays.filter { it in want } } ?: tDays
    if (moved.isEmpty()) return emptyMap()

    val out = linkedMapOf<String, List<StageAssignee>>()
    val had = target.assignees.firstOrNull { it.userId == move.userId }
    val union = (had?.let { presence(tDays, it) }.orEmpty() + moved).toSet()
    val entry = StageAssignee(move.userId, had?.role, normalizeDays(tDays, union))
    out[target.id] = if (had != null) {
        target.assignees.map { if (it.userId == move.userId) entry else it }
    } else {
        target.assignees + entry
    }

    for (id in move.removeFrom) {
        val src = sch.stages.firstOrNull { it.id == id } ?: continue
        if (src.id == target.id || src.status == ScheduleStageStatus.DONE) continue
        val a = src.assignees.firstOrNull { it.userId == move.userId } ?: continue
        val sDays = src.workdays(cal)
        val present = presence(sDays, a)
        val left = present.filter { it !in moved }
        if (left.size == present.size) continue
        out[src.id] = if (left.isEmpty()) {
            src.assignees.filter { it.userId != move.userId }
        } else {
            src.assignees.map {
                if (it.userId == move.userId) it.copy(days = normalizeDays(sDays, left)) else it
            }
        }
    }
    return out
}

/**
 * Kto z ekipy jest danego dnia na montażu INNEJ ekipy: id ekipy → dzień →
 * „Imię → Ekipa". Oś rysuje z tego „−N" w wierszu ekipy.
 */
fun lentOut(sch: CrewSchedule): Map<String, Map<LocalDate, List<String>>> {
    val cal = ScheduleCalendar(sch.days)
    val out = mutableMapOf<String, MutableMap<LocalDate, MutableList<String>>>()
    for (s in sch.stages) {
        if (s.status == ScheduleStageStatus.DONE) continue
        val sDays = s.workdays(cal)
        val where = s.crewId?.let { id -> sch.crews.firstOrNull { it.id == id }?.name } ?: "bez ekipy"
        for (a in s.assignees) {
            val person = sch.people.firstOrNull { it.id == a.userId } ?: continue
            for (home in person.crewIds) {
                if (home == s.crewId) continue
                val byDay = out.getOrPut(home) { mutableMapOf() }
                presence(sDays, a).forEach { d -> byDay.getOrPut(d) { mutableListOf() } += "${person.name} → $where" }
            }
        }
    }
    return out
}
