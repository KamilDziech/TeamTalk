package com.ekotak.teamtalk.domain.ufh

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * HISTORIA ZMIAN NA RZUCIE kondygnacji — port `ufh-plan-history.ts`.
 *
 * JEDEN strumień wpisów dla trzech narzędzi: miejsc rozdzielaczy (`manifold`),
 * pomiaru powierzchni (`area`) i źródła ciepła (`source`). Wpis odkłada stan
 * SPRZED zapisu, więc da się wrócić. Zapis idzie do
 * `formData.floors[i].manifoldHistory`; stare wpisy bez `kind` to `manifold`.
 *
 * Historię przygotowania rzutu przy PLIKU (`PlanPrep.kt`) trzyma osobny typ —
 * tam telefon tworzy wyłącznie wpisy `area`, a tu pełny zestaw.
 */

/** Ile wpisów historii trzymamy w `formData` (starsze wypadają) — `PLAN_HISTORY_MAX` panelu. */
const val UFH_PLAN_HISTORY_MAX = 20

/**
 * Podpis ostatniego zapisu narzędzia. `byName` puste = „ostempluj z sesji" —
 * patrz [stampPlanAuthors], bo telefon zapisuje do API bez pośrednictwa panelu.
 */
data class PlanStamp(val at: String, val byName: String)

/** Świeży podpis (autora dostawia stempel przy zapisie). */
fun newStamp(): PlanStamp = PlanStamp(at = jsIsoNow(), byName = "")

