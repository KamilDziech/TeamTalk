package com.ekotak.teamtalk.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cache i kolejka modułu Email.
 *
 * Pocztę czyta się dokładnie tam, gdzie nie ma zasięgu: w kotłowni, w piwnicy
 * klienta, w busie między Kobiernicami a Gliwicami. Stąd pełny offline
 * z kolejką (ustalenie 2026-09-06), jak w Zadaniach, Serwisie i Zamówieniu.
 *
 * Kluczowa decyzja tego cache'u: WĄTEK PAMIĘTA, W KTÓRYM WIDOKU GO WIDZIANO
 * (`scope`). Ta sama skrzynka firmowa daje inną listę w „Moje" i w „Wszystkie",
 * a wycinek opiekuna liczy serwer — telefon nie ma z czego go odtworzyć. Gdyby
 * obie listy leżały w jednym worku, po wejściu w „Moje" bez zasięgu wysypałaby
 * się cudza korespondencja pobrana wcześniej w widoku „Wszystkie".
 */

/** Skrzynka (zakładka modułu) — firmowa albo personalna. */
@Entity(tableName = "email_accounts")
data class EmailAccountEntity(
    @PrimaryKey val id: String,
    val address: String,
    val displayName: String?,
    /** `shared` albo `personal`. */
    val kind: String,
    /** Czy wolno przełączyć widok na „Wszystkie" (`email.view_all`). */
    val canViewAll: Boolean,
    val unread: Int,
    /** Kolejność zakładek z serwera — firmowa pierwsza. */
    val position: Int,
    val syncedAt: Long,
)

/** Licznik folderu w danej skrzynce i widoku (szuflada modułu). */
@Entity(tableName = "email_folders", primaryKeys = ["accountId", "scope", "folder"])
data class EmailFolderCountEntity(
    val accountId: String,
    val scope: String,
    val folder: String,
    val total: Int,
    val unread: Int,
    val syncedAt: Long,
)

/**
 * Nagłówek wątku. Klucz jest złożony, bo ten sam wątek serwera bywa w cache
 * dwa razy: raz w widoku „Moje", raz w „Wszystkie".
 */
@Entity(tableName = "email_threads", primaryKeys = ["id", "accountId", "scope"])
data class EmailThreadEntity(
    /** Id serwerowe albo lokalne (`local:…`) dla wiadomości z kolejki. */
    val id: String,
    val accountId: String,
    val scope: String,
    val subject: String,
    val folder: String,
    val lastAt: String,
    val unread: Boolean,
    val starred: Boolean,
    val dealId: String?,
    /**
     * Czytelna etykieta dowiązanego deala („Nazwisko · etap"). Lista wątków jej
     * nie przysyła — dopisujemy ją przy otwarciu wątku, żeby chip nad
     * korespondencją miał co pokazać także bez zasięgu.
     */
    val dealLabel: String?,
    val fromName: String?,
    val fromAddr: String,
    val snippet: String,
    val messageCount: Int,
    val hasAttachment: Boolean,
    /** Etykiety jako `id|nazwa|kolor` po średnikach — nigdy po nich nie szukamy. */
    val labelsRaw: String,
    val syncedAt: Long,
)

/**
 * Wiadomość otwartego wątku. Trzymamy WSZYSTKIE odwiedzone wątki, nie tylko
 * ostatni: człowiek wraca do korespondencji, którą czytał rano przy kliencie,
 * i bez zasięgu ma ją zobaczyć w całości.
 */
@Entity(tableName = "email_messages")
data class EmailMessageEntity(
    @PrimaryKey val id: String,
    val threadId: String,
    val outbound: Boolean,
    val fromAddr: String,
    val fromName: String?,
    /** Adresy po przecinku — pole wyświetlane, nie wyszukiwane. */
    val toAddrs: String,
    val ccAddrs: String,
    val subject: String,
    val bodyText: String?,
    val status: String,
    val createdAt: String,
    val syncedAt: Long,
)

/**
 * Załącznik. `localUri` wypełniamy dla plików dopiętych na telefonie i jeszcze
 * niewysłanych — trzymamy `content://`, a nie treść pliku: kopiowanie 20 MB do
 * bazy po to, by chwilę później je wysłać, zjadałoby pamięć bez powodu.
 */
@Entity(tableName = "email_attachments")
data class EmailAttachmentEntity(
    @PrimaryKey val id: String,
    val messageId: String,
    val filename: String,
    val mimeType: String,
    val sizeBytes: Long,
    val localUri: String?,
)

/** Etykiety organizacji — do pokazania w szufladzie i przy wątku. */
@Entity(tableName = "email_labels")
data class EmailLabelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val color: String,
    val syncedAt: Long,
)

/**
 * Zapis czekający na wysyłkę.
 *
 * Klucz (`targetId`, `kind`) sprawia, że kolejna zmiana tego samego wątku
 * nadpisuje poprzednią — liczy się ostatni stan gwiazdki, a nie droga, którą
 * człowiek do niej doszedł. Jedna kolejka na wszystkie rodzaje, bo kolejność
 * między nimi ma znaczenie: odpowiedź napisana offline musi pójść PO tym, jak
 * wątek zostanie przeniesiony, a nie odwrotnie.
 */
@Entity(tableName = "email_mutations", primaryKeys = ["targetId", "kind"])
data class EmailMutationEntity(
    /** Id wątku (zmiany) albo lokalne id wiadomości (`local:…`) przy wysyłce. */
    val targetId: String,
    val kind: String,
    /** Gotowe ciało żądania w JSON. */
    val payload: String,
    val createdAt: Long,
) {
    companion object {
        /** `PATCH /api/email/threads/{id}` — gwiazdka, przeczytane, folder, deal. */
        const val KIND_PATCH = "email_patch"

        /** `DELETE /api/email/threads/{id}` — do kosza albo trwale. */
        const val KIND_DELETE = "email_delete"

        /** `POST /api/email/messages` — wysyłka (nowy wątek albo odpowiedź). */
        const val KIND_SEND = "email_send"

        /** `POST /api/email/drafts` — wersja robocza zapisana bez zasięgu. */
        const val KIND_DRAFT = "email_draft"

        /** Prefiks identyfikatora nadawanego lokalnie do czasu wysłania. */
        const val LOCAL_ID_PREFIX = "local:"
    }
}
