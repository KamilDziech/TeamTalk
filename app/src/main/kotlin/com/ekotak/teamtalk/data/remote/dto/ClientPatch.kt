package com.ekotak.teamtalk.data.remote.dto

import com.ekotak.teamtalk.domain.model.Client
import com.ekotak.teamtalk.domain.model.ClientDraft
import com.ekotak.teamtalk.domain.model.toDraft
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Ciało `PATCH /api/clients/:id` budowane jako różnica draftu i oryginału —
 * ten sam powód co przy karcie deala: API rozróżnia „pole nieobecne = bez zmian"
 * od „`null` = wyczyść wartość", a wspólny `Json` aplikacji ma `explicitNulls =
 * false`, więc nulle z data class w ogóle nie dojechałyby do serwera.
 *
 * Imię i nazwisko są w schemacie wymagane (min. 1 znak) — pustych nie wysyłamy.
 */
fun buildClientPatch(original: Client, draft: ClientDraft): JsonObject {
    val before = original.toDraft()

    return buildJsonObject {
        if (before.firstName != draft.firstName && draft.firstName.isNotBlank()) {
            put("firstName", JsonPrimitive(draft.firstName))
        }
        if (before.lastName != draft.lastName && draft.lastName.isNotBlank()) {
            put("lastName", JsonPrimitive(draft.lastName))
        }
        putIfChanged("email", before.email, draft.email)
        putIfChanged("email2", before.email2, draft.email2)
        putIfChanged("phone", before.phone, draft.phone)
        putIfChanged("phone2", before.phone2, draft.phone2)
        putIfChanged("address", before.address, draft.address)
    }
}

/** Pole nietknięte pomijamy, wyczyszczone wysyłamy jako jawny `null`. */
private fun JsonObjectBuilder.putIfChanged(key: String, before: String?, after: String?) {
    if (before == after) return
    put(key, if (after == null) JsonNull else JsonPrimitive(after))
}
