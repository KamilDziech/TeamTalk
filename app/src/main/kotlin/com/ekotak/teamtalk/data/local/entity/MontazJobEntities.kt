package com.ekotak.teamtalk.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cache modułu Montaż — lista wyjazdów i teczka jednego wyjazdu.
 *
 * Moduł pracuje tam, gdzie zasięgu nie ma: piwnica budowanego domu, kotłownia
 * w bloku, wieś bez LTE. Teczka jest jedynym dokumentem ekipy na miejscu, więc
 * musi otworzyć się z pamięci telefonu w komplecie — z adresem, zakresem
 * z umowy, listą sprzętu i pytaniami protokołu.
 */

/**
 * Jeden montaż: pola listy osobno (po nich sortujemy i filtrujemy), a CAŁA
 * teczka w jednym polu JSON.
 *
 * Rozbicie teczki na tabele nic by tu nie dało: czyta się ją i zapisuje
 * w całości, jednym żądaniem, a osobne tabele na zakres umowy, obsadę, sprzęt
 * i pytania protokołu kazałyby ją składać przy każdym otwarciu karty — i mnożyć
 * migracje przy każdej zmianie kontraktu.
 */
@Entity(tableName = "montaz_jobs")
data class MontazJobEntity(
    @PrimaryKey val id: String,
    val dealId: String,
    val dealCode: String?,
    val clientName: String,
    val address: String?,
    val city: String?,
    val scheduledAt: String?,
    val status: String,
    val durationDays: Int,
    val difficulty: String?,
    /** Ścieżki węzłów zakresu (konwerter `Converters.fromStringList`). */
    val scopeNames: List<String> = emptyList(),
    /**
     * Pełna teczka (`JobPacketDto`) albo `null`, gdy wiersz przyszedł z samej
     * listy. Lista schodzi przy każdym otwarciu modułu, teczka dopiero przy
     * wejściu w konkretny wyjazd — i dopiero wtedy ma sens ją trzymać.
     */
    val packetJson: String?,
    val syncedAt: Long,
)

/**
 * Protokół odbioru montażu — wersja robocza i zamknięta.
 *
 * Wiersz istnieje także wtedy, gdy nic jeszcze nie poszło na serwer: ekipa
 * wypełnia protokół na budowie, często bez zasięgu, a odpowiedzi zapisywane po
 * każdym pytaniu są jedynym śladem tej pracy. `pendingSince` mówi, że zapis
 * czeka w kolejce — dla ekipy „zapisane", dla biura jeszcze nie.
 */
@Entity(tableName = "montaz_protocols")
data class MontazProtocolEntity(
    @PrimaryKey val installationId: String,
    /** Treść protokołu (`formData`) w kształcie `PROTOCOL_SCHEMA_VERSION`. */
    val formJson: String,
    /** Podpis palcem jako data URL; `null` = jeszcze nie podpisano. */
    val signature: String?,
    /** ISO; `null` = wersja robocza. */
    val closedAt: String?,
    val pendingSince: Long?,
    val syncedAt: Long,
)
