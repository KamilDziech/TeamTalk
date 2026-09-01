package com.ekotak.teamtalk.data.remote.dto

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

/**
 * Zgłoszenie z publicznej leadowni (cennikinstalacji.pl) dla karty deala —
 * `GET /api/intake/deal/:dealId/lead`. Deal spoza leadowni nie ma rekordu:
 * API odpowiada wtedy pustym ciałem, więc odczyt idzie przez `ResponseBody`
 * i ręczne parsowanie (patrz `LeadIntakeRepositoryImpl`).
 */
@Serializable
data class LeadIntakeResponseDto(
    val channel: String = "",
    val source: String = "",
    val sourceLabel: String? = null,
    val fullName: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val city: String? = null,
    val interest: String? = null,
    val budget: String? = null,
    /** Surowa treść zgłoszenia (archiwum) — na karcie pokazujemy `note`. */
    val message: String? = null,
    val note: String? = null,
    val consent: Boolean = false,
    val submittedBy: String? = null,
    val createdAt: String? = null,
    val building: LeadBuildingDto? = null,
)

/** Dane budynku z kreatora /targi — wartości opisowe, nie liczby. */
@Serializable
data class LeadBuildingDto(
    val shape: String? = null,
    val construction: String? = null,
    val area: String? = null,
    val people: String? = null,
    val floors: Int? = null,
    val stage: String? = null,
    val windows: String? = null,
    val heatedBasement: Boolean = false,
    val heatedGarage: Boolean = false,
)

/** Odpowiedź `PATCH /api/intake/deal/:dealId/lead/note` — rozwiązana notatka. */
@Serializable
data class LeadNoteResponseDto(val note: String? = null)

/**
 * Ciało zapisu notatki. Schemat po stronie API wymaga obecności pola `note`
 * (nullable, ale nie opcjonalne), a wspólny `Json` aplikacji ma
 * `explicitNulls = false` — z data class `null` w ogóle by nie dojechał, więc
 * budujemy obiekt ręcznie, jak przy patchach klienta i deala.
 */
fun buildLeadNoteBody(note: String?): JsonObject = buildJsonObject {
    val clean = note?.trim()?.ifBlank { null }
    put("note", if (clean == null) JsonNull else JsonPrimitive(clean))
}
