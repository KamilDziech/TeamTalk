package com.ekotak.teamtalk.domain.model

/**
 * Rodzaj zlecenia serwisowego — odwzorowanie `SERVICE_TYPE_LABEL` z board360.
 * Rozstrzyga, w którym widoku mapy ląduje punkt: awaria → „Serwisy",
 * przegląd i konserwacja → „Przeglądy" (dokładnie jak w panelu).
 */
enum class ServiceJobType(val wire: String, val label: String) {
    KONSERWACJA("konserwacja", "konserwacja"),
    PRZEGLAD("przeglad", "przegląd"),
    AWARIA("awaria", "awaria");

    companion object {
        fun fromWire(value: String?): ServiceJobType =
            entries.firstOrNull { it.wire == value } ?: AWARIA
    }
}

/** Status zlecenia serwisowego (`SERVICE_STATUS_LABEL`). */
enum class ServiceJobStatus(val wire: String, val label: String) {
    NEW("new", "nowe"),
    IN_PROGRESS("in_progress", "w toku"),
    DONE("done", "wykonane");

    companion object {
        fun fromWire(value: String?): ServiceJobStatus =
            entries.firstOrNull { it.wire == value } ?: NEW
    }
}

/**
 * Status karty przeglądów gwarancyjnych (`WARRANTY_STATUS_LABEL`). Kolejność
 * deklaracji = kolejność chipów w panelu, więc legenda mapy układa się tak samo.
 */
enum class WarrantyCardStatus(val wire: String, val label: String) {
    WYKONANE("wykonane", "Wykonane"),
    OCZEKUJACE("oczekujace", "Oczekujące"),
    UMOWIONE("umowione", "Umówione"),
    REZYGNACJA("rezygnacja", "Rezygnacja"),
    CZEKAMY_NA_KONTAKT("czekamy_na_kontakt", "Czekamy na kontakt"),
    BRAK_KONTAKTU("brak_kontaktu", "Brak kontaktu"),
    INNE("inne", "Inne");

    companion object {
        fun fromWire(value: String?): WarrantyCardStatus =
            entries.firstOrNull { it.wire == value } ?: INNE
    }
}
