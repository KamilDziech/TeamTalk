package com.ekotak.teamtalk.domain.ufh

import com.ekotak.teamtalk.domain.model.UfhAreaField
import com.ekotak.teamtalk.domain.model.UfhFloor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.hypot

/**
 * WARSTWA RZUTU KONDYGNACJI — mobilny odpowiednik `ufh-area-measure.ts`
 * i `ufh-manifold-marks.ts` z panelu.
 *
 * Telefon rzutu NIE RYSUJE (patrz `UfhAudit.kt`) — obrysy pomieszczeń, kropki
 * rozdzielaczy i kalibracja skali przechodzą przez niego nietknięte, jako surowy
 * JSON w `UfhFloor.planJson`. Zakładka „Oferta" musi je jednak PRZECZYTAĆ:
 * metraż zmierzony na rzucie ma w panelu pierwszeństwo przed polami wpisanymi
 * ręcznie, a długość rury liczy się od rozdzielacza. Bez tego telefon
 * pokazywałby klientowi inne liczby niż panel — a to ta sama oferta.
 *
 * Geometria idzie 1:1 za panelem: punkty są WZGLĘDNE (0–1 wobec szerokości
 * i wysokości obrazu), a jedyną informacją o rzucie, jakiej potrzebuje rachunek,
 * jest proporcja `aspect`. `y` przeliczamy na jednostki szerokości (`y / aspect`)
 * i liczymy w nich — wynik nie zależy od tego, jak duży jest obraz.
 */

/** Punkt na rzucie — współrzędne względne 0–1. */
data class PlanPoint(val x: Double, val y: Double)

/**
 * Kategoria metrażu = jedno z pól metrażu kondygnacji. Kolejność i wartości
 * 1:1 z `AREA_CATS` panelu — nowa kategoria musi wejść w obu miejscach naraz,
 * inaczej rozjadą się sumy powierzchni.
 */
enum class AreaCat(
    val key: String,
    val label: String,
    val short: String,
    /** Pole metrażu kondygnacji; `null` = kategoria bez pola (rozstaw do ustalenia). */
    val field: UfhAreaField?,
    /** Rozstaw rur [m]; `null` = powierzchnia bez pola grzewczego. */
    val spacingM: Double?,
) {
    TODO_CAT("todo", "rozstaw do ustalenia", "do ustalenia", null, null),
    NONE("none", "bez ogrzewania podłogowego", "bez OP", UfhAreaField.NO_UFH, null),
    LEAD("lead", "przestrzeń na dobiegi rur do pomieszczeń", "dobiegi", UfhAreaField.LEAD_IN, null),
    S5("s5", "rury co 5 cm", "5 cm", UfhAreaField.S5, 0.05),
    S10("s10", "rury co 10 cm", "10 cm", UfhAreaField.S10, 0.10),
    S15("s15", "rury co 15 cm", "15 cm", UfhAreaField.S15, 0.15),
    S20("s20", "rury co 20 cm", "20 cm", UfhAreaField.S20, 0.20);

    companion object {
        fun fromKey(raw: String?): AreaCat? = entries.firstOrNull { it.key == raw }
    }
}

/** Kategorie z polem metrażu I rozstawem — z nich składa się powierzchnia OP. */
val SPACING_CATS: List<AreaCat> = AreaCat.entries.filter { it.field != null && it.spacingM != null }

/**
 * Kalibracja skali rzutu: odcinek o znanej długości.
 *
 * Trzy ostatnie pola są czystym podpisem panelu — do rachunku niepotrzebne,
 * ale telefon też ZAPISUJE skalę (przygotowanie rzutu w zakładce „Pliki"),
 * więc musi je oddać nietknięte, zamiast kasować autora czyjejś kalibracji.
 */
data class PlanScale(
    val a: PlanPoint,
    val b: PlanPoint,
    /** Długość odcinka w rzeczywistości [cm]. */
    val cm: Double,
    /** Proporcja obrazu (szerokość / wysokość) z momentu kalibracji. */
    val aspect: Double,
    /** Rzut, na którym kalibrowano — panel ostrzega po podmianie pliku. */
    val planDocId: String = "",
    /** Kiedy i kto kalibrował (podpis w panelu; puste = dostawi serwer). */
    val at: String = "",
    val byName: String = "",
)

