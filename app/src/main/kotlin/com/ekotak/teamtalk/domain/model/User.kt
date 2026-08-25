package com.ekotak.teamtalk.domain.model

data class User(
    val id: String,
    val email: String,
    val displayName: String,
    /** Tenant board360 (EKOTAK = jedna organizacja). */
    val organizationId: String = "",
    /** Rola board360: admin/zarzad/koordynator/serwisant/biuro/montaz/stazysta. */
    val role: String = "",
    val permissions: List<String> = emptyList(),
    val isAdmin: Boolean = false,
)
