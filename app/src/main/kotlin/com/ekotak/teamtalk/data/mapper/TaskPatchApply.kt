package com.ekotak.teamtalk.data.mapper

import com.ekotak.teamtalk.data.local.entity.TaskEntity
import com.ekotak.teamtalk.domain.model.TaskPatch

/**
 * Nakłada zmianę na wiersz w cache, żeby lista pokazała ją od razu — także bez
 * zasięgu, kiedy zmiana czeka w kolejce (E3).
 *
 * To jest przybliżenie odpowiedzi serwera, nie jej zamiennik: pola liczone po
 * stronie board360 (`updatedAt`, `commentCount`, nazwa wykonawcy) zostają
 * nietknięte i przyjdą przy najbliższym odświeżeniu. Wykonawcy nie podmieniamy
 * po e-mailu, bo cache go nie zna — do czasu synchronizacji wiersz pokazuje
 * poprzednią osobę, co jest uczciwsze niż zgadywanie.
 */
fun TaskEntity.applyPatch(patch: TaskPatch): TaskEntity = copy(
    title = patch.title?.value ?: title,
    description = patch.description?.let { it.value } ?: description,
    status = patch.status?.value?.wire ?: status,
    priority = patch.priority?.value?.wire ?: priority,
    assigneeId = patch.assigneeId?.let { it.value } ?: assigneeId,
    assigneeEmail = if (patch.assigneeId != null) null else assigneeEmail,
    dueAt = patch.dueAt?.let { it.value } ?: dueAt,
    section = patch.section?.let { it.value?.wire } ?: section,
    estimatedMinutes = patch.estimatedMinutes?.let { it.value } ?: estimatedMinutes,
    slaHours = patch.slaHours?.let { it.value } ?: slaHours,
)
