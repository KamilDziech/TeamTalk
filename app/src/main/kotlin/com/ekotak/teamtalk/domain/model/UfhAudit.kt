package com.ekotak.teamtalk.domain.model

/**
 * Formularz audytu ogrzewania podłogowego (OP) — mobilny odpowiednik
 * `UnderfloorHeatingAuditFields` z panelu. Ten sam zestaw pytań i ten sam
 * kształt `Audit.formData`, bo audyt zrobiony w telefonie ma być w panelu
 * nieodróżnialny od zrobionego przy biurku: to z niego liczy się oferta.
 *
 * CZEGO TU NIE MA — i dlaczego: warstwa rzutu kondygnacji (kropki rozdzielaczy,
 * pomiar metrażu z obrysów, kalibracja skali, historia zmian na rzucie).
 * To rysowanie po planie budynku, palcem na 360 dp nie da się tego zrobić
 * uczciwie, a półśrodek psułby dane, z których panel liczy rurę. Zapisane
 * wartości tych pól przechodzą przez telefon NIETKNIĘTE (`UfhFloor.planJson`),
 * więc zapis z terenu nie kasuje pracy zrobionej w panelu.
 */

/** Marker rekordu formularza OP w `formData.kind` (odróżnia od audytów Heizlast). */
const val UFH_AUDIT_KIND = "underfloorHeating"

/** System ogrzewania — ustawiany ODDZIELNIE dla każdej kondygnacji. */
val UFH_SYSTEMS = listOf(
    "mokry — jastrych",
    "mokry — anhydryt",
    "system suchy",
    "frezowanie",
)

/**
 * Stary zapis miał JEDEN system na cały budynek (`formData.system`). Wartości
 * bez odpowiednika na dzisiejszej liście → puste (audytor wybiera od nowa).
 */
val UFH_LEGACY_SYSTEM_MAP = mapOf(
    "mokry (w jastrychu)" to "mokry — jastrych",
    "suchy (płyty systemowe)" to "system suchy",
)

/** Planowane sterowanie temperaturą w pomieszczeniach. */
val UFH_ROOM_CONTROLS = listOf(
    "nie (rekomendowane)",
    "tak — rozwiązanie prostsze",
    "tak — rozwiązanie premium",
)

/** Rodzaj skrzynki rozdzielacza. */
val UFH_BOX_TYPES = listOf(
    "brak",
    "natynkowa",
    "podtynkowa w ścianie działowej",
    "podtynkowa w ścianie nośnej",
)

/** Napełnienie i odpowietrzenie układu — steruje pytaniami o medium. */
val UFH_SYSTEM_FILLING = listOf(
    "po zakończeniu instalacji ogrzewania podłogowego",
    "przy montażu źródła ciepła",
)

/** Wariant napełnienia, przy którym pytamy o medium grzewcze i inhibitor. */
val UFH_FILLING_AFTER_UFH = UFH_SYSTEM_FILLING[0]

val UFH_HEAT_MEDIUM = listOf("woda sieciowa", "woda demi", "glikol")
val UFH_BIOCIDE = listOf("nie", "tak")
val UFH_WARRANTY_DOCS = listOf("nie", "tak")
val UFH_WALL_CHASE = listOf("nie", "tak")

val UFH_LEAD_IN_ROUTING = listOf(
    "położone w izolacji na chudziaku",
    "na parterze na dodatkowej warstwie styropianu lub w wyciętym styropianie",
)

val UFH_SUBFLOOR_JOINTS = listOf(
    "dopuszczalne — niezalecane",
    "niedopuszczalne",
)

val UFH_DESIGN_SCOPE = listOf(
    "własny lub uproszczony z ekotak",
    "projekt przez ekotak",
)

/** Średnica rury dobiegowej do rozdzielacza [mm]. */
val UFH_LEAD_IN_PIPE_MM = listOf("25", "32")

val UFH_PRESSURE_TEST = listOf(
    "brak próby szczelności (bardzo niezalecane)",
    "próba szczelności powietrzem z protokołem",
)

