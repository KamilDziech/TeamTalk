package com.ekotak.teamtalk.domain.usecase.profile

import com.ekotak.teamtalk.domain.model.Profile
import com.ekotak.teamtalk.domain.repository.ProfileRepository
import javax.inject.Inject

class UpdateProfileUseCase @Inject constructor(
    private val profileRepository: ProfileRepository,
) {
    suspend operator fun invoke(
        id: String,
        displayName: String? = null,
        isAdmin: Boolean? = null,
    ): Profile = profileRepository.updateProfile(id, displayName, isAdmin)
}
