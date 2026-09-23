package com.ekotak.teamtalk.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Cache i kolejka Komunikatora.
 *
 * Rozmowa z zespołem toczy się dokładnie tam, gdzie nie ma zasięgu — w
 * kotłowni, na budowie, w piwnicy. Skrzynka bez cache'u pokazywałaby wtedy
 * pustą listę, a napisana i utracona wiadomość byłaby najgorszym możliwym
 * zachowaniem tego modułu.
 *
 * Wiersze trzymają ODPOWIEDŹ SERWERA W CAŁOŚCI jako JSON (tak samo jak Cele):
 * kontrakt wiadomości ma kilkanaście pól i będzie rósł — reakcje, ankiety,
 * podglądy linków — a dopisanie pola w panelu nie ma wymuszać migracji bazy na
 * telefonach zespołu. Osobnymi kolumnami są wyłącznie te, po których się
 * sortuje i filtruje.
 */

/** Pozycja skrzynki. `archived` rozdziela dwa widoki listy, `pinned` i `lastAt`
 *  dają kolejność bez rozpakowywania JSON-a każdego wiersza. */
@Entity(tableName = "chat_threads")
data class ChatThreadEntity(
    @PrimaryKey val id: String,
    val payload: String,
    val lastAt: String,
    val pinned: Boolean,
    val archived: Boolean,
    val syncedAt: Long,
)

/** Wiadomość wątku. `createdAt` w ISO-8601 UTC, więc porządek leksykalny jest
 *  porządkiem chronologicznym — sortujemy po nim wprost w SQL. */
@Entity(tableName = "chat_thread_messages", indices = [Index(value = ["threadId"])])
data class ChatMessageEntity(
    @PrimaryKey val id: String,
    val threadId: String,
    val payload: String,
    val createdAt: String,
    val syncedAt: Long,
)

/** Skład rozmowy i katalog osób do nowej rozmowy — jeden wiersz na osobę. */
@Entity(tableName = "chat_people")
data class ChatPersonEntity(
    @PrimaryKey val id: String,
    val payload: String,
    val name: String,
    val department: String,
    val syncedAt: Long,
)

/**
 * Wiadomość czekająca na wysyłkę.
 *
 * Klucz to LOKALNY identyfikator wpisu, nie para (wątek, rodzaj): trzy zdania
 * napisane pod rząd bez zasięgu to trzy osobne wiadomości i wszystkie mają
 * dojść, w kolejności pisania.
 *
 * Kolejkujemy WYŁĄCZNIE wiadomości. Reakcja, gwiazdka, głos w ankiecie czy
 * znacznik przeczytania to informacje, nie decyzje — bez zasięgu przepadają bez
 * śladu i nic się nie dzieje, a kolejka ich stanów musiałaby rozstrzygać
 * konflikty z tym, co w międzyczasie zrobił ktoś inny.
 */
@Entity(tableName = "chat_mutations", indices = [Index(value = ["threadId"])])
data class ChatMutationEntity(
    @PrimaryKey val localId: String,
    val threadId: String,
    val kind: String,
    /** Gotowe ciało żądania w JSON (dla załącznika — same metadane). */
    val payload: String,
    /** Ścieżka pliku w cache'u telefonu; tylko dla załączników. */
    val filePath: String?,
    val fileName: String?,
    val mimeType: String?,
    val createdAt: Long,
) {
    companion object {
        /** `POST /api/chat/threads/:id/messages` — zwykła wiadomość. */
        const val KIND_TEXT = "text"

        /** `POST /api/chat/threads/:id/attachments` — zdjęcie, plik, głosówka. */
        const val KIND_ATTACHMENT = "attachment"

        /** `POST /api/chat/threads/:id/polls` — ankieta. */
        const val KIND_POLL = "poll"

        const val LOCAL_ID_PREFIX = "local:"

        fun newLocalId(): String = LOCAL_ID_PREFIX + java.util.UUID.randomUUID()
    }
}
