package com.ekotak.teamtalk.domain.ufh

import com.ekotak.teamtalk.domain.model.Product
import java.util.Locale

/**
 * KATALOG CEN JEDNOSTKOWYCH — port `price-units.ts` i `price-catalog.ts`.
 *
 * Wszystkie ceny w ekotaku są prowadzone za OPAKOWANIE (zwój 600 m, rolka 50 m²,
 * pakiet 250 spinek), a formuła ceny potrzebuje jednostki. Ten plik robi to
 * przeliczenie i — co ważniejsze — mówi wprost, kiedy się NIE da: brak
 * kartoteki, brak ceny, nieczytelna wielkość opakowania. Każdy taki brak wraca
 * jako `gap` i ląduje w podsumowaniu oferty, zamiast po cichu dać 0 zł.
 *
 * W panelu katalog składa serwer i wysyła do przeglądarki gotowy. Telefon liczy
 * go sam z kartoteki Magazynu, którą i tak trzyma w cache — dzięki temu oferta
 * z kwotami otwiera się także bez zasięgu.
 */

/** Cena jednostkowa z opisem, skąd się wzięła — albo powód, dlaczego jej nie ma. */
data class UnitPrice(
    /** Cena za jednostkę (m², mb, szt.); `null` = nie da się policzyć. */
    val value: Double? = null,
    val code: String? = null,
    val name: String? = null,
    /** Cena opakowania i przelicznik — tekst do linii formuły. */
    val from: String = "—",
    /** Czego brakuje (`null` = komplet). */
    val gap: String? = null,
)

private fun missing(gap: String, name: String? = null) = UnitPrice(gap = gap, name = name)

internal fun zl(v: Double): String = String.format(Locale.US, "%.2f", v).replace('.', ',')

internal fun numFmt(v: Double, d: Int = 2): String =
    String.format(Locale.US, "%.${d}f", v).replace('.', ',')

/** Pozycja cennika montażowego / kosztowego z Warunków finansowych. */
data class PointRate(
    val id: String,
    val name: String,
    val unit: String,
    val points: Double,
    /** Nazwa zestawu („Montaż", „Biuro", „Koszty"). */
    val scheme: String = "",
)

/** Zapisany wybór materiału domyślnego Technologii: pozycja → kod kartoteki. */
typealias MaterialDefaults = Map<String, String>

/** Zapisany narzut węzła katalogu. */
data class SavedMarkup(val base: Int, val rows: Map<String, Double>)

/** Narzut, od którego startuje każda pozycja, dopóki nikt go nie zmieni. */
const val DEFAULT_MARKUP_PCT = 30

fun byCode(products: List<Product>): Map<String, Product> =
    products.filter { !it.code.isNullOrBlank() }.associateBy { it.code!! }

// ── Ceny jednostkowe materiału ──────────────────────────────────────────────

/**
 * Cena 1 mb rury OP: zwój PODSTAWOWY katalogu (nigdy awaryjny 200 m — reguła
 * „rura tylko w zwojach 600 m") podzielony przez jego długość.
 */
fun pipePricePerM(system: String, stock: Map<String, Product>): UnitPrice {
    val cat = ufhCatalog(system)
    val coil = cat?.coils?.firstOrNull { !it.fallback }
    if (cat == null || coil == null) return missing("system rur nie ma katalogu materiału")
    val p = stock[coil.code] ?: return missing("kodu ${coil.code} nie ma w Magazynie", coil.name)
    val price = p.price ?: return missing("kartoteka rury bez ceny zakupu", p.name)
    return UnitPrice(
        value = price / coil.m,
        code = coil.code,
        name = p.name,
        from = "${zl(price)} zł ÷ zwój ${coil.m} m = ${numFmt(price / coil.m, 4)} zł/mb",
    )
}

/** Ilość w opakowaniu odczytana z kartoteki — „Rolka 50 m²", „Pakiet 250 szt.". */
fun packSize(p: Product, re: Regex): Double? {
    for (raw in listOf(p.packaging, p.comparisonSize, p.name)) {
        val m = raw?.let { re.find(it) } ?: continue
        val v = m.groupValues[1].replace(',', '.').toDoubleOrNull()
        if (v != null && v > 0) return v
    }
    return null
}

private val FOIL_RE = Regex("foli", RegexOption.IGNORE_CASE)
private val CLIP_RE = Regex("spink|klips|zszywk", RegexOption.IGNORE_CASE)
private val PLATE_RE = Regex("p.yt", RegexOption.IGNORE_CASE)
private val PLATE_BUMPS_RE = Regex("wypustk", RegexOption.IGNORE_CASE)
private val ROLL_M2_RE = Regex("(\\d+(?:[.,]\\d+)?)\\s*m(?:²|2)(?![\\w])", RegexOption.IGNORE_CASE)
private val PACK_PCS_RE = Regex("(\\d+(?:[.,]\\d+)?)\\s*szt", RegexOption.IGNORE_CASE)

