package com.ekotak.teamtalk.data.mapper

import com.ekotak.teamtalk.data.local.entity.LeaveAbsenceEntity
import com.ekotak.teamtalk.data.local.entity.LeaveBalanceEntity
import com.ekotak.teamtalk.data.local.entity.LeaveRequestEntity
import com.ekotak.teamtalk.data.remote.dto.HrDashboardDto
import com.ekotak.teamtalk.data.remote.dto.LeaveAbsenceDto
import com.ekotak.teamtalk.data.remote.dto.LeaveBalanceDto
import com.ekotak.teamtalk.data.remote.dto.LeaveCreateDto
import com.ekotak.teamtalk.data.remote.dto.LeaveInboxItemDto
import com.ekotak.teamtalk.data.remote.dto.LeaveRequestDto
import com.ekotak.teamtalk.domain.leave.countWorkingDays
import com.ekotak.teamtalk.domain.model.LeaveAbsence
import com.ekotak.teamtalk.domain.model.LeaveBalance
import com.ekotak.teamtalk.domain.model.LeaveDraft
import com.ekotak.teamtalk.domain.model.LeaveMode
import com.ekotak.teamtalk.domain.model.LeaveRequest
import com.ekotak.teamtalk.domain.model.LeaveStatus
import com.ekotak.teamtalk.domain.model.LeaveType
import java.time.LocalDate

/**
 * Mapowania modułu Urlop: DTO ↔ encja Room ↔ model domeny.
 *
 * Daty na styku z API bywają pełnym ISO (`2026-07-13T00:00:00.000Z`), bo
 * w bazie board360 to `@db.Date` serializowane przez Nest. Wszędzie liczy się
 * jednak sam dzień, więc wszystko sprowadzamy do `yyyy-MM-dd` — tak samo jak
 * panel, który ucina te ciągi do dziesięciu znaków.
 */

/** `yyyy-MM-dd` z dowolnego ISO; wartość nieczytelna daje dzisiaj. */
fun parseLeaveDay(iso: String?): LocalDate =
    runCatching { LocalDate.parse(iso.orEmpty().take(10)) }.getOrElse { LocalDate.now() }

/** Dzień → `yyyy-MM-dd`, czyli format, który przyjmuje API. */
fun LocalDate.toWireDay(): String = toString()

// ── DTO → encja ──────────────────────────────────────────────────────────────

fun LeaveRequestDto.toEntity(mine: Boolean, syncedAt: Long): LeaveRequestEntity =
    LeaveRequestEntity(
        id = id,
        userId = userId,
        mine = mine,
        employeeName = employeeName,
        employeeEmail = employeeEmail,
        employeeRole = null,
        type = type,
        startDate = parseLeaveDay(startDate).toWireDay(),
        endDate = parseLeaveDay(endDate).toWireDay(),
        workingDays = workingDays,
        status = status,
        reason = reason,
        decisionNote = decisionNote,
        decidedAt = decidedAt,
        syncedAt = syncedAt,
    )

/**
 * Pozycja skrzynki. `canDecide` i `awaitingName` przychodzą z serwera — tylko
 * on wie, czy zwierzchnik jest dziś na urlopie i kto go zastępuje, więc klient
 * ich nie odtwarza (inaczej pokazałby przycisk oddający 403).
 */
fun LeaveInboxItemDto.toEntity(syncedAt: Long): LeaveRequestEntity =
    request.toEntity(mine = false, syncedAt = syncedAt).copy(
        employeeRole = employeeRole,
        canDecide = canDecide,
        awaitingName = awaitingName,
        awaitingIsBackup = awaitingIsBackup,
    )

fun LeaveAbsenceDto.toEntity(syncedAt: Long): LeaveAbsenceEntity =
    LeaveAbsenceEntity(
        id = id,
        userId = userId,
        employeeName = employeeName,
        employeeRole = employeeRole,
        startDate = parseLeaveDay(startDate).toWireDay(),
        endDate = parseLeaveDay(endDate).toWireDay(),
        status = status,
        syncedAt = syncedAt,
    )

fun HrDashboardDto.toBalanceEntity(syncedAt: Long): LeaveBalanceEntity =
    LeaveBalanceEntity(
        year = balance.year,
        mode = balance.mode,
        entitled = balance.entitled,
        used = balance.used,
        planned = balance.planned,
        pending = balance.pending,
        remaining = balance.remaining,
        onDemandUsed = balance.onDemandUsed,
        onDemandTotal = balance.onDemandTotal,
        specialDays = balance.specialDays,
        unpaidDays = balance.unpaidDays,
        unpaidTotal = balance.unpaidTotal,
        employmentType = profile?.employmentType,
        managerId = profile?.managerId,
        backupDecisionId = profile?.backupDecisionId,
        syncedAt = syncedAt,
    )

