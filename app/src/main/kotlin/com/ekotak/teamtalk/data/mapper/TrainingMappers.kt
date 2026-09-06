package com.ekotak.teamtalk.data.mapper

import com.ekotak.teamtalk.data.remote.dto.DomainSkillRowDto
import com.ekotak.teamtalk.data.remote.dto.GradeResultDto
import com.ekotak.teamtalk.data.remote.dto.MyAssignmentDto
import com.ekotak.teamtalk.data.remote.dto.MyDomainInfoDto
import com.ekotak.teamtalk.data.remote.dto.MySkillsDto
import com.ekotak.teamtalk.data.remote.dto.PlayableLessonDto
import com.ekotak.teamtalk.data.remote.dto.PlayableQuestionDto
import com.ekotak.teamtalk.data.remote.dto.TrainingLessonDto
import com.ekotak.teamtalk.domain.model.AssignmentStatus
import com.ekotak.teamtalk.domain.model.DomainSkillCell
import com.ekotak.teamtalk.domain.model.GradeResult
import com.ekotak.teamtalk.domain.model.MySkills
import com.ekotak.teamtalk.domain.model.PlayableLesson
import com.ekotak.teamtalk.domain.model.PlayableQuestion
import com.ekotak.teamtalk.domain.model.QuestionKind
import com.ekotak.teamtalk.domain.model.SkillDomainInfo
import com.ekotak.teamtalk.domain.model.SkillLevel
import com.ekotak.teamtalk.domain.model.SkillPart
import com.ekotak.teamtalk.domain.model.TrainingAssignment
import com.ekotak.teamtalk.domain.model.TrainingLesson
import com.ekotak.teamtalk.domain.model.expandLevels

/** DTO modułu Szkolenia → model domenowy. */

fun MyAssignmentDto.toDomain(): TrainingAssignment = TrainingAssignment(
    id = id,
    lessonId = lessonId,
    title = title,
    description = description,
    status = AssignmentStatus.from(status),
    score = score,
    passThreshold = passThreshold,
    questionCount = questionCount,
    dueDate = dueDate,
    completedAt = completedAt,
    expiresAt = expiresAt,
    assignedBy = assignedBy,
)

fun TrainingLessonDto.toDomain(): TrainingLesson = TrainingLesson(
    id = id,
    title = title,
    description = description,
    videoUrl = videoUrl,
    videoProvider = videoProvider,
    passThreshold = passThreshold,
    validityMonths = validityMonths,
)

fun PlayableQuestionDto.toDomain(): PlayableQuestion = PlayableQuestion(
    id = id,
    prompt = prompt,
    kind = QuestionKind.from(kind),
    options = options,
)

fun PlayableLessonDto.toDomain(): PlayableLesson = PlayableLesson(
    assignmentId = assignmentId,
    lesson = lesson.toDomain(),
    questions = questions.map { it.toDomain() },
    status = AssignmentStatus.from(status),
    lastScore = lastScore,
)

fun GradeResultDto.toDomain(): GradeResult = GradeResult(
    score = score,
    passed = passed,
    passThreshold = passThreshold,
    correctCount = correctCount,
    total = total,
)

// ── Moje poziomy ─────────────────────────────────────────────────────────────

/**
 * Brak `requiredPart` w wierszu znaczy `praktyka` — tak samo domyśla się panel
 * (`cell.requiredPart ?? "praktyka"`), bo wiersze sprzed podziału na części
 * zapisywały pełny szczebel.
 */
fun DomainSkillRowDto.toDomain(): DomainSkillCell = DomainSkillCell(
    domainId = domainId,
    levels = expandLevels(levels),
    assessed = assessed,
    requiredLevel = SkillLevel.from(requiredLevel),
    requiredPart = SkillPart.from(requiredPart) ?: SkillPart.PRAKTYKA,
    requiredUntil = requiredUntil,
    note = note?.takeIf { it.isNotBlank() },
)

fun MyDomainInfoDto.toDomain(): SkillDomainInfo = SkillDomainInfo(
    id = id,
    name = name,
    kind = kind,
    desc = desc,
    goals = goals,
)

fun MySkillsDto.toDomain(): MySkills = MySkills(
    cells = rows.map { it.toDomain() },
    domains = domains.map { it.toDomain() },
)
