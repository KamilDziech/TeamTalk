package com.ekotak.teamtalk.domain.repository

import com.ekotak.teamtalk.domain.model.AssistantMessage
import com.ekotak.teamtalk.domain.model.AssistantReply
import com.ekotak.teamtalk.domain.model.Client
import com.ekotak.teamtalk.domain.model.Deal
import com.ekotak.teamtalk.domain.model.DealDetail
import com.ekotak.teamtalk.domain.model.DealDraft
import com.ekotak.teamtalk.domain.model.DealInstallations
import com.ekotak.teamtalk.domain.model.DealStage
import com.ekotak.teamtalk.domain.model.InstallationStage

/**
 * Lejek sprzedaży board360. Świadomie bez cache Room: etap deala zmienia się
 * często i po stronie panelu, więc nieświeża kopia w telefonie myliłaby bardziej
 * niż pomagała. Klientów doklejamy z kartoteki, która cache Roomowy ma.
 */
interface DealRepository {
    /** Lista dealów lejka. `overdue = true` → tylko z zaległym kontaktem. */
    suspend fun getDeals(stage: DealStage? = null, overdue: Boolean = false): List<Deal>

    /** Karta deala: deal + klient + historia zmian. */
    suspend fun getDealDetail(id: String): DealDetail

    /**
     * Zmiana etapu. `lostReasonCategory` wymagane przez API przy przejściu na
     * „Stracone". Zwraca deal po przejściu.
     */
    suspend fun changeStage(
        id: String,
        stage: DealStage,
        lostReasonCategory: String? = null,
        lostReason: String? = null,
        note: String? = null,
    ): Deal

    /**
     * Zapisuje zmiany karty. Wysyła wyłącznie pola różniące się od `original`,
     * dzięki czemu nie nadpisuje zmian zrobionych równolegle w panelu.
     * Gdy nic się nie zmieniło, zwraca `original` bez ruchu po sieci.
     */
    suspend fun updateDeal(original: Deal, draft: DealDraft): Deal

    // ── Kontakty towarzyszące ────────────────────────────────────────────────
    // Wszystkie trzy operacje zwracają listę PO zmianie, żeby ekran nie musiał
    // zgadywać wyniku ani robić drugiego zapytania. `setPrimaryContact` jest
    // wyjątkiem: zamienia główny kontakt deala, więc unieważnia całą kartę i
    // wymaga ponownego `getDealDetail`.

    suspend fun getCompanions(dealId: String): List<Client>

    suspend fun addCompanion(dealId: String, clientId: String): List<Client>

    suspend fun removeCompanion(dealId: String, clientId: String): List<Client>

    /** Ustawia kontakt towarzyszący jako główny (zamiana z dotychczasowym). */
    suspend fun setPrimaryContact(dealId: String, clientId: String)

    /** Asystent karty deala — Q&A po komunikacji w tym jednym dealu. */
    suspend fun askAssistant(dealId: String, messages: List<AssistantMessage>): AssistantReply

    /**
     * Migawki instalacji karty deala — wybór klienta per etap instalacyjny.
     * API liczy dziedziczenie z wcześniejszych etapów po swojej stronie, więc
     * karta dostaje wybór już efektywny.
     */
    suspend fun getInstallations(dealId: String): DealInstallations

    /**
     * Nadpisuje migawkę jednego etapu instalacyjnego
     * (`PUT /api/deals/:id/installations/:stage`). `categoryIds` to PEŁNY wybór
     * po zmianie, nie różnica — API zastępuje nim dotychczasową listę. Zwraca
     * komplet migawek, bo zmiana wcześniejszego etapu przelicza dziedziczenie
     * w etapach dalszych.
     */
    suspend fun setInstallations(
        dealId: String,
        stage: InstallationStage,
        categoryIds: List<String>,
    ): DealInstallations
}
