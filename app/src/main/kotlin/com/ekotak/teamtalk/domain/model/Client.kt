package com.ekotak.teamtalk.domain.model

/** Kategoria wpisu w kartotece — zakładki przełącznika (jak w board360). */
enum class ClientCategory(val wire: String, val tabLabel: String, val oneLabel: String) {
    KLIENT("klient", "Klienci", "klient"),
    KONTRAHENT("kontrahent", "Kontrahenci", "kontrahent"),
    AFILIANT("afiliant", "Afilianci", "afiliant"),

    /** Kubełek na resztę kartoteki — jak w panelu nie ma nazwy jednostkowej. */
    INNE("inne", "Inne", "wpis");

    /** Wartość wiersza „Kategoria" na karcie: „wpis" mówiłby tam za mało. */
    val detailLabel: String get() = if (this == INNE) "inne" else oneLabel

    companion object {
        fun fromWire(value: String?): ClientCategory =
            entries.firstOrNull { it.wire == value } ?: KLIENT
    }
}

/** Pochodzenie klienta (rozróżnienie tylko dla kategorii „klient"). */
enum class ClientType(val wire: String, val label: String) {
    WLASNY("wlasny", "własny"),
    PARTNERA("partnera", "partnera");

    companion object {
        fun fromWire(value: String?): ClientType =
            entries.firstOrNull { it.wire == value } ?: WLASNY
    }
}

/** Odległość po drodze [km] i czas dojazdu [min] z jednej bazy ekotak. */
data class TravelLeg(val km: Double, val min: Double)

/** Dojazd z obu baz; `null` per baza = trasy nie udało się wyznaczyć. */
data class ClientTravel(val kobiernice: TravelLeg?, val gliwice: TravelLeg?)

/**
 * Klient (kontrakt board360). Kartoteka mobilna odwzorowuje kartę „Klienci"
 * z panelu: podgląd, dodawanie, edycja danych, scalanie duplikatów i RODO.
 * Uprawnienia egzekwuje API — UI tylko chowa akcje, na które ich brak.
 */
data class Client(
    val id: String,
    val firstName: String,
    val lastName: String,
    val email: String? = null,
    val email2: String? = null,
    val phone: String? = null,
    val phone2: String? = null,
    val address: String? = null,
    val postalCode: String? = null,
    val city: String? = null,
    val street: String? = null,
    val geoLat: Double? = null,
    val geoLng: Double? = null,
    /** Miejscowość i gmina rozpoznane przy geokodowaniu. */
    val geoCity: String? = null,
    val geoMunicipality: String? = null,
    val travel: ClientTravel? = null,
    val type: ClientType = ClientType.WLASNY,
    val category: ClientCategory = ClientCategory.KLIENT,
    val createdAt: String? = null,
    val updatedAt: String? = null,
) {
    val displayName: String
        get() = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ").ifBlank { primaryPhone ?: "" }

    val primaryPhone: String?
        get() = phone?.takeIf { it.isNotBlank() } ?: phone2?.takeIf { it.isNotBlank() }

    /** Adres zwalidowany (geokodowany) — warunek przejścia deala do etapów ≥ audyt. */
    val hasGeo: Boolean get() = geoLat != null && geoLng != null

    /** Miejscowość do drugiej linijki karty na liście. */
    val place: String?
        get() = (geoCity ?: city)?.takeIf { it.isNotBlank() }

    /** Etykieta pod nazwą w karcie: dla klienta rozróżnia własnego i partnera. */
    val categoryHeadLabel: String
        get() = when (category) {
            ClientCategory.KLIENT ->
                if (type == ClientType.PARTNERA) "Klient partnera" else "Klient własny"
            ClientCategory.KONTRAHENT -> "Kontrahent"
            ClientCategory.AFILIANT -> "Afiliant"
            ClientCategory.INNE -> "Inne"
        }

    /** Inicjały do awatara na karcie (jedna–dwie litery). */
    val initials: String
        get() = listOf(firstName, lastName)
            .mapNotNull { it.trim().firstOrNull()?.uppercaseChar() }
            .joinToString("")
            .ifBlank { "?" }
}

/**
 * Edytowalne pola kartoteki — te, które przyjmuje `PATCH /api/clients/:id`.
 * Adres jest wolnym polem: jego zmiana czyści serwerowo części i geokodowanie,
 * a potem uruchamia ponowną walidację adresu i przeliczenie dojazdu.
 */
data class ClientDraft(
    val firstName: String,
    val lastName: String,
    val email: String?,
    val email2: String?,
    val phone: String?,
    val phone2: String?,
    val address: String?,
)

fun Client.toDraft(): ClientDraft = ClientDraft(
    firstName = firstName,
    lastName = lastName,
    email = email,
    email2 = email2,
    phone = phone,
    phone2 = phone2,
    address = address,
)

/** Dane nowego wpisu kartoteki (`POST /api/clients`). */
data class NewClient(
    val firstName: String,
    val lastName: String,
    val email: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val type: ClientType? = null,
    val category: ClientCategory = ClientCategory.KLIENT,
)
