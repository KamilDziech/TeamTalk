package com.ekotak.teamtalk.domain.usecase.profile

import com.ekotak.teamtalk.domain.model.Profile
import com.ekotak.teamtalk.domain.repository.ProfileRepository
import javax.inject.Inject

class UpsertProfileUseCase @Inject constructor(
    private val profileRepository: ProfileRepository,
) {
    suspend operator fun invoke(
        id: String? = null,
        displayName: String? = null,
    ): Profile = profileRepository.upsertProfile(id, displayName)
}
