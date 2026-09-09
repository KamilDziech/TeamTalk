package com.ekotak.teamtalk.domain.repository

import com.ekotak.teamtalk.domain.model.Audit
import com.ekotak.teamtalk.domain.model.Category
import com.ekotak.teamtalk.domain.model.OfferLock
import com.ekotak.teamtalk.domain.model.UfhState

/**
 * Audyty karty deala — z cache Room i kolejką offline.
 *
 * Dlaczego akurat tu, skoro `DealRepository` cache'u nie ma: audyt robi się
 * w domu w budowie, gdzie zasięgu zwykle nie ma, a formularz jest podstawą
 * oferty. Utrata odpowiedzi wpisanych przy kliencie to drugi dojazd, więc
 * zapis MUSI przeżyć brak sieci — inaczej niż etap deala, który zmienia się
 * w panelu i którego nieświeża kopia myliłaby bardziej, niż pomagała.
 */
interface AuditRepository {
    /**
     * Audyty deala. Przy braku sieci zwraca kopię z ostatniego pobrania razem
     * z zapisami czekającymi w kolejce (`Audit.pendingSince`).
     */
    suspend fun getAudits(dealId: String): List<Audit>

    /**
     * Katalog technologii — z niego dziedziczy się szablon formularza audytu.
     * Bez zasięgu z cache; pusty wynik znaczy, że katalogu nigdy nie pobrano
     * na tym telefonie i pierwszego audytu nie da się zacząć offline.
     */
    suspend fun getCategories(): List<Category>

    /** Instalacje deala potrzebne zakładce; bez zasięgu z ostatniego pobrania. */
    suspend fun getAuditInstallations(dealId: String): AuditInstallations

    /**
     * Formularz audytu instalacji dla pary (deal + węzeł katalogu). Gdy deal
     * ma już rekord dla tego węzła, wchodzi `PATCH`; inaczej `POST`. Bez sieci
     * zapis ląduje w kolejce i od razu w cache, żeby audytor zobaczył swoją
     * pracę zapisaną.
     *
     * Odmowa serwera (409 przy podpisanej umowie, 403, 422) to nie brak sieci —
     * taki błąd leci dalej, zamiast wozić zmianę w kółko po kolejce.
     */
    suspend fun saveInstallationAudit(
        dealId: String,
        auditId: String?,
        categoryId: String,
        state: UfhState,
        includeCooling: Boolean,
    ): AuditSaveResult

    /**
     * Umowa zamykająca ofertę deala; `null` = nic nie jest podpisane albo
     * odczyt się nie udał. Odczyt jest wygodą (żeby nie dać audytorowi pisać
     * w formularz, którego i tak nie zapisze) — strażnikiem zostaje API.
     */
    suspend fun getOfferLock(dealId: String): OfferLock?

    /** Opróżnienie kolejki — woła `AuditSyncWorker`, gdy wróci sieć. */
    suspend fun syncPendingMutations(): AuditSyncResult
}

/** Wynik przebiegu kolejki: `RETRY` = sieć znowu zawiodła, wpisy zostają. */
enum class AuditSyncResult { DONE, RETRY }

/**
 * Co się stało z zapisem. Rozróżnienie idzie aż do komunikatu na ekranie:
 * „zapisano" i „zapisano w telefonie, wyślemy w zasięgu" to dla audytora
 * u klienta dwie różne informacje, a druga decyduje o tym, czy wyjdzie
 * z budynku spokojny.
 */
enum class AuditSaveResult { SENT, QUEUED }

/**
 * Instalacje deala widziane przez zakładkę „Audyt".
 *
 * [auditStage] to węzły, które audytor ma obejść. [allStages] służy jednej
 * rzeczy: rozpoznaniu pompy ciepła gdziekolwiek w dealu, bo to ona odsłania
 * pytanie o chłodzenie podłogówką. Trzymamy je razem, żeby offline nie zgubić
 * tego drugiego — audyt zapisany bez tej wiedzy wysłałby `cooling: false`
 * i skasował odpowiedź daną wcześniej w panelu.
 */
data class AuditInstallations(
    val auditStage: List<String> = emptyList(),
    val allStages: List<String> = emptyList(),
    /**
     * Węzły etapu „sold" — zakres kupiony przez klienta. Nie służy audytowi:
     * czyta go zakładka „Zamówienie", która z tej samej migawki (i tego samego
     * zapytania) rysuje drzewo zakresu, także bez zasięgu.
     */
    val soldStage: List<String> = emptyList(),
    /**
     * Migawki WSZYSTKICH etapów: `stage.wire` → id węzłów. Czyta je zakładka
     * „Oferta", która — tak samo jak panel — schodzi kaskadą od etapu „Oferta"
     * w dół (angebot → audit → sold → montaz → edukacja → lead) i bierze
     * pierwszy niepusty. Pozostałe pola zostają, bo mają własnych czytelników.
     */
    val byStage: Map<String, List<String>> = emptyMap(),
)
