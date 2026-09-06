package com.ekotak.teamtalk.domain.ufh

import com.ekotak.teamtalk.domain.model.UfhState
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * PODSUMOWANIE OFERTY — tabelaryczny zakres deala z kwotami. Port `offer-scope.ts`.
 *
 * Bierze ILOŚCI z audytu OP (te same, co punkty ekipy) i przemnaża je przez CENY
 * JEDNOSTKOWE z karty „🧮 Formuła ceny" ([priceRows]). Żadnej drugiej formuły tu
 * nie ma — gdy zmieni się cennik węzła, tabela zmienia się razem z nim.
 *
 * Metraż audytu rozkłada się na cztery rozstawy (5/10/15/20 cm), a cena za m²
 * zależy od rozstawu — pozycja „Wykonanie instalacji" wychodzi więc jako tyle
 * wierszy, ile rozstawów zmierzył audyt. Reszta pozycji liczy się raz.
 */

/** Jeden wiersz tabeli podsumowania. */
data class ScopeRow(
    val key: String,
    val group: String,
    val name: String,
    /** Specyfikacja / skąd wzięła się ilość („rozstaw 15 cm", „2 × kondygnacja"). */
    val spec: String,
    val qty: Double?,
    val unit: String,
    /** Cena jednostki netto; `null`, gdy ilość składa się z różnych cen. */
    val unitNet: Double?,
    val net: Double,
    /** Powód zerowej kwoty („wykonane na etapie wod-kan"). */
    val zeroed: String? = null,
)

data class OfferScope(
    val rows: List<ScopeRow>,
    val net: Double,
    /** Materiał i robocizna w tej sumie — rozbicie tylko dla widoku wewnętrznego. */
    val material: Double,
    val labor: Double,
    /** Braki cennika (pozycje bez kartoteki / bez stawki) — wewnętrzne. */
    val gaps: List<String>,
)

private data class Money(val net: Double, val material: Double)

private val NO_MONEY = Money(0.0, 0.0)

private fun rowMoney(row: PriceRow?): Money =
    if (row == null) NO_MONEY else Money(r2(row.net), r2(netSplitMaterial(row)))

private fun times(m: Money, k: Double): Money = Money(r2(m.net * k), r2(m.material * k))

/** Obwody rozłożone na rozdzielacze — reszta idzie do pierwszych szafek. */
fun splitLoops(loops: Int, manifolds: Int): List<Int> {
    // Więcej rozdzielaczy niż obwodów nie ma sensu — pusty rozdzielacz wybrałby
    // najmniejsze belki z katalogu i cicho zawyżył cenę.
    val n = max(1, min(jsRound(manifolds.toDouble()).toInt(), max(1, loops)))
    val base = loops / n
    val rest = loops - base * n
    return List(n) { i -> base + if (i < rest) 1 else 0 }
}

/** Rodzaj skrzynki dla ceny — audyt rozróżnia ścianę działową i nośną, cennik nie. */
private fun cabinetKindOf(boxFlush: Int, boxSurface: Int): CabinetKind = when {
    boxFlush > 0 -> CabinetKind.PODTYNKOWA
    boxSurface > 0 -> CabinetKind.NATYNKOWA
    else -> CabinetKind.BRAK
}

/**
 * Wariant formuły odtworzony z audytu. Pozycje robione wcześniej przez ekipę
 * wod-kan zerują się dokładnie tak, jak w rozliczeniu punktów.
 */
private fun variantFrom(
    state: UfhState,
    input: UfhQuoteInput,
    spacingM: Double,
    loops: Int,
    biocide: Boolean,
    waterL: Int,
    markup: SavedMarkup,
): PriceVariant {
    val q = input.quantities
    val inst = state.install
    fun wodkan(on: Boolean): Stage = if (on) Stage.WODKAN else Stage.OP
    val stages = mapOf(
        StagedRow.MANIFOLD to wodkan(q.manifoldByWodKan),
        StagedRow.CABINET to wodkan(q.leadInByWodKan),
        StagedRow.LEAD_IN to wodkan(q.leadInByWodKan),
        StagedRow.FILLING to wodkan(!q.fillingAfterUfh),
        StagedRow.WALL_CHASE to wodkan(q.leadInByWodKan),
        StagedRow.LEAD_IN_STYRO to wodkan(q.leadInByWodKan),
    )
    return PriceVariant(
        system = state.pipeSystem,
        spacingM = spacingM,
        subfloorJoints = inst.subfloorJoints,
        loops = loops,
        cabinetKind = cabinetKindOf(q.boxFlush, q.boxSurface),
        cabinetControl = roomControlPlanned(state.roomControl),
        leadInMm = inst.leadInPipeMm.ifBlank { UFH_LEAD_IN_PIPE_MM[0] },
        medium = mediumKey(inst.heatMedium),
        waterLiters = waterL,
        biocide = biocide,
        stages = stages,
        markupBase = markup.base,
        markup = markup.rows,
    )
}

