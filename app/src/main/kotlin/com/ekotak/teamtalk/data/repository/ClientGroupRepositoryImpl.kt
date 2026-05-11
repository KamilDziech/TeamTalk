package com.ekotak.teamtalk.data.repository

import com.ekotak.teamtalk.data.local.dao.ClientGroupDao
import com.ekotak.teamtalk.data.mapper.toDomain
import com.ekotak.teamtalk.data.mapper.toEntity
import com.ekotak.teamtalk.data.remote.api.TeamTalkApi
import com.ekotak.teamtalk.data.remote.dto.CreateClientGroupRequest
import com.ekotak.teamtalk.domain.model.ClientGroup
import com.ekotak.teamtalk.domain.repository.ClientGroupRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

class ClientGroupRepositoryImpl @Inject constructor(
    private val api: TeamTalkApi,
    private val clientGroupDao: ClientGroupDao,
) : ClientGroupRepository {

    override fun getGroups(): Flow<List<ClientGroup>> = channelFlow {
        launch { clientGroupDao.observeAll().map { it.map { e -> e.toDomain() } }.collect(::send) }
        try {
            val fresh = api.getClientGroups()
            clientGroupDao.upsertAll(fresh.map { it.toEntity() })
        } catch (_: Exception) {}
    }

    override suspend fun createGroup(name: String, isDefault: Boolean): ClientGroup {
        val dto = api.createClientGroup(CreateClientGroupRequest(name, isDefault))
        clientGroupDao.upsert(dto.toEntity())
        return dto.toDomain()
    }

    override suspend fun deleteGroup(id: String) {
        api.deleteClientGroup(id)
        clientGroupDao.deleteById(id)
    }

    override suspend fun ensureDefaultGroup(): ClientGroup {
        val existing = clientGroupDao.getDefault()
        if (existing != null) return existing.toDomain()
        val groups = try { api.getClientGroups() } catch (_: Exception) { emptyList() }
        val remote = groups.firstOrNull { it.isDefault }
        if (remote != null) {
            clientGroupDao.upsert(remote.toEntity())
            return remote.toDomain()
        }
        return createGroup("Pozostałe", isDefault = true)
    }
}
