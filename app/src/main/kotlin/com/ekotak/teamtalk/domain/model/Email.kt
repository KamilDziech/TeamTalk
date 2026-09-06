package com.ekotak.teamtalk.domain.model

/**
 * Moduł Email — poczta z huba Komunikacja panelu, w układzie Gmaila.
 *
 * Dwie skrzynki na osobę (ustalenie 2026-09-06):
 *  • FIRMOWA `kontakt@ekotak.pl` — wspólna dla firmy, ale każdy widzi w niej
 *    domyślnie swój WYCINEK: wątki swoich deali plus niedowiązane od adresów
 *    swoich klientów. Całą skrzynkę otwiera przełącznik „Moje / Wszystkie",
 *    dostępny przy [Mailbox.canViewAll] (uprawnienie `email.view_all`).
 *  • PERSONALNA — pod adresem z konta pracownika, w całości i tylko dla niego.
 *
 * Wycinek liczy SERWER. Telefon go nie odtwarza i nie próbuje zgadywać: to samo
 * zapytanie z innym `scope` daje inną listę, a cache trzyma je osobno.
 */

/** Folder wątku — te same sześć co w Gmailu i w panelu. */
enum class EmailFolder(val wire: String, val label: String) {
    INBOX("inbox", "Odebrane"),
    SENT("sent", "Wysłane"),
    DRAFTS("drafts", "Wersje robocze"),
    ARCHIVE("archive", "Archiwum"),
    SPAM("spam", "Spam"),
    TRASH("trash", "Kosz");

    companion object {
        fun fromWire(wire: String?): EmailFolder =
            entries.firstOrNull { it.wire == wire } ?: INBOX
    }
}

/** Widok skrzynki firmowej. */
enum class MailboxScope(val wire: String) {
    /** Wycinek opiekuna — domyślny dla każdego. */
    MINE("mine"),

    /** Cała skrzynka firmowa; wymaga `email.view_all`. */
    ALL("all"),
}

enum class MailboxKind(val wire: String) {
    SHARED("shared"),
    PERSONAL("personal");

    companion object {
        fun fromWire(wire: String?): MailboxKind =
            entries.firstOrNull { it.wire == wire } ?: SHARED
    }
}

/** Skrzynka = zakładka modułu. */
data class Mailbox(
    val id: String,
    val address: String,
    val displayName: String?,
    val kind: MailboxKind,
    /** Czy wolno przełączyć widok na „Wszystkie". */
    val canViewAll: Boolean,
    /** Nieprzeczytane w Odebranych, w domyślnym widoku osoby. */
    val unread: Int,
) {
    /** Część przed „@" — etykieta zakładki na 360 dp. */
    val shortLabel: String get() = address.substringBefore('@')
}

data class EmailLabel(val id: String, val name: String, val color: String)

data class EmailFolderCount(val folder: EmailFolder, val total: Int, val unread: Int)

/** Wiersz listy wątków. */
data class EmailThread(
    val id: String,
    val accountId: String,
    val subject: String,
    val folder: EmailFolder,
    /** ISO-8601 z serwera; formatowanie po stronie ekranu. */
    val lastAt: String,
    val unread: Boolean,
    val starred: Boolean,
    val dealId: String?,
    val fromName: String?,
    val fromAddr: String,
    val snippet: String,
    val messageCount: Int,
    val hasAttachment: Boolean,
    val labels: List<EmailLabel>,
    /** Zmiana czeka w kolejce offline — wiersz dostaje znacznik synchronizacji. */
    val pendingSync: Boolean = false,
) {
    /** Podpis wiersza: nazwa nadawcy, a przy jej braku sam adres. */
    val who: String get() = fromName?.takeIf { it.isNotBlank() } ?: fromAddr
}

data class EmailAttachment(
    val id: String,
    val filename: String,
    val mimeType: String,
    val sizeBytes: Long,
    /**
     * Załącznik dopięty na telefonie i czekający na wysyłkę — trzymamy `content://`
     * pliku, nie jego treść. Kopiowanie 20 MB do bazy tylko po to, żeby chwilę
     * później je wysłać, zjadałoby pamięć telefonu bez powodu.
     */
    val localUri: String? = null,
)

data class EmailMessage(
    val id: String,
    val threadId: String,
    val outbound: Boolean,
    val fromAddr: String,
    val fromName: String?,
    val toAddrs: List<String>,
    val ccAddrs: List<String>,
    val subject: String,
    val bodyText: String?,
    val status: String,
    val createdAt: String,
    val attachments: List<EmailAttachment>,
) {
    /** Bez kredencji SMTP board360 zapisuje wysyłkę jako oczekującą. */
    val awaitingSmtp: Boolean get() = status == STATUS_PENDING_CONFIG
    val isDraft: Boolean get() = status == STATUS_DRAFT

    companion object {
        const val STATUS_PENDING_CONFIG = "pending_config"
        const val STATUS_DRAFT = "draft"

        /** Wiadomość zakolejkowana na telefonie, jeszcze nieznana serwerowi. */
        const val STATUS_QUEUED_LOCAL = "queued_local"
    }
}

/** Wątek otwarty do czytania. */
data class EmailThreadDetail(
    val thread: EmailThread,
    val messages: List<EmailMessage>,
    val labels: List<EmailLabel>,
    val dealLabel: String?,
)

/** Opcja pickera „Powiąż z dealem". */
data class EmailDealOption(val dealId: String, val label: String)

/**
 * Zmiana wątku. Pola nietknięte zostają `null` i nie wchodzą do żądania;
 * [Edit] pozwala odróżnić „nie ruszaj" od „wyczyść" — bez tego nie dałoby się
 * ODPIĄĆ wątku od deala.
 */
data class EmailThreadPatch(
    val starred: Boolean? = null,
    val unread: Boolean? = null,
    val folder: EmailFolder? = null,
    val dealId: Edit<String?>? = null,
    val labelIds: List<String>? = null,
) {
    val isEmpty: Boolean
        get() = starred == null && unread == null && folder == null &&
            dealId == null && labelIds == null
}

/** Treść pisana w oknie tworzenia wiadomości. */
data class EmailDraft(
    /** Z której skrzynki wychodzi. */
    val accountId: String,
    /** Odpowiedź w istniejącym wątku; `null` = nowa wiadomość. */
    val threadId: String? = null,
    val to: List<String> = emptyList(),
    val cc: List<String> = emptyList(),
    val subject: String = "",
    val body: String = "",
    /** Pliki wybrane na telefonie — `content://` + nazwa do pokazania. */
    val attachments: List<EmailDraftAttachment> = emptyList(),
    val dealId: String? = null,
)

data class EmailDraftAttachment(
    val uri: String,
    val filename: String,
    val mimeType: String,
    val sizeBytes: Long,
)

/** Migawka skrzynki dla ekranu listy. */
data class EmailSnapshot(
    val mailboxes: List<Mailbox> = emptyList(),
    val folders: List<EmailFolderCount> = emptyList(),
    val threads: List<EmailThread> = emptyList(),
    val labels: List<EmailLabel> = emptyList(),
    /** Kiedy ostatnio dolano świeże dane; `null` = pokazujemy sam cache. */
    val syncedAt: Long? = null,
)
