package com.ekotak.teamtalk.domain.ufh

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import java.time.Instant
import kotlin.math.abs

/**
 * PRZYGOTOWANIE RZUTU — mobilny odpowiednik `plan-prep.ts` z panelu.
 *
 * Wstępna robota na rzucie kondygnacji zrobiona w zakładce „Pliki", zanim
 * ktokolwiek otworzy audyt: kalibracja skali i obrysy pomieszczeń z nazwami.
 * Audyt OP wczytuje to jednym kliknięciem i dopisuje już tylko rozstaw oraz
 * rozdzielacz.
 *
 * Dlaczego przy PLIKU, a nie w audycie: skala i obrys są cechą TEGO obrazka —
 * żyją, gdy audytu jeszcze nie ma, i nie mogą po cichu nadpisać pracy audytora
 * (dziedziczenie jest świadomym kliknięciem). Zapis idzie do swobodnego JSON-a
 * `DealDocument.planData`.
 *
 * To JEDYNE miejsce, w którym telefon ZAPISUJE geometrię rzutu — dotąd wyłącznie
 * ją czytał (patrz `UfhPlan.kt`). Dlatego serializacja jest tu przepisana co do
 * zaokrąglenia: cztery miejsca po przecinku na punkt, jedno na centymetry skali,
 * jak `roundPoint`/`scaleToJson` w panelu. Inne zaokrąglenie znaczyłoby inne
 * metraże po obu stronach tej samej oferty.
 */

/** Znacznik treści — `planData` jest swobodne, więc mówimy wprost, co to jest. */
const val PLAN_PREP_KIND = "ufh-plan-prep"

/** Ile wpisów historii trzymamy (limit panelu — `PLAN_HISTORY_MAX`). */
private const val PLAN_HISTORY_MAX = 20

/**
 * Wpis historii przygotowania: stan SPRZED zapisu, żeby dało się wrócić.
 * Telefon tworzy wyłącznie wpisy rodzaju `area`, ale cudze (`manifold`,
 * `source`) musi oddać nietknięte — dlatego trzyma ich surowy JSON w [raw].
 */
data class PlanPrepHistoryEntry(
    val at: String,
    val kind: String,
    val rooms: List<RoomShape> = emptyList(),
    val scale: PlanScale? = null,
    /** Postać, w jakiej wpis przyszedł z serwera; `null` dla wpisów świeżych. */
    val raw: JsonObject? = null,
)

/** Przygotowanie rzutu zapisane na pliku. */
data class PlanPrep(
    val scale: PlanScale? = null,
    val rooms: List<RoomShape> = emptyList(),
    val history: List<PlanPrepHistoryEntry> = emptyList(),
    /** Podpis ostatniego zapisu — autora dostawia serwer, gdy zostawimy puste. */
    val savedAt: String = "",
    val savedBy: String = "",
)

/**
 * Odczyt `DealDocument.planData`. Obca albo uszkodzona treść → `null`, dokładnie
 * jak `parsePlanPrep` panelu: zapis bez skali I bez obrysów nie jest
 * przygotowaniem, tylko śmieciem po innym narzędziu.
 */
fun parsePlanPrep(v: JsonElement?): PlanPrep? {
    val o = v as? JsonObject ?: return null
    val kind = o.strOrEmpty("kind")
    if (kind.isNotEmpty() && kind != PLAN_PREP_KIND) return null
    val rooms = roomsFromJson(o["rooms"])
    val scale = scaleFromJson(o["scale"])
    if (rooms.isEmpty() && scale == null) return null
    return PlanPrep(
        scale = scale,
        rooms = rooms,
        history = historyFromJson(o["history"]),
        savedAt = o.strOrEmpty("savedAt"),
        savedBy = o.strOrEmpty("savedBy"),
    )
}

private fun historyFromJson(v: JsonElement?): List<PlanPrepHistoryEntry> {
    val arr = v as? JsonArray ?: return emptyList()
    val out = ArrayList<PlanPrepHistoryEntry>()
    for (el in arr) {
        val o = el as? JsonObject ?: continue
        // Brak `kind` = zapis sprzed pomiaru metrażu, czyli miejsca rozdzielaczy.
        val kind = o.strOrEmpty("kind").ifEmpty { "manifold" }
        if (kind == "area") {
            val rooms = roomsFromJson(o["rooms"])
            if (rooms.isEmpty()) continue
            out += PlanPrepHistoryEntry(
                at = o.strOrEmpty("at"),
                kind = kind,
                rooms = rooms,
                scale = scaleFromJson(o["scale"]),
                raw = o,
            )
        } else {
            out += PlanPrepHistoryEntry(at = o.strOrEmpty("at"), kind = kind, raw = o)
        }
        if (out.size >= PLAN_HISTORY_MAX) break
    }
    return out
}

