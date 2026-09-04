package com.ekotak.teamtalk.domain.model

/** Stan pojedynczego przeglądu liczony przez API (`computedStatus`). */
enum class WarrantyInspectionStatus(val wire: String, val label: String) {
    DONE("done", "Wykonany"),
    OVERDUE("overdue", "Po terminie"),
    PLANNED("planned", "Zaplanowany"),
    UNSCHEDULED("unscheduled", "Bez terminu");

    companion object {
        fun fromWire(value: String?): WarrantyInspectionStatus =
            entries.firstOrNull { it.wire == value } ?: UNSCHEDULED
    }
}

/** Liczba przeglądów w gwarancji Panasonic (5 lat). */
const val MAX_WARRANTY_INSPECTIONS = 5

/**
 * Pozycja harmonogramu karty gwarancyjnej (rok 1..5).
 * [suspect] = data planowana przed uruchomieniem instalacji — do korekty.
 */
data class WarrantyInspection(
    val id: String,
    val cardId: String,
    val ordinal: Int,
    val plannedAt: String?,
    val doneAt: String?,
    val price: Int?,
    val technicianId: String?,
    val note: String?,
    val computedStatus: WarrantyInspectionStatus,
    val suspect: Boolean,
)

/**
 * Karta przeglądów gwarancyjnych (Panasonic) — jedno urządzenie, pięć przeglądów.
 * Odwzorowanie `WarrantyCardView` z board360; liczniki dokleja API.
 */
data class WarrantyCard(
    val id: String,
    val brand: String,
    val name: String,
    /** Adres jako wolny tekst; współrzędne idą osobno ze snapshotu geo. */
    val location: String?,
    val commissionedAt: String?,
    val status: WarrantyCardStatus,
    val outdoorModel: String?,
    val outdoorSerial: String?,
    val indoorModel: String?,
    val indoorSerial: String?,
    val note: String?,
    val inspections: List<WarrantyInspection>,
    val doneCount: Int,
    val overdueCount: Int,
    val suspectCount: Int,
    val nextPlannedAt: String?,
    /** Czy zmiana czeka w kolejce offline. */
    val pendingSync: Boolean = false,
)
