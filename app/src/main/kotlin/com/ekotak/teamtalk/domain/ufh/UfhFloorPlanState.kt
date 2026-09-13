package com.ekotak.teamtalk.domain.ufh

import com.ekotak.teamtalk.domain.model.UfhFloor
import com.ekotak.teamtalk.domain.model.UfhState
import com.ekotak.teamtalk.domain.model.suggestFloorNames
import com.ekotak.teamtalk.domain.model.toM2
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * POLA RZUTU KONDYGNACJI DO EDYCJI — telefon przestaje być tylko przewoźnikiem
 * `UfhFloor.planJson` i sam RYSUJE rzut w zakładce „Audyt".
 *
 * Kształt, klucze i zaokrąglenia zapisu są 1:1 z `ufhToFormData` panelu
 * (`UnderfloorHeatingAuditFields.tsx`) i serializerami `roundMark`,
 * `roundSource`, `planHistoryToJson`, `roomsToJson`, `scaleToJson` — audyt
 * zapisany z telefonu ma być w panelu nieodróżnialny od zrobionego przy biurku.
 * Klucze spoza znanego zestawu przepisujemy bez zmian ([FloorPlanState.extra]).
 *
 * Zmiany idą przez [patchFloorPlan] (port funkcji o tej samej nazwie z panelu):
 * zapis POMIARU od razu przepisuje sumy do pól metrażu, a postawienie źródła
 * ciepła zabiera je z pozostałych kondygnacji.
 */

/** Klucze warstwy rzutu w kolejności zapisu panelu. */
private val PLAN_STATE_KEYS = listOf(
    "planSlot",
    "planDocId",
    "manifoldMarks",
    "heatSource",
    "manifoldHistory",
    "rooms",
    "planScale",
    "marksSavedAt",
    "marksSavedBy",
    "areaSavedAt",
    "areaSavedBy",
)

/** Pełny stan warstwy rzutu jednej kondygnacji (`UfhFloorState` panelu bez pól metrażu). */
data class FloorPlanState(
    /** Rzut kondygnacji: ręcznie wybrany slot (`""` = automatycznie). */
    val planSlot: String = "",
    /** Id zdjęcia rzutu, do którego odnoszą się kropki i obrysy. */
    val planDocId: String = "",
    val marks: List<ManifoldMark> = emptyList(),
    /** Źródło ciepła — najwyżej jedna kondygnacja budynku ma tu wartość. */
    val heatSource: HeatSourceMark? = null,
    /** Historia zmian na rzucie (najnowsze pierwsze). */
    val history: List<PlanHistoryEntry> = emptyList(),
    val rooms: List<RoomShape> = emptyList(),
    val scale: PlanScale? = null,
    /** Kto i kiedy zapisał ostatnio miejsca rozdzielaczy. */
    val marksAuthor: PlanStamp? = null,
    /** Kto i kiedy zapisał ostatnio pomiar metrażu. */
    val areaAuthor: PlanStamp? = null,
    /** Klucze spoza zestawu panelu — przepisywane bez zmian przy zapisie. */
    val extra: JsonObject = JsonObject(emptyMap()),
)

private fun JsonObject.jsString(key: String): String {
    val p = this[key] as? JsonPrimitive ?: return ""
    return if (p is JsonNull) "" else p.content
}

/** Odczyt warstwy rzutu z surowego JSON-a (`UfhFloor.planJson`); śmieci → stan pusty. */
fun parseFloorPlanState(planJson: String?): FloorPlanState {
    val raw = planJson ?: return FloorPlanState()
    val o = runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return FloorPlanState()
    return FloorPlanState(
        planSlot = o.jsString("planSlot"),
        planDocId = o.jsString("planDocId"),
        marks = marksFromJson(o["manifoldMarks"]),
        heatSource = heatSourceFromJson(o["heatSource"]),
        history = planHistoryFromJson(o["manifoldHistory"]),
        rooms = roomsFromJson(o["rooms"]),
        scale = scaleFromJson(o["planScale"]),
        marksAuthor = stampFromJson(o["marksSavedAt"], o["marksSavedBy"]),
        areaAuthor = stampFromJson(o["areaSavedAt"], o["areaSavedBy"]),
        extra = JsonObject(o.filterKeys { it !in PLAN_STATE_KEYS }),
    )
}

/** Warstwa rzutu kondygnacji do edycji. */
fun UfhFloor.planState(): FloorPlanState = parseFloorPlanState(planJson)

private fun String?.orJsonNull(): JsonElement = if (this == null) JsonNull else JsonPrimitive(this)

