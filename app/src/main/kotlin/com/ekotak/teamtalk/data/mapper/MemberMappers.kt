package com.ekotak.teamtalk.data.mapper

import com.ekotak.teamtalk.data.local.entity.TeamMemberEntity
import com.ekotak.teamtalk.data.remote.dto.TaskMemberDto
import com.ekotak.teamtalk.domain.model.TaskMember

/**
 * Książka zespołu: DTO ↔ encja cache ↔ model domenowy.
 *
 * Rola i funkcje jadą do bazy w całości — z nich liczy się dział w filtrze
 * osoby (patrz `Department`), a moduły mają grupować ludzi także bez zasięgu.
 */

fun TaskMemberDto.toTeamMemberEntity(): TeamMemberEntity = TeamMemberEntity(
    id = id,
    email = email,
    firstName = firstName,
    lastName = lastName,
    role = role,
    additionalRoles = additionalRoles,
    functions = functions,
)

fun TeamMemberEntity.toDomain(): TaskMember = TaskMember(
    id = id,
    email = email,
    firstName = firstName,
    lastName = lastName,
    role = role,
    additionalRoles = additionalRoles,
    functions = functions,
)
