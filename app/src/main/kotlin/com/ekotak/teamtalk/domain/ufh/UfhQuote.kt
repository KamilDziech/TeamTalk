package com.ekotak.teamtalk.domain.ufh

import com.ekotak.teamtalk.domain.model.UFH_DESIGN_SCOPE
import com.ekotak.teamtalk.domain.model.UFH_FILLING_AFTER_UFH
import com.ekotak.teamtalk.domain.model.UFH_LEAD_IN_ROUTING
import com.ekotak.teamtalk.domain.model.UFH_PRESSURE_TEST
import com.ekotak.teamtalk.domain.model.UFH_SUBFLOOR_JOINTS
import com.ekotak.teamtalk.domain.model.UFH_WASTE_REMOVAL
import com.ekotak.teamtalk.domain.model.UfhFloor
import com.ekotak.teamtalk.domain.model.UfhState
import com.ekotak.teamtalk.domain.model.toM2
import com.ekotak.teamtalk.domain.model.ufhHasExtendedWarranty
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * OFERTA DLA KLIENTA — rachunek zakładki „Oferta" karty deala. Port
 * `offer-quote.ts` i tej części `ufh-points.ts`, z której idą ilości.
 *
 * Audyt OP mówi CO robimy (ilości i wybory), a ten moduł zamienia to na dwa
 * ekrany dla klienta:
 *  • [offerReasons] — „Dlaczego to rozwiązanie" — każda decyzja audytu zamieniona
 *    na zdanie: co wybraliśmy i co to daje klientowi,
 *  • [technicalSections] — widok techniczny w układzie konfiguratora ekotak.pl.
 *
 * CEN TU NIE MA — ten moduł opisuje wyłącznie ZAKRES. Kwoty liczy [offerScope],
 * biorąc te same ilości i mnożąc je przez ceny jednostkowe z formuły ceny węzła.
 */

// ── Ilości z audytu ─────────────────────────────────────────────────────────

/** Skrzynki podtynkowe — obie odmiany idą na tę samą pozycję cennika. */
private val FLUSH_BOXES = listOf("podtynkowa w ścianie działowej", "podtynkowa w ścianie nośnej")
private const val SURFACE_BOX = "natynkowa"

/** Wielkości policzone z audytu — wejście wzoru i podpis „skąd ta ilość". */
data class UfhPointQuantities(
    /** Cała zsumowana długość rury OP [mb] (grzewcza + dobiegi pętli). */
    val pipeM: Double = 0.0,
    val boxSurface: Int = 0,
    val boxFlush: Int = 0,
    /** Rozdzielacze do zamontowania (szt.). */
    val manifolds: Int = 0,
    val manifoldByWodKan: Boolean = false,
    /** Pomieszczenia z samą folią, bez OP [m²]. */
    val foilM2: Double = 0.0,
    /** Kondygnacje, na których ekipa OP robi dobiegi do rozdzielaczy. */
    val leadInFloors: Int = 0,
    val leadInByWodKan: Boolean = false,
    /** Płyta systemowa z wypustkami [m²]. */
    val plateM2: Double = 0.0,
    val wallChase: Boolean = false,
    /** Napełnienie układu po zakończeniu OP (a nie przy źródle ciepła). */
    val fillingAfterUfh: Boolean = false,
    val leadInOnStyro: Boolean = false,
    val pressureTest: Boolean = false,
    val wasteRemoval: Boolean = false,
    /** Profesjonalny projekt OP po stronie ekotak. */
    val design: Boolean = false,
    /** Dokumentacja do wydłużonej 10-letniej gwarancji producenta. */
    val warranty: Boolean = false,
)

private fun num(v: String?): Double = v?.toM2() ?: 0.0

/**
 * Skrzynki rozdzielaczy na kondygnacji: liczymy KROPKI z rzutu (każda ma swój
 * rodzaj), a gdy audytor ich nie postawił — deklarowaną „Ilość rozdzielaczy"
 * razy rodzaj skrzynki wybrany dla kondygnacji.
 */
