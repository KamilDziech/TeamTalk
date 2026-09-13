package com.ekotak.teamtalk.domain.ufh

/**
 * PODEJŚCIA do rozdzielaczy — rura od źródła ciepła do każdej skrzynki. Port
 * `ufh-manifold-supply.ts`.
 *
 * To NIE jest „dobieg" pętli: tamten łączy rozdzielacz z pętlą (⌀16/18), ten łączy
 * ŹRÓDŁO z rozdzielaczem (⌀25/32 z „Danych instalacji"). Reguły:
 *  • trasa w poziomie = linia prosta źródło → kropka × 1,25,
 *  • każda różnica kondygnacji = +3 m trasy (pion),
 *  • trasa × 2 (zasilanie + powrót), na każdy rozdzielacz +3 mb ryczałtem,
 *  • bez zaznaczonego źródła podsumowanie jest zablokowane.
 *
 * Rozdzielacz na innej kondygnacji niż źródło: rzuty są wzajemnie wypoziomowane,
 * więc położenie źródła przenosimy po współrzędnych względnych i mierzymy skalą
 * kondygnacji rozdzielacza.
 */

const val SUPPLY_TRIPS = 2
const val SUPPLY_SLACK = 1.25
const val SUPPLY_FLOOR_GAP_M = 3
const val SUPPLY_RESERVE_M = 3

/** Kondygnacja w rachunku podejść. */
data class SupplyFloor(
    /** Podpis kondygnacji („Kondygnacja 2 — Piętro"). */
    val label: String,
    val marks: List<ManifoldMark>,
    val scale: PlanScale?,
    /** Rodzaj skrzynki kondygnacji — fallback dla kropek bez własnego typu. */
    val boxType: String,
    /** Kropka źródła ciepła, jeśli stoi na TEJ kondygnacji. */
    val heatSource: HeatSourceMark?,
)

data class SupplyItem(
    val key: String,
    val floorIndex: Int,
    val markIndex: Int,
    /** Krótki podpis pozycji („K2 · R1"). */
    val label: String,
    val floorLabel: String,
    val boxType: String,
    /** Odległość w poziomie źródło → rozdzielacz [m]; `null` = brak skali rzutu. */
    val horizM: Double?,
    /** Ile kondygnacji dzieli źródło od rozdzielacza (0 = ta sama). */
    val floorGap: Int,
    /** Rura na to podejście [mb] — zasilanie i powrót razem, z zapasami. */
    val mb: Double?,
)

data class SupplyPlan(
    /** Da się policzyć komplet podejść. */
    val ok: Boolean,
    /** Co blokuje wynik — do pokazania zamiast liczb. */
    val problem: String?,
    /** Kondygnacja ze źródłem (−1 = nie zaznaczone). */
    val sourceFloor: Int,
    val items: List<SupplyItem>,
    /** Ile podejść (1 na rozdzielacz). */
    val count: Int,
    /** Ile rur schodzi razem (2 × liczba podejść). */
    val pipes: Int,
    /** Rura podejściowa razem [mb]. */
    val total: Double,
)

private fun planDistM(a: PlanPoint, b: PlanPoint, scale: PlanScale): Double =
    segLen(a, b, scale.aspect) * (cmPerUnit(scale) / 100)

/** Rura jednego podejścia [mb] z odległości w poziomie i różnicy kondygnacji. */
fun supplyPipeM(horizM: Double, floorGap: Int): Double {
    val route = horizM * SUPPLY_SLACK + floorGap * SUPPLY_FLOOR_GAP_M
    return r1(route * SUPPLY_TRIPS + SUPPLY_RESERVE_M)
}

