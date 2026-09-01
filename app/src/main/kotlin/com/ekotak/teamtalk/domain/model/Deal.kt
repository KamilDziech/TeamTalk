package com.ekotak.teamtalk.domain.model

/**
 * Etap lejka sprzedaży — odwzorowanie `DEAL_STAGES` z board360
 * (api/src/modules/crm/domain/deal-stage.ts). Kolejność jest znacząca:
 * odzwierciedla przebieg lejka. Etykiety 1:1 ze `STAGE_LABEL` panelu web,
 * żeby handlowiec widział na telefonie te same nazwy co na tablicy Kanban.
 */
enum class DealStage(val wire: String, val label: String) {
    LEAD("lead", "Lead"),
    QUALIFIKACJA("qualifikacja", "Kwalifikacja"),
    EDUKACJA("edukacja", "Remarketing"),
    AUDIT("audit", "Audyt"),
    ANGEBOT("angebot", "Oferta"),
    ON_HOLD("on_hold", "Wstrzymane"),
    SOLD("sold", "Sprzedane"),
    PRZED_MONTAZEM("przed_montazem", "Przed montażem"),
    OCZEKIWANIE_NA_MONTAZ("oczekiwanie_na_montaz", "Oczekiwanie"),
    MONTAZ("montaz", "Montaż"),
    FERTIG("fertig", "Po montażu"),
    LOST("lost", "Stracone"),
    ZAKONCZONY("zakonczony", "Zakończony");