private data class FloorBoxes(val surface: Int, val flush: Int, val fromMarks: Boolean)

private fun floorBoxes(floor: UfhFloor): FloorBoxes {
    val marks = floor.plan().marks
    if (marks.isNotEmpty()) {
        var surface = 0
        var flush = 0
        for (m in marks) {
            val t = markBoxType(m, floor.boxType)
            if (t == SURFACE_BOX) surface += 1 else if (FLUSH_BOXES.contains(t)) flush += 1
        }
        return FloorBoxes(surface, flush, fromMarks = true)
    }
    val n = max(0, jsRound(num(floor.manifolds)).toInt())
    if (floor.boxType == SURFACE_BOX) return FloorBoxes(n, 0, false)
    if (FLUSH_BOXES.contains(floor.boxType)) return FloorBoxes(0, n, false)
    return FloorBoxes(0, 0, false)
}

/** Rozdzielacze na kondygnacji: kropki z rzutu, a bez nich deklarowana ilość. */
fun floorManifolds(floor: UfhFloor): Int {
    val marks = floor.plan().marks
    if (marks.isNotEmpty()) return marks.size
    return max(0, jsRound(num(floor.manifolds)).toInt())
}

/** Rozdzielacze w całym budynku — sztuki DO KUPIENIA (wod-kan tego nie zeruje). */
fun ufhManifolds(state: UfhState): Int = state.floors.sumOf { floorManifolds(it) }

/**
 * Obwody przypadające na jeden rozdzielacz — z nich dobiera się wielkość
 * rozdzielacza i szafki, a więc koszt materiału tych pozycji.
 */
fun ufhManifoldLoops(state: UfhState, loops: Int): Int {
    val m = ufhManifolds(state)
    if (m <= 0 || loops <= 0) return 0
    return ceil(loops.toDouble() / m).toInt()
}

/** Rachunek rury PER KONDYGNACJA — jedno miejsce dla wyceny i doboru materiału. */
fun ufhFloorPipes(state: UfhState): List<FloorPipe> {
    val loopMaxM = ufhLoopMaxM(state.pipeSystem)
    return state.floors.map { floorPipe(it, loopMaxM) }
}

/** Wielkości do wzoru — policzone z zapisanego audytu OP. */
fun ufhPointQuantities(state: UfhState): Pair<UfhPointQuantities, List<String>> {
    val warnings = ArrayList<String>()
    val pipes = ufhFloorPipes(state)
    val pipe = sumPipe(pipes)
    val noPipeData = pipes.count { it.source == PipeSource.NONE }
    if (noPipeData > 0) {
        warnings += "Brak danych do policzenia rury na $noPipeData kondygnacji — zmierz " +
            "pomieszczenia na rzucie albo wpisz powierzchnie wg rozstawu."
    }

    val install = state.install
    val byWodKan = install.leadInByWodKan
    var boxSurface = 0
    var boxFlush = 0
    var noBoxData = 0
    for (f in state.floors) {
        val b = floorBoxes(f)
        boxSurface += b.surface
        boxFlush += b.flush
        if (!b.fromMarks && b.surface + b.flush == 0 && jsRound(num(f.manifolds)).toInt() > 0) {
            noBoxData += 1
        }
    }
    if (noBoxData > 0 && !byWodKan) {
        warnings += "Na $noBoxData kondygnacji rozdzielacze nie mają rodzaju skrzynki " +
            "(„brak”) — te pozycje nie wchodzą do rozliczenia."
    }

    val foilM2 = r1(state.floors.sumOf { num(it.area(AreaCat.NONE.field!!)) })
    val leadInSet = install.leadInRouting.isNotBlank() && !byWodKan
    if (byWodKan) {
        warnings += "Dobiegi ze skrzynkami wykonano na etapie wod-kan — dobiegi, skrzynki " +
            "i wkuwanie nie wchodzą do punktów montażu OP."
    } else if (!leadInSet) {
        warnings += "Nie wskazano sposobu prowadzenia dobiegów — dobiegi per kondygnacja " +
            "nie są liczone."
    }

    val manifoldByWodKan = install.manifoldByWodKan
    val manifolds = ufhManifolds(state)
    if (manifoldByWodKan && manifolds > 0) {
        warnings += "Rozdzielacze zamontowała ekipa wod-kan — montaż rozdzielaczy nie wchodzi " +
            "do punktów montażu OP."
    }

    val q = UfhPointQuantities(
        pipeM = pipe.total,
        boxSurface = if (byWodKan) 0 else boxSurface,
        boxFlush = if (byWodKan) 0 else boxFlush,
        manifolds = if (manifoldByWodKan) 0 else manifolds,
        manifoldByWodKan = manifoldByWodKan,
        foilM2 = foilM2,
        leadInFloors = if (leadInSet) state.floors.size else 0,
        leadInByWodKan = byWodKan,
        plateM2 = r1(num(install.systemPlateM2)),
        wallChase = !byWodKan && install.wallChase == "tak",
        fillingAfterUfh = state.systemFilling == UFH_FILLING_AFTER_UFH,
        leadInOnStyro = !byWodKan && install.leadInRouting == UFH_LEAD_IN_ROUTING[1],
        pressureTest = install.pressureTest == UFH_PRESSURE_TEST[1],
        wasteRemoval = install.wasteRemoval == UFH_WASTE_REMOVAL[1],
        design = install.designScope == UFH_DESIGN_SCOPE[1],
        warranty = install.warrantyDocs == "tak",
    )
    return q to warnings
}

