package com.ekotak.teamtalk.domain.ufh

import com.ekotak.teamtalk.domain.model.PROPOSE_UFH_PIPE_SYSTEM
import com.ekotak.teamtalk.domain.model.ufhPipeSystem
import com.ekotak.teamtalk.domain.model.ufhPipeSystemResolved
import kotlin.math.ceil

/**
 * KATALOGI MATERIAŁU ogrzewania podłogowego — port `ufh-systems.ts`,
 * `ufh-kan-catalog.ts`, `ufh-rzt.ts`, `ufh-kan-rzt.ts`, `ufh-lead-in.ts`,
 * `ufh-cabinets.ts` i `ufh-water-chemistry.ts` z panelu.
 *
 * Kody są PRAWDZIWYMI kodami kartotek Magazynu (`products.code`) — po nich
 * wycena szuka ceny zakupu. Dane trzymamy 1:1 z panelem: rozjazd choćby
 * w jednym kodzie znaczy inną cenę oferty na telefonie niż przy biurku.
 *
 * Zmiana w panelu MUSI wejść i tutaj — to jedyne miejsce w aplikacji mobilnej,
 * gdzie te tabele żyją.
 */

// ── System rur: wielkości pochodne ───────────────────────────────────────────

/** Maksymalna długość JEDNEJ pętli [mb] wg średnicy (dobieg wlicza się w limit). */
private val LOOP_MAX_M_BY_MM = mapOf(16 to 100.0, 18 to 120.0)

/** Standardowa grubość ścianki [mm] wg średnicy — rury 16×2,0 i 18×2,0. */
private val PIPE_WALL_MM_BY_MM = mapOf(16 to 2.0, 18 to 2.0)

/** Domyślna średnica, gdy system nie jest jeszcze wybrany. */
private const val DEFAULT_PIPE_MM = 16

/** Średnica rury wybranego systemu; brak wyboru → domyślna. */
fun ufhPipeMm(system: String?): Int = ufhPipeSystem(system)?.pipeMm ?: DEFAULT_PIPE_MM

/** Limit długości pętli [mb] dla wybranego systemu (dobieg wliczony). */
fun ufhLoopMaxM(system: String?): Double = LOOP_MAX_M_BY_MM[ufhPipeMm(system)] ?: 100.0

/** Grubość ścianki rury [mm] wybranego systemu. */
fun ufhPipeWallMm(system: String?): Double = PIPE_WALL_MM_BY_MM[ufhPipeMm(system)] ?: 2.0

/** Czy audyt zostawił dobór systemu nam („Zaproponuj"). */
fun ufhIsProposedPipeSystem(system: String?): Boolean =
    system?.trim() == PROPOSE_UFH_PIPE_SYSTEM

/**
 * Etykieta systemu W OFERCIE — inna niż na liście wyboru audytu.
 *
 * Formularz audytu pokazuje „Zaproponuj — system dobiera ekotak", bo to jest
 * odpowiedź audytora. Oferta i specyfikacja mają powiedzieć klientowi, CO
 * DOSTANIE, więc rozwiązujemy wybór do systemu, który proponujemy (1:1
 * z `ufhPipeSystemLabel` panelu).
 */
fun offerPipeSystemLabel(code: String?): String =
    ufhPipeSystem(code)?.label ?: code.orEmpty()

// ── Katalog materiału wariantowego (rozdzielacz, śrubunek, zwoje) ────────────

/** Pozycja katalogowa = kartoteka Magazynu. */
data class UfhItem(val code: String, val name: String, val short: String)

data class UfhManifold(val loops: Int, val code: String, val name: String, val short: String)

data class UfhCoil(
    val m: Int,
    val code: String,
    val name: String,
    val short: String,
    /** Zwój awaryjny — kartoteka istnieje, ale dobór po nią NIE sięga. */
    val fallback: Boolean = false,
)

/** Materiał, który wynika z systemu rur — jeden komplet na system. */
data class UfhSystemCatalog(
    val systems: List<String>,
    val brand: String,
    val manifoldFamily: String,
    val pipeMm: Int,
    val manifolds: List<UfhManifold>,
    val union: UfhItem?,
    val coils: List<UfhCoil>,
)

