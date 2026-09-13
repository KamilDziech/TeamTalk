package com.ekotak.teamtalk.domain.ufh

/**
 * POMIAR METRAŻU NA RZUCIE — reszta `ufh-area-measure.ts`, której potrzebuje
 * EDYTOR rzutu (typy i geometria są w `UfhPlan.kt`).
 *
 * Do czasu zakładki „Audyt" z rysowaniem telefon rzut wyłącznie czytał; teraz
 * obrysowuje pomieszczenia, zagęszcza je łatkami i przepisuje sumy do pól
 * metrażu — dlatego dochodzą tu kolory kategorii, sumy wszystkich kategorii naraz
 * i fabryki nowych obrysów. Wartości 1:1 z panelem.
 */

/** Górna granica metrażu jednego wieloboku [m²] — łapie błędną kalibrację. */
const val ROOM_M2_MAX = 2000.0

/**
 * Kolor kategorii jako ARGB (`0xAARRGGBB`) — bez zależności od Compose.
 * Paleta 1:1 z `AREA_CATS` panelu; celowo inna niż kolory rodzajów skrzynki,
 * bo na rzucie oba zestawy są widoczne naraz.
 */
val AreaCat.color: Long
    get() = when (this) {
        AreaCat.TODO_CAT -> 0xFF7F77DD
        AreaCat.NONE -> 0xFF888780
        AreaCat.LEAD -> 0xFF7B5230
        AreaCat.S5 -> 0xFFC9448A
        AreaCat.S10 -> 0xFFD85A30
        AreaCat.S15 -> 0xFFEF9F27
        AreaCat.S20 -> 0xFF1D9E75
    }

/** Etykieta pola metrażu kondygnacji („Powierzchnia w rozstawie 5 cm (m2)"); puste bez pola. */
val AreaCat.fieldLabel: String
    get() = field?.let { "${it.label} (m2)" }.orEmpty()

/** Domyślna kategoria „pióra" po otwarciu narzędzia — najczęstszy rozstaw. */
val DEFAULT_AREA_CAT = AreaCat.S10

/** Kategorie do rysowania W AUDYCIE — bez „do ustalenia" (audyt rozstaw ustala). */
val MEASURE_CATS: List<AreaCat> = AreaCat.entries.filter { it != AreaCat.TODO_CAT }

/** Kategorie wstępnego przygotowania rzutu w zakładce „Pliki". */
val PREP_CATS: List<AreaCat> = listOf(AreaCat.TODO_CAT, AreaCat.NONE)

/** Domyślne pióro przygotowania — obrys bez decyzji o rozstawie. */
val PREP_AREA_CAT = AreaCat.TODO_CAT

/** Ile pomieszczeń czeka na rozstaw (obrysy z przygotowania rzutu). */
fun roomsNeedingSpacing(rooms: List<RoomShape>): Int = rooms.count { it.cat == AreaCat.TODO_CAT }

/** Nowe pomieszczenie z domkniętego obrysu. */
fun emptyRoom(cat: AreaCat, outline: List<PlanPoint>, manifoldId: String = ""): RoomShape =
    RoomShape(
        id = newRoomId(),
        name = "",
        cat = cat,
        outline = outline,
        holes = emptyList(),
        patches = emptyList(),
        manifoldId = manifoldId,
    )

/** Nowa łatka zagęszczenia. */
fun emptyPatch(cat: AreaCat, outline: List<PlanPoint>): AreaPatch =
    AreaPatch(id = newPatchId(), cat = cat, outline = outline)

/**
 * Czy przez pomieszczenie w ogóle idzie rura — jego kategoria ma rozstaw albo
 * ma go choć jedna łatka zagęszczenia („bez OP" z ogrzewanym fragmentem).
 */
fun roomHeats(room: RoomShape): Boolean {
    if (room.cat.spacingM != null) return true
    return room.patches.any { it.cat.spacingM != null }
}

/** Suma jednej kategorii: metraż (`null` = brak skali albo brak obrysów) i liczba wieloboków. */
data class CatSum(val m2: Double?, val count: Int)

/** Sumy wszystkich kategorii naraz (podpisy pod polami metrażu, legenda rzutu). */
fun catSums(rooms: List<RoomShape>, scale: PlanScale?): Map<AreaCat, CatSum> {
    val out = LinkedHashMap<AreaCat, CatSum>()
    for (c in AreaCat.entries) out[c] = CatSum(catM2(rooms, scale, c), catCount(rooms, c))
    return out
}

/**
 * Oznaczenie łatki: numer pomieszczenia-rodzica + litera („3a"). Litera, a nie
 * kolejny numer — łatka nie jest osobnym pomieszczeniem ani osobną pętlą.
 */
fun patchLabel(roomIndex: Int, patchIndex: Int): String {
    val i = maxOf(0, patchIndex)
    val letter = if (i < 26) {
        Char(97 + i).toString()
    } else {
        "${Char(97 + i / 26 - 1)}${Char(97 + i % 26)}"
    }
    return "${roomIndex + 1}$letter"
}

/** Opis łatki w listach — „zagęszczenie 5 cm" (bez rozstawu: co to za fragment). */
fun patchTitle(patch: AreaPatch): String {
    if (patch.cat.spacingM != null) return "zagęszczenie ${patch.cat.short}"
    return if (patch.cat == AreaCat.NONE) "fragment bez ogrzewania" else "fragment: ${patch.cat.label}"
}

/** Środek obrysu (średnia wierzchołków) — etykieta i punkt zaczepienia powiązań. */
fun centroid(points: List<PlanPoint>): PlanPoint {
    if (points.isEmpty()) return PlanPoint(0.5, 0.5)
    var x = 0.0
    var y = 0.0
    for (p in points) {
        x += p.x
        y += p.y
    }
    return PlanPoint(x / points.size, y / points.size)
}
