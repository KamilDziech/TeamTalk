package com.ekotak.teamtalk.domain.model

/**
 * Moduł Projekty na telefonie. Model niesie tylko to, co da się zrobić kciukiem
 * w drodze do klienta: gdzie stoi projekt, co jest do zrobienia i ile zostało.
 * Pieniędzy tu nie ma — API nie wysyła kwot bez `projects.finance`, a ekran
 * ogląda się przy kliencie.
 */
enum class ProjectStage(val label: String) {
    IDEA("Pomysł"),
    APPRAISAL("Ocena"),
    APPROVAL("Decyzja"),
    PLANNING("Plan"),
    ACTIVE("Realizacja"),
    CLOSED("Rozliczony"),
    ;

    companion object {
        /** Nieznany etap (starszy backend) czytamy jako „Realizacja" — projekt
         * istnieje i ktoś nad nim pracuje, więc nie chowamy go przed ludźmi. */
        fun from(raw: String?): ProjectStage = when (raw) {
            "idea" -> IDEA
            "appraisal" -> APPRAISAL
            "approval" -> APPROVAL
            "planning" -> PLANNING
            "closed" -> CLOSED
            else -> ACTIVE
        }
    }
}

data class Project(
    val id: String,
    val name: String,
    val description: String?,
    val color: String?,
    val stage: ProjectStage,
    val department: String?,
    val managerEmail: String?,
    val sponsorEmail: String?,
    val memberCount: Int,
    val taskCount: Int,
    val doneCount: Int,
    val dueAt: String?,
    val problemStatement: String?,
    val metricName: String?,
    val metricBaseline: String?,
    val metricTarget: String?,
    /**
     * Zarchiwizowany (`status = archived`). Panel dopisuje wtedy przy nazwie
     * „· archiwum" — projekt zostaje na liście deala, bo jego historia jest
     * częścią tego, co się z dealem działo.
     */
    val archived: Boolean = false,
    /** Zgłoszony bez zasięgu i wciąż w kolejce. */
    val localOnly: Boolean,
) {
    val progressPercent: Int
        get() = if (taskCount <= 0) 0 else (doneCount * 100) / taskCount
}

data class ProjectMilestone(
    val id: String,
    val projectId: String,
    val name: String,
    val dueAt: String?,
    val acceptanceCriteria: String?,
    val ownerEmail: String?,
    val doneAt: String?,
    val position: Int,
    val taskCount: Int,
    val doneCount: Int,
) {
    val done: Boolean get() = doneAt != null
}

data class ProjectTask(
    val id: String,
    val projectId: String,
    val title: String,
    val assigneeId: String?,
    val assigneeEmail: String?,
    val startAt: String?,
    val dueAt: String?,
    val status: String,
    val lifecycle: String,
    val milestoneId: String?,
    val estimatedMinutes: Int?,
    val actualMinutes: Int?,
) {
    val done: Boolean get() = status == "done"

    /** `planned` = jeszcze nie przekazane do realizacji (nie ma go w „Zadaniach"). */
    val planned: Boolean get() = lifecycle == "planned"
}

data class ProjectMember(
    val userId: String,
    val email: String?,
    val firstName: String?,
    val lastName: String?,
    val role: String?,
) {
    val displayName: String
        get() = listOfNotNull(firstName, lastName)
            .joinToString(" ")
            .ifBlank { email ?: "—" }

    val roleLabel: String
        get() = when (role) {
            "sponsor" -> "sponsor"
            "manager", "owner" -> "manager"
            "observer" -> "obserwator"
            else -> "wykonawca"
        }
}

/** Karta projektu — wszystko, co ekran pokazuje po wejściu w kafel. */
data class ProjectDetail(
    val project: Project,
    val milestones: List<ProjectMilestone>,
    val tasks: List<ProjectTask>,
    val members: List<ProjectMember>,
)

/** Pomysł zgłaszany z telefonu do Poczekalni. */
data class IdeaDraft(
    val name: String,
    val description: String?,
    val department: String?,
)
