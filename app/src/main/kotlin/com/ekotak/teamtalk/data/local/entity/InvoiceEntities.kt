package com.ekotak.teamtalk.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cache zakładki „Faktura" karty deala.
 *
 * Obie tabele trzymają CAŁĄ odpowiedź serwera jako JSON, tak samo jak cache
 * umów (`deal_contracts`). Powód jest ten sam: to migawka do pokazania, a nie
 * dane, po których cokolwiek filtrujemy w SQL — rozbicie faktury na kolumny
 * kupowałoby zapytania, których nikt nie zada, i gubiło pola, których jeszcze
 * nie znamy.
 *
 * Kolejki tu nie ma: telefon w tej zakładce niczego nie wystawia, a zapis
 * danych do faktury jedzie wspólną kolejką karty deala (`deal_mutations`).
 */
@Entity(tableName = "deal_invoices")
data class DealInvoicesEntity(
    @PrimaryKey val dealId: String,
    /** `DealInvoicesDto` zserializowane wspólnym `Json` modułu. */
    val payload: String,
    val syncedAt: Long,
)

@Entity(tableName = "deal_montaze")
data class DealMontazeEntity(
    @PrimaryKey val dealId: String,
    /** Lista `InstallationDto` zserializowana wspólnym `Json` modułu. */
    val payload: String,
    val syncedAt: Long,
)
