package com.ekotak.teamtalk.data.repository

import com.ekotak.teamtalk.data.local.dao.ClientDao
import com.ekotak.teamtalk.data.mapper.toDomain
import com.ekotak.teamtalk.data.mapper.toEntity
import com.ekotak.teamtalk.data.remote.api.TeamTalkApi
import com.ekotak.teamtalk.domain.model.Client
import com.ekotak.teamtalk.domain.repository.ClientRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

class ClientRepositoryImpl @Inject constructor(
    private val api: TeamTalkApi,
    private val clientDao: ClientDao,
) : ClientRepository {

    override fun getClients(query: String?): Flow<List<Client>> = channelFlow {
        val localFlow = if (query.isNullOrBlank()) {
            clientDao.observeAll()
        } else {
            clientDao.observeByQuery(query)
        }

        launch { localFlow.map { it.map { e -> e.toDomain() } }.collect(::send) }

        try {
            val fresh = api.getClients(q = query)
            clientDao.upsertAll(fresh.map { it.toEntity() })
        } catch (_: Exception) {}
    }

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

    private fun matches(client: Client, normalizedPhone: String): Boolean {
        if (normalizedPhone.isBlank()) return false
        return normalize(client.phone).endsWith(normalizedPhone) ||
            normalize(client.phone2).endsWith(normalizedPhone) ||
            normalizedPhone.endsWith(normalize(client.phone))
    }

    private fun normalize(raw: String?): String {
        val digits = (raw ?: "").replace(Regex("[^\\d]"), "")
        return if (digits.length >= 9) digits.takeLast(9) else digits
    }
}
