package com.ekotak.teamtalk.domain.ufh

import com.ekotak.teamtalk.domain.model.UfhState
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import java.util.Locale
import kotlin.math.abs

/**
 * ROZLICZENIE PUNKTOWE — rachunek zakładki „Rozliczenie" karty deala. Port
 * `ufh-points.ts` (reguły cennika montażowego) i `deal-settlement.ts`
 * (rozbicie na zestawy, migawka do zatwierdzenia).
 *
 * Ile punktów za pracę należy się za tego deala — w rozbiciu na instalacje
 * i na zestawy punktowe („Montaż", „Biuro") z Warunków finansowych.
 *
 * Ilości NIE liczą się tu od nowa: bierzemy je z [ufhPointQuantities], czyli
 * dokładnie tego samego rachunku, który karmi ofertę (`UfhQuote.kt`). Tu
 * dokładamy tylko drugą połowę wzoru — dopasowanie ilości do pozycji cennika
 * i mnożenie przez stawkę.
 *
 * Dlaczego reguły stoją osobno od [LABOR_RULES] w `PriceModel.kt`, skoro
 * pozycje bywają te same: tamte służą CENIE dla klienta (materiał + robocizna
 * + narzut), a te ROZLICZENIU z ekipą. Panel trzyma to tak samo w dwóch
 * plikach — pozycja może wypaść z ceny klienta, zostając w rozliczeniu ekipy
 * (i odwrotnie), więc jedna lista dla obu zastosowań zaczęłaby kłamać przy
 * pierwszej takiej różnicy.
 */

// ── Zestawy Warunków finansowych ─────────────────────────────────────────────

/**
 * Zestaw punktowy wpięty pod węzeł katalogu (`financial_terms_schemes`).
 * Rodzaj („Montaż" / „Biuro" / „Koszty z ręki") poznaje się po NAZWIE — tak
 * samo jak w panelu.
 */
data class PointScheme(
    val id: String,
    val categoryId: String,
    val name: String,
    val items: List<PointRate>,
)

/**
 * Czy zestaw to boks kosztów własnych (kwoty w zł), a nie cennik punktowy —
 * rozpoznanie po nazwie, 1:1 ze `scheme-kind.ts`.
 */
fun isCostScheme(name: String): Boolean {
    val n = name.trim()
    return COST_SCHEME_FULL.containsMatchIn(n) || COST_SCHEME_SHORT.matches(n)
}

private val COST_SCHEME_FULL = Regex("koszty\\s+z\\s+r[ęe]ki", RegexOption.IGNORE_CASE)
private val COST_SCHEME_SHORT = Regex("^koszty$", RegexOption.IGNORE_CASE)

/**
 * Stawki punktowe dla instalacji: zestawy wpięte pod węzeł i jego PRZODKÓW.
 * „Montaż" wisi zwykle pod korzeniem technologii, a formularz audytu bywa
 * dziedziczony przez podwęzły, więc szukamy wzdłuż całej ścieżki.
 *
 * „Koszty z ręki" świadomie pomijamy: to koszty własne instalacji, a nie
 * punkty do rozliczenia z ekipą.
 */
fun ratesForPath(schemes: List<PointScheme>, categoryIdPath: List<String>): List<PointRate> {
    val out = ArrayList<PointRate>()
    for (id in categoryIdPath) {
        for (s in schemes) {
            if (s.categoryId != id || isCostScheme(s.name)) continue
            for (item in s.items) out += item.copy(scheme = s.name)
        }
    }
    return out
}

// ── Wynik rachunku ───────────────────────────────────────────────────────────

/** Wiersz wyliczenia: ilość z audytu × stawka z cennika. */
data class PointLine(
    /** Klucz reguły — stabilny, niezależny od nazwy pozycji w cenniku. */
    val key: String,
    val label: String,
    val unit: String,
    val qty: Double,
    /** Punkty za jednostkę; `null` = pozycji nie ma w cenniku montażowym. */
    val rate: Double?,
    /** Punkty razem (`qty × rate`), 0 gdy brak stawki. */
    val points: Double,
    /** Skąd wzięła się ilość — podpowiedź pod wierszem. */
    val source: String,
    /** Zestaw punktowy, z którego przyszła stawka; `null` = brak stawki. */
    val scheme: String?,
)