fun stampFromJson(at: JsonElement?, byName: JsonElement?): PlanStamp? {
    val a = (at as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()
    if (a.isEmpty()) return null
    return PlanStamp(a, (byName as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty())
}

/** „Jan Kowalski · 17.08.2026, 09:12" — podpis pod miniaturą. */
fun stampLabel(s: PlanStamp?): String {
    if (s == null) return ""
    return "${s.byName.trim().ifEmpty { "autor nieustalony" }} · ${fmtWhen(s.at)}"
}

enum class PlanHistoryKind(val key: String) { MANIFOLD("manifold"), AREA("area"), SOURCE("source") }

data class PlanHistoryEntry(
    /** Znacznik czasu zapisu (ISO). */
    val at: String,
    val kind: PlanHistoryKind,
    /** Autor zapisu — id użytkownika (`null` = brak). */
    val by: String? = null,
    /** Autor zapisu — imię i nazwisko. */
    val byName: String? = null,
    /** Id zdjęcia rzutu, którego dotyczył zapis. */
    val planDocId: String? = null,
    /** `MANIFOLD` — poprzednie położenia rozdzielaczy. */
    val marks: List<ManifoldMark> = emptyList(),
    /** `MANIFOLD` — rodzaj skrzynki kondygnacji (fallback koloru kropek). */
    val boxType: String? = null,
    /** `AREA` — poprzednie obrysy pomieszczeń. */
    val rooms: List<RoomShape> = emptyList(),
    /** `AREA` — kalibracja skali z tego zapisu. */
    val scale: PlanScale? = null,
    /** `SOURCE` — poprzednia kropka źródła; `null` = „źródła tu nie było" (pełnoprawny wpis). */
    val source: HeatSourceMark? = null,
)

/** Podpis autora — nigdy pusty. */
fun authorLabel(e: PlanHistoryEntry): String = e.byName?.trim().orEmpty().ifEmpty { "autor nieustalony" }

private val WHEN_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy, HH:mm")

/** Data po polsku w strefie telefonu („17.08.2026, 09:12"); nieczytelna → surowy tekst. */
fun fmtWhen(at: String, zone: ZoneId = ZoneId.systemDefault()): String =
    runCatching { WHEN_FORMAT.format(Instant.parse(at).atZone(zone)) }.getOrDefault(at)

/** Nagłówek wpisu w liście historii. */
fun entryTitle(e: PlanHistoryEntry): String = when (e.kind) {
    PlanHistoryKind.AREA -> "pomiar powierzchni"
    PlanHistoryKind.SOURCE -> "źródło ciepła"
    PlanHistoryKind.MANIFOLD -> "miejsce rozdzielacza"
}

/** Druga linia wpisu: co ten zapis zawierał. */
fun entrySummary(e: PlanHistoryEntry): String = when (e.kind) {
    PlanHistoryKind.AREA -> if (e.rooms.size == 1) "1 pomieszczenie" else "${e.rooms.size} pomieszczeń"
    PlanHistoryKind.SOURCE -> if (e.source != null) heatSourceLabel(e.source) else "źródło nie było zaznaczone"
    PlanHistoryKind.MANIFOLD -> "${e.marks.size} × rozdzielacz"
}

private fun JsonObject.strField(key: String): String =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()

/** Odczyt historii (wpisy bez treści pomijamy). */
fun planHistoryFromJson(v: JsonElement?): List<PlanHistoryEntry> {
    val arr = v as? JsonArray ?: return emptyList()
    val out = ArrayList<PlanHistoryEntry>()
    for (el in arr) {
        val o = el as? JsonObject ?: JsonObject(emptyMap())
        val at = o.strField("at")
        val by = o.strField("by").ifEmpty { null }
        val byName = o.strField("byName").ifEmpty { null }
        val planDocId = o.strField("planDocId").ifEmpty { null }
        val kind = o.strField("kind")
        if (kind == "source") {
            out += PlanHistoryEntry(
                at = at,
                kind = PlanHistoryKind.SOURCE,
                by = by,
                byName = byName,
                planDocId = planDocId,
                source = heatSourceFromJson(o["source"]),
            )
            if (out.size >= UFH_PLAN_HISTORY_MAX) break
            continue
        }
        if (kind == "area") {
            val rooms = roomsFromJson(o["rooms"])
            if (rooms.isEmpty()) continue
            out += PlanHistoryEntry(
                at = at,
                kind = PlanHistoryKind.AREA,
                by = by,
                byName = byName,
                planDocId = planDocId,
                rooms = rooms,
                scale = scaleFromJson(o["scale"]),
            )
            continue
        }
        // Brak `kind` (albo nieznany) = zapis sprzed pomiaru metrażu → miejsca rozdzielaczy.
        val marks = marksFromJson(o["marks"])
        if (marks.isEmpty()) continue
        out += PlanHistoryEntry(
            at = at,
            kind = PlanHistoryKind.MANIFOLD,
            by = by,
            byName = byName,
            planDocId = planDocId,
            marks = marks,
            boxType = o.strField("boxType").ifEmpty { null },
        )
        if (out.size >= UFH_PLAN_HISTORY_MAX) break
    }
    return out.take(UFH_PLAN_HISTORY_MAX)
}

private fun String?.jsonOrNull(): JsonElement = if (this == null) JsonNull else JsonPrimitive(this)

/** Zapis historii w kształcie panelu (`planHistoryToJson`). */
fun planHistoryToJson(history: List<PlanHistoryEntry>): JsonArray = buildJsonArray {
    for (e in history.take(UFH_PLAN_HISTORY_MAX)) {
        add(
            buildJsonObject {
                put("at", JsonPrimitive(e.at))
                put("kind", JsonPrimitive(e.kind.key))
                put("by", e.by.jsonOrNull())
                put("byName", e.byName.jsonOrNull())
                put("planDocId", e.planDocId.jsonOrNull())
                when (e.kind) {
                    PlanHistoryKind.SOURCE -> put("source", e.source?.let { heatSourceToJson(it) } ?: JsonNull)
                    PlanHistoryKind.AREA -> {
                        put("rooms", roomsToJson(e.rooms))
                        put("scale", scaleToJson(e.scale) ?: JsonNull)
                    }
                    PlanHistoryKind.MANIFOLD -> {
                        put("marks", buildJsonArray { for (m in e.marks) add(markToJson(m)) })
                        put("boxType", e.boxType.jsonOrNull())
                    }
                }
            },
        )
    }
}

/** Nowy wpis na czele listy (z przycięciem do limitu). */
fun pushHistory(history: List<PlanHistoryEntry>, entry: PlanHistoryEntry): List<PlanHistoryEntry> =
    (listOf(entry) + history).take(UFH_PLAN_HISTORY_MAX)
