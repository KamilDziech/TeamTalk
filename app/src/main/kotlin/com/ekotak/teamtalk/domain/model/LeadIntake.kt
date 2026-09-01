package com.ekotak.teamtalk.domain.model

/**
 * Kanał, którym przyszedł lead — `LEAD_CHANNELS` z board360
 * (api/src/modules/intake/domain/channel.ts). Kanał decyduje o tym, czym jest
 * notatka: przy telefonie to zapis operatora z rozmowy, przy targach i stronie
 * uwagi wpisane przez samego klienta.
 */
enum class LeadChannel(val wire: String, val label: String) {
    TARGI("targi", "Targi"),
    WEB("web", "Strona www"),
    TEL("tel", "Telefon");

    companion object {
        fun fromWire(value: String?): LeadChannel? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * Dane budynku z kreatora /targi. To NIE są zweryfikowane dane budynku deala
 * (`DealBuildingData`) — tu klient wybierał opisowe warianty („80–100 m²",
 * „2 osoby"), więc pola są tekstem, a nie liczbami.
 */
data class LeadBuilding(
    val shape: String? = null,
    val construction: String? = null,
    val area: String? = null,
    val people: String? = null,
    val floors: Int? = null,
    val stage: String? = null,
    val windows: String? = null,
    val heatedBasement: Boolean = false,
    val heatedGarage: Boolean = false,
) {
    val isEmpty: Boolean
        get() = shape.isNullOrBlank() && construction.isNullOrBlank() && area.isNullOrBlank() &&
            people.isNullOrBlank() && floors == null && stage.isNullOrBlank() &&
            windows.isNullOrBlank() && !heatedBasement && !heatedGarage
}

/**
 * Zgłoszenie z leadowni przypięte do deala (zakładka „LEAD" karty). Brak
 * rekordu = deal nie pochodzi z leadowni; wtedy repozytorium zwraca `null`,
 * a karta pokazuje sam etap kwalifikacji i instalacje.
 */
data class LeadIntake(
    val channel: LeadChannel?,
    /** Surowe `source` deala („targi/Bielsko-Biała 2025"). */
    val source: String,
    val sourceLabel: String? = null,
    val fullName: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val city: String? = null,
    val interest: String? = null,
    val budget: String? = null,
    /** Notatka z rozmowy (kanał `tel`) albo uwagi klienta (targi/web). */
    val note: String? = null,
    val consent: Boolean = false,
    val submittedBy: String? = null,
    val createdAt: String? = null,
    val building: LeadBuilding? = null,
) {
    /** Etykieta pola notatki — zależy od kanału, tak jak w panelu. */
    val noteLabel: String
        get() = if (channel == LeadChannel.TEL) "Notatka z rozmowy" else "Uwagi klienta"

    val channelLabel: String get() = channel?.label ?: source.substringBefore('/')
}