// ── Powierzchnie ────────────────────────────────────────────────────────────

data class OfferAreaSplit(
    val cat: AreaCat,
    val label: String,
    val short: String,
    /** Rozstaw rury [m] — `null` dla powierzchni bez ogrzewania. */
    val spacingM: Double?,
    val m2: Double,
)

data class OfferAreas(
    /** Powierzchnia OGRZEWANA razem [m²] — mianownik ceny za m². */
    val heated: Double,
    /** Rozbicie powierzchni ogrzewanej na rozstawy (bez pustych). */
    val byCat: List<OfferAreaSplit>,
    /** Pomieszczenia bez OP (sama folia) [m²]. */
    val noUfh: Double,
    /** Przestrzeń na dobiegi rur do pomieszczeń [m²]. */
    val leadIn: Double,
)

/**
 * Metraż kategorii na kondygnacji: pomiar z rzutu ma pierwszeństwo (tak samo jak
 * przy długości rury), a gdy skali nie ma — pole wpisane ręcznie.
 */
fun floorCatM2(floor: UfhFloor, cat: AreaCat): Double {
    val plan = floor.plan()
    if (scaleReady(plan.scale)) {
        val measured = catM2(plan.rooms, plan.scale, cat)
        if (measured != null) return measured
    }
    val field = cat.field ?: return 0.0
    return num(floor.area(field))
}

/** Powierzchnie z audytu — ogrzewana wg rozstawów, bez OP i pod dobiegi. */
fun ufhAreas(state: UfhState): OfferAreas {
    val byCat = SPACING_CATS.map { c ->
        OfferAreaSplit(
            cat = c,
            label = c.label,
            short = c.short,
            spacingM = c.spacingM,
            m2 = r1(state.floors.sumOf { floorCatM2(it, c) }),
        )
    }.filter { it.m2 > 0 }

    return OfferAreas(
        heated = r1(byCat.sumOf { it.m2 }),
        byCat = byCat,
        noUfh = r1(state.floors.sumOf { floorCatM2(it, AreaCat.NONE) }),
        leadIn = r1(state.floors.sumOf { floorCatM2(it, AreaCat.LEAD) }),
    )
}

/** Wielkości audytu potrzebne wycenie — jeden przelot po stanie audytu. */
data class UfhQuoteInput(
    val quantities: UfhPointQuantities,
    val areas: OfferAreas,
    val pipeM: Double,
    val loops: Int,
    /** Obwody na jeden rozdzielacz (dobór wielkości rozdzielacza i szafki). */
    val manifoldLoops: Int,
    /** Rozdzielacze i szafki do kupienia (bez zerowania „robi wod-kan"). */
    val manifoldsToBuy: Int,
    val cabinetsToBuy: Int,
    val warnings: List<String>,
)

