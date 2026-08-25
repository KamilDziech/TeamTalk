package com.ekotak.teamtalk.domain.model

/** Urządzenie mobilne serwisanta (kontrakt board360). */
data class Device(
    val id: String,
    val deviceId: String,
    val model: String? = null,
    val osVersion: String? = null,
    val sim1Label: String? = null,
    val sim2Label: String? = null,
    val pushToken: String? = null,
    val lastSeenAt: String? = null,
    val createdAt: String? = null,
)
