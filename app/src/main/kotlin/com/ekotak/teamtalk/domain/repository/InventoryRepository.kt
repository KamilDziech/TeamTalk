package com.ekotak.teamtalk.domain.repository

import com.ekotak.teamtalk.domain.model.Product
import com.ekotak.teamtalk.domain.model.StockReservation
import kotlinx.coroutines.flow.Flow

/**
 * Migawka magazynu — komplet, z którego ekran składa obie listy (zapas
 * magazynowy i pozycje pod klienta).
 *
 * [reservationsAvailable] i [ordersAvailable] mówią, czy poboczne zapytania
 * odpowiedziały. Kartoteka to warunek konieczny, ale rezerwacje i zapotrzebowanie
 * mogą odpaść na uprawnieniach albo starszym serwerze — wtedy lista dalej działa,
 * tylko bez znacznika 🔒 i bez „w drodze". Milczenie byłoby gorsze: magazynier
 * wziąłby cudzy towar z półki, bo nie zobaczyłby rezerwacji.
 */
data class InventorySnapshot(
    val products: List<Product> = emptyList(),
    val reservations: List<StockReservation> = emptyList(),
    val reservationsAvailable: Boolean = true,
    val ordersAvailable: Boolean = true,
    /** Kiedy dane przyszły z sieci — podpis „dane sprzed …" przy pracy bez zasięgu. */
    val syncedAt: Long? = null,
)

/** Odczyt magazynu (etap E1). Zapis — ruchy, spis, dostawy — dochodzi w E2–E4. */
interface InventoryRepository {

    /** Ostatnia migawka; emituje po każdym odświeżeniu cache. */
    fun observe(): Flow<InventorySnapshot>

    /** Pobranie kompletu z API. Rzuca, gdy nie da się wczytać kartoteki. */
    suspend fun refresh()
}