data class UfhPointsResult(
    val quantities: UfhPointQuantities,
    /** Pozycje z niezerową ilością (te, które wchodzą do rozliczenia). */
    val lines: List<PointLine>,
    /** Pozycje pominięte, bo warunek z audytu nie jest spełniony (ilość 0). */
    val skipped: List<PointLine>,
    /** Suma punktów montażu. */
    val total: Double,
    /** Czego zabrakło: pozycji w cenniku, danych w audycie. */
    val warnings: List<String>,
)

/** Pozycje jednego zestawu punktowego w obrębie instalacji. */
data class SettlementGroup(
    /** Nazwa zestawu z Warunków finansowych; `null` = pozycji nie ma w cenniku. */
    val scheme: String?,
    val lines: List<PointLine>,
    val points: Double,
)

// ── Reguły cennika montażowego ───────────────────────────────────────────────

/**
 * Reguła = jedna pozycja cennika montażowego. [ids]/[match] łączą ją z wpisem
 * w Warunkach finansowych (najpierw po id pozycji, potem po nazwie — zestawy
 * dodawane ręcznie mają id-UUID-y), [qty] bierze ilość z audytu.
 */
data class PointRule(
    val key: String,
    val label: String,
    val unit: String,
    val ids: List<String>,
    val match: Regex,
    val source: String,
    val qty: (UfhPointQuantities) -> Double,
)

private fun re(pattern: String) = Regex(pattern, RegexOption.IGNORE_CASE)

/** Kolejność 1:1 z `UFH_POINT_RULES` panelu — tak samo czyta się rozliczenie. */
val UFH_POINT_RULES: List<PointRule> = listOf(
    PointRule(
        key = "pipe",
        label = "Rozwinięcie rury ogrzewania",
        unit = "mb",
        ids = listOf("op-rura-mb"),
        match = re("rozwini.*rur|rura.*ogrzewan"),
        source = "suma długości rury OP z całej instalacji",
        qty = { it.pipeM },
    ),
    PointRule(
        key = "boxSurface",
        label = "Skrzynka natynkowa",
        unit = "szt",
        ids = listOf("op-skrzynka-natynkowa"),
        match = re("skrzynka\\s+natynkow"),
        source = "kropki rozdzielaczy natynkowych na rzutach",
        qty = { it.boxSurface.toDouble() },
    ),
    PointRule(
        key = "boxFlush",
        label = "Skrzynka podtynkowa",
        unit = "szt",
        ids = listOf("op-skrzynka-podtynkowa"),
        match = re("skrzynka\\s+podtynkow"),
        source = "kropki rozdzielaczy podtynkowych (ściana działowa + nośna)",
        qty = { it.boxFlush.toDouble() },
    ),
    PointRule(
        key = "manifolds",
        label = "Montaż rozdzielacza",
        unit = "szt",
        ids = listOf("op-rozdzielacz"),
        match = re("monta.*rozdzielacz|^rozdzielacz"),
        source = "rozdzielacze z rzutów (montaż razem ze skrzynką)",
        qty = { it.manifolds.toDouble() },
    ),
    PointRule(
        key = "foil",
        label = "Sama folia",
        unit = "m2",
        ids = listOf("op-folia-m2"),
        match = re("sama\\s+folia|^folia"),
        source = "pomieszczenia bez OP (m²)",
        qty = { it.foilM2 },
    ),
    PointRule(
        key = "leadInFloors",
        label = "Dobieg OP/kondygnacja",
        unit = "ryczalt",
        ids = listOf("op-dobieg-kondygnacja"),
        match = re("dobieg.*kondygnac"),
        source = "kondygnacje z OP × ryczałt (dobiegi w zakresie montażu OP)",
        qty = { it.leadInFloors.toDouble() },
    ),
    PointRule(
        key = "wallChase",
        label = "Wkuwanie dobiegów do rozdzielaczy w ściany nośne",
        unit = "ryczalt",
        ids = listOf("op-wkuwanie-dobiegow"),
        match = re("wkuwan"),
        source = "„Dane instalacji” → wkuwanie w ściany nośne = tak",
        qty = { if (it.wallChase) 1.0 else 0.0 },
    ),
    PointRule(
        key = "filling",
        label = "Napełnienie i odpowietrzenie układu",
        unit = "ryczalt",
        ids = listOf("op-napelnienie-odpowietrzenie"),
        match = re("nape.nienie"),
        source = "napełnienie po zakończeniu instalacji OP",
        qty = { if (it.fillingAfterUfh) 1.0 else 0.0 },
    ),
    PointRule(
        key = "leadInStyro",
        label = "Dobiegi do rozdzielaczy na warstwie styropianu",
        unit = "ryczalt",
        ids = listOf("op-dobiegi-na-styropianie"),
        match = re("styropian"),
        source = "dobiegi na dodatkowej warstwie styropianu (robione w trakcie OP)",
        qty = { if (it.leadInOnStyro) 1.0 else 0.0 },
    ),
    PointRule(
        key = "pressureTest",
        label = "Próba szczelności",
        unit = "ryczalt",
        ids = listOf("op-proba-szczelnosci"),
        match = re("pr.ba\\s+szczelno"),
        source = "próba szczelności powietrzem z protokołem",
        qty = { if (it.pressureTest) 1.0 else 0.0 },
    ),
    PointRule(
        key = "waste",
        label = "Usunięcie odpadów montażowych na busa",
        unit = "ryczalt",
        ids = listOf("op-usuniecie-odpadow"),
        match = re("odpad"),
        source = "całkowite usunięcie odpadów przez ekotak",
        qty = { if (it.wasteRemoval) 1.0 else 0.0 },
    ),
    PointRule(
        key = "plate",
        label = "Płyta systemowa do ogrzewania z wypustkami — robocizna",
        unit = "m2",
        ids = listOf("op-plyta-systemowa-robocizna"),
        match = re("p.yta\\s+systemowa"),
        source = "„Dane instalacji” → płyta systemowa (m²)",
        qty = { it.plateM2 },
    ),
    PointRule(
        key = "design",
        label = "Profesjonalny projekt OP",
        unit = "ryczalt",
        ids = listOf("op-projekt"),
        match = re("projekt"),
        source = "„Dane instalacji” → projekt przez ekotak (zestaw „Biuro”)",
        qty = { if (it.design) 1.0 else 0.0 },
    ),
    PointRule(
        key = "warranty",
        label = "Przygotowanie wydłużonej gwarancji",
        unit = "ryczalt",
        ids = listOf("op-gwarancja-wydluzona"),
        match = re("gwarancj"),
        source = "„Dane instalacji” → dokumentacja do 10-letniej gwarancji (zestaw „Biuro”)",
        qty = { if (it.warranty) 1.0 else 0.0 },
    ),
)