/** Podejścia źródło → rozdzielacze dla całego budynku. */
fun supplyPlan(floors: List<SupplyFloor>): SupplyPlan {
    val sourceFloor = sourceFloorIndex(floors.map { it.heatSource })
    val source = if (sourceFloor >= 0) floors[sourceFloor].heatSource else null
    val marksTotal = floors.sumOf { it.marks.size }

    if (source == null) {
        return SupplyPlan(
            ok = false,
            problem = "Brak źródła ciepła na rzucie — zaznacz je zieloną kropką (tryb „Źródło ciepła” " +
                "w powiększeniu rzutu). Bez niego nie ma od czego mierzyć podejść do rozdzielaczy.",
            sourceFloor = -1,
            items = emptyList(),
            count = 0,
            pipes = 0,
            total = 0.0,
        )
    }
    if (marksTotal == 0) {
        return SupplyPlan(
            ok = false,
            problem = "Źródło ciepła zaznaczone, ale na rzutach nie ma ani jednej kropki rozdzielacza — " +
                "nie ma do czego prowadzić podejść.",
            sourceFloor = sourceFloor,
            items = emptyList(),
            count = 0,
            pipes = 0,
            total = 0.0,
        )
    }

    val items = ArrayList<SupplyItem>()
    val noScale = ArrayList<String>()
    floors.forEachIndexed { floorIndex, f ->
        if (f.marks.isEmpty()) return@forEachIndexed
        val scale = f.scale
        val ready = scaleReady(scale)
        if (!ready) noScale += f.label
        f.marks.forEachIndexed { markIndex, m ->
            val floorGap = kotlin.math.abs(floorIndex - sourceFloor)
            val horizM = if (ready) r1(planDistM(source.point, m.point, scale!!)) else null
            items += SupplyItem(
                key = "$floorIndex-${m.id.ifEmpty { markIndex.toString() }}",
                floorIndex = floorIndex,
                markIndex = markIndex,
                label = "K${floorIndex + 1} · ${manifoldShort(markIndex)}",
                floorLabel = f.label,
                boxType = markBoxType(m, f.boxType),
                horizM = horizM,
                floorGap = floorGap,
                mb = horizM?.let { supplyPipeM(it, floorGap) },
            )
        }
    }

    var sum = 0.0
    for (it in items) sum += it.mb ?: 0.0
    val problem = if (noScale.isNotEmpty()) {
        "Brak kalibracji skali: ${noScale.joinToString(", ")} — podejścia z tych kondygnacji nie są policzone."
    } else {
        null
    }
    return SupplyPlan(
        ok = problem == null,
        problem = problem,
        sourceFloor = sourceFloor,
        items = items,
        count = items.size,
        pipes = items.size * SUPPLY_TRIPS,
        total = r1(sum),
    )
}

/** Wzór do podpowiedzi pod listą podejść. */
const val SUPPLY_FORMULA =
    "podejście = (odległość źródło → rozdzielacz × 1,25 + 3 m × różnica kondygnacji) × 2 " +
        "(zasilanie i powrót) + 3 mb zapasu na rozdzielacz"

data class SupplyPipeInfo(
    /** Pełna nazwa pozycji („rura ⌀32 PEX-AL-PEX DIAMOND"). */
    val name: String,
    val producer: String,
    /** Kod kartoteki z katalogu dobiegów; `null` = nie ma czego zamówić. */
    val code: String?,
    /** Średnica nie wybrana w „Danych instalacji". */
    val missingMm: Boolean,
    /** Pod tę średnicę nie mamy przyjętej kartoteki (dziś ⌀40). */
    val missingProduct: Boolean,
)

/**
 * Czym prowadzimy podejście — rurą dobiegową z katalogu dobiegów systemu rur.
 * Dziś wszystkie systemy chodzą na DIAMOND PEX-AL-PEX, więc producent i materiał
 * są stałe; kod bierzemy z [ufhLeadInPipe] i NIE zmyślamy go dla ⌀40.
 */
fun supplyPipeInfo(leadInPipeMm: String, system: String?): SupplyPipeInfo {
    val producer = "DIAMOND"
    val material = "PEX-AL-PEX"
    val mm = leadInPipeMm.trim()
    val pipe = if (mm.isNotEmpty()) ufhLeadInPipe(system, mm) else null
    return SupplyPipeInfo(
        name = if (mm.isNotEmpty()) "rura ⌀$mm $material $producer" else "rura podejściowa $material $producer",
        producer = producer,
        code = pipe?.code,
        missingMm = mm.isEmpty(),
        missingProduct = pipe == null,
    )
}

/** „1 podejście / 2 podejścia / 5 podejść". */
fun supplyCountLabel(n: Int): String = plForm(n, "podejście", "podejścia", "podejść")

/** „1 rura / 2 rury / 5 rur". */
fun pipesLabel(n: Int): String = plForm(n, "rura", "rury", "rur")