val UFH_WASTE_REMOVAL = listOf(
    "częściowe — złożenie odpadów montażowych w wyznaczonym miejscu u inwestora",
    "całkowite usunięcie odpadów przez ekotak",
)

// ── System rur i rozdzielaczy ────────────────────────────────────────────────

/**
 * Pozycja listy „System rur i rozdzielaczy". W `formData.pipeSystem` zapisujemy
 * STABILNY kod, nie etykietę — poprawka nazwy nie może unieważnić zapisanych
 * audytów.
 */
data class UfhPipeSystem(
    val code: String,
    val label: String,
    /** Średnica rury [mm] — decyduje o najgęstszym dopuszczalnym rozstawie. */
    val pipeMm: Int,
    /** Producent daje 10-letnią gwarancję pod warunkiem dokumentacji z montażu. */
    val warranty10: Boolean = false,
)

val UFH_PIPE_SYSTEMS = listOf(
    UfhPipeSystem("pert-evoh-16", "Rura Pe-rt EVOH 16 + rozdzielacz", 16),
    UfhPipeSystem("kan-16-rzt", "Rura KAN THERM 16 + rozdzielacz nierdzewny TER/RZT", 16),
    UfhPipeSystem(
        "kan-therm-16",
        "Rura KAN THERM 16 + rozdzielacz nierdzewny KAN-THERM",
        16,
        warranty10 = true,
    ),
    UfhPipeSystem(
        "kan-therm-18",
        "Rura KAN THERM 18 + rozdzielacz nierdzewny KAN-THERM",
        18,
        warranty10 = true,
    ),
)

/** System wybrany z góry w nowym audycie OP. */
const val DEFAULT_UFH_PIPE_SYSTEM = "pert-evoh-16"

/** Audytor NIE wskazuje systemu (klient go nie narzucił) — bierzemy nasz zestaw. */
const val PROPOSE_UFH_PIPE_SYSTEM = "zaproponuj"
const val PROPOSE_UFH_PIPE_SYSTEM_LABEL = "Zaproponuj — system dobiera ekotak"

/** Co proponujemy przy wyborze „Zaproponuj". */
const val PROPOSED_UFH_PIPE_SYSTEM = "kan-16-rzt"

/** Kod, na którym realnie pracujemy: „Zaproponuj" → nasza propozycja. */
fun ufhPipeSystemResolved(code: String?): String {
    val v = code?.trim().orEmpty()
    return if (v == PROPOSE_UFH_PIPE_SYSTEM) PROPOSED_UFH_PIPE_SYSTEM else v
}

fun ufhPipeSystem(code: String?): UfhPipeSystem? {
    val v = ufhPipeSystemResolved(code)
    return UFH_PIPE_SYSTEMS.firstOrNull { it.code == v }
}

/**
 * Normalizacja zapisanej wartości do kodu — przyjmuje też pełną etykietę
 * (zapis z importu). Nieznane wartości zwracamy bez zmian, żeby nie zniknęły
 * po cichu; formularz pokaże je jako wartość spoza listy.
 */
fun ufhPipeSystemCode(raw: String?): String {
    val v = raw?.trim().orEmpty()
    if (v.isEmpty()) return ""
    if (v == PROPOSE_UFH_PIPE_SYSTEM || v == PROPOSE_UFH_PIPE_SYSTEM_LABEL) {
        return PROPOSE_UFH_PIPE_SYSTEM
    }
    return UFH_PIPE_SYSTEMS.firstOrNull { it.code == v || it.label == v }?.code ?: v
}

fun ufhPipeSystemLabel(code: String?): String = when {
    code?.trim() == PROPOSE_UFH_PIPE_SYSTEM -> PROPOSE_UFH_PIPE_SYSTEM_LABEL
    else -> ufhPipeSystem(code)?.label ?: code.orEmpty()
}

/** Czy wybrany system daje wydłużoną 10-letnią gwarancję (KAN-therm). */
fun ufhHasExtendedWarranty(code: String?): Boolean = ufhPipeSystem(code)?.warranty10 == true

