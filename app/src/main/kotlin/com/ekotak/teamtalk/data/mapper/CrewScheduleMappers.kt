package com.ekotak.teamtalk.data.mapper

import com.ekotak.teamtalk.data.remote.dto.ScheduleBacklogDto
import com.ekotak.teamtalk.data.remote.dto.ScheduleDto
import com.ekotak.teamtalk.data.remote.dto.ScheduleStageDto
import com.ekotak.teamtalk.domain.model.CrewSchedule
import com.ekotak.teamtalk.domain.model.DatePrecision
import com.ekotak.teamtalk.domain.model.PublishedVersion
import com.ekotak.teamtalk.domain.model.ScheduleBacklogItem
import com.ekotak.teamtalk.domain.model.ScheduleCalendar
import com.ekotak.teamtalk.domain.model.ScheduleCrew
import com.ekotak.teamtalk.domain.model.ScheduleDay
import com.ekotak.teamtalk.domain.model.ScheduleLeave
import com.ekotak.teamtalk.domain.model.SchedulePerson
import com.ekotak.teamtalk.domain.model.ScheduleSettings
import com.ekotak.teamtalk.domain.model.ScheduleStage
import com.ekotak.teamtalk.domain.model.ScheduleStageStatus
import com.ekotak.teamtalk.domain.model.ScheduleUnplannedDeal
import com.ekotak.teamtalk.domain.model.ScheduleWarning
import com.ekotak.teamtalk.domain.model.StageAssignee
import com.ekotak.teamtalk.domain.model.StagePatch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate

// ── Serwer → domena ──────────────────────────────────────────────────────────

/** `YYYY-MM-DD` (albo pełne ISO — bierzemy sam dzień). */
private fun day(value: String): LocalDate = LocalDate.parse(value.take(10))

fun ScheduleDto.toDomain(): CrewSchedule = CrewSchedule(
    from = day(from),
    to = day(to),
    settings = ScheduleSettings(settings.defaultGapDays, settings.publishEnabled),
    crews = crews.map {
        ScheduleCrew(it.id, it.name, it.color, it.external, it.leaderId, it.memberIds)
    },
    people = people.map { SchedulePerson(it.id, it.name, it.skills, it.crewIds, it.montage) },
    leaves = leaves.map { ScheduleLeave(it.userId, day(it.start), day(it.end), it.type) },
    days = days.map { ScheduleDay(day(it.date), it.workday, it.load, it.limit) },
    stages = stages.map { it.toDomain() },
    backlog = backlog.map { it.toDomain() },
    unplanned = unplanned.map { d ->
        ScheduleUnplannedDeal(
            dealId = d.dealId,
            clientName = d.clientName,
            city = d.city,
            installationNames = d.installations.map { it.name },
            since = day(d.since),
        )
    },
)

private fun ScheduleStageDto.toDomain(): ScheduleStage = ScheduleStage(
    id = id,
    dealId = dealId,
    clientName = clientName,
    city = city,
    title = title,
    status = ScheduleStageStatus.fromWire(status),
    scheduledAt = day(scheduledAt),
    endDate = day(endDate),
    durationDays = durationDays,
    crewId = crewId,
    assignees = assignees.map { StageAssignee(it.userId, it.role) },
    requiredRoles = requiredRoles,
    minGapDays = minGapDays,
    gapLabel = gapLabel,
    locked = locked,
    stageNo = stageNo,
    stageCount = stageCount,
    draft = draft,
    published = published?.let {
        PublishedVersion(day(it.scheduledAt), day(it.endDate), it.crewId, it.assigneeIds)
    },
    warnings = warnings.map {
        ScheduleWarning(it.code, it.message, it.userId, it.otherInstallationId, it.day?.let(::day))
    },
)

private fun ScheduleBacklogDto.toDomain(): ScheduleBacklogItem = ScheduleBacklogItem(
    id = id,
    dealId = dealId,
    clientName = clientName,
    city = city,
    title = title,
    status = ScheduleStageStatus.fromWire(status),
    scheduledAt = day(scheduledAt),
    datePrecision = when (datePrecision) {
        "day" -> DatePrecision.DAY
        "week" -> DatePrecision.WEEK
        "halfMonth" -> DatePrecision.HALF_MONTH
        "month" -> DatePrecision.MONTH
        else -> null
    },
    windowKey = windowKey,
    durationDays = durationDays,
    reservationConfirmed = reservationConfirmed,
    reservationNote = reservationNote,
)

// ── Domena → ciało PATCH ─────────────────────────────────────────────────────