/**
 * Treść do `PATCH /api/documents/:id/plan-data`. Puste `savedBy` zostawiamy
 * świadomie: serwer panelu stempluje podpis kontem z sesji, więc autor bierze
 * się z zalogowania, a nie z tego, co poda telefon.
 */
fun planPrepToJson(prep: PlanPrep): JsonObject = buildJsonObject {
    put("kind", JsonPrimitive(PLAN_PREP_KIND))
    put("version", JsonPrimitive(1))
    put("scale", scaleToJson(prep.scale) ?: JsonNull)
    put("rooms", roomsToJson(prep.rooms))
    put("history", historyToJson(prep.history))
    put("savedAt", JsonPrimitive(prep.savedAt.ifBlank { nowIso() }))
    put("savedBy", prep.savedBy.ifBlank { null }?.let { JsonPrimitive(it) } ?: JsonNull)
}

private fun historyToJson(history: List<PlanPrepHistoryEntry>): JsonArray = buildJsonArray {
    for (entry in history.take(PLAN_HISTORY_MAX)) {
        if (entry.kind != "area") {
            // Cudzy rodzaj wpisu (rozdzielacze, źródło ciepła) — przepisujemy
            // dokładnie to, co przyszło; telefon takich wpisów nie rozumie.
            entry.raw?.let { add(it) }
            continue
        }
        add(
            buildJsonObject {
                put("at", JsonPrimitive(entry.at))
                put("kind", JsonPrimitive("area"))
                // Podpis autora i id rzutu przepisujemy z wpisu — historia jest
                // wspólna z panelem i nie wolno jej po drodze okroić.
                put("by", entry.raw?.get("by") ?: JsonNull)
                put("byName", entry.raw?.get("byName") ?: JsonNull)
                put("planDocId", entry.raw?.get("planDocId") ?: JsonNull)
                put("rooms", roomsToJson(entry.rooms))
                put("scale", scaleToJson(entry.scale) ?: JsonNull)
            },
        )
    }
}

/** Nowy wpis na czele listy, z przycięciem do limitu (`pushHistory` panelu). */
fun pushPrepHistory(
    history: List<PlanPrepHistoryEntry>,
    entry: PlanPrepHistoryEntry,
): List<PlanPrepHistoryEntry> = (listOf(entry) + history).take(PLAN_HISTORY_MAX)

/** Czy jest cokolwiek do zapisania — pusty rzut KASUJE przygotowanie. */
fun prepEmpty(prep: PlanPrep): Boolean = prep.rooms.isEmpty() && !scaleReady(prep.scale)

/** Suma powierzchni obrysów [m²]; `null` bez kalibracji skali. */
fun prepM2(prep: PlanPrep): Double? {
    if (!scaleReady(prep.scale)) return null
    var total = 0.0
    for (room in prep.rooms) total += roomM2(room, prep.scale) ?: 0.0
    return jsRound(total * 10) / 10
}

/** Jednolinijkowe podsumowanie do stopki slotu („7 pom. · 84,2 m²"). */
fun prepSummary(prep: PlanPrep?): String {
    if (prep == null) return ""
    val m2 = prepM2(prep)
    val second = when {
        m2 != null -> "${m2.pl()} m²"
        scaleReady(prep.scale) -> "skala ${prep.scale!!.cm.pl()} cm"
        else -> "bez skali"
    }
    return "${prep.rooms.size} pom. · $second"
}

/** Liczba po polsku — przecinek dziesiętny, bez zbędnego „.0". */
internal fun Double.pl(): String =
    (if (this == toLong().toDouble()) toLong().toString() else toString()).replace('.', ',')

// ── Zapis geometrii ──────────────────────────────────────────────────────────

/** Punkt do zapisu — 4 miejsca po przecinku (precyzja piksela, czytelny JSON). */
private fun pointToJson(p: PlanPoint): JsonObject = buildJsonObject {
    put("x", JsonPrimitive(jsRound(p.x * 10000) / 10000))
    put("y", JsonPrimitive(jsRound(p.y * 10000) / 10000))
}