/**
 * Najgęstszy rozstaw [cm] dla wybranego systemu — ⌀18 nie da się wygiąć co
 * 5 cm, więc przy tej rurze pole „rury co 5 cm" jest zablokowane.
 */
fun ufhMinSpacingCm(code: String?): Int = if (ufhPipeSystem(code)?.pipeMm == 18) 10 else 5

// ── Stan formularza ──────────────────────────────────────────────────────────

/**
 * Pole metrażu kondygnacji. `spacingCm == null` = powierzchnia bez pola
 * grzewczego (nie wchodzi do sumy OP). Kolejność jak legenda w panelu.
 */
enum class UfhAreaField(val key: String, val label: String, val spacingCm: Int?) {
    NO_UFH("noUfhM2", "Pomieszczenia bez ogrzewania podłogowego", null),
    LEAD_IN("leadInM2", "Przestrzeń na dobiegi rur do pomieszczeń", null),
    S5("m2_5", "Powierzchnia w rozstawie 5 cm", 5),
    S10("m2_10", "Powierzchnia w rozstawie 10 cm", 10),
    S15("m2_15", "Powierzchnia w rozstawie 15 cm", 15),
    S20("m2_20", "Powierzchnia w rozstawie 20 cm", 20),
}

/**
 * Parametry instalacji wspólne dla całego budynku (sekcja „Dane instalacji").
 * Każda pozycja to przyszła linia oferty, dlatego wartości są STAŁYMI
 * etykietami z list powyżej, a nie wolnym tekstem.
 */
data class UfhInstall(
    val wallChase: String = "",
    val leadInRouting: String = "",
    val subfloorJoints: String = "",
    /** Dobiegi ze skrzynkami zrobiła ekipa wod-kan — wypadają z rozliczenia OP. */
    val leadInByWodKan: Boolean = false,
    /** Rozdzielacze zamontowała ekipa wod-kan. */
    val manifoldByWodKan: Boolean = false,
    val designScope: String = "",
    val leadInPipeMm: String = "",
    val pressureTest: String = "",
    val systemPlateM2: String = "",
    val wasteRemoval: String = "",
    val heatMedium: String = "",
    val biocide: String = "",
    val warrantyDocs: String = "",
)

/** Domyślna ilość rozdzielaczy — rozdzielacz jest regułą, brak wpisuje się „0". */
const val UFH_DEFAULT_MANIFOLDS = "1"

/** Kondygnacja z OP. Pola metrażu trzymamy jako tekst — wpisuje je człowiek. */
data class UfhFloor(
    val name: String = "",
    /** Metraż wg projektu [m²] — „powinno być", odniesienie dla sumy rozstawów. */
    val projectM2: String = "",
    val system: String = "",
    val comment: String = "",
    /** Ilość rozdzielaczy na kondygnacji (0 = brak). */
    val manifolds: String = UFH_DEFAULT_MANIFOLDS,
    val boxType: String = "brak",
    /** Metraże wg rozstawu rur — klucz z `UfhAreaField.key`. */
    val areas: Map<String, String> = emptyMap(),
    /**
     * Pola rzutu kondygnacji zapisane w panelu (kropki rozdzielaczy, obrysy,
     * skala, historia, podpisy) jako surowy JSON. Telefon ich NIE edytuje —
     * przenosi je z odczytu do zapisu bez zmian. `null` = nic takiego nie było
     * zapisane.
     */
    val planJson: String? = null,
) {
    fun area(field: UfhAreaField): String = areas[field.key].orEmpty()

    fun withArea(field: UfhAreaField, value: String): UfhFloor =
        copy(areas = areas + (field.key to value))

    /** Suma powierzchni OP kondygnacji [m²] — same rozstawy, bez „bez OP". */
    val ufhAreaSum: Double
        get() = UfhAreaField.entries
            .filter { it.spacingCm != null }
            .sumOf { area(it).toM2() ?: 0.0 }

    /** Całość kondygnacji: OP + bez OP + dobiegi. */
    val totalArea: Double
        get() = ufhAreaSum +
            (area(UfhAreaField.NO_UFH).toM2() ?: 0.0) +
            (area(UfhAreaField.LEAD_IN).toM2() ?: 0.0)
}