/** Ciało `PATCH /installations/{id}` — dokładnie te pola, które wysyła panel. */
fun StagePatch.toJson(): JsonObject = buildJsonObject {
    scheduledAt?.let { put("scheduledAt", it.toString()) }
    durationDays?.let { put("durationDays", it) }
    crew?.let { put("crewId", it.value?.let(::JsonPrimitive) ?: JsonNull) }
    assignees?.let { list ->
        put(
            "assignees",
            buildJsonArray {
                list.forEach { a ->
                    add(
                        buildJsonObject {
                            put("userId", a.userId)
                            put("role", a.role?.let(::JsonPrimitive) ?: JsonNull)
                        },
                    )
                }
            },
        )
    }
    minGapDays?.let { put("minGapDays", it.value?.let(::JsonPrimitive) ?: JsonNull) }
    gapLabel?.let { put("gapLabel", it.value?.let(::JsonPrimitive) ?: JsonNull) }
    locked?.let { put("locked", it) }
    if (fromReservation) {
        put("status", ScheduleStageStatus.PLANNED.wire)
        put("datePrecision", "day")
        put("windowKey", JsonNull)
    }
}

// ── Nakładka kolejki ─────────────────────────────────────────────────────────

/**
 * Zmiany czekające w kolejce nałożone na oś z serwera albo z pamięci telefonu.
 *
 * Bez tego etap przesunięty w piwnicy wracałby przy każdym odświeżeniu na
 * stary termin. Koniec etapu liczymy tą samą arytmetyką dni roboczych co panel;
 * ostrzeżenia zostają stare, dopóki serwer ich nie przeliczy (`pending`).
 *
 * Dwa przejścia między listami, oba jak w panelu:
 *  • etap bez ekipy i bez obsady wraca na „Do zaplanowania",
 *  • pozycja „Do zaplanowania" z ekipą albo obsadą staje na osi.
 */
fun CrewSchedule.withPending(patches: Map<String, JsonObject>): CrewSchedule {
    if (patches.isEmpty()) return this
    val cal = ScheduleCalendar(days)

    val stagesOut = mutableListOf<ScheduleStage>()
    val backlogOut = mutableListOf<ScheduleBacklogItem>()

    for (stage in stages) {
        val patch = patches[stage.id]
        if (patch == null) {
            stagesOut += stage
            continue
        }
        val next = stage.applying(patch, cal)
        if (next.crewId == null && next.assignees.isEmpty()) {
            backlogOut += next.toBacklog()
        } else {
            stagesOut += next
        }
    }
    for (item in backlog) {
        val patch = patches[item.id]
        if (patch == null) {
            backlogOut += item
            continue
        }
        val staged = item.toStage(settings.publishEnabled).applying(patch, cal)
        if (staged.crewId != null || staged.assignees.isNotEmpty()) {
            stagesOut += staged
        } else {
            backlogOut += item.copy(scheduledAt = staged.scheduledAt, pending = true)
        }
    }
    return copy(stages = stagesOut, backlog = backlogOut)
}

private fun ScheduleStage.applying(patch: JsonObject, cal: ScheduleCalendar): ScheduleStage {
    val start = patch.text("scheduledAt")?.let(::day) ?: scheduledAt
    val duration = patch.int("durationDays") ?: durationDays
    return copy(
        scheduledAt = start,
        durationDays = duration,
        endDate = cal.endOf(start, duration),
        crewId = if (patch.containsKey("crewId")) patch.text("crewId") else crewId,
        assignees = (patch["assignees"] as? JsonArray)?.mapNotNull { el ->
            val o = el as? JsonObject ?: return@mapNotNull null
            val user = o.text("userId") ?: return@mapNotNull null
            StageAssignee(user, o.text("role"))
        } ?: assignees,
        minGapDays = if (patch.containsKey("minGapDays")) patch.int("minGapDays") else minGapDays,
        gapLabel = if (patch.containsKey("gapLabel")) patch.text("gapLabel") else gapLabel,
        locked = patch.bool("locked") ?: locked,
        status = patch.text("status")?.let(ScheduleStageStatus::fromWire) ?: status,
        pending = true,
    )
}

private fun ScheduleStage.toBacklog() = ScheduleBacklogItem(
    id = id,
    dealId = dealId,
    clientName = clientName,
    city = city,
    title = title,
    status = status,
    scheduledAt = scheduledAt,
    datePrecision = null,
    windowKey = null,
    durationDays = durationDays,
    reservationConfirmed = false,
    reservationNote = null,
    pending = true,
)

private fun ScheduleBacklogItem.toStage(publishEnabled: Boolean) = ScheduleStage(
    id = id,
    dealId = dealId,
    clientName = clientName,
    city = city,
    title = title,
    status = status,
    scheduledAt = scheduledAt,
    endDate = scheduledAt,
    durationDays = durationDays,
    crewId = null,
    assignees = emptyList(),
    requiredRoles = emptyList(),
    minGapDays = null,
    gapLabel = null,
    locked = false,
    stageNo = 1,
    stageCount = 1,
    draft = publishEnabled,
    published = null,
    warnings = emptyList(),
    pending = true,
)

private fun JsonObject.text(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
private fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull
