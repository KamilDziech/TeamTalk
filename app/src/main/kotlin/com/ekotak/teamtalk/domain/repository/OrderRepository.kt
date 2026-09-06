package com.ekotak.teamtalk.domain.repository

import com.ekotak.teamtalk.domain.model.DealOffer
import com.ekotak.teamtalk.domain.model.DealOrder
import com.ekotak.teamtalk.domain.model.PurchaseLine
import com.ekotak.teamtalk.domain.model.StockReservation

/**
 * Zakładka „Zamówienie" karty deala — z cache Room i kolejką offline.
 *
 * Dlaczego offline, skoro reszta karty deala (poza Audytem) chodzi z sieci:
 * ptaszki „zamówione" / „odebrane" stawia się przy towarze — w hali, pod
 * wiatą, na budowie. Tam zasięg bywa gorszy niż przy biurku, a odhaczenie jest
 * jedynym śladem, że ktoś ten towar widział. Utrata takiego ptaszka to nie
 * „wpiszę jeszcze raz", tylko drugi objazd magazynu.
 *
 * Odczyt: najpierw sieć, przy jej braku ostatnia kopia. Zapis: najpierw sieć,
 * przy jej braku kolejka. Rozróżnienie brak-sieci (`IOException`) od odmowy
 * serwera (`HttpException`) jest tu istotne — 403 przy braku `order.manage` nie
 * stanie się prawdziwe przez ponowienie.
 */
interface OrderRepository {

    /** Komplet zakładki jednym wywołaniem; przy braku sieci z cache. */
    suspend fun getDealOrders(dealId: String): DealOrdersSnapshot

    /**
     * Zamówienie z wygranej oferty. Treść pozycji przepisuje SERWER z oferty,
     * więc zamówienie zakolejkowane bez zasięgu stoi na liście puste — z
     * podpisem, że pozycje dojdą po wysyłce. Zgadywanie ich na telefonie
     * dałoby magazynierowi listę do odhaczania, która po synchronizacji
     * mogłaby wyglądać inaczej.
     */
    suspend fun createOrder(dealId: String, offerId: String): OrderSaveResult

    /**
     * Ptaszek przy pozycji. Podajemy dokładnie jedno pole — API zostawia
     * pominięte nietknięte.
     */
    suspend fun setOrderItem(
        dealId: String,
        orderId: String,
        itemId: String,
        ordered: Boolean? = null,
        received: Boolean? = null,
    ): OrderSaveResult

    /** „Wydane" / „Zwolnij" / „Przywróć" przy linii rezerwacji materiału. */
    suspend fun setReservationStatus(
        dealId: String,
        reservationId: String,
        status: String,
    ): OrderSaveResult

    /**
     * Brak z rezerwacji na listę zakupową magazynu — ta sama droga, co „ZAMÓW"
     * w Produktach. Pozycja rusza listę zakupową, a nie stan magazynowy.
     */
    suspend fun orderMissing(
        dealId: String,
        reservationId: String,
        productId: String,
        quantity: Double,
        clientLabel: String,
    ): OrderSaveResult

    /** Opróżnienie kolejki — woła `OrderSyncWorker`, gdy wróci sieć. */
    suspend fun syncPendingMutations(): OrderSyncResult
}

/**
 * Materiał zakładki. Trzy bloki są niezależne, bo stoją na RÓŻNYCH
 * uprawnieniach board360: zamówienia wymagają `order.manage`, rezerwacje
 * `inventory.view`, oferty samego `crm.view`. Handlowiec bez magazynu ma
 * zobaczyć zamówienia, magazynier bez CRM — rezerwacje; wspólna flaga „nie
 * udało się" schowałaby jednemu i drugiemu połowę pracy.
 */
data class DealOrdersSnapshot(
    val orders: List<DealOrder> = emptyList(),
    val offers: List<DealOffer> = emptyList(),
    val reservations: List<StockReservation> = emptyList(),
    val purchases: List<PurchaseLine> = emptyList(),
    /** `false` = odczytu zamówień odmówiono (najczęściej brak `order.manage`). */
    val ordersAvailable: Boolean = true,
    /** `false` = odczytu magazynu odmówiono (brak `inventory.view`). */
    val materialsAvailable: Boolean = true,
    /** `true` = pokazujemy ostatnią kopię, bo sieci nie było. */
    val fromCache: Boolean = false,
)

/**
 * Co się stało z zapisem. Różnica idzie aż na ekran: „odhaczone" i „odhaczone
 * w telefonie, wyślemy w zasięgu" to dla magazyniera dwie różne informacje.
 */
enum class OrderSaveResult { SENT, QUEUED }

/** Wynik przebiegu kolejki: `RETRY` = sieć znowu zawiodła, wpisy zostają. */
enum class OrderSyncResult { DONE, RETRY }
