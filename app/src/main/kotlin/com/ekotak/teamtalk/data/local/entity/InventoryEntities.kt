package com.ekotak.teamtalk.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cache offline magazynu. Ekran czyta WYŁĄCZNIE stąd, a sieć tylko dolewa
 * świeże dane — dzięki temu monter sprawdza stan i półkę w kotłowni bez zasięgu
 * tak samo jak na placu.
 *
 * `min` i `target` siedzą tu jako `minQty` / `targetQty`: „min" to nazwa funkcji
 * SQL i w zapytaniach tylko myli, a kartoteka i tak przechodzi przez mapper.
 */
@Entity(tableName = "inventory_products")
data class ProductEntity(
    @PrimaryKey val id: String,
    val name: String,
    val code: String?,
    val installation: String?,
    val stockType: String,
    val dealId: String?,
    val stock: Double,
    val minQty: Double,
    val targetQty: Double,
    val price: Double?,
    val producer: String?,
    val distributor: String?,
    val distributors: List<String>,
    /** `List<DistributorPriceDto>` zserializowane wspólnym `Json` modułu. */
    val distributorPricesJson: String,
    val packaging: String?,
    val notes: String?,
    val storageZone: String?,
    val storageShelf: String?,
    val stockPolicy: String?,
    val imageUrl: String?,
    val leadTimeDays: Int?,
    val syncedAt: Long,
)

/**
 * Rezerwacja pod klienta w cache. `covered` i `missing` zapisujemy tak, jak
 * policzył je serwer — telefon nie ma z czego ich odtworzyć, bo przydział idzie
 * przez rezerwacje WSZYSTKICH deali, a nie tylko tych widocznych na ekranie.
 */
@Entity(tableName = "inventory_reservations")
data class ReservationEntity(
    @PrimaryKey val id: String,
    val dealId: String,
    val productId: String?,
    val itemName: String,
    val itemCode: String?,
    val clientLabel: String,
    val quantity: Double,
    val unit: String,
    val status: String,
    val source: String,
    val neededBy: String?,
    val note: String?,
    val covered: Double,
    val missing: Double,
    val productName: String?,
    val syncedAt: Long,
)

/**
 * Pozycja zapotrzebowania zakupowego w cache — źródło liczb „w drodze"
 * (`ordered`) i „do zamówienia" (`to_order`) przy liczniku stanu.
 */
@Entity(tableName = "inventory_orders")
data class PurchaseOrderEntity(
    /** Id serwerowe albo lokalne (`local:…`) do czasu wysłania pozycji. */
    @PrimaryKey val id: String,
    val productId: String,
    val dealId: String?,
    /** Linia rezerwacji, której brak pokrywa ta pozycja; `null` = zakup luzem. */
    val reservationId: String?,
    val quantity: Double,
    val receivedQty: Double,
    val status: String,
    val distributor: String?,
    val unitPrice: Double?,
    val expectedAt: String?,
    val note: String?,
    val syncedAt: Long,
)
