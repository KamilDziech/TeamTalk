package com.ekotak.teamtalk.domain.model

/*
 * Karta auta w telefonie (board360 docs/tasks/flota-karta-auta.md, E5): te same
 * zakładki co w panelu. Czasy już w milisekundach — ISO rozbiera repozytorium.
 */

/** Termin auta w zakładce Zadania: zaplanowany (szary), aktywny u opiekuna albo zamknięty. */
data class VehicleTaskItem(
    val deadlineId: String,
    val kind: String,
    val label: String?,
    val dueMillis: Long?,
    val dueMileage: Int?,
    val recurrenceMonths: Int?,
    val recurrenceKm: Int?,
    val ownerLabel: String?,
    val ownerIsDriver: Boolean,
    val state: State,
    val activatesMillis: Long?,
    val kmToActivation: Int?,
    val task: TaskRef?,
    val doneMillis: Long?,
) {
    enum class State { PLANNED, ACTIVE, DONE }

    data class TaskRef(val id: String, val title: String, val status: String, val assigneeLabel: String?)

    val kindLabel: String
        get() = when (kind) {
            "oc" -> "Polisa OC"
            "ac" -> "Polisa AC"
            "przeglad" -> "Przegląd"
            "serwis" -> "Serwis"
            "badanie" -> "Badanie techniczne"
            else -> "Termin"
        }
}

data class VehicleTaskHistoryItem(
    val id: String,
    val title: String,
    val status: String,
    val assigneeLabel: String?,
    val updatedMillis: Long?,
)

data class VehicleTasks(val items: List<VehicleTaskItem>, val history: List<VehicleTaskHistoryItem>)

data class VehicleFile(
    val id: String,
    val fromPolicy: Boolean,
    val category: String,
    val name: String,
    val note: String?,
    val contentType: String,
    val size: Long?,
    val createdMillis: Long?,
    /** Ścieżka pobrania względem korzenia API (`api/assets/files/{id}`). */
    val downloadPath: String,
) {
    val categoryLabel: String
        get() = VEHICLE_FILE_CATEGORIES.firstOrNull { it.first == category }?.second ?: "Inne"
}

/** Kategorie plików auta (D12) — klucz API → etykieta. */
val VEHICLE_FILE_CATEGORIES: List<Pair<String, String>> = listOf(
    "polisa" to "Polisa",
    "dowod_rejestracyjny" to "Dowód rejestracyjny",
    "badanie" to "Badanie techniczne",
    "faktura_serwis" to "Faktura serwisowa",
    "leasing" to "Umowa leasingu",
    "inne" to "Inne",
)

data class VehicleHistoryItem(
    val id: String,
    val kind: String,
    val atMillis: Long,
    val title: String,
    val detail: String?,
    val lat: Double?,
    val lng: Double?,
) {
    val kindLabel: String
        get() = when (kind) {
            "alarm" -> "Alarm"
            "obd" -> "Usterka OBD"
            "rule" -> "Reguła"
            "maintenance" -> "Serwis"
            else -> "Zdarzenie"
        }
}

data class VehicleRule(
    val id: String,
    val name: String,
    val points: Int,
    val widthM: Int,
    val speedLimitKmh: Int,
    val active: Boolean,
    val lastTriggeredMillis: Long?,
)
