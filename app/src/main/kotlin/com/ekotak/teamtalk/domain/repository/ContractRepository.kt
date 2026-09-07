package com.ekotak.teamtalk.domain.repository

import com.ekotak.teamtalk.domain.model.ContractFilling
import com.ekotak.teamtalk.domain.model.ContractKind
import com.ekotak.teamtalk.domain.model.ContractPreview
import com.ekotak.teamtalk.domain.model.DealContract
import java.io.File

/**
 * Zakładka „Umowa" karty deala — z cache Room i kolejką offline.
 *
 * Dlaczego pełny offline: umowę podpisuje się u klienta w domu albo na budowie,
 * a nie przy biurku. Handlowiec wystawia ją tam, gdzie właśnie skończył
 * rozmowę, i tam najczęściej nie ma zasięgu. Utrata wystawionej umowy to nie
 * „wpiszę jeszcze raz" — to druga wizyta u klienta.
 *
 * Czego kolejka nie zrobi: numeru, PDF-a i linku do podpisu nadaje SERWER.
 * Umowa wystawiona bez zasięgu stoi więc na liście jako „czeka na wysyłkę",
 * bez linku — i zakładka mówi to wprost, zamiast pokazywać martwy adres.
 *
 * Odczyt: najpierw sieć, przy jej braku ostatnia kopia. Zapis: najpierw sieć,
 * przy jej braku kolejka. Rozróżnienie brak-sieci (`IOException`) od odmowy
 * serwera (`HttpException`) jest tu istotne: 403 bez `deal.manage` ani 409 przy
 * zmianie, której ktoś już dokonał, nie staną się prawdziwe przez ponowienie.
 */
interface ContractRepository {

    /** Umowy deala; przy braku sieci z cache, z nałożoną kolejką. */
    suspend fun getContracts(dealId: String): ContractsSnapshot

    /**
     * Treść umowy jako formularz — prefill zmiany i wystawienia po terminie.
     * Bez sieci z cache; `null` = nigdy jej nie pobraliśmy.
     */
    suspend fun getFilling(dealId: String, contractId: String): ContractFilling?

    /** Podgląd dokumentu (HTML). Bez sieci z ostatniej kopii; `null` = brak kopii. */
    suspend fun getPreview(dealId: String, contractId: String): ContractPreview?

    /**
     * Gotowy PDF do pliku — stąd oddaje go systemowi „Udostępnij". PDF składa
     * serwer, więc bez zasięgu tej akcji nie ma; podgląd HTML zostaje.
     */
    suspend fun downloadPdf(dealId: String, contractId: String, target: File)

    /**
     * Zestawienie materiałowe do migawki umowy — z aktywnej rezerwacji deala,
     * którą policzył panel z audytu.
     *
     * Telefon materiału NIE LICZY: dobór to kilkaset linijek rachunku po stronie
     * panelu, a druga implementacja tej samej matematyki zamawiałaby zły towar
     * (ta sama decyzja, co przy „Przelicz z audytu" w zakładce „Zamówienie").
     * Pusta lista = deal nie ma jeszcze rezerwacji; wtedy zakładka mówi wprost,
     * że po podpisie magazyn trzeba ruszyć z panelu.
     */
    suspend fun materialsFromReservation(dealId: String): List<ContractMaterialLine>

    /** Nowa umowa. Bez zasięgu ląduje w kolejce razem z całą treścią. */
    suspend fun generate(dealId: String, filling: ContractFilling): ContractSaveResult

    /** Zmiana podpisanej umowy: nowa wersja albo aneks, z obowiązkowym powodem. */
    suspend fun requestChange(
        dealId: String,
        contractId: String,
        filling: ContractFilling,
        powod: String,
        rodzaj: ContractKind,
    ): ContractSaveResult

    /** Akceptacja zmiany przez zarząd — dopiero ona wysyła umowę do klienta. */
    suspend fun approveChange(dealId: String, contractId: String): ContractSaveResult

    /** Odrzucenie wniosku o zmianę — podpisana umowa zostaje bez zmian. */
    suspend fun rejectChange(dealId: String, contractId: String, powod: String?): ContractSaveResult

    /** Nowy link do podpisu dla istniejącej umowy. */
    suspend fun resend(dealId: String, contractId: String): ContractSaveResult

    /** Unieważnienie linku albo wycofanie zmiany. */
    suspend fun cancel(dealId: String, contractId: String): ContractSaveResult

    /** Odtworzenie zamówienia z Załącznika nr 1 podpisanej umowy. */
    suspend fun rebuildOrder(dealId: String, contractId: String): ContractOrderRebuild

    /** Opróżnienie kolejki — woła `ContractSyncWorker`, gdy wróci sieć. */
    suspend fun syncPendingMutations(): ContractSyncResult
}

/** Materiał z rezerwacji deala w kształcie migawki umowy. */
data class ContractMaterialLine(
    val productId: String?,
    val kod: String?,
    val nazwa: String,
    val ilosc: Double,
    val jm: String,
    val klucz: String?,
    val uwaga: String?,
)

data class ContractsSnapshot(
    val contracts: List<DealContract> = emptyList(),
    /** `true` = pokazujemy ostatnią kopię, bo sieci nie było. */
    val fromCache: Boolean = false,
    /** Komunikat odmowy serwera (np. brak `crm.view`); `null` = odczyt się udał. */
    val error: String? = null,
)

/**
 * Co się stało z zapisem. Różnica idzie aż na ekran: „link gotowy do wysłania
 * klientowi" i „umowa czeka w telefonie na zasięg" to dwie różne informacje —
 * przy drugiej handlowiec nie ma czego dać klientowi.
 */
data class ContractSaveResult(
    val queued: Boolean,
    /** Ścieżka `/umowa/<token>`; `null` przy zapisie z kolejki i przy decyzjach. */
    val sciezkaPodpisu: String? = null,
    val numer: String? = null,
    val id: String? = null,
    /** `do-podpisu` albo `czeka-na-akceptacje` przy zmianie umowy. */
    val stan: String? = null,
)

/** Wynik odtworzenia zamówienia — panel dobiera po nim komunikat. */
data class ContractOrderRebuild(
    val queued: Boolean,
    val numer: String? = null,
    /** `utworzone` | `zmienione` | `istnialo`. */
    val status: String? = null,
    val pozycje: Int = 0,
    val zamowienia: Int = 0,
)

/** Wynik przebiegu kolejki: `RETRY` = sieć znowu zawiodła, wpisy zostają. */
enum class ContractSyncResult { DONE, RETRY }
