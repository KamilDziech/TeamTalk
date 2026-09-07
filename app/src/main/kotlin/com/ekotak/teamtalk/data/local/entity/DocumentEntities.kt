package com.ekotak.teamtalk.data.local.entity

import androidx.room.Entity

/**
 * Cache metadanych plików deala (zakładka „Pliki").
 *
 * Trzymamy tu też wiersze plików wgranych BEZ ZASIĘGU — mają id z prefiksem
 * `local-`, [pending] = 1 i [localPath] wskazujące kopię treści w pamięci
 * aplikacji. Dzięki temu zdjęcie z montażu widać na karcie od razu po zrobieniu,
 * a nie dopiero po powrocie łączności.
 *
 * [planDataJson] to surowy JSON przygotowania rzutu (skala + obrysy). Trzymamy
 * go w całości, bo telefon edytuje z niego tylko część, a resztę (podpisy
 * autorów, historia panelu) ma oddać nietkniętą.
 */
@Entity(tableName = "deal_documents", primaryKeys = ["id"])
data class DealDocumentEntity(
    val id: String,
    val dealId: String,
    val name: String,
    val size: Long,
    val contentType: String,
    val category: String,
    val planDataJson: String?,
    val createdAt: String,
    /** Plik czeka w kolejce na wysyłkę (wgrany bez zasięgu). */
    val pending: Boolean,
    /** Kopia treści w pamięci aplikacji — wyłącznie dla wierszy [pending]. */
    val localPath: String?,
    val syncedAt: Long,
)

/**
 * Zmiana plików deala czekająca na wysyłkę.
 *
 * Klucz (`targetId`, `kind`) sprawia, że powtórzona decyzja o tym samym pliku
 * nadpisuje poprzednią — w kolejce liczy się ostatnia wola człowieka, nie jej
 * historia. Treść pliku NIE leży w kolejce: wiersz `deal_documents` z prefiksem
 * `local-` niesie ścieżkę do kopii i to on jest źródłem nazwy oraz sekcji, więc
 * przełożenie zakolejkowanego zdjęcia do innej sekcji nie wymaga drugiego wpisu.
 */
@Entity(tableName = "document_mutations", primaryKeys = ["targetId", "kind"])
data class DocumentMutationEntity(
    val targetId: String,
    val kind: String,
    /** Deal, którego dotyczy zmiana — kolejka odświeża się per karta. */
    val dealId: String,
    val payload: String,
    val createdAt: Long,
) {
    companion object {
        /** Wgranie pliku z kolejki; `targetId` = lokalne id wiersza. */
        const val KIND_UPLOAD = "upload"

        /** Przeniesienie pliku do innej sekcji (`PATCH /documents/:id`). */
        const val KIND_CATEGORY = "category"

        /** Zapis przygotowania rzutu (`PATCH /documents/:id/plan-data`). */
        const val KIND_PLAN = "plan"

        /** Usunięcie pliku (`DELETE /documents/:id`). */
        const val KIND_DELETE = "delete"
    }
}