/** Zapis warstwy rzutu w kształcie `ufhToFormData` panelu. */
fun floorPlanStateToJson(s: FloorPlanState): JsonObject = buildJsonObject {
    put("planSlot", s.planSlot.ifEmpty { null }.orJsonNull())
    put("planDocId", s.planDocId.ifEmpty { null }.orJsonNull())
    put("manifoldMarks", buildJsonArray { for (m in s.marks) add(markToJson(m)) })
    put("heatSource", s.heatSource?.let { heatSourceToJson(it) } ?: JsonNull)
    put("manifoldHistory", planHistoryToJson(s.history))
    put("rooms", roomsToJson(s.rooms))
    put("planScale", scaleToJson(s.scale) ?: JsonNull)
    // Puste `…SavedBy` zostaje pustym tekstem — to znak „ostempluj tę zmianę".
    put("marksSavedAt", s.marksAuthor?.at.orJsonNull())
    put("marksSavedBy", s.marksAuthor?.byName.orJsonNull())
    put("areaSavedAt", s.areaAuthor?.at.orJsonNull())
    put("areaSavedBy", s.areaAuthor?.byName.orJsonNull())
    for ((k, v) in s.extra) if (k !in PLAN_STATE_KEYS) put(k, v)
}

/** Kondygnacja z nową warstwą rzutu (pola metrażu bez zmian). */
fun UfhFloor.withPlanState(s: FloorPlanState): UfhFloor = copy(planJson = floorPlanStateToJson(s).toString())

// ── Łatka zmian ──────────────────────────────────────────────────────────────

/**
 * Jawnie podana wartość pola, które może być `null` — odróżnia „skasuj" od „bez
 * zmian" (w panelu to `null` kontra `undefined`).
 */
data class Put<out T>(val value: T)

/** Zmiana pól rzutu kondygnacji — `UfhPlanPatch` panelu; `null` = pole bez zmian. */
data class FloorPlanPatch(
    val planSlot: String? = null,
    val planDocId: String? = null,
    val marks: List<ManifoldMark>? = null,
    /** `Put(null)` = kropka skasowana; `Put(mark)` zabiera źródło z innych kondygnacji. */
    val heatSource: Put<HeatSourceMark?>? = null,
    val history: List<PlanHistoryEntry>? = null,
    val rooms: List<RoomShape>? = null,
    /** `Put(null)` = kalibracja wyczyszczona. */
    val scale: Put<PlanScale?>? = null,
    val marksAuthor: Put<PlanStamp?>? = null,
    val areaAuthor: Put<PlanStamp?>? = null,
)

/** Stan po nałożeniu łatki (`{ ...f, ...patch }`). */
fun FloorPlanState.merge(p: FloorPlanPatch): FloorPlanState = copy(
    planSlot = p.planSlot ?: planSlot,
    planDocId = p.planDocId ?: planDocId,
    marks = p.marks ?: marks,
    heatSource = if (p.heatSource != null) p.heatSource.value else heatSource,
    history = p.history ?: history,
    rooms = p.rooms ?: rooms,
    scale = if (p.scale != null) p.scale.value else scale,
    marksAuthor = if (p.marksAuthor != null) p.marksAuthor.value else marksAuthor,
    areaAuthor = if (p.areaAuthor != null) p.areaAuthor.value else areaAuthor,
)

/**
 * Przepisuje sumy z obrysów do pól metrażu. Kategoria BEZ obrysów zostaje bez
 * zmian — pomiar części pomieszczeń nie zeruje wartości wpisanych ręcznie.
 * Wartość w polu to `String(m2)` z panelu („12.5", „40").
 */
fun withMeasuredAreas(floor: UfhFloor, plan: FloorPlanState = floor.planState()): UfhFloor {
    val sums = catSums(plan.rooms, plan.scale)
    var next = floor
    for (c in MEASURE_CATS) {
        val field = c.field ?: continue
        val m2 = sums[c]?.m2 ?: continue
        next = next.withArea(field, jsNum(m2))
    }
    return next
}

/**
 * Zmiana pól rzutu kondygnacji `index` — port `patchFloorPlan` panelu.
 *  • zapis POMIARU (`rooms`/`scale`) przepisuje sumy do pól metrażu,
 *  • postawienie źródła ciepła (`heatSource = Put(mark)`) kasuje je na pozostałych
 *    kondygnacjach (skasowanie zostawia inne kondygnacje w spokoju).
 * Kondygnacje, których zmiana nie dotyczy, zostają co do bajtu (`planJson` nietknięty).
 */
