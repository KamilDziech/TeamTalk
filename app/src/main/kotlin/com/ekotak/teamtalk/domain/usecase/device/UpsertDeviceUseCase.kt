package com.ekotak.teamtalk.domain.usecase.device

import com.ekotak.teamtalk.domain.model.Device
import com.ekotak.teamtalk.domain.repository.DeviceRepository
import javax.inject.Inject

class UpsertDeviceUseCase @Inject constructor(
    private val deviceRepository: DeviceRepository,
) {
    suspend operator fun invoke(
        pushToken: String,
        userName: String,
        deviceInfo: String? = null,
    ): Device = deviceRepository.upsertDevice(pushToken, userName, deviceInfo)
}
