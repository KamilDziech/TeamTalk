package com.ekotak.teamtalk.domain.usecase.clientgroup

import com.ekotak.teamtalk.domain.model.ClientGroup
import com.ekotak.teamtalk.domain.repository.ClientGroupRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetClientGroupsUseCase @Inject constructor(
    private val repository: ClientGroupRepository,
) {
    operator fun invoke(): Flow<List<ClientGroup>> = repository.getGroups()
}
