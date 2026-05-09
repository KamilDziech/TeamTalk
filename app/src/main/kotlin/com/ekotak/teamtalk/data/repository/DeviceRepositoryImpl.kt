package com.ekotak.teamtalk.data.repository

import com.ekotak.teamtalk.data.local.dao.DeviceDao
import com.ekotak.teamtalk.data.mapper.toDomain
import com.ekotak.teamtalk.data.mapper.toEntity
import com.ekotak.teamtalk.data.remote.api.TeamTalkApi
import com.ekotak.teamtalk.data.remote.dto.UpdateLastActiveRequest
import com.ekotak.teamtalk.data.remote.dto.UpsertDeviceRequest
import com.ekotak.teamtalk.domain.model.Device
import com.ekotak.teamtalk.domain.repository.DeviceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

class DeviceRepositoryImpl @Inject constructor(
    private val api: TeamTalkApi,
    private val deviceDao: DeviceDao,
) : DeviceRepository {

    override fun getDevices(pushTokenNeq: String?): Flow<List<Device>> = channelFlow {
        val localFlow = if (pushTokenNeq != null) {
            deviceDao.observeExcluding(pushTokenNeq)
        } else {
            deviceDao.observeAll()
        }

        launch { localFlow.map { it.map { e -> e.toDomain() } }.collect(::send) }

        try {
            val fresh = api.getDevices(pushTokenNeq = pushTokenNeq)
            deviceDao.upsertAll(fresh.map { it.toEntity() })
        } catch (_: Exception) {}
    }

    override suspend fun upsertDevice(
        pushToken: String,
        userName: String,
        deviceInfo: String?,
    ): Device {
        val dto = api.upsertDevice(UpsertDeviceRequest(pushToken, userName, deviceInfo))
        deviceDao.upsert(dto.toEntity())
        return dto.toDomain()
    }

    override suspend fun updateDeviceLastActive(pushToken: String) {
        api.updateDeviceLastActive(UpdateLastActiveRequest(pushToken))
    }
}
