package com.ekotak.teamtalk.domain.ufh

import com.ekotak.teamtalk.domain.model.ufhHasExtendedWarranty

/**
 * FORMUŁA CENY ogrzewania podłogowego — port `price-model.ts` i `price-rates.ts`.
 *
 * Odpowiada na jedno pytanie: **ile kosztuje nas jednostka danej roboty** —
 * 1 m² podłogówki, 1 rozdzielacz, 1 kondygnacja dobiegu — zanim doliczymy
 * narzut. Każda pozycja rozpada się na trzy kolumny:
 *
 *   • materiał    — kartoteki Magazynu wskazane przez Technologię,
 *   • koszty inne — kwoty własne firmy z Warunków finansowych (zestaw „Koszty"),
 *   • robocizna   — punkty ekipy z Warunków finansowych, 1 pkt = 1 zł netto.
 *
 * Metraż audytu dokłada dopiero [offerScope] — tu liczymy ceny JEDNOSTKI.
 */

/** Ile spinek wchodzi na każdy metr bieżący rury. */
const val CLIPS_PER_PIPE_M = 5

/** Ile metrów rury dobiegowej liczymy na jedną kondygnację (15 mb × 2). */
const val LEAD_IN_ROUTE_M = 15
const val LEAD_IN_M_PER_FLOOR = LEAD_IN_ROUTE_M * 2

/** Metry bieżące rury na 1 m² podłogówki — odwrotność rozstawu. */
fun pipeMPerM2(spacingM: Double): Double = if (spacingM > 0) 1 / spacingM else 0.0

/** Etap, na którym pozycja jest wykonywana — decyduje, czy wchodzi w cenę OP. */
enum class Stage { OP, WODKAN }

/** Pozycje, które bywają robione wcześniej, przez ekipę wod-kan / od źródła. */
enum class StagedRow { MANIFOLD, CABINET, LEAD_IN, FILLING, WALL_CHASE, LEAD_IN_STYRO }

data class PriceVariant(
    val system: String,
    val spacingM: Double,
    val subfloorJoints: String,
    val loops: Int,
    val cabinetKind: CabinetKind,
    /** Szafka przygotowana pod listwę sterującą (rodzina „-WS"). */
    val cabinetControl: Boolean,
    val leadInMm: String,
    val medium: String,
    val waterLiters: Int,
    val biocide: Boolean,
    val stages: Map<StagedRow, Stage>,
    val markupBase: Int,
    val markup: Map<String, Double>,
)

/** Jedna linia w środku kolumny — składnik kosztu z własnym wzorem. */
data class PricePart(
    val label: String,
    val formula: String,
    val value: Double,
    /** Źródło: Technologia + Magazyn albo Warunki finansowe. */
    val source: String,
    val gap: String? = null,
)

/** Pozycja karty — jeden wiersz tabeli. */
data class PriceRow(
    val key: String,
    val no: String,
    val name: String,
    val unit: String,
    val material: List<PricePart>,
    val other: List<PricePart>,
    val labor: List<PricePart>,
    val materialSum: Double,
    val otherSum: Double,
    val laborSum: Double,
    val cost: Double,
    val markupPct: Double,
    val net: Double,
    /** Pozycja wypadła z ceny OP (etap wod-kan) — powód do pokazania w wierszu. */
    val zeroed: String?,
    val gaps: List<String>,
)

/** Klucz linii formuły → pozycja Warunków finansowych. */
private data class RateRule(
    val key: String,
    val label: String,
    val ids: List<String>,
    val match: Regex,
)

private fun re(p: String) = Regex(p, RegexOption.IGNORE_CASE)

