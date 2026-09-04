package com.ekotak.teamtalk.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cache offline modułu Serwis. Ekran czyta wyłącznie stąd — sieć tylko dolewa
 * świeże dane — więc lista otwiera się w kotłowni bez zasięgu tak samo jak
 * w biurze.
 *
 * [localOnly] oznacza zgłoszenie zapisane bez zasięgu: ma identyfikator
 * `local:<uuid>` i czeka w kolejce na wysłanie, po której dostaje id z serwera.
 */
@Entity(tableName = "service_jobs")
data class ServiceJobEntity(
    @PrimaryKey val id: String,
    val clientId: String?,
    val dealId: String?,
    val type: String,
    val status: String,
    val priority: String,
    val technicianId: String?,
    val scheduledAt: String?,
    val note: String?,
    val slaHours: Int?,
    val slaDueAt: String?,
    val slaBreached: Boolean,
    val localOnly: Boolean,
    val syncedAt: Long,
)

/**
 * Karta gwarancyjna w cache. Harmonogram pięciu przeglądów leży w jednej
 * kolumnie jako JSON: zawsze czytamy go w komplecie (pasek świateł potrzebuje
 * wszystkich pozycji), a osobna tabela dokładałaby złączenie bez zysku.
 */
@Entity(tableName = "warranty_cards")
data class WarrantyCardEntity(
    @PrimaryKey val id: String,
    val brand: String,
    val name: String,
    val location: String?,
    val commissionedAt: String?,
    val status: String,
    val outdoorModel: String?,
    val outdoorSerial: String?,
    val indoorModel: String?,
    val indoorSerial: String?,
    val note: String?,
    /** `List<WarrantyInspectionDto>` zserializowane wspólnym `Json` aplikacji. */
    val inspectionsJson: String,
    val doneCount: Int,
    val overdueCount: Int,
    val suspectCount: Int,
    val nextPlannedAt: String?,
    val syncedAt: Long,
)

/**
 * Klient w kształcie potrzebnym liście serwisu (etykieta, miasto, telefon,
 * adres). Kartoteka ma własny cache z kompletem pól, ale moduł Serwis musi
 * działać także wtedy, gdy nikt tej kartoteki jeszcze nie otworzył.
 */
@Entity(tableName = "service_clients")
data class ServiceClientEntity(
    @PrimaryKey val id: String,
    val label: String,
    val city: String?,
    val phone: String?,
    val address: String?,
)

/** Serwisant — przypisanie zlecenia i awatar w wierszu, także bez zasięgu. */
@Entity(tableName = "service_technicians")
data class ServiceTechnicianEntity(
    @PrimaryKey val id: String,
    val email: String,
    val firstName: String?,
    val lastName: String?,
)

/**
 * Zmiana czekająca na wysyłkę — kolejka offline modułu Serwis, zbudowana jak
 * ta w Zadaniach: jeden wiersz = jedno pole jednego zlecenia, więc zmiana
 * terminu zrobiona w tunelu nie cofa statusu odhaczonego pięć minut wcześniej.
 *
 * Wyjątkiem jest tworzenie (ustalenie 2026-09-02): zgłoszenie zapisane bez
 * zasięgu siedzi w kolejce pod polem [FIELD_CREATE] z całym ciałem `POST`,
 * a jego `jobId` to lokalny identyfikator, który po wysłaniu podmieniamy na
 * serwerowy razem z pozostałymi wierszami kolejki.
 */
@Entity(tableName = "service_mutations", primaryKeys = ["jobId", "field"])
data class ServiceMutationEntity(
    val jobId: String,
    val field: String,
    val payload: String,
    val createdAt: Long,
) {
    companion object {
        /** Pseudopole tworzenia — porządkuje się przed każdą zmianą pola. */
        const val FIELD_CREATE = "__create"

        /** Prefiks identyfikatora nadawanego lokalnie do czasu wysłania. */
        const val LOCAL_ID_PREFIX = "local:"
    }
}
