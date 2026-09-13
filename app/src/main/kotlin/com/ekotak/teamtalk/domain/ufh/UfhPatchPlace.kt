package com.ekotak.teamtalk.domain.ufh

/**
 * Osadzanie ŁATKI ZAGĘSZCZENIA na rzucie — port `ufh-patch-place.ts`.
 *
 * Audytor nie przełącza żadnego trybu: rysuje obrys jak zwykle, a to, czy
 * powstanie nowe pomieszczenie czy zagęszczenie istniejącego, wynika z miejsca:
 *  • obrys w całości w pomieszczeniu → łatka (bez pytania),
 *  • obrys wystaje → pytamy i docinamy go do obrysu pomieszczenia,
 *  • obrys poza wszystkim → zwykłe nowe pomieszczenie.
 *
 * Łatka NIE jest osobną pętlą — dziedziczy rozdzielacz pomieszczenia i wychodzi
 * z jego metrażu (patrz `AreaPatch`).
 */

/** Powyżej tego udziału pola uznajemy obrys za leżący w całości w pomieszczeniu. */
private const val INSIDE_RATIO = 0.995

/** Poniżej tego udziału traktujemy zetknięcie jak brak trafienia. */
private const val TOUCH_RATIO = 0.02

data class PatchTarget(
    val room: RoomShape,
    /** Obrys docięty do pomieszczenia (przy `inside` = obrys bez zmian); pusty = docięcie się nie udało. */
    val clipped: List<PlanPoint>,
    /** Obrys mieści się w pomieszczeniu w całości — nie ma o co pytać. */
    val inside: Boolean,
    /** Jaka część rysowanego obrysu wpadła do pomieszczenia (0–1). */
    val ratio: Double,
)

/**
 * Pomieszczenie, na którym audytor narysował obrys (najwięcej części wspólnej).
 * `null` = obrys nie trafia w żadne pomieszczenie → to nowy obrys.
 */
fun findPatchTarget(pts: List<PlanPoint>, rooms: List<RoomShape>): PatchTarget? {
    if (pts.size < 3) return null
    val whole = ringArea(pts)
    if (whole <= 0) return null
    var best: PatchTarget? = null
    for (room in rooms) {
        if (room.outline.size < 3) continue
        val parts = polyIntersection(pts, room.outline) ?: continue
        var common = 0.0
        for (p in parts) common += ringArea(p)
        val ratio = common / whole
        if (ratio <= TOUCH_RATIO) continue
        if (best != null && ratio <= best.ratio) continue
        val inside = ratio >= INSIDE_RATIO
        best = PatchTarget(
            room = room,
            clipped = if (inside) pts else largestIntersection(pts, room.outline).orEmpty(),
            inside = inside,
            ratio = ratio,
        )
    }
    return best
}

/** Czy łatka wchodzi na wycięcie (kominek, schody) — tego nie umiemy policzyć. */
fun hitsHole(pts: List<PlanPoint>, room: RoomShape): Boolean {
    val whole = ringArea(pts)
    if (whole <= 0) return false
    for (hole in room.holes) {
        if (hole.size < 3) continue
        val parts = polyIntersection(pts, hole) ?: continue
        var common = 0.0
        for (p in parts) common += ringArea(p)
        if (common / whole > TOUCH_RATIO) return true
    }
    return false
}

/** Dlaczego łatki nie da się dodać — tekst wprost do paska komunikatów; `null` = można. */
fun patchProblem(room: RoomShape, cat: AreaCat, outline: List<PlanPoint>): String? {
    if (outline.size < 3) {
        return "Nie umiem dociąć tego obrysu do pomieszczenia — narysuj go wewnątrz."
    }
    if (room.patches.size >= PATCHES_MAX) {
        return "Limit $PATCHES_MAX zagęszczeń w jednym pomieszczeniu."
    }
    if (cat == room.cat) {
        return "To ten sam rozstaw co pomieszczenie (${cat.short}) — wybierz inny."
    }
    if (hitsHole(outline, room)) {
        return "Zagęszczenie nachodzi na wycięcie — narysuj je poza wycięciem."
    }
    return null
}

/** Pomieszczenie, NA którym leży inny obrys — cel zamiany na zagęszczenie. */
fun patchParentOf(rooms: List<RoomShape>, roomId: String): RoomShape? {
    val room = rooms.firstOrNull { it.id == roomId } ?: return null
    return findPatchTarget(room.outline, rooms.filter { it.id != roomId })?.room
}

data class ConvertToPatchResult(
    /** Lista po zamianie — obrys zniknął jako pomieszczenie, wszedł jako łatka. */
    val rooms: List<RoomShape>? = null,
    val parent: RoomShape? = null,
    /** Obrys trzeba było dociąć do rodzica (wystawał poza niego). */
    val clipped: Boolean? = null,
    /** Dlaczego się nie da — tekst wprost do paska komunikatów. */
    val problem: String? = null,
)

/**
 * Zamiana pomieszczenia na ŁATKĘ ZAGĘSZCZENIA pomieszczenia pod spodem — naprawa
 * obrysów narysowanych jako „osobne pomieszczenie" przez pomyłkę.
 */
fun convertRoomToPatch(rooms: List<RoomShape>, roomId: String): ConvertToPatchResult {
    val room = rooms.firstOrNull { it.id == roomId }
        ?: return ConvertToPatchResult(problem = "Nie ma takiego obrysu.")
    if (room.holes.isNotEmpty()) {
        return ConvertToPatchResult(
            problem = "Ten obrys ma wycięcia — zagęszczenie ich nie zna. Cofnij je i spróbuj ponownie.",
        )
    }
    if (room.patches.isNotEmpty()) {
        return ConvertToPatchResult(
            problem = "Ten obrys ma własne zagęszczenia — zagęszczenie w zagęszczeniu nie ma sensu.",
        )
    }
    val rest = rooms.filter { it.id != roomId }
    val target = findPatchTarget(room.outline, rest)
        ?: return ConvertToPatchResult(
            problem = "Ten obrys nie leży na żadnym pomieszczeniu — nie ma czego zagęszczać.",
        )
    val outline = target.clipped
    patchProblem(target.room, room.cat, outline)?.let { return ConvertToPatchResult(problem = it) }
    return ConvertToPatchResult(
        rooms = addPatch(rest, target.room.id, room.cat, outline),
        parent = target.room,
        clipped = !target.inside,
    )
}

/** Dokłada łatkę do pomieszczenia (bez walidacji — ta jest w [patchProblem]). */
fun addPatch(rooms: List<RoomShape>, roomId: String, cat: AreaCat, outline: List<PlanPoint>): List<RoomShape> =
    rooms.map { r ->
        if (r.id == roomId) r.copy(patches = r.patches + emptyPatch(cat, outline)) else r
    }