private fun text(p: Product): String = "${p.name} ${p.code.orEmpty()}".lowercase()

private fun sizeMm(p: Product, mm: Int): Boolean =
    Regex("\\b$mm\\s*mm\\b").containsMatchIn(text(p))

/** Dopasowanie kartoteki do pozycji domyślnej Technologii (`default-materials.ts`). */
private fun slotMatches(slotId: String, p: Product): Boolean = when (slotId) {
    "foil" -> FOIL_RE.containsMatchIn(text(p))
    "clip50" -> CLIP_RE.containsMatchIn(text(p)) && sizeMm(p, 50)
    "clip40" -> CLIP_RE.containsMatchIn(text(p)) && sizeMm(p, 40)
    else -> false
}

/** Kartoteka wskazana w Technologii dla pozycji domyślnej (folia / spinki). */
fun defaultProduct(
    slotId: String,
    products: List<Product>,
    defaults: MaterialDefaults,
): Product? {
    val saved = defaults[slotId]
    if (!saved.isNullOrBlank()) {
        val hit = products.firstOrNull { it.code == saved }
        if (hit != null) return hit
    }
    // Kolejność źródeł jak w boksie Technologii: zapis firmowy → ★ z Magazynu.
    return products.filter { slotMatches(slotId, it) }.firstOrNull { it.defaultChoice }
}

/** Cena 1 m² folii — rolka z Technologii podzielona przez metraż rolki. */
fun foilPricePerM2(products: List<Product>, defaults: MaterialDefaults): UnitPrice {
    val p = defaultProduct("foil", products, defaults)
        ?: return missing("Technologia nie ma wskazanej folii (brak zapisu i ★ w Magazynie)")
    val price = p.price ?: return missing("kartoteka folii bez ceny zakupu", p.name)
    val m2 = packSize(p, ROLL_M2_RE)
        ?: return missing("nie da się odczytać metrażu rolki z kartoteki „${p.name}”", p.name)
    return UnitPrice(
        value = price / m2,
        code = p.code,
        name = p.name,
        from = "${zl(price)} zł ÷ rolka ${numFmt(m2, 0)} m² = ${zl(price / m2)} zł/m²",
    )
}

/** Cena 1 spinki — pakiet z Technologii podzielony przez liczbę sztuk. */
fun clipPricePerPc(products: List<Product>, defaults: MaterialDefaults): UnitPrice {
    val p = defaultProduct("clip50", products, defaults)
        ?: return missing("Technologia nie ma wskazanych spinek")
    val price = p.price ?: return missing("kartoteka spinek bez ceny zakupu", p.name)
    val pcs = packSize(p, PACK_PCS_RE)
        ?: return missing("nie da się odczytać wielkości pakietu z kartoteki „${p.name}”", p.name)
    return UnitPrice(
        value = price / pcs,
        code = p.code,
        name = p.name,
        from = "${zl(price)} zł ÷ pakiet ${numFmt(pcs, 0)} szt. = ${numFmt(price / pcs, 4)} zł/szt.",
    )
}

/** Cena rozdzielacza na tyle obwodów w danym systemie. */
fun manifoldPrice(system: String, loops: Int, stock: Map<String, Product>): UnitPrice {
    val cat = ufhCatalog(system) ?: return missing("system rur nie ma katalogu rozdzielaczy")
    val item = cat.manifolds.firstOrNull { it.loops >= loops }
        ?: return missing(
            "${cat.manifoldFamily} kończy się na ${cat.manifolds.lastOrNull()?.loops ?: 0} " +
                "obwodach — tyle obwodów wymaga drugiej skrzynki",
        )
    val p = stock[item.code] ?: return missing("kodu ${item.code} nie ma w Magazynie", item.name)
    val price = p.price ?: return missing("rozdzielacz bez ceny zakupu", p.name)
    return UnitPrice(
        value = price,
        code = item.code,
        name = p.name,
        from = "${cat.manifoldFamily} ${item.loops} obw. [${item.code}] = ${zl(price)} zł",
    )
}

