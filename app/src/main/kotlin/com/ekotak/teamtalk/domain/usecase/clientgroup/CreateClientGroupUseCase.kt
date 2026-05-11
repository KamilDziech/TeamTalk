package com.ekotak.teamtalk.domain.usecase.clientgroup

import com.ekotak.teamtalk.domain.model.ClientGroup
import com.ekotak.teamtalk.domain.repository.ClientGroupRepository
import javax.inject.Inject

class CreateClientGroupUseCase @Inject constructor(
    private val repository: ClientGroupRepository,
) {
    suspend operator fun invoke(name: String): ClientGroup = repository.createGroup(name)
}