/** Robocizna — zestawy punktowe „Montaż" i „Biuro" (1 pkt = 1 zł netto). */
private val LABOR_RULES = listOf(
    RateRule("pipe", "Rozwinięcie rury ogrzewania", listOf("op-rura-mb"), re("rozwini.*rur|rura.*ogrzewan")),
    RateRule("manifold", "Montaż rozdzielacza", listOf("op-rozdzielacz"), re("monta.*rozdzielacz|^rozdzielacz")),
    RateRule("cabinetSurface", "Szafka natynkowa", listOf("op-skrzynka-natynkowa"), re("(szafka|skrzynka)\\s+natynkow")),
    RateRule("cabinetFlush", "Szafka podtynkowa", listOf("op-skrzynka-podtynkowa"), re("(szafka|skrzynka)\\s+podtynkow")),
    RateRule("pressureTest", "Próba szczelności", listOf("op-proba-szczelnosci"), re("pr.ba\\s+szczelno")),
    RateRule("leadInFloor", "Dobieg OP/kondygnacja", listOf("op-dobieg-kondygnacja"), re("dobieg.*kondygnac")),
    RateRule("filling", "Napełnienie i odpowietrzenie układu", listOf("op-napelnienie-odpowietrzenie"), re("nape.nienie")),
    RateRule(
        "plate",
        "Płyta systemowa do ogrzewania z wypustkami — robocizna",
        listOf("op-plyta-systemowa-robocizna"),
        re("p.yta\\s+systemowa"),
    ),
    RateRule(
        "wasteRemoval",
        "Usunięcie odpadów montażowych na busa",
        listOf("op-usuniecie-odpadow"),
        re("usuni.cie\\s+odpad|odpad.*busa"),
    ),
    RateRule(
        "leadInStyro",
        "Dobiegi do rozdzielaczy na warstwie styropianu",
        listOf("op-dobiegi-na-styropianie"),
        re("dobieg.*styropian"),
    ),
    RateRule(
        "wallChase",
        "Wkuwanie dobiegów do rozdzielaczy w ściany nośne",
        listOf("op-wkuwanie-dobiegow"),
        re("wkuwan"),
    ),
    RateRule("foilOnly", "Sama folia", listOf("op-folia-m2"), re("sama\\s+folia|^folia")),
    RateRule("design", "Profesjonalny projekt OP", listOf("op-projekt"), re("projekt")),
    RateRule("warranty", "Przygotowanie wydłużonej gwarancji", listOf("op-gwarancja-wydluzona"), re("gwarancj")),
)

/** Koszty własne firmy — zestaw kwotowy („Koszty"), nie punkty ekipy. */
private val COST_RULES = listOf(
    RateRule(
        "fixed",
        "Koszty stałe — biuro, magazyn, podatki",
        listOf("koszt-biuro-magazyn-stale"),
        re("koszty\\s+sta"),
    ),
    RateRule("sundries", "Drobnica (izolacje, kształtki, taśmy, nypel)", emptyList(), re("drobnic")),
    RateRule("wasteCost", "Koszt odpadów montażowych", listOf("koszt-odpady-montazowe"), re("koszt.*odpad")),
    RateRule(
        "styroCost",
        "Styropian — gdy dobiegi na warstwie styropianu",
        listOf("koszt-styropian-dobiegi"),
        re("styropian"),
    ),
)

/** Pozycja cennika dla reguły; `null` = nie ma jej w Warunkach finansowych. */
private fun findRate(rates: List<PointRate>, rule: RateRule): PointRate? =
    rates.firstOrNull { rule.ids.contains(it.id) }
        ?: rates.firstOrNull { rule.match.containsMatchIn(it.name) }

/** 1 pkt = 1 zł netto — reguła firmowa, ta sama co w Warunkach finansowych. */
private fun rateAmount(rate: PointRate?): Double = rate?.points ?: 0.0

/** Zastępnik, gdy katalog nie ma wpisu dla wariantu (system spoza listy). */
private val NO_UNIT = UnitPrice(gap = "brak wpisu w katalogu cen dla tego wariantu")

private fun sum(parts: List<PricePart>): Double = r2(parts.sumOf { it.value })

private fun row(
    key: String,
    no: String,
    name: String,
    unit: String,
    material: List<PricePart> = emptyList(),
    other: List<PricePart> = emptyList(),
    labor: List<PricePart> = emptyList(),
    markupPct: Double,
    zeroed: String? = null,
): PriceRow {
    val isZeroed = zeroed != null
    val materialSum = if (isZeroed) 0.0 else sum(material)
    val otherSum = if (isZeroed) 0.0 else sum(other)
    val laborSum = if (isZeroed) 0.0 else sum(labor)
    val cost = r2(materialSum + otherSum + laborSum)
    val gaps = (material + other + labor).mapNotNull { p -> p.gap?.let { "${p.label}: $it" } }
    return PriceRow(
        key = key,
        no = no,
        name = name,
        unit = unit,
        material = material,
        other = other,
        labor = labor,
        materialSum = materialSum,
        otherSum = otherSum,
        laborSum = laborSum,
        cost = cost,
        markupPct = markupPct,
        net = r2(cost * (1 + markupPct / 100)),
        zeroed = zeroed,
        gaps = gaps,
    )
}