data class UfhState(
    val pipeSystem: String = DEFAULT_UFH_PIPE_SYSTEM,
    val roomControl: String = "",
    val systemFilling: String = "",
    /** Planowane chłodzenie instalacją podłogową — pytane tylko przy pompie ciepła. */
    val cooling: Boolean = false,
    val install: UfhInstall = UfhInstall(),
    val floors: List<UfhFloor> = listOf(UfhFloor()),
)

/** Liczba z pola tekstowego; przecinek jak kropka, pusty/nieliczbowy → null. */
fun String.toM2(): Double? {
    val t = trim().replace(',', '.')
    if (t.isEmpty()) return null
    return t.toDoubleOrNull()
}

// ── Pytania warunkowe ────────────────────────────────────────────────────────

/** Czy sekcja „Dane instalacji" pyta o medium grzewcze i inhibitor. */
fun ufhAsksMedium(state: UfhState): Boolean = state.systemFilling == UFH_FILLING_AFTER_UFH

/** Czy pytamy o dokumentację do wydłużonej gwarancji. */
fun ufhAsksWarrantyDocs(state: UfhState): Boolean = ufhHasExtendedWarranty(state.pipeSystem)

/**
 * Ile parametrów instalacji jest wypełnionych i ile ich w ogóle jest przy
 * obecnych wyborach — licznik na belce sekcji. Checkboxy „wykonane na etapie
 * wod-kan" celowo poza rachunkiem: mają wartość od początku, więc licznik
 * by przez nie nie ruszał.
 */
fun ufhInstallProgress(state: UfhState): Pair<Int, Int> {
    val base = listOf(
        state.install.wallChase,
        state.install.leadInRouting,
        state.install.subfloorJoints,
        state.install.designScope,
        state.install.leadInPipeMm,
        state.install.pressureTest,
        state.install.systemPlateM2,
        state.install.wasteRemoval,
    )
    val conditional = buildList {
        if (ufhAsksMedium(state)) {
            add(state.install.heatMedium)
            add(state.install.biocide)
        }
        if (ufhAsksWarrantyDocs(state)) add(state.install.warrantyDocs)
    }
    val all = base + conditional
    return all.count { it.isNotBlank() } to all.size
}

// ── Kompletność ──────────────────────────────────────────────────────────────

/** Pytania „Danych instalacji" zadawane zawsze — etykiety 1:1 z panelem. */
private val INSTALL_LABELS: List<Pair<String, (UfhInstall) -> String>> = listOf(
    "wkuwanie dobiegów w ściany nośne" to { i -> i.wallChase },
    "rura dobiegowa do rozdzielacza (średnica)" to { i -> i.leadInPipeMm },
    "dobiegi do rozdzielaczy (sposób prowadzenia)" to { i -> i.leadInRouting },
    "łączenie rur pe-rt pod posadzką" to { i -> i.subfloorJoints },
    "projekt instalacji ogrzewania" to { i -> i.designScope },
    "dokumentacja i próba szczelności" to { i -> i.pressureTest },
    "usunięcie odpadów montażowych" to { i -> i.wasteRemoval },
    "płyta systemowa z wypustkami (m²)" to { i -> i.systemPlateM2 },
)

/**
 * Pytania BEZ odpowiedzi — gotowe zdania dla audytora. Pusta lista = komplet.
 * Zapis jest możliwy zawsze (audyt bywa uzupełniany na raty): kolor przycisku
 * i ta lista są sygnałem, nie blokadą. Świadomie NIE wymagamy komentarza
 * kondygnacji, chłodzenia ani checkboxów „wykonane na etapie wod-kan" — mają
 * sensowną wartość od początku.
 */
