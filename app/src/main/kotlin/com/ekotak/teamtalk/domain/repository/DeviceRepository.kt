package com.ekotak.teamtalk.domain.repository

import com.ekotak.teamtalk.domain.model.Device

interface DeviceRepository {
    /** Rejestruje/aktualizuje urządzenie serwisanta (upsert board360). */
    suspend fun upsertDevice(
        deviceId: String,
        model: String? = null,
        osVersion: String? = null,
        sim1Label: String? = null,
        sim2Label: String? = null,
        pushToken: String? = null,
    ): Device
}
