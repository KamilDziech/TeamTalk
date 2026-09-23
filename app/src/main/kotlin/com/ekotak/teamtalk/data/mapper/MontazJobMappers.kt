package com.ekotak.teamtalk.data.mapper

import com.ekotak.teamtalk.data.local.entity.MontazJobEntity
import com.ekotak.teamtalk.data.local.entity.MontazProtocolEntity
import com.ekotak.teamtalk.data.remote.dto.JobPacketDto
import com.ekotak.teamtalk.data.remote.dto.JobProtocolItemDto
import com.ekotak.teamtalk.data.remote.dto.JobRowDto
import com.ekotak.teamtalk.domain.model.DealDifficulty
import com.ekotak.teamtalk.domain.model.MontazJob
import com.ekotak.teamtalk.domain.model.MontazJobAssignee
import com.ekotak.teamtalk.domain.model.MontazJobContract
import com.ekotak.teamtalk.domain.model.MontazJobRow
import com.ekotak.teamtalk.domain.model.MontazJobScopeItem
import com.ekotak.teamtalk.domain.model.MontazProtocol
import com.ekotak.teamtalk.domain.model.MontazStatus
import com.ekotak.teamtalk.domain.model.PROTOCOL_SCHEMA_VERSION
import com.ekotak.teamtalk.domain.model.ProtocolAnswer
import com.ekotak.teamtalk.domain.model.ProtocolKind
import com.ekotak.teamtalk.domain.model.ProtocolPhoto
import com.ekotak.teamtalk.domain.model.ProtocolQuestion
import com.ekotak.teamtalk.domain.montaz.MontazTool
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * MODUŁ MONTAŻ: DTO ↔ cache ↔ model domenowy, i osobno — kształt zapisu
 * protokołu.
 *
 * Teczka wyjazdu ląduje w cache'u jako jeden JSON (`packetJson`): przychodzi
 * i odchodzi w całości, więc rozbicie jej na kolumny tylko mnożyłoby migracje
 * przy każdej zmianie kontraktu z API.
 *
 * Protokół jest tu drugą, ważniejszą połową: `formData` musi wyglądać DOKŁADNIE
 * tak samo, jak zapisuje je panel (`web/src/app/app/installations/protocol-form.ts`)
 * i jak czyta generator PDF. Dlatego pytania zapisujemy RAZEM z odpowiedziami —
 * schemat w katalogu żyje dalej, a protokół sprzed pół roku ma się wydrukować
 * tak, jak go wypełniono.
 */

// ── Lista i teczka ───────────────────────────────────────────────────────────

fun JobRowDto.toEntity(now: Long): MontazJobEntity = MontazJobEntity(
    id = id,
    dealId = dealId,
    dealCode = dealCode,
    clientName = client.name,
    address = client.address,
    city = client.city,
    scheduledAt = scheduledAt,
    status = status,
    durationDays = durationDays ?: DEFAULT_MONTAZ_DAYS,
    difficulty = difficulty,
    scopeNames = emptyList(),
    // Lista nie niesie teczki — jej `null` to nie brak danych, tylko znak, że
    // wyjazdu jeszcze nie otwierano. Wiersz z pobraną teczką zachowuje ją
    // w `MontazJobRepositoryImpl` (patrz `keepPacket`).
    packetJson = null,
    syncedAt = now,
)

fun JobPacketDto.toEntity(json: Json, now: Long): MontazJobEntity = MontazJobEntity(
    id = installationId,
    dealId = dealId,
    dealCode = dealCode,
    clientName = client.name,
    address = client.address,
    city = client.city,
    scheduledAt = scheduledAt,
    status = status,
    durationDays = durationDays ?: DEFAULT_MONTAZ_DAYS,
    difficulty = difficulty,
    scopeNames = scopeNames,
    packetJson = json.encodeToString(JobPacketDto.serializer(), this),
    syncedAt = now,
)

fun MontazJobEntity.toRow(pending: Boolean = false): MontazJobRow = MontazJobRow(
    id = id,
    dealId = dealId,
    dealCode = dealCode,
    clientName = clientName,
    address = address,
    city = city,
    scheduledAt = scheduledAt,
    status = MontazStatus.fromWire(status),
    durationDays = durationDays,
    difficulty = DealDifficulty.fromWire(difficulty),
    scopeNames = scopeNames,
    pending = pending,
)

