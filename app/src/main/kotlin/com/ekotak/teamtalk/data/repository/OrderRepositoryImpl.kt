package com.ekotak.teamtalk.data.repository

import com.ekotak.teamtalk.data.local.dao.InventoryDao
import com.ekotak.teamtalk.data.local.dao.OrderDao
import com.ekotak.teamtalk.data.local.entity.DealOrderEntity
import com.ekotak.teamtalk.data.local.entity.OrderMutationEntity
import com.ekotak.teamtalk.data.local.entity.OrderMutationEntity.Companion.KIND_ORDER_CREATE
import com.ekotak.teamtalk.data.local.entity.OrderMutationEntity.Companion.KIND_ORDER_ITEM
import com.ekotak.teamtalk.data.local.entity.OrderMutationEntity.Companion.KIND_PURCHASE
import com.ekotak.teamtalk.data.local.entity.OrderMutationEntity.Companion.KIND_RESERVATION
import com.ekotak.teamtalk.data.local.entity.OrderMutationEntity.Companion.LOCAL_ID_PREFIX
import com.ekotak.teamtalk.data.local.entity.OrderMutationEntity.Companion.itemTarget
import com.ekotak.teamtalk.data.local.entity.PurchaseOrderEntity
import com.ekotak.teamtalk.data.local.preferences.SessionPreferences
import com.ekotak.teamtalk.data.mapper.isoNow
import com.ekotak.teamtalk.data.mapper.toDomain
import com.ekotak.teamtalk.data.mapper.toEntity
import com.ekotak.teamtalk.data.mapper.toPurchaseLine
import com.ekotak.teamtalk.data.remote.api.TeamTalkApi
import com.ekotak.teamtalk.data.remote.dto.OrderCreateRequest
import com.ekotak.teamtalk.data.remote.dto.OrderItemPatchRequest
import com.ekotak.teamtalk.data.remote.dto.PurchaseOrderCreateRequest
import com.ekotak.teamtalk.data.remote.dto.ReservationPatchRequest
import com.ekotak.teamtalk.data.sync.OrderSyncScheduler
import com.ekotak.teamtalk.domain.model.DealOrder
import com.ekotak.teamtalk.domain.repository.DealOrdersSnapshot
import com.ekotak.teamtalk.domain.repository.OrderRepository
import com.ekotak.teamtalk.domain.repository.OrderSaveResult
import com.ekotak.teamtalk.domain.repository.OrderSyncResult
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import retrofit2.HttpException
import java.io.IOException
import java.util.UUID
import javax.inject.Inject

/**
 * Zakładka „Zamówienie": zamówienia deala, jego oferty i rezerwacja materiału
 * z zapotrzebowaniem zakupowym — cztery odczyty, każdy z własnym prawem do
 * porażki, i jedna wspólna kolejka zapisów.
 *
 * Zmian z kolejki NIE wpisujemy w cache jako fakt. Cache trzyma to, co powiedział
 * serwer, a niewysłane decyzje nakładamy na niego przy odczycie ([overlay]).
 * Dzięki temu odświeżenie Magazynu — które podmienia rezerwacje hurtem — nie
 * kasuje ptaszka postawionego przed chwilą w kotłowni.
 */
