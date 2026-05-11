package com.ekotak.teamtalk.domain.repository

import com.ekotak.teamtalk.domain.model.ClientGroup
import kotlinx.coroutines.flow.Flow

interface ClientGroupRepository {
    fun getGroups(): Flow<List<ClientGroup>>
    suspend fun createGroup(name: String, isDefault: Boolean = false): ClientGroup
    suspend fun deleteGroup(id: String)
    suspend fun ensureDefaultGroup(): ClientGroup
}