private fun kanManifoldName(loops: Int): String {
    val word = if (loops in 2..4) "obwody" else "obwodów"
    return "Rozdzielacz InoxFlow z zaworami do siłowników i przepływomierzami, " +
        "$loops $word [UFST-$loops]"
}

/** Rozdzielacze InoxFlow UFST (zawory do siłowników + przepływomierze). */
private val KAN_MANIFOLDS: List<UfhManifold> = listOf(
    UfhManifold(2, "1316157077", kanManifoldName(2), "UFST-2"),
    UfhManifold(3, "1316157078", kanManifoldName(3), "UFST-3"),
    UfhManifold(4, "1316157079", kanManifoldName(4), "UFST-4"),
    UfhManifold(5, "1316157080", kanManifoldName(5), "UFST-5"),
    UfhManifold(6, "1316157081", kanManifoldName(6), "UFST-6"),
    UfhManifold(7, "1316157082", kanManifoldName(7), "UFST-7"),
    UfhManifold(8, "1316157083", kanManifoldName(8), "UFST-8"),
    UfhManifold(9, "1316157084", kanManifoldName(9), "UFST-9"),
    UfhManifold(10, "1316157085", kanManifoldName(10), "UFST-10"),
    UfhManifold(11, "1316157086", kanManifoldName(11), "UFST-11"),
    UfhManifold(12, "1316157087", kanManifoldName(12), "UFST-12"),
)

private val KAN_UNION_16 = UfhItem(
    code = "1110271010",
    name = "Śrubunek mosiężny GW do rur PERT i PEXC - 16×2,0 G¾\"",
    short = "Śrubunek 16×2,0 G¾\"",
)

private val KAN_UNION_18 = UfhItem(
    code = "1110271006",
    name = "Śrubunek mosiężny GW do rur PERT i PEXC - 18×2,0 G¾\"",
    short = "Śrubunek 18×2,0 G¾\"",
)

private val KAN_COILS_16 = listOf(
    UfhCoil(600, "1829198223", "Rura bluePERT - zwój 600 m - 16×2,0", "bluePERT 16×2,0 — 600 m"),
    UfhCoil(
        200,
        "1829198222",
        "Rura bluePERT - zwój 200 m - 16×2,0",
        "bluePERT 16×2,0 — 200 m",
        fallback = true,
    ),
)

private val KAN_COILS_18 = listOf(
    UfhCoil(600, "1829198226", "Rura bluePERT - zwój 600 m - 18×2,0", "bluePERT 18×2,0 — 600 m"),
    UfhCoil(
        200,
        "1829198225",
        "Rura bluePERT - zwój 200 m - 18×2,0",
        "bluePERT 18×2,0 — 200 m",
        fallback = true,
    ),
)

/** Kod kartoteki rozdzielacza TER/RZT z cennika Tokmet (2 → `PTNCHE5002S+`). */
private fun rztManifoldCode(loops: Int): String =
    "PTNCHE50${loops.toString().padStart(2, '0')}S+"

/** Rozdzielacze TER/RZT — 2…15 obwodów. */
private val RZT_MANIFOLDS: List<UfhManifold> = (2..15).map { loops ->
    val word = if (loops in 2..4) "obwody" else "obwodów"
    UfhManifold(
        loops = loops,
        code = rztManifoldCode(loops),
        name = "Rozdzielacz podłogowy nierdzewny TER/RZT, $loops $word [${rztManifoldCode(loops)}]",
        short = "RZT-$loops",
    )
}

private val RZT_UNION = UfhItem(
    code = "RZT-SRUBUNEK-16X2-G34",
    name = "Śrubunek mosiężny GW do rur PE-RT — 16×2,0 G¾\" (TER/RZT)",
    short = "Śrubunek 16×2,0 G¾\" (RZT)",
)

private val RZT_COILS = listOf(
    UfhCoil(
        600,
        "RURA-PERT-EVOH-16X2-600",
        "Rura PERT/EVOH/PERT 16×2 — zwój 600 m",
        "Pe-rt EVOH 16×2 — 600 m",
    ),
)