fun ufhQuoteInput(state: UfhState): UfhQuoteInput {
    val (quantities, warnings) = ufhPointQuantities(state)
    val pipe = sumPipe(ufhFloorPipes(state))
    return UfhQuoteInput(
        quantities = quantities,
        areas = ufhAreas(state),
        pipeM = pipe.total,
        loops = pipe.loops,
        manifoldLoops = ufhManifoldLoops(state, pipe.loops),
        manifoldsToBuy = ufhManifolds(state),
        // Szafki kupujemy tam, gdzie rozdzielacz ma skrzynkę — rodzaj „brak"
        // (rozdzielacz w szachcie) nie generuje pozycji.
        cabinetsToBuy = quantities.boxSurface + quantities.boxFlush,
        warnings = warnings,
    )
}

/** Czy audyt ma dość danych, żeby w ogóle było co wyceniać. */
fun auditUsable(input: UfhQuoteInput): Boolean = input.areas.heated > 0 || input.pipeM > 0

// ── Formaty ─────────────────────────────────────────────────────────────────

private fun dec(v: Double, digits: Int = 1): String =
    String.format(Locale.US, "%.${digits}f", v).replace('.', ',')

fun fmtAreaM2(n: Double): String = "${dec(n)} m²"

fun fmtPipeMb(n: Double): String = "${dec(n)} mb"

private fun spacingText(areas: OfferAreas): String =
    areas.byCat.joinToString(" · ") { "${it.short}: ${fmtAreaM2(it.m2)}" }

// ── „Dlaczego to rozwiązanie" ───────────────────────────────────────────────

data class OfferReason(
    /** Czego dotyczy decyzja (np. „System rur i rozdzielaczy"). */
    val topic: String,
    /** Co wybraliśmy — wartość z audytu, słowami klienta. */
    val choice: String,
    /** Dlaczego akurat to — korzyść albo warunek, który to wymusił. */
    val why: String,
)

/**
 * Uzasadnienie oferty — każda odpowiedź audytu przełożona na zdanie „co i po co".
 * To NIE jest opis marketingowy: piszemy wyłącznie o tym, co audytor faktycznie
 * zaznaczył, żeby handlowiec nie musiał tłumaczyć oferty z pamięci.
 */
