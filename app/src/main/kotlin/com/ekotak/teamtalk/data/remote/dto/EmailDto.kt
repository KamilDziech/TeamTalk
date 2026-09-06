package com.ekotak.teamtalk.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Moduł Email — poczta z huba Komunikacja panelu (board360 `api/src/modules/email`).
 * Kształt 1:1 z odpowiedziami kontrolera.
 *
 * Sedno, którego nie widać po samych polach: `GET /api/email/accounts` oddaje
 * DWIE skrzynki tej samej osoby — firmową `kontakt@ekotak.pl` (`kind = shared`)
 * i jej własną (`kind = personal`). W firmowej każdy widzi domyślnie tylko SWÓJ
 * WYCINEK (wątki jego deali plus niedowiązane od adresów jego klientów); całą
 * skrzynkę otwiera parametr `scope=all`, dostępny wyłącznie przy
 * `canViewAll = true` (uprawnienie `email.view_all`). Skrzynka personalna jest
 * widoczna w całości, ale wyłącznie dla właściciela.
 *
 * Daty przychodzą jako ISO-8601 z UTC; trzymamy je tekstem i formatujemy przy
 * rysowaniu, tak samo jak reszta modułów.
 */

/** Skrzynka = zakładka modułu. */
@Serializable
data class EmailAccountDto(
    val id: String,
    val address: String,
    val displayName: String? = null,
    /** `shared` (firmowa) albo `personal` (własna). */
    val kind: String = KIND_SHARED,
    /** Czy w tej skrzynce wolno przełączyć widok na „Wszystkie". */
    val canViewAll: Boolean = false,
    /** Nieprzeczytane w Odebranych, policzone już w domyślnym widoku osoby. */
    val unread: Int = 0,
) {
    companion object {
        const val KIND_SHARED = "shared"
        const val KIND_PERSONAL = "personal"
    }
}

/** Licznik folderu (sidebar panelu = szuflada na telefonie). */
@Serializable
data class EmailFolderCountDto(
    val folder: String,
    val total: Int = 0,
    val unread: Int = 0,
)

@Serializable
data class EmailLabelDto(
    val id: String,
    val name: String,
    val color: String,
)

/** Nagłówek wątku na liście — wszystko, co rysuje wiersz. */
@Serializable
data class EmailThreadDto(
    val id: String,
    val subject: String,
    val folder: String,
    val lastAt: String,
    val unread: Boolean = false,
    val starred: Boolean = false,
    val dealId: String? = null,
    val clientId: String? = null,
    val fromName: String? = null,
    val fromAddr: String,
    val snippet: String = "",
    val messageCount: Int = 1,
    val hasAttachment: Boolean = false,
    val labels: List<EmailLabelDto> = emptyList(),
)

@Serializable
data class EmailAttachmentDto(
    val id: String,
    val filename: String,
    val mimeType: String,
    val sizeBytes: Long = 0,
    val storageKey: String? = null,
)

@Serializable
data class EmailMessageDto(
    val id: String,
    val threadId: String,
    /** `inbound` albo `outbound`. */
    val direction: String,
    val fromAddr: String,
    val fromName: String? = null,
    val toAddrs: List<String> = emptyList(),
    val ccAddrs: List<String> = emptyList(),
    val bccAddrs: List<String> = emptyList(),
    val subject: String = "",
    val bodyText: String? = null,
    val bodyHtml: String? = null,
    /**
     * `received` / `draft` / `sent` / `pending_config`… Bez kredencji SMTP
     * board360 zapisuje wysyłkę jako `pending_config` — na ekranie to napis
     * „oczekuje na wysyłkę", nie błąd.
     */
    val status: String = "received",
    val createdAt: String,
    val attachments: List<EmailAttachmentDto> = emptyList(),
)

/** Szczegół wątku: `GET /api/email/threads/{id}`. */
@Serializable
data class EmailThreadDetailDto(
    val thread: EmailThreadHeadDto,
    val messages: List<EmailMessageDto> = emptyList(),
    val labels: List<EmailLabelDto> = emptyList(),
    /** Czytelna etykieta dowiązanego deala („Nazwisko · etap") albo null. */
    val dealLabel: String? = null,
)

@Serializable
data class EmailThreadHeadDto(
    val id: String,
    val accountId: String,
    val subject: String,
    val folder: String,
    val lastAt: String,
    val unread: Boolean = false,
    val starred: Boolean = false,
    val dealId: String? = null,
    val clientId: String? = null,
)

/** Opcja pickera „Powiąż z dealem". */
@Serializable
data class EmailDealOptionDto(
    val dealId: String,
    val label: String,
)

/**
 * Wysyłka i wersja robocza. `accountId` mówi, z KTÓREJ skrzynki idzie
 * wiadomość — bez tego odpowiedź z zakładki personalnej wychodziłaby z adresu
 * firmowego, bo serwer bierze wtedy pierwszą dostępną skrzynkę.
 */
@Serializable
data class EmailSendDto(
    val accountId: String? = null,
    val threadId: String? = null,
    val to: List<String> = emptyList(),
    val cc: List<String> = emptyList(),
    val bcc: List<String> = emptyList(),
    val subject: String = "(bez tematu)",
    val bodyText: String? = null,
    val dealId: String? = null,
)

/**
 * Wysyłka zakolejkowana bez zasięgu. Poza samym ciałem żądania niesie
 * `content://` załączników i lokalne identyfikatory wiersza w cache — po
 * udanej wysyłce trzeba je podmienić na to, co odpowiedział serwer.
 *
 * Załączników NIE kopiujemy do bazy: 20 MB zdjęć z montażu przepisane do
 * SQLite tylko po to, żeby za chwilę je wysłać, zjadałoby pamięć telefonu.
 * Cena tej decyzji: plik skasowany z telefonu przed powrotem zasięgu nie
 * poleci, a człowiek dostaje o tym powiadomienie.
 */
@Serializable
data class EmailQueuedSendDto(
    val send: EmailSendDto,
    val localMessageId: String,
    val localThreadId: String? = null,
    val attachments: List<EmailQueuedAttachmentDto> = emptyList(),
)

@Serializable
data class EmailQueuedAttachmentDto(
    val uri: String,
    val filename: String,
    val mimeType: String,
    val sizeBytes: Long = 0,
)
