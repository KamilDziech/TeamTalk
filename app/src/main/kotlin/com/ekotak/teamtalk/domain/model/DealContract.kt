package com.ekotak.teamtalk.domain.model

import com.ekotak.teamtalk.domain.ufh.zlText
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Umowy karty deala — port `contract-actions.ts` i `contract-view.ts` panelu.
 *
 * Model jest 1:1 z tym, co oddaje `ContractsController`: telefon ma pokazywać
 * ten sam stan dokumentu, co panel, bo obie strony mówią o jednym papierze,
 * który klient podpisuje. Wszystko, co panel liczy w przeglądarce (podgląd
 * kwot, licznik terminu odesłania, opis wysyłki), liczy się tutaj tak samo —
 * inaczej handlowiec u klienta widziałby inną kwotę niż biuro.
 */

/** Stan umowy — zgodny z enumem `ContractStatus` w API. */
enum class ContractStatus(val wire: String, val label: String) {
    DRAFT("draft", "Szkic"),
    SENT("sent", "Wysłana do podpisu"),

    /** Minęły 48 h terminu odesłania — link martwy, umowę wystawia się od nowa. */
    EXPIRED("expired", "Termin odesłania minął"),
    SIGNED("signed", "Podpisana"),
    CANCELLED("cancelled", "Unieważniona"),
    SUPERSEDED("superseded", "Zastąpiona po zmianie");

    companion object {
        fun fromWire(value: String?): ContractStatus =
            entries.firstOrNull { it.wire == value } ?: DRAFT
    }
}

/**
 * Rodzaj dokumentu — zgodny z enumem `ContractKind` w API.
 *
 * `umowa` — pełny dokument; wersja po zmianie ZASTĘPUJE poprzednią.
 * `aneks` — krótki dokument zmieniający; umowa pierwotna zostaje w mocy
 * w zakresie, którego aneks nie rusza.
 */
enum class ContractKind(val wire: String) {
    UMOWA("umowa"),
    ANEKS("aneks");

    companion object {
        fun fromWire(value: String?): ContractKind =
            entries.firstOrNull { it.wire == value } ?: UMOWA
    }
}

/** Ślad wniosku o zmianę umowy — kto zgłosił, kto zdecydował i dlaczego. */
data class ContractChange(
    val powod: String?,
    val zgloszona: String,
    val zgloszonaPrzez: String?,
    val zaakceptowana: String?,
    val zaakceptowanaPrzez: String?,
    val odrzucona: String?,
    val odrzuconaPrzez: String?,
    val powodOdrzucenia: String?,
)

/** Wynik odesłania podpisanego PDF klientowi (§ 17 pkt 3). */
enum class ContractEmailStatus(val wire: String) {
    SENT("sent"),
    FAILED("failed"),
    PENDING_CONFIG("pending_config"),
    BRAK_ADRESU("brak-adresu");

    companion object {
        fun fromWire(value: String?): ContractEmailStatus? =
            entries.firstOrNull { it.wire == value }
    }
}

/**
 * Co zmieniamy w tej umowie bez zasięgu. Kolejka na telefonie jest jedna
 * (patrz `ContractRepository`), a karta umowy musi umieć powiedzieć, KTÓRA
 * decyzja czeka — „umowa czeka na wysyłkę" i „akceptacja czeka na wysyłkę" to
 * dla handlowca dwie różne rzeczy.
 */
enum class ContractPending {
    /** Cała umowa powstała offline — nie ma jeszcze numeru, PDF-a ani linku. */
    NOWA,

    /** Zmiana treści (nowa wersja albo aneks) czeka na wysyłkę. */
    ZMIANA,

    /** Akceptacja zmiany przez zarząd czeka na wysyłkę. */
    AKCEPTACJA,

    /** Odrzucenie zmiany czeka na wysyłkę. */
    ODRZUCENIE,

    /** Nowy link do podpisu czeka na wysyłkę. */
    LINK,

    /** Unieważnienie (albo wycofanie zmiany) czeka na wysyłkę. */
    UNIEWAZNIENIE,

    /** Odtworzenie zamówienia z umowy czeka na wysyłkę. */
    ZAMOWIENIE,
}