private val KAN_CATALOG_16 = UfhSystemCatalog(
    systems = listOf("kan-therm-16"),
    brand = "KAN-therm",
    manifoldFamily = "InoxFlow UFST",
    pipeMm = 16,
    manifolds = KAN_MANIFOLDS,
    union = KAN_UNION_16,
    coils = KAN_COILS_16,
)

private val KAN_CATALOG_18 = UfhSystemCatalog(
    systems = listOf("kan-therm-18"),
    brand = "KAN-therm",
    manifoldFamily = "InoxFlow UFST",
    pipeMm = 18,
    manifolds = KAN_MANIFOLDS,
    union = KAN_UNION_18,
    coils = KAN_COILS_18,
)

private val RZT_CATALOG_16 = UfhSystemCatalog(
    systems = listOf("pert-evoh-16"),
    brand = "TER / RZT",
    manifoldFamily = "TER/RZT",
    pipeMm = 16,
    manifolds = RZT_MANIFOLDS,
    union = RZT_UNION,
    coils = RZT_COILS,
)

/** Wariant mieszany: rura KAN-therm ⌀16 na rozdzielaczu nierdzewnym TER/RZT. */
private val KAN_RZT_CATALOG_16 = UfhSystemCatalog(
    systems = listOf("kan-16-rzt"),
    brand = "KAN-therm + RZT",
    manifoldFamily = "TER/RZT",
    pipeMm = 16,
    manifolds = RZT_MANIFOLDS,
    union = RZT_UNION,
    coils = KAN_COILS_16,
)

val UFH_CATALOGS: List<UfhSystemCatalog> = listOf(
    KAN_CATALOG_16,
    KAN_CATALOG_18,
    RZT_CATALOG_16,
    KAN_RZT_CATALOG_16,
)

/** Katalog dla systemu z audytu; `null` = systemu jeszcze nie rozpisaliśmy. */
fun ufhCatalog(system: String?): UfhSystemCatalog? {
    val code = ufhPipeSystemResolved(system)
    if (code.isEmpty()) return null
    return UFH_CATALOGS.firstOrNull { it.systems.contains(code) }
}

/** Ile śrubunków schodzi na jeden obwód (zasilanie + powrót). */
const val UNIONS_PER_LOOP = 2

// ── Dobieg do rozdzielacza (rura ⌀25/32) ────────────────────────────────────

data class UfhLeadInPipe(val mm: String, val code: String, val name: String, val coilM: Int)

private val LEAD_IN_PIPES = listOf(
    UfhLeadInPipe(
        mm = "25",
        code = "5907547666162",
        name = "Rura wielowarstwowa PE-Xb/Al/PE-Xb 25×2,5 — zwój 50 m",
        coilM = 50,
    ),
    UfhLeadInPipe(
        mm = "32",
        code = "5907547666179",
        name = "Rura wielowarstwowa PE-Xb/Al/PE-Xb 32×3 — zwój 25 m",
        coilM = 25,
    ),
)

/** Systemy, które biorą katalog dobiegu DIAMOND PEX-AL-PEX (dziś: wszystkie). */
private val LEAD_IN_SYSTEMS = listOf("pert-evoh-16", "kan-16-rzt", "kan-therm-16", "kan-therm-18")

/**
 * Kartoteka dobiegu dla systemu i średnicy z audytu. `null` = albo system nie ma
 * katalogu, albo tej średnicy nie kupujemy (dziś ⌀40) — mówimy o tym wprost,
 * zamiast podstawiać najbliższą.
 */
fun ufhLeadInPipe(system: String?, mm: String): UfhLeadInPipe? {
    val code = ufhPipeSystemResolved(system)
    if (code.isEmpty() || !LEAD_IN_SYSTEMS.contains(code)) return null
    return LEAD_IN_PIPES.firstOrNull { it.mm == mm.trim() }
}

/** Średnice rury dobiegowej z „Danych instalacji". */
val UFH_LEAD_IN_PIPE_MM = listOf("25", "32")