/** Teczka z cache'u; `null` = wiersz przyszedł z samej listy i trzeba pobrać. */
fun MontazJobEntity.toJob(json: Json, fromCache: Boolean, pending: Boolean): MontazJob? {
    val raw = packetJson ?: return null
    val dto = runCatching { json.decodeFromString(JobPacketDto.serializer(), raw) }.getOrNull()
        ?: return null
    return dto.toDomain(fromCache = fromCache, pending = pending)
}

fun JobPacketDto.toDomain(fromCache: Boolean, pending: Boolean): MontazJob = MontazJob(
    row = MontazJobRow(
        id = installationId,
        dealId = dealId,
        dealCode = dealCode,
        clientName = client.name,
        address = client.address,
        city = client.city,
        scheduledAt = scheduledAt,
        status = MontazStatus.fromWire(status),
        durationDays = durationDays ?: DEFAULT_MONTAZ_DAYS,
        difficulty = DealDifficulty.fromWire(difficulty),
        scopeNames = scopeNames,
        pending = pending,
    ),
    phone = client.phone,
    lat = client.lat,
    lng = client.lng,
    teamNote = teamNote,
    nodeIds = nodeIds,
    contract = contract?.let { c ->
        MontazJobContract(
            numer = c.numer,
            podpisana = c.podpisana,
            pozycje = c.pozycje.map {
                MontazJobScopeItem(
                    lp = it.lp,
                    opis = it.opis,
                    ilosc = it.ilosc,
                    jm = it.jm,
                    etap = it.etap,
                )
            },
            wylaczony = c.wylaczony,
        )
    },
    assignees = assignees.map { MontazJobAssignee(it.userId, it.name, it.role) },
    tools = tools.map {
        MontazTool(
            name = it.name,
            group = it.group,
            qty = it.qty,
            unit = it.unit,
            required = it.required,
            beacon = it.beacon,
            owner = it.owner,
            note = it.note,
            fromNodes = it.fromNodes,
        )
    },
    toolNotes = toolNotes,
    protocol = protocol.map { it.toDomain() },
    fromCache = fromCache,
)

fun JobProtocolItemDto.toDomain(): ProtocolQuestion = ProtocolQuestion(
    id = id,
    label = label,
    group = group,
    kind = ProtocolKind.fromWire(kind),
    unit = unit,
    options = options,
    required = required,
    photo = ProtocolPhoto.fromWire(photo),
    photoMin = photoMin,
    hint = hint,
    fromNodes = fromNodes,
)

// ── Protokół: cache ↔ model ↔ `formData` ─────────────────────────────────────

fun MontazProtocolEntity.toDomain(json: Json): MontazProtocol {
    val form = runCatching { json.parseToJsonElement(formJson).jsonObject }.getOrNull()
    return protocolFromForm(installationId, form).copy(
        signature = signature,
        closedAt = closedAt ?: protocolFromForm(installationId, form).closedAt,
        pending = pendingSince != null,
    )
}

fun MontazProtocol.toEntity(json: Json, pendingSince: Long?, now: Long): MontazProtocolEntity =
    MontazProtocolEntity(
        installationId = installationId,
        formJson = json.encodeToString(JsonObject.serializer(), toForm()),
        signature = signature,
        closedAt = closedAt,
        pendingSince = pendingSince,
        syncedAt = now,
    )

/**
 * `formData` w kształcie wspólnym z panelem i generatorem PDF.
 *
 * Nazwy pól są celowo takie, jakie miał stary, sztywny protokół tam, gdzie
 * znaczyły to samo (`issues`, `acceptedWithoutReservations`, `clientName`) —
 * dzięki temu dokumenty z obu epok czyta się tym samym okiem, a `schemaVersion`
 * rozstrzyga, który formularz wyrysować.
 */
fun MontazProtocol.toForm(): JsonObject = buildJsonObject {
    put("schemaVersion", PROTOCOL_SCHEMA_VERSION)
    put("scope", buildJsonArray { scope.forEach { add(JsonPrimitive(it)) } })
    put("client", client)
    put("address", address)
    put("technicians", technicians)
    put("issues", issues)
    put("acceptedWithoutReservations", accepted)
    put("clientName", clientName)
    put("closedAt", closedAt)
    put(
        "items",
        buildJsonArray {
            items.forEach { q ->
                add(
                    buildJsonObject {
                        put("id", q.id)
                        put("label", q.label)
                        put("group", q.group)
                        put("kind", q.kind.wire)
                        put("unit", q.unit)
                        put("options", buildJsonArray { q.options.forEach { add(JsonPrimitive(it)) } })
                        put("required", q.required)
                        put("photo", q.photo.wire)
                        put("photoMin", q.photoMin)
                        put("hint", q.hint)
                    },
                )
            }
        },
    )
    put(
        "answers",
        buildJsonObject {
            answers.forEach { (id, a) ->
                val question = items.firstOrNull { it.id == id }
                put(
                    id,
                    buildJsonObject {
                        put("value", answerValue(question?.kind, a))
                        put("note", a.note)
                        put("photoIds", buildJsonArray { a.photoIds.forEach { add(JsonPrimitive(it)) } })
                    },
                )
            }
        },
    )
}

