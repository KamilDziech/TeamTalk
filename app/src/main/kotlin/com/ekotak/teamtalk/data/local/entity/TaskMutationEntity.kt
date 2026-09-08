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
 *
 * Wyjątkiem jest tworzenie (ustalenie 2026-09-08, zakładka „Zadania" karty
 * deala): zadanie zapisane bez zasięgu siedzi w kolejce pod pseudopolem
 * [FIELD_CREATE] z całym ciałem `POST` i adresem powiązania, a jego `taskId`
 * to identyfikator nadany lokalnie — po wysłaniu podmieniamy go na serwerowy
 * razem z resztą wierszy tego zadania. Ten sam wzorzec chodzi w Serwisie.
 */
@Entity(tableName = "task_mutations", primaryKeys = ["taskId", "field"])
data class TaskMutationEntity(
    val taskId: String,
    val field: String,
    val payload: String,
    /** Kiedy człowiek zrobił zmianę — kolejność wysyłki i podpowiedź w UI. */
    val createdAt: Long,
) {
    companion object {
        /** Pseudopole tworzenia — porządkuje się przed każdą zmianą pola. */
        const val FIELD_CREATE = "__create"

        /** Prefiks identyfikatora nadawanego lokalnie do czasu wysłania. */
        const val LOCAL_ID_PREFIX = "local:"
    }
}