// ── Szafki rozdzielaczy ─────────────────────────────────────────────────────

/** Producenci szafek, których mamy w Magazynie z kompletem rozmiarów. */
enum class CabinetBrand(val id: String, val label: String) {
    TEIRA("teira", "TEIRA"),
    KAN_THERM("kan-therm", "KAN-therm"),
}

/** Producent domyślny — TEIRA (decyzja usera 2026-08-22). */
val DEFAULT_CABINET_BRAND = CabinetBrand.TEIRA

/** Wartość ustawienia firmowego → producent. Nieznana albo pusta = domyślny. */
fun cabinetBrandOf(raw: String?): CabinetBrand =
    CabinetBrand.entries.firstOrNull { it.id == raw?.trim() } ?: DEFAULT_CABINET_BRAND

/** Sposób montażu szafki — wynika z „Rodzaju skrzynki" w audycie. */
enum class CabinetMount { NATYNKOWA, PODTYNKOWA, DZIALOWA }

/** Rodzaj skrzynki z audytu → montaż (`null` = „brak", nic nie dobieramy). */
fun cabinetMount(boxType: String): CabinetMount? {
    val t = boxType.trim().lowercase()
    return when {
        t.startsWith("natynkowa") -> CabinetMount.NATYNKOWA
        t.contains("działowej") -> CabinetMount.DZIALOWA
        t.contains("nośnej") -> CabinetMount.PODTYNKOWA
        else -> null
    }
}

/** Minimalny zapas obwodów ponad rozdzielacz. */
const val CABINET_RESERVE_MIN = 1

data class CabinetItem(
    val brand: CabinetBrand,
    val mount: CabinetMount,
    /** Przygotowana pod listwę sterującą (półka / zaczep / szyna DIN). */
    val control: Boolean,
    val code: String,
    val name: String,
    val short: String,
    /** Szerokość korpusu [mm] — po niej idzie kolejność rozmiarów. */
    val size: Int,
    /** Max obwodów wg katalogu producenta. */
    val maxLoops: Int,
)

private fun teira(
    mount: CabinetMount,
    control: Boolean,
    code: String,
    size: Int,
    maxLoops: Int,
): CabinetItem {
    val kind = if (mount == CabinetMount.NATYNKOWA) "natynkowa" else "podtynkowa"
    val shelf = if (control) " z półką" else ""
    return CabinetItem(
        brand = CabinetBrand.TEIRA,
        mount = mount,
        control = control,
        code = code,
        short = code,
        size = size,
        maxLoops = maxLoops,
        name = "Szafka do rozdzielaczy $kind$shelf $code — $maxLoops obwodów",
    )
}

private fun kan(
    mount: CabinetMount,
    control: Boolean,
    code: String,
    short: String,
    size: Int,
    maxLoops: Int,
    name: String,
): CabinetItem = CabinetItem(CabinetBrand.KAN_THERM, mount, control, code, name, short, size, maxLoops)

/** Nazwa ECOBOX-a 1:1 z Magazynu. */
private fun ecoboxName(kind: String, short: String, maxLoops: Int): String {
    val word = if (maxLoops in 2..4) "obwody" else "obwodów"
    return "Szafka $kind do rozdzielaczy ECOBOX $short — $maxLoops $word"
}

