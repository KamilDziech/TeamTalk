package com.ekotak.teamtalk.domain.usecase.device

import com.ekotak.teamtalk.domain.repository.DeviceRepository
import javax.inject.Inject

class UpdateDeviceLastActiveUseCase @Inject constructor(
    private val deviceRepository: DeviceRepository,
) {
    suspend operator fun invoke(pushToken: String) =
        deviceRepository.updateDeviceLastActive(pushToken)
}
