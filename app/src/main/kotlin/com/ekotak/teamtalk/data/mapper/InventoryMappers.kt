package com.ekotak.teamtalk.data.mapper

import com.ekotak.teamtalk.data.local.entity.ProductEntity
import com.ekotak.teamtalk.data.local.entity.PurchaseOrderEntity
import com.ekotak.teamtalk.data.local.entity.ReservationEntity
import com.ekotak.teamtalk.data.remote.dto.DistributorPriceDto
import com.ekotak.teamtalk.data.remote.dto.ProductDto
import com.ekotak.teamtalk.data.remote.dto.PurchaseOrderItemDto
import com.ekotak.teamtalk.data.remote.dto.ReservationDto
import com.ekotak.teamtalk.domain.model.Product
import com.ekotak.teamtalk.domain.model.PurchasePoint
import com.ekotak.teamtalk.domain.model.StockPolicy
import com.ekotak.teamtalk.domain.model.StockReservation
import com.ekotak.teamtalk.domain.model.StockType
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Mapowanie magazynu: DTO ↔ encja cache ↔ model domenowy.
 *
 * Ceny per punkt zakupu jadą do bazy jako JSON listy DTO — to lista o zmiennej
 * długości, a osobna tabela dokładałaby złączenie bez żadnego zysku: karta i tak
 * czyta je zawsze w komplecie.
 */
private val inventoryJson = Json { ignoreUnknownKeys = true; explicitNulls = false }

private val purchasePointsSerializer = ListSerializer(DistributorPriceDto.serializer())

// ── Kartoteki ────────────────────────────────────────────────────────────────

fun ProductDto.toEntity(now: Long): ProductEntity = ProductEntity(
    id = id,
    name = name,
    code = code,
    installation = installation,
    stockType = stockType,
    dealId = dealId,
    stock = stock,
    minQty = min,
    targetQty = target,
    price = price,
    producer = producer,
    // Panel trzyma głównego dystrybutora lustrzanie w `distributor` i na
    // pierwszym miejscu listy; gdyby pole było puste, bierzemy pierwszy z listy.
    distributor = distributor ?: distributors.firstOrNull(),
    distributors = distributors,
    distributorPricesJson = inventoryJson.encodeToString(purchasePointsSerializer, distributorPrices),
    packaging = packaging,
    comparisonSize = comparisonSize,
    defaultChoice = defaultChoice,
    notes = notes,
    storageZone = storageZone,
    storageShelf = storageShelf,
    stockPolicy = stockPolicy,
    imageUrl = imageUrl,
    leadTimeDays = leadTimeDays,
    syncedAt = now,
)

/**
 * [reserved], [inTransit] i [toOrder] doklejamy z osobnych zapytań — kartoteka
 * ich nie zna, a bez nich karta pozycji nie policzy „wolnego".
 */
fun ProductEntity.toDomain(
    reserved: Double = 0.0,
    inTransit: Double = 0.0,
    toOrder: Double = 0.0,
): Product = Product(
    id = id,
    name = name,
    code = code,
    installation = installation,
    stockType = StockType.from(stockType),
    dealId = dealId,
    stock = stock,
    min = minQty,
    target = targetQty,
    price = price,
    producer = producer,
    distributor = distributor,
    distributors = distributors,
    distributorPrices = decodePurchasePoints(distributorPricesJson),
    packaging = packaging,
    comparisonSize = comparisonSize,
    defaultChoice = defaultChoice,
    notes = notes,
    storageZone = storageZone,
    storageShelf = storageShelf,
    stockPolicy = StockPolicy.from(stockPolicy),
    imageUrl = imageUrl,
    leadTimeDays = leadTimeDays,
    reserved = reserved,
    inTransit = inTransit,
    toOrder = toOrder,
)

/** Uszkodzony wpis w cache nie może wywrócić listy — wtedy po prostu brak cen. */
private fun decodePurchasePoints(raw: String): List<PurchasePoint> =
    runCatching {
        inventoryJson.decodeFromString(purchasePointsSerializer, raw)
            .map { PurchasePoint(name = it.name, price = it.price) }
    }.getOrDefault(emptyList())

// ── Rezerwacje ───────────────────────────────────────────────────────────────

fun ReservationDto.toEntity(now: Long): ReservationEntity = ReservationEntity(
    id = id,
    dealId = dealId,
    productId = productId,
    itemName = itemName,
    itemCode = itemCode,
    clientLabel = clientLabel,
    quantity = quantity,
    unit = unit,
    status = status,
    source = source,
    neededBy = neededBy,
    note = note,
    covered = covered,
    missing = missing,
    productName = productName,
    syncedAt = now,
)

fun ReservationEntity.toDomain(): StockReservation = StockReservation(
    id = id,
    dealId = dealId,
    productId = productId,
    itemName = itemName,
    itemCode = itemCode,
    clientLabel = clientLabel,
    quantity = quantity,
    unit = unit,
    status = status,
    source = source,
    neededBy = neededBy,
    note = note,
    covered = covered,
    missing = missing,
    productName = productName,
)

// ── Zapotrzebowanie zakupowe ─────────────────────────────────────────────────

fun PurchaseOrderItemDto.toEntity(now: Long): PurchaseOrderEntity = PurchaseOrderEntity(
    id = id,
    productId = productId,
    dealId = dealId,
    reservationId = reservationId,
    quantity = quantity,
    receivedQty = receivedQty,
    status = status,
    distributor = distributor,
    unitPrice = unitPrice,
    expectedAt = expectedAt,
    note = note,
    syncedAt = now,
)

/** Ile z pozycji jeszcze nie dojechało — dostawy bywają częściowe. */
fun PurchaseOrderEntity.outstanding(): Double = maxOf(0.0, quantity - receivedQty)
