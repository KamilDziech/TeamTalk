package com.ekotak.teamtalk.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Kartoteka magazynu (`GET /api/products`) — kontrakt board360
 * (`api/src/modules/inventory/domain/product.ts`).
 *
 * Bierzemy pola, którymi żyje telefon: co to jest, ile tego jest, gdzie leży
 * i u kogo się to kupuje. Rzeczy biurkowe z panelu (grupy porównawcze, systemy
 * rur, specyfikacja katalogowa, znaczniki archiwum) świadomie pomijamy —
 * `ignoreUnknownKeys` przepuszcza je bez błędu.
 *
 * Liczby są `Float` po stronie Prismy, bo magazyn liczy też rolki i metry —
 * stąd `Double`, nie `Int`.
 */
@Serializable
data class ProductDto(
    val id: String,
    val name: String,
    val code: String? = null,
    val category: String? = null,
    /** Słownik instalacji: heat_pump|pv|underfloor|plumbing|recuperation|ac|general(+podkategorie). */
    val installation: String? = null,
    /** `stock` = zapas magazynowy, `client` = pozycja zamawiana pod deala. */
    val stockType: String = "stock",
    val dealId: String? = null,
    val deliveryStatus: String? = null,
    val stock: Double = 0.0,
    val min: Double = 0.0,
    val target: Double = 0.0,
    val imageUrl: String? = null,
    val producer: String? = null,
    /** GŁÓWNY punkt zakupu = pierwszy z `distributors`. */
    val distributor: String? = null,
    val distributors: List<String> = emptyList(),
    val distributorPrices: List<DistributorPriceDto> = emptyList(),
    val packaging: String? = null,
    /** Obiekt magazynu ze słownika („Hala", „Kontener 3"). */
    val storageZone: String? = null,
    /** Półka w obrębie obiektu — wolny tekst („A35"). */
    val storageShelf: String? = null,
    val stockPolicy: String? = null,
    val notes: String? = null,
    /** Cena netto zakupu za jednostkę u głównego dystrybutora. */
    val price: Double? = null,
    val leadTimeDays: Int? = null,
    val updatedAt: String? = null,
)

/** Cena netto u konkretnego punktu zakupu; `price = null` = punkt jest, ceny nie znamy. */
@Serializable
data class DistributorPriceDto(
    val name: String,
    val price: Double? = null,
)

/**
 * Rezerwacja materiału pod klienta (`GET /api/inventory/reservations`) razem
 * z pokryciem policzonym przez API (`ReservationView`).
 *
 * Pokrycia NIE liczymy na telefonie: przydział idzie wg daty montażu przez
 * WSZYSTKIE deale naraz, więc jedna implementacja stoi po stronie serwera.
 */
@Serializable
data class ReservationDto(
    val id: String,
    val dealId: String,
    /** `null` = pozycja bez kartoteki w magazynie (luka do założenia). */
    val productId: String? = null,
    val itemName: String,
    val itemCode: String? = null,
    val clientLabel: String,
    val quantity: Double = 0.0,
    val unit: String = "szt",
    /** active | done | cancelled — tylko `active` trzyma towar. */
    val status: String = "active",
    val source: String = "manual",
    /** Data montażu — po niej ustawia się kolejka przydziału. */
    val neededBy: String? = null,
    val note: String? = null,
    val covered: Double = 0.0,
    val missing: Double = 0.0,
    val productName: String? = null,
)

/**
 * Pozycja zapotrzebowania zakupowego (`GET /api/inventory/orders?status=open`).
 *
 * Żyje osobno od stanu: `to_order` → `ordered` → `received`. Telefon czyta z tego
 * dwie liczby przy pozycji — „do zamówienia" (`to_order`) i „w drodze" (`ordered`).
 */
@Serializable
data class PurchaseOrderItemDto(
    val id: String,
    val productId: String,
    val dealId: String? = null,
    /**
     * Linia rezerwacji, której brak pokrywa ta pozycja; `null` = zakup luzem
     * albo propozycja scalona z kilku linii. Po tym karta deala paruje wiersz
     * rezerwacji z zakupem — a gdy pola brak, po `productId`.
     */
    val reservationId: String? = null,
    val quantity: Double = 0.0,
    /** Ile już przyjęto — dostawy częściowe. */
    val receivedQty: Double = 0.0,
    val status: String = "to_order",
    val distributor: String? = null,
    val unitPrice: Double? = null,
    /** Przewidywana data dostawy, ustawiana przy „Zamówiono". */
    val expectedAt: String? = null,
    val note: String? = null,
)

/**
 * Ciało `POST /api/inventory/orders` — brak rezerwacji dokładany na listę
 * zakupową magazynu (`orderItemCreateSchema`). Pozycja rusza listę zakupową,
 * a NIE stan magazynowy: ten zmienia się dopiero przy przyjęciu dostawy.
 */
@Serializable
data class PurchaseOrderCreateRequest(
    val productId: String,
    val quantity: Double,
    val note: String? = null,
)

/**
 * Ciało `PATCH /api/inventory/reservations/{id}` (`reservationUpdateSchema`).
 * Telefon zmienia wyłącznie `status` — „Wydane" / „Zwolnij" / „Przywróć";
 * ilość, termin i parowanie kartoteki zostają w panelu magazynu.
 */
@Serializable
data class ReservationPatchRequest(
    /** active | done | cancelled. */
    val status: String,
)
