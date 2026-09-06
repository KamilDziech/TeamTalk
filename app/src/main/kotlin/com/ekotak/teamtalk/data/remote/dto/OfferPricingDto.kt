package com.ekotak.teamtalk.data.remote.dto

import kotlinx.serialization.Serializable

/**
 * Dane cennikowe zakładki „Oferta" — ustawienia firmowe i zestawy Warunków
 * finansowych. Rachunek robi telefon (`domain/ufh`), tak samo jak przeglądarka
 * w panelu; stąd idą wyłącznie surowe wartości.
 */

/** Ustawienie firmowe (`GET /api/organization/settings/:key`). */
@Serializable
data class OrgSettingDto(
    val key: String = "",
    /** Wartość jako tekst (JSON dla materiałów domyślnych i narzutu); `null` = brak wpisu. */
    val value: String? = null,
)

/** Pozycja zestawu punktowego / kosztowego. */
@Serializable
data class FinancialTermItemDto(
    val id: String = "",
    val name: String = "",
    /** `ryczalt` | `szt` | `kpl` | `m2` | `mb` | `godz`. */
    val unit: String = "ryczalt",
    val points: Double = 0.0,
)

/**
 * Zestaw Warunków finansowych wpięty pod węzeł technologii. Rodzaj („Montaż" /
 * „Biuro" / „Koszty z ręki") poznaje się po NAZWIE — tak samo jak w panelu.
 */
@Serializable
data class FinancialSchemeDto(
    val id: String = "",
    val categoryId: String = "",
    val name: String = "",
    val items: List<FinancialTermItemDto> = emptyList(),
)
