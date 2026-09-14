package com.ekotak.teamtalk.domain.model

import java.text.Normalizer

/**
 * Wizytówka zeskanowana w asystencie (`POST /api/assistant/card-scan`) i rozmowa
 * „do czego włożyć ten kontakt". Te same pytania, słowa i reguły co w panelu
 * (`web/src/app/app/assistant/card-flow.ts`) — zmieniając jedno, zmień drugie.
 *
 * Ustalenia z 2026-09-13: asystent pyta o rodzaj (klient indywidualny / B2B /
 * afiliant / inny), przy kliencie — czy od razu lead, czy sama karta, przy
 * „inny" — o rolę (dystrybutor, hurtownia, dostawca, podwykonawca → Kontrahenci,
 * reszta → Inne). Firma, NIP, stanowisko i www trafiają do nowych pól karty.
 */
data class ScannedCard(
    val firstName: String = "",
    val lastName: String = "",
    val companyName: String = "",
    val jobTitle: String = "",
    val nip: String = "",
    val phone: String = "",
    val phone2: String = "",
    val email: String = "",
    val email2: String = "",
    val website: String = "",
    val street: String = "",
    val postalCode: String = "",
    val city: String = "",
) {
    val displayName: String
        get() {
            val person = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")
            return when {
                person.isNotBlank() && companyName.isNotBlank() -> "$person ($companyName)"
                person.isNotBlank() -> person
                companyName.isNotBlank() -> companyName
                else -> "kontakt"
            }
        }

    fun get(field: CardField): String = when (field) {
        CardField.FIRST_NAME -> firstName
        CardField.LAST_NAME -> lastName
        CardField.COMPANY -> companyName
        CardField.JOB_TITLE -> jobTitle
        CardField.NIP -> nip
        CardField.PHONE -> phone
        CardField.PHONE2 -> phone2
        CardField.EMAIL -> email
        CardField.EMAIL2 -> email2
        CardField.WEBSITE -> website
        CardField.STREET -> street
        CardField.POSTAL_CODE -> postalCode
        CardField.CITY -> city
    }

    fun with(field: CardField, value: String): ScannedCard = when (field) {
        CardField.FIRST_NAME -> copy(firstName = value)
        CardField.LAST_NAME -> copy(lastName = value)
        CardField.COMPANY -> copy(companyName = value)
        CardField.JOB_TITLE -> copy(jobTitle = value)
        CardField.NIP -> copy(nip = value)
        CardField.PHONE -> copy(phone = value)
        CardField.PHONE2 -> copy(phone2 = value)
        CardField.EMAIL -> copy(email = value)
        CardField.EMAIL2 -> copy(email2 = value)
        CardField.WEBSITE -> copy(website = value)
        CardField.STREET -> copy(street = value)
        CardField.POSTAL_CODE -> copy(postalCode = value)
        CardField.CITY -> copy(city = value)
    }

    /** Wizytówka jako tekst w wątku — model wie, o kim mowa w kolejnych pytaniach. */
    fun asThreadText(): String {
        val parts = listOf(
            displayName,
            jobTitle,
            nip.takeIf { it.isNotBlank() }?.let { "NIP $it" } ?: "",
            phone.takeIf { it.isNotBlank() }?.let { "tel. $it" } ?: "",
            email,
            website,
            listOf(street, postalCode, city).filter { it.isNotBlank() }.joinToString(" "),
        ).filter { it.isNotBlank() }
        return "Zeskanowana wizytówka: ${parts.joinToString(", ")}. ${CardQuestions.KIND}"
    }
}

/** Pola karty w kolejności formularza „Popraw dane". */
enum class CardField(val label: String) {
    FIRST_NAME("Imię"),
    LAST_NAME("Nazwisko"),
    COMPANY("Firma"),
    JOB_TITLE("Stanowisko"),
    NIP("NIP"),
    PHONE("Telefon"),
    PHONE2("Telefon 2"),
    EMAIL("E-mail"),
    EMAIL2("E-mail 2"),
    WEBSITE("Strona www"),
    STREET("Ulica"),
    POSTAL_CODE("Kod pocztowy"),
    CITY("Miejscowość"),
}

data class CardDuplicate(
    val id: String,
    val name: String,
    val companyName: String?,
    val category: ClientCategory,
    val phone: String?,
    val email: String?,
)

/** Wynik skanu. `canSave` = wołający ma `deal.manage` (inaczej nie pytamy, gdzie zapisać). */
data class CardScanResult(
    val source: String,
    val card: ScannedCard,
    val duplicates: List<CardDuplicate>,
    val canSave: Boolean,
) {
    val sourceLabel: String
        get() = when (source) {
            "qr" -> "z kodu QR"
            "qr+photo" -> "z kodu QR i zdjęcia"
            else -> "ze zdjęcia"
        }
}