fun patchFloorPlan(floors: List<UfhFloor>, index: Int, patch: FloorPlanPatch): List<UfhFloor> {
    val measured = patch.rooms != null || patch.scale != null
    val movedSource = patch.heatSource?.value != null
    return floors.mapIndexed { i, f ->
        if (i != index) {
            if (!movedSource) return@mapIndexed f
            val plan = f.planState()
            if (plan.heatSource == null) f else f.withPlanState(plan.copy(heatSource = null))
        } else {
            val merged = f.planState().merge(patch)
            val withPlan = f.withPlanState(merged)
            if (measured) withMeasuredAreas(withPlan, merged) else withPlan
        }
    }
}

/** To samo na całym stanie audytu. */
fun patchFloorPlan(state: UfhState, index: Int, patch: FloorPlanPatch): UfhState =
    state.copy(floors = patchFloorPlan(state.floors, index, patch))

/**
 * Wybór „Skrzynki rozdzielacza" z listy ustawia typ dla CAŁEJ kondygnacji
 * i czyści typy pojedynczych kropek. Id kropek zostają — pod nie podpięte są
 * przypisania pomieszczeń. Kondygnacja bez warstwy rzutu zostaje bez niej.
 */
fun withFloorBoxType(floor: UfhFloor, boxType: String): UfhFloor {
    val withType = floor.copy(boxType = boxType)
    if (floor.planJson == null) return withType
    val plan = floor.planState()
    return withType.withPlanState(plan.copy(marks = plan.marks.map { it.copy(boxType = "") }))
}

/** Rodzaj skrzynki widoczny w polu kondygnacji: wspólny albo „mieszane" z podsumowaniem. */
data class FloorBoxType(val value: String, val mixed: Boolean, val summary: String)

fun floorBoxType(floor: UfhFloor, marks: List<ManifoldMark> = floor.planState().marks): FloorBoxType {
    val types = marks.map { it.boxType.ifEmpty { floor.boxType } }
    val uniq = LinkedHashSet(types).toList()
    if (uniq.size <= 1) return FloorBoxType(uniq.firstOrNull() ?: floor.boxType, false, "")
    val summary = uniq.joinToString(", ") { t -> "${types.count { it == t }} × $t" }
    return FloorBoxType(floor.boxType, true, summary)
}

/**
 * Ile kropek rozdzielaczy wolno postawić: pole „Ilość rozdzielaczy" w granicach
 * 0–6, puste = 1. Ułamek z pola zaokrąglamy w górę (panel porównuje
 * `liczba kropek < max`, więc „2,5" wpuszcza trzecią kropkę).
 */
fun floorMaxMarks(floor: UfhFloor): Int {
    val n = floor.manifolds.trim().let { if (it.isEmpty()) 1.0 else it.toM2() ?: 1.0 }
    return ceil(max(0.0, min(6.0, n))).toInt()
}

// ── Zapisy narzędzi rzutu (1:1 z `UfhPlanLightbox.tsx`) ─────────────────────

/**
 * Zapis miejsc rozdzielaczy. Historia dostaje stan SPRZED zapisu tylko wtedy,
 * gdy kropki się przesunęły albo zmieniła się ich liczba (sam typ skrzynki — bez wpisu).
 *
 * @param planDocId rzut, na którym pracowano (id pliku ze slotu)
 * @param planSlot slot do utrwalenia: wybrany ręcznie, a bez wyboru — dopasowany automatycznie
 */
fun saveMarksPatch(
    current: FloorPlanState,
    draftMarks: List<ManifoldMark>,
    boxType: String,
    planDocId: String,
    planSlot: String,
    now: String = jsIsoNow(),
): FloorPlanPatch {
    val marks = current.marks
    val moved = marks.indices.any { i ->
        val d = draftMarks.getOrNull(i)
        d == null || abs(marks[i].x - d.x) > 1e-6 || abs(marks[i].y - d.y) > 1e-6
    }
    val changed = moved || marks.size != draftMarks.size
    val history = if (changed && marks.isNotEmpty()) {
        pushHistory(
            current.history,
            PlanHistoryEntry(
                at = now,
                kind = PlanHistoryKind.MANIFOLD,
                marks = marks,
                boxType = boxType,
                planDocId = planDocId.ifEmpty { null },
            ),
        )
    } else {
        current.history
    }
    return FloorPlanPatch(
        marks = draftMarks,
        history = history,
        marksAuthor = Put(PlanStamp(now, "")),
        planDocId = planDocId,
        planSlot = planSlot,
    )
}

