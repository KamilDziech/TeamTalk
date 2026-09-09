package com.ekotak.teamtalk.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Zakładka „Faktura" karty deala — dwa niezależne odczyty.
 *
 * Faktury przychodzą z KSeF na poziom ORGANIZACJI: pobrana faktura zna
 * nabywcę, a nie kartę w lejku. Dopasowanie do deala robi board360
 * (`ListDealKsefInvoices`) i mówi wprost, po czym dopasował — telefon tego
 * NIE liczy po swojemu, bo druga implementacja tej samej reguły pokazywałaby
 * przy kliencie inny zestaw faktur niż panel w biurze.
 *
 * Kwoty zostają STRINGAMI, tak jak oddaje je API: to wartości przepisane
 * z XML-a FA(3), gdzie „42000.00" jest zapisem urzędowym. Zamiana na `Double`
 * po drodze przez telefon dokładałaby zaokrąglenie tam, gdzie go nie było.
 */
@Serializable
data class DealInvoicesDto(
    val buyer: InvoiceBuyerDto = InvoiceBuyerDto(),
    val invoices: List<KsefInvoiceDto> = emptyList(),
)

/** Po kim szukaliśmy faktur — karta pokazuje to, gdy nic nie znalazła. */
@Serializable
data class InvoiceBuyerDto(
    val nip: String? = null,
    val label: String? = null,
)

@Serializable
data class KsefInvoiceDto(
    val id: String = "",
    val ksefNumber: String = "",
    val direction: String = "sales",
    val invoiceNumber: String? = null,
    val issueDate: String? = null,
    val issuerName: String? = null,
    val issuerNip: String? = null,
    val buyerName: String? = null,
    val buyerNip: String? = null,
    val netAmount: String? = null,
    val vatAmount: String? = null,
    val grossAmount: String? = null,
    val currency: String = "PLN",
    /** `nip` = dopasowanie pewne, `name` = prawdopodobne (klient bez NIP-u). */
    val match: String = "name",
)

/**
 * Montaż deala (`GET /api/installations?dealId=`). Zakładka „Faktura" panelu
 * pokazuje tę samą listę pod drzewem zakresu, więc telefon czyta to samo.
 * Rezerwacji terminu (`status = "reserved"`) panel do listy nie bierze —
 * odsiewamy ją tak samo, po stronie klienta.
 */
@Serializable
data class InstallationDto(
    val id: String = "",
    val dealId: String = "",
    val scheduledAt: String? = null,
    val status: String = "planned",
    val difficulty: String? = null,
    val teamNote: String? = null,
)
