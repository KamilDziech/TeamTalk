package com.ekotak.teamtalk.domain.usecase.client

import com.ekotak.teamtalk.domain.repository.ClientRepository
import javax.inject.Inject

class DeleteClientUseCase @Inject constructor(
    private val clientRepository: ClientRepository,
) {
    suspend operator fun invoke(id: String) = clientRepository.deleteClient(id)
}