/** Łatka zagęszczenia — fragment pomieszczenia ułożony innym rozstawem. */
data class AreaPatch(val id: String, val cat: AreaCat, val outline: List<PlanPoint>)

/** Obwiedziony wielobok = jedno pomieszczenie (albo jego fragment). */
data class RoomShape(
    val id: String,
    val name: String,
    val cat: AreaCat,
    val outline: List<PlanPoint>,
    val holes: List<List<PlanPoint>> = emptyList(),
    val patches: List<AreaPatch> = emptyList(),
    /** Rozdzielacz zasilający pomieszczenie (`ManifoldMark.id`); puste = nieprzypisane. */
    val manifoldId: String = "",
)

/** Kropka rozdzielacza na rzucie. */
data class ManifoldMark(
    val x: Double,
    val y: Double,
    val id: String,
    /** Rodzaj skrzynki TEJ kropki; puste → typ z pola kondygnacji. */
    val boxType: String = "",
) {
    val point: PlanPoint get() = PlanPoint(x, y)
}

/** Warstwa rzutu jednej kondygnacji odczytana z `UfhFloor.planJson`. */
data class FloorPlan(
    val rooms: List<RoomShape> = emptyList(),
    val scale: PlanScale? = null,
    val marks: List<ManifoldMark> = emptyList(),
)

/** Ile pomieszczeń i łatek przyjmujemy z zapisu — zapory z panelu. */
internal const val ROOMS_MAX = 40
internal const val PATCHES_MAX = 12

private val planJson = Json { ignoreUnknownKeys = true; isLenient = true }

/**
 * Zaokrąglenie „jak w JavaScripcie" (`Math.round`) — połówka zawsze w górę.
 * `kotlin.math.round` przy wartościach ujemnych idzie w drugą stronę, a my
 * porównujemy wyniki z panelem co do dziesiątej.
 */
internal fun jsRound(v: Double): Double = floor(v + 0.5)

internal fun r1(v: Double): Double = jsRound(v * 10) / 10

internal fun r2(v: Double): Double = jsRound(v * 100) / 100

internal fun clamp01(v: Double): Double = minOf(1.0, maxOf(0.0, v))

internal fun JsonObject.numOrNull(key: String): Double? =
    (this[key] as? JsonPrimitive)?.let { p ->
        p.doubleOrNull ?: p.content.trim().replace(',', '.').toDoubleOrNull()
    }

