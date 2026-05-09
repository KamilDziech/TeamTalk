package com.ekotak.teamtalk.domain.usecase.client

import com.ekotak.teamtalk.domain.model.Client
import com.ekotak.teamtalk.domain.repository.ClientRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetClientsUseCase @Inject constructor(
    private val clientRepository: ClientRepository,
) {
    operator fun invoke(phoneEq: String? = null): Flow<List<Client>> =
        clientRepository.getClients(phoneEq)
}