/** Cena jednego śrubunka systemu (na obwód wchodzą dwa). */
fun unionPrice(system: String, stock: Map<String, Product>): UnitPrice {
    val cat = ufhCatalog(system) ?: return missing("system rur nie ma katalogu")
    val union = cat.union
        ?: return missing("system nie ma kartoteki śrubunka — pozycja do założenia w Magazynie")
    val p = stock[union.code] ?: return missing("kodu ${union.code} nie ma w Magazynie", union.name)
    val price = p.price ?: return missing("śrubunek bez ceny zakupu", p.name)
    return UnitPrice(
        value = price,
        code = union.code,
        name = p.name,
        from = "${zl(price)} zł/szt. (⌀${cat.pipeMm} mm)",
    )
}

/** Cena 1 mb rury dobiegowej danej średnicy. */
fun leadInPricePerM(system: String, mm: String, stock: Map<String, Product>): UnitPrice {
    val pipe = ufhLeadInPipe(system, mm) ?: return missing("⌀$mm mm nie ma przyjętej kartoteki dobiegu")
    val p = stock[pipe.code] ?: return missing("kodu ${pipe.code} nie ma w Magazynie", pipe.name)
    val price = p.price ?: return missing("rura dobiegowa bez ceny zakupu", p.name)
    return UnitPrice(
        value = price / pipe.coilM,
        code = pipe.code,
        name = p.name,
        from = "${zl(price)} zł ÷ zwój ${pipe.coilM} m = ${zl(price / pipe.coilM)} zł/mb",
    )
}

/** Cena 1 m² płyty systemowej z wypustkami (kartoteka prowadzona w m²). */
fun platePricePerM2(products: List<Product>): UnitPrice {
    val plates = products.filter {
        PLATE_RE.containsMatchIn(it.name) && PLATE_BUMPS_RE.containsMatchIn(it.name)
    }
    val p = plates.firstOrNull { it.defaultChoice } ?: plates.firstOrNull()
        ?: return missing("Magazyn nie ma płyty systemowej z wypustkami")
    val price = p.price ?: return missing("płyta systemowa bez ceny zakupu", p.name)
    return UnitPrice(
        value = price,
        code = p.code,
        name = p.name,
        from = "${zl(price)} zł/m² [${p.code ?: "—"}]",
    )
}

/** Cena butelki inhibitora biobójczego. */
fun biocidePrice(stock: Map<String, Product>): UnitPrice {
    val p = stock[BiocideAdeyMc10.CODE]
        ?: return missing(
            "kodu ${BiocideAdeyMc10.CODE} nie ma w Magazynie",
            BiocideAdeyMc10.SHORT,
        )
    val price = p.price ?: return missing("inhibitor bez ceny zakupu", p.name)
    return UnitPrice(
        value = price,
        code = p.code,
        name = p.name,
        from = "${zl(price)} zł / ${BiocideAdeyMc10.PACKAGING} " +
            "(${BiocideAdeyMc10.LITERS_PER_PACK} l układu)",
    )
}

/** Czynnik grzewczy: cena za litr; woda sieciowa nic nie kosztuje. */
fun mediumPricePerL(key: String, stock: Map<String, Product>): UnitPrice {
    val media = HEAT_MEDIA.firstOrNull { it.key == key } ?: HEAT_MEDIA[0]
    val code = media.code
    val packLiters = media.packLiters
    if (code == null || packLiters == null) {
        return UnitPrice(value = 0.0, name = media.label, from = "woda z sieci — nic nie kupujemy")
    }
    val p = stock[code] ?: return missing("kodu $code nie ma w Magazynie", media.label)
    val price = p.price ?: return missing("czynnik bez ceny zakupu", p.name)
    return UnitPrice(
        value = price / packLiters,
        code = p.code,
        name = p.name,
        from = "${zl(price)} zł ÷ $packLiters l = ${zl(price / packLiters)} zł/l",
    )
}

// ── Katalog dla całej listy wariantów ───────────────────────────────────────

/** Szafka dobrana dla wariantu — gotowa pozycja, bez powtarzania doboru. */
data class CabinetEntry(
    val code: String? = null,
    val name: String = "Szafka",
    val price: Double? = null,
    val maxLoops: Int? = null,
    val gap: String? = null,
)

/** Rodzaj skrzynki dla ceny — audyt rozróżnia ścianę działową i nośną, cennik nie. */
enum class CabinetKind(val wire: String) {
    BRAK("brak"),
    NATYNKOWA("natynkowa"),
    PODTYNKOWA("podtynkowa"),
}

/** Rodzaj skrzynki w słowniku audytu — `cabinetMount` rozpoznaje montaż po tych zwrotach. */
fun boxTypeFor(kind: CabinetKind): String = when (kind) {
    CabinetKind.NATYNKOWA -> "natynkowa"
    CabinetKind.PODTYNKOWA -> "podtynkowa w ścianie nośnej"
    CabinetKind.BRAK -> "brak"
}