fun offerReasons(state: UfhState, input: UfhQuoteInput): List<OfferReason> {
    val out = ArrayList<OfferReason>()
    val q = input.quantities
    val areas = input.areas
    val mm = ufhPipeMm(state.pipeSystem)
    val loopMax = ufhLoopMaxM(state.pipeSystem)

    if (state.pipeSystem.isNotBlank()) {
        out += OfferReason(
            topic = "System rur i rozdzielaczy",
            choice = offerPipeSystemLabel(state.pipeSystem),
            why = (
                // „Zaproponuj" w audycie = systemu nie narzucał klient; w ofercie
                // nazywamy go po imieniu i mówimy wprost, że to nasza rekomendacja.
                if (ufhIsProposedPipeSystem(state.pipeSystem)) {
                    "System dobraliśmy my — to nasz standard przy tym typie instalacji. "
                } else {
                    ""
                }
                ) +
                "Rura ⌀$mm mm z barierą antydyfuzyjną — pętla do ${loopMax.toInt()} mb, " +
                "co przy tym budynku pozwala rozłożyć ogrzewanie bez łączeń w wylewce." +
                if (ufhHasExtendedWarranty(state.pipeSystem)) {
                    " Ten system producent obejmuje wydłużoną gwarancją przy udokumentowanym montażu."
                } else {
                    ""
                },
        )
    }

    if (areas.heated > 0) {
        out += OfferReason(
            topic = "Powierzchnia i rozstaw rur",
            choice = fmtAreaM2(areas.heated) + " ogrzewane" +
                if (areas.byCat.isNotEmpty()) " (${spacingText(areas)})" else "",
            why = "Rozstaw dobieramy pomieszczeniami, a nie ryczałtem na cały dom: gęściej tam, " +
                "gdzie strata ciepła jest większa (łazienki, duże przeszklenia), rzadziej w " +
                "pomieszczeniach ciepłych — dzięki temu podłoga grzeje równo, a rury nie ma " +
                "więcej, niż potrzeba.",
        )
    }

    if (input.manifoldsToBuy > 0) {
        out += OfferReason(
            topic = "Rozdzielacze i szafki",
            choice = "${input.manifoldsToBuy} szt." +
                if (input.loops > 0) {
                    " na ${input.loops} obwodów (ok. ${input.manifoldLoops} na rozdzielacz)"
                } else {
                    ""
                } +
                if (q.boxSurface > 0 || q.boxFlush > 0) {
                    " · skrzynki: ${q.boxSurface} natynkowe, ${q.boxFlush} podtynkowe"
                } else {
                    ""
                },
            why = "Liczba rozdzielaczy wychodzi z rozmieszczenia pętli na rzucie — krótsze " +
                "dobiegi to mniejsze opory i niższy koszt pompowania. Wielkość rozdzielacza " +
                "i szafki dobieramy do liczby obwodów z zapasem, żeby dało się później dołożyć " +
                "pętlę.",
        )
    }

    if (state.roomControl.isNotBlank()) {
        out += OfferReason(
            topic = "Sterowanie temperaturą w pomieszczeniach",
            choice = state.roomControl,
            why = if (roomControlPlanned(state.roomControl)) {
                "Instalację przygotowujemy pod automatykę strefową — szafka ma miejsce na " +
                    "listwę sterującą i siłowniki, więc dołożenie termostatów nie wymaga przeróbek."
            } else {
                "Bez automatyki strefowej: temperaturę wyrównuje sam rozstaw rur i nastawy na " +
                    "rozdzielaczu. To rozwiązanie tańsze i mniej awaryjne, rekomendowane przy " +
                    "dobrze zaizolowanym budynku."
            },
        )
    }

    if (state.cooling) {
        out += OfferReason(
            topic = "Chłodzenie instalacją podłogową",
            choice = "tak — instalacja przygotowana do chłodzenia",
            why = "Przy chłodzeniu rozdzielacze i dobiegi izolujemy taśmą kauczukową, bo zimna " +
                "rura w ciepłym pomieszczeniu się wykrapla — bez tej izolacji wilgoć zbiera się " +
                "w szafce i w warstwie podłogi.",
        )
    }

    if (state.systemFilling.isNotBlank()) {
        val medium = state.install.heatMedium
        out += OfferReason(
            topic = "Napełnienie i odpowietrzenie układu",
            choice = state.systemFilling +
                (if (medium.isNotBlank()) " · medium: $medium" else "") +
                (if (state.install.biocide == "tak") " · z inhibitorem biobójczym" else ""),
            why = if (q.fillingAfterUfh) {
                "Układ napełniamy i odpowietrzamy zaraz po ułożeniu rur — instalacja stoi pod " +
                    "ciśnieniem do czasu montażu źródła ciepła, więc ewentualne uszkodzenie " +
                    "wyjdzie przed wylewką, a nie po niej."
            } else {
                "Napełnienie zostaje na etap montażu źródła ciepła — nie płacisz dwa razy za tę " +
                    "samą czynność, a instalacja do tego czasu jest sprawdzona próbą szczelności."
            },
        )
    }

    if (state.install.subfloorJoints.isNotBlank()) {
        val strict = state.install.subfloorJoints == UFH_SUBFLOOR_JOINTS[1]
        val waste = wastePct(state.install.subfloorJoints)
        out += OfferReason(
            topic = "Łączenie rur pod posadzką",
            choice = state.install.subfloorJoints,
            why = if (strict) {
                "Każda pętla z jednego kawałka rury — pod wylewką nie zostaje ani jedno złącze, " +
                    "czyli nie ma czego rozszczelnić. Kosztem jest większy odpad rury ($waste%), " +
                    "bo końcówek zwojów nie da się wykorzystać."
            } else {
                "Dopuszczone łączenie rury pod posadzką obniża odpad do $waste%, ale zostawia " +
                    "złącze w wylewce — rozwiązanie tańsze, którego nie rekomendujemy."
            },
        )
    }

    if (q.leadInByWodKan) {
        out += OfferReason(
            topic = "Dobiegi i skrzynki",
            choice = "wykonane na etapie wod-kan",
            why = "Dobiegi do rozdzielaczy, skrzynki i wkuwanie zostały zrobione wcześniej przez " +
                "ekipę wod-kan — w tej ofercie ich nie ma, żeby nie płacić za ten sam zakres dwa razy.",
        )
    } else if (state.install.leadInRouting.isNotBlank()) {
        out += OfferReason(
            topic = "Prowadzenie dobiegów do rozdzielaczy",
            choice = state.install.leadInRouting +
                (
                    if (state.install.leadInPipeMm.isNotBlank()) {
                        " · rura ⌀${state.install.leadInPipeMm} mm"
                    } else {
                        ""
                    }
                    ) +
                (if (q.wallChase) " · wkuwanie w ściany nośne" else ""),
            why = if (q.leadInOnStyro) {
                "Dobiegi układamy na dodatkowej warstwie styropianu (albo w wyfrezowanym) — rura " +
                    "nie leży na zimnym chudziaku, więc ciepło idzie do pomieszczeń, a nie w grunt."
            } else {
                "Dobiegi prowadzimy w izolacji na chudziaku — najkrótszą trasą od rozdzielacza, " +
                    "z zachowaniem rozdzielenia zasilania i powrotu."
            },
        )
    }

    if (q.manifoldByWodKan) {
        out += OfferReason(
            topic = "Montaż rozdzielaczy",
            choice = "wykonany na etapie wod-kan",
            why = "Rozdzielacze są już zamontowane — wycena obejmuje tylko rozłożenie " +
                "i podłączenie pętli.",
        )
    }

    if (q.plateM2 > 0) {
        out += OfferReason(
            topic = "Płyta systemowa z wypustkami",
            choice = fmtAreaM2(q.plateM2),
            why = "Zamiast folii ze spinkami układamy płytę z wypustkami: rura trzyma rozstaw " +
                "sama, montaż jest szybszy i powtarzalny, a płyta dokłada izolację pod wylewką.",
        )
    }

    if (q.foilM2 > 0) {
        out += OfferReason(
            topic = "Pomieszczenia bez ogrzewania podłogowego",
            choice = "${fmtAreaM2(q.foilM2)} — sama folia",
            why = "W pomieszczeniach bez pętli rozkładamy samą folię — wylewka wymaga jej na " +
                "całej powierzchni, ale nie płacisz tam za rurę ani za jej układanie.",
        )
    }

    if (state.install.pressureTest.isNotBlank()) {
        out += OfferReason(
            topic = "Dokumentacja i próba szczelności",
            choice = state.install.pressureTest,
            why = if (q.pressureTest) {
                "Instalację odbieramy próbą szczelności powietrzem z protokołem — to dokument, " +
                    "który rozstrzyga ewentualny spór z ekipą od wylewki i jest wymagany przy " +
                    "gwarancji producenta."
            } else {
                "Rezygnacja z próby szczelności — nieszczelność wychodzi wtedy dopiero po " +
                    "wylewce, dlatego odradzamy to rozwiązanie."
            },
        )
    }

    if (state.install.designScope.isNotBlank()) {
        out += OfferReason(
            topic = "Projekt instalacji",
            choice = state.install.designScope,
            why = if (state.install.designScope == UFH_DESIGN_SCOPE[1]) {
                "Projekt robi nasz dział projektowy: pętle, rozstawy i nastawy rozdzielacza " +
                    "liczone do zapotrzebowania budynku, do akceptacji przed montażem."
            } else {
                "Pracujemy na projekcie klienta lub uproszczonym doborze ekotak — bez kosztu " +
                    "osobnego opracowania."
            },
        )
    }

    if (state.install.warrantyDocs == "tak") {
        out += OfferReason(
            topic = "Wydłużona gwarancja producenta",
            choice = "dokumentacja montażu do 10-letniej gwarancji",
            why = "Producent wydłuża gwarancję na system, jeśli montaż zostanie udokumentowany — " +
                "przygotowujemy komplet zdjęć i protokołów.",
        )
    }

    if (state.install.wasteRemoval.isNotBlank()) {
        out += OfferReason(
            topic = "Odpady montażowe",
            choice = state.install.wasteRemoval,
            why = if (q.wasteRemoval) {
                "Odpady zabieramy z budowy — nie zostaje po nas nic do wywiezienia."
            } else {
                "Odpady składamy w wyznaczonym miejscu u inwestora — tańszy wariant, wywóz po " +
                    "Twojej stronie."
            },
        )
    }

    return out
}

