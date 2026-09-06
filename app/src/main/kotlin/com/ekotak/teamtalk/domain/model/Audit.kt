package com.ekotak.teamtalk.domain.model

/**
 * Audyt deala (`GET /api/deals/:id/audits`, FR-18). W board360 jeden rekord
 * `Audit` obsługuje DWIE różne rzeczy, rozróżniane zawartością `formData`:
 *
 *  • **Heizlast** — zapotrzebowanie budynku na ciepło; `heatloadMode` + `heatloadKw`,
 *    `formData` najwyżej z notatką. Takich rekordów deal ma dowolnie wiele
 *    (kolejne pomiary, korekty), więc panel pokazuje je jako listę.
 *  • **Formularz audytu instalacji** — `formData.kind == UFH_AUDIT_KIND`, jeden
 *    rekord na parę (deal + węzeł katalogu). To on jest podstawą oferty.
 *
 * Karta na telefonie robi to samo rozróżnienie, żeby lista Heizlast nie zaśmiecała
 * się rekordami formularza (i odwrotnie).
 */
data class Audit(
    val id: String,
    val dealId: String = "",
    val heatloadMode: HeatloadMode? = null,
    val heatloadKw: Double? = null,
    /** ISO-8601 z API — formatowanie zostawiamy warstwie prezentacji. */
    val createdAt: String = "",
    /** Notatka audytora z `formData.note` (rekordy Heizlast). */
    val note: String? = null,
    /** Marker `formData.kind` — niepusty tylko dla formularza instalacji. */
    val formKind: String? = null,
    /** `formData.categoryId` — węzeł katalogu, którego dotyczy formularz. */
    val categoryId: String? = null,
    /**
     * Formularz audytu instalacji rozłożony na pola; `null` dla rekordów
     * Heizlast. Pola, których telefon nie edytuje (warstwa rzutu kondygnacji),
     * jadą w środku jako `UfhFloor.planJson` i wracają do API nietknięte.
     */
    val installationForm: UfhState? = null,
    /**
     * Kiedy zapis trafił do kolejki offline; `null` = rekord zgodny z serwerem.
     * Zakładka pokazuje po tym „czeka na wysyłkę", żeby audytor wiedział, że
     * jego praca jest zapisana w telefonie, ale panel jej jeszcze nie widzi.
     */
    val pendingSince: Long? = null,
)

/** Sposób ustalenia Heizlast — `heatloadMode` w API. */
enum class HeatloadMode(val wire: String, val label: String) {
    SZYBKI("szybki", "szybki (szacunek wskaźnikowy)"),
    DIN("din", "DIN EN 12831 (wynik zewnętrzny)");

    companion object {
        fun fromWire(value: String?): HeatloadMode? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * Standard energetyczny budynku dla szybkiego szacunku Heizlast. Wskaźniki
 * [W/m²] są PODGLĄDEM — liczbę zapisuje serwer ze swojej domeny, my pokazujemy
 * tylko, czego audytor ma się spodziewać. Kolejność i wartości 1:1 z panelem
 * (`STANDARD_INDEX` w `AuditPanel.tsx`).
 */
enum class BuildingStandard(val wire: String, val indexWm2: Int, val label: String) {
    NIEOCIEPLONY("nieocieplony", 120, "nieocieplony (~120 W/m²)"),
    SLABO_OCIEPLONY("slabo_ocieplony", 90, "słabo ocieplony (~90)"),
    STANDARD("standard", 60, "standard po 2000 (~60)"),
    DOBRZE_OCIEPLONY("dobrze_ocieplony", 45, "dobrze ocieplony (~45)"),
    ENERGOOSZCZEDNY("energooszczedny", 30, "energooszczędny (~30)"),
    PASYWNY("pasywny", 15, "pasywny (~15)");

    companion object {
        fun fromWire(value: String?): BuildingStandard? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * Podgląd Heizlast [kW] dla szybkiego szacunku — ten sam rachunek co `previewKw`
 * w panelu: wskaźnik standardu × metraż, skorygowany wysokością kondygnacji
 * względem typowych 2,6 m. `null` = za mało danych, żeby cokolwiek pokazać.
 */
fun previewHeatloadKw(areaM2: Double?, standard: BuildingStandard?, heightM: Double?): Double? {
    if (standard == null || areaM2 == null || areaM2 <= 0) return null
    val factor = if (heightM != null && heightM > 0) heightM / 2.6 else 1.0
    return kotlin.math.round(areaM2 * standard.indexWm2 * factor / 100) / 10
}

/**
 * Umowa, która zamyka ofertę deala. Formularz audytu instalacji jest wtedy
 * tylko do odczytu: klient podpisał konkretny zakres i konkretną kwotę, a to
 * z audytu liczy się oferta. API pilnuje tego samo (409 przy zapisie bez
 * `zmianaOferty`), telefon pokazuje powód, zanim audytor zacznie pisać.
 *
 * Zmianę (nowa umowa albo aneks) robi się w panelu — automat wystawiający
 * dokument nie ma sensownego odpowiednika na 360 dp, patrz `DealAuditTab`.
 */
data class OfferLock(
    val contractId: String,
    val numer: String,
    /** Data podpisu (ISO-8601) — `null` przy dokumencie bez zapisanej daty. */
    val podpisana: String? = null,
    /** Numer dokumentu zmiany będącego już w obiegu; `null` = nic nie trwa. */
    val zmianaWToku: String? = null,
)
