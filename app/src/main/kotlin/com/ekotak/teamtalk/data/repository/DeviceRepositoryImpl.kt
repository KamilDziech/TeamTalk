package com.ekotak.teamtalk.data.repository

import com.ekotak.teamtalk.data.local.dao.DeviceDao
import com.ekotak.teamtalk.data.mapper.toDomain
import com.ekotak.teamtalk.data.mapper.toEntity
import com.ekotak.teamtalk.data.remote.api.TeamTalkApi
import com.ekotak.teamtalk.data.remote.dto.UpsertDeviceRequest
import com.ekotak.teamtalk.domain.model.Device
import com.ekotak.teamtalk.domain.repository.DeviceRepository
import javax.inject.Inject

class DeviceRepositoryImpl @Inject constructor(
    private val api: TeamTalkApi,
    private val deviceDao: DeviceDao,
) : DeviceRepository {

    override suspend fun upsertDevice(
        deviceId: String,
        model: String?,
        osVersion: String?,
        sim1Label: String?,
        sim2Label: String?,
        pushToken: String?,
    ): Device {
        val dto = api.upsertDevice(
            UpsertDeviceRequest(deviceId, model, osVersion, sim1Label, sim2Label, pushToken)
        )
        deviceDao.upsert(dto.toEntity())
        return dto.toDomain()
    }
}
