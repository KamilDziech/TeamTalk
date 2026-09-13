package com.ekotak.teamtalk.domain.ufh

import kotlin.math.ceil
import kotlin.math.max

/**
 * POJEMNOŚĆ ROZDZIELACZA — ile pętli wolno wyprowadzić z jednej belki. Port
 * `ufh-manifold-capacity.ts` + `ufhManifoldMax`/`catalogMaxLoops` z `ufh-systems.ts`.
 */

/** Domyślny limit pętli na rozdzielacz (gdy system rur nie ma katalogu). */
const val MANIFOLD_LOOP_MAX = 12

/** Największy rozdzielacz katalogu — powyżej trzeba dołożyć skrzynkę. */
fun catalogMaxLoops(cat: UfhSystemCatalog): Int = cat.manifolds.fold(0) { m, it -> max(m, it.loops) }

/**
 * Ile pętli wolno wyprowadzić z JEDNEJ skrzynki w tym systemie — tyle, ile ma
 * największy rozdzielacz katalogu (KAN-therm InoxFlow 12, TER/RZT 15).
 */
fun ufhManifoldMax(system: String?): Int = ufhCatalog(system)?.let { catalogMaxLoops(it) } ?: MANIFOLD_LOOP_MAX

enum class LoopLevel { OK, FULL, OVER }

data class LoopLoad(
    val loops: Int,
    /** Pętle ponad limit (0 = mieści się). */
    val over: Int,
    /** Wolne wyjścia (0 przy komplecie i przy przekroczeniu). */
    val free: Int,
    val level: LoopLevel,
    /** Krótka etykieta do nagłówka skrzynki („10/12"). */
    val badge: String,
)

/** Obciążenie jednej skrzynki. */
fun loopLoad(loops: Int, max: Int = MANIFOLD_LOOP_MAX): LoopLoad {
    val over = max(0, loops - max)
    return LoopLoad(
        loops = loops,
        over = over,
        free = max(0, max - loops),
        level = if (over > 0) LoopLevel.OVER else if (loops >= max) LoopLevel.FULL else LoopLevel.OK,
        badge = "$loops/$max",
    )
}

/** Ile rozdzielaczy trzeba, żeby te pętle w ogóle się zmieściły. */
fun neededManifolds(loops: Int, max: Int = MANIFOLD_LOOP_MAX): Int =
    max(1, ceil(loops.toDouble() / max).toInt())

/** Wolne wyjścia na POZOSTAŁYCH skrzynkach (bez tej o indeksie `idx`). */
fun freeLoopsElsewhere(loopsPerManifold: List<Int>, idx: Int, max: Int = MANIFOLD_LOOP_MAX): Int {
    var t = 0
    loopsPerManifold.forEachIndexed { i, l -> if (i != idx) t += loopLoad(l, max).free }
    return t
}

/** „1 wolne wyjście / 2 wolne wyjścia / 5 wolnych wyjść". */
fun outletsLabel(n: Int): String = plForm(n, "wolne wyjście", "wolne wyjścia", "wolnych wyjść")

/** Co zrobić z nadmiarem pętli na KONKRETNYM rozdzielaczu (`null` = mieści się). */
fun manifoldOverMessage(loops: Int, manifolds: Int, freeElsewhere: Int, max: Int = MANIFOLD_LOOP_MAX): String? {
    val over = loopLoad(loops, max).over
    if (over == 0) return null
    val head = "Za dużo pętli na tym rozdzielaczu: $loops z max $max"
    if (manifolds <= 1) {
        return "$head — dołóż drugi rozdzielacz (potrzebne min. ${neededManifolds(loops, max)}) " +
            "i przypisz do niego część pomieszczeń."
    }
    if (freeElsewhere >= over) {
        return "$head — przepnij co najmniej ${loopsLabel(over)} pod inny rozdzielacz " +
            "(na pozostałych ${outletsLabel(freeElsewhere)})."
    }
    return "$head — na pozostałych rozdzielaczach zostało ${outletsLabel(freeElsewhere)}, " +
        "czyli za mało: przepnij co się da i dołóż kolejny rozdzielacz."
}

/** Komunikat dla kondygnacji, gdy jeden (albo żaden) rozdzielacz nie uniesie pętli. */
fun floorLoopMessage(loops: Int, manifolds: Int, max: Int = MANIFOLD_LOOP_MAX): String? {
    if (manifolds > 1 || loops <= max) return null
    return "Ta kondygnacja to ${loopsLabel(loops)}, a jeden rozdzielacz obsłuży max $max — " +
        "dołóż drugi rozdzielacz (potrzebne min. ${neededManifolds(loops, max)}): zwiększ " +
        "„Ilość rozdzielaczy\", postaw jego kropkę na rzucie i przypisz do niej część pomieszczeń."
}
