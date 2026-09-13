package com.ekotak.teamtalk.domain.model

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * MODUŁ ZDJĘĆ AUDYTU — co ma być sfotografowane przy danej instalacji i które
 * z tych kadrów już są. Port `audit-photos.ts` z panelu, kształt `photoData`
 * jest ten sam, bo kadr zrobiony w terenie ma być w panelu nieodróżnialny od
 * wgranego przy biurku.
 *
 * Kadr jest ZWYKŁYM plikiem deala w sekcji „Audyt" ([DealDocument]), a całe
 * przypisanie („czego dotyczy") siedzi w jego `photoData`. Dlaczego nie
 * w `Audit.formData`:
 *  • zdjęcie budynku jest jedno, a audytów tyle, ile instalacji — w formularzu
 *    musiałoby się dublować,
 *  • kolejka audytu wysyła CAŁY dokument `formData` jednym wierszem, więc kadr
 *    zrobiony w piwnicy przegrałby z zapisem formularza z parteru,
 *  • plik może powstać, zanim w ogóle istnieje rekord audytu.
 *
 * Trzy grupy kadrów: budynek (wspólne dla wszystkich instalacji deala),
 * rozdzielacze (po jednym na każdy rozdzielacz z formularza) i dodatkowe
 * (dowolne, z opisem).
 */

/** Marker treści — `photoData` jest swobodne, więc mówimy wprost, co to jest. */
const val AUDIT_PHOTO_KIND = "audit-photo"

/** Zakres „cały budynek": kadr widoczny przy KAŻDEJ instalacji tego deala. */
const val BUILDING_SCOPE = "budynek"

enum class AuditPhotoRole(val wire: String) {
    BUDYNEK("budynek"),
    ROZDZIELACZ("rozdzielacz"),
    DODATKOWE("dodatkowe");

    companion object {
        fun fromWire(raw: String?): AuditPhotoRole? = entries.firstOrNull { it.wire == raw }
    }
}

data class AuditPhotoMeta(
    /** `budynek` albo id węzła katalogu (instalacji), do której kadr należy. */
    val scope: String,
    val role: AuditPhotoRole,
    /** Kadr budynku ze stałej listy ([BUILDING_SHOTS]); puste dla reszty. */
    val slot: String = "",
    /** Kondygnacja kadru rozdzielacza (indeks listy kondygnacji audytu). */
    val floorIndex: Int? = null,
    /** Numer rozdzielacza na tej kondygnacji, licząc od zera. */
    val manifoldIndex: Int? = null,
    /** Opis wpisany przez człowieka — to on jedzie do panelu i na montaż. */
    val note: String = "",
    val takenAt: String = "",
) {
    /** JSON do wysyłki (`photoData`) — identyczny z tym, co zapisuje panel. */
    fun toJson(): JsonObject = buildJsonObject {
        put("kind", JsonPrimitive(AUDIT_PHOTO_KIND))
        put("scope", JsonPrimitive(scope))
        put("role", JsonPrimitive(role.wire))
        put("slot", if (slot.isBlank()) JsonNull else JsonPrimitive(slot))
        put("floorIndex", floorIndex?.let { JsonPrimitive(it) } ?: JsonNull)
        put("manifoldIndex", manifoldIndex?.let { JsonPrimitive(it) } ?: JsonNull)
        put("note", if (note.isBlank()) JsonNull else JsonPrimitive(note.trim()))
        put("takenAt", JsonPrimitive(takenAt.ifBlank { isoStamp() }))
    }
}

/** Odczyt `photoData`; obcy albo uszkodzony zapis → `null` (zwykły plik). */
fun parseAuditPhoto(raw: JsonElement?): AuditPhotoMeta? {
    val obj = runCatching { raw?.jsonObject }.getOrNull() ?: return null
    if (obj["kind"]?.jsonPrimitive?.contentOrNull != AUDIT_PHOTO_KIND) return null
    val role = AuditPhotoRole.fromWire(obj["role"]?.jsonPrimitive?.contentOrNull) ?: return null
    val scope = obj["scope"]?.jsonPrimitive?.contentOrNull.orEmpty()
    if (scope.isBlank()) return null
    return AuditPhotoMeta(
        scope = scope,
        role = role,
        slot = obj["slot"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        floorIndex = obj["floorIndex"]?.jsonPrimitive?.intOrNull,
        manifoldIndex = obj["manifoldIndex"]?.jsonPrimitive?.intOrNull,
        note = obj["note"]?.jsonPrimitive?.contentOrNull.orEmpty(),
        takenAt = obj["takenAt"]?.jsonPrimitive?.contentOrNull.orEmpty(),
    )
}

/** Pozycja stałej listy kadrów budynku. */
data class BuildingShot(
    val slot: String,
    val label: String,
    val hint: String,
    val required: Boolean,
)

/** Stała lista kadrów budynku — kolejność = kolejność kafelków na ekranie. */
val BUILDING_SHOTS = listOf(
    BuildingShot("widok-frontu", "Widok od frontu", "cały budynek w kadrze", true),
    BuildingShot("kotlownia", "Kotłownia / źródło ciepła", "co stoi dziś i ile jest miejsca", true),
    BuildingShot(
        "rozdzielnica",
        "Rozdzielnica elektryczna",
        "otwarta, z widocznymi zabezpieczeniami",
        true,
    ),
    BuildingShot(
        "elewacja-jednostka",
        "Elewacja pod jednostkę zewnętrzną",
        "przy pompie ciepła",
        false,
    ),
    BuildingShot("dojazd", "Dojazd i wejście", "czym da się podjechać i którędy wnieść", false),
)

/** Kondygnacja widziana przez moduł zdjęć: nazwa i ilość rozdzielaczy. */
data class PhotoFloor(
    val name: String,
    /** Wartość pola „Ilość rozdzielaczy" z formularza (tekst, jak w audycie). */
    val manifolds: String,
    /** Rodzaje skrzynek z kropek na rzucie (panel) — podpis kafelka, gdy są. */
    val boxTypes: List<String> = emptyList(),
)

/** Kafelek listy kadrów: albo zdjęcie, albo polecenie „zrób to zdjęcie". */
data class PhotoSlot(
    val key: String,
    val label: String,
    val hint: String,
    val required: Boolean,
    /** Kadr budynku: ten sam plik widać przy każdej instalacji deala. */
    val shared: Boolean,
    /** Przypisanie, które dostanie zdjęcie zrobione w ten kafelek. */
    val meta: AuditPhotoMeta,
    val document: DealDocument? = null,
)

/** Kadr dodatkowy albo osierocony (rozdzielacz zdjęty z listy w formularzu). */
data class ExtraPhoto(
    val document: DealDocument,
    val meta: AuditPhotoMeta,
    val shared: Boolean,
    val orphanOf: String? = null,
)

data class ManifoldGroup(
    val floorIndex: Int,
    val floorLabel: String,
    val slots: List<PhotoSlot>,
)

data class AuditPhotoPlan(
    val building: List<PhotoSlot> = emptyList(),
    val manifolds: List<ManifoldGroup> = emptyList(),
    val extras: List<ExtraPhoto> = emptyList(),
    val required: Int = 0,
    val done: Int = 0,
    /** Braki jednym zdaniem — dokładane do listy braków audytu. */
    val missing: List<String> = emptyList(),
) {
    /** Czy cokolwiek czeka jeszcze w kolejce — kadry z terenu bez zasięgu. */
    val pendingCount: Int
        get() = building.count { it.document?.pending == true } +
            manifolds.sumOf { g -> g.slots.count { it.document?.pending == true } } +
            extras.count { it.document.pending }
}

/** Nazwa kondygnacji albo „Kondygnacja N", gdy audytor jej nie nazwał. */
private fun floorLabel(floor: PhotoFloor, index: Int): String =
    floor.name.trim().ifBlank { "Kondygnacja ${index + 1}" }

/** Ilość rozdzielaczy z pola formularza; literówka „111" nie zamawia 111 kadrów. */
private fun manifoldCount(floor: PhotoFloor): Int {
    val n = floor.manifolds.trim().replace(',', '.').toDoubleOrNull() ?: return 0
    if (n <= 0) return 0
    return minOf(n.toInt(), 20)
}

/**
 * Lista kadrów dla JEDNEJ instalacji: kadry budynku (wspólne), po jednym na
 * każdy rozdzielacz z formularza i wszystko, co ktoś dołożył sam.
 *
 * Kadr rozdzielacza, którego już nie ma w formularzu (ktoś zmniejszył ilość
 * albo usunął kondygnację), NIE ZNIKA — schodzi do dodatkowych z adnotacją.
 * Zdjęcia z terenu nie kasujemy dlatego, że zmieniła się liczba w polu.
 */
fun buildAuditPhotoPlan(
    documents: List<DealDocument>,
    floors: List<PhotoFloor>,
    categoryId: String?,
): AuditPhotoPlan {
    val photos = documents
        .mapNotNull { doc -> parseAuditPhoto(doc.photoData)?.let { doc to it } }
        .sortedByDescending { it.first.createdAt }
    val used = mutableSetOf<String>()

    fun pick(match: (AuditPhotoMeta) -> Boolean): DealDocument? {
        val hit = photos.firstOrNull { it.first.id !in used && match(it.second) } ?: return null
        used += hit.first.id
        return hit.first
    }

    val building = BUILDING_SHOTS.map { shot ->
        PhotoSlot(
            key = "budynek:${shot.slot}",
            label = shot.label,
            hint = shot.hint,
            required = shot.required,
            shared = true,
            meta = AuditPhotoMeta(
                scope = BUILDING_SCOPE,
                role = AuditPhotoRole.BUDYNEK,
                slot = shot.slot,
            ),
            document = pick {
                it.role == AuditPhotoRole.BUDYNEK &&
                    it.scope == BUILDING_SCOPE &&
                    it.slot == shot.slot
            },
        )
    }

    val manifolds = mutableListOf<ManifoldGroup>()
    if (categoryId != null) {
        floors.forEachIndexed { index, floor ->
            val count = manifoldCount(floor)
            if (count == 0) return@forEachIndexed
            val label = floorLabel(floor, index)
            val slots = (0 until count).map { nr ->
                PhotoSlot(
                    key = "rozdzielacz:$index:$nr",
                    label = "$label — rozdzielacz ${nr + 1} z $count",
                    // Rodzaj skrzynki znamy z kropek na rzucie (panel) — to
                    // podpowiedź, po czym poznać rozdzielacz, a nie pytanie.
                    hint = floor.boxTypes.getOrNull(nr) ?: "skrzynka i podejścia w kadrze",
                    required = true,
                    shared = false,
                    meta = AuditPhotoMeta(
                        scope = categoryId,
                        role = AuditPhotoRole.ROZDZIELACZ,
                        floorIndex = index,
                        manifoldIndex = nr,
                    ),
                    document = pick {
                        it.role == AuditPhotoRole.ROZDZIELACZ &&
                            it.scope == categoryId &&
                            it.floorIndex == index &&
                            it.manifoldIndex == nr
                    },
                )
            }
            manifolds += ManifoldGroup(index, label, slots)
        }
    }

    val extras = photos
        .filter { (doc, meta) ->
            doc.id !in used && (meta.scope == BUILDING_SCOPE || meta.scope == categoryId)
        }
        .map { (doc, meta) ->
            ExtraPhoto(
                document = doc,
                meta = meta,
                shared = meta.scope == BUILDING_SCOPE,
                orphanOf = when (meta.role) {
                    AuditPhotoRole.ROZDZIELACZ -> "rozdzielacz zdjęty z listy w formularzu"
                    AuditPhotoRole.BUDYNEK -> "kadr budynku spoza listy"
                    AuditPhotoRole.DODATKOWE -> null
                },
            )
        }

    val requiredSlots = building.filter { it.required } + manifolds.flatMap { it.slots }
    val missingSlots = requiredSlots.filter { it.document == null }
    val missingManifolds = missingSlots.count { it.key.startsWith("rozdzielacz:") }
    val missingBuilding = missingSlots.size - missingManifolds

    val missing = buildList {
        if (missingBuilding == 1) {
            add("Brakuje zdjęcia budynku: ${missingSlots.first { it.shared }.label}")
        } else if (missingBuilding > 1) {
            add("Brakuje $missingBuilding zdjęć budynku")
        }
        if (missingManifolds == 1) {
            add("Brakuje zdjęcia rozdzielacza")
        } else if (missingManifolds > 1) {
            add("Brakuje $missingManifolds zdjęć rozdzielaczy")
        }
    }

    return AuditPhotoPlan(
        building = building,
        manifolds = manifolds,
        extras = extras,
        required = requiredSlots.size,
        done = requiredSlots.size - missingSlots.size,
        missing = missing,
    )
}

/** Nazwa pliku kadru — po niej pozna go też zakładka „Pliki". */
fun auditPhotoFileName(label: String): String {
    val safe = label.replace(Regex("[\\\\/:*?\"<>|]+"), " ").replace(Regex("\\s+"), " ").trim()
    return "${safe.ifBlank { "Zdjęcie audytu" }}.jpg"
}

/** Znacznik czasu w tym samym formacie, co reszta zapisów telefonu (UTC, ISO). */
private fun isoStamp(): String =
    java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", java.util.Locale.US)
        .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
        .format(java.util.Date())