/**
 * Netto pozycji rozbite na dwie części oferty: materiał i robocizna. Materiałem
 * jest wyłącznie to, co kupujemy w kartotekach Magazynu — kwoty własne firmy
 * idą po stronie usługi. Robociznę liczymy odejmowaniem, żeby obie kwoty zawsze
 * złożyły się dokładnie na sumę pozycji.
 */
fun netSplitMaterial(row: PriceRow): Double = r2(row.materialSum * (1 + row.markupPct / 100))

/** Wszystkie pozycje karty — główne i dodatkowe. */
fun priceRows(catalog: PriceCatalog, variant: PriceVariant): Pair<List<PriceRow>, List<PriceRow>> {
    fun labor(key: String): Pair<RateRule, PointRate?> {
        val rule = LABOR_RULES.first { it.key == key }
        return rule to findRate(catalog.rates, rule)
    }
    fun cost(key: String): Pair<RateRule, PointRate?> {
        val rule = COST_RULES.first { it.key == key }
        return rule to findRate(catalog.costs, rule)
    }
    // Bez własnego wpisu pozycja idzie za suwakiem — to on jest wartością startową.
    fun markupOf(key: String): Double =
        variant.markup[key] ?: variant.markupBase.toDouble()

    fun stageNote(which: StagedRow): String? =
        if (variant.stages[which] == Stage.WODKAN) {
            "wykonane na etapie wod-kan / źródła ciepła — nie wchodzi w cenę ogrzewania podłogowego"
        } else {
            null
        }

    /** Linia robocizny z cennika montażowego (1 pkt = 1 zł). */
    fun laborPart(key: String, qty: Double, qtyText: String): PricePart {
        val (rule, rate) = labor(key)
        val pkt = rateAmount(rate)
        return PricePart(
            label = rate?.name ?: rule.label,
            formula = if (rate != null) {
                "$qtyText × ${numFmt(rate.points)} pkt = ${zl(pkt * qty)} zł"
            } else {
                "brak pozycji w Warunkach finansowych (${rule.label})"
            },
            value = r2(pkt * qty),
            source = "fin",
            gap = if (rate != null) null else "nie ma pozycji „${rule.label}” w zestawach „Montaż”/„Biuro”",
        )
    }

    /** Linia kosztu własnego firmy z zestawu kwotowego. */
    fun costPart(key: String): PricePart {
        val (rule, rate) = cost(key)
        val kwota = rateAmount(rate)
        return PricePart(
            label = rate?.name ?: rule.label,
            formula = if (rate != null) "${zl(kwota)} zł / instalację" else "brak pozycji w zestawie „Koszty”",
            value = r2(kwota),
            source = "fin",
            gap = if (rate != null) null else "nie ma pozycji „${rule.label}” w zestawie kosztowym",
        )
    }

    val mbPerM2 = pipeMPerM2(variant.spacingM)
    val waste = wasteFactor(variant.subfloorJoints)
    val pipe = catalog.pipe[variant.system] ?: NO_UNIT
    val foil = catalog.foil
    val clip = catalog.clip
    val spacingCm = jsRound(variant.spacingM * 100).toInt()

    // ——— 1. Koszty stałe ———
    val fixed = row(
        key = "fixed",
        no = "1",
        name = "Koszty stałe",
        unit = "zł / instalację",
        other = listOf(costPart("fixed"), costPart("sundries")),
        markupPct = markupOf("fixed"),
    )

    // ——— 2. Wykonanie instalacji (1 m²) ———
    val pipeM = mbPerM2 * waste
    val clips = mbPerM2 * CLIPS_PER_PIPE_M
    val perM2 = row(
        key = "perM2",
        no = "2",
        name = "Wykonanie instalacji",
        unit = "zł / m²",
        material = listOf(
            PricePart(
                label = pipe.name ?: "Rura systemu",
                formula = "1 ÷ ${numFmt(variant.spacingM, 2)} m = ${numFmt(mbPerM2)} mb × " +
                    "${numFmt(waste, 2)} (odpad ${wastePct(variant.subfloorJoints)} %) = " +
                    "${numFmt(pipeM)} mb × ${pipe.value?.let { numFmt(it, 4) } ?: "—"} zł/mb" +
                    (pipe.value?.let { " → ${zl(pipeM * it)} zł" } ?: ""),
                value = pipe.value?.let { r2(pipeM * it) } ?: 0.0,
                source = "tech",
                gap = pipe.gap,
            ),
            PricePart(
                label = foil.name ?: "Folia",
                formula = "1 m² × ${foil.value?.let { zl(it) } ?: "—"} zł/m²  ·  ${foil.from}",
                value = foil.value?.let { r2(it) } ?: 0.0,
                source = "tech",
                gap = foil.gap,
            ),
            PricePart(
                label = clip.name ?: "Spinki do takera",
                formula = "${numFmt(mbPerM2)} mb × $CLIPS_PER_PIPE_M szt. = ${numFmt(clips, 1)} szt. × " +
                    "${clip.value?.let { numFmt(it, 4) } ?: "—"} zł/szt." +
                    (clip.value?.let { " → ${zl(clips * it)} zł" } ?: ""),
                value = clip.value?.let { r2(clips * it) } ?: 0.0,
                source = "tech",
                gap = clip.gap,
            ),
        ),
        labor = listOf(laborPart("pipe", mbPerM2, "${numFmt(mbPerM2)} mb")),
        markupPct = markupOf("perM2"),
    )

    // ——— 3. Montaż rozdzielacza ———
    val manifold = catalog.manifold[pairKey(variant.system, variant.loops)] ?: NO_UNIT
    val union = catalog.union[variant.system] ?: NO_UNIT
    val unions = variant.loops * UNIONS_PER_LOOP
    val manifoldRow = row(
        key = "manifold",
        no = "3",
        name = "Montaż rozdzielacza",
        unit = "zł / szt. (${variant.loops} obw.)",
        material = listOf(
            PricePart(
                label = manifold.name ?: "Rozdzielacz",
                formula = manifold.from,
                value = manifold.value ?: 0.0,
                source = "tech",
                gap = manifold.gap,
            ),
            PricePart(
                label = union.name ?: "Śrubunek",
                formula = "${variant.loops} obw. × $UNIONS_PER_LOOP = $unions szt. × " +
                    "${union.value?.let { zl(it) } ?: "—"} zł" +
                    (union.value?.let { " → ${zl(unions * it)} zł" } ?: ""),
                value = union.value?.let { r2(unions * it) } ?: 0.0,
                source = "tech",
                gap = union.gap,
            ),
        ),
        labor = listOf(laborPart("manifold", 1.0, "1 szt.")),
        markupPct = markupOf("manifold"),
        zeroed = stageNote(StagedRow.MANIFOLD),
    )

    // ——— 4. Montaż skrzynki ———
    val cabinet = catalog.cabinet[
        cabinetKey(variant.cabinetKind, variant.cabinetControl, variant.loops),
    ]
    val cabinetLaborKey = if (variant.cabinetKind == CabinetKind.PODTYNKOWA) {
        "cabinetFlush"
    } else {
        "cabinetSurface"
    }
    val cabinetRow = row(
        key = "cabinet",
        no = "4",
        name = "Montaż skrzynki",
        unit = if (variant.cabinetKind == CabinetKind.BRAK) {
            "zł / szt. (brak skrzynki)"
        } else {
            "zł / szt. (${variant.loops} obw.)"
        },
        material = if (variant.cabinetKind == CabinetKind.BRAK) {
            listOf(
                PricePart(
                    "Brak skrzynki",
                    "rozdzielacz bez szafki — nic nie kupujemy",
                    0.0,
                    "tech",
                ),
            )
        } else {
            listOf(
                PricePart(
                    label = cabinet?.name ?: "Szafka",
                    formula = if (cabinet?.code != null) {
                        "${cabinet.code} — do ${cabinet.maxLoops ?: "?"} obw." +
                            (cabinet.price?.let { " = ${zl(it)} zł" } ?: "")
                    } else {
                        "producent nie ma szafki na taki wariant"
                    },
                    value = cabinet?.price ?: 0.0,
                    source = "tech",
                    gap = cabinet?.gap,
                ),
            )
        },
        labor = if (variant.cabinetKind == CabinetKind.BRAK) {
            listOf(PricePart("Brak skrzynki", "nie ma czego montować", 0.0, "fin"))
        } else {
            listOf(laborPart(cabinetLaborKey, 1.0, "1 szt."))
        },
        markupPct = markupOf("cabinet"),
        zeroed = stageNote(StagedRow.CABINET),
    )

    // ——— 5. Sprawdzenie szczelności ———
    val pressure = row(
        key = "pressureTest",
        no = "5",
        name = "Sprawdzenie szczelności",
        unit = "zł / budynek",
        material = listOf(PricePart("Materiał", "nic nie kupujemy", 0.0, "tech")),
        labor = listOf(laborPart("pressureTest", 1.0, "1 × budynek")),
        markupPct = markupOf("pressureTest"),
    )

    // ——— 6. Dobiegi z kotłowni do rozdzielacza ———
    val leadIn = catalog.leadIn[pairKey(variant.system, variant.leadInMm)] ?: NO_UNIT
    val leadInRow = row(
        key = "leadIn",
        no = "6",
        name = "Dobiegi z kotłowni do rozdzielacza",
        unit = "zł / kondygnację",
        material = listOf(
            PricePart(
                label = leadIn.name ?: "Rura dobiegowa ⌀${variant.leadInMm}",
                formula = "$LEAD_IN_ROUTE_M mb trasy × 2 (zasilanie + powrót) = " +
                    "$LEAD_IN_M_PER_FLOOR mb × ${leadIn.value?.let { zl(it) } ?: "—"} zł/mb" +
                    (leadIn.value?.let { " → ${zl(LEAD_IN_M_PER_FLOOR * it)} zł  ·  ${leadIn.from}" } ?: ""),
                value = leadIn.value?.let { r2(LEAD_IN_M_PER_FLOOR * it) } ?: 0.0,
                source = "tech",
                gap = leadIn.gap,
            ),
        ),
        labor = listOf(laborPart("leadInFloor", 1.0, "1 kondygnacja")),
        markupPct = markupOf("leadIn"),
        zeroed = stageNote(StagedRow.LEAD_IN),
    )

    // ——— 7. Zalanie i odpowietrzenie ———
    val medium = HEAT_MEDIA.firstOrNull { it.key == variant.medium } ?: HEAT_MEDIA[0]
    val mediumPrice = catalog.medium[variant.medium] ?: NO_UNIT
    val withBiocide = medium.withBiocide && variant.biocide
    val packs = if (withBiocide) biocidePacks(variant.waterLiters) else null
    val biocide = catalog.biocide
    val fillingMaterial = ArrayList<PricePart>()
    fillingMaterial += PricePart(
        label = medium.label,
        formula = if (medium.code == null) {
            "woda z sieci — nic nie kupujemy"
        } else {
            "${variant.waterLiters} l × ${mediumPrice.value?.let { zl(it) } ?: "—"} zł/l  ·  ${mediumPrice.from}"
        },
        value = when {
            medium.code == null -> 0.0
            mediumPrice.value != null -> r2(mediumPrice.value * variant.waterLiters)
            else -> 0.0
        },
        source = "tech",
        gap = if (medium.code == null) null else mediumPrice.gap,
    )
    if (packs != null) {
        fillingMaterial += PricePart(
            label = biocide.name ?: "Inhibitor ADEY MC10+",
            formula = "⌈${variant.waterLiters} l ÷ 125 l⌉ = ${bottlesLabel(packs)} × " +
                "${biocide.value?.let { zl(it) } ?: "—"} zł" +
                (biocide.value?.let { " → ${zl(packs * it)} zł" } ?: ""),
            value = biocide.value?.let { r2(packs * it) } ?: 0.0,
            source = "tech",
            gap = biocide.gap,
        )
    }
    val fillingRow = row(
        key = "filling",
        no = "7",
        name = "Zalanie i odpowietrzenie",
        unit = "zł / instalację",
        material = fillingMaterial,
        labor = listOf(laborPart("filling", 1.0, "1 × instalacja")),
        markupPct = markupOf("filling"),
        zeroed = stageNote(StagedRow.FILLING),
    )

    // ——— Koszty dodatkowe ———
    val plate = catalog.plate
    val plateRow = row(
        key = "plate",
        no = "D1",
        name = "Płyta systemowa z wypustkami",
        unit = "zł / m²",
        material = listOf(
            PricePart(
                label = plate.name ?: "Płyta systemowa",
                formula = plate.from,
                value = plate.value ?: 0.0,
                source = "tech",
                gap = plate.gap,
            ),
        ),
        labor = listOf(laborPart("plate", 1.0, "1 m²")),
        markupPct = markupOf("plate"),
    )

    val wasteRow = row(
        key = "waste",
        no = "D2",
        name = "Całkowite usunięcie odpadów montażowych",
        unit = "zł / instalację",
        other = listOf(costPart("wasteCost")),
        labor = listOf(laborPart("wasteRemoval", 1.0, "1 × instalacja")),
        markupPct = markupOf("waste"),
    )

    val styroRow = row(
        key = "leadInStyro",
        no = "D3",
        name = "Dobiegi do rozdzielaczy na warstwie styropianu",
        unit = "zł / instalację",
        other = listOf(costPart("styroCost")),
        labor = listOf(laborPart("leadInStyro", 1.0, "1 × instalacja")),
        markupPct = markupOf("leadInStyro"),
        zeroed = stageNote(StagedRow.LEAD_IN_STYRO),
    )

    val chaseRow = row(
        key = "wallChase",
        no = "D4",
        name = "Wkuwanie dobiegów w ściany nośne",
        unit = "zł / instalację",
        material = listOf(PricePart("Materiał", "nic nie kupujemy", 0.0, "tech")),
        labor = listOf(laborPart("wallChase", 1.0, "1 × instalacja")),
        markupPct = markupOf("wallChase"),
        zeroed = stageNote(StagedRow.WALL_CHASE),
    )

    val foilOnlyRow = row(
        key = "foilOnly",
        no = "D5",
        name = "Sama folia (bez ogrzewania podłogowego)",
        unit = "zł / m²",
        material = listOf(
            PricePart(
                label = foil.name ?: "Folia",
                formula = "1 m² × ${foil.value?.let { zl(it) } ?: "—"} zł/m²  ·  ${foil.from}",
                value = foil.value?.let { r2(it) } ?: 0.0,
                source = "tech",
                gap = foil.gap,
            ),
        ),
        labor = listOf(laborPart("foilOnly", 1.0, "1 m²")),
        markupPct = markupOf("foilOnly"),
    )

    val designRow = row(
        key = "design",
        no = "D6",
        name = "Profesjonalny projekt OP",
        unit = "zł / instalację",
        material = listOf(PricePart("Materiał", "nic nie kupujemy", 0.0, "tech")),
        labor = listOf(laborPart("design", 1.0, "1 × instalacja")),
        markupPct = markupOf("design"),
    )

    /**
     * Wydłużoną gwarancję prowadzi producent na SWÓJ komplet, więc pyta o nią
     * słownik systemów (`warranty10`), a nie marka katalogu: wariant mieszany ma
     * rurę KAN na cudzych belkach i idzie na standardowych 2 latach.
     */
    val isKan = ufhHasExtendedWarranty(variant.system)
    val warrantyRow = row(
        key = "warranty",
        no = "D7",
        name = "Przygotowanie wydłużonej gwarancji",
        unit = "zł / instalację",
        material = listOf(PricePart("Materiał", "nic nie kupujemy", 0.0, "tech")),
        labor = listOf(laborPart("warranty", 1.0, "1 × instalacja")),
        markupPct = markupOf("warranty"),
        zeroed = if (isKan) {
            null
        } else {
            "gwarancję wydłużoną prowadzi KAN-therm na własny komplet rury i rozdzielacza — " +
                "wariant „${offerPipeSystemLabel(variant.system)}” idzie na standardowej gwarancji"
        },
    )

    return listOf(fixed, perM2, manifoldRow, cabinetRow, pressure, leadInRow, fillingRow) to
        listOf(plateRow, wasteRow, styroRow, chaseRow, foilOnlyRow, designRow, warrantyRow)
}