/** Zapis samych typów skrzynek (bez wpisu historii — miejsca bez zmian). */
fun saveTypesPatch(draftMarks: List<ManifoldMark>): FloorPlanPatch = FloorPlanPatch(marks = draftMarks)

/** Zapis kropki źródła. Historia dostaje poprzednią kropkę — także `null` („nie było"). */
fun saveSourcePatch(
    current: FloorPlanState,
    draftSource: HeatSourceMark?,
    planDocId: String,
    planSlot: String,
    now: String = jsIsoNow(),
): FloorPlanPatch = FloorPlanPatch(
    heatSource = Put(draftSource),
    history = pushHistory(
        current.history,
        PlanHistoryEntry(
            at = now,
            kind = PlanHistoryKind.SOURCE,
            source = current.heatSource,
            planDocId = planDocId.ifEmpty { null },
        ),
    ),
    planDocId = planDocId,
    planSlot = planSlot,
)

/** Zapis pomiaru powierzchni (obrysy + skala). */
fun saveAreaPatch(
    current: FloorPlanState,
    draftRooms: List<RoomShape>,
    draftScale: PlanScale?,
    planDocId: String,
    planSlot: String,
    now: String = jsIsoNow(),
): FloorPlanPatch {
    val hadSomething = current.rooms.isNotEmpty() || scaleReady(current.scale)
    val history = if (hadSomething) {
        pushHistory(
            current.history,
            PlanHistoryEntry(
                at = now,
                kind = PlanHistoryKind.AREA,
                rooms = current.rooms,
                scale = current.scale,
                planDocId = planDocId.ifEmpty { null },
            ),
        )
    } else {
        current.history
    }
    return FloorPlanPatch(
        rooms = draftRooms,
        scale = Put(draftScale),
        history = history,
        areaAuthor = Put(PlanStamp(now, "")),
        planDocId = planDocId,
        planSlot = planSlot,
    )
}

/**
 * Wczytanie przygotowania rzutu z zakładki „Pliki" jako pomiaru kondygnacji
 * (`loadPrep` z `UfhFloorPlanMarker.tsx`). Potwierdzenie przy nadpisaniu
 * istniejącego pomiaru zostaje po stronie UI.
 */
fun loadPrepPatch(
    current: FloorPlanState,
    prep: PlanPrep,
    planDocId: String,
    planSlot: String,
    now: String = jsIsoNow(),
): FloorPlanPatch {
    val hadSomething = current.rooms.isNotEmpty() || scaleReady(current.scale)
    return FloorPlanPatch(
        rooms = prep.rooms,
        scale = Put(prep.scale),
        history = if (hadSomething) {
            pushHistory(
                current.history,
                PlanHistoryEntry(
                    at = now,
                    kind = PlanHistoryKind.AREA,
                    rooms = current.rooms,
                    scale = current.scale,
                    planDocId = planDocId,
                ),
            )
        } else {
            current.history
        },
        areaAuthor = Put(PlanStamp(now, "")),
        planDocId = planDocId,
        planSlot = planSlot,
    )
}

/**
 * Przywrócenie miejsc rozdzielaczy z historii. Przywracamy MIEJSCA, nie
 * tożsamość: R1 zostaje R1, więc id bierzemy z bieżących kropek po pozycji —
 * inaczej przypisania pomieszczeń osierociłyby się przy każdym cofnięciu.
 */
fun restoreMarks(entry: PlanHistoryEntry, currentMarks: List<ManifoldMark>, maxMarks: Int): List<ManifoldMark> =
    entry.marks.take(max(1, maxMarks)).mapIndexed { i, m ->
        m.copy(id = currentMarks.getOrNull(i)?.id ?: m.id.ifEmpty { newMarkId() })
    }

// ── Podpis autora ────────────────────────────────────────────────────────────

/**
 * Stempel autora — port `stampAuthor` z `audit-actions.ts`.
 *
 * W panelu podpis dostawia akcja serwera Next.js z sesji; telefon zapisuje audyt
 * prosto do API, które niczego nie stempluje. Bez tego każda zmiana zrobiona
 * w terenie zostałaby na zawsze „autor nieustalony". Wołać tuż przed zapisem,
 * danymi zalogowanego konta (imię i nazwisko, a bez nich login).
 *  • wpisy historii bez `byName` → `by` + `byName`,
 *  • skala bez `byName` → `byName`, a `at` tylko gdy go brak,
 *  • `marksSavedAt`/`areaSavedAt` bez podpisu → podpis.
 */
