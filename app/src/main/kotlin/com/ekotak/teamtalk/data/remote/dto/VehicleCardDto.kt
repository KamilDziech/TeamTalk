package com.ekotak.teamtalk.data.remote.dto

import kotlinx.serialization.Serializable

/*
 * Karta auta — zakładki Zadania / Pliki / Historia / Reguły (board360
 * docs/tasks/flota-karta-auta.md, E5). Kształty 1:1 z odpowiedziami API
 * (`api/src/modules/fleet/presentation/vehicle-card.controller.ts`); wszystko
 * opcjonalne, bo telefon ma przeżyć dołożenie pola po stronie serwera.
 */

/** `GET api/assets/{id}/tasks` */
@Serializable
data class VehicleTasksDto(
    val items: List<VehicleTaskItemDto> = emptyList(),
    val history: List<VehicleTaskHistoryDto> = emptyList(),
)

@Serializable
data class VehicleTaskItemDto(
    val deadlineId: String,
    val kind: String = "inny",
    val label: String? = null,
    val dueDate: String? = null,
    val dueMileage: Int? = null,
    val leadDays: Int = 14,
    val recurrenceMonths: Int? = null,
    val recurrenceKm: Int? = null,
    val ownerLabel: String? = null,
    val ownerIsDriver: Boolean = false,
    /** planned | active | done */
    val state: String = "planned",
    val activatesAt: String? = null,
    val kmToActivation: Int? = null,
    val task: VehicleTaskRefDto? = null,
    val doneAt: String? = null,
)

@Serializable
data class VehicleTaskRefDto(
    val id: String,
    val title: String = "",
    val status: String = "open",
    val assigneeLabel: String? = null,
    val dueAt: String? = null,
)

@Serializable
data class VehicleTaskHistoryDto(
    val id: String,
    val title: String = "",
    val status: String = "open",
    val assigneeLabel: String? = null,
    val dueAt: String? = null,
    val updatedAt: String? = null,
)

/** `GET api/assets/{id}/files` — pliki auta i skany polis. */
@Serializable
data class VehicleFileDto(
    val id: String,
    /** file | policy */
    val source: String = "file",
    val category: String = "inne",
    val name: String = "plik",
    val note: String? = null,
    val contentType: String? = null,
    val size: Long? = null,
    val createdAt: String? = null,
    /** Ścieżka względem `/api`, np. `/assets/files/{id}`. */
    val downloadPath: String = "",
)

/** `GET api/assets/{id}/history` — jedna oś czasu. */
@Serializable
data class VehicleHistoryItemDto(
    val id: String,
    /** alarm | obd | rule | maintenance | event */
    val kind: String = "event",
    val at: String,
    val title: String = "",
    val detail: String? = null,
    val lat: Double? = null,
    val lng: Double? = null,
)

/** `GET api/assets/{id}/rules` — reguły auta (na telefonie tylko do odczytu, D1). */
@Serializable
data class VehicleRuleDto(
    val id: String,
    val name: String = "",
    val path: List<List<Double>> = emptyList(),
    val widthM: Int = 30,
    val speedLimitKmh: Int = 0,
    val notifyUserId: String? = null,
    val active: Boolean = true,
    val lastTriggeredAt: String? = null,
)
