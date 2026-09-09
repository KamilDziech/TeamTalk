package com.ekotak.teamtalk.domain.model

/**
 * Zakładka „Faktura" karty deala.
 *
 * Panel board360 ma tu dziś ATRAPĘ: rysuje drzewo instalacji z etapu „montaz"
 * i listę montaży deala, a prawdziwe faktury żyją w osobnym module „Faktury
 * KSeF" — na poziomie organizacji, bez związku z kartą. Telefon powtarza ten
 * kształt (zakres + montaże), ale dokłada to, po co handlowiec wchodzi w tę
 * zakładkę u klienta: ile jest do zafakturowania, na kogo idzie faktura i czy
 * już wyszła.
 *
 * Rachunku telefon NIE WYMYŚLA — bierze go z podpisanej umowy tego deala
 * (`policzPodglad`), czyli z tego samego miejsca, z którego liczy go § 7
 * dokumentu. Gdyby liczył po swojemu, przy kliencie padłaby inna kwota niż na
 * papierze, który ten klient trzyma w ręku.
 */

/** Skąd wiadomo, że faktura należy do tego deala (oddaje to API). */
enum class InvoiceMatch(val wire: String) {
    /** Zgadza się NIP nabywcy — dopasowanie pewne. */
    NIP("nip"),

    /**
     * Zgadza się tylko nazwa nabywcy. Przy osobie prywatnej nie ma nic
     * pewniejszego, więc karta mówi wprost, że to dopasowanie prawdopodobne.
     */
    NAZWA("name");

    companion object {
        fun fromWire(value: String?): InvoiceMatch =
            entries.firstOrNull { it.wire == value } ?: NAZWA
    }
}

/**
 * Faktura pobrana z KSeF. Kwoty zostają tekstem — tak stoją w XML-u FA(3)
 * i tak oddaje je API; telefon ich nie przelicza, tylko formatuje do wyświetlenia.
 */
data class KsefInvoice(
    val id: String,
    val numerKsef: String,
    val numer: String?,
    val dataWystawienia: String?,
    val wystawca: String?,
    val nabywca: String?,
    val nabywcaNip: String?,
    val netto: String?,
    val vat: String?,
    val brutto: String?,
    val waluta: String,
    val dopasowanie: InvoiceMatch,
)

/** Stan montażu — te same wartości, co `InstallationStatus` w board360. */
enum class MontazStatus(val wire: String, val label: String) {
    RESERVED("reserved", "rezerwacja"),
    PLANNED("planned", "zaplanowany"),
    IN_PROGRESS("in_progress", "w toku"),
    DONE("done", "gotowy");

    companion object {
        fun fromWire(value: String?): MontazStatus =
            entries.firstOrNull { it.wire == value } ?: PLANNED
    }
}

/** Jeden wyjazd ekipy pod ten deal — wiersz listy montaży (podgląd). */
data class DealMontaz(
    val id: String,
    val termin: String?,
    val status: MontazStatus,
    val trudnosc: String?,
    val notatka: String?,
)

/**
 * Wszystko, co zakładka wie o fakturach deala.
 *
 * Pusta lista faktur ma TRZY różne przyczyny i zakładka musi je rozróżniać, bo
 * dla handlowca to trzy różne odpowiedzi: [brakDostepu] („ta sesja nie ma prawa
 * `ksef.view`"), [bladFaktur] („serwer odrzucił odczyt" — np. board360 bez
 * wdrożonej trasy) i brak obu („faktur jeszcze nie ma").
 */
data class DealInvoices(
    val nabywca: String?,
    val nabywcaNip: String?,
    val faktury: List<KsefInvoice> = emptyList(),
    val montaze: List<DealMontaz> = emptyList(),
    val brakDostepu: Boolean = false,
    /**
     * Serwer odpowiedział błędem innym niż odmowa uprawnienia (np. trasa
     * jeszcze niewdrożona). Milczące pokazanie pustej listy kazałoby wtedy
     * handlowcowi szukać faktury, której nikt nawet nie próbował policzyć.
     */
    val bladFaktur: String? = null,
    /** Dane pochodzą z ostatniego pobrania — sieci nie było. */
    val fromCache: Boolean = false,
) {
    /** Ile faktur trafiło tu po samej nazwie — karta oznacza je jako niepewne. */
    val niepewne: Int get() = faktury.count { it.dopasowanie == InvoiceMatch.NAZWA }
}

/**
 * Rachunek deala — ile jest do zafakturowania i w jakich ratach.
 *
 * Liczby biorą się z AKTUALNEJ umowy tego deala, nie z oferty: fakturuje się
 * to, co klient podpisał. Rachunek z umowy niepodpisanej pokazujemy oznaczony,
 * bo do czasu podpisu to jeszcze propozycja.
 */
data class InvoiceRachunek(
    val numerUmowy: String,
    val podpisana: Boolean,
    val kwoty: PodsumowanieKwot,
    val vatStawka: Int,
    val zaliczkaProc: Int,
    /** Ile dni klient ma na zapłatę faktury końcowej (§ 7 umowy). */
    val terminKoncowyDni: Int,
)

/**
 * NIP w postaci, po której board360 dopasowuje faktury: same cyfry i dokładnie
 * dziesięć (`PrismaDealBillingReader.digitsOnly`). `null` = tym wpisem nie
 * znajdziemy niczego, bo w XML-u FA(3) nabywca ma NIP dziesięciocyfrowy.
 *
 * Reguła siedzi tutaj, a nie w karcie, bo karta ma MÓWIĆ dokładnie to, co
 * serwer ROBI — dwie kopie tego warunku rozjechałyby się przy pierwszej zmianie
 * i telefon obiecywałby dopasowanie, którego API nie próbuje.
 */
fun nipDoFaktur(raw: String?): String? =
    raw.orEmpty().filter { it.isDigit() }.takeIf { it.length == 10 }

/**
 * „11 cyfr", „2 cyfry", „1 cyfrę" — polska odmiana po liczbie. Bez tego
 * ostrzeżenie o błędnym NIP-ie czytałoby się jak automat („ma 2 cyfr").
 */
fun cyfrySlowo(ile: Int): String = when {
    ile == 1 -> "cyfrę"
    ile % 10 in 2..4 && ile % 100 !in 12..14 -> "cyfry"
    else -> "cyfr"
}