enum class ContactKind(val wire: String, val label: String, val hint: String) {
    INDYWIDUALNY("indywidualny", "Klient indywidualny", "osoba prywatna, dom"),
    B2B("b2b", "Klient B2B", "firma, faktura na NIP"),
    AFILIANT("afiliant", "Afiliant", "poleca nam klientów"),
    INNY("inny", "Inny", "np. dystrybutor, hurtownia"),
    ;

    val isClient: Boolean get() = this == INDYWIDUALNY || this == B2B
}

enum class ContactRole(val wire: String, val label: String) {
    DYSTRYBUTOR("dystrybutor", "Dystrybutor"),
    HURTOWNIA("hurtownia", "Hurtownia"),
    DOSTAWCA("dostawca", "Dostawca"),
    PODWYKONAWCA("podwykonawca", "Podwykonawca"),
}

object CardQuestions {
    const val KIND = "Do czego włożyć ten kontakt?"
    const val MODE = "Założyć od razu lead czy tylko kartę w kartotece?"
    const val ROLE = "Kim jest ten kontakt?"
}

/** Zakładka kartoteki, do której trafi kontakt — do etykiety propozycji. */
fun tabFor(kind: ContactKind, role: String): String = when (kind) {
    ContactKind.INDYWIDUALNY, ContactKind.B2B -> "Klienci"
    ContactKind.AFILIANT -> "Afilianci"
    ContactKind.INNY ->
        if (ContactRole.entries.any { it.wire == role.trim().lowercase() }) "Kontrahenci" else "Inne"
}

/** Co blokuje zapis — pokazywane zamiast wyłączonego przycisku bez słowa. */
fun missingForSave(card: ScannedCard, kind: ContactKind?): String? = when {
    card.firstName.isBlank() && card.lastName.isBlank() && card.companyName.isBlank() ->
        "Podaj imię i nazwisko albo nazwę firmy."
    card.phone.isBlank() && card.email.isBlank() -> "Podaj telefon albo e-mail."
    kind == ContactKind.B2B && card.companyName.isBlank() -> "Klient B2B wymaga nazwy firmy."
    card.postalCode.isNotBlank() && !Regex("^\\d{2}-\\d{3}$").matches(card.postalCode.trim()) ->
        "Kod pocztowy w formacie NN-NNN."
    else -> null
}

/** Dane z wizytówki, którymi asystent otwiera kreator LEAD. */
data class LeadCardPrefill(
    val fullName: String,
    val phone: String,
    val email: String,
    val postalCode: String,
    val city: String,
    val company: LeadCompany,
)

fun ScannedCard.toLeadPrefill(kind: ContactKind): LeadCardPrefill = LeadCardPrefill(
    fullName = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ").ifBlank { companyName },
    phone = phone,
    email = email,
    postalCode = postalCode,
    city = city,
    company = LeadCompany(
        segment = if (kind == ContactKind.B2B) "b2b" else "indywidualny",
        companyName = companyName,
        nip = nip,
        jobTitle = jobTitle,
        website = website,
    ),
)

// ── Odpowiedź wpisana albo powiedziana zamiast kliknięcia ────────────────────

private fun norm(text: String): String =
    Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace('ł', 'l')

private fun matchRoleWire(t: String): String? =
    ContactRole.entries.firstOrNull { t.contains(norm(it.wire).take(7)) }?.wire

/** `null` = tekst nie jest odpowiedzią na pytanie — idzie do zwykłego czatu. */
fun matchKind(text: String): Pair<ContactKind, String?>? {
    val t = norm(text)
    matchRoleWire(t)?.let { return ContactKind.INNY to it }
    return when {
        Regex("\\bb2b\\b|\\bb 2 b\\b|firm|biznes").containsMatchIn(t) -> ContactKind.B2B to null
        Regex("afilian|polecaj").containsMatchIn(t) -> ContactKind.AFILIANT to null
        Regex("indywidual|prywat|osob").containsMatchIn(t) -> ContactKind.INDYWIDUALNY to null
        Regex("\\binn[ya]\\b").containsMatchIn(t) -> ContactKind.INNY to null
        else -> null
    }
}

/** `true` = lead, `false` = sama karta, `null` = nie wiadomo. */
fun matchLeadMode(text: String): Boolean? {
    val t = norm(text)
    return when {
        Regex("lead|lejek|deal").containsMatchIn(t) -> true
        Regex("kart|kartotek|tylko kontakt").containsMatchIn(t) -> false
        else -> null
    }
}

/** Rola z listy albo pierwsze słowa wypowiedzi („architekt"). */
fun matchRole(text: String): String? {
    val t = norm(text).trim()
    if (t.isBlank()) return null
    return matchRoleWire(t) ?: text.trim().split(Regex("\\s+")).take(3).joinToString(" ").lowercase()
}
