package com.ekotak.teamtalk.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cache modułu Projekty. Źródłem prawdy dla ekranu jest Room — lista otwiera się
 * w aucie bez zasięgu, a sieć tylko dolewa świeże dane.
 *
 * Kwot tu nie ma świadomie: API nie wysyła ich bez `projects.finance`, a technik
 * ogląda telefon przy kliencie.
 */
@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String?,
    val color: String?,
    val stage: String,
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
     * Zespół jako JSON, nie osobna tabela: lista jest krótka, tylko do odczytu
     * i zawsze czytana razem z projektem. Osobna tabela kosztowałaby migrację
     * i join, nie dając nic w zamian.
     */
    val membersJson: String?,
    /** Zapisane bez zasięgu — czeka w kolejce, przeżywa odświeżenie listy. */
    val localOnly: Boolean = false,
    val cachedAt: Long,
)

@Entity(tableName = "project_milestones")
data class ProjectMilestoneEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val name: String,
    val dueAt: String?,
    val acceptanceCriteria: String?,
    val ownerEmail: String?,
    val doneAt: String?,
    val position: Int,
    val taskCount: Int,
    val doneCount: Int,
)

/**
 * Zadanie widziane z karty projektu. CELOWO osobna tabela od `tasks` modułu
 * Zadania: tamte to zadania przekazane do realizacji, a tu przychodzą także
 * `planned` — jeszcze nieaktywne. Wrzucenie ich do wspólnej tabeli pokazałoby
 * ludziom w „Zadaniach" robotę, której nikt im jeszcze nie zlecił.
 */
@Entity(tableName = "project_tasks")
data class ProjectTaskEntity(
    @PrimaryKey val id: String,
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
)

/**
 * Kolejka zmian zrobionych bez zasięgu. Dwie operacje, bo tylko te robi się
 * w terenie: domknięcie zadania z godzinami i zgłoszenie pomysłu.
 *
 * `payload` trzyma JSON żądania — dzięki temu kolejka nie musi znać kształtu
 * API, a wysyłka jest jednym przepisaniem.
 */
@Entity(tableName = "project_mutations", primaryKeys = ["targetId", "kind"])
data class ProjectMutationEntity(
    /** Id zadania (domknięcie) albo lokalne id pomysłu (utworzenie). */
    val targetId: String,
    val kind: String,
    val payload: String,
    val createdAt: Long,
) {
    companion object {
        const val KIND_CLOSE_TASK = "close_task"
        const val KIND_CREATE_IDEA = "create_idea"

        /** Prefiks identyfikatora nadawanego lokalnie do czasu wysłania. */
        const val LOCAL_ID_PREFIX = "local:"
    }
}