data class DealContract(
    val id: String,
    val numer: String,
    val status: ContractStatus,
    /** Umowa czy aneks — od tego zależą etykiety na karcie. */
    val rodzaj: ContractKind,
    val utworzona: String,
    val wyslana: String?,
    val podpisana: String?,
    val podpisanaIp: String?,
    val wysylka: ContractEmailStatus?,
    val wyslanaMailem: String?,
    /** Kiedy klient podpisał Załącznik nr 1 — null przy umowach sprzed tego wymogu. */
    val parafaZalacznika: String?,
    /** Umowa podpisana, ale bez podpisu pod załącznikiem — do dosłania. */
    val brakParafy: Boolean,
    /** Do kiedy działa link do podpisu. */
    val wygasaLink: String?,
    /** Ścieżka publiczna `/umowa/<token>`; null gdy umowa unieważniona. */
    val sciezkaPodpisu: String?,
    /** Numer wersji: 1 = umowa pierwotna, 2+ = wersja po zmianie. */
    val wersja: Int,
    /** Numer umowy, którą ta wersja zastępuje (null przy pierwotnej). */
    val zastepuje: String?,
    /** Numer dokumentu, który zmienia tę umowę (null, gdy to najnowszy). */
    val zastapionaPrzez: String?,
    /** Czy następca zastępuje ten dokument (`umowa`), czy tylko zmienia (`aneks`). */
    val rodzajNastepcy: ContractKind?,
    /** Czy następca jest już podpisany („trwa zmiana" vs „zmieniona aneksem"). */
    val nastepcaPodpisany: Boolean,
    /** Wniosek o zmianę, z którego powstała ta wersja. */
    val zmiana: ContractChange?,
    /** Wersja czeka na decyzję zarządu. */
    val czekaNaAkceptacje: Boolean,
    /** Czy TA sesja w ogóle akceptuje zmiany umów (zarząd/admin). */
    val zarzad: Boolean,
    /** Czy TA sesja może zaakceptować/odrzucić czekający wniosek (zarząd). */
    val mogeZdecydowac: Boolean,
    /** Czy TA sesja może zgłosić zmianę tej umowy (zarząd albo opiekun deala). */
    val mozeZmienic: Boolean,
    /** Co czeka w kolejce telefonu; pusty zbiór = karta zgodna z serwerem. */
    val pending: Set<ContractPending> = emptySet(),
) {
    /** Czy cała umowa powstała bez zasięgu i serwer jeszcze o niej nie wie. */
    val localOnly: Boolean get() = ContractPending.NOWA in pending

    /** Etykieta stanu na karcie — kolejka bije stan serwera, bo jest nowsza. */
    val statusLabel: String
        get() = when {
            localOnly -> "Czeka na wysyłkę"
            ContractPending.UNIEWAZNIENIE in pending -> "Unieważnienie czeka na wysyłkę"
            ContractPending.AKCEPTACJA in pending -> "Akceptacja czeka na wysyłkę"
            ContractPending.ODRZUCENIE in pending -> "Odrzucenie czeka na wysyłkę"
            ContractPending.ZMIANA in pending -> "Zmiana czeka na wysyłkę"
            czekaNaAkceptacje -> "Zmiana — czeka na zarząd"
            else -> status.label
        }
}

/** Pozycja Załącznika nr 1 — rozpis oferty, z którego liczy się § 7. */
data class ContractItem(
    val lp: Int,
    val opis: String,
    val ilosc: Double,
    val jm: String,
    val cenaNetto: Double,
    val etap: Int,
    /**
     * Klucz pozycji z wyceny (`ScopeRow.key`). Przeliczenie Załącznika nr 1
     * z audytu trafia po nim w tę samą linię i zachowuje jej etap. Brak klucza
     * = pozycja dopisana ręcznie: przeliczenie jej nie rusza.
     */
    val klucz: String? = null,
)

/** Etap z § 7 — wartość etapu liczy się z przypisanych mu pozycji. */
data class ContractStage(val nr: Int, val nazwa: String)

/**
 * Pozycja zestawienia MATERIAŁOWEGO umowy. Na dokumencie się nie drukuje;
 * z tej listy API zakłada rezerwację materiału w chwili podpisu i zamówienia
 * po jednym na instalację.
 *
 * Telefon tego zestawienia NIE LICZY (patrz `ContractFilling.materialy`) —
 * przenosi je z poprzedniej wersji umowy albo z rezerwacji, którą policzył
 * panel.
 */
data class ContractMaterial(
    val productId: String?,
    val kod: String?,
    val nazwa: String,
    val ilosc: Double,
    val jm: String,
    val klucz: String?,
    val uwaga: String?,
    val instalacjaId: String?,
    val instalacja: String?,
)

/**
 * Treść umowy w kształcie formularza — to samo, co przyjmuje generowanie
 * i co oddaje `GET .../wypelnienie` przy zmianie.
 */