private const val GROUP_BASE = "Zakres podstawowy"
private const val GROUP_MANIFOLD = "Rozdzielacze i skrzynki"
private const val GROUP_LEAD_IN = "Dobiegi i próba szczelności"
private const val GROUP_FILLING = "Napełnienie układu"
private const val GROUP_EXTRA = "Pozycje dodatkowe z audytu"

/** Ilość → tekst z przecinkiem dziesiętnym (spec wiersza, nie kwota). */
private fun numText(v: Double, d: Int = 1): String =
    String.format(Locale.US, "%.${d}f", v).replace('.', ',')

/**
 * Tabela podsumowania oferty dla jednej instalacji.
 *
 * Wiersze bez pokrycia w audycie NIE POJAWIAJĄ SIĘ wcale — pusta rubryka
 * w ofercie jest gorsza niż jej brak. Wyjątkiem są pozycje zerowane etapem
 * wod-kan: zostają z kwotą 0 i powodem, bo klient ma widzieć, że o nich
 * pamiętamy i dlaczego za nie nie płaci.
 */
fun offerScope(state: UfhState, input: UfhQuoteInput, pricing: OfferPricing): OfferScope {
    val catalog = pricing.catalog
    val markup = pricing.markup
    val q = input.quantities
    val inst = state.install

    val leadInMm = inst.leadInPipeMm.ifBlank { UFH_LEAD_IN_PIPE_MM[0] }
    val waterL = jsRound(
        waterVolumeTotal(
            system = state.pipeSystem,
            loopM = input.pipeM,
            supplyMmRaw = leadInMm,
            supplyM = (q.leadInFloors * LEAD_IN_M_PER_FLOOR).toDouble(),
        ),
    ).toInt()

    val spacings = input.areas.byCat.filter { it.spacingM != null && it.m2 > 0 }
    val mainSpacing = if (spacings.isNotEmpty()) {
        spacings.reduce { a, b -> if (b.m2 > a.m2) b else a }.spacingM!!
    } else {
        0.15
    }

    fun rowsFor(spacingM: Double, loops: Int, biocide: Boolean) = priceRows(
        catalog,
        variantFrom(state, input, spacingM, loops, biocide, waterL, markup),
    )

    val split = splitLoops(input.loops, max(1, input.manifoldsToBuy))
    val base = rowsFor(mainSpacing, split.firstOrNull() ?: input.loops, false)
    fun find(key: String): PriceRow? =
        base.first.firstOrNull { it.key == key } ?: base.second.firstOrNull { it.key == key }
    fun money(key: String): Money = rowMoney(find(key))

    val rows = ArrayList<ScopeRow>()
    val gaps = ArrayList<String>()
    fun collect(row: PriceRow?, prefix: String? = null) {
        if (row == null) return
        gaps += row.gaps.map { "${prefix ?: "${row.no} ${row.name}"} — $it" }
    }
    fun push(r: ScopeRow) {
        if (r.net != 0.0 || r.zeroed != null) rows += r
    }

    // ── Zakres podstawowy ────────────────────────────────────────────────────
    val fixed = money("fixed")
    collect(find("fixed"))
    push(
        ScopeRow(
            key = "fixed",
            group = GROUP_BASE,
            name = "Koszty stałe i drobnica",
            spec = "przygotowanie, dojazdy, materiał drobny",
            qty = 1.0,
            unit = "instalacja",
            unitNet = fixed.net,
            net = fixed.net,
        ),
    )

    for (a in spacings) {
        val spacingM = a.spacingM!!
        val perM2Rows = rowsFor(spacingM, split.firstOrNull() ?: input.loops, false)
        val perM2 = perM2Rows.first.firstOrNull { it.key == "perM2" }
        val unit = rowMoney(perM2)
        collect(perM2, "Wykonanie instalacji (rozstaw ${jsRound(spacingM * 100).toInt()} cm)")
        push(
            ScopeRow(
                key = "perM2:${a.cat.key}",
                group = GROUP_BASE,
                name = "Wykonanie instalacji podłogowej",
                spec = "${a.label} · rozstaw co ${jsRound(spacingM * 100).toInt()} cm",
                qty = a.m2,
                unit = "m²",
                unitNet = unit.net,
                net = times(unit, a.m2).net,
            ),
        )
    }

    // ── Rozdzielacze i skrzynki ──────────────────────────────────────────────
    // Rozdzielacz 8-obwodowy i 7-obwodowy to inne belki i inne szafki, więc
    // każdy rozdzielacz liczy się osobno, po swojej liczbie obwodów.
    val perManifold = split.map { rowsFor(mainSpacing, it, false) }
    val manifoldMoney = perManifold.fold(NO_MONEY) { t, r ->
        val m = rowMoney(r.first.firstOrNull { it.key == "manifold" })
        Money(r2(t.net + m.net), r2(t.material + m.material))
    }
    val cabinetMoney = perManifold.fold(NO_MONEY) { t, r ->
        val m = rowMoney(r.first.firstOrNull { it.key == "cabinet" })
        Money(r2(t.net + m.net), r2(t.material + m.material))
    }
    perManifold.forEach { r ->
        collect(r.first.firstOrNull { it.key == "manifold" })
        collect(r.first.firstOrNull { it.key == "cabinet" })
    }

    val manifoldZeroed = find("manifold")?.zeroed
    if (input.manifoldsToBuy > 0) {
        push(
            ScopeRow(
                key = "manifold",
                group = GROUP_MANIFOLD,
                name = "Rozdzielacze z szafkami rozdzielaczowymi",
                spec = if (split.size == 1) {
                    "${split[0]} obwodów"
                } else {
                    "${split.joinToString(" + ")} obwodów"
                },
                qty = input.manifoldsToBuy.toDouble(),
                unit = "szt.",
                unitNet = if (split.size == 1) manifoldMoney.net else null,
                net = if (manifoldZeroed != null) 0.0 else manifoldMoney.net,
                zeroed = manifoldZeroed,
            ),
        )
    }

    val cabinetZeroed = find("cabinet")?.zeroed
    val cabinetKind = cabinetKindOf(q.boxFlush, q.boxSurface)
    if (input.cabinetsToBuy > 0 && cabinetKind != CabinetKind.BRAK) {
        push(
            ScopeRow(
                key = "cabinet",
                group = GROUP_MANIFOLD,
                name = "Skrzynki rozdzielaczy",
                spec = listOfNotNull(
                    cabinetKind.wire,
                    if (roomControlPlanned(state.roomControl)) "pod listwę sterującą" else null,
                ).joinToString(" · "),
                qty = input.cabinetsToBuy.toDouble(),
                unit = "szt.",
                unitNet = if (split.size == 1) cabinetMoney.net else null,
                net = if (cabinetZeroed != null) 0.0 else cabinetMoney.net,
                zeroed = cabinetZeroed,
            ),
        )
    }

    // ── Dobiegi i próba ──────────────────────────────────────────────────────
    val leadIn = money("leadIn")
    val leadInZeroed = find("leadIn")?.zeroed
    collect(find("leadIn"))
    if (q.leadInFloors > 0 || leadInZeroed != null) {
        push(
            ScopeRow(
                key = "leadIn",
                group = GROUP_LEAD_IN,
                name = "Dobiegi od źródła ciepła do rozdzielaczy",
                spec = "⌀$leadInMm mm · $LEAD_IN_M_PER_FLOOR mb na kondygnację",
                qty = q.leadInFloors.toDouble(),
                unit = "kondygnacja",
                unitNet = leadIn.net,
                net = if (leadInZeroed != null) 0.0 else times(leadIn, q.leadInFloors.toDouble()).net,
                zeroed = leadInZeroed,
            ),
        )
    }

    if (q.pressureTest) {
        val test = money("pressureTest")
        collect(find("pressureTest"))
        push(
            ScopeRow(
                key = "pressureTest",
                group = GROUP_LEAD_IN,
                name = "Próba szczelności z protokołem",
                spec = "próba powietrzem, dokumentacja",
                qty = 1.0,
                unit = "budynek",
                unitNet = test.net,
                net = test.net,
            ),
        )
    }

    // ── Napełnienie ──────────────────────────────────────────────────────────
    val filling = money("filling")
    val fillingZeroed = find("filling")?.zeroed
    collect(find("filling"))
    val mediumLabel = HEAT_MEDIA.firstOrNull { it.key == mediumKey(inst.heatMedium) }?.label
        ?: "czynnik"
    push(
        ScopeRow(
            key = "filling",
            group = GROUP_FILLING,
            name = "Napełnienie i odpowietrzenie układu",
            spec = "$mediumLabel · $waterL l",
            qty = 1.0,
            unit = "instalacja",
            unitNet = filling.net,
            net = if (fillingZeroed != null) 0.0 else filling.net,
            zeroed = fillingZeroed,
        ),
    )

    // Inhibitor to różnica pozycji „zalanie" z biocydem i bez — sama butelka
    // MC10+ jest materiałem, więc delta idzie po obu stronach rozbicia.
    val takesBiocide = HEAT_MEDIA.firstOrNull { it.key == mediumKey(inst.heatMedium) }
        ?.withBiocide == true
    if (takesBiocide && inst.biocide.trim().lowercase().startsWith("tak")) {
        val wet = rowsFor(mainSpacing, split.firstOrNull() ?: input.loops, true)
        val wetFilling = rowMoney(wet.first.firstOrNull { it.key == "filling" })
        val deltaNet = r2(wetFilling.net - filling.net)
        if (deltaNet > 0) {
            push(
                ScopeRow(
                    key = "biocide",
                    group = GROUP_FILLING,
                    name = "Inhibitor biobójczy ADEY MC10+",
                    spec = "$waterL l układu → ⌈$waterL ÷ 125⌉ butelki",
                    qty = 1.0,
                    unit = "kpl.",
                    unitNet = deltaNet,
                    net = deltaNet,
                ),
            )
        }
    }

    // ── Pozycje dodatkowe (to, co audytor zaznaczył) ─────────────────────────
    fun extra(key: String, name: String, spec: String, qty: Double?, unit: String, factor: Double = 1.0) {
        val m = money(key)
        collect(find(key))
        push(
            ScopeRow(
                key = key,
                group = GROUP_EXTRA,
                name = name,
                spec = spec,
                qty = qty,
                unit = unit,
                unitNet = m.net,
                net = times(m, factor).net,
            ),
        )
    }

    if (q.plateM2 > 0) {
        extra(
            "plate",
            "Płyta systemowa z wypustkami",
            "zamiast folii i spinek · ${numText(q.plateM2)} m²",
            q.plateM2,
            "m²",
            q.plateM2,
        )
    }
    if (q.leadInOnStyro && !q.leadInByWodKan) {
        extra("leadInStyro", "Dobiegi prowadzone na warstwie styropianu", "ryczałt na instalację", 1.0, "instalacja")
    }
    if (q.wallChase && !q.leadInByWodKan) {
        extra("wallChase", "Wkuwanie dobiegów w ściany nośne", "ryczałt na instalację", 1.0, "instalacja")
    }
    if (q.wasteRemoval) {
        extra("waste", "Usunięcie odpadów montażowych", "ryczałt na instalację", 1.0, "instalacja")
    }
    if (q.design) {
        extra("design", "Projekt instalacji ogrzewania podłogowego", "ryczałt na instalację", 1.0, "instalacja")
    }
    if (q.warranty && find("warranty")?.zeroed == null) {
        extra(
            "warranty",
            "Dokumentacja wydłużonej gwarancji producenta",
            "kompletowanie dokumentów, zgłoszenie",
            1.0,
            "instalacja",
        )
    }

    val net = r2(rows.sumOf { it.net })

    // Materiał liczymy po tych samych pozycjach, co kwoty — robocizna wychodzi
    // odejmowaniem, żeby obie części zawsze złożyły się dokładnie na sumę.
    val material = r2(
        rows.fold(0.0) { acc, r ->
            if (r.zeroed != null) return@fold acc
            val src = find(r.key.substringBefore(":")) ?: return@fold acc
            val share = if (src.net > 0) netSplitMaterial(src) / src.net else 0.0
            acc + r.net * share
        },
    )

    return OfferScope(
        rows = rows,
        net = net,
        material = material,
        labor = r2(net - material),
        gaps = gaps.distinct(),
    )
}

/** Kwota w złotych — spójnie z kartą formuły (przecinek, bez waluty). */
fun zlText(v: Double): String {
    val whole = String.format(Locale.US, "%.2f", v).replace('.', ',')
    val parts = whole.split(',')
    val digits = parts[0].removePrefix("-")
    val grouped = digits.reversed().chunked(3).joinToString(" ").reversed()
    val sign = if (parts[0].startsWith("-")) "-" else ""
    return "$sign$grouped,${parts[1]}"
}

/** Ilość w tabeli podsumowania — całkowite bez zer po przecinku. */
fun scopeQty(v: Double?): String {
    if (v == null) return ""
    return if (v == v.roundToInt().toDouble()) "${v.roundToInt()}" else numText(v)
}
