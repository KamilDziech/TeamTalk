package com.ekotak.teamtalk.data.remote.dto

import com.ekotak.teamtalk.domain.model.EmailThreadPatch
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Ciało `PATCH /api/email/threads/:id` budowane jako `JsonObject` — ten sam
 * powód co przy zadaniu i karcie klienta: API rozróżnia „pole nieobecne = bez
 * zmian" od „`null` = wyczyść wartość", a wspólny `Json` aplikacji ma
 * `explicitNulls = false`, więc `null` z data class nigdy by nie dojechał.
 *
 * W poczcie ma to jedno konkretne zastosowanie: `dealId: null` ODPINA wątek od
 * karty deala. Bez jawnego nulla przycisk „odłącz" nie miałby jak zadziałać.
 */
fun buildEmailThreadPatch(patch: EmailThreadPatch): JsonObject = buildJsonObject {
    patch.starred?.let { put("starred", JsonPrimitive(it)) }
    patch.unread?.let { put("unread", JsonPrimitive(it)) }
    patch.folder?.let { put("folder", JsonPrimitive(it.wire)) }
    patch.dealId?.let { edit ->
        put("dealId", edit.value?.let { JsonPrimitive(it) } ?: JsonNull)
    }
    patch.labelIds?.let { ids ->
        put("labelIds", JsonArray(ids.map { JsonPrimitive(it) }))
    }
}
