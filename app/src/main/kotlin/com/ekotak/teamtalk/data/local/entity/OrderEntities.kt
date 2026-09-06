package com.ekotak.teamtalk.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cache i kolejka zakładki „Zamówienie".
 *
 * Zamówienie odhacza się tam, gdzie towar fizycznie jest: w magazynie za halą
 * i na budowie. Zasięg bywa tam gorszy niż w biurze, a „zamówione / odebrane"
 * to jedyny ślad, że ktoś ten towar widział — dlatego zakładka ma pełny offline
 * z kolejką, jak Zadania i Serwis, a nie sam cache do odczytu.
 */

/**
 * Zamówienie deala w cache. Pozycje trzymamy jako surowy JSON listy
 * `OrderItemDto`: to kontrakt z panelem, a rozbicie na osobną tabelę tylko po
 * to, by złożyć ją z powrotem przy każdym odczycie, niczego by nie kupiło —
 * pozycji jest kilkanaście i zawsze czyta się je razem z nagłówkiem.
 */
@Entity(tableName = "deal_orders")
data class DealOrderEntity(
    /** Id serwerowe albo lokalne (`local:…`) do czasu wysłania zamówienia. */
    @PrimaryKey val id: String,
    val dealId: String,
    val status: String,
    val contractId: String?,
    val installationId: String?,
    val installationName: String?,
    val source: String,
    val createdAt: String,
    /** `List<OrderItemDto>` zserializowane wspólnym `Json` modułu. */
    val itemsJson: String,
    val syncedAt: Long,
)

/**
 * Oferta deala w cache — z wygranej zakłada się zamówienie. Trzymamy ją, bo bez
 * zasięgu selektor „Wygrana oferta…" byłby pusty i magazynier nie miałby czego
 * wskazać; sumy służą do rozróżnienia dwóch ofert tego samego deala.
 */
@Entity(tableName = "deal_offers")
data class DealOfferEntity(
    @PrimaryKey val id: String,
    val dealId: String,
    val number: String,
    val status: String,
    val netTotal: Double,
    val grossTotal: Double,
    val margin: Double,
    val syncedAt: Long,
)

/**
 * Zmiana zakładki czekająca na wysyłkę.
 *
 * Jedna tabela na cztery rodzaje zapisu ([KIND_ORDER_CREATE], [KIND_ORDER_ITEM],
 * [KIND_RESERVATION], [KIND_PURCHASE]) — wszystkie idą tą samą drogą i mają
 * wspólny porządek wysyłki, a rozbicie na cztery kolejki kazałoby pilnować
 * kolejności między nimi (ptaszek przy pozycji zamówienia założonego offline
 * musi pójść PO tym zamówieniu).
 *
 * Klucz (`targetId`, `kind`) sprawia, że kolejne odhaczenie tej samej pozycji
 * nadpisuje poprzednie: liczy się ostatnia decyzja magazyniera, a nie ścieżka,
 * którą do niej doszedł.
 */
@Entity(tableName = "order_mutations", primaryKeys = ["targetId", "kind"])
data class OrderMutationEntity(
    /**
     * Co zmieniamy: id zamówienia (`__create`), `orderId|itemId` (ptaszek),
     * id rezerwacji albo id linii, pod którą kupujemy brak.
     */
    val targetId: String,
    val kind: String,
    /** Gotowe ciało żądania. */
    val payload: String,
    val dealId: String,
    val createdAt: Long,
) {
    companion object {
        /** `POST /deals/{id}/orders` — zamówienie z wygranej oferty. */
        const val KIND_ORDER_CREATE = "order_create"

        /** `PATCH /orders/{id}/items/{itemId}` — ptaszek „zamówione"/„odebrane". */
        const val KIND_ORDER_ITEM = "order_item"

        /** `PATCH /inventory/reservations/{id}` — „Wydane" / „Zwolnij" / „Przywróć". */
        const val KIND_RESERVATION = "reservation"

        /** `POST /inventory/orders` — brak dokładany na listę zakupową magazynu. */
        const val KIND_PURCHASE = "purchase"

        /** Prefiks identyfikatora nadawanego lokalnie do czasu wysłania. */
        const val LOCAL_ID_PREFIX = "local:"

        /** Klucz ptaszka: jedno zamówienie ma wiele pozycji, każda swój wiersz. */
        fun itemTarget(orderId: String, itemId: String): String = "$orderId|$itemId"
    }
}
