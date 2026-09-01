package com.ekotak.teamtalk.domain.model

/** Priorytet zadania (kontrakt board360). */
enum class TaskPriority(val wire: String, val label: String) {
    LOW("low", "Niski"),
    NORMAL("normal", "Normalny"),
    HIGH("high", "Wysoki");

    companion object {
        fun fromWire(value: String?): TaskPriority =
            entries.firstOrNull { it.wire == value } ?: NORMAL
    }
}

/** Status zadania. Etykiety 1:1 ze `STATUS_LABEL` z tablicy zadań board360. */
enum class TaskStatus(val wire: String, val label: String) {
    OPEN("open", "Do zrobienia"),
    IN_PROGRESS("in_progress", "W toku"),
    DONE("done", "Zrobione");

    companion object {
        fun fromWire(value: String?): TaskStatus =
            entries.firstOrNull { it.wire == value } ?: OPEN
    }
}

/**
 * Skąd wzięło się zadanie — klient (przez swój deal) albo projekt. board360
 * dokleja nazwy przy odczycie listy, więc na telefonie nie trzeba dobierać
 * kartoteki, żeby pokazać, kogo zadanie dotyczy.
 */
sealed interface TaskSource {
    val id: String
    val label: String?

    data class Deal(override val id: String, override val label: String?) : TaskSource
    data class Project(override val id: String, override val label: String?) : TaskSource
}

/**
 * Zadanie zespołu (board360, FR-26). Tworzone m.in. po połączeniu telefonicznym.
 * Powiązanie przy tworzeniu niesie [TaskLink] — z klientem wiąże je jego deal, bo
 * model zadania nie ma pola `clientId`; przy odczycie to samo powiązanie wraca
 * jako [source].
 */
data class Task(
    val id: String,
    val title: String,
    val description: String?,
    val assigneeId: String?,
    val assigneeEmail: String?,
    val dueAt: String?,
    val status: TaskStatus,
    val priority: TaskPriority,
    /** Sekcja (rozwijalna grupa na liście); null = „Bez sekcji". */
    val section: TaskSection?,
    /** Szacowany nakład („potrzebny czas") w minutach; null = nie podano. */
    val estimatedMinutes: Int?,
    /** SLA w godzinach (24 / 168 / 720); termin SLA = createdAt + slaHours. */
    val slaHours: Int?,
    /** Liczba komentarzy — znacznik na wierszu listy (wątek wchodzi w E5). */
    val commentCount: Int,
    /** Kto zlecił: id użytkownika albo „system" (automat board360). */
    val createdBy: String?,
    val createdAt: String,
    val updatedAt: String?,
    val source: TaskSource?,
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

    /** Inicjały do awatara na liście zadań — „Jan Serwisant" → „JS". */
    val initials: String
        get() = displayName.split(' ', '.', '-')
            .filter { it.isNotBlank() }
            .take(2)
            .map { it.first().uppercaseChar() }
            .joinToString("")
            .ifBlank { "?" }
}
