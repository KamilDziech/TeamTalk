package com.ekotak.teamtalk.data.remote.dto

import com.ekotak.teamtalk.domain.model.CalendarEventPatch
import com.ekotak.teamtalk.domain.model.CalendarPatch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Ciała `PATCH` modułu Kalendarz budowane jako `JsonObject` — ten sam powód co
 * przy zadaniach i serwisie: API rozróżnia „pole nieobecne = bez zmian" od
 * „`null` = wyczyść", a wspólny `Json` aplikacji ma `explicitNulls = false`.
 *
 * To samo ciało ląduje w kolejce offline, więc każde pole musi dać się scalić
 * z innym wpisem tego samego wydarzenia po samej nazwie klucza.
 */

fun buildCalendarEventPatch(patch: CalendarEventPatch): JsonObject = buildJsonObject {
    patch.title?.let { put("title", JsonPrimitive(it.value)) }
    patch.description?.let { putNullableText("description", it.value) }
    patch.location?.let { putNullableText("location", it.value) }
    patch.color?.let { putNullableText("color", it.value) }
    patch.startAt?.let { put("startAt", JsonPrimitive(it.value)) }
    patch.endAt?.let { putNullableText("endAt", it.value) }
    patch.allDay?.let { put("allDay", JsonPrimitive(it.value)) }
    patch.assigneeId?.let { putNullableText("assigneeId", it.value) }
    patch.attendeeIds?.let { edit ->
        put("attendeeIds", JsonArray(edit.value.map { JsonPrimitive(it) }))
    }
}

fun buildCalendarPatch(patch: CalendarPatch): JsonObject = buildJsonObject {
    patch.name?.let { put("name", JsonPrimitive(it.value)) }
    patch.color?.let { put("color", JsonPrimitive(it.value)) }
    patch.description?.let { putNullableText("description", it.value) }
}

private fun JsonObjectBuilder.putNullableText(key: String, value: String?) {
    put(key, if (value == null) JsonNull else JsonPrimitive(value))
}