fun stampPlanAuthors(floor: UfhFloor, userId: String, userName: String, now: String = jsIsoNow()): UfhFloor {
    val raw = floor.planJson ?: return floor
    val o = runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull() ?: return floor
    val next = LinkedHashMap<String, JsonElement>(o)
    (o["manifoldHistory"] as? JsonArray)?.let { arr ->
        next["manifoldHistory"] = JsonArray(
            arr.map { entry ->
                if (entry is JsonObject && !jsTruthy(entry["byName"])) {
                    JsonObject(
                        LinkedHashMap<String, JsonElement>(entry).apply {
                            put("by", JsonPrimitive(userId))
                            put("byName", JsonPrimitive(userName))
                        },
                    )
                } else {
                    entry
                }
            },
        )
    }
    (o["planScale"] as? JsonObject)?.let { sc ->
        if (!jsTruthy(sc["byName"])) {
            next["planScale"] = JsonObject(
                LinkedHashMap<String, JsonElement>(sc).apply {
                    put("byName", JsonPrimitive(userName))
                    val at = sc["at"]
                    put("at", if (at == null || at is JsonNull) JsonPrimitive(now) else at)
                },
            )
        }
    }
    for ((atKey, byKey) in listOf("marksSavedAt" to "marksSavedBy", "areaSavedAt" to "areaSavedBy")) {
        if (jsTruthy(o[atKey]) && !jsTruthy(o[byKey])) next[byKey] = JsonPrimitive(userName)
    }
    return floor.copy(planJson = JsonObject(next).toString())
}

/** Stempel autora na wszystkich kondygnacjach audytu. */
fun stampPlanAuthors(state: UfhState, userId: String, userName: String, now: String = jsIsoNow()): UfhState =
    state.copy(floors = state.floors.map { stampPlanAuthors(it, userId, userName, now) })

/** Prawdziwość wartości JSON-a po JS-owemu (`!!v`). */
private fun jsTruthy(v: JsonElement?): Boolean {
    if (v == null || v is JsonNull) return false
    if (v !is JsonPrimitive) return true
    if (v.isString) return v.content.isNotEmpty()
    val c = v.content
    if (c == "true") return true
    if (c == "false") return false
    val n = c.toDoubleOrNull() ?: return true
    return n != 0.0 && !n.isNaN()
}

// ── Rzut kondygnacji ze slotów „Projekt domu" ────────────────────────────────

/** Wartość „rzut nie dotyczy" w selektorze audytu. */
const val NO_PLAN = "brak"

/** Nazwa bez ogonków i wielkości liter — do dopasowania kondygnacja → slot. */
private fun normName(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD)
        .replace(Regex("[\\u0300-\\u036f]"), "")
        .replace("ł", "l")
        .lowercase()
        .trim()

private val FLOOR_NO_RE = Regex("(\\d+)\\s*\\.?\\s*pietro|pietro\\s*(\\d+)")

/** Slot rzutu z NAZWY kondygnacji („Parter" → `parter`, „1. piętro" → `pietro1`); `null` = własna nazwa. */
fun slotForFloorName(name: String): String? {
    val n = normName(name)
    if (n.isEmpty()) return null
    if (n.contains("piwnic")) return "piwnica"
    if (n.contains("parter")) return "parter"
    if (n.contains("poddasz")) return "poddasze"
    if (n.contains("garaz")) return "garaz"
    FLOOR_NO_RE.find(n)?.let { m ->
        val num = m.groups[1]?.value ?: m.groups[2]?.value
        return "pietro$num"
    }
    if (n.contains("pietro")) return "pietro"
    return null
}

/**
 * Automatyczne dopasowanie rzutu do kondygnacji audytu: po nazwie, po pozycji
 * (nazwa podpowiadana regułą „Projekt domu"), a na końcu `pietro` ↔ `pietro1`.
 * Zwraca klucz slotu, dla którego istnieje plik, albo `null`.
 */
fun resolveFloorPlanSlot(
    floorName: String,
    index: Int,
    floorCount: Int,
    hasBasement: Boolean,
    available: Set<String>,
): String? {
    val byName = slotForFloorName(floorName)
    if (byName != null && available.contains(byName)) return byName

    val suggested = suggestFloorNames(floorCount, hasBasement).getOrNull(index).orEmpty()
    val byIndex = slotForFloorName(suggested)
    if (byIndex != null && available.contains(byIndex)) return byIndex

    for (key in listOf(byName, byIndex)) {
        if (key == null) continue
        val alt = when (key) {
            "pietro" -> "pietro1"
            "pietro1" -> "pietro"
            else -> null
        }
        if (alt != null && available.contains(alt)) return alt
    }
    return null
}
