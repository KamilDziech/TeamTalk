package com.ekotak.teamtalk.domain.model

/**
 * Audyt deala (`GET /api/deals/:id/audits`, FR-18). W board360 jeden rekord
 * `Audit` obsługuje DWIE różne rzeczy, rozróżniane zawartością `formData`:
 *
 *  • **Heizlast** — zapotrzebowanie budynku na ciepło (`heatloadMode`,
 *    `heatloadKw`). Telefon go NIE prowadzi: te wpisy zakłada i pokazuje sam
 *    panel, tutaj wpadają na listę jako rekordy bez `installationForm` i nikt
 *    ich nie czyta. Kolumny cache (`AuditEntity`) zostają, żeby kopia była
 *    wierna odpowiedzi serwera.
 *  • **Formularz audytu instalacji** — `formData.kind == UFH_AUDIT_KIND`, jeden
 *    rekord na parę (deal + węzeł katalogu). To on jest podstawą oferty i to
 *    jedyna rzecz, którą telefon z tego endpointu edytuje.
 */
data class Audit(
    val id: String,
    val dealId: String = "",
    /** ISO-8601 z API — formatowanie zostawiamy warstwie prezentacji. */
    val createdAt: String = "",
    /** Notatka audytora z `formData.note`. */
    val note: String? = null,
    /** Marker `formData.kind` — niepusty tylko dla formularza instalacji. */
    val formKind: String? = null,
    /** `formData.categoryId` — węzeł katalogu, którego dotyczy formularz. */
    val categoryId: String? = null,
    /**
     * Formularz audytu instalacji rozłożony na pola; `null` dla pozostałych
     * rekordów (np. Heizlast z panelu). Pola, których telefon nie edytuje
     * (warstwa rzutu kondygnacji), jadą w środku jako `UfhFloor.planJson`
     * i wracają do API nietknięte.
     */
    val installationForm: UfhState? = null,
    /**
     * Kiedy zapis trafił do kolejki offline; `null` = rekord zgodny z serwerem.
     * Zakładka pokazuje po tym „czeka na wysyłkę", żeby audytor wiedział, że
     * jego praca jest zapisana w telefonie, ale panel jej jeszcze nie widzi.
     */
    val pendingSince: Long? = null,
)

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
