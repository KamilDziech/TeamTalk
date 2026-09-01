package com.ekotak.teamtalk.domain.model

/** Priorytet zadania (kontrakt board360). */
enum class TaskPriority(val wire: String) {
    LOW("low"),
    NORMAL("normal"),
    HIGH("high");

    companion object {
        fun fromWire(value: String?): TaskPriority =
            entries.firstOrNull { it.wire == value } ?: NORMAL
    }
}

/**
 * Zadanie zespołu (board360, FR-26). Tworzone m.in. po połączeniu telefonicznym.
 * Powiązanie zadania niesie [TaskLink] — z klientem wiąże je jego deal, bo model
 * zadania nie ma pola `clientId`. Klient bez deala trafia do opisu.
 */
data class Task(
    val id: String,
    val title: String,
    val description: String?,
    val assigneeId: String?,
    val assigneeEmail: String?,
    val dueAt: String?,
    val status: String,
    val priority: TaskPriority,
    val createdAt: String,
)

/**
 * Do czego przypiąć zadanie — wynik kroku „kogo dotyczy" w kreatorze. Każdy
 * wariant to inny endpoint board360, bo `Task` nie ma pola `clientId`:
 * klient wiąże się z zadaniem przez swój deal.
 */
sealed interface TaskLink {
    /** Zadanie bez powiązania — `POST /api/tasks`. */
    data object None : TaskLink

    /** Zadanie pod dealem klienta — `POST /api/deals/{id}/tasks`. */
    data class Deal(val dealId: String) : TaskLink

    /** Zadanie w projekcie — `POST /api/projects/{id}/tasks`. */
    data class Project(val projectId: String) : TaskLink
}

/** Projekt do wyboru w kreatorze zadania. `GET /api/projects`. */
data class TaskProject(
    val id: String,
    val name: String,
    val taskCount: Int?,
)

/** Członek zespołu (do wyboru osoby przypisanej). `GET /api/tasks/members`. */
data class TaskMember(
    val id: String,
    val email: String,
    val firstName: String?,
    val lastName: String?,
    val role: String?,
    /** Role dodatkowe — przy dopasowaniu do kafelka liczą się na równi z główną. */
    val additionalRoles: List<String> = emptyList(),
    /** Funkcje w firmie — patrz [TaskTeam]. Wiele na osobę, więc ten sam człowiek
     *  pojawia się pod kilkoma kafelkami kreatora. */
    val functions: List<String> = emptyList(),
) {
    /** Etykieta do wyświetlenia: „Imię Nazwisko" lub e-mail. */
    val displayName: String
        get() = listOfNotNull(firstName, lastName)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { email }
}