val CABINETS: List<CabinetItem> = listOf(
    // TEIRA natynkowe ORN (bez półki)
    teira(CabinetMount.NATYNKOWA, false, "ORN-1", 385, 4),
    teira(CabinetMount.NATYNKOWA, false, "ORN-2", 485, 6),
    teira(CabinetMount.NATYNKOWA, false, "ORN-3", 615, 8),
    teira(CabinetMount.NATYNKOWA, false, "ORN-4", 760, 10),
    teira(CabinetMount.NATYNKOWA, false, "ORN-5", 845, 12),
    teira(CabinetMount.NATYNKOWA, false, "ORN-6", 1015, 14),
    teira(CabinetMount.NATYNKOWA, false, "ORN-7", 1115, 16),
    // TEIRA natynkowe z półką ORN-WS
    teira(CabinetMount.NATYNKOWA, true, "ORN-2WS", 485, 6),
    teira(CabinetMount.NATYNKOWA, true, "ORN-3WS", 615, 8),
    teira(CabinetMount.NATYNKOWA, true, "ORN-4WS", 765, 10),
    teira(CabinetMount.NATYNKOWA, true, "ORN-5WS", 845, 12),
    teira(CabinetMount.NATYNKOWA, true, "ORN-6WS", 1015, 14),
    teira(CabinetMount.NATYNKOWA, true, "ORN-7WS", 1115, 16),
    // TEIRA podtynkowe ORP (bez półki)
    teira(CabinetMount.PODTYNKOWA, false, "ORP-1", 380, 4),
    teira(CabinetMount.PODTYNKOWA, false, "ORP-2", 480, 6),
    teira(CabinetMount.PODTYNKOWA, false, "ORP-3", 610, 8),
    teira(CabinetMount.PODTYNKOWA, false, "ORP-4", 760, 10),
    teira(CabinetMount.PODTYNKOWA, false, "ORP-5", 840, 12),
    teira(CabinetMount.PODTYNKOWA, false, "ORP-6", 1010, 14),
    teira(CabinetMount.PODTYNKOWA, false, "ORP-7", 1110, 16),
    // TEIRA podtynkowe z półką ORP-WS
    teira(CabinetMount.PODTYNKOWA, true, "ORP-2WS", 480, 6),
    teira(CabinetMount.PODTYNKOWA, true, "ORP-3WS", 610, 8),
    teira(CabinetMount.PODTYNKOWA, true, "ORP-4WS", 760, 10),
    teira(CabinetMount.PODTYNKOWA, true, "ORP-5WS", 840, 12),
    teira(CabinetMount.PODTYNKOWA, true, "ORP-6WS", 1010, 14),
    teira(CabinetMount.PODTYNKOWA, true, "ORP-7WS", 1110, 16),

    // KAN-therm bez sterowania — ECOBOX (bez półki pod automatykę)
    kan(CabinetMount.NATYNKOWA, false, "1406180002", "SNE-0 385", 385, 4, ecoboxName("natynkowa", "SNE-0 385", 4)),
    kan(CabinetMount.NATYNKOWA, false, "1406180003", "SNE-1 485", 485, 6, ecoboxName("natynkowa", "SNE-1 485", 6)),
    kan(CabinetMount.NATYNKOWA, false, "1406180004", "SNE-2 615", 615, 9, ecoboxName("natynkowa", "SNE-2 615", 9)),
    kan(CabinetMount.NATYNKOWA, false, "1406180005", "SNE-3 760", 760, 12, ecoboxName("natynkowa", "SNE-3 760", 12)),
    kan(CabinetMount.NATYNKOWA, false, "1406180006", "SNE-4 845", 845, 14, ecoboxName("natynkowa", "SNE-4 845", 14)),
    kan(CabinetMount.NATYNKOWA, false, "1406180000", "SNE-5 1015", 1015, 16, ecoboxName("natynkowa", "SNE-5 1015", 16)),
    kan(CabinetMount.NATYNKOWA, false, "1406180001", "SNE-6 1150", 1150, 18, ecoboxName("natynkowa", "SNE-6 1150", 18)),
    kan(CabinetMount.PODTYNKOWA, false, "1406117001", "SPE-0 335", 335, 4, ecoboxName("podtynkowa", "SPE-0 335", 4)),
    kan(CabinetMount.PODTYNKOWA, false, "1406117002", "SPE-1 435", 435, 6, ecoboxName("podtynkowa", "SPE-1 435", 6)),
    kan(CabinetMount.PODTYNKOWA, false, "1406117003", "SPE-2 565", 565, 8, ecoboxName("podtynkowa", "SPE-2 565", 8)),
    kan(CabinetMount.PODTYNKOWA, false, "1406117004", "SPE-3 715", 715, 11, ecoboxName("podtynkowa", "SPE-3 715", 11)),
    kan(CabinetMount.PODTYNKOWA, false, "1406117005", "SPE-4 795", 795, 13, ecoboxName("podtynkowa", "SPE-4 795", 13)),
    kan(CabinetMount.PODTYNKOWA, false, "1406117006", "SPE-5 965", 965, 15, ecoboxName("podtynkowa", "SPE-5 965", 15)),
    kan(CabinetMount.PODTYNKOWA, false, "1406117000", "SPE-6 1140", 1140, 17, ecoboxName("podtynkowa", "SPE-6 1140", 17)),

    // KAN-therm ze sterowaniem — SWN-OP/SWP-OP (fabryczny zaczep na listwę)
    kan(CabinetMount.NATYNKOWA, true, "1446180000", "SWN-OP 580", 580, 5, "Szafka natynkowa do rozdzielaczy SWN-OP 580"),
    kan(CabinetMount.NATYNKOWA, true, "1446180001", "SWN-OP 780", 780, 9, "Szafka natynkowa do rozdzielaczy SWN-OP 780"),
    kan(CabinetMount.NATYNKOWA, true, "1446180002", "SWN-OP 930", 930, 12, "Szafka natynkowa do rozdzielaczy SWN-OP 930"),
    kan(CabinetMount.PODTYNKOWA, true, "1446117003", "SWP-OP 580", 580, 5, "Szafka podtynkowa do rozdzielaczy SWP-OP 580"),
    kan(CabinetMount.PODTYNKOWA, true, "1446117004", "SWP-OP 780", 780, 9, "Szafka podtynkowa do rozdzielaczy SWP-OP 780"),
    kan(CabinetMount.PODTYNKOWA, true, "1446117005", "SWP-OP 930", 930, 12, "Szafka podtynkowa do rozdzielaczy SWP-OP 930"),

    // KAN-therm do ściany działowej — Slim+ (szyna DIN pod listwę)
    kan(CabinetMount.DZIALOWA, true, "1414183018", "Slim+ 450", 450, 2, "Szafka podtynkowa Slim+ 450"),
    kan(CabinetMount.DZIALOWA, true, "1414183019", "Slim+ 550", 550, 4, "Szafka podtynkowa Slim+ 550"),
    kan(CabinetMount.DZIALOWA, true, "1414183020", "Slim+ 700", 700, 7, "Szafka podtynkowa Slim+ 700"),
    kan(CabinetMount.DZIALOWA, true, "1414183021", "Slim+ 850", 850, 10, "Szafka podtynkowa Slim+ 850"),
    kan(CabinetMount.DZIALOWA, true, "1414183022", "Slim+ 1000", 1000, 12, "Szafka podtynkowa Slim+ 1000"),
    kan(CabinetMount.DZIALOWA, true, "1414183023", "Slim+ 1200", 1200, 12, "Szafka podtynkowa Slim+ 1200"),
)