// ── Encja → domena ───────────────────────────────────────────────────────────

fun LeaveRequestEntity.toDomain(pendingSync: Boolean = false): LeaveRequest =
    LeaveRequest(
        id = id,
        userId = userId,
        mine = mine,
        employeeName = employeeName,
        employeeRole = employeeRole,
        type = LeaveType.fromWire(type),
        start = parseLeaveDay(startDate),
        end = parseLeaveDay(endDate),
        workingDays = workingDays,
        status = LeaveStatus.fromWire(status),
        reason = reason,
        decisionNote = decisionNote,
        decidedAt = decidedAt,
        canDecide = canDecide,
        awaitingName = awaitingName,
        awaitingIsBackup = awaitingIsBackup,
        pendingSync = pendingSync,
    )

fun LeaveAbsenceEntity.toDomain(): LeaveAbsence =
    LeaveAbsence(
        id = id,
        userId = userId,
        employeeName = employeeName,
        employeeRole = employeeRole,
        start = parseLeaveDay(startDate),
        end = parseLeaveDay(endDate),
        status = LeaveStatus.fromWire(status),
    )

fun LeaveBalanceEntity.toDomain(): LeaveBalance =
    LeaveBalance(
        year = year,
        mode = LeaveMode.fromWire(mode),
        entitled = entitled,
        used = used,
        planned = planned,
        pending = pending,
        remaining = remaining,
        onDemandUsed = onDemandUsed,
        onDemandTotal = onDemandTotal,
        specialDays = specialDays,
        unpaidDays = unpaidDays,
        unpaidTotal = unpaidTotal,
        employmentType = employmentType,
        managerId = managerId,
        backupDecisionId = backupDecisionId,
    )

// ── Domena → DTO / encja lokalna ─────────────────────────────────────────────

fun LeaveDraft.toDto(): LeaveCreateDto =
    LeaveCreateDto(
        type = type.wire,
        startDate = start.toWireDay(),
        endDate = end.toWireDay(),
        reason = reason?.takeIf { it.isNotBlank() },
    )

/**
 * Wniosek złożony bez zasięgu. Dni robocze liczymy tą samą arytmetyką co
 * serwer (`countWorkingDays`), więc liczba na ekranie nie zmieni się po
 * wysłaniu; status zostaje „oczekuje", bo taki nada mu API.
 */
fun LeaveDraft.toLocalEntity(localId: String, userId: String, now: Long): LeaveRequestEntity =
    LeaveRequestEntity(
        id = localId,
        userId = userId,
        mine = true,
        employeeName = null,
        employeeEmail = null,
        employeeRole = null,
        type = type.wire,
        startDate = start.toWireDay(),
        endDate = end.toWireDay(),
        workingDays = countWorkingDays(start, end),
        status = LeaveStatus.OCZEKUJE.wire,
        reason = reason,
        decisionNote = null,
        decidedAt = null,
        syncedAt = now,
    )

/** Podmiana dat i rodzaju w wierszu, który czeka w kolejce. */
fun LeaveRequestEntity.withDraft(draft: LeaveDraft, now: Long): LeaveRequestEntity =
    copy(
        type = draft.type.wire,
        startDate = draft.start.toWireDay(),
        endDate = draft.end.toWireDay(),
        workingDays = countWorkingDays(draft.start, draft.end),
        // Zmiana własnego wniosku cofa go do akceptacji — tak robi API.
        status = LeaveStatus.OCZEKUJE.wire,
        decisionNote = null,
        decidedAt = null,
        reason = draft.reason ?: reason,
        syncedAt = now,
    )

/** Liczniki dla kartoteki, której jeszcze nie pobraliśmy — same zera i tryb wymiaru. */
fun emptyBalance(year: Int): LeaveBalance = LeaveBalanceDto(year = year).let {
    LeaveBalance(
        year = it.year,
        mode = LeaveMode.fromWire(it.mode),
        entitled = it.entitled,
        used = it.used,
        planned = it.planned,
        pending = it.pending,
        remaining = it.remaining,
        onDemandUsed = it.onDemandUsed,
        onDemandTotal = it.onDemandTotal,
        specialDays = it.specialDays,
        unpaidDays = it.unpaidDays,
        unpaidTotal = it.unpaidTotal,
    )
}
