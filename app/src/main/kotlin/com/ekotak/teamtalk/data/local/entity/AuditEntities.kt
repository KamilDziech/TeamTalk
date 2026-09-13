package com.ekotak.teamtalk.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cache i kolejka zakładki „Audyt". Audyt robi się w domu w budowie, gdzie
 * zasięgu zwykle nie ma — a formularz jest podstawą oferty, więc utrata
 * odpowiedzi wpisanych przy kliencie oznacza drugi dojazd.
 */

/**
 * Audyt deala w cache. `formData` trzymamy jako surowy JSON: to kontrakt
 * z panelem (patrz `AuditMapper`), a rozkładanie go na kolumny tylko po to,
 * żeby złożyć z powrotem przy wysyłce, gubiłoby pola, których telefon nie
 * edytuje (warstwa rzutu kondygnacji).
 */
@Entity(tableName = "audits")
data class AuditEntity(
    /** Id serwerowe albo lokalne (`local:…`) do czasu wysłania rekordu. */
    @PrimaryKey val id: String,
    val dealId: String,
    /**
     * Heizlast rekordu — telefon go NIE prowadzi (wpisy zakłada i pokazuje
     * panel), ale kopia ma być wierna odpowiedzi serwera, więc obie kolumny
     * zostają jako czysty passthrough. Kasowanie ich znaczyłoby przebudowę
     * tabeli w migracji za zero pożytku.
     */
    val heatloadMode: String?,
    val heatloadKw: Double?,
    val formData: String?,
    val createdAt: String,
    /**
     * Kiedy zmiana trafiła do kolejki; `null` = rekord zgodny z serwerem.
     * Wiersz zakładki pokazuje po tym „czeka na wysyłkę".
     */
    val pendingSince: Long?,
    /**
     * `updatedAt` wersji SERWERA, którą ten wiersz odzwierciedla (ISO).
     * Zapis do kolejki go nie rusza — przy niewysłanej zmianie to nadal wersja,
     * na której audytor zaczął edycję. `null` = wiersz sprzed migracji 28 → 29
     * albo API bez tego pola; wtedy zapis idzie bez sprawdzania konfliktu.
     */
    val updatedAt: String? = null,
)

/**
 * Katalog technologii w cache. Bez niego formularz audytu nie ma czego
 * dziedziczyć (`auditForm`) ani jak nazwać instalacji, więc PIERWSZY audyt
 * danego węzła bez zasięgu byłby niemożliwy.
 *
 * Cache jest globalny (nie per deal) i podmieniany w całości przy każdym
 * udanym odczycie — katalog zmienia się rzadko i tylko w panelu.
 */
@Entity(tableName = "catalog_categories")
data class CatalogCategoryEntity(
    @PrimaryKey val id: String,
    val parentId: String?,
    val name: String,
    val position: Int,
    /** Szablon `Category.auditForm` jako surowy JSON; `null` = węzeł go nie ma. */
    val auditForm: String?,
    /**
     * Role montażowe węzła (`Category.montageRoles`) — z nich zakładka „Montaż"
     * liczy pokrycie obsady. Pusto = wiersz sprzed migracji 25 → 26 albo węzeł
     * bez własnego zakresu montażowego (wtedy dziedziczy go po rodzicu).
     */
    val montageRoles: List<String> = emptyList(),
    /** Zawartość karty „🧰 Narzędzia" węzła jako surowy JSON (`Category.tools`). */
    val tools: String? = null,
)

/**
 * Migawka instalacji etapu „Audyt" jednego deala — lista węzłów katalogu,
 * które audytor ma obejść. Osobno od pełnych migawek karty deala: zakładka
 * używa dokładnie tego jednego etapu, a reszta i tak leci z sieci.
 */
@Entity(tableName = "audit_installations")
data class AuditInstallationsEntity(
    @PrimaryKey val dealId: String,
    /** Id węzłów po przecinku (konwerter `Converters.fromStringList`). */
    val categoryIds: List<String>,
    /**
     * Węzły ze WSZYSTKICH etapów deala. Potrzebne do jednej rzeczy: rozpoznania
     * pompy ciepła, która odsłania pytanie o chłodzenie podłogówką. Bez tego
     * audyt zapisany offline chowałby to pytanie i wysyłał `cooling: false`,
     * kasując odpowiedź daną wcześniej w panelu.
     */
    val allStageCategoryIds: List<String>,
    /**
     * Węzły etapu „sold" — zakres, który klient KUPIŁ. Trzyma je ta sama tabela,
     * bo to jedna migawka instalacji deala i jedno zapytanie do API; zakładka
     * „Zamówienie" rysuje z nich drzewo zakresu, także bez zasięgu.
     */
    val soldStageCategoryIds: List<String> = emptyList(),
    /**
     * Wszystkie etapy naraz jako JSON `{ "angebot": ["id", …], … }`. Zakładka
     * „Oferta" schodzi po nich kaskadą (angebot → audit → sold → …), a osobne
     * kolumny per etap dokładałyby migrację przy każdym nowym czytelniku.
     */
    val stagesJson: String = "{}",
    val syncedAt: Long,
)

/**
 * Zapis audytu czekający na wysyłkę.
 *
 * Inaczej niż w Zadaniach jeden wiersz to CAŁY dokument, nie pojedyncze pole:
 * `Audit.formData` jest jednym dokumentem, który API podmienia w całości, więc
 * scalanie pól z dwóch zapisów dałoby formularz, którego nikt nie wypełnił.
 * Klucz (`auditId`, `field`) sprawia, że kolejny zapis tego samego audytu
 * nadpisuje poprzedni — liczy się ostatnia decyzja audytora.
 */
@Entity(tableName = "audit_mutations", primaryKeys = ["auditId", "field"])
data class AuditMutationEntity(
    val auditId: String,
    val field: String,
    /**
     * Gotowe ciało żądania (`{"formData":{…}}`). BEZ `expectedUpdatedAt` —
     * ten dokłada wysyłka z [baseUpdatedAt], żeby rozstrzygnięcie konfliktu
     * nie musiało przepisywać dokumentu.
     */
    val payload: String,
    val dealId: String,
    val createdAt: Long,
    /**
     * `updatedAt` wersji serwera, na której audytor ZACZĄŁ edycję. Kolejne
     * zapisy tego samego audytu przed wysyłką go nie zmieniają — inaczej
     * zmiana z panelu zrobiona w międzyczasie przeszłaby niezauważona.
     * Przesuwa go dopiero udana wysyłka (na `updatedAt` z odpowiedzi) albo
     * „nadpisz" (na `updatedAt` bieżącej wersji serwera). `null` = wysyłka
     * bez warunku (rekord sprzed migracji, `POST`).
     */
    val baseUpdatedAt: String? = null,
    /**
     * Bieżący audyt z serwera (`current` z 409 `AUDIT_STALE`) jako surowy JSON.
     * Niepusty = wiersz w konflikcie: worker go NIE wysyła i NIE kasuje, czeka
     * na decyzję człowieka („nadpisz" / „porzuć moje").
     */
    val conflictJson: String? = null,
    /** Kiedy telefon dostał 409 `AUDIT_STALE` (epoch ms). */
    val conflictAt: Long? = null,
) {
    companion object {
        /** Rekordu jeszcze nie ma na serwerze — pójdzie `POST`. */
        const val FIELD_CREATE = "__create"

        /** Rekord istnieje — pójdzie `PATCH` z podmianą `formData`. */
        const val FIELD_FORM = "__form"

        /** Prefiks identyfikatora nadawanego lokalnie do czasu wysłania. */
        const val LOCAL_ID_PREFIX = "local:"
    }
}
