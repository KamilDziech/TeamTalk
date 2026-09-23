package com.ekotak.teamtalk.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Moduł Cele — kształt 1:1 z modułem `goals` panelu board360
 * (`api/src/modules/goals`). Telefon woła DOKŁADNIE te same trasy, co panel:
 * realizację i status liczy serwer, żeby „70%" znaczyło na obu ekranach to samo.
 *
 * Wartości są liczbami w jednostce miernika (złotówki z groszami, sztuki,
 * procent 0–100) — panel dostaje to samo i tak samo formatuje.
 */

/** Pozycja katalogu mierników — z niej buduje się kreator celu. */
@Serializable
data class GoalMetricDto(
    val code: String,
    val label: String,
    /** `pln` | `szt` | `pct` | `pkt`. */
    val unit: String,
    /** Moduł, z którego liczy się realizacja („Lejek", „Serwis"…). */
    val source: String,
    /** `up` = im więcej tym lepiej. */
    val direction: String,
    val scopes: List<String>,
    val hint: String,
)

@Serializable
data class GoalDepartmentDto(val key: String, val label: String)

/** Pozycja regulaminu punktowego — nagroda do wyboru przy celu osobistym. */
@Serializable
data class GoalRuleDto(
    val id: String,
    val code: String = "",
    val name: String,
    val category: String = "",
    val points: Int = 0,
)

@Serializable
data class GoalCatalogDto(
    val metrics: List<GoalMetricDto> = emptyList(),
    val departments: List<GoalDepartmentDto> = emptyList(),
    /** Aktywne pozycje regulaminu; serwer oddaje je pod `goals.view`. */
    val rules: List<GoalRuleDto> = emptyList(),
)

@Serializable
data class GoalPersonDto(
    val id: String,
    val name: String,
    /** `biuro` | `serwis` | `montaz` | `pozostali`. */
    val department: String,
)

/** Osoba w tabeli wkładów działu — `contribution` w jednostce celu wiodącego. */
@Serializable
data class GoalMemberDto(
    val id: String,
    val name: String,
    val department: String,
    val contribution: Double = 0.0,
)

/**
 * Cel z policzoną realizacją. `pacePct` to TEMPO — ile procent powinno być
 * zrobione na dziś; z niego bierze się status, więc telefon nie liczy nic sam.
 */
@Serializable
data class GoalDto(
    val id: String,
    val scope: String,
    val ownerUserId: String? = null,
    val ownerName: String? = null,
    val teamKey: String? = null,
    val metric: String,
    val metricLabel: String,
    val unit: String,
    val source: String,
    val name: String,
    val target: Double,
    val value: Double,
    val pct: Double,
    val pacePct: Double,
    /** `done` | `ok` | `warn` | `bad`. */
    val status: String,
    val direction: String,
    val periodStart: String,
    val periodEnd: String,
    val periodKey: String,
    val warnAtPct: Int = 90,
    /** `active` | `closed` — stan samego celu, nie jego realizacji. */
    val goalStatusRaw: String = "active",
    val closedAt: String? = null,
    val lastCheckinAt: String? = null,
    /** Nagroda za osiągnięcie — przychodzi tylko do osób z prawem zapisu. */
    val ruleId: String? = null,
    val ruleLabel: String? = null,
)

@Serializable
data class GoalHistoryRowDto(
    val id: String,
    val periodKey: String,
    val name: String,
    val unit: String,
    val target: Double,
    val value: Double,
    val pct: Double,
)

@Serializable
data class PersonalGoalsDto(
    val periodKey: String,
    val person: GoalPersonDto? = null,
    val canManage: Boolean = false,
    /** Czyje cele wolno otworzyć: ja + podwładni (zarząd: wszyscy). */
    val managed: List<GoalPersonDto> = emptyList(),
    val items: List<GoalDto> = emptyList(),
    val history: List<GoalHistoryRowDto> = emptyList(),
)

@Serializable
data class TeamGoalsDto(
    val periodKey: String,
    val teamKey: String,
    val teamLabel: String,
    /** `false` = zwykły członek działu: karty bez imiennego rozbicia. */
    val detailed: Boolean = false,
    val canManage: Boolean = false,
    val leadGoalId: String? = null,
    val items: List<GoalDto> = emptyList(),
    val members: List<GoalMemberDto> = emptyList(),
    val personalGoals: List<GoalDto> = emptyList(),
    val history: List<GoalHistoryRowDto> = emptyList(),
)

@Serializable
data class GoalDepartmentValueDto(
    val key: String,
    val label: String,
    val value: Double,
)

@Serializable
data class CompanyGoalsDto(
    val periodKey: String,
    val canManage: Boolean = false,
    val items: List<GoalDto> = emptyList(),
    val leadGoalId: String? = null,
    val leadMetricLabel: String? = null,
    val byDepartment: List<GoalDepartmentValueDto> = emptyList(),
    val history: List<GoalHistoryRowDto> = emptyList(),
)

@Serializable
data class GoalTrendPointDto(val at: String, val value: Double)

@Serializable
data class GoalTrendDto(
    val goalId: String,
    val target: Double,
    val points: List<GoalTrendPointDto> = emptyList(),
)

/**
 * Ciało zapisu celu. Pola opcjonalne pomijamy przy łatce (`PATCH`), więc
 * `null` znaczy „nie ruszaj", a nie „wyczyść" — tak samo czyta to serwer.
 */
@Serializable
data class GoalWriteDto(
    val scope: String? = null,
    val ownerUserId: String? = null,
    val teamKey: String? = null,
    val metric: String? = null,
    val name: String? = null,
    val target: Double? = null,
    val direction: String? = null,
    val periodKey: String? = null,
    val warnAtPct: Int? = null,
    /** Pozycja regulaminu punktowego; `null` = cel bez nagrody. */
    val ruleId: String? = null,
)

/** Wpis ręczny — STAN celu na dany dzień, nie przyrost. */
@Serializable
data class GoalCheckinDto(
    val value: Double,
    val note: String = "",
    /** `yyyy-MM-dd`; pominięte = dzisiaj wg serwera. */
    val reportedOn: String? = null,
)
