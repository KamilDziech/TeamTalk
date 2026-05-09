package com.ekotak.teamtalk.domain.usecase.profile

import com.ekotak.teamtalk.domain.model.Profile
import com.ekotak.teamtalk.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetProfilesUseCase @Inject constructor(
    private val profileRepository: ProfileRepository,
) {
    operator fun invoke(
        idEq: String? = null,
        isAdminEq: Boolean? = null,
    ): Flow<List<Profile>> = profileRepository.getProfiles(idEq, isAdminEq)
}
