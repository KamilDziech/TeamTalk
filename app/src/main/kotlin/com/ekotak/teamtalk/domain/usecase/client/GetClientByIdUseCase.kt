package com.ekotak.teamtalk.domain.usecase.client

import com.ekotak.teamtalk.domain.model.Client
import com.ekotak.teamtalk.domain.repository.ClientRepository
import javax.inject.Inject

class GetClientByIdUseCase @Inject constructor(
    private val clientRepository: ClientRepository,
) {
    suspend operator fun invoke(id: String): Client = clientRepository.getClientById(id)
}