/** Odpowiedzi sterujące doborem szafki — komplet wejścia poza liczbą obwodów. */
data class CabinetChoice(val brand: CabinetBrand, val control: Boolean)

/**
 * „Planowane sterowanie temperaturą w pomieszczeniach" z audytu → tak/nie dla
 * doboru szafki. Szafce robi różnicę tylko to, CZY listwa sterująca ma gdzie
 * zamieszkać, więc oba „tak" dobierają tę samą rodzinę.
 */
fun roomControlPlanned(raw: String?): Boolean =
    (raw ?: "").trim().lowercase().startsWith("tak")

data class CabinetPick(
    val item: CabinetItem,
    /** Najmniejsza, która by się zmieściła — punkt odniesienia dla podpisu. */
    val min: CabinetItem,
    val reserve: Int,
    /** Nawet największa w rodzinie nie unosi tylu obwodów. */
    val tooSmall: Boolean,
)

/**
 * Szafka dla rozdzielacza o tylu obwodach — najmniejsza, która unosi obwody
 * plus [CABINET_RESERVE_MIN] obwodu zapasu. `null` = nie ma czego dobierać.
 */
fun pickCabinet(loops: Int, boxType: String, choice: CabinetChoice): CabinetPick? {
    val mount = cabinetMount(boxType) ?: return null
    if (loops <= 0) return null
    fun inFamily(m: CabinetMount, control: Boolean): List<CabinetItem> =
        CABINETS.filter { it.brand == choice.brand && it.mount == m && it.control == control }
            .sortedBy { it.size }

    // Ściana działowa u producenta bez płytkiej rodziny (TEIRA) — schodzimy na
    // zwykłą podtynkową, zamiast zostawić wycenę bez szafki.
    var usedMount = mount
    if (inFamily(mount, true).isEmpty() && inFamily(mount, false).isEmpty()) {
        if (mount != CabinetMount.DZIALOWA) return null
        usedMount = CabinetMount.PODTYNKOWA
        if (inFamily(usedMount, true).isEmpty() && inFamily(usedMount, false).isEmpty()) return null
    }

    var list = inFamily(usedMount, choice.control)
    if (list.isEmpty()) list = inFamily(usedMount, !choice.control)
    if (list.isEmpty()) return null

    val minIdx = list.indexOfFirst { it.maxLoops >= loops }
    if (minIdx < 0) {
        val last = list.last()
        return CabinetPick(last, last, 0, tooSmall = true)
    }
    val withReserve = list.indexOfFirst { it.maxLoops >= loops + CABINET_RESERVE_MIN }
    val idx = if (withReserve < 0) list.size - 1 else withReserve
    return CabinetPick(list[idx], list[minIdx], list[idx].maxLoops - loops, tooSmall = false)
}

