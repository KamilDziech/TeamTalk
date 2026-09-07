package com.ekotak.teamtalk.domain.ufh

import com.ekotak.teamtalk.domain.model.ContractItem
import com.ekotak.teamtalk.domain.model.UfhState

/**
 * ZAŁĄCZNIK NR 1 POLICZONY Z AUDYTU — port `deal-offer-load.ts` panelu.
 *
 * Te same liczby, które zakładka „Oferta" pokazuje klientowi (ilości z audytu ×
 * ceny jednostkowe z formuły ceny węzła), zamienione na pozycje umowy. Dwa
 * źródła tej samej prawdy zawsze się kiedyś rozjeżdżają, a rozjazd na
 * dokumencie, który klient podpisuje, jest najdroższym z możliwych — dlatego
 * handlowiec niczego tu nie przepisuje z ręki.
 *
 * Wejście (audyt instalacji + jej cennik) przychodzi gotowe z karty deala;
 * rachunek leci lokalnie, tak samo jak w przeglądarce panelu.
 */

private fun grosz(x: Double): Double = Math.round(x * 100.0) / 100.0

/** Instalacja gotowa do przeliczenia: nazwa, audyt i cennik jej węzła. */
data class ContractScopeInstallation(
    /** Węzeł-właściciel formularza audytu — wchodzi w klucz pozycji. */
    val ownerId: String?,
    val name: String,
    val form: UfhState?,
    val pricing: OfferPricing?,
)

/**
 * Wynik przeliczenia. `policzone = false` znaczy „nie ma z czego" — wtedy
 * formularz zostawia to, co miał, i pokazuje [braki]. Cicha pusta tabela byłaby
 * gorsza: zaniżona kwota na umowie jest droższa niż komunikat.
 */
data class ContractScopeResult(
    /** Pozycje gotowe do Załącznika nr 1 — `lp` nadane, `etap` domyślnie 1. */
    val pozycje: List<ContractItem>,
    /**
     * Przedmiot umowy (§ 1) opisany wielkościami z audytu. Pusty, gdy nie ma
     * czego opisać — rozpis wielkości i tak jest w Załączniku nr 1.
     */
    val przedmiot: String,
    val netto: Double,
    /** Czego automat nie policzył: instalacje bez formuły ceny, luki w cenniku. */
    val braki: List<String>,
    val policzone: Boolean,
)

/**
 * Wiersz wyceny → pozycja Załącznika nr 1.
 *
 * Cena jednostkowa bywa pusta, gdy ilość składa się z kilku stawek (metraż
 * rozłożony na rozstawy) — wtedy odtwarzamy ją z kwoty, żeby na dokumencie nie
 * było pustej rubryki „cena netto". Pozycja wyzerowana (np. „wykonane na etapie
 * wod-kan") ZOSTAJE w rozpisie z ceną 0 i powodem w opisie: klient ma widzieć
 * pełny zakres, także to, za co nie płaci.
 */
private fun pozycjaZWiersza(row: ScopeRow, prefiks: String, klucz: String): ContractItem {
    val ilosc = row.qty?.takeIf { it > 0 } ?: 1.0
    val cena = if (row.zeroed != null) 0.0 else (row.unitNet ?: grosz(row.net / ilosc))
    val opis = listOf(
        prefiks,
        row.name,
        if (row.spec.isNotBlank()) "— ${row.spec}" else "",
        row.zeroed?.let { "($it)" }.orEmpty(),
    ).filter { it.isNotBlank() }.joinToString(" ")
    return ContractItem(
        lp = 0,
        opis = opis,
        ilosc = ilosc,
        jm = row.unit,
        cenaNetto = cena,
        etap = 1,
        klucz = klucz,
    )
}

/** „1 obwód" / „3 obwody" / „12 obwodów" — dokument prawny, więc odmieniamy. */
private fun obwody(n: Int): String {
    if (n == 1) return "1 obwód"
    val d = n % 10
    val s = n % 100
    val maloMnoga = d in 2..4 && s !in 12..14
    return "$n ${if (maloMnoga) "obwody" else "obwodów"}"
}

/**
 * PRZEDMIOT UMOWY (§ 1) opisany wielkościami z audytu.
 *
 * Jedno zdanie, wyłącznie z tego, co audytor faktycznie zaznaczył — bez
 * marketingu i bez wielkości, których nie ma. Szczegóły stoją niżej,
 * w Załączniku nr 1; § 1 ma odpowiedzieć na pytanie „co ja właściwie kupuję".
 */
private fun przedmiotInstalacji(nazwa: String, state: UfhState, input: UfhQuoteInput): String {
    val q = input.quantities
    val czesci = mutableListOf<String>()

    if (input.areas.heated > 0) czesci += "${fmtAreaM2(input.areas.heated)} powierzchni ogrzewanej"
    if (input.loops > 0) {
        czesci += if (input.manifoldsToBuy > 0) {
            val rozdzielacze = if (input.manifoldsToBuy == 1) {
                "1 rozdzielaczu"
            } else {
                "${input.manifoldsToBuy} rozdzielaczach"
            }
            "${obwody(input.loops)} na $rozdzielacze"
        } else {
            obwody(input.loops)
        }
    }
    val system = offerPipeSystemLabel(state.pipeSystem).trim()
    if (system.isNotBlank()) czesci += "system $system"
    if (input.pipeM > 0) czesci += "${fmtPipeMb(input.pipeM)} rury grzewczej"

    val szafki = listOf(
        if (q.boxFlush > 0) "${q.boxFlush} podtynkowe" else "",
        if (q.boxSurface > 0) "${q.boxSurface} natynkowe" else "",
    ).filter { it.isNotBlank() }.joinToString(" i ")
    if (szafki.isNotBlank()) czesci += "szafki rozdzielaczy: $szafki"
    if (state.cooling) czesci += "z funkcją chłodzenia"

    // Nazwa w cudzysłowie, a nie odmieniona: nazwy węzłów katalogu są dowolne,
    // a odmiana ich przez przypadki wyszłaby na dokumencie prawnym gorzej.
    val wstep = "Wykonanie instalacji „$nazwa”"
    return if (czesci.isEmpty()) wstep else "$wstep — ${czesci.joinToString(", ")}"
}

