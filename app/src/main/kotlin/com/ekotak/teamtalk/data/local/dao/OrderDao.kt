package com.ekotak.teamtalk.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.ekotak.teamtalk.data.local.entity.DealOfferEntity
import com.ekotak.teamtalk.data.local.entity.DealOrderEntity
import com.ekotak.teamtalk.data.local.entity.OrderMutationEntity

/** Cache i kolejka zakładki „Zamówienie" karty deala. */
@Dao
interface OrderDao {

    // ── Zamówienia ────────────────────────────────────────────────────────────

    @Query("SELECT * FROM deal_orders WHERE dealId = :dealId ORDER BY createdAt ASC")
    suspend fun getOrders(dealId: String): List<DealOrderEntity>

    @Query("SELECT * FROM deal_orders WHERE id = :id")
    suspend fun getOrder(id: String): DealOrderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOrder(order: DealOrderEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOrders(orders: List<DealOrderEntity>)

    @Query("DELETE FROM deal_orders WHERE id = :id")
    suspend fun deleteOrder(id: String)

    @Query("DELETE FROM deal_orders WHERE dealId = :dealId AND id NOT LIKE 'local:%'")
    suspend fun deleteSyncedOrders(dealId: String)

    /**
     * Podmiana zamówień deala odpowiedzią serwera. Zamówienia założone offline
     * (`local:…`) ZOSTAJĄ — serwer o nich jeszcze nie wie, więc jego odpowiedź
     * nie jest w ich sprawie nowsza, a ich zniknięcie wyglądałoby, jakby
     * zamówienie nie powstało, i magazynier założyłby je drugi raz.
     */
    @Transaction
    suspend fun replaceOrders(dealId: String, orders: List<DealOrderEntity>) {
        deleteSyncedOrders(dealId)
        upsertOrders(orders)
    }

    // ── Oferty ────────────────────────────────────────────────────────────────

    @Query("SELECT * FROM deal_offers WHERE dealId = :dealId ORDER BY number ASC")
    suspend fun getOffers(dealId: String): List<DealOfferEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOffers(offers: List<DealOfferEntity>)

    @Query("DELETE FROM deal_offers WHERE dealId = :dealId")
    suspend fun deleteOffers(dealId: String)

    /** Oferty żyją w panelu — cache deala podmieniamy w całości, bez scalania. */
    @Transaction
    suspend fun replaceOffers(dealId: String, offers: List<DealOfferEntity>) {
        deleteOffers(dealId)
        upsertOffers(offers)
    }

    // ── Kolejka ───────────────────────────────────────────────────────────────

    /** Cała kolejka, od najstarszego zapisu — tak ją opróżnia worker. */
    @Query("SELECT * FROM order_mutations ORDER BY createdAt ASC")
    suspend fun getMutations(): List<OrderMutationEntity>

    /** Kolejka jednego deala — po niej zakładka rysuje znaczniki „czeka". */
    @Query("SELECT * FROM order_mutations WHERE dealId = :dealId")
    suspend fun getMutationsForDeal(dealId: String): List<OrderMutationEntity>

    /** Ciało zapisu czekającego pod tym kluczem; `null` = nic nie czeka. */
    @Query("SELECT payload FROM order_mutations WHERE targetId = :targetId AND kind = :kind")
    suspend fun getMutationPayload(targetId: String, kind: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMutation(mutation: OrderMutationEntity)

    /**
     * Kasujemy dokładnie ten wiersz (para `targetId` + `kind`), nie całą kolejkę
     * celu: kiedy worker wysyła ptaszek „zamówione", magazynier może w tej samej
     * chwili odhaczyć „odebrane" — sprzątamy więc tylko to, co poszło na serwer.
     */
    @Query("DELETE FROM order_mutations WHERE targetId = :targetId AND kind = :kind")
    suspend fun deleteMutation(targetId: String, kind: String)
}
