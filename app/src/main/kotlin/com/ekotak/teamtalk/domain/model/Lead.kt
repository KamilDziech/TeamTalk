package com.ekotak.teamtalk.domain.model

/**
 * Kreator LEAD (kafelek pulpitu, makieta `design/mockups/modul-lead.html`,
 * ustalenia 2026-09-13). Wartości `wire` to dokładnie to, czego oczekuje
 * `POST /api/intake/app/lead` — etykiety żyją tu obok, żeby ekran i podsumowanie
 * mówiły tym samym językiem.
 */

/** Czym interesuje się klient — na start jedna instalacja (etykieta = nazwa w katalogu). */
enum class LeadInterest(val label: String) {
    OGRZEWANIE_PODLOGOWE("Ogrzewanie podłogowe"),
}

/** Dom w budowie czy już zamieszkały — tu kreator się rozgałęzia. */
enum class LeadOccupancy(val wire: String, val label: String) {
    W_BUDOWIE("w_budowie", "W budowie"),
    ZAMIESZKALY("zamieszkaly", "Zamieszkały"),
}

enum class LeadProjectKind(val wire: String, val label: String, val hint: String?) {
    KATALOG("katalog", "Znam nazwę projektu", "np. Archon, Z500, MGProjekt"),
    WLASNY("wlasny", "Projekt własny", "indywidualny, od architekta"),
    NIE_PAMIETA("niepamieta", "Z projektu, ale nie pamięta nazwy", null),
}

/** Technologia ścian — wartości jak w leadowni /targi, bo tak czyta je karta deala. */
enum class LeadConstruction(val wire: String, val short: String, val light: Boolean) {
    MUROWANY("Murowany", "Murowany", false),
    SZKIELETOWY("Szkieletowy", "Szkieletowy", true),
    Z_BALA("Z bala", "Z bala", true),
    // Keramzyt ma ciężki strop — pytamy jak przy murowanym (ustalenie 2026-09-13).
    KERAMZYT("Gotowe ściany z keramzytu", "Keramzyt", false),
}

enum class LeadShape(val wire: String) {
    PARTEROWY("Parterowy"),
    PIETROWY("Piętrowy"),
    BLIZNIAK("Bliźniak"),
}

/** Nowy dom: wariant ogrzewania podłogowego. */
enum class FloorHeatingVariant(val wire: String) {
    STANDARD("standard"),
    SUCHY("suchy"),
    OPIS("opis"),
}

/** Dom zamieszkały: co klient chce zrobić. */
enum class RenovationWorks(val wire: String, val label: String, val hint: String, val areaQuestion: String) {
    SKUCIE(
        "skucie",
        "Skuć wylewki i zrobić od nowa",
        "styropian, normalne ogrzewanie podłogowe, nowe wylewki",
        "Ile m² wylewek do skucia?",
    ),
    SUCHY(
        "suchy",
        "Nakleić system suchy na obecne wylewki",
        "bez kucia, podłoga rośnie o kilka centymetrów",
        "Ile m² podłogi pod system suchy?",
    ),
    FREZOWANIE(
        "frezowanie",
        "Wyfrezować ogrzewanie w obecnych wylewkach",
        "bez kucia i bez podnoszenia podłogi",
        "Ile m² wylewek do frezowania?",
    ),
}

/** Źródła ciepła do wyboru przy domu zamieszkałym. */
val LEAD_HEAT_SOURCES: List<String> = listOf(
    "Pompa ciepła powietrzna",
    "Pompa ciepła gruntowa",
    "Kocioł gazowy",
    "Pellet / drewno",
    "Ogrzewanie elektryczne",
    "Sieć ciepłownicza",
    "Jeszcze nie wiadomo",
)

/** „Skąd o nas wie" — pierwsze trzy jak w leadowni /tel. */
enum class LeadOrigin(val wire: String, val label: String) {
    REKOMENDACJA("rekomendacja", "Rekomendacja"),
    POWRACAJACY("klient powracający", "Klient powracający"),
    BANER("widział baner", "Widział baner"),
    INTERNET("internet", "Internet"),
    INNE("inne", "Inne"),
}

/** Wydarzenie z listy „Które targi?". */
data class LeadEvent(val id: String, val name: String, val eventDate: String?)

/** Komplet odpowiedzi kreatora gotowy do wysłania. */
data class LeadDraft(
    val channel: LeadChannel,
    val eventId: String?,
    val takenById: String?,
    val interests: List<LeadInterest>,
    val occupancy: LeadOccupancy,
    // ── nowy dom ──
    val projectKind: LeadProjectKind?,
    val projectName: String?,
    val construction: LeadConstruction?,
    val basement: Boolean,
    val garage: Boolean,
    val shape: LeadShape?,
    val areaM2: Int?,
    val floorHeatingVariant: FloorHeatingVariant?,
    val floorHeatingNote: String?,
    val lightSlab: Boolean,
    val milling: Boolean,
    // ── dom zamieszkały ──
    val works: RenovationWorks?,
    val worksAreaM2: Int?,
    val heatSource: String?,
    val heatSourcePlanned: Boolean,
    // ── klient ──
    val fullName: String,
    val phone: String?,
    val email: String?,
    val postalCode: String?,
    val city: String?,
    val origin: LeadOrigin?,
    val referralFrom: String?,
    /** Firma z wizytówki zeskanowanej w asystencie; null = kreator otwarty z kafelka. */
    val company: LeadCompany? = null,
)

/**
 * Dane firmowe leada z wizytówki. `segment = b2b` ustawia na karcie deala
 * „Firma (B2B)" i nabywcę faktury; firma, NIP, stanowisko i www idą do kartoteki.
 */
data class LeadCompany(
    val segment: String,
    val companyName: String,
    val nip: String,
    val jobTitle: String,
    val website: String,
)

/** Wynik zapisu: karta w lejku albo lead w kolejce do wysłania. */
sealed interface LeadSubmitResult {
    data class Created(val dealId: String) : LeadSubmitResult
    data object Queued : LeadSubmitResult
}
