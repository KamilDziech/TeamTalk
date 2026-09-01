package com.ekotak.teamtalk.data.remote.dto

import com.ekotak.teamtalk.domain.model.Deal
import com.ekotak.teamtalk.domain.model.DealDraft
import com.ekotak.teamtalk.domain.model.toDraft
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Ciało `PATCH /api/deals/:id` budowane jako różnica draftu i oryginału.
 *
 * Dlaczego ręczny `JsonObject`, a nie zwykłe DTO: API rozróżnia „pole nieobecne
 * = bez zmian" od „`null` = wyczyść wartość", a wspólny `Json` aplikacji ma
 * `explicitNulls = false` — z data class nulle wypadłyby z żądania i nie dałoby
 * się niczego wyczyścić. Tu decydujemy o każdym polu osobno: nietknięte pomijamy
 * (nie nadpisujemy cudzych równoległych zmian), wyczyszczone wysyłamy jako
 * jawny `JsonNull`.
 */
fun buildDealPatch(original: Deal, draft: DealDraft): JsonObject {
    val before = original.toDraft()

    return buildJsonObject {
        // Pola nullowalne: pusty tekst w formularzu = null = wyczyszczenie.
        putIfChanged("source", before.source, draft.source)
        putIfChanged("description", before.description, draft.description)
        putIfChanged("projectName", before.projectName, draft.projectName)
        putIfChanged("discountCode", before.discountCode, draft.discountCode)
        putIfChanged("driveFolder", before.driveFolder, draft.driveFolder)
        putIfChanged("auditAddress", before.auditAddress, draft.auditAddress)
        putIfChanged("meetingUrl", before.meetingUrl, draft.meetingUrl)
        putIfChanged("billingName", before.billingName, draft.billingName)
        putIfChanged("billingCompany", before.billingCompany, draft.billingCompany)
        putIfChanged("billingNip", before.billingNip, draft.billingNip)
        putIfChanged("billingAddress", before.billingAddress, draft.billingAddress)

        // Enumy — do JSON-a idzie wartość `wire` zgodna ze schematem Zoda.
        putIfChanged("segment", before.segment.wire, draft.segment.wire)
        putIfChanged("buildingKind", before.buildingKind.wire, draft.buildingKind.wire)
        putIfChanged("difficulty", before.difficulty?.wire, draft.difficulty?.wire)
        putIfChanged("buyerPersona", before.buyerPersona?.wire, draft.buyerPersona?.wire)
        putIfChanged("meetingKind", before.meetingKind?.wire, draft.meetingKind?.wire)
        putIfChanged("auditAddressKind", before.auditAddressKind?.wire, draft.auditAddressKind?.wire)

        // Flagi — schemat nie dopuszcza tu nulla, więc zawsze wartość logiczna.
        putIfChanged("rodoConsent", before.rodoConsent, draft.rodoConsent)
        putIfChanged(
            "elderlyContactException",
            before.elderlyContactException,
            draft.elderlyContactException,
        )
        putIfChanged(
            "billingSameAsInstall",
            before.billingSameAsInstall,
            draft.billingSameAsInstall,
        )

        // Daty — API przyjmuje ISO 8601 (`z.coerce.date()`), null czyści termin.
        putIfChanged("nextContactAt", before.nextContactAt?.toIso(), draft.nextContactAt?.toIso())
        putIfChanged("meetingAt", before.meetingAt?.toIso(), draft.meetingAt?.toIso())
        putIfChanged("auditMeetingAt", before.auditMeetingAt?.toIso(), draft.auditMeetingAt?.toIso())

        putIfChanged("meetingDurationMin", before.meetingDurationMin, draft.meetingDurationMin)

        // Opiekunowie — `ownerId` jest wymagany (bez nulla), pozostali mogą być puści.
        if (before.ownerId != draft.ownerId && draft.ownerId.isNotBlank()) {
            put("ownerId", JsonPrimitive(draft.ownerId))
        }
        putIfChanged("stageOwnerId", before.stageOwnerId, draft.stageOwnerId)
        putIfChanged("meetingOwnerId", before.meetingOwnerId, draft.meetingOwnerId)
        putIfChanged("auditOwnerId", before.auditOwnerId, draft.auditOwnerId)

        // `buildingData` i `ozcData` to w API obiekty `.strict()` podmieniane w
        // całości — wysyłamy je tylko, gdy zmieniło się którekolwiek pole bloku,
        // a pusty blok czyścimy nullem.
        if (buildingChanged(before, draft)) {
            put(
                "buildingData",
                if (draft.buildingDataEmpty) JsonNull else buildJsonObject {
                    putNullable("people", draft.people)
                    putNullable("areaM2", draft.areaM2)
                    putNullable("floors", draft.floors)
                    putNullable("shape", draft.shape)
                    putNullable("construction", draft.construction)
                    putNullable("stage", draft.buildingStage)
                    putNullable("windows", draft.windows)
                    putNullable("heatedBasement", draft.heatedBasement)
                    putNullable("heatedGarage", draft.heatedGarage)
                },
            )
        }

        if (ozcChanged(before, draft)) {
            put(
                "ozcData",
                if (draft.ozcEmpty) JsonNull else buildJsonObject {
                    putNullable("buildingKw", draft.ozcBuildingKw)
                    putNullable("dhwKw", draft.ozcDhwKw)
                    putNullable("sourceUrl", draft.ozcSourceUrl)
                    put("confirmed", JsonPrimitive(draft.ozcConfirmed))
                },
            )
        }
    }
}

private fun buildingChanged(before: DealDraft, draft: DealDraft): Boolean =
    before.people != draft.people ||
        before.areaM2 != draft.areaM2 ||
        before.floors != draft.floors ||
        before.shape != draft.shape ||
        before.construction != draft.construction ||
        before.buildingStage != draft.buildingStage ||
        before.windows != draft.windows ||
        before.heatedBasement != draft.heatedBasement ||
        before.heatedGarage != draft.heatedGarage

private fun ozcChanged(before: DealDraft, draft: DealDraft): Boolean =
    before.ozcBuildingKw != draft.ozcBuildingKw ||
        before.ozcDhwKw != draft.ozcDhwKw ||
        before.ozcSourceUrl != draft.ozcSourceUrl ||
        before.ozcConfirmed != draft.ozcConfirmed

// ── Pomocniki budowania obiektu ──────────────────────────────────────────────

private fun kotlinx.serialization.json.JsonObjectBuilder.putIfChanged(
    key: String,
    before: Any?,
    after: Any?,
) {
    if (before == after) return
    put(key, after.toJson())
}

/** Wewnątrz `buildingData`/`ozcData` pola puste jadą jako jawny null. */
private fun kotlinx.serialization.json.JsonObjectBuilder.putNullable(key: String, value: Any?) {
    put(key, value.toJson())
}

private fun Any?.toJson() = when (this) {
    null -> JsonNull
    is String -> JsonPrimitive(this)
    is Int -> JsonPrimitive(this)
    is Long -> JsonPrimitive(this)
    is Double -> JsonPrimitive(this)
    is Boolean -> JsonPrimitive(this)
    else -> JsonPrimitive(toString())
}

private fun Long.toIso(): String =
    SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(Date(this))