data class ContractFilling(
    val przedmiot: String = "",
    val termin: String = "",
    val podstawaZalacznika: String = "",
    val etapy: List<ContractStage> = listOf(ContractStage(1, "Etap 1 — montaż instalacji")),
    val pozycje: List<ContractItem> = emptyList(),
    val vatStawka: Int = DOMYSLNY_VAT,
    val zaliczkaProc: Int = DOMYSLNA_ZALICZKA,
    val terminKoncowyDni: Int = DOMYSLNE_DNI_KONCOWE,
    /**
     * Zestawienie materiałowe z migawki umowy. Telefon go nie liczy: dobór
     * materiału to kilkaset linijek rachunku po stronie panelu, a druga
     * implementacja tej samej matematyki zamawiałaby zły towar (ta sama
     * decyzja, co przy „Przelicz z audytu" w zakładce „Zamówienie").
     */
    val materialy: List<ContractMaterial> = emptyList(),
)

const val DOMYSLNY_VAT = 8
const val DOMYSLNA_ZALICZKA = 30
const val DOMYSLNE_DNI_KONCOWE = 3

/** Termin odesłania umowy — tyle czasu klient ma na podpis (`WAZNOSC_LINKU_H`). */
const val TERMIN_ODESLANIA_H = 48

/** Gotowy dokument do podglądu — ten sam HTML, z którego powstaje PDF. */
data class ContractPreview(
    val numer: String,
    val podpisana: Boolean,
    val html: String,
    /** `true` = pokazujemy kopię z telefonu, bo sieci nie było. */
    val fromCache: Boolean = false,
)

/**
 * Ile zostało do końca terminu odesłania — tekst na pasku umowy wysłanej.
 * `null`, gdy terminu nie ma (umowa bez tokenu) albo już minął: wtedy karta
 * pokazuje „termin minął", a nie licznik.
 */
fun pozostalyCzas(wygasa: String?, teraz: Instant = Instant.now()): String? {
    if (wygasa.isNullOrBlank()) return null
    val koniec = runCatching { Instant.parse(wygasa) }.getOrNull() ?: return null
    val minuty = ChronoUnit.MINUTES.between(teraz, koniec)
    if (minuty <= 0) return null
    val godziny = minuty / 60
    return if (godziny >= 1) "zostało $godziny h ${minuty % 60} min" else "zostało $minuty min"
}

/** Co pokazać o wysyłce podpisanego PDF do klienta. */
data class OpisWysylki(val tekst: String, val ostrzezenie: Boolean)

fun opisWysylki(status: ContractEmailStatus?): OpisWysylki? = when (status) {
    null -> null
    ContractEmailStatus.SENT -> OpisWysylki("PDF wysłany na e-mail klienta", false)
    ContractEmailStatus.PENDING_CONFIG -> OpisWysylki(
        "Nie wysłano — brak konfiguracji SMTP. Prześlij PDF klientowi ręcznie.",
        true,
    )

    ContractEmailStatus.BRAK_ADRESU -> OpisWysylki(
        "Nie wysłano — klient nie ma adresu e-mail w kartotece.",
        true,
    )

    ContractEmailStatus.FAILED -> OpisWysylki(
        "Wysyłka nie powiodła się — prześlij PDF ręcznie.",
        true,
    )
}

/** Kwota w złotych — ta sama postać, co w podsumowaniu oferty. */
fun zl(x: Double): String = zlText(x)

data class PodsumowanieKwot(
    val netto: Double,
    val vat: Double,
    val brutto: Double,
    val zaliczka: Double,
    val reszta: Double,
    /** Etapy, na które wskazują pozycje, a których nie ma na liście etapów. */
    val sieroty: List<Int>,
)

private fun grosz(x: Double): Double = Math.round(x * 100.0) / 100.0

/**
 * Podgląd kwot liczony tak samo jak w API (wartość etapu = suma jego pozycji),
 * żeby handlowiec widział sumy przed wygenerowaniem, a nie dopiero na PDF-ie.
 * Port `policzPodglad` z `contract-view.ts`.
 */
fun policzPodglad(
    pozycje: List<ContractItem>,
    etapy: List<ContractStage>,
    vatStawka: Int,
    zaliczkaProc: Int,
): PodsumowanieKwot {
    val netto = grosz(pozycje.sumOf { grosz(it.ilosc * it.cenaNetto) })
    val vat = grosz(netto * vatStawka / 100.0)
    val brutto = grosz(netto + vat)
    val zaliczka = grosz(brutto * zaliczkaProc / 100.0)

    val znane = etapy.map { it.nr }.toSet()
    val sieroty = pozycje.map { it.etap }.filterNot { it in znane }.distinct().sorted()

    return PodsumowanieKwot(
        netto = netto,
        vat = vat,
        brutto = brutto,
        zaliczka = zaliczka,
        reszta = grosz(brutto - zaliczka),
        sieroty = sieroty,
    )
}