/** Dopasowanie reguły do pozycji cennika: najpierw po id, potem po nazwie. */
private fun findRate(rates: List<PointRate>, rule: PointRule): PointRate? =
    rates.firstOrNull { rule.ids.contains(it.id) }
        ?: rates.firstOrNull { rule.match.containsMatchIn(it.name) }

/**
 * Punkty montażu OP: `Σ ilość z audytu × punkty z cennika`. [rates] to pozycje
 * ze WSZYSTKICH zestawów punktowych węzła (montaż + biuro) — projekt i gwarancja
 * siedzą w „Biurze".
 */
fun ufhMontagePoints(state: UfhState, rates: List<PointRate>): UfhPointsResult {
    val (quantities, quantityWarnings) = ufhPointQuantities(state)
    val warnings = ArrayList(quantityWarnings)
    val lines = ArrayList<PointLine>()
    val skipped = ArrayList<PointLine>()
    val missing = ArrayList<String>()

    for (rule in UFH_POINT_RULES) {
        val rate = findRate(rates, rule)
        val qty = r1(rule.qty(quantities))
        val line = PointLine(
            key = rule.key,
            label = rate?.name?.takeIf { it.isNotBlank() } ?: rule.label,
            unit = rate?.unit?.takeIf { it.isNotBlank() } ?: rule.unit,
            qty = qty,
            rate = rate?.points,
            points = if (rate != null) r1(qty * rate.points) else 0.0,
            source = rule.source,
            scheme = rate?.scheme?.takeIf { it.isNotBlank() },
        )
        if (qty > 0) {
            lines += line
            if (rate == null) missing += rule.label
        } else {
            skipped += line
        }
    }

    if (missing.isNotEmpty()) {
        warnings += "Brak pozycji w cenniku montażowym: ${missing.joinToString(", ")} — " +
            "punkty nie naliczone."
    }
    return UfhPointsResult(
        quantities = quantities,
        lines = lines,
        skipped = skipped,
        total = r1(lines.sumOf { it.points }),
        warnings = warnings,
    )
}