fun cabinetKey(kind: CabinetKind, control: Boolean, loops: Int): String =
    "${kind.wire}|${if (control) "auto" else "std"}|$loops"

fun pairKey(a: String, b: Any): String = "$a|$b"

/** Zakres obwodów, dla których w ogóle liczymy cenę rozdzielacza i szafki. */
val GRID_LOOPS: List<Int> = (2..15).toList()

data class PriceCatalog(
    val pipe: Map<String, UnitPrice>,
    val manifold: Map<String, UnitPrice>,
    val union: Map<String, UnitPrice>,
    val leadIn: Map<String, UnitPrice>,
    val cabinet: Map<String, CabinetEntry>,
    val medium: Map<String, UnitPrice>,
    val foil: UnitPrice,
    val clip: UnitPrice,
    val plate: UnitPrice,
    val biocide: UnitPrice,
    /** Cennik montażowy („Montaż" + „Biuro") i kwotowy („Koszty"). */
    val rates: List<PointRate>,
    val costs: List<PointRate>,
    val brand: CabinetBrand,
)

/**
 * Katalog cen dla całej listy wariantów. `products` to CAŁY Magazyn (rura
 * dobiegowa i czynnik bywają założone pod inną instalacją), `installProducts` to
 * pozycje ogrzewania podłogowego — z nich idą materiały domyślne Technologii,
 * bo tam dopasowanie jest po nazwie.
 */
fun buildCatalog(
    products: List<Product>,
    installProducts: List<Product>,
    defaults: MaterialDefaults,
    brand: CabinetBrand,
    rates: List<PointRate>,
    costs: List<PointRate>,
): PriceCatalog {
    val stock = byCode(products)
    val pipe = HashMap<String, UnitPrice>()
    val union = HashMap<String, UnitPrice>()
    val manifold = HashMap<String, UnitPrice>()
    val leadIn = HashMap<String, UnitPrice>()

    for (sys in com.ekotak.teamtalk.domain.model.UFH_PIPE_SYSTEMS) {
        pipe[sys.code] = pipePricePerM(sys.code, stock)
        union[sys.code] = unionPrice(sys.code, stock)
        for (loops in GRID_LOOPS) {
            manifold[pairKey(sys.code, loops)] = manifoldPrice(sys.code, loops, stock)
        }
        for (mm in UFH_LEAD_IN_PIPE_MM) {
            leadIn[pairKey(sys.code, mm)] = leadInPricePerM(sys.code, mm, stock)
        }
    }

    val cabinet = HashMap<String, CabinetEntry>()
    for (kind in CabinetKind.entries) {
        for (control in listOf(false, true)) {
            for (loops in GRID_LOOPS) {
                val key = cabinetKey(kind, control, loops)
                if (kind == CabinetKind.BRAK) {
                    cabinet[key] = CabinetEntry(name = "Brak skrzynki", price = 0.0)
                    continue
                }
                val pick = pickCabinet(loops, boxTypeFor(kind), CabinetChoice(brand, control))
                if (pick == null) {
                    cabinet[key] = CabinetEntry(
                        gap = "dobór nie znalazł szafki dla tego wariantu",
                    )
                    continue
                }
                val p = stock[pick.item.code]
                cabinet[key] = CabinetEntry(
                    code = pick.item.code,
                    name = p?.name ?: pick.item.name,
                    price = p?.price,
                    maxLoops = pick.item.maxLoops,
                    gap = when {
                        p == null -> "kodu ${pick.item.code} nie ma w Magazynie"
                        p.price == null -> "szafka bez ceny zakupu"
                        pick.tooSmall -> "nawet największa szafka rodziny nie unosi tylu obwodów"
                        else -> null
                    },
                )
            }
        }
    }

    val medium = HEAT_MEDIA.associate { it.key to mediumPricePerL(it.key, stock) }

    return PriceCatalog(
        pipe = pipe,
        manifold = manifold,
        union = union,
        leadIn = leadIn,
        cabinet = cabinet,
        medium = medium,
        foil = foilPricePerM2(installProducts, defaults),
        clip = clipPricePerPc(installProducts, defaults),
        plate = platePricePerM2(installProducts),
        biocide = biocidePrice(stock),
        rates = rates,
        costs = costs,
        brand = brand,
    )
}

/** Katalog cen jednostkowych węzła + jego narzut — wejście całego rachunku. */
data class OfferPricing(val catalog: PriceCatalog, val markup: SavedMarkup)
