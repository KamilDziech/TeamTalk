package com.ekotak.teamtalk.domain.usecase.client

import com.ekotak.teamtalk.domain.model.Client
import com.ekotak.teamtalk.domain.repository.ClientRepository
import javax.inject.Inject

class CreateClientUseCase @Inject constructor(
    private val clientRepository: ClientRepository,
) {
    suspend operator fun invoke(
        phone: String,
        name: String? = null,
        address: String? = null,
        notes: String? = null,
    ): Client {
        require(phone.isNotBlank()) { "Numer telefonu nie może być pusty" }
        return clientRepository.createClient(phone, name, address, notes)
    }
}
