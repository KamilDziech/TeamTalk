package com.ekotak.teamtalk.domain.ufh

import kotlinx.serialization.json.JsonPrimitive
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.sqrt

/**
 * ARYTMETYKA I FORMATY „JAK W JAVASCRIPCIE" — wspólne pomocniki portu rzutu.
 *
 * Rzut audytu OP liczy się po obu stronach (panel w TS, telefon w Kotlinie)
 * i ma dawać te same liczby CO DO BITU, bo z nich idzie rura, pętle i oferta.
 * Trzy miejsca, w których JVM domyślnie robi coś innego niż V8:
 *
 *  • `Math.hypot` — V8 liczy go sumą kwadratów po normalizacji (Kahan), a JVM
 *    algorytmem fdlibm; różnica bywa w ostatnim bicie, a to potrafi przestawić
 *    zaokrąglenie dobiegu o 0,1 mb,
 *  • `toFixed` — JS zaokrągla DOKŁADNĄ wartość binarną, `String.format` pracuje
 *    na skróconym zapisie dziesiętnym,
 *  • `String(n)` — JS nie dopisuje „.0" do liczb całkowitych.
 */

/**
 * `Math.hypot(a, b)` z V8 (builtins `MathHypot`): normalizacja przez największą
 * wartość bezwzględną i suma kwadratów z kompensacją Kahana.
 */
internal fun jsHypot(a: Double, b: Double): Double {
    val x = abs(a)
    val y = abs(b)
    if (x == Double.POSITIVE_INFINITY || y == Double.POSITIVE_INFINITY) return Double.POSITIVE_INFINITY
    if (x.isNaN() || y.isNaN()) return Double.NaN
    val max = maxOf(x, y)
    if (max == 0.0) return 0.0
    var sum = 0.0
    var compensation = 0.0
    for (v in doubleArrayOf(x, y)) {
        val n = v / max
        val summand = n * n - compensation
        val preliminary = sum + summand
        compensation = (preliminary - sum) - summand
        sum = preliminary
    }
    return sqrt(sum) * max
}

/** `String(n)` z JS — bez „.0" przy liczbach całkowitych i bez notacji wykładniczej. */
internal fun jsNum(v: Double): String {
    if (v.isNaN()) return "NaN"
    if (v.isInfinite()) return if (v > 0) "Infinity" else "-Infinity"
    if (v == floor(v) && abs(v) < 1e15) return v.toLong().toString()
    return BigDecimal(v.toString()).stripTrailingZeros().toPlainString()
}

/** `n.toFixed(digits)` z JS — połówki w górę liczone na dokładnej wartości binarnej. */
internal fun jsFixed(v: Double, digits: Int): String {
    if (!v.isFinite()) return jsNum(v)
    return BigDecimal(v).setScale(digits, RoundingMode.HALF_UP).toPlainString()
}

/** Liczba do JSON-a tak, jak zapisuje ją panel (`1`, a nie `1.0`). */
internal fun jsonNum(v: Double): JsonPrimitive =
    if (v == floor(v) && abs(v) < 1e15) JsonPrimitive(v.toLong()) else JsonPrimitive(v)

private val ISO_MILLIS: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)

/** `new Date().toISOString()` — zawsze z milisekundami, jak w panelu. */
internal fun jsIsoNow(): String = ISO_MILLIS.format(Instant.now())

/** Odmiana „1 X / 2 Y / 5 Z" — reguła wspólna wszystkich podpisów rzutu. */
internal fun plForm(n: Int, one: String, few: String, many: String): String {
    if (n == 1) return "1 $one"
    val last = n % 10
    val teens = n % 100
    val isFew = last in 2..4 && teens !in 12..14
    return "$n ${if (isFew) few else many}"
}
