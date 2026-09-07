package com.ekotak.teamtalk.domain.model

import kotlinx.serialization.json.JsonElement

/**
 * Sekcja pliku deala — 1:1 z `DealDocumentCategory` panelu i z
 * `document-category.ts` API. Kolejność `entries` to kolejność sekcji na
 * ekranie, dokładnie jak tablica `CATEGORIES` w `DealFilesPanel`.
 *
 * `photo` odróżnia sekcje zdjęciowe (kafelki w siatce, pod wspólnym nagłówkiem
 * „Zdjęcia") od dokumentowych (wiersze z miniaturą). To jedyne, co odróżnia je
 * po stronie UI — API traktuje wszystkie sekcje tak samo.
 */
enum class DocumentCategory(val wire: String, val label: String, val photo: Boolean = false) {
    PROJEKT("projekt", "Projekt domu"),
    DOTACJA("dotacja", "Dokumenty dotacji"),
    PROTOKOL("protokol", "Protokoły"),
    AUDYT("audyt", "Audyt", photo = true),
    MONTAZ("montaz", "Zdjęcia z montażu", photo = true),
    UMOWA("umowa", "UMOWA"),
    INNE("inne", "Pozostałe");

    companion object {
        /** Nieznana sekcja (nowsza wersja panelu) ląduje w „Pozostałe", jak w web. */
        fun fromWire(raw: String?): DocumentCategory =
            entries.firstOrNull { it.wire == raw } ?: INNE
    }
}

/**
 * Plik deala (zakładka „Pliki"). Metadane — treść leży w MinIO po stronie API
 * i schodzi osobnym żądaniem (`GET /api/documents/:id`).
 *
 * [planData] to swobodny JSON z przygotowaniem rzutu (skala + obrysy
 * pomieszczeń) zrobionym na tym pliku; czyta go `PlanPrep`. Trzymamy go jako
 * surowy [JsonElement], bo telefon musi oddać nietknięte te pola, których nie
 * edytuje — ta sama zasada, co przy `UfhFloor.planJson`.
 *
 * [pending] i [localPath] opisują plik żyjący jeszcze tylko w telefonie: wgrany
 * bez zasięgu, czekający w kolejce. Taki wiersz ma id z prefiksem
 * `local-`, więc nie da się go pomylić z dokumentem, o którym wie serwer.
 */
data class DealDocument(
    val id: String,
    val dealId: String,
    val name: String,
    val size: Long,
    val contentType: String,
    val category: DocumentCategory,
    val planData: JsonElement? = null,
    val createdAt: String = "",
    /** Czeka w kolejce na wysyłkę (wgrany bez zasięgu). */
    val pending: Boolean = false,
    /** Ścieżka kopii w pamięci aplikacji — tylko dla plików z kolejki. */
    val localPath: String? = null,
) {
    /** Nazwa bez prefiksu slotu rzutu — to ją pokazujemy człowiekowi. */
    val displayName: String get() = stripSlot(name)

    /** Slot rzutu dopięty do nazwy (`[[parter]] rzut.jpg` → `parter`). */
    val slot: String? get() = slotOfName(name)

    val isImage: Boolean
        get() = contentType.startsWith("image/") || IMAGE_EXT.containsMatchIn(name)

    val isPdf: Boolean
        get() = contentType.contains("pdf", ignoreCase = true) || name.endsWith(".pdf", true)

    /** „312 KB" — ta sama reguła co `kb()` w panelu (bajty poniżej 1 KiB). */
    val sizeLabel: String
        get() = if (size < 1024) "$size B" else "${(size / 1024.0).toInt()} KB"

    private companion object {
        val IMAGE_EXT = Regex("\\.(jpe?g|png|heic|webp|gif|bmp|tiff?)$", RegexOption.IGNORE_CASE)
    }
}

/** Prefiks id dla plików czekających w kolejce — nigdy nie trafia na serwer. */
const val LOCAL_DOCUMENT_PREFIX = "local-"

/**
 * Czy `d` to auto-kopia JPG pojedynczej strony PDF-a („<baza> — str. N.jpg"),
 * której oryginał leży w tym samym zbiorze. Panel takich kopii nie pokazuje
 * w „Pozostałych plikach projektu" — strony bierze się wprost z paska miniatur
 * PDF-a. Nazwy wgrane ręcznie (bez siostrzanego PDF-a) zostają widoczne.
 */
fun isPdfPageCopy(document: DealDocument, all: List<DealDocument>): Boolean {
    if (!document.isImage) return false
    val match = PAGE_COPY_RE.find(stripSlot(document.name)) ?: return false
    val base = match.groupValues[1]
    return all.any { it.isPdf && stripSlot(it.name).removeSuffix(".pdf") == base }
}

private val PAGE_COPY_RE = Regex("^(.*) — str\\. \\d+\\.(?:jpe?g|png)$", RegexOption.IGNORE_CASE)

/** Czy plik nadaje się do wgrania — panel dopuszcza wyłącznie zdjęcia i PDF-y. */
fun isImageOrPdfUpload(name: String, contentType: String): Boolean =
    contentType.startsWith("image/") ||
        contentType.contains("pdf", ignoreCase = true) ||
        UPLOAD_EXT.containsMatchIn(name)

private val UPLOAD_EXT =
    Regex("\\.(jpe?g|png|heic|heif|webp|gif|bmp|tiff?|pdf)$", RegexOption.IGNORE_CASE)
