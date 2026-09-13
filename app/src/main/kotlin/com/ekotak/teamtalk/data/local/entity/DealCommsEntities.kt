package com.ekotak.teamtalk.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Cache i kolejka zakładki „Komunikacja" karty deala.
 *
 * Powód offline'u jest tu mocniejszy niż gdzie indziej: korespondencję z klientem
 * czyta się i pisze PRZY KLIENCIE — w kotłowni, na budowie, w aucie po
 * spotkaniu. Streszczenie rozmowy spisane bez zasięgu i utracone razem z ekranem
 * byłoby najgorszym możliwym zachowaniem tej zakładki, bo drugi raz nikt go
 * z pamięci nie odtworzy.
 *
 * Poczta ma własny cache (`email_*`) i tu nie wchodzi — zakładka czyta ją tym
 * samym repozytorium, co moduł Email.
 */

/** Wiadomość wewnętrznego wątku zespołu o dealu. */
@Entity(tableName = "deal_comments", indices = [Index(value = ["dealId"])])
data class DealCommentEntity(
    @PrimaryKey val id: String,
    val dealId: String,
    val authorId: String,
    val authorName: String,
    val body: String,
    val createdAt: String,
    val mine: Boolean,
    val syncedAt: Long,
)

/** Wiadomość ze skrzynki WhatsApp deala. */
@Entity(tableName = "deal_whatsapp", indices = [Index(value = ["dealId"])])
data class DealWhatsappEntity(
    @PrimaryKey val id: String,
    val dealId: String,
    /** `inbound` albo `outbound`. */
    val direction: String,
    val body: String?,
    val template: String?,
    val status: String,
    val createdAt: String,
    val syncedAt: Long,
)

/**
 * Streszczenie rozmowy przypiętej do deala. Osobno od `voice_reports`: tamta
 * tabela to cache modułu notatek po połączeniu (klucz: połączenie i klient),
 * a tu leży wycinek deala z polami, których notatka z nagrania nie ma —
 * ustaleniami i następnym krokiem.
 */
@Entity(tableName = "deal_call_summaries", indices = [Index(value = ["dealId"])])
data class DealCallSummaryEntity(
    @PrimaryKey val id: String,
    val dealId: String,
    val body: String,
    val agreements: String?,
    val nextStep: String?,
    val phoneNumber: String?,
    val direction: String?,
    val occurredAt: String,
    val durationSec: Int?,
    val hasRecording: Boolean,
    val manual: Boolean,
    val syncedAt: Long,
)

/**
 * Wiadomość czekająca na wysyłkę.
 *
 * Klucz jest LOKALNYM identyfikatorem wpisu, a nie parą (deal, rodzaj) jak
 * w kolejce karty deala. Powód: to nie są decyzje, które można nadpisać ostatnią
 * wersją — trzy wiadomości napisane pod rząd bez zasięgu to trzy osobne
 * wiadomości i wszystkie mają dojść, w kolejności pisania.
 */
@Entity(tableName = "deal_comm_mutations", indices = [Index(value = ["dealId"])])
data class DealCommMutationEntity(
    @PrimaryKey val localId: String,
    val dealId: String,
    val kind: String,
    /** Gotowe ciało żądania w JSON. */
    val payload: String,
    val createdAt: Long,
) {
    companion object {
        /** `POST /api/discussions/deal/:id/comments` — wpis wewnętrzny. */
        const val KIND_COMMENT = "comment"

        /** `POST /api/deals/:id/whatsapp` — wiadomość do klienta. */
        const val KIND_WHATSAPP = "whatsapp"

        /** `POST /api/voice-reports/manual` — streszczenie rozmowy. */
        const val KIND_CALL = "call"

        /** Prefiks identyfikatora nadawanego lokalnie do czasu wysłania. */
        const val LOCAL_ID_PREFIX = "local:"

        fun newLocalId(): String = LOCAL_ID_PREFIX + java.util.UUID.randomUUID()
    }
}
