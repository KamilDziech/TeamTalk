package com.ekotak.teamtalk.data.local.entity

import androidx.room.Entity

/**
 * Zmiana zadania czekająca na wysyłkę — sedno kolejki offline (E3).
 *
 * Jeden wiersz = jedno pole jednego zadania, bo `PATCH /api/tasks/:id` czyta
 * obecność klucza: dzięki temu zmiana terminu zrobiona w tunelu nie cofa
 * statusu odhaczonego pięć minut wcześniej. Klucz główny (`taskId`, `field`)
 * sprawia, że ponowna zmiana tego samego pola nadpisuje poprzednią — w kolejce
 * nie ma po co trzymać historii, liczy się ostatnia decyzja człowieka.
 *
 * [payload] to gotowy fragment ciała żądania (`{"status":"done"}`), żeby
 * kolejka nie musiała znać typów pól ani ich kolejno serializować od nowa.
 */
@Entity(tableName = "task_mutations", primaryKeys = ["taskId", "field"])
data class TaskMutationEntity(
    val taskId: String,
    val field: String,
    val payload: String,
    /** Kiedy człowiek zrobił zmianę — kolejność wysyłki i podpowiedź w UI. */
    val createdAt: Long,
)
