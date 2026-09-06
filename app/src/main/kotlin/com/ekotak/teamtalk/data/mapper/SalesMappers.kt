package com.ekotak.teamtalk.data.mapper

import com.ekotak.teamtalk.data.local.entity.DealOfferEntity
import com.ekotak.teamtalk.data.local.entity.DealOrderEntity
import com.ekotak.teamtalk.data.local.entity.PurchaseOrderEntity
import com.ekotak.teamtalk.data.remote.dto.OfferDto
import com.ekotak.teamtalk.data.remote.dto.OrderDto
import com.ekotak.teamtalk.data.remote.dto.OrderItemDto
import com.ekotak.teamtalk.domain.model.DealOffer
import com.ekotak.teamtalk.domain.model.DealOrder
import com.ekotak.teamtalk.domain.model.OfferStatus
import com.ekotak.teamtalk.domain.model.OrderItem
import com.ekotak.teamtalk.domain.model.OrderSource
import com.ekotak.teamtalk.domain.model.OrderStatus
import com.ekotak.teamtalk.domain.model.PurchaseLine
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Zamówienia, oferty i zakupy zakładki „Zamówienie": API ↔ cache ↔ domena. */

private val itemsSerializer = ListSerializer(OrderItemDto.serializer())

fun OrderDto.toEntity(json: Json, now: Long): DealOrderEntity = DealOrderEntity(
    id = id,
    dealId = dealId,
    status = status,
    contractId = contractId,
    installationId = installationId,
    installationName = installationName,
    source = source,
    // Zamówienie bez daty utworzenia (starsze API) i tak musi mieć czym się
    // sortować — bierzemy chwilę pobrania, więc stanie na końcu listy.
    createdAt = createdAt ?: isoNow(now),
    itemsJson = json.encodeToString(itemsSerializer, items),
    syncedAt = now,
)

fun DealOrderEntity.toDomain(json: Json): DealOrder = DealOrder(
    id = id,
    dealId = dealId,
    status = OrderStatus.fromWire(status),
    contractId = contractId,
    installationId = installationId,
    installationName = installationName,
    source = OrderSource.fromWire(source),
    createdAt = createdAt,
    items = decodeItems(json, itemsJson).map {
        OrderItem(
            id = it.id,
            name = it.name,
            quantity = it.quantity,
            ordered = it.ordered,
            received = it.received,
        )
    },
)

/**
 * Uszkodzony JSON pozycji nie może wywrócić zakładki — nagłówek zamówienia
 * niesie własną treść (instalacja, źródło, stan), a pozycje wrócą przy
 * najbliższym udanym odczycie.
 */
private fun decodeItems(json: Json, raw: String): List<OrderItemDto> =
    runCatching { json.decodeFromString(itemsSerializer, raw) }.getOrDefault(emptyList())

/** Pozycje z powrotem do JSON-a cache'u — po nałożeniu ptaszka z kolejki. */
fun encodeItems(json: Json, items: List<OrderItemDto>): String =
    json.encodeToString(itemsSerializer, items)

fun OfferDto.toEntity(dealId: String, now: Long): DealOfferEntity = DealOfferEntity(
    id = id,
    dealId = dealId,
    number = number,
    status = status,
    netTotal = netTotal,
    grossTotal = grossTotal,
    margin = margin,
    syncedAt = now,
)

fun DealOfferEntity.toDomain(): DealOffer = DealOffer(
    id = id,
    number = number,
    status = OfferStatus.fromWire(status),
    netTotal = netTotal,
    grossTotal = grossTotal,
    margin = margin,
)

fun PurchaseOrderEntity.toPurchaseLine(): PurchaseLine = PurchaseLine(
    id = id,
    productId = productId,
    reservationId = reservationId,
    quantity = quantity,
    receivedQty = receivedQty,
    status = status,
    distributor = distributor,
    expectedAt = expectedAt,
)

/**
 * Znacznik czasu w formacie, w jakim daty przychodzą z API — rekord zapisany
 * offline ma stanąć na liście tam, gdzie stanie po wysłaniu.
 */
fun isoNow(millis: Long = System.currentTimeMillis()): String =
    java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
        .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        .format(java.util.Date(millis))
