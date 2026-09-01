package com.ekotak.teamtalk.data.remote.dto

import com.ekotak.teamtalk.domain.model.TaskPatch
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Ciało `PATCH /api/tasks/:id` budowane jako `JsonObject` — ten sam powód co
 * przy karcie klienta i deala: API rozróżnia „pole nieobecne = bez zmian" od
 * „`null` = wyczyść wartość", a wspólny `Json` aplikacji ma `explicitNulls =
 * false`, więc nulle z data class w ogóle nie dojechałyby do serwera.
 */
fun buildTaskPatch(patch: TaskPatch): JsonObject = buildJsonObject {
    patch.title?.let { put("title", JsonPrimitive(it.value)) }
    patch.description?.let { putNullable("description", it.value) }
    patch.status?.let { put("status", JsonPrimitive(it.value.wire)) }
    patch.priority?.let { put("priority", JsonPrimitive(it.value.wire)) }
    patch.assigneeId?.let { putNullable("assigneeId", it.value) }
    patch.dueAt?.let { putNullable("dueAt", it.value) }
    patch.section?.let { putNullable("section", it.value?.wire) }
    patch.estimatedMinutes?.let { putNullable("estimatedMinutes", it.value) }
    patch.slaHours?.let { putNullable("slaHours", it.value) }
}

private fun JsonObjectBuilder.putNullable(key: String, value: String?) {
    put(key, if (value == null) JsonNull else JsonPrimitive(value))
}

private fun JsonObjectBuilder.putNullable(key: String, value: Int?) {
    put(key, if (value == null) JsonNull else JsonPrimitive(value))
}
