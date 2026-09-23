package com.ekotak.teamtalk.data.mapper

import com.ekotak.teamtalk.data.remote.dto.CompanyGoalsDto
import com.ekotak.teamtalk.data.remote.dto.GoalCatalogDto
import com.ekotak.teamtalk.data.remote.dto.GoalDto
import com.ekotak.teamtalk.data.remote.dto.GoalHistoryRowDto
import com.ekotak.teamtalk.data.remote.dto.GoalMemberDto
import com.ekotak.teamtalk.data.remote.dto.GoalMetricDto
import com.ekotak.teamtalk.data.remote.dto.GoalPersonDto
import com.ekotak.teamtalk.data.remote.dto.GoalTrendDto
import com.ekotak.teamtalk.data.remote.dto.GoalWriteDto
import com.ekotak.teamtalk.data.remote.dto.PersonalGoalsDto
import com.ekotak.teamtalk.data.remote.dto.TeamGoalsDto
import com.ekotak.teamtalk.domain.model.CompanyGoals
import com.ekotak.teamtalk.domain.model.Goal
import com.ekotak.teamtalk.domain.model.GoalCatalog
import com.ekotak.teamtalk.domain.model.GoalDepartment
import com.ekotak.teamtalk.domain.model.GoalDepartmentValue
import com.ekotak.teamtalk.domain.model.GoalDraft
import com.ekotak.teamtalk.domain.model.GoalHistoryRow
import com.ekotak.teamtalk.domain.model.GoalMember
import com.ekotak.teamtalk.domain.model.GoalMetric
import com.ekotak.teamtalk.domain.model.GoalPerson
import com.ekotak.teamtalk.domain.model.GoalRule
import com.ekotak.teamtalk.domain.model.GoalScope
import com.ekotak.teamtalk.domain.model.GoalStatus
import com.ekotak.teamtalk.domain.model.GoalTrend
import com.ekotak.teamtalk.domain.model.GoalTrendPoint
import com.ekotak.teamtalk.domain.model.GoalUnit
import com.ekotak.teamtalk.domain.model.PersonalGoals
import com.ekotak.teamtalk.domain.model.TeamGoals

/**
 * Moduł Cele: DTO ⇄ model domenowy. Żadnego liczenia — realizację, tempo
 * i status przysyła serwer, a tutaj tylko przepisujemy pola i zamieniamy
 * łańcuchy na typy wyliczeniowe.
 */

fun GoalDto.toDomain(pending: Boolean = false): Goal = Goal(
    id = id,
    scope = GoalScope.of(scope),
    ownerUserId = ownerUserId,
    ownerName = ownerName,
    teamKey = teamKey,
    metric = metric,
    metricLabel = metricLabel,
    unit = GoalUnit.of(unit),
    source = source,
    name = name,
    target = target,
    value = value,
    pct = pct,
    pacePct = pacePct,
    status = GoalStatus.of(status),
    direction = direction,
    periodKey = periodKey,
    warnAtPct = warnAtPct,
    lifecycle = goalStatusRaw,
    closedAt = closedAt,
    lastCheckinAt = lastCheckinAt,
    ruleId = ruleId,
    ruleLabel = ruleLabel,
    pending = pending,
)

fun GoalHistoryRowDto.toDomain(): GoalHistoryRow = GoalHistoryRow(
    id = id,
    periodKey = periodKey,
    name = name,
    unit = GoalUnit.of(unit),
    target = target,
    value = value,
    pct = pct,
)

fun GoalPersonDto.toDomain(): GoalPerson = GoalPerson(id = id, name = name, department = department)

fun GoalMemberDto.toDomain(): GoalMember =
    GoalMember(id = id, name = name, department = department, contribution = contribution)

fun GoalMetricDto.toDomain(): GoalMetric = GoalMetric(
    code = code,
    label = label,
    unit = GoalUnit.of(unit),
    source = source,
    direction = direction,
    scopes = scopes.map { GoalScope.of(it) },
    hint = hint,
)

