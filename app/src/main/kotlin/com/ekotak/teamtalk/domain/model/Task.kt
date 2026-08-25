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
 * Powiązanie z klientem/rozmową zawarte jest w tytule/opisie — `POST /api/tasks`
 * nie przyjmuje `clientId`.
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

/** Członek zespołu (do wyboru osoby przypisanej). `GET /api/tasks/members`. */
data class TaskMember(
    val id: String,
    val email: String,
    val firstName: String?,
    val lastName: String?,
    val role: String?,
) {
    /** Etykieta do wyświetlenia: „Imię Nazwisko" lub e-mail. */
    val displayName: String
        get() = listOfNotNull(firstName, lastName)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { email }
}
