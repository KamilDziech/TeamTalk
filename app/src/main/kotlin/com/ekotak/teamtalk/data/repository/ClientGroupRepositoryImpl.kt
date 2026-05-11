package com.ekotak.teamtalk.data.repository

import com.ekotak.teamtalk.data.local.dao.ClientGroupDao
import com.ekotak.teamtalk.data.local.entity.ClientGroupEntity
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
import java.time.Instant
import java.util.UUID
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
        return try {
            val dto = api.createClientGroup(CreateClientGroupRequest(name, isDefault))
            clientGroupDao.upsert(dto.toEntity())
            dto.toDomain()
        } catch (_: Exception) {
            // backend unavailable — store locally with generated id
            val local = ClientGroupEntity(
                id = UUID.randomUUID().toString(),
                name = name,
                isDefault = isDefault,
                createdAt = Instant.now().toString(),
            )
            clientGroupDao.upsert(local)
            local.toDomain()
        }
    }

    override suspend fun deleteGroup(id: String) {
        try { api.deleteClientGroup(id) } catch (_: Exception) {}
        clientGroupDao.deleteById(id)
    }

    override suspend fun ensureDefaultGroup(): ClientGroup {
        val existing = clientGroupDao.getDefault()
        if (existing != null) return existing.toDomain()
        try {
            val groups = api.getClientGroups()
            clientGroupDao.upsertAll(groups.map { it.toEntity() })
            val remote = groups.firstOrNull { it.isDefault }
            if (remote != null) return remote.toDomain()
        } catch (_: Exception) {}
        return createGroup("Pozostałe", isDefault = true)
    }
}
