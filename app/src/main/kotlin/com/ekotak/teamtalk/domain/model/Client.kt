package com.ekotak.teamtalk.domain.model

/**
 * Klient (kontrakt board360, read-only dla serwisanta). Serwisant klientów tylko
 * przegląda — tworzenie/edycja to praca biura w panelu board360.
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
    val type: String? = null,
    val category: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
) {
    val displayName: String
        get() = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ").ifBlank { primaryPhone ?: "" }

    val primaryPhone: String?
        get() = phone?.takeIf { it.isNotBlank() } ?: phone2?.takeIf { it.isNotBlank() }
}
