package com.ekotak.teamtalk.domain.usecase.client

import com.ekotak.teamtalk.domain.model.Client
import com.ekotak.teamtalk.domain.repository.ClientRepository
import javax.inject.Inject

class UpdateClientUseCase @Inject constructor(
    private val clientRepository: ClientRepository,
) {
    suspend operator fun invoke(
        id: String,
        phone: String? = null,
        name: String? = null,
        address: String? = null,
        notes: String? = null,
    ): Client = clientRepository.updateClient(id, phone, name, address, notes)
}
