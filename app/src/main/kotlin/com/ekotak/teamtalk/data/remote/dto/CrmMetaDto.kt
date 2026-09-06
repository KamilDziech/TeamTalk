package com.ekotak.teamtalk.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Węzeł katalogu technologii (`GET /api/categories`). Kategorie główne to te
 * z `parentId = null` — ich nazwy trafiają na badge instalacji w kartotece.
 */
@Serializable
data class CategoryDto(
    val id: String,
    val parentId: String? = null,
    val name: String = "",
    val position: Int = 0,
    /**
     * Szablon ustrukturyzowanego formularza audytu tej technologii
     * (`Category.auditForm`). Zakładka „Audyt" karty deala DZIEDZICZY go
     * z najbliższego przodka, który go ma — dzięki temu wybór marki pod
     * technologią pyta o to samo, o co pyta technologia.
     */
    val auditForm: JsonObject? = null,
)

/**
 * Powiązanie kontaktu towarzyszącego z dealem (`GET /api/deals/contacts`).
 * Główny kontakt deala siedzi w `Deal.clientId` — tu są wyłącznie dodatkowi.
 */
@Serializable
data class DealContactLinkDto(
    val dealId: String,
    val clientId: String,
)

/**
 * Ciało operacji na kontaktach karty deala: dopięcie kontaktu towarzyszącego
 * (`POST /api/deals/:id/contacts`) i ustawienie głównego
 * (`PATCH /api/deals/:id/contacts/primary`). Oba przyjmują to samo pole.
 */
@Serializable
data class DealContactRequest(val clientId: String)
