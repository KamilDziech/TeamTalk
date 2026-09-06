package com.ekotak.teamtalk.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Sprzedaż: oferty i zamówienia deala (`api/src/modules/sales`). Kontrakt
 * ten sam, co czyta zakładka „Zamówienie" panelu (`SalesPanels.tsx`).
 */

/** Pozycja zamówienia — nazwa, ilość i dwa ptaszki magazynu. */
@Serializable
data class OrderItemDto(
    val id: String,
    val name: String,
    val quantity: Double = 0.0,
    val ordered: Boolean = false,
    val received: Boolean = false,
)

@Serializable
data class OrderDto(
    val id: String,
    val dealId: String,
    val supplierId: String? = null,
    /** open | ordered | received — serwer liczy go z pozycji. */
    val status: String = "open",
    val contractId: String? = null,
    val installationId: String? = null,
    val installationName: String? = null,
    /** offer = z wygranej oferty (ręcznie) | contract = z podpisanej umowy. */
    val source: String = "offer",
    val createdAt: String? = null,
    val items: List<OrderItemDto> = emptyList(),
)

/**
 * Oferta deala. Pozycji nie bierzemy: zamówienie zakłada się WSKAZUJĄC ofertę,
 * a jej treść przepisuje serwer — `ignoreUnknownKeys` przepuszcza resztę.
 */
@Serializable
data class OfferDto(
    val id: String,
    val number: String,
    /** draft | sent | won | lost. */
    val status: String = "draft",
    val netTotal: Double = 0.0,
    val grossTotal: Double = 0.0,
    val margin: Double = 0.0,
)

/** Ciało `POST /api/deals/{id}/orders` — zamówienie z wygranej oferty. */
@Serializable
data class OrderCreateRequest(val offerId: String)

/**
 * Ciało `PATCH /api/orders/{id}/items/{itemId}`. Wysyłamy WYŁĄCZNIE odhaczone
 * pole — API zostawia pominięte nietknięte, a `explicitNulls = false` naszego
 * `Json` wycina `null`-e z ciała. Wysłanie obu naraz nadpisywałoby ptaszek,
 * którego magazynier nie dotknął (choćby zmieniony w tym czasie w panelu).
 */
@Serializable
data class OrderItemPatchRequest(
    val ordered: Boolean? = null,
    val received: Boolean? = null,
)
