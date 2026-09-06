package com.ekotak.teamtalk.data.mapper

import com.ekotak.teamtalk.data.local.entity.AuditEntity
import com.ekotak.teamtalk.data.local.entity.CatalogCategoryEntity
import com.ekotak.teamtalk.data.remote.dto.AuditDto
import com.ekotak.teamtalk.data.remote.dto.CategoryDto
import com.ekotak.teamtalk.data.remote.dto.ContractSummaryDto
import com.ekotak.teamtalk.domain.model.Audit
import com.ekotak.teamtalk.domain.model.Category
import com.ekotak.teamtalk.domain.model.BuildingStandard
import com.ekotak.teamtalk.domain.model.HeatloadMode
import com.ekotak.teamtalk.domain.model.OfferLock
import com.ekotak.teamtalk.domain.model.UFH_AUDIT_KIND
import com.ekotak.teamtalk.domain.model.UFH_BOX_TYPES
import com.ekotak.teamtalk.domain.model.UFH_DEFAULT_MANIFOLDS
import com.ekotak.teamtalk.domain.model.UFH_LEGACY_SYSTEM_MAP
import com.ekotak.teamtalk.domain.model.UfhAreaField
import com.ekotak.teamtalk.domain.model.UfhFloor
import com.ekotak.teamtalk.domain.model.UfhInstall
import com.ekotak.teamtalk.domain.model.UfhState
import com.ekotak.teamtalk.domain.model.DEFAULT_UFH_PIPE_SYSTEM
import com.ekotak.teamtalk.domain.model.toM2
import com.ekotak.teamtalk.domain.model.ufhAsksMedium
import com.ekotak.teamtalk.domain.model.ufhAsksWarrantyDocs
import com.ekotak.teamtalk.domain.model.ufhPipeSystemCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Tłumaczenie audytów między API a modelem telefonu.
 *
 * Najwrażliwszy fragment całej zakładki: `Audit.formData` jest kontraktem
 * z panelem — to z niego liczy się oferta i materiał. Dlatego zapis z telefonu
 * wysyła DOKŁADNIE te same klucze, w tych samych typach, co
 * `ufhToFormData` w board360, a pola warstwy rzutu (kropki rozdzielaczy,
 * obrysy, skala, historia, podpisy) przechodzą tędy nietknięte.
 */

// ── Audyt (rekord) ───────────────────────────────────────────────────────────

fun AuditDto.toEntity(dealId: String): AuditEntity = AuditEntity(
    id = id,
    dealId = this.dealId.ifBlank { dealId },
    heatloadMode = heatloadMode,
    heatloadKw = heatloadKw,
    formData = formData?.toString(),
    createdAt = createdAt,
    // Rekord prosto z serwera jest z definicji wysłany.
    pendingSince = null,
)

/**
 * Rekord z cache. Uszkodzonego `formData` (przerwany zapis, ręczna zmiana
 * pliku bazy) nie próbujemy ratować — wpis wraca jako audyt bez formularza,
 * zamiast wywracać całą zakładkę.
 */