    companion object {
        fun fromWire(value: String?): DealStage? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * Fazy lejka — odpowiednik `FUNNEL_GROUPS` z board360. Na wąskim ekranie nie da
 * się pokazać 11 kolumn Kanbana naraz, więc filtr mobilny działa na fazach, a
 * karty w obrębie fazy są pogrupowane nagłówkami etapów.
 */
enum class FunnelGroup(val label: String, val stages: List<DealStage>) {
    BOW("BOW", listOf(DealStage.LEAD, DealStage.QUALIFIKACJA, DealStage.EDUKACJA)),
    SPRZEDAZ("Sprzedaż", listOf(DealStage.AUDIT, DealStage.ANGEBOT, DealStage.ON_HOLD)),
    MONTAZ(
        "Etap montażowy",
        listOf(
            DealStage.SOLD,
            DealStage.PRZED_MONTAZEM,
            DealStage.OCZEKIWANIE_NA_MONTAZ,
            DealStage.MONTAZ,
        ),
    ),
    PO_MONTAZU("Po montażu", listOf(DealStage.FERTIG)),
}

/** Etapy widoczne w lejku (bez `lost`/`zakonczony` — to Archiwum i domknięte). */
val PIPELINE_STAGES: List<DealStage> = FunnelGroup.entries.flatMap { it.stages }

/**
 * Dozwolone przejścia etapów — kopia maszyny stanów z board360
 * (api/src/modules/crm/domain/stage-transitions.ts). Trzymamy ją lokalnie, żeby
 * pokazać tylko sensowne przyciski; autorytatywna walidacja i tak jest w API
 * (blokady walidacyjne zwracają 422 z listą braków).
 */
private val ALLOWED_TRANSITIONS: Map<DealStage, List<DealStage>> = mapOf(
    DealStage.LEAD to listOf(DealStage.QUALIFIKACJA, DealStage.LOST),
    // audit = ręczne pominięcie Remarketingu na życzenie operatora.
    DealStage.QUALIFIKACJA to listOf(DealStage.EDUKACJA, DealStage.AUDIT, DealStage.LOST),
    DealStage.EDUKACJA to listOf(DealStage.AUDIT, DealStage.LOST),
    DealStage.AUDIT to listOf(DealStage.ANGEBOT, DealStage.LOST),
    DealStage.ANGEBOT to listOf(DealStage.SOLD, DealStage.ON_HOLD, DealStage.LOST),
    DealStage.ON_HOLD to listOf(DealStage.ANGEBOT, DealStage.LOST),
    DealStage.SOLD to listOf(DealStage.PRZED_MONTAZEM, DealStage.LOST),
    DealStage.PRZED_MONTAZEM to listOf(DealStage.OCZEKIWANIE_NA_MONTAZ, DealStage.LOST),
    DealStage.OCZEKIWANIE_NA_MONTAZ to listOf(DealStage.MONTAZ, DealStage.LOST),
    DealStage.MONTAZ to listOf(DealStage.FERTIG, DealStage.LOST),
    DealStage.FERTIG to listOf(DealStage.ZAKONCZONY),
    DealStage.LOST to listOf(DealStage.LEAD),
    DealStage.ZAKONCZONY to listOf(DealStage.FERTIG),
)

/**
 * Etapy osiągalne z danego etapu. Świadomie NIE pokazujemy cofania po głównej
 * ścieżce (API je dopuszcza) — korekta pomyłki to praca w panelu, nie akcja
 * wykonywana jedną ręką w terenie.
 */
fun nextStages(from: DealStage): List<DealStage> = ALLOWED_TRANSITIONS[from].orEmpty()

/**
 * Powody utraty deala — `LOST_REASONS` z board360. Kategoria jest wymagana przy
 * przejściu na „Stracone", inaczej API zwraca 422.
 */
val LOST_REASONS: List<Pair<String, String>> = listOf(
    "cena" to "Cena / budżet",
    "konkurencja" to "Wybrał konkurencję",
    "rezygnacja" to "Rezygnacja z inwestycji",
    "brak_kontaktu" to "Brak kontaktu",
    "termin" to "Termin / czas realizacji",
    "inne" to "Inne",
)

/** Powody utraty właściwe dla kolumny „Lead" (odrzucenie surowego leada). */
val LEAD_LOST_REASONS: List<Pair<String, String>> = listOf(
    "odleglosc" to "Zbyt daleka odległość",
    "wlasny_material" to "Montaż na własnym materiale klienta",
    "harmonogram" to "Zajęty harmonogram",
    "brak_odzewu" to "Klient się nie odzywa",
    "niepelne_dane" to "Niepełne dane kontaktowe",
)

/** Zestaw powodów utraty właściwy dla etapu, na którym oznaczamy stratę. */
fun lostReasonsForStage(stage: DealStage): List<Pair<String, String>> =
    if (stage == DealStage.LEAD) LEAD_LOST_REASONS else LOST_REASONS

/** Segment klienta na dealu (oś niezależna od typu klienta w kartotece). */
enum class DealSegment(val wire: String, val label: String) {
    INDYWIDUALNY("indywidualny", "Indywidualny"),
    B2B("b2b", "Firma (B2B)");

    companion object {
        fun fromWire(value: String?): DealSegment =
            entries.firstOrNull { it.wire == value } ?: INDYWIDUALNY
    }
}

/** Rodzaj budynku na dealu. */
enum class DealBuildingKind(val wire: String, val label: String) {
    NOWY("nowy", "Dom nowy"),
    MODERNIZACJA("modernizacja", "Modernizacja");

    companion object {
        fun fromWire(value: String?): DealBuildingKind =
            entries.firstOrNull { it.wire == value } ?: NOWY
    }
}

/** Etykieta trudności deala/montażu (FR-16). */
enum class DealDifficulty(val wire: String, val label: String) {
    LATWY("latwy", "łatwy"),
    NORMALNY("normalny", "normalny"),
    TRUDNY("trudny", "trudny");

    companion object {
        fun fromWire(value: String?): DealDifficulty? = entries.firstOrNull { it.wire == value }
    }
}

/** Buyer persona klienta — profil decyzyjny kupującego (zakł. Dane). */
enum class DealBuyerPersona(val wire: String, val label: String) {
    ANALITYK("analityk", "Analityk"),
    ZAUFANY("zaufany", "Zaufany"),
    PREMIUM("premium", "Premium");

    companion object {
        fun fromWire(value: String?): DealBuyerPersona? = entries.firstOrNull { it.wire == value }
    }
}

/** Miejsce spotkania wstępnego (zakł. LEAD / Remarketing karty deala). */
enum class MeetingKind(val wire: String, val label: String) {
    KLIENT("klient", "U klienta"),
    BIURO("biuro", "W biurze"),
    ONLINE("online", "Online");

    companion object {
        fun fromWire(value: String?): MeetingKind? = entries.firstOrNull { it.wire == value }
    }
}

/** Rodzaj miejsca spotkania audytowego (zakł. „Audyt"). */
enum class AuditAddressKind(val wire: String, val label: String) {
    INSTALACJA("instalacja", "Adres instalacji"),
    BIURO("biuro", "W biurze"),
    ONLINE("online", "Online");

    companion object {
        fun fromWire(value: String?): AuditAddressKind? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * Zweryfikowane dane budynku (blok „Dane budynku" karty deala).
 * `areaM2` przy zapisie API przyjmuje wyłącznie liczbę całkowitą, ale przy
 * odczycie mogą przyjść starsze wpisy ułamkowe — stąd `Double` w modelu.
 */
data class DealBuildingData(
    val people: Int? = null,
    val areaM2: Double? = null,
    val floors: Int? = null,
    val shape: String? = null,
    val construction: String? = null,
    val stage: String? = null,
    val windows: String? = null,
    val heatedBasement: Boolean? = null,
    val heatedGarage: Boolean? = null,
) {
    val isEmpty: Boolean
        get() = people == null && areaM2 == null && floors == null && shape.isNullOrBlank() &&
            construction.isNullOrBlank() && stage.isNullOrBlank() && windows.isNullOrBlank() &&
            heatedBasement == null && heatedGarage == null
}

/** OZC — zapotrzebowanie na ciepło przepisane z cieplo.app. */
data class DealOzcData(
    val buildingKw: Double? = null,
    val dhwKw: Double? = null,
    val sourceUrl: String? = null,
    val confirmed: Boolean = false,
) {
    val isEmpty: Boolean get() = buildingKw == null && dhwKw == null && sourceUrl.isNullOrBlank()
}

/**
 * Deal — proces sprzedażowy w lejku board360. Pola odpowiadają tym, które
 * przyjmuje `PATCH /api/deals/:id`; poza zasięgiem mobilnym zostaje oferta,
 * materiały, rozliczenie i pliki (osobne endpointy, ekrany na panel).
 *
 * Uwaga: `GET /api/deals` NIE zwraca klienta — na liście dane klienta doklejamy
 * z lokalnej kartoteki po `clientId`, a pełne przychodzą dopiero z `GET /api/deals/:id`.
 * Daty trzymamy jako surowe ISO 8601 (spójnie z resztą aplikacji).
 */
data class Deal(
    val id: String,
    val clientId: String,
    val ownerId: String,
    /** Opiekun bieżącego etapu (z „Procesu sprzedaży"); `null` = nieprzypisany. */
    val stageOwnerId: String? = null,
    val stage: DealStage,
    /** Moment wejścia w bieżący etap — podstawa licznika „X dni w etapie". */
    val stageEnteredAt: String? = null,
    val source: String? = null,
    val nextContactAt: String? = null,
    val segment: DealSegment = DealSegment.INDYWIDUALNY,
    val buildingKind: DealBuildingKind = DealBuildingKind.NOWY,
    val difficulty: DealDifficulty? = null,
    val buyerPersona: DealBuyerPersona? = null,
    val projectName: String? = null,
    val buildingData: DealBuildingData? = null,
    val ozcData: DealOzcData? = null,
    val description: String? = null,
    val discountCode: String? = null,
    val driveFolder: String? = null,
    /** Zgoda RODO; `rodoConsentAt` stempluje API przy zaznaczeniu. */
    val rodoConsent: Boolean = false,
    val rodoConsentAt: String? = null,
    /** Wyjątek „osoba starsza" — lead→kwalifikacja bez e-maila. */
    val elderlyContactException: Boolean = false,
    // ── Spotkanie wstępne / wizja ────────────────────────────────────────────
    val meetingKind: MeetingKind? = null,
    val meetingAt: String? = null,
    val meetingOwnerId: String? = null,
    val meetingDurationMin: Int? = null,
    val meetingUrl: String? = null,
    // ── Audyt ────────────────────────────────────────────────────────────────
    val auditAddressKind: AuditAddressKind? = null,
    val auditAddress: String? = null,
    val auditMeetingAt: String? = null,
    val auditOwnerId: String? = null,
    // ── Dane do faktury ──────────────────────────────────────────────────────
    val billingSameAsInstall: Boolean = true,
    val billingName: String? = null,
    val billingCompany: String? = null,
    val billingNip: String? = null,
    val billingAddress: String? = null,
    // ── Auto-kwalifikacja leada ──────────────────────────────────────────────
    /**
     * `true` = automat nie zakwalifikował leada i czeka na decyzję człowieka.
     * Bez niej board360 po upływie okna (domyślnie 3 dni od `qualReviewAt`)
     * przenosi deal na „Stracone" — dlatego zakładka LEAD krzyczy o tym banerem.
     */
    val qualReview: Boolean = false,
    val qualReviewAt: String? = null,
    val qualReviewReason: String? = null,
    val lostReason: String? = null,
    val lostReasonCategory: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

/**
 * Wpis historii deala (ActivityLog, append-only) — zakładka „historia".
 * `fromStage`/`toStage` wypełnione tylko dla akcji `stage_change`.
 */
data class DealActivity(
    val id: String,
    val action: String,
    val userId: String,
    val createdAt: String,
    val fromStage: DealStage? = null,
    val toStage: DealStage? = null,
    val lostReason: String? = null,
    val note: String? = null,
)

/**
 * Karta deala z `GET /api/deals/:id`: deal + klient + historia zmian.
 *
 * `companions` przychodzi z osobnego `GET /api/deals/:id/contacts` — API celowo
 * nie dokleja go do deala, bo lista kontaktów zmienia się niezależnie od karty.
 * Puste, dopóki dociągnięcie kontaktów nie wróci; brak kontaktów towarzyszących
 * jest normalnym stanem, więc nie odróżniamy „jeszcze nie wiem" od „nie ma".
 */
data class DealDetail(
    val deal: Deal,
    val client: Client?,
    val activities: List<DealActivity> = emptyList(),
    val companions: List<Client> = emptyList(),
)

/** Deal wzbogacony o klienta z lokalnej kartoteki — model karty na liście. */
data class DealListItem(
    val deal: Deal,
    val client: Client?,
) {
    val clientName: String
        get() = client?.displayName?.takeIf { it.isNotBlank() }
            ?: deal.projectName?.takeIf { it.isNotBlank() }
            ?: "Klient bez kartoteki"

    val phone: String? get() = client?.primaryPhone

    /** Miasto z kartoteki, a w razie braku pełny adres. */
    val place: String?
        get() = client?.city?.takeIf { it.isNotBlank() }
            ?: client?.address?.takeIf { it.isNotBlank() }
}
