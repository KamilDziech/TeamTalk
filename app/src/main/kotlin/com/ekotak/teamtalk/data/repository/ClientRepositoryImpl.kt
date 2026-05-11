package com.ekotak.teamtalk.data.repository

import com.ekotak.teamtalk.data.local.dao.ClientDao
import com.ekotak.teamtalk.data.mapper.toDomain
import com.ekotak.teamtalk.data.mapper.toEntity
import com.ekotak.teamtalk.data.remote.api.TeamTalkApi
import com.ekotak.teamtalk.data.remote.dto.CreateClientRequest
import com.ekotak.teamtalk.data.remote.dto.UpdateClientRequest
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

    override fun getClients(phoneEq: String?, groupId: String?): Flow<List<Client>> = channelFlow {
        val localFlow = when {
            phoneEq != null -> clientDao.observeExactPhone(phoneEq)
            groupId != null -> clientDao.observeByGroupId(groupId)
            else            -> clientDao.observeAll()
        }

        launch { localFlow.map { it.map { e -> e.toDomain() } }.collect(::send) }

        try {
            val fresh = api.getClients(phoneEq = phoneEq, groupIdEq = groupId)
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
        return try {
            val results = api.getClients(phoneEq = phone)
            clientDao.upsertAll(results.map { it.toEntity() })
            results.firstOrNull()?.toDomain()
        } catch (_: Exception) {
            clientDao.getByPhone(phone)?.toDomain()
        }
    }

    override suspend fun createClient(
        phone: String,
        name: String?,
        address: String?,
        notes: String?,
        groupId: String?,
    ): Client {
        val dto = api.createClient(CreateClientRequest(phone, name, address, notes, groupId))
        clientDao.upsert(dto.toEntity())
        return dto.toDomain()
    }

    override suspend fun updateClient(
        id: String,
        phone: String?,
        name: String?,
        address: String?,
        notes: String?,
    ): Client {
        val dto = api.updateClient(id, UpdateClientRequest(phone, name, address, notes))
        clientDao.upsert(dto.toEntity())
        return dto.toDomain()
    }

    override suspend fun deleteClient(id: String) {
        api.deleteClient(id)
        clientDao.deleteById(id)
    }
}