fun GoalCatalogDto.toDomain(): GoalCatalog = GoalCatalog(
    metrics = metrics.map { it.toDomain() },
    departments = departments.map { GoalDepartment(it.key, it.label) },
    rules = rules.map { GoalRule(it.id, it.code, it.name, it.category, it.points) },
)

fun GoalTrendDto.toDomain(): GoalTrend = GoalTrend(
    goalId = goalId,
    target = target,
    points = points.map { GoalTrendPoint(it.at, it.value) },
)

/** [pending] = zbiór celów z zapisem czekającym w kolejce. */
fun PersonalGoalsDto.toDomain(pending: Set<String> = emptySet()): PersonalGoals = PersonalGoals(
    periodKey = periodKey,
    person = person?.toDomain(),
    canManage = canManage,
    managed = managed.map { it.toDomain() },
    items = items.map { it.toDomain(pending.contains(it.id)) },
    history = history.map { it.toDomain() },
)

fun TeamGoalsDto.toDomain(pending: Set<String> = emptySet()): TeamGoals = TeamGoals(
    periodKey = periodKey,
    teamKey = teamKey,
    teamLabel = teamLabel,
    detailed = detailed,
    canManage = canManage,
    items = items.map { it.toDomain(pending.contains(it.id)) },
    members = members.map { it.toDomain() },
    personalGoals = personalGoals.map { it.toDomain(pending.contains(it.id)) },
    history = history.map { it.toDomain() },
)

fun CompanyGoalsDto.toDomain(pending: Set<String> = emptySet()): CompanyGoals = CompanyGoals(
    periodKey = periodKey,
    canManage = canManage,
    items = items.map { it.toDomain(pending.contains(it.id)) },
    leadMetricLabel = leadMetricLabel,
    byDepartment = byDepartment.map { GoalDepartmentValue(it.key, it.label, it.value) },
    history = history.map { it.toDomain() },
)

/** Ciało zapisu celu. `null` znaczy „nie ruszaj" — serwer czyta obecność klucza. */
fun GoalDraft.toDto(): GoalWriteDto = GoalWriteDto(
    scope = scope.wire,
    ownerUserId = ownerUserId,
    teamKey = teamKey,
    metric = metric,
    name = name,
    target = target,
    direction = direction,
    periodKey = periodKey,
    warnAtPct = warnAtPct,
    ruleId = ruleId,
)

/**
 * Cel zapisany bez zasięgu — karta widoczna od razu, zanim serwer go zobaczy.
 *
 * Realizacja zostaje zerowa i BEZ statusu liczonego na telefonie: dopóki cel
 * nie dotrze na serwer, nikt nie policzył jego wykonania, a zgadywanie „0% =
 * zagrożony" byłoby kłamstwem. Ekran rysuje taką kartę z plakietką „W kolejce".
 */
fun GoalDraft.toLocalDto(localId: String, metricLabel: String, unit: String, source: String): GoalDto =
    GoalDto(
        id = localId,
        scope = scope.wire,
        ownerUserId = ownerUserId,
        ownerName = null,
        teamKey = teamKey,
        metric = metric,
        metricLabel = metricLabel,
        unit = unit,
        source = source,
        name = name.ifBlank { metricLabel },
        target = target,
        value = 0.0,
        pct = 0.0,
        pacePct = 0.0,
        status = GoalStatus.OK.wire,
        direction = direction ?: "up",
        periodStart = "",
        periodEnd = "",
        periodKey = periodKey,
        warnAtPct = warnAtPct ?: 90,
        goalStatusRaw = "active",
        ruleId = ruleId,
    )

/** Nałożenie łatki na cel leżący w cache — podgląd zmiany przed wysyłką. */
fun GoalDto.withDraft(draft: GoalDraft): GoalDto = copy(
    metric = draft.metric,
    name = draft.name.ifBlank { name },
    target = draft.target,
    direction = draft.direction ?: direction,
    periodKey = draft.periodKey,
    warnAtPct = draft.warnAtPct ?: warnAtPct,
)