/** Odpowiedź w takim typie JSON-a, jakiego oczekuje panel i PDF. */
private fun answerValue(kind: ProtocolKind?, a: ProtocolAnswer): JsonPrimitive = when (kind) {
    ProtocolKind.NUMBER -> a.number?.let { JsonPrimitive(it) } ?: JsonPrimitive(null as String?)
    ProtocolKind.CHECK -> a.checked?.let { JsonPrimitive(it) } ?: JsonPrimitive(null as String?)
    ProtocolKind.PHOTO -> JsonPrimitive(null as String?)
    else -> if (a.text.isBlank()) JsonPrimitive(null as String?) else JsonPrimitive(a.text)
}

/**
 * Odczyt zapisanego protokołu — tolerancyjny, bo to samo pole zapisuje panel,
 * a kształt rośnie z czasem. Protokół w STARYM kształcie (bez `schemaVersion`)
 * oddajemy pusty: jego pola nie pasują do pytań z zakresu, a nadpisywanie go
 * z telefonu skasowałoby dokument, który ktoś już podpisał.
 */
fun protocolFromForm(installationId: String, form: JsonObject?): MontazProtocol {
    if (form == null) return MontazProtocol(installationId = installationId)
    val version = form["schemaVersion"]?.jsonPrimitive?.intOrNull
    if (version != PROTOCOL_SCHEMA_VERSION) return MontazProtocol(installationId = installationId)

    val items = form["items"]?.jsonArray.orEmpty().mapNotNull { el ->
        val o = el.jsonObject
        val id = o.str("id") ?: return@mapNotNull null
        ProtocolQuestion(
            id = id,
            label = o.str("label").orEmpty(),
            group = o.str("group") ?: "Wykonanie",
            kind = ProtocolKind.fromWire(o.str("kind")),
            unit = o.str("unit").orEmpty(),
            options = o["options"]?.jsonArray.orEmpty().mapNotNull { it.jsonPrimitive.contentOrNullSafe() },
            required = o["required"]?.jsonPrimitive?.booleanOrNull ?: false,
            photo = ProtocolPhoto.fromWire(o.str("photo")),
            photoMin = o["photoMin"]?.jsonPrimitive?.intOrNull,
            hint = o.str("hint").orEmpty(),
        )
    }

    val answers = buildMap {
        form["answers"]?.jsonObject?.forEach { (id, el) ->
            val o = el.jsonObject
            val value = o["value"]?.jsonPrimitive
            put(
                id,
                ProtocolAnswer(
                    checked = value?.booleanOrNull,
                    number = value?.doubleOrNull,
                    text = value?.contentOrNullSafe().orEmpty(),
                    note = o.str("note").orEmpty(),
                    photoIds = o["photoIds"]?.jsonArray.orEmpty()
                        .mapNotNull { it.jsonPrimitive.contentOrNullSafe() },
                ),
            )
        }
    }

    return MontazProtocol(
        installationId = installationId,
        scope = form["scope"]?.jsonArray.orEmpty().mapNotNull { it.jsonPrimitive.contentOrNullSafe() },
        client = form.str("client").orEmpty(),
        address = form.str("address").orEmpty(),
        technicians = form.str("technicians").orEmpty(),
        items = items,
        answers = answers,
        issues = form.str("issues").orEmpty(),
        accepted = form["acceptedWithoutReservations"]?.jsonPrimitive?.booleanOrNull ?: false,
        clientName = form.str("clientName").orEmpty(),
        closedAt = form.str("closedAt"),
    )
}

private fun JsonObject.str(key: String): String? = this[key]?.jsonPrimitive?.contentOrNullSafe()

/** `content` bez wywrotki na `null` i bez brania „null" za treść. */
private fun JsonPrimitive.contentOrNullSafe(): String? =
    if (this is kotlinx.serialization.json.JsonNull) null else content.takeIf { it != "null" }

private fun kotlinx.serialization.json.JsonArray?.orEmpty(): List<kotlinx.serialization.json.JsonElement> =
    this ?: emptyList()