/**
 * Rozbicie wyniku na zestawy punktowe. Kolejność zestawów bierzemy z kolejności
 * pozycji w cenniku (czyli od korzenia technologii w dół), a pozycje bez stawki
 * lądują w osobnej grupie `null` — to one wymagają uzupełnienia punktacji
 * w Warunkach finansowych, więc idą na koniec: to lista braków, nie rozliczenie.
 */
fun groupBySchemes(result: UfhPointsResult): List<SettlementGroup> {
    val order = ArrayList<String?>()
    val byScheme = LinkedHashMap<String?, MutableList<PointLine>>()
    for (line in result.lines) {
        val key = if (line.rate == null) null else line.scheme
        if (!byScheme.containsKey(key)) {
            byScheme[key] = ArrayList()
            order += key
        }
        byScheme.getValue(key) += line
    }
    return order
        .sortedBy { if (it == null) 1 else 0 }
        .map { scheme ->
            val lines = byScheme[scheme].orEmpty()
            SettlementGroup(scheme, lines, r1(lines.sumOf { it.points }))
        }
}

/** Nazwa grupy do wyświetlenia. */
fun groupLabel(scheme: String?): String = scheme ?: "Brak pozycji w cenniku"

/** Suma punktów deala — z migawek tam, gdzie są, z rachunku na żywo w reszcie. */
fun totalPoints(values: List<Double>): Double = r1(values.sum())

/**
 * Rozjazd zatwierdzonej migawki z bieżącym rachunkiem. Próg 0,05 pkt jak
 * w panelu: poniżej niego to zaokrąglenie, a nie zmiana zakresu.
 */
fun settlementStale(snapshotPoints: Double, livePoints: Double): Boolean =
    abs(snapshotPoints - livePoints) > 0.05

// ── Migawka zatwierdzenia ────────────────────────────────────────────────────

/**
 * Liczba w migawce zapisana tak, jak zapisałby ją panel: całkowite BEZ „.0".
 * `JSON.stringify` w przeglądarce nie odróżnia 1424 od 1424.0, a Kotlin tak —
 * bez tego migawka z telefonu różniłaby się od migawki z panelu znakami, mimo
 * że niesie tę samą liczbę, i nie dałoby się ich porównać wprost.
 */
private fun num(v: Double): JsonPrimitive {
    val whole = v.toLong()
    return if (v == whole.toDouble()) JsonPrimitive(whole) else JsonPrimitive(v)
}

/**
 * Migawka zapisywana przy zatwierdzeniu — na tyle opisowa, żeby dało się ją
 * odczytać BEZ ponownego liczenia (nazwy pozycji, ilości, stawki, jednostki).
 * Kształt 1:1 z panelem, bo czyta ją ten sam ekran Warunków finansowych.
 */
fun settlementBreakdown(
    installation: String,
    path: List<String>,
    groups: List<SettlementGroup>,
): JsonObject = buildJsonObject {
    put("installation", JsonPrimitive(installation))
    put("path", JsonArray(path.map { JsonPrimitive(it) }))
    put(
        "groups",
        buildJsonArray {
            for (g in groups) {
                add(
                    buildJsonObject {
                        put("scheme", JsonPrimitive(groupLabel(g.scheme)))
                        put("points", num(g.points))
                        put(
                            "lines",
                            buildJsonArray {
                                for (l in g.lines) {
                                    add(
                                        buildJsonObject {
                                            put("key", JsonPrimitive(l.key))
                                            put("label", JsonPrimitive(l.label))
                                            put("unit", JsonPrimitive(l.unit))
                                            put("qty", num(l.qty))
                                            put(
                                                "rate",
                                                l.rate?.let { num(it) } ?: JsonNull,
                                            )
                                            put("points", num(l.points))
                                        },
                                    )
                                }
                            },
                        )
                    },
                )
            }
        },
    )
}

// ── Formatowanie ─────────────────────────────────────────────────────────────

/** Liczba po polsku, bez zbędnego „,0" („1390,7" / „100"). */
fun fmtPoints(n: Double): String {
    val r = r1(n)
    if (!r.isFinite()) return "0"
    val whole = r.toLong()
    if (r == whole.toDouble()) return whole.toString()
    return String.format(Locale.US, "%.1f", r).replace('.', ',')
}

/** Jednostka pozycji cennika w formie do wyświetlenia. */
fun unitLabel(unit: String): String = when (unit) {
    "ryczalt" -> "ryczałt"
    "szt" -> "szt."
    "kpl" -> "kpl."
    "m2" -> "m²"
    "mb" -> "mb"
    "godz" -> "godz."
    else -> unit
}
