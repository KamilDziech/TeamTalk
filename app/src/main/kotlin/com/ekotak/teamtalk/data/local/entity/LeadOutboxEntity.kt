package com.ekotak.teamtalk.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Lead zapisany bez zasięgu — na targach i u klienta sieci bywa mało
 * (ustalenie 2026-09-13). Serwer nie zna go wcale, więc nie ma czego
 * cache'ować ani z czym się godzić: wiersz to gotowe ciało żądania, które
 * worker wysyła i kasuje.
 */
@Entity(tableName = "lead_outbox")
data class LeadOutboxEntity(
    /** `clientRef` żądania — ten sam przy każdej próbie, serwer po nim rozpoznaje duplikat. */
    @PrimaryKey val clientRef: String,
    /** `AppLeadRequestDto` jako JSON. */
    val payload: String,
    /** Imię i nazwisko klienta — do powiadomienia, gdy serwer odrzuci zgłoszenie. */
    val fullName: String,
    val createdAt: Long,
)
