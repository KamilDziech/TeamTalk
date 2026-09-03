package com.ekotak.teamtalk.data.repository

import com.ekotak.teamtalk.data.local.dao.ClientDao
import com.ekotak.teamtalk.data.mapper.toDomain
import com.ekotak.teamtalk.data.mapper.toEntity
import com.ekotak.teamtalk.data.remote.api.TeamTalkApi
import com.ekotak.teamtalk.data.remote.dto.AssistantMessageDto
import com.ekotak.teamtalk.data.remote.dto.ClientAssistantRequest
import com.ekotak.teamtalk.data.remote.dto.CreateClientRequest
import com.ekotak.teamtalk.data.remote.dto.MergeClientsRequest
import com.ekotak.teamtalk.data.remote.dto.buildClientPatch
import com.ekotak.teamtalk.domain.model.AssistantMessage
import com.ekotak.teamtalk.domain.model.AssistantReply
import com.ekotak.teamtalk.domain.model.Client
import com.ekotak.teamtalk.domain.model.ClientDraft
import com.ekotak.teamtalk.domain.model.NewClient
import com.ekotak.teamtalk.domain.repository.ClientRepository
import com.ekotak.teamtalk.domain.search.matchesQuery
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

class ClientRepositoryImpl @Inject constructor(
    private val api: TeamTalkApi,
    private val clientDao: ClientDao,
) : ClientRepository {

    /**
     * Szukanie leci po stronie aplikacji, nie zapytaniem SQL. `LIKE` porównywał
     * całe hasło z każdą kolumną osobno, więc „Jan Kowalski" nie trafiał w
     * nikogo (imię jest w jednej kolumnie, nazwisko w drugiej), a ogonki
     * rozjeżdżały wielkość liter. [matchesQuery] dzieli hasło na słowa i zdejmuje
     * ogonki; kartoteka mieści się w pamięci — cała ładuje się i tak na mapę.
     */
    override fun getClients(query: String?): Flow<List<Client>> = channelFlow {
        val localFlow = clientDao.observeAll().map { rows ->
            val clients = rows.map { it.toDomain() }
            if (query.isNullOrBlank()) clients else clients.filter { it.matchesQuery(query) }
        }

        launch { localFlow.collect(::send) }

        try {
            val fresh = api.getClients(q = query)
            clientDao.upsertAll(fresh.map { it.toEntity() })
        } catch (_: Exception) {}
    }

    override fun observeClient(id: String): Flow<Client?> =
        clientDao.observeById(id).map { it?.toDomain() }

    override suspend fun getClientById(id: String): Client {
        return try {
            val dto = api.getClientById(id)
            clientDao.upsert(dto.toEntity())
            dto.toDomain()
        } catch (e: Exception) {
            clientDao.getById(id)?.toDomain() ?: throw e
        }
    }

    override suspend fun getClientByPhone(phone: String): Client? {
        val normalized = normalize(phone)
        return try {
            val results = api.getClients(q = phone)
            clientDao.upsertAll(results.map { it.toEntity() })
            results.map { it.toDomain() }.firstOrNull { matches(it, normalized) }
                ?: clientDao.getByPhone(phone)?.toDomain()
        } catch (_: Exception) {
            clientDao.getByPhone(phone)?.toDomain()
        }
    }

    /** Błąd świadomie leci dalej — pull-to-refresh musi umieć pokazać awarię. */
    override suspend fun refresh() {
        val fresh = api.getClients()
        clientDao.upsertAll(fresh.map { it.toEntity() })
    }

    override suspend fun createClient(input: NewClient): Client {
        val dto = api.createClient(
            CreateClientRequest(
                firstName = input.firstName,
                lastName = input.lastName,
                email = input.email,
                phone = input.phone,
                address = input.address,
                type = input.type?.wire,
                category = input.category.wire,
            ),
        )
        clientDao.upsert(dto.toEntity())
        return dto.toDomain()
    }

    override suspend fun updateClient(original: Client, draft: ClientDraft): Client {
        val patch = buildClientPatch(original, draft)
        // Pusty patch API odrzuciłoby, a i tak nie ma czego zapisywać.
        if (patch.isEmpty()) return original
        val dto = api.updateClient(original.id, patch)
        clientDao.upsert(dto.toEntity())
        return dto.toDomain()
    }

    override suspend fun mergeClients(targetId: String, sourceIds: List<String>): Client {
        val dto = api.mergeClients(targetId, MergeClientsRequest(sourceIds))
        // Rekordy źródłowe zniknęły po stronie serwera — usuwamy je też z cache,
        // inaczej scalone duplikaty wracałyby na listę do następnego odświeżenia.
        clientDao.deleteByIds(sourceIds)
        clientDao.upsert(dto.toEntity())
        return dto.toDomain()
    }

    override suspend fun eraseClient(id: String): Client {
        val dto = api.eraseClient(id)
        clientDao.upsert(dto.toEntity())
        return dto.toDomain()
    }

    override suspend fun askAssistant(
        clientId: String,
        messages: List<AssistantMessage>,
    ): AssistantReply {
        val reply = api.askClientAssistant(
            id = clientId,
            request = ClientAssistantRequest(
                messages = messages.map { AssistantMessageDto(role = it.role, content = it.content) },
            ),
        )
        return AssistantReply(
            text = reply.text,
            configured = reply.configured,
            commsCount = reply.commsCount,
            dealCount = reply.dealCount,
        )
    }

    private fun matches(client: Client, normalizedPhone: String): Boolean {
        if (normalizedPhone.isBlank()) return false
        return normalize(client.phone).endsWith(normalizedPhone) ||
            normalize(client.phone2).endsWith(normalizedPhone) ||
            normalizedPhone.endsWith(normalize(client.phone))
    }

    private fun normalize(raw: String?): String {
        val digits = (raw ?: "").filter { it.isDigit() }
        return if (digits.length >= 9) digits.takeLast(9) else digits
    }
}
