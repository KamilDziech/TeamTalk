package com.ekotak.teamtalk.domain.model

/**
 * Zmiana zlecenia serwisowego — pola nietknięte zostają `null` i nie trafiają do
 * żądania. [Edit] rozróżnia „wyczyść” (`Edit(null)`) od „nie ruszaj” (pominięte),
 * bo `PATCH /api/service-jobs/:id` czyta obecność klucza. Ten sam kształt trafia
 * do kolejki offline: jedno pole na raz, żeby dwie zmiany zrobione bez zasięgu
 * nie nadpisywały się nawzajem.
 */
data class ServiceJobPatch(
    val clientId: Edit<String>? = null,
    val status: Edit<ServiceJobStatus>? = null,
    val priority: Edit<ServiceJobPriority>? = null,
    val note: Edit<String?>? = null,
    val technicianId: Edit<String?>? = null,
    val scheduledAt: Edit<String?>? = null,
    val slaHours: Edit<Int?>? = null,
) {
    val isEmpty: Boolean
        get() = clientId == null && status == null && priority == null && note == null &&
            technicianId == null && scheduledAt == null && slaHours == null
}

/**
 * Nowe zlecenie. Klient jest opcjonalny — zgłoszenie wolno zapisać „na szybko”,
 * z samym opisem usterki; wiersz świeci wtedy czerwienią do czasu uzupełnienia.
 */
data class ServiceJobDraft(
    val clientId: String? = null,
    val type: ServiceJobType,
    val technicianId: String? = null,
    val scheduledAt: String? = null,
    val note: String? = null,
    val priority: ServiceJobPriority? = null,
    val slaHours: Int? = null,
)

/** Nowa karta przeglądów gwarancyjnych — wymagana jest tylko nazwa. */
data class WarrantyCardDraft(
    val name: String,
    val brand: String = "Panasonic",
    val location: String? = null,
    val commissionedAt: String? = null,
    val status: WarrantyCardStatus = WarrantyCardStatus.OCZEKUJACE,
    val outdoorModel: String? = null,
    val outdoorSerial: String? = null,
    val indoorModel: String? = null,
    val indoorSerial: String? = null,
    val note: String? = null,
)

/** Zmiana pól karty gwarancyjnej (`PATCH /api/warranty-cards/:id`). */
data class WarrantyCardPatch(
    val brand: Edit<String>? = null,
    val status: Edit<WarrantyCardStatus>? = null,
    val location: Edit<String?>? = null,
    val commissionedAt: Edit<String?>? = null,
    val outdoorModel: Edit<String?>? = null,
    val outdoorSerial: Edit<String?>? = null,
    val indoorModel: Edit<String?>? = null,
    val indoorSerial: Edit<String?>? = null,
    val note: Edit<String?>? = null,
) {
    val isEmpty: Boolean
        get() = brand == null && status == null && location == null && commissionedAt == null &&
            outdoorModel == null && outdoorSerial == null && indoorModel == null &&
            indoorSerial == null && note == null
}

/**
 * Zapis pozycji harmonogramu (`PUT /api/warranty-cards/:id/inspections`).
 * Trasa jest „upsertem” po numerze przeglądu — wysyłamy komplet trzech pól,
 * bo tak samo robi to panel (formularz wiersza ma jeden przycisk „Zapisz”).
 */
data class WarrantyInspectionUpsert(
    val ordinal: Int,
    val plannedAt: String?,
    val doneAt: String?,
    val price: Int?,
    val technicianId: String? = null,
    val note: String? = null,
)
