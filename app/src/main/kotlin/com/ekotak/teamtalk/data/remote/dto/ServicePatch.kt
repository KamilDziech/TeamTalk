package com.ekotak.teamtalk.data.remote.dto

import com.ekotak.teamtalk.domain.model.ServiceJobPatch
import com.ekotak.teamtalk.domain.model.WarrantyCardPatch
import com.ekotak.teamtalk.domain.model.WarrantyInspectionUpsert
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Ciała żądań `PATCH`/`PUT` modułu Serwis budowane jako `JsonObject` — ten sam
 * powód co przy zadaniach i kartach klienta: API rozróżnia „pole nieobecne =
 * bez zmian" od „`null` = wyczyść wartość", a wspólny `Json` aplikacji ma
 * `explicitNulls = false`, więc nulle z data class w ogóle by nie dojechały.
 */

fun buildServiceJobPatch(patch: ServiceJobPatch): JsonObject = buildJsonObject {
    patch.clientId?.let { put("clientId", JsonPrimitive(it.value)) }
    patch.status?.let { put("status", JsonPrimitive(it.value.wire)) }
    patch.priority?.let { put("priority", JsonPrimitive(it.value.wire)) }
    patch.note?.let { putNullable("note", it.value) }
    patch.technicianId?.let { putNullable("technicianId", it.value) }
    patch.scheduledAt?.let { putNullable("scheduledAt", it.value) }
    patch.slaHours?.let { putNullable("slaHours", it.value) }
}

fun buildWarrantyCardPatch(patch: WarrantyCardPatch): JsonObject = buildJsonObject {
    patch.brand?.let { put("brand", JsonPrimitive(it.value)) }
    patch.status?.let { put("status", JsonPrimitive(it.value.wire)) }
    patch.location?.let { putNullable("location", it.value) }
    patch.commissionedAt?.let { putNullable("commissionedAt", it.value) }
    patch.outdoorModel?.let { putNullable("outdoorModel", it.value) }
    patch.outdoorSerial?.let { putNullable("outdoorSerial", it.value) }
    patch.indoorModel?.let { putNullable("indoorModel", it.value) }
    patch.indoorSerial?.let { putNullable("indoorSerial", it.value) }
    patch.note?.let { putNullable("note", it.value) }
}

/**
 * Upsert pozycji harmonogramu. `ordinal` jest kluczem, a pozostałe pola jadą
 * zawsze — także jako `null`, bo wyczyszczenie daty wykonania to normalna
 * korekta (przegląd wpisany omyłkowo wraca do „po terminie").
 */
fun buildWarrantyInspection(input: WarrantyInspectionUpsert): JsonObject = buildJsonObject {
    put("ordinal", JsonPrimitive(input.ordinal))
    putNullable("plannedAt", input.plannedAt)
    putNullable("doneAt", input.doneAt)
    putNullable("price", input.price)
    input.technicianId?.let { put("technicianId", JsonPrimitive(it)) }
    input.note?.let { put("note", JsonPrimitive(it)) }
}

private fun JsonObjectBuilder.putNullable(key: String, value: String?) {
    put(key, if (value == null) JsonNull else JsonPrimitive(value))
}

private fun JsonObjectBuilder.putNullable(key: String, value: Int?) {
    put(key, if (value == null) JsonNull else JsonPrimitive(value))
}