// ── Widok techniczny (układ konfiguratora ekotak.pl) ────────────────────────

data class TechRow(val label: String, val value: String, val note: String? = null)

data class TechSection(val title: String, val subtitle: String? = null, val rows: List<TechRow>)

private fun yn(v: Boolean): String = if (v) "tak" else "nie"

private fun dash(v: String): String = v.trim().ifEmpty { "—" }

/**
 * Specyfikacja techniczna w układzie konfiguratora z ekotak.pl: parametry główne
 * → kondygnacje → parametry instalacji → parametry uzupełniane przez inżyniera.
 * Ta sama kolejność pytań, co na stronie, tylko wypełniona danymi audytu tego
 * deala.
 */
fun technicalSections(state: UfhState, input: UfhQuoteInput): List<TechSection> {
    val q = input.quantities
    val areas = input.areas
    val out = ArrayList<TechSection>()

    out += TechSection(
        title = "Parametry główne",
        rows = listOf(
            TechRow("Powierzchnia podłóg ze wszystkich kondygnacji", fmtAreaM2(areas.heated + areas.noUfh)),
            TechRow(
                label = "Powierzchnia ogrzewana podłogowo",
                value = fmtAreaM2(areas.heated),
                note = if (areas.byCat.isNotEmpty()) spacingText(areas) else null,
            ),
            TechRow("Ilość kondygnacji", "${state.floors.size}"),
            TechRow("System", dash(offerPipeSystemLabel(state.pipeSystem))),
            TechRow("Planowane sterowanie temperaturą w pomieszczeniach", dash(state.roomControl)),
            TechRow("Planowane chłodzenie instalacją podłogową", yn(state.cooling)),
            TechRow("Napełnienie i odpowietrzenie układu", dash(state.systemFilling)),
        ),
    )

    state.floors.forEachIndexed { i, f ->
        val marks = f.plan().marks
        val rows = ArrayList<TechRow>()
        rows += TechRow(
            label = "Ilość rozdzielaczy",
            value = "${floorManifolds(f)} szt.",
            note = if (marks.isNotEmpty()) "z kropek na rzucie" else "z deklaracji audytora",
        )
        rows += TechRow("Skrzynki rozdzielacza", dash(f.boxType))
        for (c in SPACING_CATS) {
            val m2 = floorCatM2(f, c)
            if (m2 > 0) rows += TechRow("Powierzchnia — ${c.label}", fmtAreaM2(m2))
        }
        val noUfh = floorCatM2(f, AreaCat.NONE)
        if (noUfh > 0) rows += TechRow("Pomieszczenia bez ogrzewania podłogowego", fmtAreaM2(noUfh))
        val lead = floorCatM2(f, AreaCat.LEAD)
        if (lead > 0) rows += TechRow("Przestrzeń na dobiegi rur do pomieszczeń", fmtAreaM2(lead))
        if (f.system.isNotBlank()) rows += TechRow("System ogrzewania", f.system)
        if (f.comment.isNotBlank()) rows += TechRow("Komentarz", f.comment.trim())
        out += TechSection(
            title = f.name.trim().ifEmpty { "Kondygnacja ${i + 1}" },
            subtitle = f.projectM2.trim().takeIf { it.isNotEmpty() }
                ?.let { "metraż wg projektu: $it m²" },
            rows = rows,
        )
    }

    val inst = state.install
    val instRows = ArrayList<TechRow>()
    instRows += TechRow("Rodzaj medium grzewczego", dash(inst.heatMedium))
    instRows += TechRow("Inhibitor korozji / biobójczy", dash(inst.biocide))
    instRows += TechRow(
        label = "Łączenie rur pe-rt pod posadzką",
        value = dash(inst.subfloorJoints),
        note = inst.subfloorJoints.takeIf { it.isNotBlank() }
            ?.let { "nadatek na odpad ${wastePct(it)}%" },
    )
    instRows += TechRow("Dokumentacja i próba szczelności", dash(inst.pressureTest))
    instRows += TechRow("Projekt instalacji ogrzewania", dash(inst.designScope))
    instRows += TechRow("Dobiegi do rozdzielaczy", dash(inst.leadInRouting))
    instRows += TechRow("Wkuwanie dobiegów w ściany nośne", dash(inst.wallChase))
    instRows += TechRow("Usunięcie odpadów montażowych", dash(inst.wasteRemoval))
    if (ufhHasExtendedWarranty(state.pipeSystem)) {
        instRows += TechRow("Dokumentacja do wydłużonej gwarancji", dash(inst.warrantyDocs))
    }
    if (inst.leadInByWodKan) {
        instRows += TechRow(
            "Dobiegi ze skrzynkami",
            "wykonane na etapie wod-kan",
            "poza zakresem tej oferty",
        )
    }
    if (inst.manifoldByWodKan) {
        instRows += TechRow(
            "Montaż rozdzielaczy",
            "wykonany na etapie wod-kan",
            "poza zakresem tej oferty",
        )
    }
    out += TechSection("Parametry instalacji", rows = instRows)

    out += TechSection(
        title = "Parametry instalacji — uzupełnia inżynier ekotak",
        rows = listOf(
            TechRow(
                "Rura dobiegowa do rozdzielacza (średnica)",
                if (inst.leadInPipeMm.isNotBlank()) "⌀${inst.leadInPipeMm} mm" else "—",
            ),
            TechRow(
                "Płyta systemowa do ogrzewania z wypustkami",
                if (q.plateM2 > 0) fmtAreaM2(q.plateM2) else "—",
            ),
        ),
    )

    out += TechSection(
        title = "Wyliczenia z audytu",
        subtitle = "wielkości, na których policzona jest wycena",
        rows = listOf(
            TechRow(
                "Rura ogrzewania razem",
                fmtPipeMb(input.pipeM),
                "pętle grzewcze + dobiegi pętli do rozdzielacza",
            ),
            TechRow(
                "Obwody (pętle)",
                "${input.loops}",
                input.manifoldLoops.takeIf { it > 0 }?.let { "ok. $it na rozdzielacz" },
            ),
            TechRow("Rozdzielacze", "${input.manifoldsToBuy} szt."),
            TechRow("Skrzynki rozdzielaczy", "${q.boxSurface} natynkowe · ${q.boxFlush} podtynkowe"),
            TechRow("Kondygnacje z dobiegami w zakresie OP", "${q.leadInFloors}"),
            TechRow("Sama folia (pomieszczenia bez OP)", if (q.foilM2 > 0) fmtAreaM2(q.foilM2) else "—"),
        ),
    )

    return out
}

/** Ilość w tabeli — liczby całkowite bez zer po przecinku. */
fun qtyText(v: Double?): String {
    if (v == null) return ""
    return if (v == v.roundToInt().toDouble()) "${v.roundToInt()}" else dec(v)
}
