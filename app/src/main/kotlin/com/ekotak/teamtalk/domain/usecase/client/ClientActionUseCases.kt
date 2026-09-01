package com.ekotak.teamtalk.domain.usecase.client

import com.ekotak.teamtalk.domain.model.AssistantMessage
import com.ekotak.teamtalk.domain.model.AssistantReply
import com.ekotak.teamtalk.domain.model.Client
import com.ekotak.teamtalk.domain.model.ClientDraft
import com.ekotak.teamtalk.domain.model.NewClient
import com.ekotak.teamtalk.domain.repository.ClientRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Podgląd pojedynczego klienta z cache (żywy strumień). */
class ObserveClientUseCase @Inject constructor(
    private val clientRepository: ClientRepository,
) {
    operator fun invoke(id: String): Flow<Client?> = clientRepository.observeClient(id)
}

/** Wymuszone pobranie kartoteki z API (pull-to-refresh). */
class RefreshClientsUseCase @Inject constructor(
    private val clientRepository: ClientRepository,
) {
    suspend operator fun invoke() = clientRepository.refresh()
}

/** Nowy wpis kartoteki. Wymaga `deal.manage` (egzekwuje API). */
class CreateClientUseCase @Inject constructor(
    private val clientRepository: ClientRepository,
) {
    suspend operator fun invoke(input: NewClient): Client = clientRepository.createClient(input)
}

/** Edycja danych klienta. Zmiana adresu uruchamia serwerowo re-geokodowanie. */
class UpdateClientUseCase @Inject constructor(
    private val clientRepository: ClientRepository,
) {
    suspend operator fun invoke(original: Client, draft: ClientDraft): Client =
        clientRepository.updateClient(original, draft)
}

/** Scalenie duplikatów w rekord docelowy. Operacji nie można cofnąć. */
class MergeClientsUseCase @Inject constructor(
    private val clientRepository: ClientRepository,
) {
    suspend operator fun invoke(targetId: String, sourceIds: List<String>): Client =
        clientRepository.mergeClients(targetId, sourceIds)
}

/** Anonimizacja danych osobowych (RODO). Wymaga `settings.company`. */
class EraseClientUseCase @Inject constructor(
    private val clientRepository: ClientRepository,
) {
    suspend operator fun invoke(id: String): Client = clientRepository.eraseClient(id)
}

/** Pytanie do asystenta karty klienta (multi-turn — historia w argumencie). */
class AskClientAssistantUseCase @Inject constructor(
    private val clientRepository: ClientRepository,
) {
    suspend operator fun invoke(
        clientId: String,
        messages: List<AssistantMessage>,
    ): AssistantReply = clientRepository.askAssistant(clientId, messages)
}