// ── Chemia i czynnik grzewczy ───────────────────────────────────────────────

/** Inhibitor biobójczy — 500 ml na 125 l wody kotłowej. */
object BiocideAdeyMc10 {
    const val CODE = "5060106371805"
    const val SHORT = "ADEY MC10+ biocyd 500 ml"
    const val PACKAGING = "Butelka 500 ml"
    const val LITERS_PER_PACK = 125
}

/** Ile opakowań inhibitora kupujemy — pełne butelki, zawsze w górę. */
fun biocidePacks(liters: Int): Int? {
    if (liters <= 0) return null
    return ceil(liters.toDouble() / BiocideAdeyMc10.LITERS_PER_PACK).toInt()
}

/** „1 butelka / 2 butelki / 5 butelek" — odmiana po liczbie. */
fun bottlesLabel(n: Int): String {
    val t = n % 10
    val h = n % 100
    val word = when {
        n == 1 -> "butelka"
        t in 2..4 && h !in 12..14 -> "butelki"
        else -> "butelek"
    }
    return "$n $word"
}

/** Czynnik, którym napełniamy układ. */
data class HeatMediumOption(
    val key: String,
    val label: String,
    /** Kod kartoteki Magazynu; `null` = nie kupujemy nic (woda z sieci). */
    val code: String?,
    val isDefault: Boolean,
    /** Czy do tego czynnika dolewamy inhibitor biobójczy. */
    val withBiocide: Boolean,
    /** Ile litrów czynnika jest w jednym opakowaniu (`null` = woda z sieci). */
    val packLiters: Int?,
)

val HEAT_MEDIA: List<HeatMediumOption> = listOf(
    HeatMediumOption("mains", "Woda sieciowa (wodociągowa)", null, true, withBiocide = true, packLiters = null),
    HeatMediumOption(
        key = "demi",
        label = "Woda demineralizowana (dejonizowana)",
        code = "CHEM-WODA-DEMI-1000",
        isDefault = false,
        withBiocide = false,
        packLiters = 1000,
    ),
    HeatMediumOption(
        key = "glycol",
        label = "Glikol propylenowy -20 °C (gotowa mieszanka)",
        code = "CHEM-GLIKOL-PG20-1000",
        isDefault = false,
        withBiocide = false,
        packLiters = 1000,
    ),
)

/** Odpowiedź audytu o czynnik grzewczy → klucz z [HEAT_MEDIA]. */
fun mediumKey(raw: String?): String {
    val s = (raw ?: "").trim().lowercase()
    return when {
        s.startsWith("glikol") -> "glycol"
        s.contains("demi") -> "demi"
        else -> HEAT_MEDIA.firstOrNull { it.isDefault }?.key ?: "mains"
    }
}
