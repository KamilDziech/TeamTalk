package com.ekotak.teamtalk.domain.model

/**
 * Zamówienia karty deala — odpowiednik `Order` z board360
 * (`api/src/modules/sales/domain/order.ts`) plus to, co zakładka „Zamówienie"
 * panelu dokłada obok: wygrane oferty (z nich zakłada się zamówienie)
 * i rezerwację materiału z zapotrzebowaniem zakupowym.
 */

/** Stan zamówienia. Wynika z pozycji, nie z osobnego pola — patrz [deriveOrderStatus]. */
enum class OrderStatus(val wire: String, val label: String) {
    OPEN("open", "otwarte"),
    ORDERED("ordered", "zamówione"),
    RECEIVED("received", "odebrane");

    companion object {
        fun fromWire(value: String?): OrderStatus =
            entries.firstOrNull { it.wire == value } ?: OPEN
    }
}

/** `offer` = założone ręcznie z wygranej oferty, `contract` = automat po podpisie umowy. */
enum class OrderSource(val wire: String) {
    OFFER("offer"),
    CONTRACT("contract");

    companion object {
        fun fromWire(value: String?): OrderSource =
            entries.firstOrNull { it.wire == value } ?: OFFER
    }
}

/**
 * Pozycja zamówienia z dwoma ptaszkami magazynu: „zamówione" (poszło do
 * dystrybutora) i „odebrane" (leży na półce). [pending] znaczy, że ptaszek
 * czeka w kolejce — magazynier odhaczył go bez zasięgu.
 */
data class OrderItem(
    val id: String,
    val name: String,
    val quantity: Double,
    val ordered: Boolean,
    val received: Boolean,
    val pending: Boolean = false,
)

data class DealOrder(
    val id: String,
    val dealId: String,
    val status: OrderStatus,
    /** Umowa, z której pochodzi treść zamówienia; `null` = założone ręcznie z oferty. */
    val contractId: String?,
    val installationId: String?,
    val installationName: String?,
    val source: OrderSource,
    val createdAt: String,
    val items: List<OrderItem>,
    /** Kiedy zmiana trafiła do kolejki; `null` = zamówienie zgodne z serwerem. */
    val pendingSince: Long? = null,
) {
    /**
     * Status liczony z pozycji, a nie brany z pola `status`. Bez zasięgu serwer
     * nie przeliczy nagłówka, a magazynier ma zobaczyć skutek swojego ptaszka
     * od razu — inaczej odhaczenie ostatniej pozycji zostawiłoby zamówienie
     * „otwarte" aż do powrotu sieci.
     */
    val derivedStatus: OrderStatus get() = deriveOrderStatus(items)

    /** Nazwa nagłówka: z umowy powstaje po jednym zamówieniu na INSTALACJĘ. */
    val title: String
        get() = installationName?.takeIf { it.isNotBlank() }
            ?.let { "Zamówienie — $it" }
            ?: "Zamówienie"

    val orderedCount: Int get() = items.count { it.ordered }
    val receivedCount: Int get() = items.count { it.received }
}

/**
 * Reguła FR-14, przeniesiona 1:1 z `order.ts`: wszystkie odebrane → `received`;
 * inaczej wszystkie zamówione → `ordered`; inaczej `open`. Puste → `open`.
 */
fun deriveOrderStatus(items: List<OrderItem>): OrderStatus = when {
    items.isEmpty() -> OrderStatus.OPEN
    items.all { it.received } -> OrderStatus.RECEIVED
    items.all { it.ordered } -> OrderStatus.ORDERED
    else -> OrderStatus.OPEN
}

enum class OfferStatus(val wire: String, val label: String) {
    DRAFT("draft", "szkic"),
    SENT("sent", "wysłana"),
    WON("won", "wygrana"),
    LOST("lost", "przegrana");

    companion object {
        fun fromWire(value: String?): OfferStatus =
            entries.firstOrNull { it.wire == value } ?: DRAFT
    }
}

/**
 * Oferta deala w kształcie, jakiego potrzebuje ta zakładka: numer, stan i sumy.
 * Pozycji nie ciągniemy — zamówienie zakłada się WSKAZUJĄC ofertę, a jej treść
 * przepisuje serwer.
 */
data class DealOffer(
    val id: String,
    val number: String,
    val status: OfferStatus,
    val netTotal: Double,
    val grossTotal: Double,
    val margin: Double,
) {
    val isWon: Boolean get() = status == OfferStatus.WON
}

/**
 * Pozycja zapotrzebowania zakupowego deala (`GET /api/inventory/orders`).
 * Odpowiada na pytanie, które w panelu zajmuje kolumnę „Zamówienie" przy
 * rezerwacji: czy ten brak ktoś już kupuje i kiedy to przyjedzie.
 */
data class PurchaseLine(
    val id: String,
    val productId: String,
    val reservationId: String?,
    val quantity: Double,
    val receivedQty: Double,
    /** proposed | to_order | ordered | received | cancelled */
    val status: String,
    val distributor: String?,
    val expectedAt: String?,
) {
    val outstanding: Double get() = maxOf(0.0, quantity - receivedQty)
}
