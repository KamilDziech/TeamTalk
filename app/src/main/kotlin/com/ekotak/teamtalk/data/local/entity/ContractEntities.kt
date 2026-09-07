package com.ekotak.teamtalk.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cache i kolejka zakładki „Umowa".
 *
 * Umowę podpisuje się u klienta w domu, a nie przy biurku — i to tam najczęściej
 * nie ma zasięgu. Dlatego zakładka trzyma pełną kopię: listę umów, treść
 * dokumentu do prefillu zmiany i HTML podglądu. Zapisy idą przez kolejkę,
 * tak jak w „Zamówieniu" i „Rozliczeniu".
 *
 * Czego kolejka NIE potrafi: nadać numeru, złożyć PDF-a ani wystawić linku do
 * podpisu — to robi serwer. Umowa wystawiona bez zasięgu stoi więc na liście
 * z podpisem „czeka na wysyłkę", a link i dokument pojawiają się po
 * synchronizacji. Zakładka mówi to wprost, żeby nikt nie próbował dać klientowi
 * linku, którego jeszcze nie ma.
 */

/**
 * Umowa deala w cache. Trzymamy CAŁY `ContractDto` jako JSON, a nie rozbity na
 * kolumny: to kontrakt z panelem (dwadzieścia parę pól, wszystkie czytane
 * razem), a rozbicie go tutaj kazałoby dokładać kolumnę i migrację przy każdym
 * nowym polu modułu umów.
 */
@Entity(tableName = "deal_contracts")
data class DealContractEntity(
    @PrimaryKey val id: String,
    val dealId: String,
    /** `ContractDto` zserializowane wspólnym `Json` modułu. */
    val payload: String,
    /** Data utworzenia — po niej układa się lista (serwer oddaje od najnowszej). */
    val createdAt: String,
    val syncedAt: Long,
)

/**
 * Treść umowy w kształcie formularza (`GET .../wypelnienie`) — prefill zmiany
 * i wystawienia nowej umowy po terminie odesłania. Trzymamy ją osobno, bo to
 * osobny odczyt: lista umów jej nie niesie, a bez zasięgu formularz zmiany
 * byłby pusty i handlowiec pisałby umowę od zera.
 */
@Entity(tableName = "contract_fillings")
data class ContractFillingEntity(
    @PrimaryKey val contractId: String,
    val dealId: String,
    val numer: String,
    val wersja: Int,
    /** `ContractFillingDto` zserializowane wspólnym `Json` modułu. */
    val payload: String,
    val syncedAt: Long,
)

/**
 * Podgląd dokumentu (HTML, ten sam co idzie do PDF). Kopia w bazie jest po to,
 * żeby handlowiec mógł pokazać klientowi treść umowy bez zasięgu — PDF wymaga
 * pobrania z serwera, a HTML raz obejrzany zostaje w telefonie.
 */
@Entity(tableName = "contract_previews")
data class ContractPreviewEntity(
    @PrimaryKey val contractId: String,
    val numer: String,
    val podpisana: Boolean,
    val html: String,
    val syncedAt: Long,
)

/**
 * Zapis zakładki czekający na wysyłkę.
 *
 * Jedna tabela na wszystkie rodzaje zapisu — wszystkie idą tą samą drogą i mają
 * wspólny porządek wysyłki (akceptacja zmiany musi pójść PO zmianie, unieważnienie
 * po wystawieniu). Klucz (`targetId`, `kind`) sprawia, że powtórzona decyzja
 * nadpisuje poprzednią: liczy się ostatnia wola człowieka, a nie ścieżka,
 * którą do niej doszedł.
 */
@Entity(tableName = "contract_mutations", primaryKeys = ["targetId", "kind"])
data class ContractMutationEntity(
    /** Id umowy, której dotyczy zapis; przy nowej umowie lokalne `local:…`. */
    val targetId: String,
    val kind: String,
    /** Gotowe ciało żądania. */
    val payload: String,
    val dealId: String,
    val createdAt: Long,
) {
    companion object {
        /** `POST /deals/{id}/contracts` — nowa umowa wystawiona bez zasięgu. */
        const val KIND_CREATE = "contract_create"

        /** `POST .../{id}/change` — zmiana podpisanej umowy (nowa wersja albo aneks). */
        const val KIND_CHANGE = "contract_change"

        /** `POST .../{id}/change/approve` — decyzja zarządu na „tak". */
        const val KIND_APPROVE = "contract_approve"

        /** `POST .../{id}/change/reject` — decyzja zarządu na „nie". */
        const val KIND_REJECT = "contract_reject"

        /** `POST .../{id}/resend` — nowy link do podpisu. */
        const val KIND_RESEND = "contract_resend"

        /** `DELETE .../{id}` — unieważnienie linku albo wycofanie zmiany. */
        const val KIND_CANCEL = "contract_cancel"

        /** `POST .../{id}/zamowienie` — odtworzenie zamówienia z umowy. */
        const val KIND_ORDER = "contract_order"

        /** Prefiks identyfikatora nadawanego lokalnie do czasu wysłania. */
        const val LOCAL_ID_PREFIX = "local:"
    }
}
