package com.ekotak.teamtalk.domain.model

/**
 * Jedno pole zmiany. Opakowanie jest potrzebne, bo `null` znaczy tu co innego
 * niż brak wpisu: `Edit(null)` czyści wartość w board360, a pominięte pole
 * zostawia ją w spokoju (`PATCH /api/tasks/:id` czyta obecność klucza).
 */
@JvmInline
value class Edit<out T>(val value: T)

/**
 * Zmiana zadania — pola nietknięte zostają `null` i nie trafiają do żądania.
 * Kolejka offline (E3) będzie kolejkowała dokładnie takie porcje: jedno pole
 * na raz, żeby dwie zmiany zrobione bez zasięgu nie nadpisywały się nawzajem.
 */
data class TaskPatch(
    val title: Edit<String>? = null,
    val description: Edit<String?>? = null,
    val status: Edit<TaskStatus>? = null,
    val priority: Edit<TaskPriority>? = null,
    val assigneeId: Edit<String?>? = null,
    val dueAt: Edit<String?>? = null,
    val section: Edit<TaskSection?>? = null,
    val estimatedMinutes: Edit<Int?>? = null,
    val slaHours: Edit<Int?>? = null,
) {
    /** Pusty patch nie ma po co jechać na serwer. */
    val isEmpty: Boolean
        get() = title == null && description == null && status == null && priority == null &&
            assigneeId == null && dueAt == null && section == null &&
            estimatedMinutes == null && slaHours == null
}