/** Początek zdania, którym automat opisuje przedmiot umowy — patrz wyżej. */
private val PRZEDMIOT_Z_AUTOMATU = Regex("^wykonanie instalacji [„\"]", RegexOption.IGNORE_CASE)

/**
 * Czy przedmiot umowy (§ 1) wygląda na napisany przez automat, a nie przez
 * człowieka. Po tym poznajemy, czy wolno go nadpisać przy przeliczeniu: własne
 * sformułowanie handlowca ma przetrwać zmianę oferty.
 */
fun przedmiotZAutomatu(tekst: String?): Boolean {
    val t = (tekst ?: "").trim()
    return t.isEmpty() || PRZEDMIOT_Z_AUTOMATU.containsMatchIn(t)
}

/**
 * Pozycje umowy policzone z audytów deala. Deal wieloinstalacyjny dostaje
 * pozycje wszystkich instalacji, z nazwą instalacji w opisie — inaczej dwie
 * identyczne linie „Wykonanie instalacji" byłyby nie do odróżnienia.
 */
fun contractScopeItems(installations: List<ContractScopeInstallation>): ContractScopeResult {
    val zAudytem = installations.filter { it.form != null }
    if (zAudytem.isEmpty()) {
        return ContractScopeResult(emptyList(), "", 0.0, emptyList(), policzone = false)
    }

    val pozycje = mutableListOf<ContractItem>()
    val przedmioty = mutableListOf<String>()
    val braki = mutableListOf<String>()
    var policzone = false
    val wieleInstalacji = zAudytem.size > 1

    for (inst in zAudytem) {
        val state = inst.form ?: continue
        val nazwa = inst.name.ifBlank { "instalacja" }
        val input = ufhQuoteInput(state)

        if (!auditUsable(input)) {
            braki += "$nazwa: audyt bez metrażu i pomiaru z rzutu — nie ma czego wyceniać"
            continue
        }
        val pricing = inst.pricing
        if (pricing == null) {
            braki += "$nazwa: brak formuły ceny węzła — pozycje trzeba wpisać ręcznie"
            continue
        }

        val scope = offerScope(state, input, pricing)
        if (scope.rows.isEmpty()) {
            braki += "$nazwa: wycena nie dała żadnej pozycji"
            continue
        }
        policzone = true
        przedmioty += przedmiotInstalacji(nazwa, state, input)
        scope.gaps.forEach { braki += "$nazwa: $it" }
        scope.rows.forEach { row ->
            pozycje += pozycjaZWiersza(
                row = row,
                prefiks = if (wieleInstalacji) "$nazwa:" else "",
                klucz = "${inst.ownerId ?: "-"}:${row.key}",
            )
        }
    }

    return ContractScopeResult(
        pozycje = pozycje.mapIndexed { i, p -> p.copy(lp = i + 1) },
        // Średnik, nie nowa linia: § 1 drukuje się jako jeden akapit.
        przedmiot = if (przedmioty.isEmpty()) "" else "${przedmioty.joinToString("; ")}.",
        netto = grosz(pozycje.sumOf { grosz(it.ilosc * it.cenaNetto) }),
        braki = braki,
        policzone = policzone,
    )
}

/**
 * Nakłada świeżo policzone pozycje na te, które są już w formularzu/umowie.
 *
 * Trzy zasady, wszystkie po to, żeby przeliczenie niczego nie zjadło:
 *  1. pozycja z tym samym `klucz` bierze NOWĄ ilość i cenę, ale ZACHOWUJE etap
 *     przypisany wcześniej ręcznie (§ 7 nie przeskakuje sam),
 *  2. pozycja bez klucza (dopisana z ręki) zostaje nietknięta na końcu listy,
 *  3. pozycja z kluczem, której wycena już nie zwraca, znika — wypadła
 *     z audytu, więc nie ma czego fakturować.
 *
 * WYJĄTEK na umowy sprzed automatu: gdy ŻADNA ze starych pozycji nie ma klucza,
 * cała lista pochodzi sprzed wprowadzenia przeliczania i jest odpowiednikiem
 * tego, co wycena właśnie policzyła — trzymanie jej obok podwoiłoby rozpis.
 */
fun scalPozycje(stare: List<ContractItem>, nowe: List<ContractItem>): List<ContractItem> {
    val poKluczu = stare.filter { it.klucz != null }.associateBy { it.klucz!! }
    val legacy = poKluczu.isEmpty()
    val reczne = if (legacy) emptyList() else stare.filter { it.klucz == null && it.opis.isNotBlank() }

    val zAudytu = nowe.map { p ->
        val etap = p.klucz?.let { poKluczu[it]?.etap }?.takeIf { it > 0 } ?: p.etap
        p.copy(etap = etap)
    }

    return (zAudytu + reczne).mapIndexed { i, p -> p.copy(lp = i + 1) }
}