internal fun JsonObject.strOrEmpty(key: String): String =
    (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()

internal fun pointsFromJson(v: kotlinx.serialization.json.JsonElement?): List<PlanPoint> {
    val arr = v as? JsonArray ?: return emptyList()
    return arr.mapNotNull { el ->
        val o = el as? JsonObject ?: return@mapNotNull null
        val x = o.numOrNull("x") ?: return@mapNotNull null
        val y = o.numOrNull("y") ?: return@mapNotNull null
        PlanPoint(clamp01(x), clamp01(y))
    }
}

internal fun scaleFromJson(v: kotlinx.serialization.json.JsonElement?): PlanScale? {
    val o = v as? JsonObject ?: return null
    val a = (o["a"] as? JsonObject)?.let { p ->
        val x = p.numOrNull("x") ?: return@let null
        val y = p.numOrNull("y") ?: return@let null
        PlanPoint(clamp01(x), clamp01(y))
    } ?: return null
    val b = (o["b"] as? JsonObject)?.let { p ->
        val x = p.numOrNull("x") ?: return@let null
        val y = p.numOrNull("y") ?: return@let null
        PlanPoint(clamp01(x), clamp01(y))
    } ?: return null
    val cm = o.numOrNull("cm") ?: return null
    val aspect = o.numOrNull("aspect") ?: return null
    if (cm <= 0 || aspect <= 0) return null
    return PlanScale(
        a = a,
        b = b,
        cm = cm,
        aspect = aspect,
        planDocId = o.strOrEmpty("planDocId"),
        at = o.strOrEmpty("at"),
        byName = o.strOrEmpty("byName"),
    )
}

private fun patchesFromJson(v: kotlinx.serialization.json.JsonElement?): List<AreaPatch> {
    val arr = v as? JsonArray ?: return emptyList()
    val out = ArrayList<AreaPatch>()
    for ((i, el) in arr.withIndex()) {
        val o = el as? JsonObject ?: continue
        val outline = pointsFromJson(o["outline"])
        if (outline.size < 3) continue
        out += AreaPatch(
            id = o.strOrEmpty("id").ifEmpty { "p$i" },
            cat = AreaCat.fromKey(o.strOrEmpty("cat")) ?: AreaCat.NONE,
            outline = outline,
        )
        if (out.size >= PATCHES_MAX) break
    }
    return out
}

internal fun roomsFromJson(v: kotlinx.serialization.json.JsonElement?): List<RoomShape> {
    val arr = v as? JsonArray ?: return emptyList()
    val out = ArrayList<RoomShape>()
    for ((i, el) in arr.withIndex()) {
        val o = el as? JsonObject ?: continue
        val outline = pointsFromJson(o["outline"])
        if (outline.size < 3) continue
        val holes = (o["holes"] as? JsonArray)
            ?.map { pointsFromJson(it) }
            ?.filter { it.size >= 3 }
            .orEmpty()
        out += RoomShape(
            id = o.strOrEmpty("id").ifEmpty { "r$i" },
            name = o.strOrEmpty("name"),
            cat = AreaCat.fromKey(o.strOrEmpty("cat")) ?: AreaCat.NONE,
            outline = outline,
            holes = holes,
            patches = patchesFromJson(o["patches"]),
            manifoldId = o.strOrEmpty("manifoldId").trim(),
        )
        if (out.size >= ROOMS_MAX) break
    }
    return out
}

/**
 * Kropki rozdzielaczy. Kropki bez `id` (zapisy sprzed przypisywania pomieszczeń)
 * dostają je DETERMINISTYCZNIE z pozycji (`m1`, `m2`…), dokładnie jak w panelu —
 * inaczej przypisanie pomieszczenia do rozdzielacza przeskoczyłoby na cudzą
 * kropkę i dobieg policzyłby się od innej skrzynki.
 */
private fun marksFromJson(v: kotlinx.serialization.json.JsonElement?): List<ManifoldMark> {
    val arr = v as? JsonArray ?: return emptyList()
    val out = ArrayList<ManifoldMark>()
    val used = HashSet<String>()
    for (el in arr) {
        val o = el as? JsonObject ?: continue
        val x = o.numOrNull("x") ?: continue
        val y = o.numOrNull("y") ?: continue
        val raw = o.strOrEmpty("id").trim()
        val fallback = "m${out.size + 1}"
        val id = when {
            raw.isNotEmpty() && !used.contains(raw) -> raw
            !used.contains(fallback) -> fallback
            else -> "mk${out.size + 1}"
        }
        used += id
        out += ManifoldMark(clamp01(x), clamp01(y), id, o.strOrEmpty("boxType"))
    }
    return out
}

/**
 * Warstwa rzutu kondygnacji. Zapis panelu trzyma ją pod kluczami `rooms`,
 * `planScale` i `manifoldMarks` — telefon przenosi je hurtem (`planJson`),
 * a tutaj rozpakowuje na potrzeby rachunku.
 */
fun UfhFloor.plan(): FloorPlan {
    val raw = planJson ?: return FloorPlan()
    val o = runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull()
        ?: return FloorPlan()
    return FloorPlan(
        rooms = roomsFromJson(o["rooms"]),
        scale = scaleFromJson(o["planScale"]),
        marks = marksFromJson(o["manifoldMarks"]),
    )
}

// ── geometria ────────────────────────────────────────────────────────────────

/** Y w jednostkach szerokości obrazu. */
private fun uy(y: Double, aspect: Double): Double = if (aspect > 0) y / aspect else y

/** Punkt przeliczony na jednostki szerokości obrazu. */
fun unitPoint(p: PlanPoint, aspect: Double): PlanPoint = PlanPoint(p.x, uy(p.y, aspect))

/** Długość odcinka w jednostkach szerokości obrazu. */
fun segLen(a: PlanPoint, b: PlanPoint, aspect: Double): Double =
    hypot(b.x - a.x, uy(b.y, aspect) - uy(a.y, aspect))

/** Czy skala jest kompletna i sensowna (bez tego pomiar jest zablokowany). */
fun scaleReady(s: PlanScale?): Boolean {
    if (s == null) return false
    if (s.cm <= 0 || s.aspect <= 0) return false
    return segLen(s.a, s.b, s.aspect) > 1e-4
}

/** Ile centymetrów na jednostkę szerokości obrazu. */
fun cmPerUnit(s: PlanScale): Double = s.cm / segLen(s.a, s.b, s.aspect)

/** Pole wieloboku (wzór Gaussa) w jednostkach² szerokości obrazu. */
private fun polyArea(points: List<PlanPoint>, aspect: Double): Double {
    if (points.size < 3) return 0.0
    var sum = 0.0
    for (i in points.indices) {
        val p = points[i]
        val q = points[(i + 1) % points.size]
        sum += p.x * uy(q.y, aspect) - q.x * uy(p.y, aspect)
    }
    return abs(sum) / 2
}

/** Zaokrąglenie metrażu do 1 cm² — jeden format dla pól i łatek. */
private fun m2FromArea(area: Double, k: Double): Double = jsRound(area * k * k / 10000 * 100) / 100

/** Powierzchnia CAŁEGO pomieszczenia [m²] — obrys minus wycięcia, z łatkami. */
fun roomM2(room: RoomShape, scale: PlanScale?): Double? {
    if (!scaleReady(scale)) return null
    val s = scale!!
    val outline = polyArea(room.outline, s.aspect)
    if (outline <= 0) return null
    val holes = room.holes.sumOf { polyArea(it, s.aspect) }
    return m2FromArea(maxOf(0.0, outline - holes), cmPerUnit(s))
}

/** Powierzchnia łatki zagęszczenia [m²]. */
fun patchM2(patch: AreaPatch, scale: PlanScale?): Double? {
    if (!scaleReady(scale)) return null
    val s = scale!!
    val area = polyArea(patch.outline, s.aspect)
    if (area <= 0) return null
    return m2FromArea(area, cmPerUnit(s))
}

/** Suma powierzchni łatek pomieszczenia [m²] (wszystkich kategorii). */
fun patchesM2(room: RoomShape, scale: PlanScale?): Double =
    room.patches.sumOf { patchM2(it, scale) ?: 0.0 }

/** Powierzchnia pomieszczenia w JEGO rozstawie [m²] — bez łatek zagęszczenia. */
fun roomBaseM2(room: RoomShape, scale: PlanScale?): Double? {
    val total = roomM2(room, scale) ?: return null
    return maxOf(0.0, jsRound((total - patchesM2(room, scale)) * 100) / 100)
}

/** Ile wieloboków (pomieszczeń + łatek) należy do kategorii. */
private fun catCount(rooms: List<RoomShape>, cat: AreaCat): Int {
    var n = 0
    for (r in rooms) {
        if (r.cat == cat) n += 1
        n += r.patches.count { it.cat == cat }
    }
    return n
}

/**
 * Suma powierzchni [m²] danej kategorii: pomieszczenia tej kategorii (bez swoich
 * łatek) + łatki tej kategorii z dowolnego pomieszczenia.
 * `null` = brak skali albo nic w tej kategorii.
 */
fun catM2(rooms: List<RoomShape>, scale: PlanScale?, cat: AreaCat): Double? {
    if (!scaleReady(scale) || catCount(rooms, cat) == 0) return null
    var t = 0.0
    for (r in rooms) {
        if (r.cat == cat) t += roomBaseM2(r, scale) ?: 0.0
        for (p in r.patches) if (p.cat == cat) t += patchM2(p, scale) ?: 0.0
    }
    return jsRound(t * 10) / 10
}

/** Czy punkt leży wewnątrz obrysu (ray casting). */
fun pointInPoly(p: PlanPoint, poly: List<PlanPoint>): Boolean {
    var inside = false
    var j = poly.size - 1
    for (i in poly.indices) {
        val a = poly[i]
        val b = poly[j]
        val straddles = (a.y > p.y) != (b.y > p.y)
        if (straddles && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x) inside = !inside
        j = i
    }
    return inside
}

/** Podpis wieloboku („Pom. 3", gdy bez nazwy). */
fun roomLabel(room: RoomShape, index: Int): String =
    room.name.trim().ifEmpty { "Pom. ${index + 1}" }

/** Pozycja kropki o danym id (−1 = już jej nie ma). */
fun markIndexById(marks: List<ManifoldMark>, id: String?): Int {
    if (id.isNullOrBlank()) return -1
    return marks.indexOfFirst { it.id == id }
}

/** Efektywny rodzaj skrzynki kropki (własny albo odziedziczony z kondygnacji). */
fun markBoxType(m: ManifoldMark, floorBoxType: String): String =
    m.boxType.ifEmpty { floorBoxType }
