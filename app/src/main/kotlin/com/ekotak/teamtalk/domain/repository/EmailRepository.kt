package com.ekotak.teamtalk.domain.repository

import com.ekotak.teamtalk.domain.model.EmailDealOption
import com.ekotak.teamtalk.domain.model.EmailDraft
import com.ekotak.teamtalk.domain.model.EmailFolder
import com.ekotak.teamtalk.domain.model.EmailSnapshot
import com.ekotak.teamtalk.domain.model.EmailThreadDetail
import com.ekotak.teamtalk.domain.model.EmailThreadPatch
import com.ekotak.teamtalk.domain.model.MailboxScope
import kotlinx.coroutines.flow.Flow

/**
 * Moduł Email — poczta w układzie Gmaila, dwie skrzynki na osobę.
 *
 * Źródłem prawdy dla ekranu jest Room: skrzynka otwiera się w kotłowni bez
 * zasięgu, a sieć tylko dolewa świeże dane. Zapis idzie wprost do API, a gdy
 * sieci nie ma — do kolejki i do cache, żeby zmiana była widoczna od razu.
 */
interface EmailRepository {

    /** Migawka wybranej skrzynki i widoku: zakładki, foldery, lista wątków. */
    fun observe(accountId: String?, scope: MailboxScope, folder: EmailFolder): Flow<EmailSnapshot>

    /** Wątek do czytania — z cache, uzupełniany z sieci, gdy jest zasięg. */
    fun observeThread(threadId: String): Flow<EmailThreadDetail?>

    /** Dociąga skrzynki, liczniki i listę wątków dla wskazanego widoku. */
    suspend fun refresh(accountId: String?, scope: MailboxScope, folder: EmailFolder)

    /** Dociąga treść wątku (i oznacza go na serwerze jako przeczytany). */
    suspend fun refreshThread(threadId: String)

    /** Wyszukiwanie w skrzynce — wyłącznie po sieci, bez zapisu do cache. */
    suspend fun search(
        accountId: String,
        scope: MailboxScope,
        folder: EmailFolder,
        query: String,
    ): List<com.ekotak.teamtalk.domain.model.EmailThread>

    /** Gwiazdka, przeczytane, folder, dowiązanie do deala, etykiety. */
    suspend fun patchThread(threadId: String, patch: EmailThreadPatch)

    /** Do kosza, a z kosza — trwale. */
    suspend fun deleteThread(threadId: String)

    /** Wysyłka: nowy wątek albo odpowiedź w istniejącym. */
    suspend fun send(draft: EmailDraft)

    /** Zapis wersji roboczej. */
    suspend fun saveDraft(draft: EmailDraft)

    /** Opcje pickera „Powiąż z dealem" (filtr po nazwisku klienta). */
    suspend fun dealOptions(query: String?): List<EmailDealOption>

    /** Opróżnia kolejkę; woła ją robotnik po powrocie zasięgu. */
    suspend fun syncPendingMutations(): EmailSyncResult
}

/** Wynik przebiegu kolejki. */
data class EmailSyncResult(
    val sent: Int = 0,
    val rejected: List<EmailSyncRejection> = emptyList(),
    /** Sieć znowu padła — reszta kolejki czeka na kolejne obudzenie. */
    val incomplete: Boolean = false,
)

/**
 * Zapis, którego serwer nie przyjął. Musi trafić do człowieka powiadomieniem:
 * widział na ekranie wysłaną wiadomość, więc jej cichy zanik byłby najgorszym
 * możliwym zachowaniem — poczta wygląda wtedy na dostarczoną, a nie jest.
 */
data class EmailSyncRejection(val label: String, val reason: String?)