private fun pointsToJson(points: List<PlanPoint>): JsonArray = buildJsonArray {
    for (p in points) add(pointToJson(p))
}

/** `null`, gdy kalibracja jest niepełna — tak samo jak `scaleToJson` panelu. */
fun scaleToJson(s: PlanScale?): JsonObject? {
    if (!scaleReady(s)) return null
    val scale = s!!
    return buildJsonObject {
        put("a", pointToJson(scale.a))
        put("b", pointToJson(scale.b))
        put("cm", JsonPrimitive(jsRound(scale.cm * 10) / 10))
        put("aspect", JsonPrimitive(jsRound(scale.aspect * 10000) / 10000))
        put("planDocId", scale.planDocId.ifBlank { null }?.let { JsonPrimitive(it) } ?: JsonNull)
        put("at", scale.at.ifBlank { null }?.let { JsonPrimitive(it) } ?: JsonNull)
        put("byName", scale.byName.ifBlank { null }?.let { JsonPrimitive(it) } ?: JsonNull)
    }
}

fun roomsToJson(rooms: List<RoomShape>): JsonArray = buildJsonArray {
    for (room in rooms.take(ROOMS_MAX)) {
        add(
            buildJsonObject {
                put("id", JsonPrimitive(room.id))
                put(
                    "name",
                    room.name.trim().ifBlank { null }?.let { JsonPrimitive(it) } ?: JsonNull,
                )
                put("cat", JsonPrimitive(room.cat.key))
                put("outline", pointsToJson(room.outline))
                put("holes", buildJsonArray { for (hole in room.holes) add(pointsToJson(hole)) })
                put(
                    "patches",
                    buildJsonArray {
                        for (patch in room.patches.take(PATCHES_MAX)) {
                            add(
                                buildJsonObject {
                                    put("id", JsonPrimitive(patch.id))
                                    put("cat", JsonPrimitive(patch.cat.key))
                                    put("outline", pointsToJson(patch.outline))
                                },
                            )
                        }
                    },
                )
                put(
                    "manifoldId",
                    room.manifoldId.ifBlank { null }?.let { JsonPrimitive(it) } ?: JsonNull,
                )
            },
        )
    }
}

// ── Porównania ───────────────────────────────────────────────────────────────

private fun samePoints(a: List<PlanPoint>, b: List<PlanPoint>): Boolean =
    a.size == b.size && a.indices.all {
        abs(a[it].x - b[it].x) < 1e-6 && abs(a[it].y - b[it].y) < 1e-6
    }

/** Równe obrysy — decyduje, czy jest co zapisywać i czy odkładać wpis historii. */
fun sameRooms(a: List<RoomShape>, b: List<RoomShape>): Boolean =
    a.size == b.size && a.indices.all { i ->
        val x = a[i]
        val y = b[i]
        x.cat == y.cat &&
            x.name.trim() == y.name.trim() &&
            x.manifoldId == y.manifoldId &&
            samePoints(x.outline, y.outline) &&
            x.holes.size == y.holes.size &&
            x.holes.indices.all { samePoints(x.holes[it], y.holes[it]) } &&
            x.patches.size == y.patches.size &&
            x.patches.indices.all {
                x.patches[it].cat == y.patches[it].cat &&
                    samePoints(x.patches[it].outline, y.patches[it].outline)
            }
    }

fun sameScale(a: PlanScale?, b: PlanScale?): Boolean {
    if (a == null || b == null) return a === b || (!scaleReady(a) && !scaleReady(b))
    return abs(a.cm - b.cm) < 1e-6 &&
        abs(a.a.x - b.a.x) < 1e-6 &&
        abs(a.a.y - b.a.y) < 1e-6 &&
        abs(a.b.x - b.b.x) < 1e-6 &&
        abs(a.b.y - b.b.y) < 1e-6
}

/** Nowe id obrysu — unikalne w obrębie sesji, jak `newRoomId` panelu. */
fun newRoomId(): String = "r${System.currentTimeMillis().toString(36)}${idSeq()}"

/** Nowe id łatki zagęszczenia — jak [newRoomId], innym prefiksem. */
fun newPatchId(): String = "p${System.currentTimeMillis().toString(36)}${idSeq()}"

private var seq = 0

private fun idSeq(): String {
    seq += 1
    return seq.toString(36)
}

internal fun nowIso(): String = Instant.now().toString()
