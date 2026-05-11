package com.ekotak.teamtalk.domain.usecase.clientgroup

import com.ekotak.teamtalk.domain.model.ClientGroup
import com.ekotak.teamtalk.domain.repository.ClientGroupRepository
import javax.inject.Inject

class EnsureDefaultGroupUseCase @Inject constructor(
    private val repository: ClientGroupRepository,
) {
    suspend operator fun invoke(): ClientGroup = repository.ensureDefaultGroup()
}