fun AuditEntity.toDomain(): Audit {
    val parsed = formData
        ?.let { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
    return Audit(
        id = id,
        dealId = dealId,
        heatloadMode = HeatloadMode.fromWire(heatloadMode),
        heatloadKw = heatloadKw,
        createdAt = createdAt,
        note = parsed?.str("note").takeIf { !it.isNullOrBlank() },
        formKind = parsed?.str("kind"),
        categoryId = parsed?.str("categoryId"),
        installationForm = parsed
            ?.takeIf { it.str("kind") == UFH_AUDIT_KIND }
            ?.let { ufhFromFormData(it) },
        pendingSince = pendingSince,
    )
}

fun CategoryDto.toCatalogEntity(): CatalogCategoryEntity = CatalogCategoryEntity(
    id = id,
    parentId = parentId,
    name = name,
    position = position,
    auditForm = auditForm?.toString(),
)

fun CatalogCategoryEntity.toDomain(): Category = Category(
    id = id,
    parentId = parentId,
    name = name,
    position = position,
    auditForm = auditForm
        ?.let { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() }
        ?.let { ufhFromFormData(it) },
)

fun AuditDto.toDomain(): Audit = Audit(
    id = id,
    dealId = dealId,
    heatloadMode = HeatloadMode.fromWire(heatloadMode),
    heatloadKw = heatloadKw,
    createdAt = createdAt,
    note = formData?.str("note").takeIf { !it.isNullOrBlank() },
    formKind = formData?.str("kind"),
    categoryId = formData?.str("categoryId"),
    installationForm = formData
        ?.takeIf { it.str("kind") == UFH_AUDIT_KIND }
        ?.let { ufhFromFormData(it) },
)

/**
 * Ciało nowego audytu Heizlast. `heatloadInputs` liczy SERWER — telefon podaje
 * wejścia (metraż, standard, wysokość), a nie wynik: gdyby liczył sam,
 * wskaźniki rozjechałyby się z panelem przy pierwszej korekcie domeny.
 */
fun buildHeatloadBody(
    mode: HeatloadMode?,
    areaM2: Double?,
    standard: BuildingStandard?,
    heightM: Double?,
    kw: Double?,
    note: String?,
): JsonObject = buildJsonObject {
    when (mode) {
        HeatloadMode.SZYBKI -> {
            put("heatloadMode", JsonPrimitive(mode.wire))
            put(
                "heatloadInputs",
                buildJsonObject {
                    put("area", JsonPrimitive(areaM2))
                    put("standard", JsonPrimitive(standard?.wire))
                    if (heightM != null) put("height", JsonPrimitive(heightM))
                },
            )
        }
        HeatloadMode.DIN -> {
            put("heatloadMode", JsonPrimitive(mode.wire))
            put("heatloadKw", JsonPrimitive(kw))
        }
        // Sam wpis do dziennika audytu — bez Heizlast. Panel dopuszcza to samo
        // (tryb pusty + notatka), więc telefon nie jest tu bardziej wymagający.
        null -> Unit
    }
    val trimmed = note?.trim().orEmpty()
    if (trimmed.isNotEmpty()) {
        put("formData", buildJsonObject { put("note", JsonPrimitive(trimmed)) })
    }
}

// ── Formularz audytu instalacji (OP) ─────────────────────────────────────────

/** Klucze kondygnacji, których telefon NIE edytuje — przenosimy je bez zmian. */
private val PLAN_KEYS = listOf(
    "manifoldMarks",
    "heatSource",
    "manifoldHistory",
    "rooms",
    "planScale",
    "marksSavedAt",
    "marksSavedBy",
    "areaSavedAt",
    "areaSavedBy",
    "planSlot",
    "planDocId",
)

/**
 * Odczyt stanu formularza z `formData`. Używany dla dwóch źródeł: rekordu deala
 * i szablonu z katalogu (`Category.auditForm`) — kształt jest ten sam, więc
 * dziedziczenie sprowadza się do wyboru, który obiekt tu podamy.
 */
fun ufhFromFormData(fd: JsonObject?): UfhState {
    if (fd == null) return UfhState()

    // Stary zapis: jeden system na cały budynek → przepisujemy na kondygnacje.
    val legacySystem = UFH_LEGACY_SYSTEM_MAP[fd.str("system").orEmpty()].orEmpty()

    val floors = (fd["floors"] as? JsonArray)
        ?.mapNotNull { it as? JsonObject }
        ?.map { o ->
            UfhFloor(
                name = o.str("name").orEmpty(),
                projectM2 = o.str("projectM2").orEmpty(),
                system = o.str("system").orEmpty().ifEmpty { legacySystem },
                comment = o.str("comment").orEmpty(),
                // Puste (stare zapisy) → 1: rozdzielacz jest regułą.
                manifolds = o.str("manifolds").orEmpty().ifEmpty { UFH_DEFAULT_MANIFOLDS },
                boxType = o.str("boxType").orEmpty().ifEmpty { UFH_BOX_TYPES[0] },
                areas = UfhAreaField.entries.associate { f -> f.key to o.str(f.key).orEmpty() },
                planJson = o.planPassthrough(),
            )
        }
        .orEmpty()

    return UfhState(
        // Zapisana wartość bywa etykietą (import) → normalizujemy do kodu.
        // Pusto (audyt sprzed wprowadzenia pola) → system domyślny.
        pipeSystem = ufhPipeSystemCode(fd.str("pipeSystem")).ifEmpty { DEFAULT_UFH_PIPE_SYSTEM },
        roomControl = fd.str("roomControl").orEmpty(),
        systemFilling = fd.str("systemFilling").orEmpty(),
        cooling = fd["cooling"]?.jsonPrimitive?.booleanOrNull == true,
        install = ufhInstallFromJson(fd["install"] as? JsonObject),
        floors = floors.ifEmpty { listOf(UfhFloor()) },
    )
}

private fun ufhInstallFromJson(o: JsonObject?): UfhInstall {
    if (o == null) return UfhInstall()
    return UfhInstall(
        wallChase = o.str("wallChase").orEmpty(),
        leadInRouting = o.str("leadInRouting").orEmpty(),
        subfloorJoints = o.str("subfloorJoints").orEmpty(),
        leadInByWodKan = o["leadInByWodKan"]?.jsonPrimitive?.booleanOrNull == true,
        manifoldByWodKan = o["manifoldByWodKan"]?.jsonPrimitive?.booleanOrNull == true,
        designScope = o.str("designScope").orEmpty(),
        leadInPipeMm = o.str("leadInPipeMm").orEmpty(),
        pressureTest = o.str("pressureTest").orEmpty(),
        systemPlateM2 = o.str("systemPlateM2").orEmpty(),
        wasteRemoval = o.str("wasteRemoval").orEmpty(),
        heatMedium = o.str("heatMedium").orEmpty(),
        biocide = o.str("biocide").orEmpty(),
        warrantyDocs = o.str("warrantyDocs").orEmpty(),
    )
}

/**
 * Serializacja stanu do `formData`. `includeCooling = false` wymusza
 * `cooling: false` — pole chłodzenia pokazujemy tylko przy pompie ciepła,
 * a do oferty nie może trafić odpowiedź, której audytor nie widział.
 *
 * `categoryId` przypina rekord do węzła katalogu (jeden formularz na parę
 * deal + węzeł), dokładnie jak w panelu.
 */
fun ufhToFormData(s: UfhState, includeCooling: Boolean, categoryId: String): JsonObject =
    buildJsonObject {
        put("kind", JsonPrimitive(UFH_AUDIT_KIND))
        put("categoryId", JsonPrimitive(categoryId))
        put("pipeSystem", s.pipeSystem.orNull())
        put("roomControl", s.roomControl.orNull())
        put("systemFilling", s.systemFilling.orNull())
        put("cooling", JsonPrimitive(includeCooling && s.cooling))
        put("install", ufhInstallToJson(s))
        put(
            "floors",
            buildJsonArray {
                s.floors.forEach { f ->
                    add(
                        buildJsonObject {
                            put("name", f.name.trim().orNull())
                            put("projectM2", f.projectM2.numOrNull())
                            put("system", f.system.orNull())
                            put("comment", f.comment.trim().orNull())
                            put("manifolds", f.manifolds.numOrNull())
                            put("boxType", f.boxType.orNull())
                            UfhAreaField.entries.forEach { field ->
                                put(field.key, f.area(field).numOrNull())
                            }
                            // Warstwa rzutu wraca taka, jaka przyszła — telefon
                            // jej nie rysuje, więc nie ma prawa jej skasować.
                            f.planJson?.let { raw ->
                                (Json.parseToJsonElement(raw) as? JsonObject)
                                    ?.forEach { (key, value) -> put(key, value) }
                            }
                        },
                    )
                }
            },
        )
    }

/**
 * Pytania warunkowe niewidoczne przy obecnych wyborach zapisujemy jako `null` —
 * inaczej w ofercie zostawałaby odpowiedź, której audytor już nie widzi.
 */
private fun ufhInstallToJson(s: UfhState): JsonObject {
    val i = s.install
    val medium = ufhAsksMedium(s)
    val warranty = ufhAsksWarrantyDocs(s)
    return buildJsonObject {
        put("wallChase", i.wallChase.orNull())
        put("leadInRouting", i.leadInRouting.orNull())
        put("subfloorJoints", i.subfloorJoints.orNull())
        put("leadInByWodKan", JsonPrimitive(i.leadInByWodKan))
        put("manifoldByWodKan", JsonPrimitive(i.manifoldByWodKan))
        put("designScope", i.designScope.orNull())
        put("leadInPipeMm", i.leadInPipeMm.numOrNull())
        put("pressureTest", i.pressureTest.orNull())
        put("systemPlateM2", i.systemPlateM2.numOrNull())
        put("wasteRemoval", i.wasteRemoval.orNull())
        put("heatMedium", if (medium) i.heatMedium.orNull() else JsonNull)
        put("biocide", if (medium) i.biocide.orNull() else JsonNull)
        put("warrantyDocs", if (warranty) i.warrantyDocs.orNull() else JsonNull)
    }
}

// ── Blokada oferty ───────────────────────────────────────────────────────────

/**
 * Umowa zamykająca ofertę — ten sam wybór co `getOfferLock` w panelu: lista
 * przychodzi od najnowszej, a zmianę wystawia się do dokumentu, którego nikt
 * jeszcze nie zmienia. `null` = brak podpisu, audyt otwarty.
 */
fun offerLockFrom(contracts: List<ContractSummaryDto>): OfferLock? {
    val signed = contracts.filter { it.status == "signed" }
    if (signed.isEmpty()) return null
    val target = signed.firstOrNull { it.zastapionaPrzez == null } ?: signed.first()
    val inProgress = contracts.firstOrNull {
        it.zastepuje != null && it.status != "cancelled" && it.status != "signed"
    }
    return OfferLock(
        contractId = target.id,
        numer = target.numer,
        podpisana = target.podpisana,
        zmianaWToku = inProgress?.numer,
    )
}

// ── Pomocniki JSON ───────────────────────────────────────────────────────────

/**
 * Wartość pola jako tekst — API zapisuje liczby jako liczby, a formularz
 * trzyma wszystko w polach tekstowych, więc `12.5` ma tu wyjść jako „12.5",
 * a nie jako `"12.5"` z cudzysłowami.
 */
private fun JsonObject.str(key: String): String? {
    val prim = this[key]?.jsonPrimitive ?: return null
    if (prim is JsonNull) return null
    return prim.content
}

/** Puste pole formularza → jawny `null` w JSON-ie (nie pusty string). */
private fun String.orNull(): JsonElement =
    if (isBlank()) JsonNull else JsonPrimitive(this)

/**
 * Puste/nieliczbowe pole → `null` (nie 0 — brak odpowiedzi to nie zero).
 * Wartość całkowitą zapisujemy jako liczbę całkowitą: `manifolds: 1`, a nie
 * `1.0`. Panel czyta te pola przez `String(...)`, więc ułamkowe zero robiłoby
 * z „1 rozdzielacza" tekst „1.0" w polu, którego nikt nie ruszał.
 */
private fun String.numOrNull(): JsonElement {
    val value = toM2() ?: return JsonNull
    val whole = value.toLong()
    return if (value == whole.toDouble()) JsonPrimitive(whole) else JsonPrimitive(value)
}

/**
 * Pola warstwy rzutu jako surowy JSON do przeniesienia. `null`, gdy żadnego
 * z nich nie było — nowy audyt nie ma po co wysyłać pustej ramy.
 */
private fun JsonObject.planPassthrough(): String? {
    val kept = PLAN_KEYS.mapNotNull { key -> this[key]?.let { key to it } }
    if (kept.isEmpty()) return null
    return JsonObject(kept.toMap()).toString()
}
