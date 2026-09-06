package com.ekotak.teamtalk.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.ekotak.teamtalk.data.local.entity.ProductEntity
import com.ekotak.teamtalk.data.local.entity.PurchaseOrderEntity
import com.ekotak.teamtalk.data.local.entity.ReservationEntity
import kotlinx.coroutines.flow.Flow

/** Cache magazynu: kartoteki, rezerwacje pod klientów i zapotrzebowanie zakupowe. */
@Dao
interface InventoryDao {

    @Query("SELECT * FROM inventory_products")
    fun observeProducts(): Flow<List<ProductEntity>>

    @Query("SELECT * FROM inventory_reservations")
    fun observeReservations(): Flow<List<ReservationEntity>>

    @Query("SELECT * FROM inventory_orders")
    fun observeOrders(): Flow<List<PurchaseOrderEntity>>

    /** Cała kartoteka jednym odczytem — wycena oferty liczy z niej ceny jednostkowe. */
    @Query("SELECT * FROM inventory_products")
    suspend fun getProducts(): List<ProductEntity>

    @Query("SELECT * FROM inventory_products WHERE id = :id")
    suspend fun getProduct(id: String): ProductEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProducts(items: List<ProductEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertReservations(items: List<ReservationEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOrders(items: List<PurchaseOrderEntity>)

    @Query("SELECT * FROM inventory_reservations WHERE dealId = :dealId")
    suspend fun getReservationsForDeal(dealId: String): List<ReservationEntity>

    @Query("SELECT * FROM inventory_orders WHERE dealId = :dealId")
    suspend fun getOrdersForDeal(dealId: String): List<PurchaseOrderEntity>

    @Query("DELETE FROM inventory_products")
    suspend fun clearProducts()

    @Query("DELETE FROM inventory_reservations")
    suspend fun clearReservations()

    /**
     * Pozycje zakupowe założone bez zasięgu (`local:…`) przeżywają czyszczenie:
     * serwer o nich jeszcze nie wie, więc jego lista nie jest w ich sprawie
     * nowsza — a ich zniknięcie wyglądałoby, jakby „ZAMÓW braki" nic nie zrobiło.
     */
    @Query("DELETE FROM inventory_orders WHERE id NOT LIKE 'local:%'")
    suspend fun clearOrders()

    @Query("DELETE FROM inventory_orders WHERE id = :id")
    suspend fun deletePurchaseOrder(id: String)

    @Query("DELETE FROM inventory_reservations WHERE dealId = :dealId")
    suspend fun clearReservationsForDeal(dealId: String)

    @Query("DELETE FROM inventory_orders WHERE dealId = :dealId AND id NOT LIKE 'local:%'")
    suspend fun clearOrdersForDeal(dealId: String)

    /**
     * Podmiana kompletu kartotek. Kasujemy i wstawiamy w jednej transakcji, bo
     * `GET /api/products` zwraca CAŁĄ kartotekę — pozycja skasowana w panelu
     * (miękko, do archiwum) musi zniknąć też z telefonu, a samo `upsert`
     * zostawiłoby ją w cache na zawsze.
     */
    @Transaction
    suspend fun replaceProducts(items: List<ProductEntity>) {
        clearProducts()
        upsertProducts(items)
    }

    @Transaction
    suspend fun replaceReservations(items: List<ReservationEntity>) {
        clearReservations()
        upsertReservations(items)
    }

    @Transaction
    suspend fun replaceOrders(items: List<PurchaseOrderEntity>) {
        clearOrders()
        upsertOrders(items)
    }

    /**
     * Rezerwacje i zakupy JEDNEGO deala — tak odświeża je zakładka „Zamówienie".
     * Zakres wymiany jest wąski celowo: karta deala pyta o `status=all` (potrzebuje
     * też historii), a ekran Magazynu o same aktywne, więc szeroka podmiana
     * z karty kasowałaby połowę tego, co Magazyn właśnie pobrał.
     */
    @Transaction
    suspend fun replaceDealMaterials(
        dealId: String,
        reservations: List<ReservationEntity>,
        orders: List<PurchaseOrderEntity>,
    ) {
        clearReservationsForDeal(dealId)
        upsertReservations(reservations)
        clearOrdersForDeal(dealId)
        upsertOrders(orders)
    }
}
