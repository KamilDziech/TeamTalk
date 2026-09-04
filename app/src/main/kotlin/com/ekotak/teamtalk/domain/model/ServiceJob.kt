package com.ekotak.teamtalk.domain.model

/** Priorytet zlecenia serwisowego — gwiazdka na wierszu listy (jak w Zadaniach). */
enum class ServiceJobPriority(val wire: String, val label: String) {
    LOW("low", "Niski"),
    NORMAL("normal", "Normalny"),
    HIGH("high", "Wysoki");

    companion object {
        fun fromWire(value: String?): ServiceJobPriority =
            entries.firstOrNull { it.wire == value } ?: NORMAL
    }
}

/**
 * Dwie dziedziny modułu Serwis — odwzorowanie `ServiceDomain` z panelu
 * (`web/src/app/app/service/ServiceView.tsx`):
 *  - [PRZEGLAD] — planowa obsługa: przeglądy, konserwacje i karty gwarancyjne,
 *  - [SERWIS] — interwencja: awarie z oknem SLA.
 *
 * Podział steruje filtrem listy i kalendarza, rodzajem punktów na mapie oraz
 * zestawem typów dostępnych w formularzu nowego zlecenia. Na telefonie dziedzinę
 * ustala kafelek pulpitu i nie da się jej przełączyć w środku ekranu, więc każda
 * dziedzina niesie własny [title] na pasek górny (ustalenie 2026-09-03).
 */
enum class ServiceDomain(
    val label: String,
    val title: String,
    val types: List<ServiceJobType>,
    val addLabel: String,
    val emptyLabel: String,
) {
    PRZEGLAD(
        label = "Przegląd",
        title = "Przeglądy",
        types = listOf(ServiceJobType.PRZEGLAD, ServiceJobType.KONSERWACJA),
        addLabel = "Nowy przegląd",
        emptyLabel = "Brak przeglądów spełniających filtry.",
    ),
    SERWIS(
        label = "Serwis",
        title = "Serwis",
        types = listOf(ServiceJobType.AWARIA),
        addLabel = "Nowe zgłoszenie",
        emptyLabel = "Brak zgłoszeń serwisowych (awarii).",
    ),
}

/** Widok modułu: te same trzy zakładki co w panelu. */
enum class ServiceView(val label: String) {
    LISTA("Lista"),
    KALENDARZ("Kalendarz"),
    MAPA("Mapa"),
}

/**
 * Zlecenie serwisowe (board360 FR-17, `GET /api/service-jobs`). Zlecenie wolno
 * zapisać niedouzupełnione — sam opis usterki, bez klienta, serwisanta i terminu
 * — takie wiersze świecą na liście czerwienią (patrz `ServiceRules`).
 *
 * [slaDueAt] i [slaBreached] liczy serwer; telefon dolicza tylko „ile zostało”.
 */
data class ServiceJob(
    val id: String,
    /** `null` = zlecenie zapisane „na szybko”, bez wskazanego klienta. */
    val clientId: String?,
    val dealId: String?,
    val type: ServiceJobType,
    val status: ServiceJobStatus,
    val priority: ServiceJobPriority,
    val technicianId: String?,
    /** Termin wizyty — z dokładnością do dnia (godzinę ustala serwisant telefonicznie). */
    val scheduledAt: String?,
    /** Opis usterki; pierwsza linia jest nazwą zlecenia na liście. */
    val note: String?,
    /** Okno SLA w godzinach (24 / 168 / 720); `null` = domyślne wg typu. */
    val slaHours: Int?,
    val slaDueAt: String?,
    val slaBreached: Boolean,
    /** Czy zmiana czeka w kolejce offline — znacznik „czeka na wysyłkę” w wierszu. */
    val pendingSync: Boolean = false,
    /** Czy wiersz powstał bez zasięgu i nie ma jeszcze id z serwera. */
    val localOnly: Boolean = false,
)

/** Serwisant (`GET /api/technicians`) — przypisanie zlecenia i filtr osoby. */
data class Technician(
    val id: String,
    val email: String,
    val firstName: String?,
    val lastName: String?,
) {
    /** Etykieta jak w panelu: „Imię Nazwisko”, a gdy brak — e-mail. */
    val displayName: String
        get() = listOfNotNull(firstName, lastName)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { email }

    /** Inicjały do awatara w kółku wiersza. */
    val initials: String
        get() = displayName.split(' ', '.', '-', '@')
            .filter { it.isNotBlank() }
            .take(2)
            .map { it.first().uppercaseChar() }
            .joinToString("")
            .ifBlank { "?" }
}

/** Klient w kształcie potrzebnym modułowi Serwis: etykieta wiersza i miasto. */
data class ServiceClient(
    val id: String,
    val label: String,
    val city: String?,
    val phone: String?,
    val address: String?,
)