class OrderRepositoryImpl @Inject constructor(
    private val api: TeamTalkApi,
    private val dao: OrderDao,
    private val inventoryDao: InventoryDao,
    private val json: Json,
    private val syncScheduler: OrderSyncScheduler,
    private val sessionPreferences: SessionPreferences,
) : OrderRepository {

    override suspend fun getDealOrders(dealId: String): DealOrdersSnapshot {
        val now = System.currentTimeMillis()
        var offline = false
        var ordersAvailable = true
        var materialsAvailable = true

        coroutineScope {
            val ordersCall = async { runCatching { api.getDealOrders(dealId) } }
            val offersCall = async { runCatching { api.getDealOffers(dealId) } }
            // Karta deala pyta o `status=all`: potrzebuje też historii („wydane",
            // „zwolnione") i pozycji już przyjętych — inaczej wiersz zamknięty
            // wczoraj zniknąłby bez śladu, a brak wyglądałby na niekupiony.
            val reservationsCall = async {
                runCatching { api.getInventoryReservations(status = "all", dealId = dealId) }
            }
            val purchasesCall = async {
                runCatching { api.getInventoryOrders(status = "all", dealId = dealId) }
            }

            ordersCall.await()
                .onSuccess { rows -> dao.replaceOrders(dealId, rows.map { it.toEntity(json, now) }) }
                .onFailure { e ->
                    if (e is IOException) offline = true else ordersAvailable = false
                }
            offersCall.await()
                .onSuccess { rows -> dao.replaceOffers(dealId, rows.map { it.toEntity(dealId, now) }) }
                .onFailure { e -> if (e is IOException) offline = true }

            val reservations = reservationsCall.await()
            val purchases = purchasesCall.await()
            if (reservations.isSuccess && purchases.isSuccess) {
                inventoryDao.replaceDealMaterials(
                    dealId = dealId,
                    reservations = reservations.getOrThrow().map { it.toEntity(now) },
                    orders = purchases.getOrThrow().map { it.toEntity(now) },
                )
            } else {
                // Oba odczyty stoją na tym samym `inventory.view`, więc odpadają
                // razem — i razem podmieniamy cache. Zapisanie połowy zostawiłoby
                // rezerwacje bez zakupów, czyli braki wyglądające na niekupione.
                val error = reservations.exceptionOrNull() ?: purchases.exceptionOrNull()
                if (error is IOException) offline = true else materialsAvailable = false
            }
        }

        // Czytamy z cache, nie z odpowiedzi: w bazie leżą też zamówienia i zakupy
        // założone bez zasięgu, o których serwer jeszcze nie wie.
        val queue = dao.getMutationsForDeal(dealId)
        return DealOrdersSnapshot(
            orders = dao.getOrders(dealId).map { it.toDomain(json) }.map { overlay(it, queue) },
            offers = dao.getOffers(dealId).map { it.toDomain() },
            reservations = inventoryDao.getReservationsForDeal(dealId).map { entity ->
                val patch = queue.firstOrNull {
                    it.kind == KIND_RESERVATION && it.targetId == entity.id
                }
                entity.toDomain().copy(
                    status = patch?.let { statusOf(it.payload) } ?: entity.status,
                    pending = patch != null,
                )
            },
            purchases = inventoryDao.getOrdersForDeal(dealId).map { it.toPurchaseLine() },
            ordersAvailable = ordersAvailable,
            materialsAvailable = materialsAvailable,
            fromCache = offline,
        )
    }

    /**
     * Ptaszki czekające w kolejce nałożone na zamówienie z cache. Bez tego
     * odhaczona pozycja wracałaby na ekran odznaczona przy pierwszym wejściu
     * w zakładkę — i magazynier odhaczyłby ją drugi raz.
     */
    private fun overlay(order: DealOrder, queue: List<OrderMutationEntity>): DealOrder {
        val mine = queue.filter {
            (it.kind == KIND_ORDER_CREATE && it.targetId == order.id) ||
                (it.kind == KIND_ORDER_ITEM && it.targetId.startsWith("${order.id}|"))
        }
        if (mine.isEmpty()) return order

        val patches = mine.filter { it.kind == KIND_ORDER_ITEM }.associateBy { it.targetId }
        val items = order.items.map { item ->
            val body = patches[itemTarget(order.id, item.id)]?.let { parse(it.payload) }
                ?: return@map item
            item.copy(
                ordered = body.bool("ordered") ?: item.ordered,
                received = body.bool("received") ?: item.received,
                pending = true,
            )
        }
        return order.copy(items = items, pendingSince = mine.minOf { it.createdAt })
    }

    // ── Zapisy ────────────────────────────────────────────────────────────────

    override suspend fun createOrder(dealId: String, offerId: String): OrderSaveResult = try {
        val created = api.createDealOrder(dealId, OrderCreateRequest(offerId))
        dao.upsertOrder(created.toEntity(json, System.currentTimeMillis()))
        OrderSaveResult.SENT
    } catch (_: IOException) {
        val localId = LOCAL_ID_PREFIX + UUID.randomUUID()
        val now = System.currentTimeMillis()
        // Zamówienie od razu w cache — bez tego magazynier po kliknięciu
        // zobaczyłby listę bez zmian i założyłby je drugi raz. Pozycji nie
        // zgadujemy: przepisuje je serwer z oferty.
        dao.upsertOrder(
            DealOrderEntity(
                id = localId,
                dealId = dealId,
                status = "open",
                contractId = null,
                installationId = null,
                installationName = null,
                source = "offer",
                createdAt = isoNow(now),
                itemsJson = "[]",
                syncedAt = now,
            ),
        )
        enqueue(
            targetId = localId,
            kind = KIND_ORDER_CREATE,
            payload = buildJsonObject { put("offerId", JsonPrimitive(offerId)) },
            dealId = dealId,
            now = now,
        )
        OrderSaveResult.QUEUED
    }

    override suspend fun setOrderItem(
        dealId: String,
        orderId: String,
        itemId: String,
        ordered: Boolean?,
        received: Boolean?,
    ): OrderSaveResult {
        val body = buildJsonObject {
            ordered?.let { put("ordered", JsonPrimitive(it)) }
            received?.let { put("received", JsonPrimitive(it)) }
        }
        return try {
            val updated = api.updateOrderItem(
                orderId = orderId,
                itemId = itemId,
                request = OrderItemPatchRequest(ordered = ordered, received = received),
            )
            dao.upsertOrder(updated.toEntity(json, System.currentTimeMillis()))
            dao.deleteMutation(itemTarget(orderId, itemId), KIND_ORDER_ITEM)
            OrderSaveResult.SENT
        } catch (_: IOException) {
            // Ptaszek czeka w kolejce, a zakładka pokazuje go z kolejki
            // (`overlay`) — cache zostaje przy tym, co wie serwer.
            val queued = mergeItemPatch(itemTarget(orderId, itemId), body)
            enqueue(
                targetId = itemTarget(orderId, itemId),
                kind = KIND_ORDER_ITEM,
                payload = queued,
                dealId = dealId,
                now = System.currentTimeMillis(),
            )
            OrderSaveResult.QUEUED
        }
    }

    /**
     * Drugi ptaszek przy tej samej pozycji dokłada się do czekającego, zamiast
     * go zastąpić: „zamówione" i „odebrane" to dwa niezależne pola, a nadpisanie
     * ciała zgubiłoby to postawione minutę wcześniej.
     */
    private suspend fun mergeItemPatch(target: String, patch: JsonObject): JsonObject {
        val waiting = dao.getMutationPayload(target, KIND_ORDER_ITEM) ?: return patch
        return buildJsonObject {
            parse(waiting).forEach { (key, value) -> put(key, value) }
            patch.forEach { (key, value) -> put(key, value) }
        }
    }

    override suspend fun setReservationStatus(
        dealId: String,
        reservationId: String,
        status: String,
    ): OrderSaveResult = try {
        val updated = api.updateReservation(reservationId, ReservationPatchRequest(status))
        inventoryDao.upsertReservations(listOf(updated.toEntity(System.currentTimeMillis())))
        dao.deleteMutation(reservationId, KIND_RESERVATION)
        OrderSaveResult.SENT
    } catch (_: IOException) {
        enqueue(
            targetId = reservationId,
            kind = KIND_RESERVATION,
            payload = buildJsonObject { put("status", JsonPrimitive(status)) },
            dealId = dealId,
            now = System.currentTimeMillis(),
        )
        OrderSaveResult.QUEUED
    }

    override suspend fun orderMissing(
        dealId: String,
        reservationId: String,
        productId: String,
        quantity: Double,
        clientLabel: String,
    ): OrderSaveResult {
        val note = "Pod klienta: $clientLabel"
        return try {
            val created = api.createPurchaseOrderItem(
                PurchaseOrderCreateRequest(productId = productId, quantity = quantity, note = note),
            )
            inventoryDao.upsertOrders(listOf(created.toEntity(System.currentTimeMillis())))
            OrderSaveResult.SENT
        } catch (_: IOException) {
            val now = System.currentTimeMillis()
            // Pozycja od razu w cache pod lokalnym id — wiersz rezerwacji ma od
            // razu pokazać „na liście zakupowej", inaczej ktoś kupi to drugi raz.
            inventoryDao.upsertOrders(
                listOf(
                    PurchaseOrderEntity(
                        id = LOCAL_ID_PREFIX + UUID.randomUUID(),
                        productId = productId,
                        dealId = dealId,
                        reservationId = reservationId,
                        quantity = quantity,
                        receivedQty = 0.0,
                        status = "to_order",
                        distributor = null,
                        unitPrice = null,
                        expectedAt = null,
                        note = note,
                        syncedAt = now,
                    ),
                ),
            )
            enqueue(
                targetId = reservationId,
                kind = KIND_PURCHASE,
                payload = buildJsonObject {
                    put("productId", JsonPrimitive(productId))
                    put("quantity", JsonPrimitive(quantity))
                    put("note", JsonPrimitive(note))
                },
                dealId = dealId,
                now = now,
            )
            OrderSaveResult.QUEUED
        }
    }

    private suspend fun enqueue(
        targetId: String,
        kind: String,
        payload: JsonObject,
        dealId: String,
        now: Long,
    ) {
        dao.upsertMutation(
            OrderMutationEntity(
                targetId = targetId,
                kind = kind,
                payload = payload.toString(),
                dealId = dealId,
                createdAt = now,
            ),
        )
        // Warunek sieci zdejmuje z nas odpytywanie — system sam obudzi
        // robotnika, gdy telefon wróci w zasięg.
        syncScheduler.scheduleSync()
    }

    // ── Kolejka ───────────────────────────────────────────────────────────────

    /**
     * Opróżnianie kolejki, od najstarszego zapisu — a to ważne właśnie tutaj:
     * zamówienie założone offline musi pójść PRZED zakupami braków, które przy
     * nim powstały. Odmowa serwera kończy wpis (ponowienie nic nie zmieni), ale
     * mówimy o niej człowiekowi: to jego praca przy towarze przepadła.
     */
    override suspend fun syncPendingMutations(): OrderSyncResult {
        val queue = dao.getMutations()
        if (queue.isEmpty()) return OrderSyncResult.DONE

        for (row in queue) {
            val body = runCatching { json.parseToJsonElement(row.payload) as? JsonObject }.getOrNull()
            if (body == null) {
                // Nieczytelne ciało — ponowienie nic nie da, a wpis blokowałby
                // kolejkę w nieskończoność.
                dao.deleteMutation(row.targetId, row.kind)
                continue
            }

            try {
                when (row.kind) {
                    KIND_ORDER_CREATE -> {
                        val offerId = body.text("offerId") ?: throw IllegalStateException("offerId")
                        val created = api.createDealOrder(row.dealId, OrderCreateRequest(offerId))
                        dao.upsertOrder(created.toEntity(json, System.currentTimeMillis()))
                        // Zamówienie dostało id od serwera — lokalna atrapa
                        // znika, inaczej lista pokazałaby je dwa razy.
                        dao.deleteOrder(row.targetId)
                    }

                    KIND_ORDER_ITEM -> {
                        val (orderId, itemId) = row.targetId.split("|", limit = 2)
                            .takeIf { it.size == 2 }
                            ?: throw IllegalStateException(row.targetId)
                        val updated = api.updateOrderItem(
                            orderId = orderId,
                            itemId = itemId,
                            request = OrderItemPatchRequest(
                                ordered = body.bool("ordered"),
                                received = body.bool("received"),
                            ),
                        )
                        dao.upsertOrder(updated.toEntity(json, System.currentTimeMillis()))
                    }

                    KIND_RESERVATION -> {
                        val status = body.text("status") ?: throw IllegalStateException("status")
                        val updated = api.updateReservation(
                            row.targetId,
                            ReservationPatchRequest(status),
                        )
                        inventoryDao.upsertReservations(
                            listOf(updated.toEntity(System.currentTimeMillis())),
                        )
                    }

                    KIND_PURCHASE -> {
                        val created = api.createPurchaseOrderItem(
                            PurchaseOrderCreateRequest(
                                productId = body.text("productId")
                                    ?: throw IllegalStateException("productId"),
                                quantity = body.number("quantity") ?: 0.0,
                                note = body.text("note"),
                            ),
                        )
                        dropLocalPurchase(row.dealId, row.targetId)
                        inventoryDao.upsertOrders(
                            listOf(created.toEntity(System.currentTimeMillis())),
                        )
                    }

                    else -> Unit
                }
                // Kasujemy dokładnie ten wiersz, nie całą kolejkę celu:
                // w trakcie wysyłki magazynier mógł odhaczyć coś jeszcze.
                dao.deleteMutation(row.targetId, row.kind)
            } catch (_: IOException) {
                // Sieć znowu padła — reszta kolejki poczeka na następny przebieg.
                return OrderSyncResult.RETRY
            } catch (e: Exception) {
                dao.deleteMutation(row.targetId, row.kind)
                if (row.kind == KIND_ORDER_CREATE) dao.deleteOrder(row.targetId)
                if (row.kind == KIND_PURCHASE) dropLocalPurchase(row.dealId, row.targetId)
                sessionPreferences.saveSyncProblem(discardMessage(row.kind, e))
            }
        }
        return OrderSyncResult.DONE
    }

    /** Atrapa pozycji zakupowej sprzed wysyłki — po niej zostaje wpis serwera. */
    private suspend fun dropLocalPurchase(dealId: String, reservationId: String) {
        inventoryDao.getOrdersForDeal(dealId)
            .filter { it.id.startsWith(LOCAL_ID_PREFIX) && it.reservationId == reservationId }
            .forEach { inventoryDao.deletePurchaseOrder(it.id) }
    }

    private fun discardMessage(kind: String, e: Exception): String {
        val what = when (kind) {
            KIND_ORDER_CREATE -> "Zamówienie nie powstało"
            KIND_ORDER_ITEM -> "Odhaczenie pozycji zamówienia przepadło"
            KIND_RESERVATION -> "Zmiana rezerwacji materiału przepadła"
            else -> "Zamówienie braku w magazynie przepadło"
        }
        val code = (e as? HttpException)?.code()
        return when (code) {
            403 -> "$what — brak uprawnień."
            404 -> "$what — rekord zniknął z panelu."
            409, 422 -> "$what — serwer odrzucił zmianę. Sprawdź kartę w panelu."
            null -> "$what — nie udało się wysłać."
            else -> "$what — serwer go odrzucił (kod $code)."
        }
    }

    // ── Drobiazgi na JSON-ie kolejki ──────────────────────────────────────────

    private fun parse(raw: String): JsonObject =
        runCatching { json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: JsonObject(emptyMap())

    private fun statusOf(payload: String): String? = parse(payload).text("status")

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

    private fun JsonObject.bool(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.booleanOrNull

    private fun JsonObject.number(key: String): Double? =
        (this[key] as? JsonPrimitive)?.doubleOrNull
}
