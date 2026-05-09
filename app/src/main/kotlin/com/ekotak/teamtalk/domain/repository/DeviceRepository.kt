package com.ekotak.teamtalk.domain.repository

import com.ekotak.teamtalk.domain.model.Device
import kotlinx.coroutines.flow.Flow

interface DeviceRepository {
    /** Stream of registered devices, backed by local cache. */
    fun getDevices(pushTokenNeq: String? = null): Flow<List<Device>>

    /** Registers or updates a device entry by push token (server upsert). */
    suspend fun upsertDevice(
        pushToken: String,
        userName: String,
        deviceInfo: String? = null,
    ): Device

    /** Pings the server to update last_active_at for keep-alive tracking. */
    suspend fun updateDeviceLastActive(pushToken: String)
}
