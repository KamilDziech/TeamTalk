package com.ekotak.teamtalk.data.repository

import com.ekotak.teamtalk.data.local.dao.InventoryDao
import com.ekotak.teamtalk.data.mapper.outstanding
import com.ekotak.teamtalk.data.mapper.toDomain
import com.ekotak.teamtalk.data.mapper.toEntity
import com.ekotak.teamtalk.data.remote.api.TeamTalkApi
import com.ekotak.teamtalk.domain.repository.InventoryRepository
import com.ekotak.teamtalk.domain.repository.InventorySnapshot
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Moduł Magazyn — mobilny odpowiednik `web/src/app/app/inventory` (etap E1).
 *
 * Źródłem prawdy dla ekranu jest Room: lista otwiera się bez zasięgu, a sieć
 * tylko dolewa świeże dane. Kartoteka przychodzi w całości (`GET /api/products`
 * nie ma dziś ani filtrów, ani znacznika zmiany), więc podmieniamy ją w cache
 * hurtem — inaczej pozycja skasowana w panelu zostałaby na telefonie na zawsze.
 *
 * Trzy liczby przy pozycji — zarezerwowane, w drodze, do zamówienia — składamy
 * TUTAJ, z rezerwacji i zapotrzebowania. Kartoteka ich nie zna, a ekran nie ma
 * ich skąd policzyć bez kompletu obu list.
 */
@Singleton
class InventoryRepositoryImpl @Inject constructor(
    private val api: TeamTalkApi,
    private val dao: InventoryDao,
) : InventoryRepository {

    private val reservationsAvailable = MutableStateFlow(true)
    private val ordersAvailable = MutableStateFlow(true)
    private val syncedAt = MutableStateFlow<Long?>(null)

    override fun observe(): Flow<InventorySnapshot> = combine(
        dao.observeProducts(),
        dao.observeReservations(),
        dao.observeOrders(),
    ) { products, reservations, orders ->
        // Tylko AKTYWNE rezerwacje trzymają towar — wydane i zwolnione nie
        // liczą się do niczego (ta sama reguła co `reservation.ts` w panelu).
        val reservedByProduct = reservations
            .filter { it.status == "active" && it.productId != null }
            .groupBy { it.productId!! }
            .mapValues { (_, rows) -> rows.sumOf { it.quantity } }

        // `ordered` = towar u dystrybutora, jeszcze niedostarczony („w drodze");
        // `to_order` = koszyk, którego nikt jeszcze nie zamówił. Licznik nie może
        // obiecywać towaru, którego nie zamówiono, więc idą osobno.
        val inTransitByProduct = orders
            .filter { it.status == "ordered" }
            .groupBy { it.productId }
            .mapValues { (_, rows) -> rows.sumOf { it.outstanding() } }
        val toOrderByProduct = orders
            .filter { it.status == "to_order" }
            .groupBy { it.productId }
            .mapValues { (_, rows) -> rows.sumOf { it.outstanding() } }

        InventorySnapshot(
            products = products.map {
                it.toDomain(
                    reserved = reservedByProduct[it.id] ?: 0.0,
                    inTransit = inTransitByProduct[it.id] ?: 0.0,
                    toOrder = toOrderByProduct[it.id] ?: 0.0,
                )
            },
            reservations = reservations.map { it.toDomain() },
            reservationsAvailable = reservationsAvailable.value,
            ordersAvailable = ordersAvailable.value,
            syncedAt = syncedAt.value ?: products.maxOfOrNull { it.syncedAt },
        )
    }

    /**
     * Kartoteka jest warunkiem koniecznym — jej błąd leci wyżej i ekran pokazuje
     * komunikat (albo poprzedni cache). Rezerwacje i zapotrzebowanie są dodatkiem:
     * gdy odpadną, zapisujemy to w migawce i lecimy dalej z samą kartoteką.
     */
    override suspend fun refresh() = coroutineScope {
        val now = System.currentTimeMillis()

        val productsCall = async { api.getProducts() }
        val reservationsCall = async { runCatching { api.getInventoryReservations() } }
        val ordersCall = async { runCatching { api.getInventoryOrders() } }

        val products = productsCall.await()
        dao.replaceProducts(products.map { it.toEntity(now) })

        reservationsCall.await()
            .onSuccess { rows ->
                dao.replaceReservations(rows.map { it.toEntity(now) })
                reservationsAvailable.value = true
            }
            .onFailure { reservationsAvailable.value = false }

        ordersCall.await()
            .onSuccess { rows ->
                dao.replaceOrders(rows.map { it.toEntity(now) })
                ordersAvailable.value = true
            }
            .onFailure { ordersAvailable.value = false }

        syncedAt.value = now
    }
}
