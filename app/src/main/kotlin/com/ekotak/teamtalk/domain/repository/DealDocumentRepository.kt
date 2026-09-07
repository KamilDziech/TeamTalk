package com.ekotak.teamtalk.domain.repository

import com.ekotak.teamtalk.domain.model.DealDocument
import com.ekotak.teamtalk.domain.model.DocumentCategory
import kotlinx.serialization.json.JsonObject

/** Pliki deala po odczycie: treść z cache plus stan łączności. */
data class DocumentsSnapshot(
    val documents: List<DealDocument>,
    /** Serwer był nieosiągalny — pokazujemy to, co telefon ma u siebie. */
    val offline: Boolean = false,
    /** Odmowa serwera (brak uprawnienia, 5xx) — inna sprawa niż brak zasięgu. */
    val error: String? = null,
)

/** Wpis z kolejki, którego serwer nie przyjął — człowiek musi się dowiedzieć. */
data class DocumentSyncRejection(val label: String, val reason: String?)

data class DocumentSyncResult(
    val sent: Int = 0,
    val rejected: List<DocumentSyncRejection> = emptyList(),
    /** Kolejka nie doszła do końca (nadal bez zasięgu) — worker ponowi. */
    val incomplete: Boolean = false,
)

/**
 * Zakładka „Pliki" karty deala.
 *
 * Wszystkie cztery zapisy (wgranie, przeniesienie do innej sekcji, usunięcie,
 * przygotowanie rzutu) są KOLEJKOWANE: telefon zapisuje decyzję u siebie, oddaje
 * sterowanie od razu i wysyła ją, gdy wróci zasięg. Zdjęcia z montażu robi się
 * w kotłowni, a nie przy biurku, więc „spróbuj później" byłoby tu bezużyteczne.
 */
interface DealDocumentRepository {

    /** Pliki deala — z serwera, gdy się da, a zawsze z cache i kolejką na wierzchu. */
    suspend fun getDocuments(dealId: String): DocumentsSnapshot

    /**
     * Wgranie pliku. Kopia treści ląduje w pamięci aplikacji, wiersz pojawia się
     * na karcie od razu (jako „czeka na wysyłkę"), a wysyłką zajmuje się kolejka.
     *
     * @param category `null` = „wykryj sekcję automatycznie". Do czasu wysyłki
     *   plik leży wtedy w „Pozostałe" — sekcję zna dopiero serwer.
     */
    suspend fun upload(
        dealId: String,
        name: String,
        contentType: String,
        category: DocumentCategory?,
        bytes: ByteArray,
    ): DealDocument?

    suspend fun setCategory(document: DealDocument, category: DocumentCategory)

    /** `planData = null` kasuje przygotowanie rzutu (wraca stan „nieprzygotowany"). */
    suspend fun setPlanData(document: DealDocument, planData: JsonObject?)

    suspend fun delete(document: DealDocument)

    /** Opróżnia kolejkę. Woła to worker po powrocie łączności. */
    suspend fun syncPendingMutations(): DocumentSyncResult
}