fun ufhMissingAnswers(s: UfhState): List<String> {
    val out = ArrayList<String>()

    if (s.pipeSystem.isBlank()) out += "Dane ogólne: system rur i rozdzielaczy"
    if (s.roomControl.isBlank()) {
        out += "Dane ogólne: planowane sterowanie temperaturą w pomieszczeniach"
    }
    if (s.systemFilling.isBlank()) out += "Dane ogólne: napełnienie i odpowietrzenie układu"

    for ((label, read) in INSTALL_LABELS) {
        if (read(s.install).isBlank()) out += "Dane instalacji: $label"
    }
    if (ufhAsksMedium(s)) {
        if (s.install.heatMedium.isBlank()) out += "Dane instalacji: rodzaj medium grzewczego"
        if (s.install.biocide.isBlank()) out += "Dane instalacji: inhibitor biobójczy"
    }
    if (ufhAsksWarrantyDocs(s) && s.install.warrantyDocs.isBlank()) {
        out += "Dane instalacji: dokumentacja do wydłużonej 10-letniej gwarancji"
    }

    s.floors.forEachIndexed { i, f ->
        val named = f.name.trim()
        val head = "Kondygnacja ${i + 1}" + if (named.isEmpty()) "" else " — $named"
        if (f.name.isBlank()) out += "$head: nazwa kondygnacji"
        if ((f.projectM2.toM2() ?: 0.0) <= 0) out += "$head: metraż wg projektu (m²)"
        if (f.system.isBlank()) out += "$head: system ogrzewania"
        if (f.manifolds.isBlank()) out += "$head: ilość rozdzielaczy"
        if (f.ufhAreaSum <= 0) out += "$head: powierzchnia OP wg rozstawu rur (m²)"
    }

    return out
}

// ── Podpowiedzi z danych budynku ─────────────────────────────────────────────

/**
 * Nazwy kondygnacji podpowiadane z danych budynku (zakł. „Dane"). Reguła 1:1
 * ze slotami „Projekt domu", żeby nazwy kondygnacji w audycie zgadzały się
 * z nazwami rzutów.
 */
fun suggestFloorNames(count: Int, hasBasement: Boolean): List<String> {
    val names = ArrayList<String>()
    if (hasBasement) names += "Piwnica"
    val above = (count - names.size).coerceAtLeast(0)
    when {
        above <= 1 -> if (above == 1) names += "Parter"
        above == 2 -> {
            names += "Parter"
            names += "Poddasze"
        }
        else -> {
            names += "Parter"
            // Piętra pośrednie między parterem a poddaszem.
            val mid = above - 2
            if (mid == 1) names += "Piętro" else for (i in 1..mid) names += "$i. piętro"
            names += "Poddasze"
        }
    }
    return names.take(count)
}

/**
 * Ilość kondygnacji z OP podpowiadana z danych budynku: kondygnacje nadziemne
 * + ogrzewana piwnica (w `buildingData.floors` piwnica nie jest liczona).
 * `null` = brak danych, nie podpowiadamy.
 */
fun suggestFloorCount(floors: Int?, hasBasement: Boolean): Int? {
    if (floors == null || floors <= 0) return null
    return (floors + if (hasBasement) 1 else 0).coerceIn(1, 6)
}

/**
 * Wstępne wypełnienie z danych budynku — używane przy PIERWSZYM wypełnianiu
 * audytu (deal nie ma jeszcze rekordu, startujemy z szablonu katalogu).
 * Nadpisuje tylko puste nazwy, nigdy danych audytora.
 */
fun applyBuildingToUfh(s: UfhState, buildingFloors: Int?, hasBasement: Boolean): UfhState {
    val count = suggestFloorCount(buildingFloors, hasBasement)
    val resized = when {
        count == null -> s
        count < s.floors.size -> s.copy(floors = s.floors.take(count))
        count > s.floors.size ->
            s.copy(floors = s.floors + List(count - s.floors.size) { UfhFloor() })
        else -> s
    }
    val names = suggestFloorNames(resized.floors.size, hasBasement)
    return resized.copy(
        floors = resized.floors.mapIndexed { i, f ->
            if (f.name.isNotBlank()) f else f.copy(name = names.getOrElse(i) { "" })
        },
    )
}
